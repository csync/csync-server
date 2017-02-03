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

package com.ibm.csync.auth.facebook

import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.ibm.csync.session.UserInfo
import com.typesafe.scalalogging.LazyLogging
import org.json4s._
import org.json4s.native.JsonMethods._
import scala.util.Try
import scalaj.http.{Http, HttpResponse}

object ValidateFacebookToken extends LazyLogging {

  val facebookAppId = sys.env.getOrElse("CSYNC_FACEBOOK_ID", "")
  val facebookAppSecret = sys.env.getOrElse("CSYNC_FACEBOOK_SECRET", "")

  val jsonFactory: JsonFactory = new GsonFactory()

  def validate(token: String): UserInfo = {
    logger.info(s"[validateToken]: $token Validating facebook id token representing user’s identity asserted by the identity provider")

    val url = s"https://graph.facebook.com/debug_token?input_token=${token}&access_token=${facebookAppId}|${facebookAppSecret}"

    val response: Try[HttpResponse[String]] = Try(Http(url).asString)

    if (response.isFailure || response.get.code != 200) {
      logger.info(s"[validateFacebookToken]: Token validation failed for token: ${token}")
      throw new Exception("Cannot establish session. Token validation failed")
    }

    val data = response.get.body
    val parsed = parse(data)

    if((parsed \ "data" \ "is_valid").values.equals(false)) {
      logger.info(s"[validateFacebookToken]: Token validation failed for token: ${token}")
      throw new Exception("Cannot establish session. Token validation failed")
    }

    val id =(parsed \ "data" \ "user_id" ).values
    val authenticatorId = s"facebook:${id}"

    logger.debug(s"[validateToken]: Validated id token. Contains authenticatorid $authenticatorId")
    UserInfo(authenticatorId)
  }
}
