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

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import spray.json._

import akka.http.neo4j._

class StringClientSpec extends BaseSpec {

  val client = Neo4jClients.stringClient(user = config.getString("database.user"),
                                         pass = config.getString("database.pass"))

  "StringClient" should "be able to insert basic node" in {
    val cql = """CREATE (n:Test {title: "Hello, world!"}) RETURN n""".n4jQuery

    val resp: Future[String] = client send cql

    val expectedRes =
      """
      {
        "results": [{
          "columns":["n"],
          "data": [{
            "row":[{"title":"Hello, world!"}]
          }]
        }],
        "errors":[]}
      """.parseJson.toString

    assert(Await.result(resp, 5.seconds) == expectedRes)
  }

  it should "receive an error on erroneous query" in {
    val cql = N4jQuery("ERROR")
    val resp: Future[String] = client send cql

    val expectedRes =
      """
      {
        "results":[],
        "errors":[{
          "code":"Neo.ClientError.Statement.InvalidSyntax",
          "message":"Invalid input 'E': expected <init> (line 1, column 1 (offset: 0))\n\"ERROR\"\n ^"
        }]
      }
      """.parseJson.toString

    assert(Await.result(resp, 5.seconds) == expectedRes)
  }
}
