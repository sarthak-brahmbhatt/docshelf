// Builds an OpenAI strict-mode JSON schema (all properties required, additionalProperties=false) from a Java record class
package com.docshelf.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class JsonSchemaGenerator {

    private JsonSchemaGenerator() {
    }

    /** Root schema for a record class; nested records, enums, lists, Optional/@SchemaNullable and dates are supported. */
    public static Map<String, Object> generate(Class<?> recordClass) {
        if (!recordClass.isRecord()) {
            throw new IllegalArgumentException("Schema root must be a record: " + recordClass.getName());
        }
        return objectSchema(recordClass, new HashSet<>());
    }

    private static Map<String, Object> objectSchema(Class<?> record, Set<Class<?>> stack) {
        if (!stack.add(record)) {
            throw new IllegalArgumentException("Recursive record types are not supported by strict schemas: " + record.getName());
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        SchemaDescription typeDesc = record.getAnnotation(SchemaDescription.class);
        if (typeDesc != null) {
            schema.put("description", typeDesc.value());
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (RecordComponent rc : record.getRecordComponents()) {
            String name = propertyName(rc);
            boolean nullable = rc.isAnnotationPresent(SchemaNullable.class) || rc.getType() == Optional.class;
            Map<String, Object> prop = schemaFor(rc.getGenericType(), nullable, stack);
            SchemaDescription desc = rc.getAnnotation(SchemaDescription.class);
            if (desc != null) {
                prop.put("description", desc.value());
            }
            properties.put(name, prop);
            required.add(name);
        }
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        stack.remove(record);
        return schema;
    }

    private static String propertyName(RecordComponent rc) {
        JsonProperty jp = rc.getAnnotation(JsonProperty.class);
        if (jp != null && !jp.value().isEmpty()) {
            return jp.value();
        }
        try {
            JsonProperty onAccessor = rc.getAccessor().getAnnotation(JsonProperty.class);
            if (onAccessor != null && !onAccessor.value().isEmpty()) {
                return onAccessor.value();
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return rc.getName();
    }

    private static Map<String, Object> schemaFor(Type type, boolean nullable, Set<Class<?>> stack) {
        if (type instanceof ParameterizedType pt) {
            Class<?> raw = (Class<?>) pt.getRawType();
            if (raw == Optional.class) {
                return schemaFor(pt.getActualTypeArguments()[0], true, stack);
            }
            if (Collection.class.isAssignableFrom(raw)) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("type", nullable ? List.of("array", "null") : "array");
                s.put("items", schemaFor(pt.getActualTypeArguments()[0], false, stack));
                return s;
            }
            if (Map.class.isAssignableFrom(raw)) {
                throw new IllegalArgumentException("Map is not representable in a strict schema; use a List of records instead");
            }
            return schemaFor(raw, nullable, stack);
        }
        if (type instanceof Class<?> c) {
            if (c.isArray()) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("type", nullable ? List.of("array", "null") : "array");
                s.put("items", schemaFor(c.getComponentType(), false, stack));
                return s;
            }
            if (c.isEnum()) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("type", nullable ? List.of("string", "null") : "string");
                List<Object> values = new ArrayList<>();
                for (Object e : c.getEnumConstants()) {
                    values.add(((Enum<?>) e).name());
                }
                if (nullable) {
                    values.add(null);
                }
                s.put("enum", values);
                return s;
            }
            if (c.isRecord()) {
                Map<String, Object> s = objectSchema(c, stack);
                if (nullable) {
                    s.put("type", List.of("object", "null"));
                }
                return s;
            }
            String primitive = primitiveType(c);
            if (primitive != null) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("type", nullable ? List.of(primitive, "null") : primitive);
                String format = formatHint(c);
                if (format != null) {
                    s.put("description", format);
                }
                return s;
            }
            if (Map.class.isAssignableFrom(c)) {
                throw new IllegalArgumentException("Map is not representable in a strict schema; use a List of records instead");
            }
        }
        throw new IllegalArgumentException("Unsupported schema type: " + type.getTypeName());
    }

    private static String primitiveType(Class<?> c) {
        if (c == String.class || c == UUID.class || c == LocalDate.class || c == LocalDateTime.class
                || c == LocalTime.class || c == OffsetDateTime.class || c == Instant.class || c == char.class
                || c == Character.class) {
            return "string";
        }
        if (c == int.class || c == Integer.class || c == long.class || c == Long.class || c == short.class
                || c == Short.class || c == byte.class || c == Byte.class || c == BigInteger.class) {
            return "integer";
        }
        if (c == double.class || c == Double.class || c == float.class || c == Float.class
                || c == BigDecimal.class) {
            return "number";
        }
        if (c == boolean.class || c == Boolean.class) {
            return "boolean";
        }
        return null;
    }

    private static String formatHint(Class<?> c) {
        if (c == LocalDate.class) {
            return "ISO-8601 date, yyyy-MM-dd";
        }
        if (c == LocalDateTime.class || c == OffsetDateTime.class || c == Instant.class) {
            return "ISO-8601 date-time";
        }
        if (c == LocalTime.class) {
            return "ISO-8601 time, HH:mm";
        }
        if (c == UUID.class) {
            return "UUID";
        }
        return null;
    }

    /** Schema name used in the OpenAI request (letters, digits, underscores). */
    public static String schemaName(Class<?> type) {
        return type.getSimpleName().replaceAll("[^A-Za-z0-9_]", "_");
    }
}
