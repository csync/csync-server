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

import org.postgresql.ds.PGPoolingDataSource
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, PropSpec}

class PostgresTests extends PropSpec with Matchers with ScalaFutures {
  // scalastyle:off
  //Println is throwing scalastyle errors, remove this when we remove them
  property("create schema") {

    val ds = new PGPoolingDataSource
    ds.setServerName("localhost")
    ds.setCurrentSchema("pg_temp")
    ds.setMaxConnections(1)

    Database.createTables(ds)

    val c = ds.getConnection()
    try {
      val s = c.createStatement()
      try {
        for (sql <- Database.getSQL) {
          //println(s"$sql")
          try {
            val x = s.execute(sql)
            // println(s"$x")
            //println(s"${s.getUpdateCount()}")
          } catch {
            case ex: SQLException => //println(s"${ex.getSQLState}")
          }
        }
      } finally {
        s.close()
      }
    } finally {
      c.close()
    }

  }
  // scalastyle:on
}
