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

import java.sql.{Connection, ResultSet}

import com.ibm.csync.database.SqlStatement
import com.ibm.csync.session.Session
import com.ibm.csync.types._
import com.ibm.csync.types.ResponseCode._

import scala.collection.mutable

class PubState(sqlConnection: Connection, req: Pub, us: Session) {

  private[commands] val updates = mutable.ArrayBuffer[Data]()
  private val pubKey = Key(req.path)
  private val (patternWhere, patternVals) = Pattern(req.path).asWhere
  private val pubData = req.data
  private val creatorId = CreatorId(us.userInfo.userId)
  private val pubAcl = req.assumeACL map { ACL(_, creatorId) }

  def delete(): VTS = {

    SqlStatement.runQuery(
      sqlConnection,
      "SELECT vts,cts,aclid,creatorid,key FROM latest WHERE " + patternWhere + " AND isDeleted = false FOR UPDATE ", patternVals

    ) { rs =>
        var highestVts = VTS(-1)
        while (rs.next) {
          val oldVts = rs.getLong("vts")
          val oldCts = rs.getLong("cts")
          val oldCreator = CreatorId(rs.getString("creatorid"))
          val oldAcl = ACL(rs.getString("aclid"), oldCreator)
          val oldPath = rs.getString("key")
          if (req.cts <= oldCts) PubCtsCheckFailed.throwIt()

          oldAcl.checkDelete(sqlConnection, us.userInfo)

          val newVts = SqlStatement.updateGetVts(
            sqlConnection,
            "UPDATE latest SET vts=default, cts = ?, isDeleted = true, data = null WHERE vts = ? RETURNING vts",
            Seq(req.cts, oldVts)
          )

          updates += Data(
            vts = newVts.vts,
            cts = req.cts,
            acl = oldAcl.id,
            creator = oldCreator.id,
            path = oldPath.split('.'),
            deletePath = true,
            data = None
          )
          if (newVts.vts > highestVts.vts) { highestVts = newVts }
        }
        if (highestVts == -1) {
          /* TODO: Is this really needed? */
          CannotDeleteNonExistingPath.throwIt()
        } else {
          return highestVts
        }
      }
  }

  //
  // Create entry in database
  // Precondition: no entry for that key in latest table
  //
  def create(): VTS = {
    val effectiveParentAcl = getEffectiveAcl(pubKey.parent)
    effectiveParentAcl.checkCreate(sqlConnection, us.userInfo)
    val newAcl = pubAcl.getOrElse(ACL(effectiveParentAcl.id, creatorId))
    // TODO: we don't need this
    SqlStatement.runUpdate(
      sqlConnection,
      "DELETE FROM attic WHERE key = ? AND aclid = ? AND creatorid = ?",
      Seq(pubKey.asString, newAcl.id, creatorId.id)
    ) { n => assert(n <= 1) }
    val newVts = SqlStatement.updateGetVts(
      sqlConnection,
      s"""
          INSERT INTO latest (cts,aclid,creatorid,key,isDeleted,data${dbNames(pubKey)})
          VALUES (?,?,?,?,false,?${dbVals(pubKey)}) RETURNING vts
        """,
      Seq(req.cts, newAcl.id, creatorId.id, pubKey.asString, pubData.orNull) ++ (pubKey.parts map { _.asString })
    )

    updates += Data(
      vts = newVts.vts,
      cts = req.cts,
      acl = newAcl.id,
      creator = creatorId.id,
      deletePath = false,
      path = pubKey.asStrings,
      data = pubData
    )

    newVts
  }

  def getEffectiveAcl(key: Key): ACL = {
    if (key.parts.isEmpty) {
      ACL("$publicCreate", CreatorId("$publicUser"))
    } else {
      SqlStatement.runQuery(
        sqlConnection,
        "SELECT aclid,creatorid,isDeleted,vts FROM latest WHERE key = ?",
        Seq(key.asString)
      ) { rs =>
          if (rs.next) {
            if (rs.getBoolean("isDeleted")) {
              getEffectiveAcl(key.parent)
            } else {
              ACL(rs.getString("aclid"), CreatorId(rs.getString("creatorid")))
            }
          } else {
            getEffectiveAcl(key.parent)
          }
        }
    }
  }

  def createOrUpdate(): VTS = {

    SqlStatement.runQuery(
      sqlConnection,
      "SELECT vts,cts,aclid,creatorid,isDeleted FROM latest WHERE key = ? FOR UPDATE",
      Seq(pubKey.asString)
    ) { rs =>
        if (rs.next) {
          doUpdate(rs)
        } else {
          create()
        }
      }
  }

