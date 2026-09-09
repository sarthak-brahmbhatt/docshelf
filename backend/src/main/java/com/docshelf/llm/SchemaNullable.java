// Marks a record component as nullable in the generated strict JSON schema (alternative to wrapping in Optional)
package com.docshelf.llm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD})
public @interface SchemaNullable {
}
