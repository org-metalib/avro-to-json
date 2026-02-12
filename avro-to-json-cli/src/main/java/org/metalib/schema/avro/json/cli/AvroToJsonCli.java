package org.metalib.schema.avro.json.cli;

import org.jsonschema2pojo.*;
import org.metalib.schema.avro.json.LombokAnnotator;
import org.metalib.schema.avro.json.AvroToJsonSchemaConverter;
import org.metalib.schema.avro.json.ConverterOptions;
import org.metalib.schema.avro.json.JsonSchemaDraft;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@Command(name = "avro-to-json", mixinStandardHelpOptions = true, version = "0.0.1",
        description = "Converts an Avro schema to a JSON Schema or Java POJOs.")
public class AvroToJsonCli implements Callable<Integer> {

    static class FileInput {
        @Parameters(index = "0", description = "The Avro schema file (.avsc) to convert.")
        File inputFile;
    }

    static class RegistryInput {
        @Option(names = {"--registry"}, required = true, description = "Schema Registry URL.")
        String registryUrl;

        @Option(names = {"--subject"}, required = true, description = "Schema subject name.")
        String subject;

        @Option(names = {"--version"}, defaultValue = "latest", description = "Schema version (default: latest).")
        String version;
    }

    @ArgGroup(exclusive = true, multiplicity = "1")
    InputSource inputSource;

    static class InputSource {
        @ArgGroup(exclusive = false)
        FileInput fileInput;

        @ArgGroup(exclusive = false)
        RegistryInput registryInput;
    }

    @Option(names = {"-o", "--output"}, description = "The output JSON Schema file. If not specified, prints to stdout.")
    private File outputFile;

    @Option(names = {"--strict"}, description = "Use strict JSON Schema mode (no POJO optimizations).")
    private boolean strict;

    @Option(names = {"--draft"}, defaultValue = "draft-07",
            description = "JSON Schema draft version: draft-07 or draft-2020-12 (default: draft-07).")
    private String draft;

    @Option(names = {"--generate-pojo"}, description = "Generate Java POJO source files instead of JSON Schema.")
    private boolean generatePojo;

    @Option(names = {"-p", "--package"}, defaultValue = "", description = "Target Java package for generated POJOs (default: \"\").")
    private String targetPackage;

    @Option(names = {"--pojo-output"}, defaultValue = ".", description = "Output directory for generated .java files (default: current dir).")
    private File pojoOutputDir;

    @Option(names = {"--no-lombok"}, description = "Disable Lombok annotations (only use Jackson).")
    private boolean noLombok;

