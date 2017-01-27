/*
 * Copyright IBM Corporation 2017
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

import org.scalatest.{FunSuite, Matchers}

class KeyTests extends FunSuite with Matchers {
  // scalastyle:off line.size.limit
  test("Null string cannot become a key") {
    assertThrows[com.ibm.csync.types.ClientError] {
      Key.apply(Seq(""))
    }
  }

  test("String with 17 parts should fail as max number of parts is 16") {
    assertThrows[com.ibm.csync.types.ClientError] {
      Key.apply(Seq("a", "b", "a", "b", "a", "b", "a", "b", "a", "b", " a", "b", "a", "b", "a", "b", "a"))
    }
  }

  test("String with 201 characters in one part should fail as max string length is 200") {
    assertThrows[com.ibm.csync.types.ClientError] {
      Key.apply(Seq("aikgosmjfuyfxdtrhiwyqwosoecrqcwnvzehfasfhgespaenogteuncxxddxeyowfcormgqrvnbeskblprelxlpolfvowpsibtolhcmypgnlnfekrqufckwrszusrwmhxeltcgzodnlnlxeualeqiaxbujdnfqalhzyatcqtmtpltlrbzkeohaibmqpowbcftpyicspca"))
    }
  }

  test("String with 200 characters in one part should pass as max string length is 200") {
    try {
      val key = Key.apply(Seq("ikgosmjfuyfxdtrhiwyqwosoecrqcwnvzehfasfhgespaenogteuncxxddxeyowfcormgqrvnbeskblprelxlpolfvowpsibtolhcmypgnlnfekrqufckwrszusrwmhxeltcgzodnlnlxeualeqiaxbujdnfqalhzyatcqtmtpltlrbzkeohaibmqpowbcftpyicspca"))
      assert(key.asString == "ikgosmjfuyfxdtrhiwyqwosoecrqcwnvzehfasfhgespaenogteuncxxddxeyowfcormgqrvnbeskblprelxlpolfvowpsibtolhcmypgnlnfekrqufckwrszusrwmhxeltcgzodnlnlxeualeqiaxbujdnfqalhzyatcqtmtpltlrbzkeohaibmqpowbcftpyicspca")
    } catch {
      case _: Throwable => fail()
    }
  }

  test("String with 200 (201 with the .) characters in two parts should fail as max string length is 200") {
    assertThrows[com.ibm.csync.types.ClientError] {
      Key.apply(Seq("aikgosmjfuyfxdtrhiwyqwosoecrqcwnvzehfasfhgespaenogteun", "cxxddxeyowfcormgqrvnbeskblprelxlpolfvowpsibtolhcmypgnlnfekrqufckwrszusrwmhxeltcgzodnlnlxeualeqiaxbujdnfqalhzyatcqtmtpltlrbzkeohaibmqpowbcftpyicspca"))
    }
  }

  test("String with 199(200 with the .) characters in two parts should pass as max string length is 200 ") {
    try {
      val key = Key.apply(Seq("kgosmjfuyfxdtrhiwyqwosoecrqcwnvzeh", "fasfhgespaenogteuncxxddxeyowfcormgqrvnbeskblprelxlpolfvowpsibtolhcmypgnlnfekrqufckwrszusrwmhxeltcgzodnlnlxeualeqiaxbujdnfqalhzyatcqtmtpltlrbzkeohaibmqpowbcftpyicspca"))
      assert(key.asString == "kgosmjfuyfxdtrhiwyqwosoecrqcwnvzeh.fasfhgespaenogteuncxxddxeyowfcormgqrvnbeskblprelxlpolfvowpsibtolhcmypgnlnfekrqufckwrszusrwmhxeltcgzodnlnlxeualeqiaxbujdnfqalhzyatcqtmtpltlrbzkeohaibmqpowbcftpyicspca")
    } catch {
      case _: Throwable => fail()
    }
  }

  test("String with no parts should throw error") {
    assertThrows[com.ibm.csync.types.ClientError] {
      val key = Key.apply(Seq())
    }
  }
  // scalastyle:on line.size.limit
}
