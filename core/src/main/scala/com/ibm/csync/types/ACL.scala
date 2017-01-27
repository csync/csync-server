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

package com.ibm.csync.types

import java.sql.Connection

import com.ibm.csync.database.SqlStatement
import com.ibm.csync.session.UserInfo
import com.ibm.csync.types.ResponseCode.{
  CreatePermissionDenied,
  DeletePermissionDenied,
  ReadPermissionDenied,
  UpdatePermissionDenied
}

sealed trait ACL {
  val id: String

  def getReadGroups(connection: Connection): Seq[String]
  def checkRead(sqlConnection: Connection, user: UserInfo): Unit
  def checkUpdate(sqlConnection: Connection, user: UserInfo): Unit
  def checkDelete(sqlConnection: Connection, user: UserInfo): Unit
  def checkCreate(sqlConnection: Connection, user: UserInfo): Unit
}

object ACL {

  trait Helper extends ACL {
    val creatorId: CreatorId
    override def getReadGroups(connection: Connection): Seq[String] = Seq(creatorId.id, "$publicUser")
    override def checkRead(sqlConnection: Connection, user: UserInfo) {
      if ((user.userId != creatorId.id) && (user.userId != "$publicUser")) {
        ReadPermissionDenied.throwIt()
      }
    }
    override def checkUpdate(sqlConnection: Connection, user: UserInfo) {
      if ((user.userId != creatorId.id) && (user.userId != "$publicUser")) {
        UpdatePermissionDenied.throwIt()
      }
    }
    override def checkCreate(sqlConnection: Connection, user: UserInfo) {
      if ((user.userId != creatorId.id) && (user.userId != "$publicUser")) {
        CreatePermissionDenied.throwIt()
      }
    }
    override def checkDelete(sqlConnection: Connection, user: UserInfo) {
      if ((user.userId != creatorId.id) && (user.userId != "$publicUser")) {
        DeletePermissionDenied.throwIt()
      }
    }
  }

  case class Private(creatorId: CreatorId) extends Helper {
    val id = "$private"
  }

  case class PublicRead(creatorId: CreatorId) extends Helper {
    val id = "$publicRead"
    override def getReadGroups(connection: Connection): Seq[String] = Seq("$world")
    override def checkRead(sqlConnection: Connection, user: UserInfo) { /* Allow */ }
  }

  case class PublicWrite(creatorId: CreatorId) extends Helper {
    val id = "$publicWrite"
    override def checkUpdate(sqlConnection: Connection, user: UserInfo) { /* Allow */ }
    override def checkDelete(sqlConnection: Connection, user: UserInfo) { /* Allow */ }
  }

  case class PublicCreate(creatorId: CreatorId) extends Helper {
    val id = "$publicCreate"
    override def checkCreate(sqlConnection: Connection, user: UserInfo) { /* Allow */ }
  }

  case class PublicReadWrite(creatorId: CreatorId) extends Helper {
    val id = "$publicReadWrite"
    override def getReadGroups(connection: Connection): Seq[String] = Seq("$world")
    override def checkRead(sqlConnection: Connection, user: UserInfo) { /* Allow */ }
    override def checkUpdate(sqlConnection: Connection, user: UserInfo) { /* Allow */ }
    override def checkDelete(sqlConnection: Connection, user: UserInfo) { /* Allow */ }
  }

  case class PublicReadCreate(creatorId: CreatorId) extends Helper {
    val id = "$publicReadCreate"
    override def getReadGroups(connection: Connection): Seq[String] = Seq("$world")
    override def checkRead(sqlConnection: Connection, user: UserInfo) { /* Allow */ }
    override def checkCreate(sqlConnection: Connection, user: UserInfo) { /* Allow */ }
  }

  case class PublicWriteCreate(creatorId: CreatorId) extends Helper {
    val id = "$publicWriteCreate"
    override def checkUpdate(sqlConnection: Connection, user: UserInfo) { /* Allow */ }
    override def checkDelete(sqlConnection: Connection, user: UserInfo) { /* Allow */ }
    override def checkCreate(sqlConnection: Connection, user: UserInfo) { /* Allow */ }
  }

  case class PublicReadWriteCreate(creatorId: CreatorId) extends Helper {
    val id = "$publicReadWriteCreate"
    override def getReadGroups(connection: Connection): Seq[String] = Seq("$world")
    override def checkRead(sqlConnection: Connection, user: UserInfo) { /* Allow */ }
    override def checkUpdate(sqlConnection: Connection, user: UserInfo) { /* Allow */ }
    override def checkDelete(sqlConnection: Connection, user: UserInfo) { /* Allow */ }
    override def checkCreate(sqlConnection: Connection, user: UserInfo) { /* Allow */ }
  }

  case class General(id: String, creator: CreatorId) extends ACL {

    override def getReadGroups(sqlConnection: Connection): Seq[String] = {
      if (id == "$private") {
        Seq(creator.id)
      } else if (id.startsWith("$public")) {
        if (id.contains("Read")) Seq("$world") else Seq(creator.id)
      } else {
        SqlStatement.queryResult(
          sqlConnection,
          "SELECT groupid from acls where aclid = ? and acltype = 'read'", Seq(id)
        ) { rs => rs.getString("groupid") }
      }
    }

    private def can(sqlConnection: Connection, responseCode: ResponseCode, kind: String, userInfo: UserInfo): Unit = {
      SqlStatement.runQuery(
        sqlConnection,
        """
        SELECT count(*) from acls join membership
        ON (acls.groupId = membership.groupId)
        WHERE aclId = ? and aclType = ? and userId = ?
        """,
        Seq(id, kind, userInfo.userId)
      ) { rs =>
          rs.next
          val count = rs.getInt(1)
          if (count == 0) responseCode.throwIt()
        }
    }

    override def checkRead(sqlConnection: Connection, user: UserInfo): Unit =
      can(sqlConnection, ReadPermissionDenied, "read", user)

    override def checkUpdate(sqlConnection: Connection, user: UserInfo): Unit =
      can(sqlConnection, UpdatePermissionDenied, "update", user)

    override def checkCreate(sqlConnection: Connection, user: UserInfo): Unit =
      can(sqlConnection, CreatePermissionDenied, "create", user)

    override def checkDelete(sqlConnection: Connection, user: UserInfo): Unit =
      can(sqlConnection, DeletePermissionDenied, "delete", user)
  }

  def wellKnowReadableACLids: Seq[String] = {
    Seq("$publicRead", "$publicReadWrite", "$publicReadCreate", "$publicReadWriteCreate")
  }

  def builtin(id: String, creator: CreatorId): ACL = id match {
    case "$private" => Private(creator)
    case "$publicRead" => PublicRead(creator)
    case "$publicWrite" => PublicWrite(creator)
    case "$publicCreate" => PublicCreate(creator)
    case "$publicReadCreate" => PublicReadCreate(creator)
    case "$publicReadWrite" => PublicReadWrite(creator)
    case "$publicWriteCreate" => PublicWriteCreate(creator)
    case "$publicReadWriteCreate" => PublicReadWriteCreate(creator)
    case _ => throw new IllegalArgumentException(id)
  }

  def apply(id: String, creator: CreatorId): ACL =
    if (id.startsWith("$")) {
      builtin(id, creator)
    } else {
      General(id, creator)
    }

}
