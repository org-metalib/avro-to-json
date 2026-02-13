package org.metalib.schema.avro.json.sample;

import com.example.Category;
import com.example.Order;
import com.example.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    // --- Lombok-generated code tests ---

    @Test
    void userBuilderAndGetters() {
        User user = User.builder()
                .id(1)
                .username("alice")
                .email("alice@example.com")
                .createdAt(1700000000)
                .build();

        assertEquals(1, user.getId());
        assertEquals("alice", user.getUsername());
        assertEquals("alice@example.com", user.getEmail());
        assertEquals(1700000000, user.getCreatedAt());
    }

    @Test
    void userSetters() {
        User user = new User();
        user.setId(2);
        user.setUsername("bob");

        assertEquals(2, user.getId());
        assertEquals("bob", user.getUsername());
    }

    @Test
    void userEqualsAndHashCode() {
        User a = User.builder().id(1).username("alice").build();
        User b = User.builder().id(1).username("alice").build();
        User c = User.builder().id(2).username("carol").build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void userToString() {
        User user = User.builder().id(1).username("alice").build();
        String str = user.toString();

        assertTrue(str.contains("id=1"));
        assertTrue(str.contains("username=alice"));
    }

    @Test
    void userToBuilder() {
        User original = User.builder().id(1).username("alice").email("alice@example.com").build();
        User copy = original.toBuilder().username("alice2").build();

        assertEquals(1, copy.getId());
        assertEquals("alice2", copy.getUsername());
        assertEquals("alice@example.com", copy.getEmail());
    }

    @Test
    void userJacksonRoundTrip() throws IOException {
        User user = User.builder()
                .id(1)
                .username("alice")
                .email("alice@example.com")
                .createdAt(1700000000)
                .build();

        String json = MAPPER.writeValueAsString(user);
        User deserialized = MAPPER.readValue(json, User.class);

        assertEquals(user, deserialized);
    }

    @Test
    void orderBuilderWithCollectionsAndEnum() {
        Order order = Order.builder()
                .orderId("order-123")
                .userId(1)
                .amount(99.99)
                .items(List.of("item1", "item2"))
                .status(Order.Status.CONFIRMED)
                .placedAt(1700000000)
                .build();

        assertEquals("order-123", order.getOrderId());
        assertEquals(List.of("item1", "item2"), order.getItems());
        assertEquals(Order.Status.CONFIRMED, order.getStatus());
    }

    @Test
    void orderStatusFromValue() {
        assertEquals(Order.Status.PENDING, Order.Status.fromValue("PENDING"));
        assertEquals(Order.Status.SHIPPED, Order.Status.fromValue("SHIPPED"));
        assertThrows(IllegalArgumentException.class, () -> Order.Status.fromValue("INVALID"));
    }

    @Test
    void orderJacksonRoundTrip() throws IOException {
        Order order = Order.builder()
                .orderId("order-123")
                .userId(1)
                .amount(99.99)
                .items(List.of("item1", "item2"))
                .status(Order.Status.DELIVERED)
                .placedAt(1700000000)
                .build();

        String json = MAPPER.writeValueAsString(order);
        Order deserialized = MAPPER.readValue(json, Order.class);

        assertEquals(order.getOrderId(), deserialized.getOrderId());
        assertEquals(order.getStatus(), deserialized.getStatus());
        assertEquals(order.getItems(), deserialized.getItems());
    }

    @Test
    void categoryBuilderAndRecursiveType() {
        Category parent = Category.builder().name("Electronics").build();
        Category child = Category.builder().name("Laptops").build();

        assertEquals("Electronics", parent.getName());
        assertEquals("Laptops", child.getName());
    }

    @Test
    void categoryJacksonRoundTrip() throws IOException {
        Category category = Category.builder().name("Books").build();

        String json = MAPPER.writeValueAsString(category);
        Category deserialized = MAPPER.readValue(json, Category.class);

        assertEquals(category.getName(), deserialized.getName());
    }
}