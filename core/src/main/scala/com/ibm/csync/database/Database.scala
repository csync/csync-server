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

package com.ibm.csync.database

import java.sql.SQLException
import javax.sql.DataSource

import com.ibm.csync.Utils._
import com.typesafe.scalalogging.LazyLogging

case class Table(name: String, fields: Seq[Field] = Seq(), indexes: Seq[Index] = Seq()) {

  def fields(f: Field, fs: Field*): Table = this.copy(fields = fields ++ Seq(f) ++ fs.toSeq)

  def fields(fs: Traversable[Field]): Table = this.copy(fields = fields ++ fs.toSeq)

  def indexes(i: Index, is: Index*): Table = this.copy(indexes = indexes ++ Seq(i) ++ is.toSeq)

  def index(col: String, rest: String*): Table = {
    val cols = Seq(col) ++ rest
    val index = Index(
      name,
      (Seq(name) ++ cols).mkString("_"),
      false,
      cols: _*
    )
    this.copy(indexes = indexes ++ Seq(index))
  }

  def getSQL: Seq[String] = Seq(s"create table $name ()") ++
    (fields map { f => s"alter table $name add column ${f.asString}" }) ++
    (indexes map { _.asString })

  def unique(cols: String*): Table = {
    val index = Index(name, (Seq(name) ++ cols.toSeq).mkString("_"), true, cols: _*)
    this.copy(indexes = indexes ++ Seq(index))
  }

}

trait Step {
  val asString: String
}

case class Field(name: String, typ: String,
    isNull: Boolean = true,
    limit: Option[Long] = None,
    isPrimary: Boolean = false,
    isUnique: Boolean = false,
    extra: String = "") extends Step {
  lazy val asString: String = "%s %s %s %s %s %s %s".format(
    name,
    typ,
    limit match {
      case Some(x) => "(%d)".format(x)
      case None => ""
    },
    if (isNull) " NULL " else " NOT NULL ",
    if (isPrimary) " PRIMARY KEY " else "",
    if (isUnique) "UNIQUE" else "",
    extra
  )

  def primary: Field = this.copy(isPrimary = true)

  def length(x: Long): Field = this.copy(limit = Some(x))

  def notNull: Field = this.copy(isNull = false)

  def maybeNull: Field = this.copy(isNull = true)

  def unique: Field = this.copy(isUnique = true)

  def extra(txt: String): Field = this.copy(extra = extra + " " + txt)
}

case class Index(table: String, name: String, isUnique: Boolean, cols: String*) extends Step {
  lazy val asString: String = "create %s index %s on %s (%s)".format(
    if (isUnique) "UNIQUE" else "",
    name,
    table,
    cols.mkString(",")
  )

}

object Database extends LazyLogging {

  private def bigint(name: String) = Field(name, "bigint")

  private def varchar(name: String) = Field(name, "varchar")

  private def serial(name: String) = Field(name, "serial", isNull = false)

  private def boolean(name: String) = Field(name, "boolean")

  private def addKeys(t: Table, i: Int): Table = if (i < 0) t else {
    val colName = s"key$i"
    addKeys(
      t.fields(varchar(colName).maybeNull).index(colName),
      i - 1
    )
  }

  val KEY_COMPONENTS = 16

  def getSQL: Seq[String] = Seq(
    addKeys(
      Table("latest").fields(
        serial("vts").unique,
        bigint("cts").notNull,
        varchar("key").notNull.unique,
        varchar("aclid").notNull,
        boolean("isDeleted").notNull,
        varchar("creatorId").notNull,
        varchar("data")
      ),
      KEY_COMPONENTS - 1
    ),
    addKeys(
      Table("attic").fields(
        serial("vts").unique,
        varchar("key").notNull,
        varchar("aclid").notNull,
        varchar("creatorId").notNull
      ),
      KEY_COMPONENTS - 1
    ).unique("key", "aclid", "creatorId").index("key"),

    Table("users").fields(
      varchar("userId").primary,
      varchar("authenticatorId").notNull
    ).unique("userId", "authenticatorId").index("userId").index("authenticatorId"),

    Table("groups").fields(
      varchar("groupId").primary,
      varchar("groupName")
    ),

    Table("membership").fields(
      varchar("userId").notNull.extra("references users ON DELETE CASCADE"),
      varchar("groupId").notNull.extra("references groups ON DELETE CASCADE")
    ).unique("userId", "groupId"),

    Table("acls").fields(
      varchar("aclId").notNull,
      varchar("aclType").notNull,
      varchar("groupId").notNull.extra("references groups ON DELETE CASCADE")
    ).unique("aclId", "aclType", "groupId")
  ) flatMap (_.getSQL)

  private def handleSQLException(ex: SQLException): Unit = {
    ex.getSQLState match {
      case "42P07" =>
        logger.debug("relation already exists")
      case "42701" =>
        logger.debug("column already exists")
      case x =>
        logger.debug(s"SQLState = $x")
        throw ex
    }

  }

  def createTables(ds: DataSource): String = {
    using(ds.getConnection()) { c =>
      c.setAutoCommit(true)
      using(c.createStatement()) { s =>
        for (sql <- Database.getSQL) {
          logger.debug(s"$sql")
          try {
            val x = s.execute(sql)
            logger.debug(s"$x")
            logger.debug(s"${s.getUpdateCount}")
          } catch {
            case ex: SQLException => handleSQLException(ex)
          }
        }
      }
    }

    using(ds.getConnection()) { c2 =>
      c2.setAutoCommit(true)
      using(c2.createStatement()) { s =>
        val newUUID = java.util.UUID.randomUUID().toString
        val n = s.executeUpdate(
          s"""
             INSERT INTO latest (cts,key,aclid,isDeleted,creatorId,data,key0,key1,key2)
             VALUES (0,'sys.info.uuid','$$PublicRead',false,'$$publicUser','$newUUID','sys','info','uuid')
             ON CONFLICT DO NOTHING
           """
        )
        if (n == 1) {
          newUUID
        } else {
          using(s.executeQuery("SELECT data from latest where key = 'sys.info.uuid'")) { rs =>
            rs.next()
            rs.getString("data")
          }
        }
      }
    }
  }

}
