/*
 * Copyright IBM Corporation 2016-2017
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.csync.commands

import java.sql.Connection

import com.ibm.csync.database._
import com.ibm.csync.session.Session
import com.ibm.csync.types.Pattern

case class Advance(rvts: Long, pattern: Seq[String]) extends Command {

  override def doit(us: Session): AdvanceResponse = {
    us.transaction { sqlConnection =>
      val limit = 10
      val (patternWhere, patternVals) = Pattern(pattern).asWhere
      val acls = getAcls(sqlConnection, us.userInfo)
      val aclWhere = List.fill(acls.length)("?").mkString(",")
      val queryVals1 = Seq(rvts) ++ patternVals ++ acls ++ Seq(us.userInfo.userId, limit)

      var maxVts = SqlStatement.queryResult(
        sqlConnection,
        "SELECT last_value FROM latest_vts_seq",
        Seq()
      ) { rs => rs.getLong("last_value") }.head

      val rs1: Seq[Long] = getFromLatest(sqlConnection, patternWhere, aclWhere, queryVals1)
      if (rs1.length == limit) {
        maxVts = rs1.last
      }

      val queryVals2 = Seq(rvts, maxVts) ++ patternVals ++ acls ++ Seq(us.userInfo.userId, limit)
      val rs2: Seq[Long] = getFromAttic(sqlConnection, patternWhere, aclWhere, queryVals2)
      if (rs2.length == limit) {
        maxVts = rs2.last
        AdvanceResponse(rs1.filter(_ < maxVts) ++ rs2, maxVts)
      } else {
        AdvanceResponse(rs1 ++ rs2, maxVts)
      }
    }
  }

  def addTerm(op: String, term: String): String = {
    if (term.length > 0) { s"$op $term" } else { "" }
  }

  private def getFromAttic(sqlConnection: Connection, patternWhere: String, aclWhere: String, queryVals2: Seq[Any]) =
    SqlStatement.queryResult(
      sqlConnection,
      s"""
          SELECT vts FROM attic WHERE vts > ? AND vts < ?
              ${addTerm("AND", patternWhere)}
					    AND (aclid IN ($aclWhere) OR creatorid = ?)
					    ORDER BY vts LIMIT ?
          """,
      queryVals2
    ) { rs => rs.getLong("vts") }

  private def getFromLatest(sqlConnection: Connection, patternWhere: String, aclWhere: String, queryVals1: Seq[Any]) =
    SqlStatement.queryResult(
      sqlConnection,
      s"""
          SELECT vts FROM latest WHERE vts > ?
              ${addTerm("AND", patternWhere)}
					    AND (aclid IN ($aclWhere) OR creatorid = ?)
					    ORDER BY vts LIMIT ?
          """,
      queryVals1
    ) { rs => rs.getLong("vts") }

}
