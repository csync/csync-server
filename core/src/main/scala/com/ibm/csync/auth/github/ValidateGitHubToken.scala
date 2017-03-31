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

package com.ibm.csync.auth.github

import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.ibm.csync.session.UserInfo
import com.typesafe.scalalogging.LazyLogging
import org.json4s.native.JsonMethods._

import scala.util.Try
import scalaj.http.{Http, HttpResponse}

object ValidateGitHubToken extends LazyLogging {

  val githubClientId = sys.env.getOrElse("CSYNC_GITHUB_ID", "")
  val githubClientSecret = sys.env.getOrElse("CSYNC_GITHUB_SECRET", "")

  val jsonFactory: JsonFactory = new GsonFactory()

  def validate(token: String): UserInfo = {
    logger.info(s"[validateToken]: $token Validating github id token representing user’s identity asserted by the identity provider")

    val url = s"https://api.github.com/applications/${githubClientId}/tokens/$token"

    val response: Try[HttpResponse[String]] = Try(Http(url).auth(githubClientId, githubClientSecret).asString)

    if (response.isFailure || response.get.code != 200) {
      logger.info(s"[validateGitHubToken]: Token validation failed for token: ${token}")
      throw new Exception("Cannot establish session. Token validation failed")
    }

    val data = response.get.body
    val parsed = parse(data)
    val id = (parsed \ "user" \ "id").values

    if (id == None) {
      logger.info(s"[validateGitHubToken]: Token validation failed for token: ${token}")
      throw new Exception("Cannot establish session. Token validation failed")
    }

    val authenticatorId = s"github:${id}"

    logger.debug(s"[validateToken]: Validated id token. Contains authenticatorid $authenticatorId")
    UserInfo(authenticatorId)
  }
}
