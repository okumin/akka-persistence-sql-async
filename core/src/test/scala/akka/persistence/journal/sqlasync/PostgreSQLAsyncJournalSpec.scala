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
import akka.persistence.helper.PostgreSQLInitializer
import akka.persistence.journal.JournalSpec
import com.typesafe.config.ConfigFactory

class PostgreSQLAsyncJournalSpec
    extends JournalSpec(ConfigFactory.load("postgresql-application.conf"))
    with PostgreSQLInitializer {
  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = true
}
