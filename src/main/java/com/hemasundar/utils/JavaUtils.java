package com.hemasundar.utils;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.experimental.UtilityClass;

import java.util.Map;

@UtilityClass
public class JavaUtils {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();

    public static <T> T convertYamlToPojo(String yamlData, Class<T> tClass) {
        try {
            return YAML_MAPPER.readValue(yamlData, tClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert YAML to POJO: " + e.getMessage(), e);
        }
    }

    public static String convertYamlToJson(String yamlData) {
        try {
            Object pojo = YAML_MAPPER.readValue(yamlData, Object.class);
            return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(pojo);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert YAML to JSON: " + e.getMessage(), e);
        }
    }

    public static <T> T convertJsonToPojo(String jsonData, Class<T> tClass) {
        try {
            return JSON_MAPPER.readValue(jsonData, tClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert JSON to POJO: " + e.getMessage(), e);
        }
    }

    /**
     * Converts an Object (e.g., Map from deserialized JSON) to a typed POJO.
     * Useful for converting nested objects within a larger JSON structure.
     *
     * @param fromValue The source object (Map, LinkedHashMap, etc.)
     * @param toClass   The target class type
     * @param <T>       The target type
     * @return The converted POJO
     */
    public static <T> T convertValue(Object fromValue, Class<T> toClass) {
        return JSON_MAPPER.convertValue(fromValue, toClass);
    }

    /**
     * Converts JSON string to a Map with String keys and typed values.
     * Useful for API responses that return a map of symbol -> data.
     *
     * @param jsonData   The JSON string to parse
     * @param valueClass The class type for map values
     * @param <T>        The value type
     * @return Map of String to T
     */
    public static <T> Map<String, T> convertJsonToMap(String jsonData, Class<T> valueClass) {
        try {
            JavaType mapType = JSON_MAPPER.getTypeFactory()
                    .constructMapType(Map.class, String.class, valueClass);
            return JSON_MAPPER.readValue(jsonData, mapType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert JSON to Map: " + e.getMessage(), e);
        }
    }
}
