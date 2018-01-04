/*
 * Copyright 2014 okumin.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.journal.sqlasync

import akka.persistence.CapabilityFlag
import akka.persistence.helper.MySQLInitializer
import akka.persistence.journal.JournalPerfSpec
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._

class MySQLAsyncJournalPerfSpec
    extends JournalPerfSpec(ConfigFactory.load("mysql-application.conf"))
    with MySQLInitializer {
  override def awaitDurationMillis: Long = 100.seconds.toMillis
  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = true
}
