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

case class Advance(lvts: Long = Long.MaxValue, rvts: Long = Long.MaxValue, pattern: Seq[String]) extends Command {

  override def doit(us: Session): AdvanceResponse = {

    us.transaction { sqlConnection =>
      //Limit is double actual limit.
      val limit = 10
      val (patternWhere, patternVals) = Pattern(pattern).asWhere
      val acls = getAcls(sqlConnection, us.userInfo)
      val aclWhere = List.fill(acls.length)("?").mkString(",")
      val queryVals1 = Seq(rvts) ++ patternVals ++ acls ++ Seq(us.userInfo.userId, limit) ++ Seq(lvts) ++ patternVals ++ acls ++ Seq(us.userInfo.userId, limit)
      println("queryvals1: " + queryVals1.toString())
      var minVts: Long = 0

      var maxVts = SqlStatement.queryResult(
        sqlConnection,
        "SELECT last_value FROM latest_vts_seq",
        Seq()
      ) { rs => rs.getLong("last_value") }.head

      val rs1: Seq[Long] = getFromLatest(sqlConnection, patternWhere, aclWhere, queryVals1)
      if (rs1.length == limit) {
        maxVts = rs1.head
      }
      if (rs1.nonEmpty) {
        minVts = rs1.last
      }


      val queryVals2 = Seq(rvts, maxVts) ++ patternVals ++ acls ++ Seq(us.userInfo.userId, limit) ++ Seq(lvts, minVts) ++ patternVals ++ acls ++ Seq(us.userInfo.userId, limit)
      println("queryvals2: " + queryVals2.toString())
      val rs2: Seq[Long] = getFromAttic(sqlConnection, patternWhere, aclWhere, queryVals2)
      if (rs2.length == limit) {
        println("rs2 >= limit rs1: " + rs1.toString() + " rs2: " + rs2.toString())
        maxVts = rs2.head
        minVts = rs2.last
        val filteredRs = rs1.filter(_ < maxVts).filter(_ > minVts)
        AdvanceResponse(filteredRs ++ rs2, minVts, maxVts)

      } else {

        println("rs2 < limit rs1: " + rs1.toString() + " rs2: " + rs2.toString())
        if (minVts > lvts) {
          minVts = lvts
        }
        if (rvts != Long.MaxValue && maxVts < rvts) {
          maxVts = rvts
        }
        AdvanceResponse(rs1 ++ rs2, minVts, maxVts)
      }
    }
  }

  def addTerm(op: String, term: String): String = {
    if (term.length > 0) {
      s"$op $term"
    } else {
      ""
    }
  }

  private def getFromAttic(sqlConnection: Connection, patternWhere: String, aclWhere: String, queryVals2: Seq[Any]) =
    SqlStatement.queryResult(
      sqlConnection,
      s"""
          (SELECT vts FROM attic WHERE (vts > ? AND vts < ?)
              ${addTerm("AND", patternWhere)}
					    AND (aclid IN ($aclWhere) OR creatorid = ?)
					    ORDER BY vts ASC LIMIT ?)

         UNION ALL

         (SELECT vts FROM attic WHERE (vts < ? AND vts > ?)
                       ${addTerm("AND", patternWhere)}
         					    AND (aclid IN ($aclWhere) OR creatorid = ?)
                      ORDER BY vts DESC LIMIT ?)
          ORDER BY vts DESC
          """,
      queryVals2
    ) { rs => rs.getLong("vts") }

  private def getFromLatest(sqlConnection: Connection, patternWhere: String, aclWhere: String, queryVals1: Seq[Any]) =
    SqlStatement.queryResult(
      sqlConnection,
      s"""
          (SELECT vts FROM latest WHERE vts > ?
              ${addTerm("AND", patternWhere)}
					    AND (aclid IN ($aclWhere) OR creatorid = ?)
					    ORDER BY vts ASC LIMIT ?)
          UNION ALL
         ( SELECT vts FROM latest WHERE vts < ?
                       ${addTerm("AND", patternWhere)}
         					    AND (aclid IN ($aclWhere) OR creatorid = ?)

         					    ORDER BY vts DESC LIMIT ?)
                  ORDER BY vts DESC
          """,
      queryVals1

    ) { rs => rs.getLong("vts") }

}
