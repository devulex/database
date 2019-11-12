package com.editbox.database.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD})
@Retention(RUNTIME)
public @interface MaxLength {

    /**
     * The field length. Applies to a string values and an array values.
     */
    int value() default 255;
}
