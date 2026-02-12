# avro-to-json-cli

Command-line tool that converts [Apache Avro](https://avro.apache.org/) schemas to [JSON Schema](https://json-schema.org/) or generates Java POJO source files. Packaged as a fat JAR via maven-shade-plugin.

## Build

```shell
mvn clean package
```

The fat JAR is produced at `target/avro-to-json-cli-0.0.3-SNAPSHOT.jar`.

## Usage

### Convert a local `.avsc` file

```shell
java -jar target/avro-to-json-cli-0.0.3-SNAPSHOT.jar schema.avsc
```

### Write output to a file

```shell
java -jar target/avro-to-json-cli-0.0.3-SNAPSHOT.jar schema.avsc -o output.json
```

### Fetch from a Confluent Schema Registry

```shell
java -jar target/avro-to-json-cli-0.0.3-SNAPSHOT.jar \
    --registry http://localhost:8081 --subject my-topic-value
```

Optionally specify a version (defaults to `latest`):

```shell
java -jar target/avro-to-json-cli-0.0.3-SNAPSHOT.jar \
    --registry http://localhost:8081 --subject my-topic-value --version 3
```

### Generate Java POJOs

Generate Lombok + Jackson annotated Java classes from an Avro schema:

```shell
java -jar target/avro-to-json-cli-0.0.3-SNAPSHOT.jar schema.avsc \
    --generate-pojo -p com.example --pojo-output /tmp/pojo
```

Generate POJOs without Lombok (Jackson only, with getters/setters/toString/hashCode):

```shell
java -jar target/avro-to-json-cli-0.0.3-SNAPSHOT.jar schema.avsc \
    --generate-pojo --no-lombok -p com.example --pojo-output /tmp/pojo
```

Generate POJOs from a Schema Registry:

```shell
java -jar target/avro-to-json-cli-0.0.3-SNAPSHOT.jar \
    --registry http://localhost:8081 --subject my-topic-value \
    --generate-pojo -p com.example --pojo-output /tmp/pojo
```

### Options

| Flag | Description | Default |
|---|---|---|
| `-o`, `--output` | Write JSON Schema to a file instead of stdout | stdout |
| `--strict` | Strict mode — preserves nullable unions, omits `additionalProperties` and `javaType` hints | off (POJO-optimized) |
| `--draft` | JSON Schema draft version: `draft-07` or `draft-2020-12` | `draft-07` |
| `--generate-pojo` | Generate Java POJO source files instead of JSON Schema | off |
| `-p`, `--package` | Target Java package for generated POJOs | `""` |
| `--pojo-output` | Output directory for generated `.java` files | current dir |
| `--no-lombok` | Disable Lombok annotations (only use Jackson) | off (Lombok enabled) |
| `--registry` | Confluent Schema Registry URL | — |
| `--subject` | Schema subject name (used with `--registry`) | — |
| `--version` | Schema version (used with `--registry`) | `latest` |
| `-h`, `--help` | Show help | — |
| `-V`, `--version` | Show version | — |
