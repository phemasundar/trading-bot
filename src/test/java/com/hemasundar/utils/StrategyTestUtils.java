package com.hemasundar.utils;

import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionChainResponse.ExpirationDateKey;
import com.hemasundar.options.models.OptionChainResponse.OptionData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StrategyTestUtils {

    public static OptionChainResponse createMockChain(String symbol, double underlyingPrice) {
        OptionChainResponse chain = new OptionChainResponse();
        chain.setSymbol(symbol);
        chain.setUnderlyingPrice(underlyingPrice);
        chain.setCallExpDateMap(new HashMap<>());
        chain.setPutExpDateMap(new HashMap<>());
        return chain;
    }

    public static void addOption(OptionChainResponse chain, String expiry, int dte, double strike,
            double bid, double ask, double delta, boolean isPut) {
        ExpirationDateKey key = new ExpirationDateKey(expiry, dte);
        Map<ExpirationDateKey, Map<String, List<OptionData>>> expMap = isPut ? chain.getPutExpDateMap()
                : chain.getCallExpDateMap();

        Map<String, List<OptionData>> strikeMap = expMap.computeIfAbsent(key, k -> new HashMap<>());
        List<OptionData> options = strikeMap.computeIfAbsent(String.valueOf(strike), k -> new ArrayList<>());

        OptionData data = new OptionData();
        data.setStrikePrice(strike);
        data.setBid(bid);
        data.setAsk(ask);
        data.setMark((bid + ask) / 2.0);
        data.setVolatility(50.0); // Default 50% IV for tests
        data.setDelta(delta);
        data.setPutCall(isPut ? "PUT" : "CALL");
        data.setExpirationDate(expiry);
        data.setDaysToExpiration(dte);
        data.setQuoteTimeInLong(System.currentTimeMillis());

        options.add(data);
    }

    /**
     * Adds an option with a custom IV value (for IV interpolation tests).
     */
    public static void addOption(OptionChainResponse chain, String expiry, int dte, double strike,
            double bid, double ask, double delta, boolean isPut, double iv) {
        ExpirationDateKey key = new ExpirationDateKey(expiry, dte);
        Map<ExpirationDateKey, Map<String, List<OptionData>>> expMap = isPut ? chain.getPutExpDateMap()
                : chain.getCallExpDateMap();

        Map<String, List<OptionData>> strikeMap = expMap.computeIfAbsent(key, k -> new HashMap<>());
        List<OptionData> options = strikeMap.computeIfAbsent(String.valueOf(strike), k -> new ArrayList<>());

        OptionData data = new OptionData();
        data.setStrikePrice(strike);
        data.setBid(bid);
        data.setAsk(ask);
        data.setMark((bid + ask) / 2.0);
        data.setVolatility(iv);
        data.setDelta(delta);
        data.setPutCall(isPut ? "PUT" : "CALL");
        data.setExpirationDate(expiry);
        data.setDaysToExpiration(dte);
        data.setQuoteTimeInLong(System.currentTimeMillis());

        options.add(data);
    }

    /**
     * Convenience alias for tests that need to specify a custom IV.
     */
    public static void addOptionWithIV(OptionChainResponse chain, String expiry, int dte, double strike,
            double bid, double ask, double delta, boolean isPut, double iv) {
        addOption(chain, expiry, dte, strike, bid, ask, delta, isPut, iv);
    }
}
