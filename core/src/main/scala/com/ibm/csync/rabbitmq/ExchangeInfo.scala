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

case class ExchangeInfo(id: String, typ: String = "topic", durable: Boolean = false,
    autoDelete: Boolean = false,
    args: Map[String, Object] = Map()) extends LazyLogging {
  def id(x: String): ExchangeInfo = this.copy(id = x)
  def typ(x: String): ExchangeInfo = this.copy(typ = x)
  def durable(x: Boolean): ExchangeInfo = this.copy(durable = x)
  def autoDelete(x: Boolean): ExchangeInfo = this.copy(autoDelete = x)
  def args(x: Map[String, Object]): ExchangeInfo = this.copy(args = x)
  def arg(k: String, v: Object): ExchangeInfo = this.copy(args = args + (k -> v))

  lazy val name = "x3-" + id

  def declare(ch: Channel): Exchange = {
    import collection.JavaConverters._

    ch.exchangeDeclare(name, typ, durable, autoDelete, args.asJava)
    Exchange(this, ch)

  }
}
