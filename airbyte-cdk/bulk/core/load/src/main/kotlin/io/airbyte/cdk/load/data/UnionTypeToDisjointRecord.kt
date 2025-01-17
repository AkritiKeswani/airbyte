/*
 * Copyright (c) 2024 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.cdk.load.data

import io.airbyte.cdk.load.message.DestinationRecord

class UnionTypeToDisjointRecord : AirbyteSchemaIdentityMapper {
    override fun mapUnion(schema: UnionType): AirbyteType {
        if (schema.options.size < 2) {
            return schema
        }
        /* Create a schema of { "type": "string", "<typename(option1)>": <type(option1)>, etc... } */
        val properties = linkedMapOf("type" to FieldType(StringType, nullable = false))
        schema.options.forEach {
            val name = typeName(it)
            if (name in properties) {
                throw IllegalArgumentException("Union of types with same name: $name")
            }
            properties[typeName(it)] = FieldType(it, nullable = true)
        }
        return ObjectType(properties)
    }

    companion object {
        fun typeName(type: AirbyteType): String =
            when (type) {
                is StringType -> "string"
                is BooleanType -> "boolean"
                is IntegerType -> "integer"
                is NumberType -> "number"
                is DateType -> "date"
                is TimestampTypeWithTimezone -> "timestamp_with_timezone"
                is TimestampTypeWithoutTimezone -> "timestamp_without_timezone"
                is TimeTypeWithTimezone -> "time_with_timezone"
                is TimeTypeWithoutTimezone -> "time_without_timezone"
                is ArrayType,
                is ArrayTypeWithoutSchema -> "array"
                is ObjectType,
                is ObjectTypeWithoutSchema,
                is ObjectTypeWithEmptySchema -> "object"
                is UnionType -> "union"
                is UnknownType -> "unknown"
            }
    }
}

class UnionValueToDisjointRecord(meta: DestinationRecord.Meta) : AirbyteValueIdentityMapper(meta) {
    override fun mapUnion(
        value: AirbyteValue,
        schema: UnionType,
        path: List<String>
    ): AirbyteValue {
        if (schema.options.size < 2) {
            return value
        }

        val type =
            schema.options.find { matches(it, value) }
                ?: throw IllegalArgumentException("No matching union option in $schema for $value")
        return ObjectValue(
            values =
                linkedMapOf(
                    "type" to StringValue(UnionTypeToDisjointRecord.typeName(type)),
                    UnionTypeToDisjointRecord.typeName(type) to map(value, type, path)
                )
        )
    }

    private fun matches(schema: AirbyteType, value: AirbyteValue): Boolean {
        return when (schema) {
            is ArrayType,
            is ArrayTypeWithoutSchema -> value is ArrayValue
            is BooleanType -> value is BooleanValue
            is DateType -> value is DateValue
            is IntegerType -> value is IntegerValue
            is NumberType -> value is NumberValue
            is ObjectType,
            is ObjectTypeWithoutSchema,
            is ObjectTypeWithEmptySchema -> value is ObjectValue
            is StringType -> value is StringValue
            is TimeTypeWithTimezone,
            is TimeTypeWithoutTimezone,
            is TimestampTypeWithTimezone,
            is TimestampTypeWithoutTimezone -> value is TimeValue
            is UnionType -> schema.options.any { matches(it, value) }
            is UnknownType -> false
        }
    }
}
