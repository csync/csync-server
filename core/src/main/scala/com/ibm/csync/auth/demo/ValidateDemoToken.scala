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

package com.ibm.csync.auth.demo

import com.ibm.csync.session.{Session, UserInfo}
import com.typesafe.scalalogging.LazyLogging

object ValidateDemoToken extends LazyLogging {

  def validate(token: String): UserInfo = {
    logger.info(s"[validateToken]: $token Validating demo id token representing user’s identity asserted by the identity provider")

    token match {
      case Session.demoToken => UserInfo("demoUser")
      case Session.userToken(user) => UserInfo(user)
      case _ =>
        logger.debug(s"Token validation failed for token: $token")
        throw new Exception("Cannot establish session. Token validation failed")
    }
  }
}
