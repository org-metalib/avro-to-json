package org.metalib.schema.avro.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;

import java.util.*;

public class AvroToJsonSchemaConverter {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Set<String> AVRO_INTERNAL_PROPS = Set.of("logicalType", "precision", "scale", "connect.parameters");

    private final ConverterOptions options;

    public AvroToJsonSchemaConverter() {
        this(ConverterOptions.pojoOptimized());
    }

    public AvroToJsonSchemaConverter(ConverterOptions options) {
        this.options = options;
    }

    public String convert(String avroSchemaJson) {
        Schema schema = new Schema.Parser().parse(avroSchemaJson);
        ObjectNode jsonSchema = mapper.createObjectNode();
        jsonSchema.put("$schema", options.draft().schemaUrl());

        ConversionContext context = new ConversionContext();
        ObjectNode root = convert(schema, context);

        // If definitions exist, add them to the root
        if (!context.definitions().isEmpty()) {
            ObjectNode definitionsNode = jsonSchema.putObject(options.draft().definitionsKeyword());
            context.definitions().forEach(definitionsNode::set);
        }

        jsonSchema.setAll(root);
        return jsonSchema.toPrettyString();
    }

    private ObjectNode convert(Schema schema, ConversionContext context) {
        // Handle Recursive Records
        if (schema.getType() == Schema.Type.RECORD) {
            String name = schema.getFullName();
            if (context.seenRecords().contains(name)) {
                ObjectNode refNode = mapper.createObjectNode();
                refNode.put("$ref", options.draft().refPrefix() + name);
                return refNode;
            }
            context.seenRecords().add(name);
        }

        ObjectNode node = mapper.createObjectNode();

        // 1. Handle Logical Types
        handleLogicalType(node, schema);

        // 2. Standard Type Mapping
        switch (schema.getType()) {
            case RECORD -> {
                node.put("type", "object");
                node.put("title", schema.getName());
                ObjectNode properties = node.putObject("properties");
                ArrayNode required = node.putArray("required");
                for (Schema.Field field : schema.getFields()) {
                    properties.set(field.name(), convert(field.schema(), context));
                    if (!isNullable(field.schema())) {
                        required.add(field.name());
                    }
                    if (field.hasDefaultValue()) {
                        Object defaultValue = field.defaultVal();
                        JsonNode nodeToUpdate = properties.get(field.name());
                        if (nodeToUpdate.isObject()) {
                            JsonNode defaultNode = (defaultValue == null || defaultValue.getClass().getName().equals("org.apache.avro.JsonProperties$Null"))
                                    ? mapper.nullNode()
                                    : mapper.valueToTree(defaultValue);
                            ((ObjectNode) nodeToUpdate).set("default", defaultNode);
                        }
                    }
                }
                if (options.omitEmptyRequired() && required.isEmpty()) {
                    node.remove("required");
                }
                if (options.additionalPropertiesFalse()) {
                    node.put("additionalProperties", false);
                }
                // Save to definitions if it's a named record
                context.definitions().put(schema.getFullName(), node.deepCopy());
            }
            case ARRAY -> {
                node.put("type", "array");
                node.set("items", convert(schema.getElementType(), context));
            }
            case MAP -> {
                node.put("type", "object");
                node.set("additionalProperties", convert(schema.getValueType(), context));
            }
            case ENUM -> {
                node.put("type", "string");
                ArrayNode enumValues = node.putArray("enum");
                schema.getEnumSymbols().forEach(enumValues::add);
            }
            case UNION -> handleUnion(node, schema, context);
            case STRING -> {
                if (!node.has("type")) node.put("type", "string");
            }
            case INT, LONG -> {
                if (!node.has("type")) node.put("type", "integer");
            }
            case FLOAT, DOUBLE -> {
                if (!node.has("type")) node.put("type", "number");
            }
            case BOOLEAN -> node.put("type", "boolean");
            case NULL -> node.put("type", "null");
            case BYTES, FIXED -> {
                if (!node.has("type")) {
                    node.put("type", "string");
                    node.put("contentEncoding", "base64");
                }
            }
        }

        // 3. Metadata & Docs
        if (schema.getDoc() != null) {
            node.put("description", schema.getDoc());
        }

        // 4. Custom Properties (filter out Avro-internal props already handled above)
        for (Map.Entry<String, Object> entry : schema.getObjectProps().entrySet()) {
            if (!AVRO_INTERNAL_PROPS.contains(entry.getKey())) {
                node.putPOJO(entry.getKey(), entry.getValue());
            }
        }

        return node;
    }

