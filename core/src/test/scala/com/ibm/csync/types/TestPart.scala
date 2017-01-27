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

import org.scalacheck.{Gen}
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, PropSpec}

import scala.util.{Success, Try}

object TestPart {
  // scalastyle:off magic.number
  val idInsideGen = Gen.listOf(
    Gen.frequency(
      2 -> Gen.numChar,
      5 -> Gen.alphaLowerChar,
      4 -> Gen.alphaUpperChar,
      1 -> Gen.const('-'),
      1 -> Gen.const('_')
    )
  )

  val idGen = for (
    first <- Gen.alphaLowerChar;
    rest <- idInsideGen
  ) yield (first +: rest).mkString

  val idPartGen = idGen map { id => Identifier(id) }

  val innerGen = Gen.frequency(
    6 -> idPartGen,
    2 -> Star
  )

  val partGen = Gen.frequency(
    10 -> innerGen,
    1 -> Pound
  )

}

class TestPart extends PropSpec with PropertyChecks with Matchers {

  import TestPart._

  property("basic parts") {
    Star.asString should be("*")
    Pound.asString should be("#")
    forAll { (s: String) =>
      Identifier(s).asString should be(s)
    }
  }

  property("parse parts") {

    Seq("zebra", "Zebra", "aA-", "aA0_-") foreach { x =>
      Try(Part(x)) should be(Success(Identifier(x)))
    }

    noException should be thrownBy {
      Part("*") should be(Star)
      Part("#") should be(Pound)
    }

    Seq("?", "**", "abc^", "A b", "a*", "b#") foreach { x =>
      val e = the[ClientError] thrownBy {
        Part(x)
      }
      e.code should be(ResponseCode.InvalidPathFormat)
      e.msg should be(Some(x))
    }

    noException should be thrownBy {
      forAll(idGen) { p =>
        Part(p) should be(Identifier(p))
      }
    }
  }

  property("random parts") {
    forAll(partGen) { p =>
      p should be(Part(p.asString))
    }
  }
  // scalastyle:on magic.number
}
