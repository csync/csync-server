/*
 * Copyright IBM Corporation 2017
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
import com.ibm.csync.types.{Key, SessionId, Token}
import org.postgresql.ds.PGPoolingDataSource
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSuite, Matchers}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}

class SubTests extends FunSuite with Matchers with ScalaFutures {
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

  test("Sub on a wildcard at the end") {

    val promise = Promise[Map[Key, Data]]()
    val responseData = mutable.Map[Key, Data]()
    val session = fakeSession { outgoing =>
      outgoing match {
        case d: Data =>
          val key = Key(d.path)
          responseData(key) = d
          if (responseData.keySet.size == 2) {
            promise.success(responseData.toMap)
          }
        case _ =>
      }
      Future.successful(())
    }
    try {
      Sub(Seq("a", "*")).doit(session)
      Pub(99, Seq("a"), Some("x"), false, None, None).doit(session)
      val firstPubResponse = Pub(100, Seq("a", "b"), Some("y"), false, None, None).doit(session)
      val secondPubResponse = Pub(101, Seq("a", "c"), Some("z"), false, None, None).doit(session)
      val res = promise.future.futureValue
      val firstKey = res(Key("a", "b"))
      val secondKey = res(Key("a", "c"))

      firstKey.cts should be(firstPubResponse.cts)
      firstKey.vts should be(firstPubResponse.vts)
      firstKey.data should be(Some("y"))
      firstKey.creator should be("demoUser")
      firstKey.acl should be("$publicCreate")
      firstKey.deletePath should be(false)

      secondKey.cts should be(secondPubResponse.cts)
      secondKey.vts should be(secondPubResponse.vts)
      secondKey.data should be(Some("z"))
      secondKey.creator should be("demoUser")
      secondKey.acl should be("$publicCreate")
      secondKey.deletePath should be(false)
    } finally {
      session.close()
    }
  }

  test("Sub on a wildcard at the beginning") {

    val promise = Promise[Map[Key, Data]]()
    val responseData = mutable.Map[Key, Data]()
    val session = fakeSession { outgoing =>
      outgoing match {
        case d: Data =>
          val key = Key(d.path)
          responseData(key) = d
          if (responseData.keySet.size == 2) {
            promise.success(responseData.toMap)
          }
        case _ =>
      }
      Future.successful(())
    }
    try {
      Sub(Seq("*", "d")).doit(session)
      Pub(106, Seq("g", "d", "e"), Some("z"), false, None, None).doit(session)
      Pub(107, Seq("c", "f", "d"), Some("z"), false, None, None).doit(session)
      Pub(102, Seq("c"), Some("x"), false, None, None).doit(session)
      val firstPubResponse = Pub(103, Seq("c", "d"), Some("y"), false, None, None).doit(session)
      Pub(104, Seq("e"), Some("x"), false, None, None).doit(session)
      val secondPubResponse = Pub(105, Seq("f", "d"), Some("z"), false, None, None).doit(session)

      val res = promise.future.futureValue
      val firstKey = res(Key("c", "d"))
      val secondKey = res(Key("f", "d"))

      firstKey.cts should be(firstPubResponse.cts)
      firstKey.vts should be(firstPubResponse.vts)
      firstKey.data should be(Some("y"))
      firstKey.creator should be("demoUser")
      firstKey.acl should be("$publicCreate")
      firstKey.deletePath should be(false)

      secondKey.cts should be(secondPubResponse.cts)
      secondKey.vts should be(secondPubResponse.vts)
      secondKey.data should be(Some("z"))
      secondKey.creator should be("demoUser")
      secondKey.acl should be("$publicCreate")
      secondKey.deletePath should be(false)
    } finally {
      session.close()
    }
  }
  // scalastyle:on magic.number
}
