package com.github.amaksoft.inwrapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker interface for unwrapping response from wrapper of specified type
 * <p>
 * Created by amak on 2018-02-13.
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PARAMETER})
public @interface InWrapper {
    Class[] value();
}
