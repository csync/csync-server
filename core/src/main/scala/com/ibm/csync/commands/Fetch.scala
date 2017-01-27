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

import com.ibm.csync.database._
import com.ibm.csync.session.Session

import scala.collection.mutable

case class Fetch(vts: Seq[Long]) extends Command {

  //override def shortString: String = s"$vts"

  val FETCH_GROUP_SIZE = 10

  override def doit(us: Session): FetchResponse = {
    val updates = mutable.ArrayBuffer[Data]()

    us.transaction { sqlConnection =>
      val acls = com.ibm.csync.commands.getAcls(sqlConnection, us.userInfo)
      val aclWhere = List.fill(acls.length)("?").mkString(",")

      // sort vts list (will most likely be ordered, but just to be sure)
      vts.sorted.grouped(FETCH_GROUP_SIZE).toList.foreach { vtsChunk =>
        val vtsWhere = List.fill(vtsChunk.length)("?").mkString(",")
        val queryVals = acls ++ Seq(us.userInfo.userId) ++ vtsChunk
        updates ++= SqlStatement.queryResult(
          sqlConnection,
          s"""
            SELECT vts,cts,key,aclid,creatorid,isDeleted,data FROM latest
                WHERE (aclid IN ($aclWhere) OR creatorid = ?)
                AND vts IN ($vtsWhere)
          """,
          queryVals
        ) { rs =>
            Data(
              vts = rs.getLong("vts"), cts = rs.getLong("cts"), path = rs.getString("key").split('.'),
              acl = rs.getString("aclid"), creator = rs.getString("creatorid"), deletePath = rs.getBoolean("isdeleted"),
              data = Option(rs.getString("data"))
            )
          }

        updates ++= SqlStatement.queryResult(
          sqlConnection,
          s"""
            SELECT vts,key,aclid,creatorid FROM attic
                WHERE (aclid IN ($aclWhere) OR creatorid = ?)
                AND vts IN ($vtsWhere)
          """,
          queryVals
        ) { rs =>
            Data(
              vts = rs.getLong("vts"), cts = 0, path = rs.getString("key").split('.'),
              acl = rs.getString("aclid"), creator = rs.getString("creatorid"), deletePath = true, data = None
            )
          }
      }
    }
    FetchResponse(updates)
  }

}
