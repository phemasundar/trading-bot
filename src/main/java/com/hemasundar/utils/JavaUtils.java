package com.hemasundar.utils;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

public class JavaUtils {
    public static <T> T convertYamlToPojo (String yamlData, Class<T> tClass ) {
        return YAMLMapper.builder().build().readValue(yamlData, tClass);
    }

    public static <T> T convertJsonToPojo (String jsonData, Class<T> tClass ) {
        return JsonMapper.builder().build().readValue(jsonData, tClass);
    }

}
