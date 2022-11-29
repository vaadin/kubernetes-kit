package com.vaadin.kubernetes.starter.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@EnabledIf(value = "com.vaadin.kubernetes.starter.test.Conditions#javaIOOpenForReflection", //
        disabledReason = "Test need reflection access to java.io package "
                + "(--add-opens java.base/java.io=ALL-UNNAMED')")
public @interface EnableOnJavaIOReflection {

}
