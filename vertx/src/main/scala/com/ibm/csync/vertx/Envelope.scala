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

import com.ibm.csync.commands
import com.ibm.csync.commands._
import org.json4s.{Extraction, JValue, NoTypeHints}
import org.json4s.native.Serialization

class Formats {
  implicit protected val format = Serialization.formats(NoTypeHints)
}

case class ResponseEnvelope(kind: String, closure: Option[JValue], payload: JValue,
    version: Int = commands.MESSAGE_VERSION) extends Formats {
  lazy val asString: String = Serialization.write(this)
}

object ResponseEnvelope extends Formats {
  def apply(closure: Option[JValue], msg: Response): ResponseEnvelope = {
    val asJson = Extraction.decompose(msg)
    ResponseEnvelope(msg.kind, closure, asJson)
  }
}

case class RequestEnvelope(version: Option[Int], kind: String,
    closure: Option[JValue], payload: JValue) extends Formats {

  def check(): RequestEnvelope = {
    version match {
      case None =>
        throw new Exception("missing version")
      case Some(x) =>
        if (x != commands.MESSAGE_VERSION) {
          throw new Exception(s"bad version $x != ${commands.MESSAGE_VERSION}")
        }
    }
    this
  }

  lazy val asRequest: Command = kind match {
    case "pub" => Extraction.extract[Pub](payload)
    case "advance" => Extraction.extract[Advance](payload)
    case "sub" => Extraction.extract[Sub](payload)
    case "unsub" => Extraction.extract[Unsub](payload)
    case "fetch" => Extraction.extract[Fetch](payload)
    case _ => throw new Exception(s"unknown kind $kind")
  }
}

object RequestEnvelope extends Formats {

  def apply(s: String): RequestEnvelope = Serialization.read[RequestEnvelope](s).check()

}