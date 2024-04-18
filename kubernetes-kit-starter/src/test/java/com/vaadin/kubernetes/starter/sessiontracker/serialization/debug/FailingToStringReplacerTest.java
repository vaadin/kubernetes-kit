package com.vaadin.kubernetes.starter.sessiontracker.serialization.debug;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.vaadin.kubernetes.starter.sessiontracker.serialization.TransientHandler;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.TransientInjectableObjectInputStream;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.TransientInjectableObjectOutputStream;
import com.vaadin.kubernetes.starter.test.EnableOnJavaIOReflection;

import static org.assertj.core.api.Assertions.assertThat;

@EnableOnJavaIOReflection
class FailingToStringReplacerTest {

    @Test
    void toStringReplacer_objectSerialized()
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        TransientHandler handler = new DebugTransientHandler(
                new Job("SID", "KEY"));

        ThrowingToStringWithFields obj = new ThrowingToStringWithFields();

        try (TransientInjectableObjectOutputStream tos = TransientInjectableObjectOutputStream
                .newInstance(os, handler)) {
            tos.writeWithTransients(obj);
        }

        try (TransientInjectableObjectInputStream tis = new TransientInjectableObjectInputStream(
                new ByteArrayInputStream(os.toByteArray()), handler)) {
            ThrowingToStringWithFields out = tis.readWithTransients();
            assertThat(out).usingRecursiveComparison().isEqualTo(obj);
        }
    }

    private static class ThrowingToStringWithFields implements Serializable {

        String x = null;
        String string = "TEST";

        int primitive = 10;

        List<Object> list = List.of("TEST", 123);

        Map<String, Object> map = Map.of("TEST", 123);

        @Override
        public String toString() {
            return "Booom! " + x.length();
        }
    }

}
