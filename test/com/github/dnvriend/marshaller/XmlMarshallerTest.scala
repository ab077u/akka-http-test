/*
 * Copyright 2016 Dennis Vriend
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

package com.github.dnvriend.marshaller

import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.marshalling.{ Marshal, Marshaller }
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling._
import com.github.dnvriend.TestSpec

import scala.concurrent.Future
import scala.xml._

// see: http://doc.akka.io/docs/akka-http/current/scala/http/client-side/host-level.html#host-level-api
// see: http://doc.akka.io/docs/akka-http/current/scala/http/implications-of-streaming-http-entity.html#implications-of-streaming-http-entities
// see: http://doc.akka.io/docs/akka/2.4.9/scala/http/routing-dsl/directives/marshalling-directives/entity.html#entity
// see: http://doc.akka.io/docs/akka-http/current/scala/http/common/http-model.html#httpresponse
// see: http://doc.akka.io/docs/akka-http/current/scala/http/common/marshalling.html#http-marshalling-scala
// see: http://doc.akka.io/docs/akka-http/current/scala/http/common/xml-support.html
trait NodeSeqUnmarshaller {
  implicit def responseToAUnmarshaller[A](implicit
    resp: FromResponseUnmarshaller[NodeSeq],
    toA: Unmarshaller[NodeSeq, A]): Unmarshaller[HttpResponse, A] = {
    resp.flatMap(toA).asScala
  }
}
case class Person(name: String, age: Int)
object Person extends NodeSeqUnmarshaller {
  implicit val unmarshaller: Unmarshaller[NodeSeq, Person] = Unmarshaller.strict[NodeSeq, Person] { xml =>
    val name: String = (xml \ "name").text
    val age: Int = (xml \ "age").text.toInt
    Person(name, age)
  }
  implicit val marshaller: Marshaller[Person, NodeSeq] = Marshaller.opaque[Person, NodeSeq] { person =>
    <person>
      <name>{ person.name }</name>
      <age>{ person.age }</age>
    </person>
  }
}

class XmlMarshallerTest extends TestSpec {
  it should "unmarshal to a person" in {
    val resp = HttpResponse(
      entity = HttpEntity(contentType = ContentType(MediaTypes.`application/xml`, HttpCharsets.`UTF-8`), "<person><name>dennis</name><age>42</age></person>")
    )
    Unmarshal[HttpResponse](resp).to[Person].futureValue shouldBe Person("dennis", 42)
  }

  it should "marshal to NodeSeq" in {
    Marshal[Person](Person("dennis", 42)).to[NodeSeq].futureValue shouldBe a[NodeSeq]
  }

  it should "marshal to a RequestEntity" in {
    val result: Future[RequestEntity] = for {
      xml <- Marshal[Person](Person("dennis", 42)).to[NodeSeq]
      ent <- Marshal[NodeSeq](xml).to[RequestEntity]
    } yield ent

    result.futureValue shouldBe ""
  }

  it should "" in {
    import WsClientUnmarshallers._
    withClient { client =>
      client.url("").get().map {
        resp => resp.xml.as[Person]
      }
    }
  }
}

object WsClientUnmarshallers {
  implicit class ElemOps(val xml: NodeSeq) extends AnyVal {
    def as[A](implicit unmarshaller: XmlUnmarshaller[A]): A = {
      unmarshaller.fromXml(xml)
    }
  }
}

trait XmlUnmarshaller[A] {
  def fromXml(xml: NodeSeq): A
}