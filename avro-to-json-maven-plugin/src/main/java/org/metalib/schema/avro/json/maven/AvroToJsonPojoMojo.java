package org.metalib.schema.avro.json.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jsonschema2pojo.*;
import org.jsonschema2pojo.rules.RuleFactory;
import org.metalib.schema.avro.json.LombokAnnotator;
import org.metalib.schema.avro.json.AvroToJsonSchemaConverter;
import org.metalib.schema.avro.json.ConverterOptions;
import org.metalib.schema.avro.json.JsonSchemaDraft;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Generates Java POJO classes from Avro schema files (.avsc).
 *
 * <p>This Mojo performs a two-step pipeline:
 * <ol>
 *   <li>Converts Avro schemas (.avsc) to JSON Schema (.json) using avro-to-json-core</li>
 *   <li>Generates Java POJOs from JSON Schema using jsonschema2pojo</li>
 * </ol>
 *
 * <p>The generated POJOs use Jackson annotations for JSON serialization and,
 * when {@code useLombok} is enabled, Lombok annotations for boilerplate reduction.
 */
@Mojo(name = "generate-pojo", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class AvroToJsonPojoMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Directory containing Avro schema files (.avsc).
     */
    @Parameter(property = "avro-to-json.sourceDirectory", defaultValue = "${project.basedir}/src/main/avro")
    private File sourceDirectory;

    /**
     * Intermediate directory for generated JSON Schema files.
     */
    @Parameter(property = "avro-to-json.jsonSchemaDirectory", defaultValue = "${project.build.directory}/generated-resources/json-schema")
    private File jsonSchemaDirectory;

    /**
     * Output directory for generated Java POJO source files.
     */
    @Parameter(property = "avro-to-json.pojoOutputDirectory", defaultValue = "${project.build.directory}/generated-sources/avro-pojo")
    private File pojoOutputDirectory;

    /**
     * Target Java package for generated classes.
     */
    @Parameter(property = "avro-to-json.targetPackage", defaultValue = "")
    private String targetPackage;

    /**
     * Use strict JSON Schema mode (no POJO optimizations).
     */
    @Parameter(property = "avro-to-json.strict", defaultValue = "false")
    private boolean strict;

    /**
     * JSON Schema draft version: {@code draft-07} or {@code draft-2020-12}.
     */
    @Parameter(property = "avro-to-json.draft", defaultValue = "draft-07")
    private String draft;

    /**
     * Whether to add Lombok annotations ({@code @Data}, {@code @Builder},
     * {@code @NoArgsConstructor}, {@code @AllArgsConstructor}) to generated classes.
     */
    @Parameter(property = "avro-to-json.useLombok", defaultValue = "true")
    private boolean useLombok;

    /**
     * Annotation style for generated POJOs: {@code jackson}, {@code jackson2}, or {@code jackson3}.
     */
    @Parameter(property = "avro-to-json.annotationStyle", defaultValue = "jackson2")
    private String annotationStyle;

    /**
     * Source type for jsonschema2pojo: {@code jsonSchema}, {@code yamlSchema}, {@code json}, or {@code yaml}.
     */
    @Parameter(property = "avro-to-json.sourceType", defaultValue = "jsonSchema")
    private String sourceType;

    @Override
    public void execute() throws MojoExecutionException {
        if (!sourceDirectory.isDirectory()) {
            getLog().info("Source directory does not exist, skipping: " + sourceDirectory);
            return;
        }

        // Step 1: Convert Avro schemas to JSON Schema
        int count = convertAvroToJsonSchema();
        if (count == 0) {
            getLog().info("No .avsc files found in " + sourceDirectory);
            return;
        }
        getLog().info("Converted " + count + " Avro schema(s) to JSON Schema");

        // Step 2: Generate POJOs from JSON Schema
        generatePojosFromJsonSchema();

        // Step 3: Add generated sources to the Maven compile path
        project.addCompileSourceRoot(pojoOutputDirectory.getAbsolutePath());
        getLog().info("Added " + pojoOutputDirectory + " to compile source roots");
    }

    private int convertAvroToJsonSchema() throws MojoExecutionException {
        JsonSchemaDraft schemaDraft = switch (draft) {
            case "draft-2020-12" -> JsonSchemaDraft.DRAFT_2020_12;
            default -> JsonSchemaDraft.DRAFT_07;
        };
        ConverterOptions options = (strict ? ConverterOptions.strict() : ConverterOptions.pojoOptimized())
                .withDraft(schemaDraft);
        AvroToJsonSchemaConverter converter = new AvroToJsonSchemaConverter(options);

        Path sourcePath = sourceDirectory.toPath();
        int count = 0;

        try (Stream<Path> paths = Files.walk(sourcePath)) {
            for (Path avscFile : paths.filter(p -> p.toString().endsWith(".avsc")).toList()) {
                Path relativePath = sourcePath.relativize(avscFile);
                String jsonFileName = relativePath.toString().replaceAll("\\.avsc$", ".json");
                Path outputPath = jsonSchemaDirectory.toPath().resolve(jsonFileName);

                Files.createDirectories(outputPath.getParent());

                String avroSchema = Files.readString(avscFile);
                String jsonSchema = converter.convert(avroSchema);
                Files.writeString(outputPath, jsonSchema);

                getLog().debug("Converted " + relativePath + " -> " + jsonFileName);
                count++;
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to process Avro schema files", e);
        }

        return count;
    }

    private void generatePojosFromJsonSchema() throws MojoExecutionException {
        GenerationConfig config = new DefaultGenerationConfig() {
            @Override
            public Iterator<URL> getSource() {
                try {
                    return Collections.singletonList(jsonSchemaDirectory.toURI().toURL()).iterator();
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to convert directory to URL", e);
                }
            }

            @Override
            public File getTargetDirectory() {
                return pojoOutputDirectory;
            }

            @Override
            public String getTargetPackage() {
                return targetPackage != null ? targetPackage : "";
            }

            @Override
            public AnnotationStyle getAnnotationStyle() {
                return AnnotationStyle.valueOf(annotationStyle.toUpperCase());
            }

            @Override
            public InclusionLevel getInclusionLevel() {
                return InclusionLevel.NON_NULL;
            }

            @Override
            public SourceType getSourceType() {
                return parseSourceType(sourceType);
            }

            // Lombok handles these — disable jsonschema2pojo generation
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
                // Use only "#/" — do NOT include "." because avro-to-json-core
                // generates definition keys with dotted namespaces (e.g. "com.example.User")
                // and the default "#/." would split on "." causing path resolution failures.
                return "#/";
            }
        };

        try {
            RuleLogger logger = new RuleLogger() {
                @Override
                public void debug(String msg) {
                    getLog().debug(msg);
                }

                @Override
                public void error(String msg) {
                    getLog().error(msg);
                }

                @Override
                public void error(String msg, Throwable e) {
                    getLog().error(msg, e);
                }

                @Override
                public void info(String msg) {
                    getLog().info(msg);
                }

                @Override
                public void warn(String msg) {
                    getLog().warn(msg);
                }

                @Override
                public void warn(String msg, Throwable e) {
                    getLog().warn(msg, e);
                }

                @Override
                public boolean isDebugEnabled() {
                    return getLog().isDebugEnabled();
                }

                @Override
                public boolean isErrorEnabled() {
                    return getLog().isErrorEnabled();
                }

                @Override
                public boolean isInfoEnabled() {
                    return getLog().isInfoEnabled();
                }

                @Override
                public boolean isTraceEnabled() {
                    return getLog().isDebugEnabled();
                }

                @Override
                public boolean isWarnEnabled() {
                    return getLog().isWarnEnabled();
                }

                @Override
                public void trace(String msg) {
                    getLog().debug(msg);
                }
            };

            Jsonschema2Pojo.generate(config, logger);
            getLog().info("Generated POJO classes in " + pojoOutputDirectory);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate POJO classes from JSON Schema", e);
        }
    }

    private static SourceType parseSourceType(String value) {
        return switch (value) {
            case "jsonSchema" -> SourceType.JSONSCHEMA;
            case "yamlSchema" -> SourceType.YAMLSCHEMA;
            case "json" -> SourceType.JSON;
            case "yaml" -> SourceType.YAML;
            default -> SourceType.valueOf(value.toUpperCase());
        };
    }
}
