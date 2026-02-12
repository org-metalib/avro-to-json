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
    private static final Path POJO_DIR = Path.of("target/generated-sources/avro-pojo");
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

    @Test
    void pojoClassesGenerated() {
        // Verify POJO source files were generated
        assertTrue(Files.exists(POJO_DIR), "POJO output directory should exist");

        Path comExampleDir = POJO_DIR.resolve("com/example");
        assertTrue(Files.exists(comExampleDir.resolve("User.java")),
                "User.java POJO should be generated");
        assertTrue(Files.exists(comExampleDir.resolve("Order.java")),
                "Order.java POJO should be generated");
        assertTrue(Files.exists(comExampleDir.resolve("Category.java")),
                "Category.java POJO should be generated");
    }

    @Test
    void userPojoContainsLombokAnnotations() throws IOException {
        Path userJava = POJO_DIR.resolve("com/example/User.java");
        assertTrue(Files.exists(userJava), "User.java should exist");

        String source = Files.readString(userJava);
        assertTrue(source.contains("@Data"), "User.java should have @Data annotation");
        assertTrue(source.contains("@Builder"), "User.java should have @Builder annotation");
        assertTrue(source.contains("@NoArgsConstructor"), "User.java should have @NoArgsConstructor");
        assertTrue(source.contains("@AllArgsConstructor"), "User.java should have @AllArgsConstructor");
    }

    @Test
    void userPojoContainsJacksonAnnotations() throws IOException {
        Path userJava = POJO_DIR.resolve("com/example/User.java");
        assertTrue(Files.exists(userJava), "User.java should exist");

        String source = Files.readString(userJava);
        assertTrue(source.contains("@JsonProperty"), "User.java should have @JsonProperty annotations");
        assertTrue(source.contains("@JsonInclude"), "User.java should have @JsonInclude annotation");
    }
}