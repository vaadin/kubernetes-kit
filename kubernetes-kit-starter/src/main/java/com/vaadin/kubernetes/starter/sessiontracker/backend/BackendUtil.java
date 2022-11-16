package com.vaadin.kubernetes.starter.sessiontracker.backend;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class BackendUtil {
    public static Map<byte[], byte[]> toByteMap(Map<String, String> data) {
        Map<byte[], byte[]> result = new HashMap<>();
        for (String key : data.keySet()) {
            result.put(b(key), b(data.get(key)));
        }
        return result;
    }

    public static Map<String, String> fromByteMap(Map<byte[], byte[]> data) {
        Map<String, String> result = new HashMap<>();
        for (byte[] key : data.keySet()) {
            result.put(s(key), s(data.get(key)));
        }
        return result;
    }

    public static byte[] b(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }

    public static String s(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }

}
