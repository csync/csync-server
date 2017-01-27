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

class Key private (val parts: Seq[Identifier]) {
  lazy val asString: String = parts.map { _.asString }.mkString(".")
  lazy val asStrings: Seq[String] = parts.map { _.asString }

  lazy val parent = new Key(parts.take(parts.length - 1))

  override def toString: String = s"Key($asString)"
  override def hashCode: Int = asString.hashCode
  override def equals(other: Any): Boolean = other match {
    case o: Key => asString == o.asString
    case _ => false
  }
}

object Key {

  def apply(parts: Seq[String]): Key = {
    val len = parts.length
    val totalLen = parts.map { _.length }.sum + (len - 1)
    if (len == 0) {
      ResponseCode.InvalidPathFormat.throwIt("no parts")
    }
    if (len > 16) {
      ResponseCode.InvalidPathFormat.throwIt(s"too many parts $len in ${parts.mkString(".")}")
    }
    if (totalLen > 200) {
      ResponseCode.InvalidPathFormat.throwIt(s"too long $totalLen ${parts.mkString(".")}")
    }
    val partsSeq = parts map { Part(_) }
    new Key(partsSeq.collect { case x @ Identifier(_) => x })

  }

  def apply(part: String, parts: String*): Key = apply(part +: parts)

}
