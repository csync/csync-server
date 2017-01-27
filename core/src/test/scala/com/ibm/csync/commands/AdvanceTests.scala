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
import com.ibm.csync.types.{SessionId, Token}
import org.postgresql.ds.PGPoolingDataSource
import org.scalatest.{FunSuite, Matchers}
import scala.concurrent.Future

// scalastyle:off magic.number
class AdvanceTests extends FunSuite with Matchers {

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

  test("Test Simple Advance") {
    val session = fakeSession { _ => Future.successful(()) }
    try {
      Pub(99, Seq("a"), Some("x"), false, None, None).doit(session)
      Pub(100, Seq("a"), Some("y"), false, None, None).doit(session)
      Pub(101, Seq("a"), Some("yy"), false, None, None).doit(session)
      Pub(102, Seq("a"), Some("yyy"), false, None, None).doit(session)
      val lastVTS = Pub(103, Seq("a"), Some("yyyy"), false, None, None).doit(session).vts
      val advanceResponse = Advance(1, Seq("a")).doit(session)
      advanceResponse.maxvts should be(lastVTS)
      advanceResponse.vts.head should be(lastVTS)
      advanceResponse.vts.size should be(1)
    } finally {
      session.close()
    }
  }

  test("Test Advance with higher maxVTS then vts") {
    val session = fakeSession { _ => Future.successful(()) }
    try {
      Pub(99, Seq("a"), Some("x"), false, None, None).doit(session)
      Pub(100, Seq("a"), Some("y"), false, None, None).doit(session)
      Pub(101, Seq("a"), Some("yy"), false, None, None).doit(session)
      val lastAVTS = Pub(102, Seq("a"), Some("yyy"), false, None, None).doit(session).vts
      val maxVTS = Pub(103, Seq("b"), Some("yyyy"), false, None, None).doit(session).vts
      val advanceResponse = Advance(1, Seq("a")).doit(session)
      advanceResponse.maxvts should be(maxVTS)
      advanceResponse.vts.head should be(lastAVTS)
      advanceResponse.vts.size should be(1)
    } finally {
      session.close()
    }
  }

  test("Test Advance that reaches limit") {
    val session = fakeSession { _ => Future.successful(()) }
    try {
      Pub(99, Seq("a"), Some("x"), false, None, None).doit(session)
      Pub(100, Seq("b"), Some("y"), false, None, None).doit(session)
      Pub(101, Seq("c"), Some("yy"), false, None, None).doit(session)
      Pub(102, Seq("d"), Some("yyy"), false, None, None).doit(session)
      Pub(103, Seq("e"), Some("yyyy"), false, None, None).doit(session)
      Pub(104, Seq("f"), Some("yyyyy"), false, None, None).doit(session)
      Pub(105, Seq("g"), Some("yyyyyy"), false, None, None).doit(session)
      Pub(106, Seq("h"), Some("yyyyyyy"), false, None, None).doit(session)
      Pub(107, Seq("j"), Some("yyyyyyyy"), false, None, None).doit(session)
      val tenthPubVTS = Pub(108, Seq("k"), Some("yyyyyyyyy"), false, None, None).doit(session).vts
      Pub(109, Seq("l"), Some("yyyyyyyyyy"), false, None, None).doit(session)
      val advanceResponse = Advance(1, Seq("*")).doit(session)
      //vts starts at 1, so 10 entries should get us to 11
      advanceResponse.maxvts should be(tenthPubVTS)
      //Max advance return is 10
      advanceResponse.vts.size should be(10)
    } finally {
      session.close()
    }
  }
  //TODO Add advance tests for changed ACLs, updates and deletes
  // scalastyle:on magic.number
}
