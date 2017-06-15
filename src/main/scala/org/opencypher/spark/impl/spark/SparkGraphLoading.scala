package org.opencypher.spark.impl.spark

import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types._
import org.apache.spark.sql.{Row, SparkSession}
import org.neo4j.driver.internal.{InternalNode, InternalRelationship}
import org.opencypher.spark.api.expr._
import org.opencypher.spark.api.ir.QueryModel
import org.opencypher.spark.api.ir.global.{GlobalsRegistry, PropertyKey, TokenRegistry}
import org.opencypher.spark.api.record.{OpaqueField, ProjectedExpr, RecordHeader, SlotContent}
import org.opencypher.spark.api.schema.{Schema, VerifiedSchema}
import org.opencypher.spark.api.spark.{SparkCypherGraph, SparkCypherRecords, SparkCypherTokens, SparkGraphSpace}
import org.opencypher.spark.api.types._
import org.opencypher.spark.impl.record.SparkCypherRecordsTokens
import org.opencypher.spark.impl.syntax.header._
import org.opencypher.spark_legacy.benchmark.Converters.cypherValue

trait SparkGraphLoading {

  def loadSchema(nodeQ: String, relQ: String)(implicit sc: SparkSession): VerifiedSchema = {
    val (nodes, rels) = loadRDDs(nodeQ, relQ)

    loadSchema(nodes, rels)
  }

  private def loadSchema(nodes: RDD[InternalNode], rels: RDD[InternalRelationship]): VerifiedSchema = {
    import scala.collection.JavaConverters._

    val nodeSchema = nodes.aggregate(Schema.empty)({
      // TODO: what about nodes without labels?
      case (acc, next) => next.labels().asScala.foldLeft(acc) {
        case (acc2, l) =>
          // for nodes without properties
          val withLabel = acc2.withNodeKeys(l)()
          next.asMap().asScala.foldLeft(withLabel) {
            case (acc3, (k, v)) =>
              acc3.withNodeKeys(l)(k -> typeOf(v))
          }
      }
    }, _ ++ _)

    val completeSchema = rels.aggregate(nodeSchema)({
      case (acc, next) =>
        // for rels without properties
        val withType = acc.withRelationshipKeys(next.`type`())()
        next.asMap().asScala.foldLeft(withType) {
          case (acc3, (k, v)) =>
            acc3.withRelationshipKeys(next.`type`())(k -> typeOf(v))
        }
    },  _ ++ _)

    completeSchema.verify
  }

  def fromNeo4j(nodeQuery: String, relQuery: String)
               (implicit sc: SparkSession): SparkGraphSpace =
    fromNeo4j(nodeQuery, relQuery, "source", "rel", "target", None)

  def fromNeo4j(nodeQuery: String, relQuery: String, schema: VerifiedSchema)
               (implicit sc: SparkSession): SparkGraphSpace =
    fromNeo4j(nodeQuery, relQuery, "source", "rel", "target", Some(schema))


  def fromNeo4j(nodeQuery: String, relQuery: String,
                sourceNode: String, rel: String, targetNode: String,
                maybeSchema: Option[VerifiedSchema] = None)
               (implicit sc: SparkSession): SparkGraphSpace = {
    val (nodes, rels) = loadRDDs(nodeQuery, relQuery)

    val verified = maybeSchema.getOrElse(loadSchema(nodes, rels))

    implicit val context = LoadingContext(verified.schema, GlobalsRegistry(TokenRegistry.fromSchema(verified)))

    createSpace(nodes, rels, sourceNode, rel, targetNode)
  }

  private def loadRDDs(nodeQ: String, relQ: String)(implicit session: SparkSession) = {
    val neo4j = EncryptedNeo4j(session)
    val nodes = neo4j.cypher(nodeQ).loadNodeRdds.map(row => row(0).asInstanceOf[InternalNode])
    val rels = neo4j.cypher(relQ).loadRowRdd.map(row => row(0).asInstanceOf[InternalRelationship])

    nodes -> rels
  }

