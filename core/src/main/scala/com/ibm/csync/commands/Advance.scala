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
case class Advance(lvts: Long = Long.MaxValue, rvts: Long = Long.MaxValue, var backwardLimit: Int = 0, var forwardLimit: Int = Int.MaxValue, pattern: Seq[String]) extends Command {

  override def doit(us: Session): AdvanceResponse = {

    us.transaction { sqlConnection =>

      //Sanitize the limits, we wish to provide limits up to our maximum allowed.
      val limit = 20
      backwardLimit = math.min(backwardLimit, limit)
      forwardLimit = math.min(forwardLimit, limit)

      //Convert the data we have into a usable form for the database calls
      val (patternWhere, patternVals) = Pattern(pattern).asWhere
      val acls = getAcls(sqlConnection, us.userInfo)
      val aclWhere = List.fill(acls.length)("?").mkString(",")

      //Get sane default values for min and max vts
      var minVts: Long = 0
      var maxVts = SqlStatement.queryResult(
        sqlConnection,
        "SELECT last_value FROM latest_vts_seq",
        Seq()
      ) { rs => rs.getLong("last_value") }.head

      // Getting New (as in VTS later then rvts) data
      var forwardList: Seq[Long] = Seq.empty[Long]
      if (forwardLimit > 0) {
        val queryVals1 = Seq(rvts) ++ patternVals ++ acls ++ Seq(us.userInfo.userId, forwardLimit) ++ Seq(rvts) ++ patternVals ++ acls ++ Seq(us.userInfo.userId, forwardLimit, forwardLimit)
        println(queryVals1.toString())
        forwardList = getNewData(sqlConnection, patternWhere, aclWhere, queryVals1)
        if (forwardList.length == forwardLimit) { //We asked for backwards limit number of items and we received that many, new lvts is the lowest vts of that list
          maxVts = forwardList.last
        }
      } else { //We are not trying to go forwards, but we still need to find a valid rvts
        //If an rvts was provided, we haven't moved forward so it should still be valid
        if (rvts != Long.MaxValue) {
          maxVts = rvts
        } else if (lvts != Long.MaxValue) { // If an lvts was provided and not an rvts, we haven't moved forward so that should still be valid
          maxVts = lvts
        }
        //We default to the last_value sql statement above if no rvts or lvts was provided
      }

      // Getting old data
      var backwardList: Seq[Long] = Seq.empty[Long]
      if (backwardLimit > 0) {
        val queryVals2 = Seq(lvts) ++ patternVals ++ acls ++ Seq(us.userInfo.userId, backwardLimit) ++ Seq(lvts) ++ patternVals ++ acls ++ Seq(us.userInfo.userId, backwardLimit, backwardLimit)
        println(queryVals2.toString())
        backwardList = getOldData(sqlConnection, patternWhere, aclWhere, queryVals2)
        if (backwardList.length == backwardLimit) {
          //We asked for backwards limit number of items and we received that many, new lvts is the lowest vts of that list
          minVts = backwardList.last - 1
        }
      } else { //They are not trying to go backwards, but we still need to provide a valid new lvts
        //This is a bit complicated because they can provide lvts and rvts or we can default them to other values
        //Basically, we end up setting min vts to the lowest of lvts,rvts,maxvts and forwardlist.head, but forward list.head
        //should always be <= maxvts if it exists
        minVts = math.min(lvts, rvts - 1)
        if (forwardList.length > 0) {
          minVts = math.min(minVts, forwardList.head - 1)
        } else {
          minVts = math.min(maxVts - 1, minVts)
        }
      }

      println(AdvanceResponse(backwardList, forwardList, minVts, maxVts).toString())
      AdvanceResponse(backwardList, forwardList, minVts, maxVts)
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
          (SELECT vts FROM latest WHERE vts <= ?
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
