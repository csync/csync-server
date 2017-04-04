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

package com.ibm.csync.auth.google

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.ibm.csync.session.UserInfo
import com.ibm.csync.types.ClientError
import com.ibm.csync.types.ResponseCode.InvalidAuthenticatorId
import com.typesafe.scalalogging.LazyLogging

object ValidateGoogleToken extends LazyLogging {

  private val googleClientId = java.util.Arrays.asList(sys.env.getOrElse("CSYNC_GOOGLE_CLIENT_IDS", ""))

  val googleIssuer = "accounts.google.com"
  val googlePlayIssuer = "https://accounts.google.com"

  val jsonFactory: JsonFactory = new GsonFactory()
  val transport: HttpTransport = new NetHttpTransport()

  val googleVerifier: GoogleIdTokenVerifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
    .setAudience(googleClientId)
    .setIssuer(googleIssuer)
    .build()

  val googlePlayVerifier: GoogleIdTokenVerifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
    .setAudience(googleClientId)
    .setIssuer(googlePlayIssuer)
    .build()

  def validate(token: String): Option[UserInfo] = {
    logger.info(s"[validateToken]: $token Validating google id token representing user’s identity asserted by the identity provider")

    // token validation
    // Verify can throw or return null - we combine these into null to avoid duplicate error handling.
    Option(googleVerifier.verify(token)) orElse {
      Option(googlePlayVerifier.verify(token))
    } map { idToken =>
      val payload = idToken.getPayload
      val expires = payload.getExpirationTimeSeconds
      if ((expires * 1000) < System.currentTimeMillis()) {
        throw ClientError(InvalidAuthenticatorId, Option("Cannot establish session. Token validation failed"))
      }
      val authenticatorId = s"${payload.getIssuer}:${payload.getSubject}"
      val email: Option[String] = Option(payload.get("email")) map {
        _.toString()
      }

      logger.debug(s"[validateToken]: Validated id token. Contains authenticatorid $authenticatorId and email $email")
      UserInfo(authenticatorId, expires)
    }
  }
}
