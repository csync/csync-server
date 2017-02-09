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

package com.ibm.csync.session

import java.nio.ByteBuffer
import java.sql.{Connection => SqlConnection}
import javax.sql.DataSource

import com.ibm.csync.auth.demo.ValidateDemoToken
import com.ibm.csync.auth.facebook.ValidateFacebookToken
import com.ibm.csync.auth.github.ValidateGitHubToken
import com.ibm.csync.auth.google.ValidateGoogleToken
import com.ibm.csync.commands.{ConnectResponse, Data, Err, Response}
import com.ibm.csync.database.SqlStatement
import com.ibm.csync.rabbitmq.{ExchangeInfo, QueueInfo, RoutingKey}
import com.ibm.csync.types.{Pattern, SessionId, Token}
import com.rabbitmq.client.AMQP.Queue.{BindOk, UnbindOk}
import com.rabbitmq.client.{AMQP, DefaultConsumer, Envelope, Connection => RabbitConnection}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.util.matching.Regex

object Session {
  val DemoAuthProvider = "demo"
  val GoogleAuthProvider = "google"
  val GithubAuthProvider = "github"
  val FacebookAuthProvider = "facebook"

  val demoToken: String = """demoToken"""
  val userToken: Regex = """demoToken\((.*)\)""".r

  val masterExchangeInfo = ExchangeInfo("master")
  def userExchangeInfo(userInfo: UserInfo): ExchangeInfo = masterExchangeInfo.id(userInfo.userId)
  def sessionQueueInfo(sessionId: SessionId): QueueInfo = QueueInfo(id = sessionId.id)
    .messageTTL(Constants.MESSAGE_TTL).queueTTL(Constants.QUEUE_TTL)
}

case class Session(ds: DataSource, uuid: String,
    connection: RabbitConnection,
    authProvider: Option[String],
    token: Token,
    sessionId: SessionId)(outgoing: Response => Future[_]) extends LazyLogging { session =>

  import Session._

  logger.info(s"session constructor $sessionId using $token")

  private val ch = connection.createChannel()

  val userInfo: UserInfo = try {
    authProvider match {
      case Some(GoogleAuthProvider) => ValidateGoogleToken.validate(token.s).get
      case Some(GithubAuthProvider) => ValidateGitHubToken.validate(token.s)
      case Some(FacebookAuthProvider) => ValidateFacebookToken.validate(token.s)
      case Some(DemoAuthProvider) | None => ValidateDemoToken.validate(token.s)
      case Some(unknownProvider) =>
        logger.info(s"[validateToken]: Unknown provider ${'\"'}$unknownProvider${'\"'}")
        throw new Exception("Cannot establish session. Token validation failed - unknown provider")
    }
  } catch {
    case ex: Exception =>
      outgoing(Err(msg = ex.getMessage, cause = None))
      throw ex
  }

  private val canRead = transaction { sqlConnection =>
    Seq("$world", userInfo.userId) ++ SqlStatement.queryResult(
      sqlConnection,
      "select groupid from membership where userid = ?", Seq(userInfo.userId)
    ) { rs => rs.getString(1) }
  }

  private val mx = masterExchangeInfo.declare(ch)
  private val ux = userExchangeInfo(userInfo).declare(ch)

  canRead foreach { g =>
    mx.bindTo(ux, RoutingKey(g, "#"))
  }

  private val uq = sessionQueueInfo(sessionId).declare(ch)

  private val tag = ch.basicConsume(uq.info.name, true, new DefaultConsumer(ch) {
    override def handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties,
      body: Array[Byte]): Unit = {
      import boopickle.Default._

      val res = Unpickle[Data].fromBytes(ByteBuffer.wrap(body))
      outgoing(res)
    }
  })

  outgoing(ConnectResponse(uuid = uuid, uid = userInfo.userId, expires = userInfo.expires))

  def subscribe(pattern: Pattern): BindOk =
    ch.queueBind(uq.info.name, ux.info.name, "*." + pattern.asString)

  def unsubscribe(pattern: Pattern): UnbindOk =
    ch.queueUnbind(uq.info.name, ux.info.name, "*." + pattern.asString)

  def send(group: String, key: Seq[String], data: Array[Byte]): Unit = {
    val rk = group + "." + key.mkString(".")
    ch.basicPublish(mx.info.name, rk, com.ibm.csync.rabbitmq.Constants.basicProperties, data)
  }

  def close(): Unit = {
    try {
      logger.debug(s"closing session $sessionId, tag $tag")
      ch.close()
    } catch {
      case e: Throwable => logger.error(s"cancel for session $sessionId", e)
    }
  }

  def transaction[T](f: SqlConnection => T): T = {
    val c = ds.getConnection
    try {
      c.setAutoCommit(false)
      val x = f(c)
      c.commit()
      x
    } catch {
      case e: Throwable =>
        c.rollback()
        throw e
    } finally {
      c.close()
    }
  }

}
