# avro-to-json-core

Java library that converts [Apache Avro](https://avro.apache.org/) schemas to [JSON Schema](https://json-schema.org/).

## Maven dependency

```xml
<dependency>
    <groupId>org.metalib.schema.avro.json</groupId>
    <artifactId>avro-to-json-core</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Features

- Records, arrays, maps, enums, unions (including multi-type)
- Logical types: uuid, date, time, timestamps, decimal, duration
- Recursive records via `$ref` + definitions
- Default values and documentation pass-through
- Custom Avro property preservation
- `bytes`/`fixed` as base64-encoded strings
- JSON Schema **draft-07** and **draft-2020-12**
- Two conversion modes: **POJO-optimized** (default) and **strict**

## Usage

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

## Conversion modes

| Behavior | POJO-optimized (default) | Strict |
|---|---|---|
| Nullable unions `["null", X]` | Flattened to X's schema | `type: ["null", "string"]` or `oneOf` |
| `additionalProperties` | `false` | omitted |
| Empty `required` array | omitted | preserved |
| `javaType` hints for logical types | yes | no |

## Example

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
