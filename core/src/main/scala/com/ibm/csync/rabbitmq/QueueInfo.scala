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

import com.rabbitmq.client.Channel
import com.typesafe.scalalogging.LazyLogging

case class QueueInfo(
    id: String = null,
    durable: Boolean = false,
    exclusive: Boolean = false,
    autoDelete: Boolean = false,
    args: Map[String, Object] = Map()
) extends LazyLogging {
  def id(x: String): QueueInfo = this.copy(id = x)
  def durable(x: Boolean): QueueInfo = this.copy(durable = x)
  def exclusive(x: Boolean): QueueInfo = this.copy(exclusive = x)
  def autoDelete(x: Boolean): QueueInfo = this.copy(autoDelete = x)
  def args(x: Map[String, Object]): QueueInfo = this.copy(args = x)
  def arg(k: String, v: Object): QueueInfo = this.copy(args = args + (k -> v))
  def queueTTL(t: Int): QueueInfo = this.arg("x-expires", new Integer(t))
  def messageTTL(t: Int): QueueInfo = this.arg("x-message-ttl", new Integer(t))

  lazy val name: String = "q3-" + id

  def declare(ch: Channel): Queue = {
    import collection.JavaConverters._

    logger.debug(s"[QueueInfo.declare]: Declaring queue $name with properties $this")

    ch.queueDeclare(name, durable, exclusive, autoDelete, args.asJava)
    Queue(this, ch)
  }
}
