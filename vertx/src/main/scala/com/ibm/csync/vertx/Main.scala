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

package com.ibm.csync.vertx

import java.io.{File, FileInputStream}
import java.net.URLDecoder
import java.nio.file.{Files, Paths}
import javax.sql.DataSource

import com.ibm.bluemix.deploymenttracker.client.CFJavaTrackerClient
import com.ibm.csync.commands._
import com.ibm.csync.database.Database
import com.ibm.csync.rabbitmq.Factory
import com.ibm.csync.session.{Session, UserInfo}
import com.ibm.csync.types.ResponseCode.InvalidSchemaJSON
import com.ibm.csync.types.{ClientError, SessionId, Token}
import com.ibm.json.java.{JSON, JSONArtifact, JSONObject}
import com.rabbitmq.client.Connection
import com.typesafe.scalalogging.LazyLogging
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.vertx.core._
import io.vertx.core.http._
import io.vertx.core.net.PemKeyCertOptions
import org.json4s._
import org.json4s.native.Serialization
import org.postgresql.ds.PGSimpleDataSource

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.sys.process.Process

object Main extends LazyLogging {

  def promiseHandler[T](p: Promise[T]): Handler[AsyncResult[T]] =
    e => if (e.succeeded()) p.success(e.result()) else p.failure(e.cause())

  implicit val currentThreadExecutionContext = new ExecutionContext {

    override def reportFailure(cause: Throwable): Unit = {
      logger.error("ec", cause)
    }

    override def execute(runnable: Runnable): Unit = {
      runnable.run()
    }
  }

  var CSyncUUID: String = ""

  def sendResponse(ctx: VertxContext, closure: Option[JValue], res: Response, ws: ServerWebSocket): Future[_] =
    closure match {
      case Some(_) =>
        val s = ResponseEnvelope(closure, res).asString
        logger.debug(s"sending reply $s")
        ctx.runEventLoop {
          ws.writeFinalTextFrame(s)
        }
      case None => Future.successful(())
    }

  def handleFrame(ctx: VertxContext, frame: WebSocketFrame, state: SessionState.Ref, ws: ServerWebSocket): Unit =
    state.value.onFrame(state, frame) { (session, msg) =>
      logger.debug(s"request for ${session.sessionId}")
      Future {
        RequestEnvelope(msg)
      } flatMap { env =>
        ctx.runBlocking {
          env.asRequest.doit(session)
        } recover {
          case ClientError(code, err) =>
            Happy(code.id, err.getOrElse(code.name))
        } flatMap { r =>
          sendResponse(ctx, env.closure, r, ws)
        }
      } recoverWith {
        case e =>
          logger.error("", e)
          state.value.close(state)
      } recover {
        case e =>
          logger.error("", e)
      }
    }

  def handleRestCalls(ctx: VertxContext, request: HttpServerRequest, dataSource: DataSource,
    rabbitConnection: Connection, body: String): Future[_] = {

    ctx.runBlocking {

      val provider = Option(request.getParam("authProvider"))
      val token = Token(request.getParam("token"))
      var userInfo: UserInfo = null
      try {
        userInfo = Session.auth(token, provider, logger)
      } catch {
        case e: ClientError =>
          logger.error(e.toString)
          request.response().putHeader("Content-Type", "application/json")
          request.response().setStatusCode(400).end("{\"error\" : \"" + e.code.name + "\"}")
          Future.failed(e)
      }

      implicit val formats = DefaultFormats + FieldSerializer[PubResponse]() + FieldSerializer[FetchResponse]() + FieldSerializer[Data]()
      if (request.method().equals(HttpMethod.POST)) {

        try {
          val pub = RequestEnvelope(body).asRequest.asInstanceOf[Pub]
          val pubResponse = pub.doit(rabbitConnection, dataSource, userInfo)
          request.response().putHeader("Content-Type", "application/json")
          request.response().end(Serialization.write(pubResponse))
          Future.successful(pubResponse)
        } catch {
          case e: ClientError =>
            logger.error(e.toString)
            request.response().putHeader("Content-Type", "application/json")
            request.response().setStatusCode(400).end("{\"error\" : \"" + e.code.name + "\"}")
            Future.failed(e)
          case e: Exception =>
            logger.error(e.toString)
            val error = ClientError(InvalidSchemaJSON, None)
            request.response().putHeader("Content-Type", "application/json")
            request.response().setStatusCode(400).end("{\"error\" : \"" + error.code.name + "\"}")
            Future.failed(e)
        }

      } else if (request.method().equals(HttpMethod.GET)) {

        if (!request.params().contains("path")) {
          request.response().putHeader("Content-Type", "application/json")
          request.response().setStatusCode(400).end("{\"error\" : \"path is required\"}")
        }
        val path = URLDecoder.decode(request.getParam("path"), "UTF-8").split('.')

        try {
          val fetchResponse = Fetch.fetchRest(dataSource, userInfo, path)
          request.response().putHeader("Content-Type", "application/json")
          request.response.end(Serialization.write(fetchResponse))

        } catch {
          case e: ClientError =>
            logger.error(e.toString)
            request.response().putHeader("Content-Type", "application/json")
            request.response().setStatusCode(400).end("{\"error\" : \"" + e.code.name + "\"}")
            Future.failed(e)
        }
      }
    }

    Future.successful(None)
  }

