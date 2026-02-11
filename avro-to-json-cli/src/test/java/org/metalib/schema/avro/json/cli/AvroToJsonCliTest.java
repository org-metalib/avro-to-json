package org.metalib.schema.avro.json.cli;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AvroToJsonCliTest {

    @TempDir
    Path tempDir;

    @Test
    public void testCliConversion() throws IOException {
        Path input = tempDir.resolve("user.avsc");
        Files.writeString(input, """
                {
                  "type": "record",
                  "name": "User",
                  "fields": [
                    {"name": "id", "type": "int"}
                  ]
                }
                """);

        Path output = tempDir.resolve("user.json");

        int exitCode = new CommandLine(new AvroToJsonCli()).execute(
                input.toString(),
                "--output", output.toString()
        );

        assertEquals(0, exitCode);
        assertTrue(Files.exists(output));
        String content = Files.readString(output);
        assertTrue(content.contains("\"type\" : \"integer\""));
        assertTrue(content.contains("\"title\" : \"User\""));
    }

    @Test
    public void testCliDraft202012() throws Exception {
        Path input = tempDir.resolve("recursive.avsc");
        Files.writeString(input, """
                {
                  "type": "record",
                  "name": "Node",
                  "fields": [
                    {"name": "value", "type": "string"},
                    {"name": "next", "type": ["null", "Node"], "default": null}
                  ]
                }
                """);

        Path output = tempDir.resolve("recursive.json");

        int exitCode = new CommandLine(new AvroToJsonCli()).execute(
                input.toString(),
                "--draft", "draft-2020-12",
                "--output", output.toString()
        );

        assertEquals(0, exitCode);
        assertTrue(Files.exists(output));
        String content = Files.readString(output);
        JsonNode node = new ObjectMapper().readTree(content);
        assertEquals("https://json-schema.org/draft/2020-12/schema", node.get("$schema").asText());
        assertTrue(node.has("$defs"));
        assertFalse(node.has("definitions"));
    }

    @Test
    public void testCliStrictMode() throws Exception {
        Path input = tempDir.resolve("nullable.avsc");
        Files.writeString(input, """
                {
                  "type": "record",
                  "name": "NullableTest",
                  "fields": [
                    {"name": "email", "type": ["null", "string"], "default": null}
                  ]
                }
                """);

        Path output = tempDir.resolve("nullable.json");

        int exitCode = new CommandLine(new AvroToJsonCli()).execute(
                input.toString(),
                "--strict",
                "--output", output.toString()
        );

        assertEquals(0, exitCode);
        assertTrue(Files.exists(output));
        String content = Files.readString(output);
        JsonNode node = new ObjectMapper().readTree(content);
        JsonNode emailType = node.get("properties").get("email").get("type");
        assertTrue(emailType.isArray());
        assertEquals("null", emailType.get(0).asText());
        assertEquals("string", emailType.get(1).asText());
    }

    @Test
    public void testCliRegistryInput() throws Exception {
        String avroSchema = """
                {"type":"record","name":"User","fields":[{"name":"id","type":"int"}]}""";

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/subjects/User/versions/latest/schema", exchange -> {
            byte[] response = avroSchema.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        });
        server.start();

        try {
            Path output = tempDir.resolve("registry-user.json");

            int exitCode = new CommandLine(new AvroToJsonCli()).execute(
                    "--registry", "http://localhost:" + port,
                    "--subject", "User",
                    "--output", output.toString()
            );

            assertEquals(0, exitCode);
            assertTrue(Files.exists(output));
            String content = Files.readString(output);
            JsonNode node = new ObjectMapper().readTree(content);
            assertEquals("User", node.get("title").asText());
            assertEquals("object", node.get("type").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testCliMutualExclusivity() throws Exception {
        Path input = tempDir.resolve("dummy.avsc");
        Files.writeString(input, "{}");

        int exitCode = new CommandLine(new AvroToJsonCli()).execute(
                input.toString(),
                "--registry", "http://localhost:8081",
                "--subject", "User"
        );

        assertNotEquals(0, exitCode);
    }
}
