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

package com.ibm.csync.rabbitmq

import org.scalacheck.{Arbitrary}
import org.scalatest.{Matchers, PropSpec}
import org.scalatest.prop.{PropertyChecks}

trait RabbitGenerators {
  val genQueueInfo = for (
    id <- Arbitrary.arbString.arbitrary
  ) yield QueueInfo(
    id = id
  )

  //implicit val arbitraryQueueInfo = Arbitrary(genQueueInfo)
}

class RabbitMQTests extends PropSpec with RabbitGenerators with PropertyChecks with Matchers {
  // scalastyle:off null
  property("queueInfo") {
    forAll(genQueueInfo) { qinfo =>
      //Not sure what to test here, we need to write RabbitTests
      qinfo.id shouldNot be(null)
    }
  }
  // scalastyle:on null
}
