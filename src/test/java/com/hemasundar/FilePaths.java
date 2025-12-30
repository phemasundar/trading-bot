package com.hemasundar;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FilePaths {
    public static final Path testConfig = Path.of("src/test/resources/test.properties");
    public static final Path securitiesConfig = Path.of("src/test/resources/securities.yaml");
}