  // Called once per web socket connect, runs for its side effects
  def handleConnect(ctx: VertxContext, request: HttpServerRequest, ds: DataSource,
    rabbitConnection: Connection): Future[_] = {
    val ws = request.upgrade()
    ws.pause
    // until we know what to do with incoming messages
    val state = new SessionState.Ref(new SessionState.HasSocket(ctx, ws))
    logger.info(s"WebSocket connection ${ws.uri()}")

    ws.closeHandler { _ =>
      state.value.closeHandler(state).recover {
        case e =>
          logger.error("close", e)
      }
    }

    ws.frameHandler { frame =>
      handleFrame(ctx, frame, state, ws)
    }

    ctx.runBlocking {
      val provider = Option(request.getParam("authProvider"))
      val token = Token(request.getParam("token"))
      val sessionId = SessionId(request.getParam("sessionId"))
      Session(ds, CSyncUUID, rabbitConnection, provider, token, sessionId) { outgoing =>
        Future {
          ResponseEnvelope(None, outgoing).asString
        } flatMap { js =>
          logger.debug(s"sending back $js")
          ctx.runEventLoop {
            ws.writeFinalTextFrame(js)
          }
          // TODO: close socket
        } recover {
          case e =>
            logger.error(s"write to $sessionId failed", e)
        }
      }
    } flatMap { session =>
      state.value.setSession(state, session)
    } flatMap { _ =>
      ctx.runEventLoop {
        ws.resume
      }
    } recoverWith {
      case e =>
        logger.error("", e)
        state.value.close(state)
    } recover {
      case e =>
        logger.error("", e)
    }
  }

  private def initPostgres: DataSource = {
    // Postgres settings
    val pgt = new PGSimpleDataSource()
    if (sys.env.get("CSYNC_DB_STRING").isEmpty) {
      pgt.setServerName("localhost")
    } else {
      pgt.setUrl(sys.env("CSYNC_DB_STRING"))
    }

    val hikariConfig = new HikariConfig()
    hikariConfig.setDataSource(pgt)
    hikariConfig.setAutoCommit(false)
    new HikariDataSource(hikariConfig)
  }

  private def initRabbit: Connection = {
    // RabbitMQ settings
    val rabbitUri = sys.env.getOrElse("CSYNC_RABBITMQ_URI", "amqp://guest:guest@localhost:5672/csync")

    val rabbitFactory = Factory(rabbitUri)

    rabbitFactory.newConnection
  }