  case class LoadingContext(schema: Schema, globals: GlobalsRegistry)

  private def createSpace(nodes: RDD[InternalNode], rels: RDD[InternalRelationship],
                          sourceNode: String = "source", rel: String = "rel", targetNode: String = "target")
                         (implicit sparkSession: SparkSession, context: LoadingContext): SparkGraphSpace = {

    val nodeFields = (v: Var) => computeNodeFields(v)
    val nodeHeader = (v: Var) => nodeFields(v).map(_._1).foldLeft(RecordHeader.empty) {
      case (acc, next) => acc.update(addContent(next))._1
    }
    val nodeStruct = (v: Var) => StructType(nodeFields(v).map(_._2).toArray)
    val nodeRDD = (v: Var) => nodes.map(nodeToRow(nodeHeader(v), nodeStruct(v)))
    val nodeFrame = (v: Var) => {
      val slot = nodeHeader(v).slotFor(v)
      val df = sparkSession.createDataFrame(nodeRDD(v), nodeStruct(v))
      val col = df.col(df.columns(slot.index))
      df.repartition(col).sortWithinPartitions(col).cache()
    }

    val relFields = (v: Var) => computeRelFields(v)
    val relHeader = (v: Var) => relFields(v).map(_._1).foldLeft(RecordHeader.empty) {
      case (acc, next) => acc.update(addContent(next))._1
    }
    val relStruct = (v: Var) => StructType(relFields(v).map(_._2).toArray)
    val relRDD = (v: Var) => rels.map(relToRow(relHeader(v), relStruct(v)))
    val relFrame = (v: Var) => {
      val slot = nodeHeader(v).slotFor(v)
      val df = sparkSession.createDataFrame(relRDD(v), relStruct(v)).cache()
      val col = df.col(df.columns(slot.index))
      df.repartition(col).sortWithinPartitions(col).cache()
    }

    new SparkGraphSpace with Serializable {
      selfSpace =>

      override def base = new SparkCypherGraph with Serializable {
        selfBase =>

        override def nodes(v: Var) = SparkCypherRecords.create(nodeHeader(v), nodeFrame(v))(selfSpace)
        override def relationships(v: Var) = SparkCypherRecords.create(relHeader(v), relFrame(v))(selfSpace)
        override def space: SparkGraphSpace = selfSpace

        override def model: QueryModel[Expr] = QueryModel.base[Expr](sourceNode, rel, targetNode, context.globals)

        override def details: SparkCypherRecords = ???

        override def schema: Schema = context.schema
      }

      override def session = sparkSession
      override def tokens = SparkCypherRecordsTokens(context.globals.tokens)
    }
  }

  private def computeNodeFields(in: Var)(implicit context: LoadingContext): Seq[(SlotContent, StructField)] = {
    val node = Var(in.name)(CTNode)

    val schema = context.schema
    val tokens = context.globals.tokens

    val labelFields = schema.labels.map { name =>
      val label = HasLabel(node, tokens.labelByName(name))(CTBoolean)
      val slot = ProjectedExpr(label)
      val field = StructField(SparkColumnName.of(slot), BooleanType, nullable = false)
      slot -> field
    }
    val propertyFields = schema.labels.flatMap { l =>
      schema.nodeKeys(l).map {
        case (name, t) =>
          val property = Property(node, tokens.propertyKeyByName(name))(t)
          val slot = ProjectedExpr(property)
          val field = StructField(SparkColumnName.of(slot), toSparkType(t), nullable = t.isNullable)
          slot -> field
      }
    }
    val nodeSlot = OpaqueField(node)
    val nodeField = StructField(SparkColumnName.of(nodeSlot), LongType, nullable = false)
    val slotField = nodeSlot -> nodeField
    Seq(slotField) ++ labelFields ++ propertyFields
  }

