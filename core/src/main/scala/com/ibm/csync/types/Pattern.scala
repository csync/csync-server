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

import scala.collection.mutable

/* Key with wildcards */
class Pattern(parts: Seq[Part]) {
  lazy val asString: String = (parts map { _.asString }).mkString(".")
  lazy val asStrings: Seq[String] = parts.map { _.asString }
  override def toString: String = asString

  lazy val asWhere: (String, Seq[String]) = {
    val terms = mutable.Buffer[String]()
    val vals = mutable.Buffer[String]()

    var i = 0
    var lastIsStar = false
    var isStrict = true
    var lastStarIndex = -1
    val n = parts.length
    for (p <- parts) {
      p match {
        case Star =>
          lastIsStar = true
          lastStarIndex = i
        case Pound =>
          isStrict = false
        case Identifier(id) =>
          lastIsStar = false
          terms.append(s"key$i = ?")
          vals.append(id)
      }
      i += 1
    }

    if (lastIsStar) {
      terms.append(s"key$lastStarIndex is not null")
    }

    if (isStrict) {
      if (n < Pattern.MAX_PARTS) {
        terms.append(s"key$n is null")
      }
    }

    (terms.mkString(" AND "), vals.asInstanceOf[Seq[String]])
  }
}

object Pattern {

  val MAX_LENGTH = 200
  val MAX_PARTS = 16

  def apply[T](parts: Seq[String]): Pattern = {
    val len = parts.length
    val totalLen = parts.map { _.length }.sum + (len - 1)
    if (len == 0) {
      throw new IllegalArgumentException("no parts")
    }
    if (len > MAX_PARTS) {
      throw new IllegalArgumentException(s"too many parts $len")
    }
    if (totalLen > MAX_LENGTH) {
      throw new IllegalArgumentException(s"too long $totalLen")
    }

    val partsSeq = parts map { Part(_) }
    new Pattern(partsSeq)
  }

}
