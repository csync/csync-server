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

import com.ibm.csync.database.Database
import com.ibm.csync.rabbitmq.Factory
import com.ibm.csync.session.Session
import com.ibm.csync.types.{SessionId, Token}
import org.postgresql.ds.PGPoolingDataSource
import org.scalatest.{FunSuite, Matchers}
import scala.concurrent.Future

class FetchTests extends FunSuite with Matchers {
  // scalastyle:off magic.number
  def fakeSession(f: Response => Future[_]): Session = {

    val ds = new PGPoolingDataSource()
    ds.setCurrentSchema("pg_temp")
    ds.setServerName("localhost")
    ds.setMaxConnections(1)
    Database.createTables(ds)

    val rabbit = Factory("amqp://guest:guest@localhost:5672/testing").newConnection

    Session(ds, "", rabbit, Some("demo"),
      Token("demoToken"), SessionId())(f)
  }

  test("Fetch a Single Node") {
    val session = fakeSession { _ => Future.successful(()) }
    try {
      //Setup tests by publishing a key and getting it.
      val pubResponse = Pub(102, Seq("d"), Some("x"), false, None, None).doit(session)

      //Check to be sure fetch is the key we published
      val fetchResponse = Fetch(null,List(2)).doit(session)
      fetchResponse.response.head.vts should be(pubResponse.vts)
      fetchResponse.response.head.cts should be(pubResponse.cts)
      fetchResponse.response.head.data should be(Some("x"))
      fetchResponse.response.head.creator should be("demoUser")
      fetchResponse.response.head.acl should be("$publicCreate")
    } finally {
      session.close()
    }
  }

  test("Fetch a non existant node") {
    val session = fakeSession { _ => Future.successful(()) }
    try {
      //Check to be sure no fetch exists
      val fetchResponse = Fetch(null,List(2)).doit(session)
      fetchResponse.response.size should be(0)
    } finally {
      session.close()
    }
  }

  test("Both fetches are nuill") {
    val session = fakeSession { _ => Future.successful(()) }
    try {
      //Check to be sure no fetch exists
      val fetchResponse = Fetch(null,null).doit(session)
      fetchResponse.response.size should be(0)
    } finally {
      session.close()
    }
  }

  //TODO Add fetche tests for changed ACLs, updates and deletes
  // scalastyle:on magic.number
}
