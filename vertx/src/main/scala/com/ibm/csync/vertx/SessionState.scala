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

import com.ibm.csync.session.Session
import com.typesafe.scalalogging.LazyLogging
import io.vertx.core.http.{ServerWebSocket, WebSocketFrame}

import scala.concurrent.{ExecutionContext, Future}

trait SessionState extends LazyLogging {
  def closeHandler(ref: SessionState.Ref): Future[_]
  def onFrame(ref: SessionState.Ref, frame: WebSocketFrame)(cb: (Session, String) => Any)
  def close(ref: SessionState.Ref): Future[_]
  def setSession(ref: SessionState.Ref, session: Session): Future[_]
}

object SessionState extends LazyLogging {

  class Ref(init: SessionState) {
    var value: SessionState = init
  }

  ///////////////////////////
  // No session, no socket //
  ///////////////////////////

  object HasNothing extends SessionState {
    override def closeHandler(ref: Ref): Future[_] = Future.successful(())

    override def onFrame(ref: Ref, frame: WebSocketFrame)(cb: (Session, String) => Any): Unit = {
      logger.error(s"dropping frame")
    }

    override def close(ref: Ref): Future[_] =
      Future.successful(())

    // TODO: just report
    override def setSession(ref: Ref, session: Session): Future[_] =
      Future.failed(new IllegalStateException("session not expected"))
  }

  ////////////////////////
  // No session, socket //
  ////////////////////////

  class HasSocket(ctx: VertxContext, val ws: ServerWebSocket) extends SessionState {
    override def closeHandler(ref: Ref): Future[_] = ctx.runEventLoop {
      ref.value = HasNothing
    }

    override def onFrame(ref: Ref, frame: WebSocketFrame)(cb: (Session, String) => Any): Unit = {
      logger.debug(s"dropping frame")
    }

    override def close(ref: Ref): Future[_] =
      ctx.runEventLoop {
        ref.value = HasNothing
        ws.close()
      }

    override def setSession(ref: Ref, session: Session): Future[_] = ctx.runEventLoop {
      ref.value = new HasSession(ctx, ws, session)
    }
  }

  ////////////////////////
  // Session and socket //
  ////////////////////////

  class HasSession(ctx: VertxContext, ws: ServerWebSocket, session: Session) extends SessionState {
    var message = new StringBuilder

    implicit val ec: ExecutionContext = Main.currentThreadExecutionContext

    override def closeHandler(ref: Ref): Future[_] =
      ctx.runEventLoop {
        ref.value = HasNothing
      } flatMap { _ =>
        ctx.runBlocking {
          logger.error(s"closing session")
          session.close()
        }
      }

    override def onFrame(ref: Ref, frame: WebSocketFrame)(cb: (Session, String) => Any): Unit = {
      val s = if (frame.isText) frame.textData else new String(frame.binaryData.getBytes)
      message.append(s)
      if (frame.isFinal) {
        val x = message.toString
        message = new StringBuilder
        cb(session, x)
      }
    }

    override def close(ref: Ref): Future[_] = {
      //logger.error(s"closing session $session")
      //logger.info(s"closing session $session")
      ctx.runEventLoop {
        ref.value = HasNothing
      } flatMap { _ =>
        ctx.runBlocking {
          session.close()
        }
      } recover {
        case _ => ()
      } flatMap { _ =>
        ctx.runEventLoop {
          ws.close()
        }
      }
    }

    override def setSession(ref: Ref, session: Session): Nothing =
      throw new IllegalStateException("session not expected")
  }
}