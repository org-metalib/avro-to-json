package org.metalib.schema.avro.json.cli;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class SchemaRegistryClientTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        baseUrl = "http://localhost:" + port;
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    public void testFetchSchemaSuccess() throws Exception {
        String avroSchema = """
                {"type":"record","name":"User","fields":[{"name":"id","type":"int"}]}""";

        server.createContext("/subjects/User/versions/latest/schema", exchange -> {
            byte[] response = avroSchema.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();

        SchemaRegistryClient client = new SchemaRegistryClient(baseUrl);
        String result = client.fetchSchema("User", "latest");

        assertEquals(avroSchema, result);
    }

    @Test
    public void testFetchSchemaNotFound() throws Exception {
        server.createContext("/subjects/Missing/versions/latest/schema", exchange -> {
            byte[] response = "{\"error_code\":40401,\"message\":\"Subject not found\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();

        SchemaRegistryClient client = new SchemaRegistryClient(baseUrl);

        IOException ex = assertThrows(IOException.class, () -> client.fetchSchema("Missing", "latest"));
        assertTrue(ex.getMessage().contains("404"));
    }

    @Test
    public void testFetchSchemaUrlEncodesSubject() throws Exception {
        String avroSchema = """
                {"type":"record","name":"User","fields":[{"name":"id","type":"int"}]}""";

        server.createContext("/subjects/com.example.User-value/versions/latest/schema", exchange -> {
            byte[] response = avroSchema.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();

        SchemaRegistryClient client = new SchemaRegistryClient(baseUrl);
        String result = client.fetchSchema("com.example.User-value", "latest");

        assertEquals(avroSchema, result);
    }

    @Test
    public void testFetchSchemaSpecificVersion() throws Exception {
        String avroSchema = """
                {"type":"record","name":"Event","fields":[{"name":"ts","type":"long"}]}""";

        server.createContext("/subjects/Event/versions/3/schema", exchange -> {
            byte[] response = avroSchema.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();

        SchemaRegistryClient client = new SchemaRegistryClient(baseUrl);
        String result = client.fetchSchema("Event", "3");

        assertEquals(avroSchema, result);
    }
}
