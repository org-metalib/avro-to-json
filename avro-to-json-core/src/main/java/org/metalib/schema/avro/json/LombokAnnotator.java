package org.metalib.schema.avro.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JDefinedClass;
import org.jsonschema2pojo.AbstractAnnotator;
import org.jsonschema2pojo.GenerationConfig;

/**
 * A custom jsonschema2pojo annotator that adds Lombok annotations to generated classes.
 *
 * <p>Adds {@code @Data}, {@code @Builder}, {@code @NoArgsConstructor}, and
 * {@code @AllArgsConstructor} to every generated POJO class. These annotations
 * are added on the {@link #propertyOrder} callback, which fires once per class
 * after all properties have been defined.
 *
 * <p>When using this annotator, disable jsonschema2pojo's built-in generation of
 * getters, setters, equals, hashCode, toString, builders, and constructors, since
 * Lombok handles all of these.
 */
public class LombokAnnotator extends AbstractAnnotator {

    public LombokAnnotator() {
    }

    public LombokAnnotator(GenerationConfig generationConfig) {
        super(generationConfig);
    }

    @Override
    public void propertyOrder(JDefinedClass clazz, JsonNode propertiesNode) {
        clazz.annotate(clazz.owner().ref("lombok.Data"));
        JAnnotationUse builder = clazz.annotate(clazz.owner().ref("lombok.Builder"));
        builder.param("toBuilder", true);
        clazz.annotate(clazz.owner().ref("lombok.NoArgsConstructor"));
        if (!clazz.fields().isEmpty()) {
            clazz.annotate(clazz.owner().ref("lombok.AllArgsConstructor"));
        }
    }
}
