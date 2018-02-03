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
package org.opencypher.caps.impl.spark.acceptance

import org.opencypher.caps.api.value.CAPSMap
import org.opencypher.caps.impl.spark.CAPSGraph

import scala.collection.immutable.Bag

trait AggregationBehaviour {
  this: AcceptanceTest =>

  def aggregationBehaviour(initGraph: String => CAPSGraph): Unit = {

    describe("AVG") {

      test("avg(prop) with integers in WITH") {
        val graph = initGraph("CREATE ({val:2}),({val:4}),({val:6})")

        val result = graph.cypher("MATCH (n) WITH AVG(n.val) AS res RETURN res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 4)
        ))
      }

      test("avg(prop) with integers in RETURN") {
        val graph = initGraph("CREATE ({val: 2}),({val: 4}),({val: 6})")

        val result = graph.cypher("MATCH (n) RETURN AVG(n.val) AS res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 4)
        ))
      }

      test("avg(prop) with integers in RETURN without alias") {
        val graph = initGraph("CREATE ({val: 2}),({val: 4}),({val: 6})")

        val result = graph.cypher("MATCH (n) RETURN AVG(n.val)")

        result.records.toMaps should equal(Bag(
          CAPSMap("AVG(n.val)" -> 4)
        ))
      }

      test("avg(prop) with floats in WITH") {
        val graph = initGraph("CREATE ({val:5.0D}),({val:5.0D}),({val:0.5D})")

        val result = graph.cypher("MATCH (n) WITH AVG(n.val) AS res RETURN res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 3.5)
        ))
      }

      test("avg(prop) with floats in RETURN") {
        val graph = initGraph("CREATE ({val:5.0D}),({val:5.0D}),({val:0.5D})")

        val result = graph.cypher("MATCH (n) RETURN AVG(n.val) AS res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 3.5)
        ))
      }

      test("avg(prop) with single null value in WITH") {
        val graph = initGraph("CREATE ({val:42.0D}),({val:23.0D}),()")

        val result = graph.cypher("MATCH (n) WITH AVG(n.val) AS res RETURN res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 32.5)
        ))
      }

      test("avg(prop) with single null value in RETURN") {
        val graph = initGraph("CREATE ({val:42.0D}),({val:23.0D}),()")

        val result = graph.cypher("MATCH (n) RETURN AVG(n.val) AS res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 32.5)
        ))
      }

      ignore("avg(prop) with only null values in WITH") {
        val graph = initGraph("CREATE ({val:NULL}),(),()")

        val result = graph.cypher("MATCH (n) WITH AVG(n.val) AS res RETURN res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> null)
        ))
      }

      ignore("avg(prop) with only null values in RETURN") {
        val graph = initGraph("CREATE ({val:NULL}),(),()")

        val result = graph.cypher("MATCH (n) RETURN AVG(n.val) AS res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> null)
        ))
      }
    }

    describe("COUNT") {

      test("count(*) in WITH") {
        val graph = initGraph("CREATE ({name: 'foo'}), ({name: 'bar'}), (), (), (), ({name: 'baz'})")

        val result = graph.cypher("MATCH (n) WITH count(*) AS nbrRows RETURN nbrRows")

        result.records.toMaps should equal(Bag(
          CAPSMap("nbrRows" -> 6)
        ))
      }

      test("count(*) in RETURN") {
        val graph = initGraph("CREATE ({name: 'foo'}), ({name: 'bar'}), (), (), (), ({name: 'baz'})")

        val result = graph.cypher("MATCH (n) RETURN count(*) AS nbrRows")

        result.records.toMaps should equal(Bag(
          CAPSMap("nbrRows" -> 6)
        ))
      }

      test("count(n) in RETURN") {
        val graph = initGraph("CREATE ({name: 'foo'}), ({name: 'bar'}), (), (), (), ({name: 'baz'})")

        val result = graph.cypher("MATCH (n) RETURN count(n) AS nbrRows")

        result.records.toMaps should equal(Bag(
          CAPSMap("nbrRows" -> 6)
        ))
      }

      test("count(n) in RETURN without alias") {
        val graph = initGraph("CREATE ({name: 'foo'}), ({name: 'bar'}), (), (), (), ({name: 'baz'})")

        val result = graph.cypher("MATCH (n) RETURN count(n)")

        result.records.toMaps should equal(Bag(
          CAPSMap("count(n)" -> 6)
        ))
      }

      test("count(*) in return without alias") {
        val graph = initGraph("CREATE ({name: 'foo'}), ({name: 'bar'}), (), (), (), ({name: 'baz'})")

        val result = graph.cypher("MATCH (n) RETURN count(*)")

        result.records.toMaps should equal(Bag(
          CAPSMap("count(*)" -> 6)
        ))
      }

      test("simple count(prop)") {
        val graph = initGraph("CREATE ({name: 'foo'}), ({name: 'bar'}), (), (), (), ({name: 'baz'})")

        val result = graph.cypher("MATCH (n) WITH count(n.name) AS nonNullNames RETURN nonNullNames")

        result.records.toMaps should equal(Bag(
          CAPSMap("nonNullNames" -> 3)
        ))
      }

      test("simple count(node)") {
        val graph = initGraph("CREATE ({name: 'foo'}), ({name: 'bar'}), (), (), (), ({name: 'baz'})")

        val result = graph.cypher("MATCH (n) WITH count(n) AS nodes RETURN nodes")

        result.records.toMaps should equal(Bag(
          CAPSMap("nodes" -> 6)
        ))
      }

      test("count after expand") {
        val graph = initGraph("CREATE ({name: 'foo'})-[:A]->(:B), ({name: 'bar'}), (), ()-[:A]->(:B), (), ({name: 'baz'})")

        val result = graph.cypher("MATCH (n)-->(b:B) WITH count(b) AS nodes RETURN nodes")

        result.records.toMaps should equal(Bag(
          CAPSMap("nodes" -> 2)
        ))
      }

      test("count() with grouping in RETURN clause") {
        val graph = initGraph("CREATE ({name: 'foo'}), ({name: 'foo'}), (), (), (), ({name: 'baz'})")

        val result = graph.cypher("MATCH (n) RETURN n.name as name, count(*) AS amount")

        result.records.toMaps should equal(Bag(
          CAPSMap("name" -> "foo", "amount" -> 2),
          CAPSMap("name" -> null, "amount" -> 3),
          CAPSMap("name" -> "baz", "amount" -> 1)
        ))
      }

      test("count() with grouping in WITH clause") {
        val graph = initGraph("CREATE ({name: 'foo'}), ({name: 'foo'}), (), (), (), ({name: 'baz'})")

        val result = graph.cypher("MATCH (n) WITH n.name as name, count(*) AS amount RETURN name, amount")

        result.records.toMaps should equal(Bag(
          CAPSMap("name" -> "foo", "amount" -> 2),
          CAPSMap("name" -> null, "amount" -> 3),
          CAPSMap("name" -> "baz", "amount" -> 1)
        ))
      }

      test("count() with grouping on multiple keys") {
        val graph = initGraph("CREATE ({name: 'foo', age: 42}), ({name: 'foo', age: 42}), ({name: 'foo', age: 23}), (), (), ({name: 'baz', age: 23})")

        val result = graph
            .cypher("MATCH (n) WITH n.name AS name, n.age AS age, count(*) AS amount RETURN name, age, amount")

        result.records.toMaps should equal(Bag(
          CAPSMap("name" -> "foo", "age" -> 23, "amount" -> 1),
          CAPSMap("name" -> "foo", "age" -> 42, "amount" -> 2),
          CAPSMap("name" -> "baz", "age" -> 23, "amount" -> 1),
          CAPSMap("name" -> null, "age" -> null, "amount" -> 2)
        ))
      }
    }

    describe("MIN") {

      test("min(prop) in WITH") {
        val graph = initGraph("CREATE ({val: 42}),({val: 23}),({val: 84})")

        val result = graph.cypher("MATCH (n) WITH MIN(n.val) AS res RETURN res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 23L)
        ))
      }

      test("min(prop) in RETURN") {
        val graph = initGraph("CREATE ({val: 42}),({val: 23}),({val: 84})")

        val result = graph.cypher("MATCH (n) RETURN MIN(n.val) AS res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 23L)
        ))
      }

      test("min(prop) with single null value in WITH") {
        val graph = initGraph("CREATE ({val: 42}),({val: 23}),()")

        val result = graph.cypher("MATCH (n) WITH MIN(n.val) AS res RETURN res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 23L)
        ))
      }

      test("min(prop) with single null value in RETURN") {
        val graph = initGraph("CREATE ({val: 42}),({val: 23}),()")

        val result = graph.cypher("MATCH (n) RETURN MIN(n.val) AS res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 23L)
        ))
      }

      test("min(prop) with single null value in RETURN without alias") {
        val graph = initGraph("CREATE ({val: 42}),({val: 23}),()")

        val result = graph.cypher("MATCH (n) RETURN MIN(n.val)")

        result.records.toMaps should equal(Bag(
          CAPSMap("MIN(n.val)" -> 23L)
        ))
      }

      ignore("min(prop) with only null values in WITH") {
        val graph = initGraph("CREATE ({val:NULL}),(),()")

        val result = graph.cypher("MATCH (n) WITH MIN(n.val) AS res RETURN res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> null)
        ))
      }

      ignore("min(prop) with only null values in RETURN") {
        val graph = initGraph("CREATE ({val:NULL}),(),()")

        val result = graph.cypher("MATCH (n) RETURN MIN(n.val) AS res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> null)
        ))
      }
    }

    describe("MAX") {

      test("max(prop) in WITH") {
        val graph = initGraph("CREATE ({val: 42}),({val: 23}),({val: 84})")

        val result = graph.cypher("MATCH (n) WITH MAX(n.val) AS res RETURN res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 84L)
        ))
      }

      test("max(prop) in RETURN") {
        val graph = initGraph("CREATE ({val: 42}),({val: 23}),({val: 84})")

        val result = graph.cypher("MATCH (n) RETURN MAX(n.val) AS res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 84L)
        ))
      }

      test("max(prop) with single null value in WITH") {
        val graph = initGraph("CREATE ({val: 42}),({val: 23}),()")

        val result = graph.cypher("MATCH (n) WITH MAX(n.val) AS res RETURN res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 42L)
        ))
      }

      test("max(prop) with single null value in RETURN") {
        val graph = initGraph("CREATE ({val: 42}),({val: 23}),()")

        val result = graph.cypher("MATCH (n) RETURN MAX(n.val) AS res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 42L)
        ))
      }

      test("max(prop) with single null value in RETURN without alias") {
        val graph = initGraph("CREATE ({val: 42}),({val: 23}),()")

        val result = graph.cypher("MATCH (n) RETURN MAX(n.val)")

        result.records.toMaps should equal(Bag(
          CAPSMap("MAX(n.val)" -> 42L)
        ))
      }

      ignore("simple max(prop) with only null values in WITH") {
        val graph = initGraph("CREATE ({val:NULL}),(),()")

        val result = graph.cypher("MATCH (n) WITH MAX(n.val) AS res RETURN res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> null)
        ))
      }

      ignore("simple max(prop) with only null values in RETURN") {
        val graph = initGraph("CREATE ({val:NULL}),(),()")

        val result = graph.cypher("MATCH (n) RETURN MAX(n.val) AS res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> null)
        ))
      }
    }


    describe("SUM") {

      test("sum(prop) with integers in WITH") {
        val graph = initGraph("CREATE ({val: 2}),({val: 4}),({val: 6})")

        val result = graph.cypher("MATCH (n) WITH SUM(n.val) AS res RETURN res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 12)
        ))
      }

      test("sum(prop) with integers in RETURN") {
        val graph = initGraph("CREATE ({val: 2}),({val: 4}),({val: 6})")

        val result = graph.cypher("MATCH (n) RETURN SUM(n.val) AS res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 12)
        ))
      }

      test("sum(prop) with floats in WITH") {
        val graph = initGraph("CREATE ({val:5.0D}),({val:5.0D}),({val:0.5D})")

        val result = graph.cypher("MATCH (n) WITH SUM(n.val) AS res RETURN res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 10.5)
        ))
      }

      test("sum(prop) with floats in RETURN") {
        val graph = initGraph("CREATE ({val:5.0D}),({val:5.0D}),({val:0.5D})")

        val result = graph.cypher("MATCH (n) RETURN SUM(n.val) AS res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 10.5)
        ))
      }

      test("sum(prop) with floats in RETURN without alias") {
        val graph = initGraph("CREATE ({val:5.0D}),({val:5.0D}),({val:0.5D})")

        val result = graph.cypher("MATCH (n) RETURN SUM(n.val)")

        result.records.toMaps should equal(Bag(
          CAPSMap("SUM(n.val)" -> 10.5)
        ))
      }

      test("simple sum(prop) with single null value in WITH") {
        val graph = initGraph("CREATE ({val:42.0D}),({val:23.0D}),()")

        val result = graph.cypher("MATCH (n) WITH SUM(n.val) AS res RETURN res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 65.0)
        ))
      }

      test("simple sum(prop) with single null value in RETURN") {
        val graph = initGraph("CREATE ({val:42.0D}),({val:23.0D}),()")

        val result = graph.cypher("MATCH (n) RETURN SUM(n.val) AS res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> 65.0)
        ))
      }

      ignore("simple sum(prop) with only null values in WITH") {
        val graph = initGraph("CREATE ({val:NULL}),(),()")

        val result = graph.cypher("MATCH (n) WITH SUM(n.val) AS res RETURN res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> null)
        ))
      }

      ignore("simple sum(prop) with only null values in RETURN") {
        val graph = initGraph("CREATE ({val:NULL}),(),()")

        val result = graph.cypher("MATCH (n) RETURN SUM(n.val) AS res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> null)
        ))
      }
    }

    describe("COLLECT") {

      test("collect(prop) with integers in WITH") {
        val graph = initGraph("CREATE ({val: 2}),({val: 4}),({val: 6})")

        val result = graph.cypher("MATCH (n) WITH COLLECT(n.val) AS res RETURN res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> Seq(6, 4, 2))
        ))
      }

      test("collect(prop) with integers in RETURN") {
        val graph = initGraph("CREATE ({val: 2}),({val: 4}),({val: 6})")

        val result = graph.cypher("MATCH (n) RETURN COLLECT(n.val) AS res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> Seq(6, 4, 2))
        ))
      }


      test("simple collect(prop) with single null value in WITH") {
        val graph = initGraph("CREATE ({val:42.0D}),({val:23.0D}),()")

        val result = graph.cypher("MATCH (n) WITH COLLECT(n.val) AS res RETURN res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> Seq(23.0, 42.0))
        ))
      }

      test("simple collect(prop) with single null value in RETURN") {
        val graph = initGraph("CREATE ({val:42.0D}),({val:23.0D}),()")

        val result = graph.cypher("MATCH (n) RETURN COLLECT(n.val) AS res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> Seq(23.0, 42.0))
        ))
      }


      test("simple collect(prop) with only null values in WITH") {
        val graph = initGraph("CREATE ({val:NULL}),(),()")

        val result = graph.cypher("MATCH (n) WITH Collect(n.val) AS res RETURN res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> Seq.empty)
        ))
      }

      test("simple collect(prop) with only null values in RETURN") {
        val graph = initGraph("CREATE ({val:NULL}),(),()")

        val result = graph.cypher("MATCH (n) RETURN COLLECT(n.val) AS res")

        result.records.toMaps should equal(Bag(
          CAPSMap("res" -> Seq.empty)
        ))
      }
    }

    describe("Combinations") {

      test("multiple aggregates in WITH") {
        val graph = initGraph("CREATE ({val: 42}),({val: 23}),({val: 84})")

        val result = graph.cypher(
          """MATCH (n)
            |WITH
            | AVG(n.val) AS avg,
            | COUNT(*) AS cnt,
            | MIN(n.val) AS min,
            | MAX(n.val) AS max,
            | SUM(n.val) AS sum,
            | COLLECT(n.val) AS col
            |RETURN avg, cnt, min, max, sum, col""".stripMargin)

        result.records.toMaps should equal(Bag(
          CAPSMap("avg" -> 49, "cnt" -> 3, "min" -> 23L, "max" -> 84L, "sum" -> 149, "col" -> Seq(84, 23, 42))
        ))
      }

      test("multiple aggregates in RETURN") {
        val graph = initGraph("CREATE ({val: 42}),({val: 23}),({val: 84})")

        val result = graph.cypher(
          """MATCH (n)
            |RETURN
            | AVG(n.val) AS avg,
            | COUNT(*) AS cnt,
            | MIN(n.val) AS min,
            | MAX(n.val) AS max,
            | SUM(n.val) AS sum,
            | COLLECT(n.val) AS col""".stripMargin)

        result.records.toMaps should equal(Bag(
          CAPSMap("avg" -> 49, "cnt" -> 3, "min" -> 23L, "max" -> 84L, "sum" -> 149, "col" -> Seq(84, 23, 42))
        ))
      }

      test("multiple aggregates with grouping in RETURN clause") {
        val graph = initGraph("CREATE ({key: 'a', val: 42}),({key: 'a',val: 23}),({key: 'b', val: 84})")

        val result = graph.cypher(
          """MATCH (n)
            |RETURN
            | n.key AS key,
            | AVG(n.val) AS avg,
            | COUNT(*) AS cnt,
            | MIN(n.val) AS min,
            | MAX(n.val) AS max,
            | SUM(n.val) AS sum,
            | COLLECT(n.val) as col""".stripMargin)

        result.records.toMaps should equal(Bag(
          CAPSMap(
            "key" -> "a", "avg" -> 32, "cnt" -> 2, "min" -> 23L, "max" -> 42L, "sum" -> 65, "col" -> Seq(23, 42)),
          CAPSMap("key" -> "b", "avg" -> 84, "cnt" -> 1, "min" -> 84, "max" -> 84, "sum" -> 84, "col" -> Seq(84))
        ))
      }

      test("multiple aggregates with grouping in WITH clause") {
        val graph = initGraph("CREATE ({key: 'a', val: 42}),({key: 'a',val: 23}),({key: 'b', val: 84})")

        val result = graph.cypher(
          """MATCH (n)
            |WITH
            | n.key AS key,
            | AVG(n.val) AS avg,
            | COUNT(*) AS cnt,
            | MIN(n.val) AS min,
            | MAX(n.val) AS max,
            | SUM(n.val) AS sum,
            | COLLECT(n.val) as col
            |RETURN key, avg, cnt, min, max, sum, col""".stripMargin)

        result.records.toMaps should equal(Bag(
          CAPSMap(
            "key" -> "a", "avg" -> 32, "cnt" -> 2, "min" -> 23L, "max" -> 42L, "sum" -> 65, "col" -> Seq(23, 42)),
          CAPSMap("key" -> "b", "avg" -> 84, "cnt" -> 1, "min" -> 84, "max" -> 84, "sum" -> 84, "col" -> Seq(84))
        ))
      }
    }
  }
}