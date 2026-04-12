package com.hemasundar.utils;

import com.hemasundar.utils.JavaUtils;
import lombok.Data;
import org.testng.annotations.Test;
import java.util.Map;
import static org.testng.Assert.*;

public class JavaUtilsTest {

    public static class TestPojo {
        private String name;
        private int value;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }

    @Test
    public void testConvertJsonToPojo() {
        String json = "{\"name\":\"test\", \"value\":123}";
        TestPojo pojo = JavaUtils.convertJsonToPojo(json, TestPojo.class);
        assertEquals(pojo.getName(), "test");
        assertEquals(pojo.getValue(), 123);
    }

    @Test
    public void testConvertYamlToPojo() {
        String yaml = "name: test\nvalue: 123";
        TestPojo pojo = JavaUtils.convertYamlToPojo(yaml, TestPojo.class);
        assertEquals(pojo.getName(), "test");
        assertEquals(pojo.getValue(), 123);
    }

    @Test
    public void testConvertValue() {
        Map<String, Object> map = Map.of("name", "mapTest", "value", 456);
        TestPojo pojo = JavaUtils.convertValue(map, TestPojo.class);
        assertEquals(pojo.getName(), "mapTest");
        assertEquals(pojo.getValue(), 456);
    }

    @Test
    public void testConvertJsonToMap() {
        String json = "{\"key1\": {\"name\":\"v1\", \"value\":1}, \"key2\": {\"name\":\"v2\", \"value\":2}}";
        Map<String, TestPojo> map = JavaUtils.convertJsonToMap(json, TestPojo.class);
        assertEquals(map.size(), 2);
        assertEquals(map.get("key1").getName(), "v1");
        assertEquals(map.get("key2").getValue(), 2);
    }
}
