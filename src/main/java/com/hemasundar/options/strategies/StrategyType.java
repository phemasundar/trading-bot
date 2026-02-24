package com.hemasundar.options.strategies;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Enum defining available trading strategy types with their display names.
 * Each type knows how to create its own strategy instance (Factory Pattern).
 */
@Getter
@RequiredArgsConstructor
public enum StrategyType {

    // Put Credit Spread Strategies
    PUT_CREDIT_SPREAD("Put Credit Spread") {
        @Override
        public AbstractTradingStrategy createStrategy() {
            return new PutCreditSpreadStrategy();
        }
    },
    TECH_PUT_CREDIT_SPREAD("Technical Put Credit Spread") {
        @Override
        public AbstractTradingStrategy createStrategy() {
            return new PutCreditSpreadStrategy(this);
        }
    },
    BULLISH_LONG_PUT_CREDIT_SPREAD("Bullish Long Put Credit Spread") {
        @Override
        public AbstractTradingStrategy createStrategy() {
            return new PutCreditSpreadStrategy(this);
        }
    },

    // Call Credit Spread Strategies
    CALL_CREDIT_SPREAD("Call Credit Spread") {
        @Override
        public AbstractTradingStrategy createStrategy() {
            return new CallCreditSpreadStrategy();
        }
    },
    TECH_CALL_CREDIT_SPREAD("Technical Call Credit Spread") {
        @Override
        public AbstractTradingStrategy createStrategy() {
            return new CallCreditSpreadStrategy(this);
        }
    },

    // Other Strategies
    IRON_CONDOR("Iron Condor") {
        @Override
        public AbstractTradingStrategy createStrategy() {
            return new IronCondorStrategy();
        }
    },
    BULLISH_LONG_IRON_CONDOR("Bullish Long Iron Condor") {
        @Override
        public AbstractTradingStrategy createStrategy() {
            return new IronCondorStrategy(this);
        }
    },
    LONG_CALL_LEAP("Long Call LEAP") {
        @Override
        public AbstractTradingStrategy createStrategy() {
            return new LongCallLeapStrategy();
        }
    },
    LONG_CALL_LEAP_TOP_N("Long Call LEAP Top N") {
        @Override
        public AbstractTradingStrategy createStrategy() {
            return new LongCallLeapTopNStrategy();
        }
    },
    BULLISH_BROKEN_WING_BUTTERFLY("Bullish Broken Wing Butterfly") {
        @Override
        public AbstractTradingStrategy createStrategy() {
            return new BrokenWingButterflyStrategy();
        }
    },
    BULLISH_ZEBRA("Bullish ZEBRA") {
        @Override
        public AbstractTradingStrategy createStrategy() {
            return new ZebraStrategy();
        }
    };

    private final String displayName;

    /**
     * Creates a new strategy instance for this type.
     * Factory method pattern - each enum value knows how to create its strategy.
     */
    public abstract AbstractTradingStrategy createStrategy();

    /**
     * Jackson deserializer - allows parsing from enum name (e.g.,
     * "PUT_CREDIT_SPREAD").
     */
    @JsonCreator
    public static StrategyType fromString(String value) {
        return StrategyType.valueOf(value);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
