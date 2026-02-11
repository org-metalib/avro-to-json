# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

This is a Maven multi-module project (Java 17). All commands run from the repo root:

```shell
# Build everything (compile + test + package)
mvn clean package

# Run all tests
mvn test

# Run a single test class
mvn -pl avro-to-json-core test -Dtest=AvroToJsonSchemaConverterTest
mvn -pl avro-to-json-cli test -Dtest=AvroToJsonCliTest

# Compile without tests
mvn compile -DskipTests
```

## Architecture

**Parent POM:** `pom.xml` — defines shared dependency versions (Avro 1.12.1, Jackson 2.19.4, JUnit 5.13.4, Picocli 4.7.7).

### avro-to-json-core

The conversion library (`org.metalib.schema.avro.json`). Converts Apache Avro schemas to JSON Schema.

- `AvroToJsonSchemaConverter` — main public class. `convert(String avroSchemaJson)` parses Avro and returns pretty-printed JSON Schema.
- `ConverterOptions` — Java record controlling conversion behavior. Two presets:
  - `pojoOptimized()` (default) — flattens `["null", X]` unions to just X's schema, sets `additionalProperties: false`, omits empty `required` arrays, adds `javaType` hints for logical types.
  - `strict()` — preserves nullable unions as `type: ["null", "string"]` arrays (simple types) or `oneOf` (complex types), no `additionalProperties`, keeps empty `required`, no `javaType` hints.
- `JsonSchemaDraft` — enum for draft-07 (uses `definitions`) and draft-2020-12 (uses `$defs`). Selected via `ConverterOptions.withDraft()`.
- Handles: records, arrays, maps, enums, unions, logical types (uuid, date, time, timestamps, decimal, duration), recursive records (via `$ref` + definitions), default values, custom Avro property pass-through, bytes/fixed as base64.
- Internal state during conversion tracked via private `ConversionContext` record (definitions map + seen records set).

### avro-to-json-cli

CLI wrapper using Picocli (`org.metalib.schema.avro.json.cli`). Packaged as a fat JAR via maven-shade-plugin.

- `AvroToJsonCli` — two mutually exclusive input sources (ArgGroup):
  - File: positional `.avsc` file path.
  - Schema Registry: `--registry <url> --subject <name> [--version <v>]`.
- Flags: `-o/--output` (file output, otherwise stdout), `--strict` (strict mode), `--draft` (draft-07 or draft-2020-12).
- `SchemaRegistryClient` — fetches Avro schemas from Confluent Schema Registry via HTTP (`/subjects/{subject}/versions/{version}/schema`).

### avro-to-json-maven-plugin

Maven plugin (`org.metalib.schema.avro.json.maven`). Packaging: `maven-plugin`.

- `AvroToJsonMojo` — goal `generate`, default phase `GENERATE_SOURCES`. Scans `sourceDirectory` for `.avsc` files, converts each to JSON Schema in `outputDirectory`.
- Parameters (all configurable via `<configuration>` or `-Davro-to-json.*`):
  - `sourceDirectory` (default: `src/main/avro`) — directory containing `.avsc` files.
  - `outputDirectory` (default: `target/generated-resources/json-schema`) — output for `.json` files.
  - `strict` (default: `false`) — use strict mode.
  - `draft` (default: `draft-07`) — JSON Schema draft version.
