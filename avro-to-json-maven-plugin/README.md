# avro-to-json-maven-plugin

Maven plugin that converts Apache Avro schemas (`.avsc`) to JSON Schema and optionally generates Java POJO classes with Lombok and Jackson annotations.

## Goals

### `generate`

Converts `.avsc` files to `.json` JSON Schema files.

```xml
<plugin>
    <groupId>org.metalib.schema.avro.json</groupId>
    <artifactId>avro-to-json-maven-plugin</artifactId>
    <version>0.0.3-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Parameters:**

| Parameter | Property | Default | Description |
|-----------|----------|---------|-------------|
| `sourceDirectory` | `avro-to-json.sourceDirectory` | `src/main/avro` | Directory containing `.avsc` files |
| `outputDirectory` | `avro-to-json.outputDirectory` | `target/generated-resources/json-schema` | Output for `.json` files |
| `strict` | `avro-to-json.strict` | `false` | Use strict JSON Schema mode (no POJO optimizations) |
| `draft` | `avro-to-json.draft` | `draft-07` | JSON Schema draft version (`draft-07` or `draft-2020-12`) |

### `generate-pojo`

Converts `.avsc` files to Java POJO source files with Jackson annotations and optional Lombok support. Runs the Avro-to-JSON-Schema conversion internally, then uses [jsonschema2pojo](https://github.com/joelittlejohn/jsonschema2pojo) to produce Java classes.

```xml
<plugin>
    <groupId>org.metalib.schema.avro.json</groupId>
    <artifactId>avro-to-json-maven-plugin</artifactId>
    <version>0.0.3-SNAPSHOT</version>
    <executions>
        <execution>
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

**Parameters:**

| Parameter | Property | Default | Description |
|-----------|----------|---------|-------------|
| `sourceDirectory` | `avro-to-json.sourceDirectory` | `src/main/avro` | Directory containing `.avsc` files |
| `jsonSchemaDirectory` | `avro-to-json.jsonSchemaDirectory` | `target/generated-resources/json-schema` | Intermediate JSON Schema output |
| `pojoOutputDirectory` | `avro-to-json.pojoOutputDirectory` | `target/generated-sources/avro-pojo` | Output for generated `.java` files |
| `targetPackage` | `avro-to-json.targetPackage` | `""` | Java package for generated classes |
| `strict` | `avro-to-json.strict` | `false` | Use strict JSON Schema mode |
| `draft` | `avro-to-json.draft` | `draft-07` | JSON Schema draft version |
| `useLombok` | `avro-to-json.useLombok` | `true` | Add Lombok annotations to generated classes |

When `useLombok` is `true`, generated classes include:
- `@Data`, `@Builder(toBuilder = true)`, `@NoArgsConstructor`, `@AllArgsConstructor` (Lombok)
- `@JsonProperty`, `@JsonPropertyOrder`, `@JsonInclude(NON_NULL)` (Jackson)

The generated source directory is automatically added to Maven's compile source roots.

## Required Dependencies (consuming project)

For `generate-pojo` with Lombok enabled, the consuming project needs:

```xml
<dependencies>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-annotations</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

## Both Goals Together

You can run both goals in the same plugin execution to get JSON Schema files **and** Java POJOs:

```xml
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
        </configuration>
    </execution>
</executions>
```

## Example

Given `src/main/avro/User.avsc`:

```json
{
  "type": "record",
  "name": "User",
  "namespace": "com.example",
  "fields": [
    {"name": "id", "type": "int"},
    {"name": "username", "type": "string"},
    {"name": "email", "type": ["null", "string"], "default": null}
  ]
}
```

The `generate-pojo` goal produces:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "username", "email"})
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @JsonProperty("id")
    public Integer id;
    @JsonProperty("username")
    public String username;
    @JsonProperty("email")
    public String email;
}
```

See the [sample project](../samples/avro-to-json-maven-plugin-sample/) for a complete working example.