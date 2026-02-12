# avro-to-json-maven-plugin-sample

Sample project demonstrating `avro-to-json-maven-plugin` usage with both JSON Schema generation and POJO generation.

## What This Sample Does

1. **`generate` goal** -- converts three Avro schemas in `src/main/avro/` to JSON Schema files in `target/generated-resources/json-schema/`
2. **`generate-pojo` goal** -- generates Java POJOs with Lombok + Jackson annotations in `target/generated-sources/avro-pojo/`

## Avro Schemas

| Schema | Demonstrates |
|--------|-------------|
| `User.avsc` | Primitive types, nullable fields, logical types (timestamp-millis) |
| `Order.avsc` | Arrays, maps, enums, UUID, decimal, timestamp-millis |
| `Category.avsc` | Recursive/self-referential types |

## Build

From the repository root:

```shell
mvn clean package
```

Or build just this module (requires the plugin to be installed first):

```shell
mvn install -pl avro-to-json-core,avro-to-json-maven-plugin
mvn package -pl samples/avro-to-json-maven-plugin-sample
```

## Generated Output

After building, inspect the generated files:

```
target/
  generated-resources/json-schema/   # JSON Schema files
    User.json
    Order.json
    Category.json
  generated-sources/avro-pojo/       # Java POJOs
    com/example/
      User.java
      Order.java
      Category.java
```

## Plugin Configuration

```xml
<plugin>
    <groupId>org.metalib.schema.avro.json</groupId>
    <artifactId>avro-to-json-maven-plugin</artifactId>
    <version>${project.version}</version>
    <executions>
        <execution>
            <id>generate-json-schema</id>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
        <execution>
            <id>generate-pojo</id>
            <goals>
                <goal>generate-pojo</goal>
            </goals>
            <configuration>
                <targetPackage>com.example</targetPackage>
                <useLombok>true</useLombok>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Tests

The test suite verifies:

- JSON Schema files are generated and contain expected properties
- Recursive schemas produce `definitions`/`$defs` sections
- POJO `.java` files are generated for all three schemas
- Generated POJOs contain Lombok annotations (`@Data`, `@Builder(toBuilder = true)`, `@NoArgsConstructor`, `@AllArgsConstructor`)
- Generated POJOs contain Jackson annotations (`@JsonProperty`, `@JsonInclude`)