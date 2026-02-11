package org.metalib.schema.avro.json;

public enum JsonSchemaDraft {
    DRAFT_07("http://json-schema.org/draft-07/schema#", "definitions"),
    DRAFT_2020_12("https://json-schema.org/draft/2020-12/schema", "$defs");

    private final String schemaUrl;
    private final String definitionsKeyword;
    private final String refPrefix;

    JsonSchemaDraft(String schemaUrl, String definitionsKeyword) {
        this.schemaUrl = schemaUrl;
        this.definitionsKeyword = definitionsKeyword;
        this.refPrefix = "#/" + definitionsKeyword + "/";
    }

    public String schemaUrl() { return schemaUrl; }
    public String definitionsKeyword() { return definitionsKeyword; }
    public String refPrefix() { return refPrefix; }
}
