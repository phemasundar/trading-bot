package com.hemasundar.serializers;

import com.hemasundar.options.models.OptionChainResponse;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;

public class ExpirationKeyDeserializer extends KeyDeserializer {
    @Override
    public OptionChainResponse.ExpirationDateKey deserializeKey(String key, DeserializationContext ctxt) {
        if (key == null || !key.contains(":")) {
            return new OptionChainResponse.ExpirationDateKey(key, 0);
        }
        String[] parts = key.split(":");
        return new OptionChainResponse.ExpirationDateKey(parts[0], Integer.parseInt(parts[1]));
    }
}
