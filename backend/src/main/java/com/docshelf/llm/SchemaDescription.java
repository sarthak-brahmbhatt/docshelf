// Optional description for a record component or record type, copied into the generated JSON schema
package com.docshelf.llm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.TYPE, ElementType.FIELD})
public @interface SchemaDescription {
    String value();
}
