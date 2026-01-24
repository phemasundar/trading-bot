package com.hemasundar.options.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum for filter types used in JSON configuration.
 * Each type knows how to deserialize its filter from JSON.
 */
@Getter
@RequiredArgsConstructor
public enum FilterType {

    CREDIT_SPREAD("CreditSpreadFilter", CreditSpreadFilter.class),
    LONG_CALL_LEAP("LongCallLeapFilter", LongCallLeapFilter.class),
    BROKEN_WING_BUTTERFLY("BrokenWingButterflyFilter", BrokenWingButterflyFilter.class);

    private final String jsonName;
    private final Class<? extends OptionsStrategyFilter> filterClass;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Parses a filter from JSON using this filter type's class.
     */
    public OptionsStrategyFilter parseFilter(JsonNode filterNode) throws Exception {
        return MAPPER.treeToValue(filterNode, filterClass);
    }

    /**
     * Finds the FilterType by its JSON name string.
     */
    public static FilterType fromJsonName(String jsonName) {
        for (FilterType type : values()) {
            if (type.jsonName.equals(jsonName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown filter type: " + jsonName);
    }
}
