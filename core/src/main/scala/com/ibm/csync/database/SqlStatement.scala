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

import java.sql.{Connection, PreparedStatement, ResultSet}

import com.ibm.csync.Utils.using
import com.ibm.csync.types.VTS
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable

object SqlStatement extends LazyLogging {
  def prep[T](c: Connection, sql: String, args: Seq[Any])(f: PreparedStatement => T): T = {
    logger.debug(sql)
    using(c.prepareStatement(sql)) { s =>
      var i = 1
      for (a <- args) {
        s.setObject(i, a)
        logger.debug(s".... ${String.valueOf(a)}")
        i += 1
      }
      f(s)
    }
  }

  def runQuery[T](c: Connection, sql: String, args: Seq[Any])(f: ResultSet => T): T = prep(c, sql, args) { s =>
    using(s.executeQuery()) { rs =>
      f(rs)
    }
  }

  def queryResult[T](c: Connection, sql: String, args: Seq[Any])(f: ResultSet => T): Seq[T] =
    prep(c, sql, args) { s =>
      using(s.executeQuery()) { rs =>
        val b = mutable.Buffer[T]()
        while (rs.next) {
          b.append(f(rs))
        }
        b.asInstanceOf[Seq[T]]
      }
    }

  def runUpdate[T](c: Connection, sql: String, args: Seq[Any])(f: Int => T): T = prep(c, sql, args) { s =>
    val res = s.executeUpdate()
    logger.debug(s".... returned $res")
    f(res)
  }

  def runUpdate1(c: Connection, sql: String, args: Seq[Any]): Unit = runUpdate(c, sql, args) { count =>
    assert(count == 1)
  }

  def updateGetVts(c: Connection, sql: String, args: Seq[Any]): VTS = runQuery(c, sql, args) { rs =>
    rs.next
    val vts = rs.getLong(1)
    logger.debug(s".... returned $vts")
    VTS(vts)
  }
}
