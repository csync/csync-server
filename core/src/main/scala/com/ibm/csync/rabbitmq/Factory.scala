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

import com.rabbitmq.client.{Connection, ConnectionFactory}
import org.slf4j.LoggerFactory

import scalaj.http.Http

case class Factory(
    uri: String
) {

  lazy val connFactory: ConnectionFactory = {
    val t = new ConnectionFactory
    t.setUri(uri)
    t
  }

  def newConnection: Connection = {
    val perms =
      """
			  {
			    "configure" : ".*",
			    "read"      : ".*",
			    "write"     : ".*"
			  }
			"""

    val url = s"http://${connFactory.getHost}:" + Constants.PORT

    try {
      Http(s"$url/api/vhosts/${connFactory.getVirtualHost}")
        .header("content-type", "application/json")
        .auth(connFactory.getUsername, connFactory.getPassword).put("")
        .asString
        .throwError

      Http(s"$url/api/permissions/${connFactory.getVirtualHost}/${connFactory.getUsername}")
        .header("content-type", "application/json")
        .auth(connFactory.getUsername, connFactory.getPassword)
        .put(perms)
        .asString
        .throwError
    } catch {

      case _ => {
        lazy val logger = LoggerFactory.getLogger(getClass)
        logger.debug("RabbitMQ: Unable to create virtualHost and set permissions.")
      }
    }
    connFactory.newConnection
  }
}
