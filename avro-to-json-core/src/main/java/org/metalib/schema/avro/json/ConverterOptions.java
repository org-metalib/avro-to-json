package org.metalib.schema.avro.json;

public record ConverterOptions(
        boolean flattenNullableUnions,
        boolean additionalPropertiesFalse,
        boolean omitEmptyRequired,
        boolean javaTypeHints,
        JsonSchemaDraft draft
) {
    public static ConverterOptions pojoOptimized() {
        return new ConverterOptions(true, true, true, true, JsonSchemaDraft.DRAFT_07);
    }

    public static ConverterOptions strict() {
        return new ConverterOptions(false, false, false, false, JsonSchemaDraft.DRAFT_07);
    }

    public ConverterOptions withDraft(JsonSchemaDraft draft) {
        return new ConverterOptions(flattenNullableUnions, additionalPropertiesFalse, omitEmptyRequired, javaTypeHints, draft);
    }
}
