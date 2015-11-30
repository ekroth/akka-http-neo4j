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

package akka.http.neo4j

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import headers.{Accept, Authorization, BasicHttpCredentials}
import akka.stream.scaladsl._
import spray.json._

object Neo4jClients extends DefaultJsonProtocol with NullOptions {
  import akka.actor.ActorSystem
  import akka.stream.ActorMaterializer
  import scala.concurrent.ExecutionContext
  import scala.concurrent.Future
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import akka.http.scaladsl.unmarshalling.Unmarshal

  sealed trait BaseRESTClient[T] {
    import HttpMethods._
    import MediaTypes._

    implicit def sys: ActorSystem
    implicit def exec: ExecutionContext
    implicit def mat: ActorMaterializer

    protected def hostName: String
    protected def hostPort: Int
    protected def hostPath: String
    protected def userAndPass: Option[(String, String)]

    private val authHeader: Seq[HttpHeader] = userAndPass map {
      case (u, p) => Seq(Authorization(BasicHttpCredentials(u, p)))
    } getOrElse Nil

    private[this] val headers: List[HttpHeader] = List(
      Accept(MediaRange(`application/json`))) ++ authHeader

    type ConnectionFlow = Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]]

    private[this] lazy val connectionFlow: ConnectionFlow =
      Http().outgoingConnection(hostName, hostPort)

    private[this] def requestEntity(obj: JsValue): RequestEntity =
      HttpEntity(MediaTypes.`application/json`, obj.toString)

    private[this] def sendRequest[A : JsonFormat](obj: A): Future[HttpResponse] = Source
      .single(HttpRequest(
        POST,
        uri = hostPath + "transaction/commit",
        entity = requestEntity(obj.toJson),
        headers = headers
      ))
      .via(connectionFlow)
      .runWith(Sink.head)

    protected def parser: HttpResponse => Future[T]

    def send(query: N4jQuery): Future[T] =
      sendRequest(query).flatMap(parser)
  }

  trait JsonClient extends BaseRESTClient[JsValue] {
    override def parser = resp => Unmarshal(resp.entity).to[JsValue]
  }

  trait StringClient extends BaseRESTClient[String] {
    override def parser = resp => Unmarshal(resp.entity).to[String]
  }

  trait ObjectClient extends BaseRESTClient[N4jResponse] {
    override def parser = { json =>
      case class Response(results: Seq[Result], errors: Seq[JsValue])
      case class Result(columns: IndexedSeq[String], data: Seq[Data])
      case class Data(row: IndexedSeq[JsValue])

      implicit val jsonData     = jsonFormat1(Data.apply)
      implicit val jsonResult   = jsonFormat2(Result.apply)
      implicit val jsonResponse = jsonFormat2(Response.apply)

      def parseResp(resp: Seq[Result]): N4jRows = {
        val results: Result = resp.head
        val columns: Seq[String] = results.columns
        val rows: Seq[Seq[JsValue]] = results.data.map(_.row)

        val rowWithColumns: Seq[Map[String, JsValue]] =
          rows.map { row =>
            if (row.length != columns.length) None
            else Some(columns.zip(row).toMap)
          }.flatten

        rowWithColumns.toStream
      }

      Unmarshal(json.entity).to[Response] map { resp =>
        if (resp.errors != Nil)
          Left(resp.errors.head.convertTo[N4jError])
        else
          Right(parseResp(resp.results))
      }
    }
  }

  def jsonClient(host: String = "localhost",
                 port: Int    = 7474,
                 path: String = "/db/data/",
                 user: String = "",
                 pass: String = "")(implicit system: ActorSystem, materializer: ActorMaterializer): JsonClient = new JsonClient {
    implicit val sys  = system
    implicit val exec = system.dispatcher
    implicit val mat  = materializer

    override def hostName: String = host
    override def hostPort: Int = port
    override def hostPath: String = path
    override def userAndPass =
      if (user != "" && pass != "")
        Some((user, pass))
      else
        None
  }

  def stringClient(host: String = "localhost",
                   port: Int    = 7474,
                   path: String = "/db/data/",
                   user: String = "",
                   pass: String = "")(implicit system: ActorSystem, materializer: ActorMaterializer): StringClient = new StringClient {
    implicit val sys  = system
    implicit val exec = system.dispatcher
    implicit val mat  = materializer

    override def hostName: String = host
    override def hostPort: Int = port
    override def hostPath: String = path
    override def userAndPass =
      if (user != "" && pass != "")
        Some((user, pass))
      else
        None
  }

  def objectClient(host: String = "localhost",
                   port: Int    = 7474,
                   path: String = "/db/data/",
                   user: String = "",
                   pass: String = "")(implicit system: ActorSystem, materializer: ActorMaterializer): ObjectClient = new ObjectClient {
    implicit val sys  = system
    implicit val exec = system.dispatcher
    implicit val mat  = materializer

    override def hostName: String = host
    override def hostPort: Int = port
    override def hostPath: String = path
    override def userAndPass =
      if (user != "" && pass != "")
        Some((user, pass))
      else
        None
  }
}
