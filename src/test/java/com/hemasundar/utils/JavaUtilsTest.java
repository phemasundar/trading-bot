package com.hemasundar.utils;

import com.hemasundar.utils.JavaUtils;
import lombok.Data;
import org.testng.annotations.Test;
import java.time.LocalDate;
import java.util.Map;
import static org.testng.Assert.*;

public class JavaUtilsTest {

    public static class TestPojo {
        private String name;
        private int value;
        private LocalDate date;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
    }

    @Test
    public void testConvertJsonToPojo() {
        String json = "{\"name\":\"test\", \"value\":123, \"date\":\"2026-10-25\"}";
        TestPojo pojo = JavaUtils.convertJsonToPojo(json, TestPojo.class);
        assertEquals(pojo.getName(), "test");
        assertEquals(pojo.getValue(), 123);
        assertEquals(pojo.getDate(), LocalDate.of(2026, 10, 25));
    }

    @Test
    public void testConvertYamlToPojo() {
        String yaml = "name: test\nvalue: 123\ndate: 2026-10-25";
        TestPojo pojo = JavaUtils.convertYamlToPojo(yaml, TestPojo.class);
        assertEquals(pojo.getName(), "test");
        assertEquals(pojo.getValue(), 123);
        assertEquals(pojo.getDate(), LocalDate.of(2026, 10, 25));
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

    @Test
    public void testConstructor() throws Exception {
        var constructor = JavaUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        try {
            constructor.newInstance();
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue(e.getCause() instanceof UnsupportedOperationException);
        }
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testConvertYamlToPojo_Invalid() {
        JavaUtils.convertYamlToPojo("[[invalid yaml:::", TestPojo.class);
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testConvertYamlToJson_Invalid() {
        JavaUtils.convertYamlToJson("[[invalid yaml:::");
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testConvertJsonToPojo_Invalid() {
        JavaUtils.convertJsonToPojo("invalid json", TestPojo.class);
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testConvertJsonToMap_Invalid() {
        JavaUtils.convertJsonToMap("invalid json", TestPojo.class);
    }

    @Test
    public void testConvertJsonToPojo_EarningsCalendarResponse() {
        String json = """
            {
              "earningsCalendar": [
                {
                  "date": "2026-10-25",
                  "epsActual": null,
                  "epsEstimate": "1.5",
                  "hour": "amc",
                  "quarter": 4,
                  "revenueActual": null,
                  "revenueEstimate": 100000000,
                  "symbol": "NVDA",
                  "year": 2026
                }
              ]
            }
            """;
        com.hemasundar.pojos.EarningsCalendarResponse response = JavaUtils.convertJsonToPojo(json, com.hemasundar.pojos.EarningsCalendarResponse.class);
        assertNotNull(response);
        assertNotNull(response.getEarningsCalendar());
        assertEquals(response.getEarningsCalendar().size(), 1);
        assertEquals(response.getEarningsCalendar().get(0).getDate(), LocalDate.of(2026, 10, 25));
        assertEquals(response.getEarningsCalendar().get(0).getSymbol(), "NVDA");
    }
}
