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

package akka.persistence.common

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import scalikejdbc.async.{AsyncConnectionPool, AsyncConnectionPoolSettings, AsyncConnectionSettings}

private[persistence] object ScalikeJDBCExtension
    extends ExtensionId[ScalikeJDBCExtension]
    with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ScalikeJDBCExtension = {
    new ScalikeJDBCExtension(system)
  }

  override def lookup(): ExtensionId[_ <: Extension] = ScalikeJDBCExtension
}

private[persistence] class ScalikeJDBCExtension(system: ExtendedActorSystem) extends Extension {
  val config = SQLAsyncConfig(system)
  val connectionPoolName = s"${config.rootKey}.${config.url}"
  val sessionProvider: ScalikeJDBCSessionProvider = {
    new DefaultScalikeJDBCSessionProvider(connectionPoolName, system.dispatcher)
  }
  AsyncConnectionPool.add(
    name = connectionPoolName,
    url = config.url,
    user = config.user,
    password = if (config.password == "") null else config.password,
    settings = AsyncConnectionPoolSettings(
      maxPoolSize = config.maxPoolSize,
      maxQueueSize = config.waitQueueCapacity,
      connectionSettings = AsyncConnectionSettings(
        connectTimeout = config.connectTimeout,
        queryTimeout = config.queryTimeout
      )
    )
  )
}
