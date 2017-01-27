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

package com.ibm.csync

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object Utils {

  def using[T <: AutoCloseable, U](resource: T)(f: T => U): U = {
    try {
      f(resource)
    } finally {
      resource.close()
    }
  }

  def attempt[T](n: Int)(f: => Try[T]): Try[T] = {
    @tailrec
    def loop(count: Int, last: Option[Try[T]]): Try[T] =
      if (count < 1) {
        last.getOrElse(Failure(new IllegalArgumentException(s"n = $n")))
      } else {
        f match {
          case x @ Success(_) => x
          case x @ Failure(_) => loop(count - 1, Some(x))
        }
      }

    loop(n, None)
  }

  def optionToFuture[T](option: Option[T], e: => Throwable): Future[T] =
    option match {
      case Some(data) => Future.successful(data)
      case None => Future.failed(e)
    }

}

