/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.util

import org.apache.arrow.memory.RootAllocator
import org.apache.arrow.vector.complex.MapVector
import org.apache.arrow.vector.types.pojo.{ArrowType, Field, FieldType, Schema}
import org.apache.arrow.vector.types.{DateUnit, FloatingPointPrecision, TimeUnit}
import org.apache.spark.sql.types._

import scala.collection.JavaConverters._

/**
 * Copied from Spark 3.1.2. To avoid the package conflicts between spark 2 and spark 3.
 */
object DorisArrowUtils {

  val rootAllocator = new RootAllocator(Long.MaxValue)

  def toArrowSchema(schema: StructType, timeZoneId: String): Schema = {
    new Schema(schema.map { field =>
      toArrowField(field.name, field.dataType, field.nullable, timeZoneId)
    }.asJava)
  }

  def toArrowField(
                    name: String, dt: DataType, nullable: Boolean, timeZoneId: String): Field = {
    dt match {
      case ArrayType(elementType, containsNull) =>
        val fieldType = new FieldType(nullable, ArrowType.List.INSTANCE, null)
        new Field(name, fieldType,
          Seq(toArrowField("element", elementType, containsNull, timeZoneId)).asJava)
      case StructType(fields) =>
        val fieldType = new FieldType(nullable, ArrowType.Struct.INSTANCE, null)
        new Field(name, fieldType,
          fields.map { field =>
            toArrowField(field.name, field.dataType, field.nullable, timeZoneId)
          }.toSeq.asJava)
      case MapType(keyType, valueType, valueContainsNull) =>
        val mapType = new FieldType(nullable, new ArrowType.Map(false), null)
        // Note: Map Type struct can not be null, Struct Type key field can not be null
        new Field(name, mapType,
          Seq(toArrowField(MapVector.DATA_VECTOR_NAME,
            new StructType()
              .add(MapVector.KEY_NAME, keyType, nullable = false)
              .add(MapVector.VALUE_NAME, valueType, nullable = valueContainsNull),
            nullable = false,
            timeZoneId)).asJava)
      case dataType =>
        val fieldType = new FieldType(nullable, toArrowType(dataType, timeZoneId), null)
        new Field(name, fieldType, Seq.empty[Field].asJava)
    }
  }

  def toArrowType(dt: DataType, timeZoneId: String): ArrowType = dt match {
    case BooleanType => ArrowType.Bool.INSTANCE
    case ByteType => new ArrowType.Int(8, true)
    case ShortType => new ArrowType.Int(8 * 2, true)
    case IntegerType => new ArrowType.Int(8 * 4, true)
    case LongType => new ArrowType.Int(8 * 8, true)
    case FloatType => new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE)
    case DoubleType => new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)
    case StringType => ArrowType.Utf8.INSTANCE
    case BinaryType => ArrowType.Binary.INSTANCE
    case DecimalType.Fixed(precision, scale) => new ArrowType.Decimal(precision, scale, 128)
    case DateType => new ArrowType.Date(DateUnit.DAY)
    case TimestampType =>
      if (timeZoneId == null) {
        throw new UnsupportedOperationException(
          s"${TimestampType.catalogString} must supply timeZoneId parameter")
      } else {
        new ArrowType.Timestamp(TimeUnit.MICROSECOND, timeZoneId)
      }
    case _ =>
      throw new UnsupportedOperationException(s"Unsupported data type: ${dt.catalogString}")
  }

  def fromArrowField(field: Field): DataType = {
    field.getType match {
      case _: ArrowType.Map =>
        val elementField = field.getChildren.get(0)
        val keyType = fromArrowField(elementField.getChildren.get(0))
        val valueType = fromArrowField(elementField.getChildren.get(1))
        MapType(keyType, valueType, elementField.getChildren.get(1).isNullable)
      case ArrowType.List.INSTANCE =>
        val elementField = field.getChildren().get(0)
        val elementType = fromArrowField(elementField)
        ArrayType(elementType, containsNull = elementField.isNullable)
      case ArrowType.Struct.INSTANCE =>
        val fields = field.getChildren().asScala.map { child =>
          val dt = fromArrowField(child)
          StructField(child.getName, dt, child.isNullable)
        }
        StructType(fields)
      case arrowType => fromArrowType(arrowType)
    }
  }

  def fromArrowType(dt: ArrowType): DataType = dt match {
    case ArrowType.Bool.INSTANCE => BooleanType
    case int: ArrowType.Int if int.getIsSigned && int.getBitWidth == 8 => ByteType
    case int: ArrowType.Int if int.getIsSigned && int.getBitWidth == 8 * 2 => ShortType
    case int: ArrowType.Int if int.getIsSigned && int.getBitWidth == 8 * 4 => IntegerType
    case int: ArrowType.Int if int.getIsSigned && int.getBitWidth == 8 * 8 => LongType
    case float: ArrowType.FloatingPoint
      if float.getPrecision() == FloatingPointPrecision.SINGLE => FloatType
    case float: ArrowType.FloatingPoint
      if float.getPrecision() == FloatingPointPrecision.DOUBLE => DoubleType
    case ArrowType.Utf8.INSTANCE => StringType
    case ArrowType.Binary.INSTANCE => BinaryType
    case d: ArrowType.Decimal => DecimalType(d.getPrecision, d.getScale)
    case date: ArrowType.Date if date.getUnit == DateUnit.DAY => DateType
    case ts: ArrowType.Timestamp if ts.getUnit == TimeUnit.MICROSECOND => TimestampType
    case _ => throw new UnsupportedOperationException(s"Unsupported data type: $dt")
  }

}
