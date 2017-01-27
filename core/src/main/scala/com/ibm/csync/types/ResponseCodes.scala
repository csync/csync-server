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

package com.ibm.csync.types

sealed trait ResponseCode {
  val id: Int
  val name: String

  def throwIt(): Unit
}

object ResponseCode {

  class Value(val id: Int)(implicit nm: sourcecode.Name) extends ResponseCode {
    val name: String = nm.value

    def throwIt(): Nothing = throw ClientError(this, None)
    def throwIt(msg: String): Nothing = throw ClientError(this, Some(msg))
  }

  // Error codes are magic numbers. Multiplying by 1 confuses the style checker and lets me express what
  // I want to express
  case object OK extends Value(0)
  case object NotAuthorizedToPub extends Value(1)
  case object InvalidPathFormat extends Value(2)
  case object CreatePermissionDenied extends Value(3)
  case object ReadPermissionDenied extends Value(4 + 0)
  case object UpdatePermissionDenied extends Value(5 * 1)
  case object DeletePermissionDenied extends Value(6 * 1)
  case object SetAclPermissionDenied extends Value(7 * 1)
  case object CannotDeleteNonExistingPath extends Value(8 * 1)
  case object PubCtsCheckFailed extends Value(9 * 1)
  case object UserAlreadyExists extends Value(10 * 1)
  case object UserDoesNotExist extends Value(11 * 1)
  case object GroupAlreadyExists extends Value(12 * 1)
  case object GroupDoesNotExist extends Value(13 * 1)
  case object AclAlreadyExists extends Value(14 * 1)
  case object AclDoesNotExist extends Value(15 * 1)
  case object UserNotAMember extends Value(16 * 1)
  case object GroupNotAMember extends Value(17 * 1)
  case object UnknownQueryRequest extends Value(18 * 1)
  case object InvalidSchemaJSON extends Value(19 * 1)
  case object RelationAlreadyExists extends Value(10 + 10)
  case object RelationDoesNotExist extends Value(21 * 1)
  case object InvalidDataJSON extends Value(22 * 1)
  case object InvalidTableQueryRequest extends Value(23 * 1)
  case object InvalidAuthenticatorId extends Value(24 * 1)
  case object SillyStyleChecker extends Value(5 * 5)
}