  def main(args: Array[String]) {
    System.setProperty(
      "vertx.logger-delegate-factory-class-name",
      classOf[io.vertx.core.logging.SLF4JLogDelegateFactory].getName
    )
    val vertx = Vertx.vertx()
    val ds = initPostgres
    val rabbitConnection = initRabbit
    val port = sys.env.getOrElse("CSYNC_PORT", "6005")
    val serverOptions = if (Files.exists(Paths.get("/certs/privkey.pem")) && Files.exists(Paths.get("/certs/cert.pem"))) {
      new HttpServerOptions()
        .setPort(port.toInt)
        .setSsl(true)
        .setPemKeyCertOptions(new PemKeyCertOptions().setKeyPath("/certs/privkey.pem").setCertPath("/certs/cert.pem"))
    } else {
      new HttpServerOptions().setPort(port.toInt)
    }

    val groupId = sys.env.getOrElse("group_id", "")
    val uuid = sys.env.getOrElse("uuid", "")
    val space_id = sys.env.getOrElse("space_id", "")
    val image_id = sys.env.getOrElse("Image_id", "")
    val memory_limit = (Process("cat /sys/fs/cgroup/memory/memory.limit_in_bytes").!!.toString.trim().toLong / 1024 ^ 3).toString
    logger.info(memory_limit)

    if (!space_id.isEmpty) {
      val httpClient = vertx.createHttpClient()
      httpClient.getNow(443, "google-analytics.com",
        "/collect?v=1&tid=UA-91208269-3&cid=555&t=event&ec=createdBluemixInstance&ea=forCSync&cd1=" + uuid + "&cd2=" + space_id + "&cd3=" + groupId + "&cd4=" + image_id + "&cd5=" + memory_limit,
        (event: HttpClientResponse) => logger.debug("response from GA: " + event.statusCode()))

      val f: File = new File("public/package.json")
      if (f.exists()) {
        val is = new FileInputStream("public/package.json")
        val json: JSONArtifact = JSON.parse(is, true)
        logger.info(json.toString)
        val client = new CFJavaTrackerClient().track(json.asInstanceOf[JSONObject])
      }

    }

    def loop(n: Int): Future[_] =
      if (n <= 0) {
        Future.successful(())
      } else {
        deploy(vertx, ds, rabbitConnection, serverOptions).flatMap { s =>
          logger.info(s"# $n listening on port ${s.actualPort()}")
          loop(n - 1)
        }
      }

    // TODO: magic number
    val nThreads = 10

    Future {
      CSyncUUID = Database.createTables(ds)
    } flatMap { _ =>
      loop(nThreads)
    } recover {
      case e =>
        logger.error("initialization error", e)
        vertx.close()
    } recover {
      case e =>
        logger.error("", e)
    }
  }

  private def deploy(vertx: Vertx, ds: DataSource, rabbitConnection: Connection,
    serverOptions: HttpServerOptions): Future[HttpServer] = {
    val out = Promise[HttpServer]

    vertx.deployVerticle(new AbstractVerticle {
      override def start(): Unit = {
        val ctx = VertxContext(vertx.getOrCreateContext())
        val server = vertx.createHttpServer(serverOptions)
        val disableDataviewer = sys.env.getOrElse("DISABLE_DATAVIEWER", "false")

        server.requestHandler { request =>
          def send(f: String) = {
            if (!disableDataviewer.toBoolean) {
              val p = Promise[Void]
              if (Files.exists(Paths.get(s"public/dataviewer/$f"))) {
                request.response.sendFile(s"public/dataviewer/$f", promiseHandler(p))
              } else {
                request.response.setStatusCode(404)
                request.response.end()
              }
              p.future
            } else {
              request.response.setStatusCode(404)
              request.response.end()
              Future.successful(None)
            }
          }

          (request.path() match {
            case "/connect" => handleConnect(ctx, request, ds, rabbitConnection)
            case "/key" =>
              if (request.method().equals(HttpMethod.POST)) {

                request.bodyHandler((totalBuffer: io.vertx.core.buffer.Buffer) => {

                  handleRestCalls(ctx, request, ds, rabbitConnection, totalBuffer.toString())
                })
                Future.successful(None)
              } else if (request.method().equals(HttpMethod.GET)) {
                handleRestCalls(ctx, request, ds, rabbitConnection, "")
              } else Future.successful(None)
            case "/" => send("index.html")

            // TODO: think about security
            case x if x.contains("..") => send("")
            case x => send(x)
          }) recover {
            case e =>
              logger.error("request", e)
          }

        }

        server.listen(promiseHandler(out))
      }
    })

    out.future
  }
}
