/**
 * Copyright 2013 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package models

import play.api.mvc.{JavascriptLitteral, QueryStringBindable}
import scala.collection.JavaConverters._

/**
 *
 * QueryStringBinders for some data types missing in 2.0.4
 */
//TODO: remove when updating to 2.1
object QueryBinders {

  /**
   * QueryString binder for List
   */
  implicit def bindableList[T: QueryStringBindable]: QueryStringBindable[List[T]] = new QueryStringBindable[List[T]] {
    def bind(key: String, params: Map[String, Seq[String]]) = Some(Right(bindList[T](key, params)))
    def unbind(key: String, values: List[T]) = unbindList(key, values)
    override def javascriptUnbind = javascriptUnbindList(implicitly[QueryStringBindable[T]].javascriptUnbind)
  }

  /**
   * QueryString binder for java.util.List
   */
  implicit def bindableJavaList[T: QueryStringBindable]: QueryStringBindable[java.util.List[T]] = new QueryStringBindable[java.util.List[T]] {
    def bind(key: String, params: Map[String, Seq[String]]) = Some(Right(bindList[T](key, params).asJava))
    def unbind(key: String, values: java.util.List[T]) = unbindList(key, values.asScala)
    override def javascriptUnbind = javascriptUnbindList(implicitly[QueryStringBindable[T]].javascriptUnbind)
  }

  private def bindList[T: QueryStringBindable](key: String, params: Map[String, Seq[String]]): List[T] = {
    for {
      values <- params.get(key).toList
      rawValue <- values
      bound <- implicitly[QueryStringBindable[T]].bind(key, Map(key -> Seq(rawValue)))
      value <- bound.right.toOption
    } yield value
  }

  private def unbindList[T: QueryStringBindable](key: String, values: Iterable[T]): String = {
    (for (value <- values) yield {
      implicitly[QueryStringBindable[T]].unbind(key, value)
    }).mkString("&")
  }

  private def javascriptUnbindList(jsUnbindT: String) = "function(k,vs){var l=vs&&vs.length,r=[],i=0;for(;i<l;i++){r[i]=(" + jsUnbindT + ")(k,vs[i])}return r.join('&')}"

  /**
   * Convert a Scala List[T] to Javascript array
   */
  implicit def litteralOption[T](implicit jsl: JavascriptLitteral[T]) = new JavascriptLitteral[List[T]] {
    def to(value: List[T]) = "[" + value.map { v => jsl.to(v)+"," } +"]"
  }
}

