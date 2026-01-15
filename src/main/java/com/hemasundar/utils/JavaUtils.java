package com.hemasundar.utils;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.Map;

public class JavaUtils {
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder().build();

    public static <T> T convertYamlToPojo(String yamlData, Class<T> tClass) {
        return YAML_MAPPER.readValue(yamlData, tClass);
    }

    public static <T> T convertJsonToPojo(String jsonData, Class<T> tClass) {
        return JSON_MAPPER.readValue(jsonData, tClass);
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
        JavaType mapType = JSON_MAPPER.getTypeFactory()
                .constructMapType(Map.class, String.class, valueClass);
        return JSON_MAPPER.readValue(jsonData, mapType);
    }

}
