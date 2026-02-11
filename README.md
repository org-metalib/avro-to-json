# avro-to-json

Converts [Apache Avro](https://avro.apache.org/) schemas to [JSON Schema](https://json-schema.org/).

## Modules

| Module | Description |
|---|---|
| [avro-to-json-core](avro-to-json-core/) | Conversion library |
| [avro-to-json-cli](avro-to-json-cli/) | Command-line interface (fat JAR via maven-shade) |

## Requirements

- Java 17+
- Maven 3.6+

## Build

```shell
mvn clean package
```

## License

[Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)
