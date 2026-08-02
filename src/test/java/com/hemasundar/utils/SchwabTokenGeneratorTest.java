package com.hemasundar.utils;

import com.hemasundar.config.properties.SchwabConfig;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SchwabTokenGeneratorTest {

    @Test
    public void testGeneratorInitialization() {
        SchwabConfig config = new SchwabConfig();
        config.setAppKey("test-key");
        config.setAppSecret("test-secret");

        SchwabTokenGenerator generator = new SchwabTokenGenerator(config);
        Assert.assertNotNull(generator);
    }
}
