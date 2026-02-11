# avro-to-json

Converts [Apache Avro](https://avro.apache.org/) schemas to [JSON Schema](https://json-schema.org/).

## Features

- Records, arrays, maps, enums, unions (including multi-type)
- Logical types: uuid, date, time, timestamps, decimal, duration
- Recursive records via `$ref` + definitions
- Default values and documentation pass-through
- Custom Avro property preservation
- `bytes`/`fixed` as base64-encoded strings
- JSON Schema **draft-07** and **draft-2020-12**
- Two conversion modes: **POJO-optimized** (default) and **strict**

### Conversion modes

| Behavior | POJO-optimized (default) | Strict |
|---|---|---|
| Nullable unions `["null", X]` | Flattened to X's schema | `type: ["null", "string"]` or `oneOf` |
| `additionalProperties` | `false` | omitted |
| Empty `required` array | omitted | preserved |
| `javaType` hints for logical types | yes | no |

## Modules

| Module | Description |
|---|---|
| `avro-to-json-core` | Conversion library |
| `avro-to-json-cli` | Command-line interface (fat JAR via maven-shade) |

## Requirements

- Java 17+
- Maven 3.6+

## Build

```shell
mvn clean package
```

## Usage

### Library

```java
import org.metalib.schema.avro.json.AvroToJsonSchemaConverter;
import org.metalib.schema.avro.json.ConverterOptions;
import org.metalib.schema.avro.json.JsonSchemaDraft;

// Default (POJO-optimized, draft-07)
var converter = new AvroToJsonSchemaConverter();
String jsonSchema = converter.convert(avroSchemaJson);

// Strict mode
var strict = new AvroToJsonSchemaConverter(ConverterOptions.strict());

// Draft-2020-12
var draft2020 = new AvroToJsonSchemaConverter(
    ConverterOptions.pojoOptimized().withDraft(JsonSchemaDraft.DRAFT_2020_12));
```

### CLI

```shell
# From a file
java -jar avro-to-json-cli/target/avro-to-json-cli-0.0.1-SNAPSHOT.jar schema.avsc

# Write to file
java -jar avro-to-json-cli/target/avro-to-json-cli-0.0.1-SNAPSHOT.jar schema.avsc -o output.json

# Strict mode with draft-2020-12
java -jar avro-to-json-cli/target/avro-to-json-cli-0.0.1-SNAPSHOT.jar schema.avsc --strict --draft draft-2020-12

# From a Confluent Schema Registry
java -jar avro-to-json-cli/target/avro-to-json-cli-0.0.1-SNAPSHOT.jar \
    --registry http://localhost:8081 --subject my-topic-value
```

### Example

Given this Avro schema:

```json
{
  "type": "record",
  "name": "User",
  "fields": [
    {"name": "id", "type": {"type": "string", "logicalType": "uuid"}},
    {"name": "name", "type": "string"},
    {"name": "email", "type": ["null", "string"], "default": null}
  ]
}
```

The default (POJO-optimized) output:

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "User",
  "properties": {
    "id": {
      "type": "string",
      "format": "uuid",
      "javaType": "java.util.UUID"
    },
    "name": {
      "type": "string"
    },
    "email": {
      "type": "string",
      "default": null
    }
  },
  "required": ["id", "name"],
  "additionalProperties": false
}
```

## License

[Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)
