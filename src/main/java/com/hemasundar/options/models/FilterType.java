package com.hemasundar.options.models;

import com.hemasundar.utils.JavaUtils;
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
    IRON_CONDOR("IronCondorFilter", IronCondorFilter.class),
    LONG_CALL_LEAP("LongCallLeapFilter", LongCallLeapFilter.class),
    BROKEN_WING_BUTTERFLY("BrokenWingButterflyFilter", BrokenWingButterflyFilter.class),
    ZEBRA("ZebraFilter", ZebraFilter.class),
    ;

    private final String jsonName;
    private final Class<? extends OptionsStrategyFilter> filterClass;

    /**
     * Parses a filter from an Object (e.g., Map from deserialized JSON).
     */
    public OptionsStrategyFilter parseFilter(Object filterObject) {
        return JavaUtils.convertValue(filterObject, filterClass);
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
