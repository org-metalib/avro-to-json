# avro-to-json

Converts [Apache Avro](https://avro.apache.org/) schemas to [JSON Schema](https://json-schema.org/) and generates Java POJOs with [Lombok](https://projectlombok.org/) + [Jackson](https://github.com/FasterXML/jackson) annotations.

## Modules

| Module | Description |
|---|---|
| [avro-to-json-core](avro-to-json-core/) | Conversion library + shared `LombokAnnotator` for POJO generation |
| [avro-to-json-cli](avro-to-json-cli/) | Command-line interface (fat JAR) — JSON Schema output or POJO generation |
| [avro-to-json-maven-plugin](avro-to-json-maven-plugin/) | Maven plugin — `generate` (JSON Schema) and `generate-pojo` (Lombok/Jackson POJOs) goals |
| [avro-to-json-maven-plugin-sample](samples/avro-to-json-maven-plugin-sample/) | Sample project demonstrating plugin usage |

## Requirements

- Java 17+
- Maven 3.6+

## Build

```shell
mvn clean package
```

## License

[Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)