    private boolean handleLogicalType(ObjectNode node, Schema schema) {
        LogicalType logicalType = schema.getLogicalType();
        String name;
        if (logicalType != null) {
            name = logicalType.getName();
        } else {
            // Fallback: Avro may not register all logical types (e.g. duration)
            String prop = schema.getProp("logicalType");
            if (prop == null) return false;
            name = prop;
        }

        switch (name) {
            case "decimal" -> {
                node.put("type", "number");
                if (options.javaTypeHints()) node.put("javaType", "java.math.BigDecimal");
                return true;
            }
            case "timestamp-millis", "timestamp-micros" -> {
                node.put("type", "integer");
                node.put("format", "utc-millisec");
                if (options.javaTypeHints()) node.put("javaType", "java.time.Instant");
                return true;
            }
            case "date" -> {
                node.put("type", "string");
                node.put("format", "date");
                if (options.javaTypeHints()) node.put("javaType", "java.time.LocalDate");
                return true;
            }
            case "time-millis", "time-micros" -> {
                node.put("type", "string");
                node.put("format", "time");
                if (options.javaTypeHints()) node.put("javaType", "java.time.LocalTime");
                return true;
            }
            case "uuid" -> {
                node.put("type", "string");
                node.put("format", "uuid");
                if (options.javaTypeHints()) node.put("javaType", "java.util.UUID");
                return true;
            }
            case "duration" -> {
                node.put("type", "string");
                node.put("format", "duration");
                if (options.javaTypeHints()) node.put("javaType", "java.time.Duration");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void handleUnion(ObjectNode node, Schema schema, ConversionContext context) {
        List<Schema> types = schema.getTypes();
        List<Schema> nonNullTypes = types.stream()
                .filter(s -> s.getType() != Schema.Type.NULL)
                .toList();
        boolean hasNull = types.size() != nonNullTypes.size();

        if (options.flattenNullableUnions()) {
            // POJO mode: flatten ["null", X] → just X's schema
            if (hasNull && nonNullTypes.size() == 1) {
                ObjectNode inner = convert(nonNullTypes.get(0), context);
                node.setAll(inner);
                return;
            }
        } else {
            // Strict mode
            if (hasNull && nonNullTypes.size() == 1) {
                Schema inner = nonNullTypes.get(0);
                if (isSimpleType(inner)) {
                    ArrayNode typeArray = mapper.createArrayNode();
                    typeArray.add("null");
                    typeArray.add(mapSimpleTypeName(inner));
                    node.set("type", typeArray);
                    return;
                }
                // Complex nullable type → oneOf
                ArrayNode oneOf = node.putArray("oneOf");
                oneOf.add(mapper.createObjectNode().put("type", "null"));
                oneOf.add(convert(inner, context));
                return;
            }
        }

        // True multi-type union → oneOf
        ArrayNode oneOf = node.putArray("oneOf");
        for (Schema subSchema : types) {
            oneOf.add(convert(subSchema, context));
        }
    }

    private boolean isSimpleType(Schema schema) {
        return switch (schema.getType()) {
            case STRING, INT, LONG, FLOAT, DOUBLE, BOOLEAN, NULL, BYTES -> true;
            default -> false;
        };
    }

    private String mapSimpleTypeName(Schema schema) {
        return switch (schema.getType()) {
            case STRING -> "string";
            case INT, LONG -> "integer";
            case FLOAT, DOUBLE -> "number";
            case BOOLEAN -> "boolean";
            case NULL -> "null";
            case BYTES -> "string";
            default -> throw new IllegalArgumentException("Not a simple type: " + schema.getType());
        };
    }

    private boolean isNullable(Schema schema) {
        if (schema.getType() == Schema.Type.UNION) {
            return schema.getTypes().stream().anyMatch(s -> s.getType() == Schema.Type.NULL);
        }
        return schema.getType() == Schema.Type.NULL;
    }

    // Context record to hold state during a single conversion
    private record ConversionContext(Map<String, JsonNode> definitions, Set<String> seenRecords) {
        ConversionContext() {
            this(new HashMap<>(), new HashSet<>());
        }
    }
}
