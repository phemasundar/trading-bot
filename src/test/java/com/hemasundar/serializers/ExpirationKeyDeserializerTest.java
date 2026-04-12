package com.hemasundar.serializers;

import com.hemasundar.options.models.OptionChainResponse;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ExpirationKeyDeserializerTest {

    @Test
    public void testDeserializeKey_WithColon() {
        ExpirationKeyDeserializer deserializer = new ExpirationKeyDeserializer();
        OptionChainResponse.ExpirationDateKey result = deserializer.deserializeKey("2024-05-17:1", null);
        
        Assert.assertEquals(result.getDate(), "2024-05-17");
        Assert.assertEquals(result.getDaysToExpiry(), 1);
    }

    @Test
    public void testDeserializeKey_WithoutColon() {
        ExpirationKeyDeserializer deserializer = new ExpirationKeyDeserializer();
        OptionChainResponse.ExpirationDateKey result = deserializer.deserializeKey("2024-05-17", null);
        
        Assert.assertEquals(result.getDate(), "2024-05-17");
        Assert.assertEquals(result.getDaysToExpiry(), 0);
    }

    @Test
    public void testDeserializeKey_Null() {
        ExpirationKeyDeserializer deserializer = new ExpirationKeyDeserializer();
        OptionChainResponse.ExpirationDateKey result = deserializer.deserializeKey(null, null);
        
        Assert.assertNull(result.getDate());
        Assert.assertEquals(result.getDaysToExpiry(), 0);
    }
}
