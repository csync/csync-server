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

import com.ibm.csync.vertx.{Main, VertxContext}
import io.vertx.core.{AbstractVerticle, Context, Vertx}
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.time.{Milliseconds, Span}
import org.scalatest.{Matchers, PropSpec}

import scala.concurrent.Promise

class T1 extends PropSpec with Matchers with ScalaFutures {

  /*
  def show(vertx: Vertx): Unit = {
    val ctx = vertx.getOrCreateContext()
    println(s"$ctx")
    println(s"worker ${ctx.isWorkerContext}")
    println(s"eventloop ${ctx.isEventLoopContext}")
    println(s"id ${Thread.currentThread().getId()}")
    println(s"workerThread ${Context.isOnWorkerThread}")
    println(s"eventLoopThread ${Context.isOnEventLoopThread}")
    println(s"vertxThread ${Context.isOnVertxThread}")
    println(s"threadName ${Thread.currentThread().getName}")
  }
  */

  property("verticle") {
    //printf("**** starting test\n")
    val vertx = Vertx.vertx()
    implicit val ec = Main.currentThreadExecutionContext
    val promise = Promise[Int]

    vertx.deployVerticle(new AbstractVerticle {
      //printf("***** inside verticle\n")

      override def start() {
        //printf("****** hello\n")
        val topContext = vertx.getOrCreateContext
        val ctx = VertxContext(topContext)
        val topThread = Thread.currentThread

        val p1 = Promise[Int]

        topContext.isEventLoopContext should be(true)
        topContext.isWorkerContext should be(false)
        Context.isOnEventLoopThread should be(true)
        Context.isOnWorkerThread should be(false)
        Context.isOnVertxThread should be(true)

        val f = ctx.runBlocking {
          //printf("************ inside runBlocking\n")
          vertx.getOrCreateContext should be(topContext)
          Context.isOnEventLoopThread should be(false)
          Context.isOnWorkerThread should be(true)
          Context.isOnVertxThread should be(true)
          Thread.currentThread shouldNot be(topThread)
        }

        f.map { _ =>
          //printf("**** inside map\n")
          vertx.getOrCreateContext should be(topContext)
          Context.isOnEventLoopThread should be(false)
          Context.isOnWorkerThread should be(true)
          Context.isOnVertxThread should be(true)
          Thread.currentThread shouldNot be(topThread)

          topContext.runOnContext { _ =>
            //printf("*** inside runOnContext")
            vertx.getOrCreateContext should be(topContext)
            Context.isOnEventLoopThread should be(true)
            Context.isOnWorkerThread should be(false)
            Context.isOnVertxThread should be(true)
            Thread.currentThread should be(topThread)
            p1.success(1)
          }
        }

        p1.future.map { t =>
          vertx.getOrCreateContext should be(topContext)
          Context.isOnEventLoopThread should be(true)
          Context.isOnWorkerThread should be(false)
          Context.isOnVertxThread should be(true)
          Thread.currentThread should be(topThread)
          promise.success(t + 1)
        }
      }
    })

    //Thread.sleep(10000000)

    promise.future.futureValue(PatienceConfiguration.Timeout(Span(5000, Milliseconds))) should be(2)

  }
}
