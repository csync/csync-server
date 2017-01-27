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

package com.ibm.csync.types

import scala.util.matching.Regex

/* Parts of a path */
sealed trait Part {
  val asString: String

  val mustBeLast = false
  val isBroken = false
}

object Part {
  val idRegEx: Regex = """(^[a-zA-Z0-9][a-zA-Z0-9\-_]*$)""".r

  def apply(s: String): Part = s match {
    case idRegEx(id) => Identifier(id)
    case "*" => Star
    case "#" => Pound
    case x => ResponseCode.InvalidPathFormat.throwIt(s)
  }

}

/* '#' */
case object Pound extends Part {
  val asString = "#"
  override val mustBeLast = true
}

/* '*' */
case object Star extends Part {
  val asString = "*"
}

case class Identifier(asString: String) extends Part