  private def doUpdate(rs: ResultSet) = {
    val oldVts = VTS(rs.getLong("vts"))
    val oldCts = rs.getLong("cts")
    val oldIsDeleted = rs.getBoolean("isDeleted")
    val oldCreatorId = CreatorId(rs.getString("creatorid"))
    val oldAcl = ACL(rs.getString("aclid"), oldCreatorId)

    if (req.cts <= oldCts) PubCtsCheckFailed.throwIt()

    val newAclId = req.assumeACL.getOrElse(oldAcl.id)

    if (oldIsDeleted) {
      doUpdateDeleted(oldVts, oldCreatorId, oldAcl, newAclId)
    } else {
      doUpdateInPlace(oldVts, oldCreatorId, oldAcl, newAclId)
    }
  }

  private def doUpdateInPlace(oldVts: VTS, oldCreatorId: CreatorId, oldAcl: ACL, newAclId: String) = {
    oldAcl.checkUpdate(sqlConnection, us.userInfo)
    if (oldAcl.id != newAclId || oldCreatorId.id != creatorId.id) {
      val keys = Seq(pubKey.asString, oldAcl.id, oldCreatorId.id)
      // changing ACL, make it look like we're deleting the old record
      val deleteVts = SqlStatement.updateGetVts(
        sqlConnection,
        s"""INSERT INTO attic (vts,key,aclid,creatorid${dbNames(pubKey)})
            VALUES (nextval('latest_vts_seq'),?,?,?${dbVals(pubKey)})
            ON CONFLICT (key,aclid,creatorid) DO
                 UPDATE SET vts = nextval('latest_vts_seq')
                 WHERE attic.key = ? and attic.aclid = ? and attic.creatorid = ?
            RETURNING vts""",
        keys ++ (pubKey.parts map { _.asString }) ++ keys
      )
      updates += Data(
        cts = req.cts,
        vts = deleteVts.vts,
        deletePath = true,
        acl = oldAcl.id,
        creator = oldCreatorId.id,
        path = pubKey.asStrings,
        data = None
      )
    }
    // TODO: Need to decide how to interpret data=None on a pub
    val newVts = pubData match {
      case Some(stuff) =>
        SqlStatement.updateGetVts(
          sqlConnection,
          "UPDATE latest SET vts=default, cts = ?, aclid = ?, data = ? WHERE vts = ? RETURNING vts",
          Seq(req.cts, newAclId, stuff, oldVts.vts)
        )
      case None =>
        SqlStatement.updateGetVts(
          sqlConnection,
          "UPDATE latest SET vts=default, cts = ?, aclid = ? WHERE vts = ? RETURNING vts",
          Seq(req.cts, newAclId, oldVts.vts)
        )
    }
    updates += Data(
      cts = req.cts,
      vts = newVts.vts,
      deletePath = false,
      acl = newAclId,
      creator = oldCreatorId.id,
      path = pubKey.asStrings,
      data = pubData
    )
    newVts
  }

  private def doUpdateDeleted(oldVts: VTS, oldCreatorId: CreatorId, oldAcl: ACL, newAclId: String) = {
    // move deleted record to attic
    SqlStatement.runUpdate1(sqlConnection, "DELETE FROM latest WHERE vts = ?", Seq(oldVts.vts))
    if (oldAcl.id != newAclId || oldCreatorId.id != creatorId.id) {
      // We maintain the invariant that the same key+aclid+creatorid will never exist in both latest and attic,
      // so this insert should never encounter a duplicate key exception

      val keys = Seq(pubKey.asString, oldAcl.id, oldCreatorId.id)

      SqlStatement.runUpdate1(
        sqlConnection,
        s"""INSERT INTO attic (vts,key,aclid,creatorid${dbNames(pubKey)})
            VALUES (?,?,?,?${dbVals(pubKey)})
            ON CONFLICT (key,aclid,creatorid) DO
                UPDATE  SET vts = ? WHERE attic.key = ? and attic.aclid = ? and attic.creatorid = ?
          """,
        Seq(oldVts.vts) ++ keys ++ pubKey.parts.map { _.asString } ++ Seq(oldVts.vts) ++ keys

      )
    }
    create()
  }
}

