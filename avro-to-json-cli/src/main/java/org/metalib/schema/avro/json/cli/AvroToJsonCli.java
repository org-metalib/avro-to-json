package org.metalib.schema.avro.json.cli;

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
import java.nio.file.Files;
import java.util.concurrent.Callable;

@Command(name = "avro-to-json", mixinStandardHelpOptions = true, version = "0.0.1",
        description = "Converts an Avro schema to a JSON Schema.")
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

    public static void main(String[] args) {
        int exitCode = new CommandLine(new AvroToJsonCli()).execute(args);
        System.exit(exitCode);
    }
}
