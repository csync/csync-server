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

package com.ibm.csync

import java.sql.Connection

import com.ibm.csync.database.SqlStatement
import com.ibm.csync.session.{Session, UserInfo}
import com.ibm.csync.types.{ACL, Key}

package object commands {

  trait Command {
    def doit(us: Session): Response
    //def shortString: String
  }

  val MESSAGE_VERSION = 15

  def dbNames(key: Key): String = {
    (key.parts.indices map { i => s",key$i" }).mkString
  }

  def dbVals(key: Key): String = {
    ",?" * key.parts.length
  }

  def getAcls(sqlConnection: Connection, user: UserInfo): Seq[String] = {
    val acls = ACL.wellKnowReadableACLids ++ SqlStatement.queryResult(
      sqlConnection,
      """
        SELECT DISTINCT acls.aclid AS aclid
					FROM acls, membership
					WHERE membership.userid = ? AND membership.groupid = acls.groupid AND acltype = 'read'
      """,
      Seq(user.userId)
    ) { rs => rs.getString("aclid") }
    acls
  }

}
