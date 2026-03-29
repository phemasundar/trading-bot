package com.hemasundar.serializers;

import com.hemasundar.options.models.OptionChainResponse;
import com.fasterxml.jackson.databind.DeserializationContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;

public class ExpirationKeyDeserializerTest {

    private ExpirationKeyDeserializer deserializer;
    private DeserializationContext mockContext;

    @BeforeMethod
    public void setUp() {
        deserializer = new ExpirationKeyDeserializer();
        mockContext = mock(DeserializationContext.class);
    }

    @Test
    public void testDeserializeKey_Valid() {
        String key = "2024-01-01:45";
        OptionChainResponse.ExpirationDateKey result = deserializer.deserializeKey(key, mockContext);
        assertEquals(result.getDate(), "2024-01-01");
        assertEquals(result.getDaysToExpiry(), 45);
    }

    @Test
    public void testDeserializeKey_Null() {
        OptionChainResponse.ExpirationDateKey result = deserializer.deserializeKey(null, mockContext);
        assertEquals(result.getDate(), null);
        assertEquals(result.getDaysToExpiry(), 0);
    }

    @Test
    public void testDeserializeKey_NoColon() {
        String key = "2024-01-01";
        OptionChainResponse.ExpirationDateKey result = deserializer.deserializeKey(key, mockContext);
        assertEquals(result.getDate(), "2024-01-01");
        assertEquals(result.getDaysToExpiry(), 0);
    }

    @Test(expectedExceptions = NumberFormatException.class)
    public void testDeserializeKey_MalformedDays() {
        String key = "2024-01-01:abc";
        deserializer.deserializeKey(key, mockContext);
    }
}
