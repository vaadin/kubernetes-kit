package com.vaadin.kubernetes.starter.sessiontracker.backend;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BackendUtilTest {
    @Test
    void toByteMap_returnsCorrectMap() {
        Map<String, String> data = new HashMap<>();
        data.put("foo", "bar");

        Map<byte[], byte[]> byteMap = BackendUtil.toByteMap(data);
        assertEquals(1, byteMap.size());

        for (Map.Entry<byte[], byte[]> entry : byteMap.entrySet()) {
            assertArrayEquals(new byte[] { 'f', 'o', 'o' }, entry.getKey());
            assertArrayEquals(new byte[] { 'b', 'a', 'r' }, entry.getValue());
        }
    }

    @Test
    void fromByteMap_returnsCorrectMap() {
        Map<byte[], byte[]> data = new HashMap<>();
        data.put(new byte[] { 'f', 'o', 'o' }, new byte[] { 'b', 'a', 'r' });

        Map<String, String> stringMap = BackendUtil.fromByteMap(data);
        assertEquals(1, stringMap.size());

        for (Map.Entry<String, String> entry : stringMap.entrySet()) {
            assertEquals("foo", entry.getKey());
            assertEquals("bar", entry.getValue());
        }
    }

    @Test
    void b_byteArrayReturned() {
        assertArrayEquals(new byte[] { 'f', 'o', 'o' }, BackendUtil.b("foo"));
    }

    @Test
    void s_stringReturned() {
        assertEquals("foo", BackendUtil.s(new byte[] { 'f', 'o', 'o' }));
    }
}
