package com.hemasundar.serializers;

import com.hemasundar.pojos.OptionChainResponse;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.KeyDeserializer;

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
