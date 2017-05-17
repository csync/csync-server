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

//scalastyle:off
case class Advance(lvts: Long = Long.MaxValue, rvts: Long = Long.MaxValue, var backwardLimit: Int = Int.MaxValue, var forwardLimit: Int = Int.MaxValue, pattern: Seq[String]) extends Command {

  override def doit(us: Session): AdvanceResponse = {

    us.transaction { sqlConnection =>

      val limit = 10
      backwardLimit = math.min(backwardLimit,limit)
      forwardLimit = math.min(forwardLimit,limit)

      val (patternWhere, patternVals) = Pattern(pattern).asWhere
      val acls = getAcls(sqlConnection, us.userInfo)
      val aclWhere = List.fill(acls.length)("?").mkString(",")

      var minVts: Long = 0
      var maxVts = SqlStatement.queryResult(
        sqlConnection,
        "SELECT last_value FROM latest_vts_seq",
        Seq()
      ) { rs => rs.getLong("last_value") }.head

      var forwardList: Seq[Long] = Seq.empty[Long]

      if (forwardLimit > 0) {
        val queryVals1 = Seq(rvts) ++ patternVals ++ acls ++ Seq(us.userInfo.userId, forwardLimit) ++ Seq(rvts) ++ patternVals ++ acls ++ Seq(us.userInfo.userId, forwardLimit, forwardLimit)
        println(queryVals1.toString())
        forwardList = getNewData(sqlConnection, patternWhere, aclWhere, queryVals1)
        if (forwardList.length == forwardLimit) { //We asked for backwards limit number of items and we received that many, new lvts is the lowest vts of that list
          maxVts = forwardList.last
        }
      } else {
        if(rvts != Long.MaxValue) {
          maxVts = rvts
        } else if (lvts != Long.MaxValue) {
          maxVts = lvts
        }
      }

      var backwardList: Seq[Long] = Seq.empty[Long]
      if (backwardLimit > 0) {
        val queryVals2 = Seq(lvts) ++ patternVals ++ acls ++ Seq(us.userInfo.userId, backwardLimit) ++ Seq(lvts) ++ patternVals ++ acls ++ Seq(us.userInfo.userId, backwardLimit, backwardLimit)
        println(queryVals2.toString())
        backwardList = getOldData(sqlConnection, patternWhere, aclWhere, queryVals2)
        if (backwardList.length == backwardLimit) { //We asked for backwards limit number of items and we received that many, new lvts is the lowest vts of that list
          minVts = backwardList.last
        }
      }
      else {
        minVts = math.min(lvts,rvts)
        if (forwardList.length > 0) {
          minVts = math.min(minVts, forwardList.head)
        }
        else {
          minVts = math.min(maxVts,minVts)
        }
      }

      AdvanceResponse(backwardList ++ forwardList, minVts, maxVts)
    }

  }

  def addTerm(op: String, term: String): String = {
    if (term.length > 0) {
      s"$op $term"
    } else {
      ""
    }
  }

  private def getNewData(sqlConnection: Connection, patternWhere: String, aclWhere: String, queryVals1: Seq[Any]) =
    SqlStatement.queryResult(
      sqlConnection,
      s"""
          (SELECT vts FROM latest WHERE vts > ?
              ${addTerm("AND", patternWhere)}
					    AND (aclid IN ($aclWhere) OR creatorid = ?)
					    ORDER BY vts ASC LIMIT ?)
          UNION ALL
         ( SELECT vts FROM attic WHERE vts > ?
                       ${addTerm("AND", patternWhere)}
         					    AND (aclid IN ($aclWhere) OR creatorid = ?)

         					    ORDER BY vts ASC LIMIT ?)
                  ORDER BY vts ASC LIMIT ?
          """,
      queryVals1

    ) { rs => rs.getLong("vts") }

  private def getOldData(sqlConnection: Connection, patternWhere: String, aclWhere: String, queryVals1: Seq[Any]) =
    SqlStatement.queryResult(
      sqlConnection,
      s"""
          (SELECT vts FROM latest WHERE vts < ?
              ${addTerm("AND", patternWhere)}
					    AND (aclid IN ($aclWhere) OR creatorid = ?)
					    ORDER BY vts DESC LIMIT ?)
          UNION ALL
         ( SELECT vts FROM attic WHERE vts < ?
                       ${addTerm("AND", patternWhere)}
         					    AND (aclid IN ($aclWhere) OR creatorid = ?)

         					    ORDER BY vts DESC LIMIT ?)
                  ORDER BY vts DESC LIMIT ?
          """,
      queryVals1

    ) { rs => rs.getLong("vts") }


}
