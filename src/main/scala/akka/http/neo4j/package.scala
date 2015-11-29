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

package akka.http

import spray.json._

package object neo4j extends DefaultJsonProtocol {
  import scala.util.Try

  /** This class is passed to the RESTClients in order to perform a query */
  final case class N4jError(code: String, message: String)

  implicit val jsonN4jError = jsonFormat2(N4jError.apply)

  /** Allow N4jQuery to be parsable to JSON for use in RESTClients */
  implicit final object N4jQueryJson extends JsonFormat[N4jQuery] {
    def write(q: N4jQuery) = JsObject(
      "statements" -> JsArray(
        JsObject("statement" -> JsString(q.statement),
                 "parameters" -> q.parameters.toJson)
      )
    )

    def read(value: JsValue) =
      deserializationError("Should't unwrap N4jQuery")
  }

  type N4jResponse = Either[N4jError, N4jRows]
  type N4jRows     = Stream[Map[String, JsValue]]

  /** Allows the use of `"MATCH...".n4jQuery` on strings */
  implicit class StringToQuery(val str: String) extends AnyVal {
      def n4jQuery: N4jQuery = N4jQuery(str)
  }

  implicit final class ReadFromMap(val map: Map[String, JsValue]) extends AnyVal {
    def read[T : JsonFormat](field: String): T =
      map(field).convertTo[T]
  }

  implicit final class ReadOptFromMap(val map: Map[String, JsValue]) extends AnyVal {
    def readOpt[T : JsonFormat](field: String): Option[T] =
      Try(map.read[T](field)).toOption
  }

  implicit final class WriteCCtoNode[T <: Product](val product: T) extends AnyVal {
    def toCQLNode(name: String)(implicit writer: JsonWriter[T]): String = {
      val nodeProps = writer.write(product).toString(NodePrinter)
      val nodeType  = product.productPrefix

      s"($name:$nodeType $nodeProps)"
    }
  }
}