case class Pub(cts: Long, path: Seq[String], data: Option[String],
    deletePath: Boolean, assumeACL: Option[String], schema: Option[String]) extends Command {

  //override def shortString: String = s"${if (deletePath) "delete " else ""}${path.mkString(".")}:$assumeACL@$cts"

  def addUser(sqlConnection: Connection, userId: String, authenticatorId: String): Unit = {
    SqlStatement.runUpdate(
      sqlConnection,
      "INSERT INTO users (userid,authenticatorid) VALUES (?,?) ON CONFLICT DO NOTHING",
      Seq(userId, authenticatorId)
    ) { n => assert(n <= 1) }
  }

  def addGroup(sqlConnection: Connection, groupId: String): Unit = {
    SqlStatement.runUpdate(
      sqlConnection,
      "INSERT INTO groups (groupid) VALUES (?) ON CONFLICT DO NOTHING",
      Seq(groupId)
    ) { n => assert(n <= 1) }
  }

  def addMembership(sqlConnection: Connection, groupId: String, userId: String): Unit = {
    SqlStatement.runUpdate(
      sqlConnection,
      "INSERT INTO membership (groupid,userid) VALUES (?,?) ON CONFLICT DO NOTHING",
      Seq(groupId, userId)
    ) { n => assert(n <= 1) }
  }

  override def doit(us: Session): PubResponse = {

    // Invariants (guarded by transactions):
    //     - a given key,acl,creatorid combination will never be present in both latest and attic
    //     - for a given key, the entry in latest will have the largest VTS
    //

    val aclToReaders = mutable.Map[ACL, Seq[String]]()

    val (dbOutcome, newVts) = us.transaction { implicit sqlConnection =>
      val state = new PubState(sqlConnection, this, us)

      // perform the operation
      val vts = if (deletePath) {
        state.delete()
      } else {
        state.createOrUpdate()
      }

      // perform any side-effects
      val steps = state.updates.toList

      doSideEffects(sqlConnection, aclToReaders, steps)

      (steps, vts)
    }

    for (r <- dbOutcome) {
      import boopickle.Default._
      val acl = ACL(r.acl, CreatorId(r.creator))
      val data = Pickle.intoBytes(r).array()
      val targetGroups = aclToReaders(acl)

      targetGroups foreach { us.send(_, r.path, data) }
    }

    PubResponse(0, "OK", cts, newVts.vts)
  }

  private def doSideEffects(
    sqlConnection: Connection,
    aclToReaders: mutable.Map[ACL, Seq[String]], steps: List[Data]
  ) = {
    for (s <- steps) {
      val creator = CreatorId(s.creator)
      val a = ACL(s.acl, creator)

      val groups = aclToReaders.get(a) match {
        case Some(g) =>
          g
        case None =>
          val t = a.getReadGroups(sqlConnection)
          aclToReaders(a) = t
          t
      }

      s.path match {
        case Seq("sys", "acls", aclId, permission, groupId) =>
          doAclUpdate(sqlConnection, s, aclId, permission, groupId)
        case Seq("sys", "users", userId) =>
          doUserUpdate(sqlConnection, s, userId)
        case Seq("sys", "groups", groupId) =>
          doGroupUpdate(sqlConnection, s, groupId)
        case Seq("sys", "groups", groupId, "member", userId) =>
          doMembershipUpdate(sqlConnection, s, groupId, userId)
        case _ =>
      }
    }
  }

  private def doMembershipUpdate(sqlConnection: Connection, s: Data, groupId: String, userId: String) = {
    if (s.deletePath) {
      ???
    } else {
      addMembership(sqlConnection, groupId, userId)
    }
  }

  private def doGroupUpdate(sqlConnection: Connection, s: Data, groupId: String) = {
    if (s.deletePath) {
      ???
    } else {
      addGroup(sqlConnection, groupId)
    }
  }

  private def doUserUpdate(sqlConnection: Connection, s: Data, userId: String) = {
    if (s.deletePath) {
      ???
    } else {
      addUser(sqlConnection, userId, "authenticator")
      addGroup(sqlConnection, userId)
      addMembership(sqlConnection, userId, userId)
    }
  }

  private def doAclUpdate(sqlConnection: Connection, s: Data, aclId: String, permission: String, groupId: String) = {
    if (s.deletePath) {
      SqlStatement.runUpdate(
        sqlConnection,
        "DELETE acls WHERE aclid = ? AND acltype = ? AND groupid = ?",
        Seq(aclId, permission, groupId)
      ) { n => assert(n <= 1) }
    } else {
      SqlStatement.runUpdate(
        sqlConnection,
        "INSERT INTO acls (aclid,acltype,groupid) VALUES (?,?,?) ON CONFLICT DO NOTHING",
        Seq(aclId, permission, groupId)
      ) { n => assert(n <= 1) }
    }
  }
}
