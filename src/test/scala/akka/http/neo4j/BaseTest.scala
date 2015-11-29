/* Copyright 2015 Felix Mulder
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

package akka.http.neo4j.test

import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import org.scalatest._
import scala.concurrent._
import scala.concurrent.duration._

import akka.http.neo4j._

trait BaseSpec extends Matchers
with FlatSpecLike
with ScalatestRouteTest
with BeforeAndAfterAll
with BeforeAndAfterEach
{
  val config = ConfigFactory.load()

  private val cleaner = Neo4jClients.stringClient(user = config.getString("database.user"),
                                                  pass = config.getString("database.pass"))

  override def beforeAll: Unit = {
    val deleteQuery = N4jQuery("MATCH (n) OPTIONAL MATCH (n)-[r]-() DELETE n,r")
    Await.result(cleaner.send(deleteQuery), 5.seconds)
  }

  override def afterEach: Unit = {
    val deleteQuery = N4jQuery("MATCH (n) OPTIONAL MATCH (n)-[r]-() DELETE n,r")
    Await.result(cleaner.send(deleteQuery), 5.seconds)
  }
}

