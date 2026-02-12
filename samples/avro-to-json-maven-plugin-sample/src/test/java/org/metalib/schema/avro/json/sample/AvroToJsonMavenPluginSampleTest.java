package org.metalib.schema.avro.json.sample;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AvroToJsonMavenPluginSampleTest {

    private static final Path OUTPUT_DIR = Path.of("target/generated-resources/json-schema");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void userJsonSchemaGenerated() throws IOException {
        final Path userSchema = OUTPUT_DIR.resolve("User.json");
        assertTrue(Files.exists(userSchema), "User.json should be generated");

        final JsonNode root = MAPPER.readTree(userSchema.toFile());
        assertEquals("object", root.get("type").asText());
        assertTrue(root.has("properties"));
        assertTrue(root.get("properties").has("id"));
        assertTrue(root.get("properties").has("username"));
        assertTrue(root.get("properties").has("email"));
        assertTrue(root.get("properties").has("createdAt"));
    }

    @Test
    void orderJsonSchemaGenerated() throws IOException {
        final Path orderSchema = OUTPUT_DIR.resolve("Order.json");
        assertTrue(Files.exists(orderSchema), "Order.json should be generated");

        final JsonNode root = MAPPER.readTree(orderSchema.toFile());
        assertEquals("object", root.get("type").asText());
        assertTrue(root.get("properties").has("orderId"));
        assertTrue(root.get("properties").has("items"));
        assertTrue(root.get("properties").has("metadata"));
        assertTrue(root.get("properties").has("status"));
    }

    @Test
    void categoryJsonSchemaGenerated() throws IOException {
        final Path categorySchema = OUTPUT_DIR.resolve("Category.json");
        assertTrue(Files.exists(categorySchema), "Category.json should be generated");

        final JsonNode root = MAPPER.readTree(categorySchema.toFile());
        assertEquals("object", root.get("type").asText());
        assertTrue(root.get("properties").has("name"));
        assertTrue(root.get("properties").has("parent"));
        // Recursive type should use $ref
        assertTrue(root.has("definitions") || root.has("$defs"),
                "Recursive schema should have definitions");
    }
}
