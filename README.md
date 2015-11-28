akka-http-neo4j
===============
Neo4j Scala library for use with akka-http and
[spray-json](https://github.com/spray/spray-json)

[![Build Status](https://travis-ci.org/felixmulder/akka-http-neo4j.svg?branch=master)](https://travis-ci.org/felixmulder/akka-http-neo4j)

This library supports Cypher asynchronous queries to the Neo4j Database. It
provides several shorthands and useful abstractions for interacting with Neo4j.
It relies on akka-http to perform queries to the
[Neo4j REST API](http://neo4j.com/docs/stable/rest-api.html).

spray-json is not required, but to enjoy the full benefits of the library - it
is recommended.

Examples
--------

There are three default clients:
 * `StringClient` - returns the HTML response body as is
 * `JsonClient` - parses the HTML body to JSON
 * `ObjectClient` - parse the HTML body to rows on the form
   `Stream[Map[String, JsValue]]`

### Performing queries ###

```scala
import akka.http.neo4j._

val query  = """CREATE (m:Message {text: "Hello, world!"}) RETURN m""".n4jQuery
val client = Neo4jClients.objectClient()

client send query
// returns: Future[N4jResponse]
```

As you can see, `String` has the convenience method `.n4jQuery` which turns the
string into an `N4jQuery` acceptable by the client's `send` method.


### Parsing result ###

The future returned by the client is in fact an `Either`. Either an `N4jError`
or an `N4jRows` is returned. The latter is just a type alias for
`Stream[Map[String,JsValue]]`.

```scala
val req = client send query // using same values as the above example

req map {
  case Right(rows) => rows foreach println // prints: Map(m -> {text:"Hello, world!"})
  case Left(err)   => println(s"${err.code} ${err.message}")
}
```


### Case Classes ###

Case classes can both be read from and to the database given that the case class
has a `JsonFormat[T]`. This is easily achievable:

```scala
case class Foo(title: String, desc: String)
implicit val formatFoo = jsonFormat2(Foo.apply)
```

This case class can now be turned into a CQL node.

#### Writing to DB ####

```scala
Foo("Title!", "Description!").toCQLNode("n")
// returns: (n:Foo {title:"Title!",desc:"Description!"})
```

The case class can now be inserted into a query:

```scala
val fooNode = Foo("Title!", "Description!").toCQLNode("n")

val insert = """CREATE $fooNode RETURN n""".n4jQuery
```

#### Parsing case classes from DB ####

```scala
(client send insert) map {
  case Right(rows) => rows map { _.read[Foo]("n") }
  case _ => Nil
}
// returns a stream of Foo:s
```
