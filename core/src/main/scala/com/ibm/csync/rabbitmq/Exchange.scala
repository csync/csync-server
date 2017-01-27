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

case class Exchange(info: ExchangeInfo, ch: Channel) extends LazyLogging {

  import Constants._

  def publish(key: RoutingKey, data: String): Unit =
    ch.basicPublish(info.name, key.asString, basicProperties, data.getBytes())

  def bindTo(to: Exchange, routingKey: RoutingKey): AMQP.Exchange.BindOk =
    ch.exchangeBind(to.info.name, info.name, routingKey.asString)

  def unbindTo(to: Exchange, routingKey: RoutingKey): AMQP.Exchange.UnbindOk =
    ch.exchangeUnbind(to.info.name, info.name, routingKey.asString)

  def bindTo(to: Queue, routingKey: RoutingKey): AMQP.Queue.BindOk =
    ch.queueBind(to.info.name, info.name, routingKey.asString)

  def unbindTo(to: Queue, routingKey: RoutingKey): AMQP.Queue.UnbindOk =
    ch.queueUnbind(to.info.name, info.name, routingKey.asString)
}
