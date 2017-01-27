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

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}

case class Queue(info: QueueInfo, ch: Channel) extends LazyLogging {

  def remove(implicit ec: ExecutionContext): Future[AMQP.Queue.DeleteOk] = Future {
    logger.debug(s"[Queue.remove]: Deleting queue ${info.name} with properties $this")
    ch.queueDelete(info.name)
  }

  def publish(data: String)(implicit ec: ExecutionContext): Future[_] = Future {
    logger.debug(s"[Queue.publish]: publishing to queue ${info.name}");
    ch.basicPublish("", info.name, Constants.basicProperties, data.getBytes())
  }

}