    @Override
    public Integer call() throws Exception {
        String avroSchema;

        if (inputSource.fileInput != null) {
            File inputFile = inputSource.fileInput.inputFile;
            if (!inputFile.exists()) {
                System.err.println("Error: Input file does not exist: " + inputFile.getAbsolutePath());
                return 1;
            }
            try {
                avroSchema = Files.readString(inputFile.toPath());
            } catch (IOException e) {
                System.err.println("Error: Failed to read input file: " + e.getMessage());
                return 1;
            }
        } else {
            RegistryInput reg = inputSource.registryInput;
            try {
                SchemaRegistryClient client = new SchemaRegistryClient(reg.registryUrl);
                avroSchema = client.fetchSchema(reg.subject, reg.version);
            } catch (IOException e) {
                System.err.println("Error: Failed to fetch schema from registry: " + e.getMessage());
                return 1;
            }
        }

        JsonSchemaDraft schemaDraft = switch (draft) {
            case "draft-2020-12" -> JsonSchemaDraft.DRAFT_2020_12;
            default -> JsonSchemaDraft.DRAFT_07;
        };
        ConverterOptions options = (strict ? ConverterOptions.strict() : ConverterOptions.pojoOptimized())
                .withDraft(schemaDraft);
        AvroToJsonSchemaConverter converter = new AvroToJsonSchemaConverter(options);
        String jsonSchema;
        try {
            jsonSchema = converter.convert(avroSchema);
        } catch (Exception e) {
            System.err.println("Error: Conversion failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }

        if (generatePojo) {
            return generatePojoFiles(jsonSchema);
        }

        if (outputFile != null) {
            try {
                Files.writeString(outputFile.toPath(), jsonSchema);
                String sourceName = inputSource.fileInput != null
                        ? inputSource.fileInput.inputFile.getName()
                        : inputSource.registryInput.subject;
                System.out.println("Successfully converted " + sourceName + " to " + outputFile.getName());
            } catch (IOException e) {
                System.err.println("Error: Failed to write output file: " + e.getMessage());
                return 1;
            }
        } else {
            System.out.println(jsonSchema);
        }

        return 0;
    }

    private Integer generatePojoFiles(String jsonSchema) {
        Path tempDir = null;
        try {
            // Write JSON Schema to a temp directory for jsonschema2pojo
            tempDir = Files.createTempDirectory("avro-to-json-cli");
            Path tempJsonSchema = tempDir.resolve("schema.json");
            Files.writeString(tempJsonSchema, jsonSchema);

            Files.createDirectories(pojoOutputDir.toPath());

            boolean useLombok = !noLombok;
            Path jsonSchemaDir = tempDir;

            GenerationConfig config = new DefaultGenerationConfig() {
                @Override
                public Iterator<URL> getSource() {
                    try {
                        return Collections.singletonList(jsonSchemaDir.toUri().toURL()).iterator();
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to convert directory to URL", e);
                    }
                }

                @Override
                public File getTargetDirectory() {
                    return pojoOutputDir;
                }

                @Override
                public String getTargetPackage() {
                    return targetPackage != null ? targetPackage : "";
                }

                @Override
                public AnnotationStyle getAnnotationStyle() {
                    return AnnotationStyle.JACKSON;
                }

                @Override
                public InclusionLevel getInclusionLevel() {
                    return InclusionLevel.NON_NULL;
                }

                @Override
                public SourceType getSourceType() {
                    return SourceType.JSONSCHEMA;
                }

                @Override
                public boolean isIncludeHashcodeAndEquals() {
                    return !useLombok;
                }

                @Override
                public boolean isIncludeToString() {
                    return !useLombok;
                }

                @Override
                public boolean isIncludeGetters() {
                    return !useLombok;
                }

                @Override
                public boolean isIncludeSetters() {
                    return !useLombok;
                }

                @Override
                public boolean isGenerateBuilders() {
                    return false;
                }

                @Override
                public boolean isIncludeConstructors() {
                    return false;
                }

                @Override
                public boolean isIncludeAdditionalProperties() {
                    return false;
                }

                @Override
                public boolean isIncludeGeneratedAnnotation() {
                    return false;
                }

                @Override
                public Class<? extends Annotator> getCustomAnnotator() {
                    return useLombok ? LombokAnnotator.class : NoopAnnotator.class;
                }

                @Override
                public boolean isRemoveOldOutput() {
                    return true;
                }

                @Override
                public String getRefFragmentPathDelimiters() {
                    return "#/";
                }
            };

            Jsonschema2Pojo.generate(config, new NoopRuleLogger());

            // Print summary of generated files
            try (Stream<Path> files = Files.walk(pojoOutputDir.toPath())) {
                long count = files.filter(p -> p.toString().endsWith(".java")).count();
                System.out.println("Generated " + count + " Java source file(s) in " + pojoOutputDir.getAbsolutePath());
            }

            return 0;
        } catch (IOException e) {
            System.err.println("Error: POJO generation failed: " + e.getMessage());
            return 1;
        } finally {
            // Clean up temp directory
            if (tempDir != null) {
                try {
                    try (Stream<Path> files = Files.walk(tempDir)) {
                        files.sorted(java.util.Comparator.reverseOrder())
                                .forEach(p -> {
                                    try {
                                        Files.deleteIfExists(p);
                                    } catch (IOException ignored) {
                                    }
                                });
                    }
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static class NoopRuleLogger implements RuleLogger {
        @Override public void debug(String msg) {}
        @Override public void error(String msg) {}
        @Override public void error(String msg, Throwable e) {}
        @Override public void info(String msg) {}
        @Override public void warn(String msg) {}
        @Override public void warn(String msg, Throwable e) {}
        @Override public boolean isDebugEnabled() { return false; }
        @Override public boolean isErrorEnabled() { return false; }
        @Override public boolean isInfoEnabled() { return false; }
        @Override public boolean isTraceEnabled() { return false; }
        @Override public boolean isWarnEnabled() { return false; }
        @Override public void trace(String msg) {}
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new AvroToJsonCli()).execute(args);
        System.exit(exitCode);
    }
}
