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

import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import akka.http.scaladsl.model.headers._

import spray.json._

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import org.scalatest._

import akka.http.neo4j._

class ObjectClientSpec extends BaseSpec {
  val client = Neo4jClients.objectClient(user = config.getString("database.user"),
                                         pass = config.getString("database.pass"))

  "ObjectClient" should "be able to perform simple query" in {
    val cql =
      """CREATE (n:Test {title: "Hello, world!"}) RETURN n""".n4jQuery

    val resp: Future[N4jResponse] = client send cql

    val expectedResp = Right {
      Stream(Map("n" -> JsObject("title" -> JsString("Hello, world!"))))
    }

    assert(Await.result(resp, 5.seconds) == expectedResp)
  }

  it should "return errors on CQL syntax error" in {
    Await.result(client send "ERROR".n4jQuery, 5.seconds) match {
      case Left(err) => assert(err.code == "Neo.ClientError.Statement.InvalidSyntax")
      case Right(_)  => fail("Expected error message")
    }
  }

  it should "be able to return multiple nodes" in {
    val setup =
      """
      CREATE (n1:Test {title: "T1"})-[:LOVES]->(n2:Test {title: "T2"})
      CREATE (n3:Test {title: "T3"})-[:LOVES]->(n4:Test {title: "T4"})
      RETURN n1
      """.n4jQuery
    Await.result(client send setup, 5.seconds)

    val query =
      """
      MATCH (n1:Test)-[:LOVES]->(n2:Test)
      RETURN n1, n2;
      """.n4jQuery

    Await.result(client send query, 5.seconds) match {
      case Right(rows) => assert(rows.length == 2)
      case Left(err) => fail(s"Received error from server: $err")
    }
  }

  it should "be able to parse case class from returned node" in {
    case class Foo(title: String, desc: String)
    implicit val jsonFoo = jsonFormat2(Foo.apply)
    val insert =
      """
      CREATE (n:Foo {title: "Title", desc: "working deserialization"})
      RETURN n
      """.n4jQuery

    Await.result(client send insert, 5.seconds) match {
      case Right(rows) => {
        val actualRes = rows.map(_.read[Foo]("n")).head
        val expectedRes = Foo("Title", "working deserialization")

        assert(actualRes == expectedRes)
      }
      case Left(err) => fail(s"Received error from server: $err")
    }
  }

  it should "be able to parse node from case class" in {
    case class Foo(title: String, desc: String)
    implicit val jsonFoo = jsonFormat2(Foo.apply)

    val node = Foo("Title!", "Description!").toCQLNode("n")

    assert(node == """(n:Foo {title:"Title!",desc:"Description!"})""")
  }
}
