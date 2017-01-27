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

import org.scalacheck.Gen
import org.scalatest.{Matchers, PropSpec}
import org.scalatest.prop.PropertyChecks

import scala.util.{Failure, Success, Try}

case class PatternInfo(vals: Seq[Option[String]], isStrict: Boolean) {

}
// scalastyle:off magic.number null
object TestPattern {

  val starOrId = Gen.frequency(
    (8, TestPart.idGen map { Some(_) }),
    (2, None)
  )
  val infoGen = for (
    n <- Gen.choose(0, 16);
    lst <- Gen.listOf(starOrId);
    isStrict <- Gen.oneOf(true, false)
  ) yield PatternInfo(lst, isStrict)

}

class TestPattern extends PropSpec with PropertyChecks with Matchers {

  val patternGen = Gen.listOf(TestPart.partGen) map { new Pattern(_) }

  property("simple patterns") {
    Try { Pattern(Seq[String]()) } match {
      case Failure(e) => e should not be (null)
      case Success(x) => fail(x.toString)
    }
  }

  property("arbitrary patterns") {
    forAll(patternGen) { p =>
      p.asString should not be (null)
    }
  }

  property("arbitrary patterns to string") {
    forAll(patternGen) { p =>
      p.asWhere should not be (null)
    }
  }

  property("query generator") {
    //Need to properly test this
    //printf("%s\n", Pattern(Seq("a", "*", "c", "#")).asWhere)
  }
  // scalastyle:on magic.number null
}
