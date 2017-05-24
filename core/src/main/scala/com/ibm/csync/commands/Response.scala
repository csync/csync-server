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

package com.ibm.csync.commands

sealed trait Response {
  def kind: String
}

case class AdvanceResponse(backwardVTS: Seq[Long], vts: Seq[Long], lvts: Long, rvts: Long) extends Response {
  override def kind: String = "advanceResponse"
}
case class ConnectResponse(uuid: String, uid: String, expires: Long) extends Response {
  override def kind: String = "connectResponse"
}
case class Data(path: Seq[String], data: Option[String], deletePath: Boolean,
    acl: String, creator: String, cts: Long, vts: Long) extends Response {
  override def kind: String = "data"
}
case class Error(msg: String, cause: Option[String]) extends Response {
  override def kind: String = "error"
}
case class FetchResponse(backwardResponse: Seq[Data], response: Seq[Data]) extends Response {
  override def kind: String = "fetchResponse"
}
case class RestFetchResponse(response: Seq[Data]) extends Response {
  override def kind: String = "fetchResponse"
}
case class GetAclsResponse(acls: Seq[String]) extends Response {
  override def kind: String = "getAclsResponse"
}
case class Happy(code: Int, msg: String) extends Response {
  override def kind: String = "happy"
}
case class PubResponse(code: Int, msg: String, cts: Long, vts: Long) extends Response {
  override def kind: String = "happy" // TODO: fix this
}
