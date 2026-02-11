package org.metalib.schema.avro.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.SchemaParseException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AvroToJsonSchemaConverterTest {

    private final AvroToJsonSchemaConverter converter = new AvroToJsonSchemaConverter();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testSimpleRecord() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "User",
                  "fields": [
                    {"name": "id", "type": "int"},
                    {"name": "username", "type": "string"}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        
        assertEquals("object", node.get("type").asText());
        assertEquals("User", node.get("title").asText());
        assertTrue(node.get("properties").has("id"));
        assertTrue(node.get("properties").has("username"));
        assertFalse(node.get("additionalProperties").asBoolean());
    }

    @Test
    public void testNullableField() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "OptionalUser",
                  "fields": [
                    {"name": "email", "type": ["null", "string"], "default": null}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        JsonNode emailNode = node.get("properties").get("email");

        // Nullable union should be flattened to just the non-null type
        assertEquals("string", emailNode.get("type").asText());
        assertTrue(emailNode.get("default").isNull());
        // Field should not be in required
        assertFalse(node.has("required"), "All-optional record should have no required array");
    }
    
    @Test
    public void testRecursiveRecord() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "Node",
                  "fields": [
                    {"name": "value", "type": "string"},
                    {"name": "next", "type": ["null", "Node"], "default": null}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);

        // Check for definitions
        assertTrue(node.has("definitions"));
        assertTrue(node.get("definitions").has("Node"));

        // Nullable union flattened: next should have $ref directly, not inside oneOf
        JsonNode nextNode = node.get("properties").get("next");
        assertTrue(nextNode.has("$ref"), "Should have $ref directly");
        assertEquals("#/definitions/Node", nextNode.get("$ref").asText());
        assertTrue(nextNode.get("default").isNull());
    }
    
    @Test
    public void testLogicalTypes() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "Event",
                  "fields": [
                    {"name": "id", "type": {"type": "string", "logicalType": "uuid"}},
                    {"name": "ts", "type": {"type": "long", "logicalType": "timestamp-millis"}}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        
        JsonNode idNode = node.get("properties").get("id");
        assertEquals("string", idNode.get("type").asText());
        assertEquals("uuid", idNode.get("format").asText());
        
        JsonNode tsNode = node.get("properties").get("ts");
        assertEquals("integer", tsNode.get("type").asText());
    }
    
    @Test
    public void testCustomProperties() throws Exception {
         String avroSchema = """
                {
                  "type": "record",
                  "name": "Meta",
                  "extraField": "customValue",
                  "fields": []
                }""";
         
        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        
        assertTrue(node.has("extraField"));
        assertEquals("customValue", node.get("extraField").asText());
    }

    @Test
    public void testDefaultValue() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "DefaultTest",
                  "fields": [
                    {"name": "amount", "type": "int", "default": 100},
                    {"name": "tag", "type": ["null", "string"], "default": null}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);

        JsonNode amountNode = node.get("properties").get("amount");
        assertTrue(amountNode.has("default"), "Should have default property");
        assertEquals(100, amountNode.get("default").asInt());

        // tag: nullable union flattened to just "string"
        JsonNode tagNode = node.get("properties").get("tag");
        assertEquals("string", tagNode.get("type").asText());
        assertTrue(tagNode.has("default"), "Should have default property");
        assertTrue(tagNode.get("default").isNull());
    }

    @Test
    public void testOptionalFields() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "OptionalData",
                  "fields": [
                    {"name": "optString", "type": ["null", "string"], "default": null},
                    {"name": "optInt", "type": ["int", "null"], "default": null},
                    {"name": "optRecord", "type": ["null", {
                      "type": "record",
                      "name": "Nested",
                      "fields": [{"name": "foo", "type": "string"}]
                    }], "default": null}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        JsonNode props = node.get("properties");

        // 1. optString: flattened to just "string"
        assertEquals("string", props.get("optString").get("type").asText());

        // 2. optInt: flattened to just "integer"
        assertEquals("integer", props.get("optInt").get("type").asText());

        // 3. optRecord: flattened to the record schema directly
        JsonNode optRecord = props.get("optRecord");
        assertEquals("object", optRecord.get("type").asText());
        assertEquals("Nested", optRecord.get("title").asText());
        assertTrue(optRecord.get("properties").has("foo"));

        // 4. No required array (all fields are nullable)
        assertFalse(node.has("required"), "All-optional record should have no required array");
    }

    @Test
    public void testMultiTypeUnion() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "MultiUnion",
                  "fields": [
                    {"name": "value", "type": ["null", "string", "int"]}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        JsonNode valueNode = node.get("properties").get("value");

        // True multi-type union should use oneOf
        assertTrue(valueNode.has("oneOf"), "Multi-type union should use oneOf");
        assertEquals(3, valueNode.get("oneOf").size());
    }

    @Test
    public void testAdditionalPropertiesFalse() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "Strict",
                  "fields": [
                    {"name": "name", "type": "string"}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);

        assertTrue(node.has("additionalProperties"));
        assertFalse(node.get("additionalProperties").asBoolean());
    }

    @Test
    public void testJavaTypeHints() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "AllLogical",
                  "fields": [
                    {"name": "id", "type": {"type": "string", "logicalType": "uuid"}},
                    {"name": "ts", "type": {"type": "long", "logicalType": "timestamp-millis"}},
                    {"name": "tsMicro", "type": {"type": "long", "logicalType": "timestamp-micros"}},
                    {"name": "d", "type": {"type": "int", "logicalType": "date"}},
                    {"name": "t", "type": {"type": "int", "logicalType": "time-millis"}},
                    {"name": "tMicro", "type": {"type": "long", "logicalType": "time-micros"}},
                    {"name": "amount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 10, "scale": 2}}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        JsonNode props = node.get("properties");

        assertEquals("java.util.UUID", props.get("id").get("javaType").asText());
        assertEquals("java.time.Instant", props.get("ts").get("javaType").asText());
        assertEquals("java.time.Instant", props.get("tsMicro").get("javaType").asText());
        assertEquals("java.time.LocalDate", props.get("d").get("javaType").asText());
        assertEquals("java.time.LocalTime", props.get("t").get("javaType").asText());
        assertEquals("java.time.LocalTime", props.get("tMicro").get("javaType").asText());
        assertEquals("java.math.BigDecimal", props.get("amount").get("javaType").asText());
    }

    @Test
    public void testStrictModeNoJavaTypeHints() throws Exception {
        AvroToJsonSchemaConverter strictConverter = new AvroToJsonSchemaConverter(ConverterOptions.strict());
        String avroSchema = """
                {
                  "type": "record",
                  "name": "Event",
                  "fields": [
                    {"name": "id", "type": {"type": "string", "logicalType": "uuid"}},
                    {"name": "ts", "type": {"type": "long", "logicalType": "timestamp-millis"}}
                  ]
                }""";

        String jsonSchema = strictConverter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        JsonNode props = node.get("properties");

        assertFalse(props.get("id").has("javaType"));
        assertFalse(props.get("ts").has("javaType"));
    }

    @Test
    public void testStrictModeNullableUnionTypeArray() throws Exception {
        AvroToJsonSchemaConverter strictConverter = new AvroToJsonSchemaConverter(ConverterOptions.strict());
        String avroSchema = """
                {
                  "type": "record",
                  "name": "Test",
                  "fields": [
                    {"name": "email", "type": ["null", "string"], "default": null}
                  ]
                }""";

        String jsonSchema = strictConverter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        JsonNode emailType = node.get("properties").get("email").get("type");

        assertTrue(emailType.isArray());
        assertEquals(2, emailType.size());
        assertEquals("null", emailType.get(0).asText());
        assertEquals("string", emailType.get(1).asText());
    }

    @Test
    public void testStrictModeNoAdditionalPropertiesFalse() throws Exception {
        AvroToJsonSchemaConverter strictConverter = new AvroToJsonSchemaConverter(ConverterOptions.strict());
        String avroSchema = """
                {
                  "type": "record",
                  "name": "Open",
                  "fields": [
                    {"name": "name", "type": "string"}
                  ]
                }""";

        String jsonSchema = strictConverter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);

        assertFalse(node.has("additionalProperties"));
    }

    @Test
    public void testStrictModeKeepsEmptyRequired() throws Exception {
        AvroToJsonSchemaConverter strictConverter = new AvroToJsonSchemaConverter(ConverterOptions.strict());
        String avroSchema = """
                {
                  "type": "record",
                  "name": "AllOptional",
                  "fields": [
                    {"name": "tag", "type": ["null", "string"], "default": null}
                  ]
                }""";

        String jsonSchema = strictConverter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);

        assertTrue(node.has("required"));
        assertTrue(node.get("required").isArray());
        assertEquals(0, node.get("required").size());
    }

    @Test
    public void testStrictModeNullableRecordUsesOneOf() throws Exception {
        AvroToJsonSchemaConverter strictConverter = new AvroToJsonSchemaConverter(ConverterOptions.strict());
        String avroSchema = """
                {
                  "type": "record",
                  "name": "Wrapper",
                  "fields": [
                    {"name": "nested", "type": ["null", {
                      "type": "record",
                      "name": "Inner",
                      "fields": [{"name": "x", "type": "int"}]
                    }], "default": null}
                  ]
                }""";

        String jsonSchema = strictConverter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        JsonNode nestedNode = node.get("properties").get("nested");

        assertTrue(nestedNode.has("oneOf"));
        assertEquals(2, nestedNode.get("oneOf").size());
        assertEquals("null", nestedNode.get("oneOf").get(0).get("type").asText());
        assertEquals("object", nestedNode.get("oneOf").get(1).get("type").asText());
    }

    @Test
    public void testDurationLogicalType() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "DurationTest",
                  "fields": [
                    {"name": "interval", "type": {"type": "fixed", "size": 12, "name": "duration", "logicalType": "duration"}}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        JsonNode intervalNode = node.get("properties").get("interval");

        assertEquals("string", intervalNode.get("type").asText());
        assertEquals("duration", intervalNode.get("format").asText());
        assertEquals("java.time.Duration", intervalNode.get("javaType").asText());
    }

    @Test
    public void testDurationLogicalTypeStrictMode() throws Exception {
        AvroToJsonSchemaConverter strictConverter = new AvroToJsonSchemaConverter(ConverterOptions.strict());
        String avroSchema = """
                {
                  "type": "record",
                  "name": "DurationStrictTest",
                  "fields": [
                    {"name": "interval", "type": {"type": "fixed", "size": 12, "name": "duration", "logicalType": "duration"}}
                  ]
                }""";

        String jsonSchema = strictConverter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        JsonNode intervalNode = node.get("properties").get("interval");

        assertEquals("string", intervalNode.get("type").asText());
        assertEquals("duration", intervalNode.get("format").asText());
        assertFalse(intervalNode.has("javaType"));
    }

    @Test
    public void testFixedWithoutLogicalTypeStillBase64() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "FixedTest",
                  "fields": [
                    {"name": "hash", "type": {"type": "fixed", "size": 16, "name": "hash16"}}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        JsonNode hashNode = node.get("properties").get("hash");

        assertEquals("string", hashNode.get("type").asText());
        assertEquals("base64", hashNode.get("contentEncoding").asText());
    }

    @Test
    public void testDecimalOnBytesType() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "DecimalBytesTest",
                  "fields": [
                    {"name": "amount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 10, "scale": 2}}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        JsonNode amountNode = node.get("properties").get("amount");

        assertEquals("number", amountNode.get("type").asText());
        assertFalse(amountNode.has("contentEncoding"), "decimal on bytes should not have contentEncoding");
        assertEquals("java.math.BigDecimal", amountNode.get("javaType").asText());
    }

    @Test
    public void testDraft202012SchemaUrl() throws Exception {
        AvroToJsonSchemaConverter draft2020Converter = new AvroToJsonSchemaConverter(
                ConverterOptions.pojoOptimized().withDraft(JsonSchemaDraft.DRAFT_2020_12));
        String avroSchema = """
                {
                  "type": "record",
                  "name": "Simple",
                  "fields": [{"name": "id", "type": "int"}]
                }""";

        String jsonSchema = draft2020Converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);

        assertEquals("https://json-schema.org/draft/2020-12/schema", node.get("$schema").asText());
    }

    @Test
    public void testDraft202012UsesDefsNotDefinitions() throws Exception {
        AvroToJsonSchemaConverter draft2020Converter = new AvroToJsonSchemaConverter(
                ConverterOptions.pojoOptimized().withDraft(JsonSchemaDraft.DRAFT_2020_12));
        String avroSchema = """
                {
                  "type": "record",
                  "name": "Node",
                  "fields": [
                    {"name": "value", "type": "string"},
                    {"name": "next", "type": ["null", "Node"], "default": null}
                  ]
                }""";

        String jsonSchema = draft2020Converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);

        assertFalse(node.has("definitions"), "Should not have 'definitions'");
        assertTrue(node.has("$defs"), "Should have '$defs'");
        assertTrue(node.get("$defs").has("Node"));

        JsonNode nextNode = node.get("properties").get("next");
        assertEquals("#/$defs/Node", nextNode.get("$ref").asText());
    }

    @Test
    public void testDraft07BackwardCompatibility() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "Node",
                  "fields": [
                    {"name": "value", "type": "string"},
                    {"name": "next", "type": ["null", "Node"], "default": null}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);

        assertEquals("http://json-schema.org/draft-07/schema#", node.get("$schema").asText());
        assertTrue(node.has("definitions"), "Default should use 'definitions'");
        assertFalse(node.has("$defs"));
    }

    @Test
    public void testConverterOptionsWithDraft() {
        ConverterOptions base = ConverterOptions.pojoOptimized();
        ConverterOptions modified = base.withDraft(JsonSchemaDraft.DRAFT_2020_12);

        assertEquals(JsonSchemaDraft.DRAFT_2020_12, modified.draft());
        assertEquals(base.flattenNullableUnions(), modified.flattenNullableUnions());
        assertEquals(base.additionalPropertiesFalse(), modified.additionalPropertiesFalse());
        assertEquals(base.omitEmptyRequired(), modified.omitEmptyRequired());
        assertEquals(base.javaTypeHints(), modified.javaTypeHints());
    }

    @Test
    public void testMalformedJsonThrowsException() {
        assertThrows(Exception.class, () -> converter.convert("not valid json at all"));
    }

    @Test
    public void testMissingTypeFieldThrowsException() {
        assertThrows(SchemaParseException.class, () -> converter.convert("{\"name\": \"NoType\"}"));
    }

    @Test
    public void testNamespacedRecordRef() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "Tree",
                  "namespace": "com.example",
                  "fields": [
                    {"name": "value", "type": "int"},
                    {"name": "left", "type": ["null", "Tree"], "default": null},
                    {"name": "right", "type": ["null", "Tree"], "default": null}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);

        assertTrue(node.get("definitions").has("com.example.Tree"));
        JsonNode leftNode = node.get("properties").get("left");
        assertEquals("#/definitions/com.example.Tree", leftNode.get("$ref").asText());
    }

    @Test
    public void testEnumWithDoc() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "EnumHolder",
                  "fields": [
                    {
                      "name": "status",
                      "type": {
                        "type": "enum",
                        "name": "Status",
                        "doc": "Order status",
                        "symbols": ["PENDING", "ACTIVE", "CLOSED"]
                      }
                    }
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        JsonNode statusNode = node.get("properties").get("status");

        assertEquals("string", statusNode.get("type").asText());
        assertEquals(3, statusNode.get("enum").size());
        assertEquals("PENDING", statusNode.get("enum").get(0).asText());
        assertEquals("Order status", statusNode.get("description").asText());
    }

    @Test
    public void testArrayType() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "ListHolder",
                  "fields": [
                    {"name": "tags", "type": {"type": "array", "items": "string"}}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        JsonNode tagsNode = node.get("properties").get("tags");

        assertEquals("array", tagsNode.get("type").asText());
        assertEquals("string", tagsNode.get("items").get("type").asText());
    }

    @Test
    public void testMapType() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "MapHolder",
                  "fields": [
                    {"name": "metadata", "type": {"type": "map", "values": "int"}}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        JsonNode metaNode = node.get("properties").get("metadata");

        assertEquals("object", metaNode.get("type").asText());
        assertEquals("integer", metaNode.get("additionalProperties").get("type").asText());
    }

    @Test
    public void testBytesType() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "BinaryHolder",
                  "fields": [
                    {"name": "data", "type": "bytes"}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        JsonNode dataNode = node.get("properties").get("data");

        assertEquals("string", dataNode.get("type").asText());
        assertEquals("base64", dataNode.get("contentEncoding").asText());
    }

    @Test
    public void testAvroInternalPropsNotLeaked() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "DecimalTest",
                  "fields": [
                    {"name": "amount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 10, "scale": 2}}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);
        JsonNode amountNode = node.get("properties").get("amount");

        assertEquals("number", amountNode.get("type").asText());
        assertFalse(amountNode.has("logicalType"), "logicalType should be filtered from output");
        assertFalse(amountNode.has("precision"), "precision should be filtered from output");
        assertFalse(amountNode.has("scale"), "scale should be filtered from output");
    }

    @Test
    public void testRecordDocumentation() throws Exception {
        String avroSchema = """
                {
                  "type": "record",
                  "name": "Documented",
                  "doc": "A well-documented record",
                  "fields": [
                    {"name": "x", "type": "int"}
                  ]
                }""";

        String jsonSchema = converter.convert(avroSchema);
        JsonNode node = mapper.readTree(jsonSchema);

        assertEquals("A well-documented record", node.get("description").asText());
    }

    @Test
    public void testConverterOptionsPresets() {
        ConverterOptions pojo = ConverterOptions.pojoOptimized();
        assertTrue(pojo.flattenNullableUnions());
        assertTrue(pojo.additionalPropertiesFalse());
        assertTrue(pojo.omitEmptyRequired());
        assertTrue(pojo.javaTypeHints());
        assertEquals(JsonSchemaDraft.DRAFT_07, pojo.draft());

        ConverterOptions strict = ConverterOptions.strict();
        assertFalse(strict.flattenNullableUnions());
        assertFalse(strict.additionalPropertiesFalse());
        assertFalse(strict.omitEmptyRequired());
        assertFalse(strict.javaTypeHints());
        assertEquals(JsonSchemaDraft.DRAFT_07, strict.draft());
    }
}