  private def computeRelFields(in: Var)(implicit context: LoadingContext): Seq[(SlotContent, StructField)] = {
    val rel = Var(in.name)(CTRelationship)

    val schema = context.schema
    val tokens = context.globals.tokens

    val propertyFields = schema.relationshipTypes.flatMap { typ =>
      schema.relationshipKeys(typ).map {
        case (name, t) =>
          val property = Property(rel, tokens.propertyKeyByName(name))(t)
          val slot = ProjectedExpr(property)
          val field = StructField(SparkColumnName.of(slot), toSparkType(t), nullable = t.isNullable)
          slot -> field
      }
    }
    val typeSlot = ProjectedExpr(TypeId(rel)(CTInteger))
    val typeField = StructField(SparkColumnName.of(typeSlot), IntegerType, nullable = false)

    val idSlot = OpaqueField(rel)
    val idField = StructField(SparkColumnName.of(idSlot), LongType, nullable = false)

    val sourceSlot = ProjectedExpr(StartNode(rel)(CTNode))
    val sourceField = StructField(SparkColumnName.of(sourceSlot), LongType, nullable = false)
    val targetSlot = ProjectedExpr(EndNode(rel)(CTNode))
    val targetField = StructField(SparkColumnName.of(targetSlot), LongType, nullable = false)

    Seq(sourceSlot -> sourceField, idSlot -> idField,
      typeSlot -> typeField, targetSlot -> targetField) ++ propertyFields
  }

  private case class nodeToRow(header: RecordHeader, schema: StructType)
                              (implicit context: LoadingContext) extends (InternalNode => Row) {
    override def apply(importedNode: InternalNode): Row = {
      val graphSchema = context.schema
      val globals = context.globals

      import scala.collection.JavaConverters._

      val props = importedNode.asMap().asScala
      val labels = importedNode.labels().asScala.toSet

      val keys = labels.map(l => graphSchema.nodeKeys(l)).reduce(_ ++ _)

      val values = header.slots.map { s =>
        s.content.key match {
          case Property(_, PropertyKey(keyName)) =>
            val propValue = keys.get(keyName) match {
              case Some(t) if t == s.content.cypherType => props.get(keyName).orNull
              case _ => null
            }
            sparkValue(schema(s.index).dataType, propValue)
          case HasLabel(_, label) =>
            labels(label.name)
          case _: Var =>
            importedNode.id()

          case _ => ??? // nothing else should appear
        }
      }

      Row(values: _*)
    }
  }

  private case class relToRow(header: RecordHeader, schema: StructType)
                             (implicit context: LoadingContext) extends (InternalRelationship => Row) {
    override def apply(importedRel: InternalRelationship): Row = {
      val graphSchema = context.schema
      val tokens = context.globals.tokens

      import scala.collection.JavaConverters._

      val relType = importedRel.`type`()
      val props = importedRel.asMap().asScala

      val keys = graphSchema.relationshipKeys(relType)

      val values = header.slots.map { s =>
        s.content.key match {
          case Property(_, PropertyKey(keyName)) =>
            val propValue = keys.get(keyName) match {
              case Some(t) if t == s.content.cypherType => props.get(keyName).orNull
              case _ => null
            }
            sparkValue(schema(s.index).dataType, propValue)

          case _: StartNode =>
            importedRel.startNodeId()

          case _: EndNode =>
            importedRel.endNodeId()

          case _: TypeId =>
            tokens.relTypeRefByName(relType).id

          case _: Var =>
            importedRel.id()

          case x => throw new IllegalArgumentException(s"Header contained unexpected expression: $x")
        }
      }

      Row(values: _*)
    }
  }

  private def sparkValue(typ: DataType, value: AnyRef): Any = typ match {
    case StringType | LongType | BooleanType | DoubleType => value
    case BinaryType => if (value == null) null else value.toString.getBytes // TODO: Call kryo
    case _ => cypherValue(value)
  }

  def configureNeo4jAccess(config: SparkConf)(url: String, user: String = "", pw: String = ""): SparkConf = {
    if (url.nonEmpty) config.set("spark.neo4j.bolt.url", url)
    if (user.nonEmpty) config.set("spark.neo4j.bolt.user", user)
    if (pw.nonEmpty) config.set("spark.neo4j.bolt.password", pw) else config
  }
}
