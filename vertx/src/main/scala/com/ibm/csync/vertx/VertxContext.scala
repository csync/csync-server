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

import io.vertx.core.Context

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

case class VertxContext(ctx: Context) {

  def runBlocking[A](f: => A): Future[A] = {
    if (Context.isOnWorkerThread) {
      Future.fromTry(Try(f))
    } else {
      val p = Promise[A]
      ctx.executeBlocking[A](
        (_: io.vertx.core.Future[A]) => {
          Try(f) match {
            case Success(a) => p.success(a)
            case Failure(e) => p.failure(e)
          }
          ()
        },
        null
      )
      p.future
    }
  }

  def runEventLoop[A](f: => A): Future[A] = {
    if (Context.isOnEventLoopThread) {
      Future.fromTry(Try(f))
    } else {
      val p = Promise[A]
      ctx.runOnContext { _ =>
        Try(f) match {
          case Success(a) => p.success(a)
          case Failure(e) => p.failure(e)
        }
      }
      p.future
    }
  }

}
