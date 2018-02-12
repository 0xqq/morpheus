/*
 * Copyright (c) 2016-2018 "Neo4j, Inc." [https://neo4j.com]
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
package org.opencypher.caps.cosc.impl.datasource

import java.net.URI

import org.opencypher.caps.api.io.PropertyGraphDataSource
import org.opencypher.caps.cosc.impl.COSCSession
import org.opencypher.caps.impl.exception.IllegalArgumentException

case class COSCGraphSourceHandler(
  sessionGraphSourceFactory: COSCSessionPropertyGraphDataSourceFactory,
  additionalGraphSourceFactories: Set[COSCPropertyGraphDataSourceFactory]) {
  private val factoriesByScheme: Map[String, COSCPropertyGraphDataSourceFactory] = {
    val allFactories = Seq(sessionGraphSourceFactory)
    val entries = allFactories.flatMap(factory => factory.schemes.map(scheme => scheme -> factory))
    if (entries.size == entries.map(_._1).size)
      entries.toMap
    else
      throw IllegalArgumentException(
        "at most one graph source factory per URI scheme",
        s"factories for schemes: ${allFactories.map(factory => factory.name -> factory.schemes.mkString("[", ", ", "]")).mkString(",")}"
      )
  }

  def mountSourceAt(source: COSCPropertyGraphDataSource, uri: URI)(implicit coscSession: COSCSession): Unit =
    sessionGraphSourceFactory.mountSourceAt(source, uri)

  def unmountAll(implicit coscSession: COSCSession): Unit =
    sessionGraphSourceFactory.unmountAll(coscSession)

  def sourceAt(uri: URI)(implicit coscSession: COSCSession): PropertyGraphDataSource =
    optSourceAt(uri).getOrElse(throw IllegalArgumentException(s"graph source for URI: $uri"))

  def optSourceAt(uri: URI)(implicit coscSession: COSCSession): Option[PropertyGraphDataSource] =
    factoriesByScheme
      .get(uri.getScheme)
      .map(_.sourceFor(uri))
}
