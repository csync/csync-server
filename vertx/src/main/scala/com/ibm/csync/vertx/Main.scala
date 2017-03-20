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
import java.nio.file.{Paths, Files}
import javax.sql.DataSource

import com.ibm.csync.commands.{Happy, Response}
import com.ibm.csync.database.Database
import com.ibm.csync.rabbitmq.Factory
import com.ibm.csync.session.Session
import com.ibm.csync.types.{ClientError, SessionId, Token}
import com.rabbitmq.client.Connection
import com.typesafe.scalalogging.LazyLogging
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.vertx.core._
import io.vertx.core.http._
import io.vertx.core.net.PemKeyCertOptions
import org.json4s.JValue
import org.postgresql.ds.PGSimpleDataSource
import com.ibm.bluemix.deploymenttracker.client.CFJavaTrackerClient
import com.ibm.json.java.{JSON, JSONArtifact, JSONObject}

import scala.concurrent.{ExecutionContext, Future, Promise}

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

  // Called once per web socket connect, runs for its side effects
  def handleConnect(ctx: VertxContext, request: HttpServerRequest, ds: DataSource,
    rabbitConnection: Connection): Future[_] = {
    val ws = request.upgrade()
    ws.pause // until we know what to do with incoming messages
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
          ctx.runEventLoop { ws.writeFinalTextFrame(js) }
          // TODO: close socket
        } recover {
          case e =>
            logger.error(s"write to $sessionId failed", e)
        }
      }
    } flatMap { session =>
      state.value.setSession(state, session)
    } flatMap { _ =>
      ctx.runEventLoop { ws.resume }
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

    val f: File = new File("public/package.json")
    if (f.exists()) {
      val is = new FileInputStream("public/package.json")
      val json: JSONArtifact = JSON.parse(is, true)
      logger.info(json.toString)
      val client = new CFJavaTrackerClient().track(json.asInstanceOf[JSONObject])
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
              request.response.sendFile(s"public/dataviewer/$f", promiseHandler(p))
              p.future
            } else {
              Future.successful(None)
            }
          }

          (request.path() match {
            case "/connect" => handleConnect(ctx, request, ds, rabbitConnection)
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
