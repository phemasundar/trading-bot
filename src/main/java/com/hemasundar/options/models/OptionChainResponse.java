package com.hemasundar.options.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hemasundar.technical.MathExpression;
import com.hemasundar.technical.MathExpressionEvaluator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Represents the Schwab API option chain response.
 * <p>
 * Unused API response fields are retained in this class and {@link OptionData} for developer
 * schema discoverability, but annotated with {@link JsonIgnore} so Jackson skips deserializing
 * and allocating unused {@link String} and {@link List} objects across thousands of option contracts.
 * If a field is needed in the future, simply remove its {@code @JsonIgnore} annotation.
 */
@Data
@Log4j2
@NoArgsConstructor
@ToString(of = {"symbol", "underlyingPrice"})
@JsonIgnoreProperties(ignoreUnknown = true)
public class OptionChainResponse {
    public String symbol;
    @JsonIgnore public String status;
    public Object underlying;
    @JsonIgnore public String strategy;
    @JsonIgnore public int interval;
    @JsonIgnore public boolean isDelayed;
    @JsonIgnore public boolean isIndex;
    public double interestRate;
    public double underlyingPrice;
    public double volatility;
    public int daysToExpiration;
    public double dividendYield;
    @JsonIgnore public int numberOfContracts;
    @JsonIgnore public String assetMainType;
    @JsonIgnore public String assetSubType;
    @JsonIgnore public boolean isChainTruncated;

    // Map keys: Expiration Date String (e.g., "2026-01-02:6")
    // Nested Map keys: Strike Price String (e.g., "110.0")
    public Map<ExpirationDateKey, Map<String, List<OptionData>>> callExpDateMap;
    public Map<ExpirationDateKey, Map<String, List<OptionData>>> putExpDateMap;

    public double getUnderlyingPrice() {
        if (this.underlyingPrice > 0) {
            return this.underlyingPrice;
        }
        if (this.underlying instanceof Map<?, ?> map) {
            for (String key : List.of("last", "mark", "close", "bid", "ask")) {
                Object val = map.get(key);
                if (val instanceof Number num && num.doubleValue() > 0) {
                    return num.doubleValue();
                }
            }
        }
        return this.underlyingPrice;
    }

    public Map<String, List<OptionChainResponse.OptionData>> getOptionDataForASpecificExpiryDate(OptionType optionType,
            String targetExpiryDate) {
        Map<ExpirationDateKey, Map<String, List<OptionData>>> expDateMap = (optionType == OptionType.PUT)
                ? this.getPutExpDateMap()
                : this.getCallExpDateMap();
        return expDateMap.entrySet().stream()
                .filter(entry -> entry.getKey().getDate().equals(targetExpiryDate))
                .map(Map.Entry::getValue)
                .findFirst().orElse(Map.of());
    }

    public String getExpiryDateBasedOnDTE(int targetDays) {
        Map<ExpirationDateKey, Map<String, List<OptionData>>> callMap = this.getCallExpDateMap();
        Map<ExpirationDateKey, Map<String, List<OptionData>>> putMap = this.getPutExpDateMap();

        String targetExpiryDate = Stream.of(putMap, callMap)
                .filter(m -> m != null && !m.isEmpty())
                .flatMap(m -> m.keySet().stream())
                .distinct()
                .min(Comparator.comparingInt(key -> Math.abs(key.getDaysToExpiry() - targetDays)))
                .map(OptionChainResponse.ExpirationDateKey::getDate)
                .orElse(null);
        if (targetExpiryDate != null) {
            log.debug("[{}] Target Expiry Date: {}", this.symbol, targetExpiryDate);
        }
        return targetExpiryDate;
    }

    /**
     * Removes invalid options (e.g., bad data from API) from the internal maps.
     * Prevents bad data from reaching strategies.
     */
    public void removeInvalidOptions() {
        removeInvalidFromMap(callExpDateMap);
        removeInvalidFromMap(putExpDateMap);
    }

    private void removeInvalidFromMap(Map<ExpirationDateKey, Map<String, List<OptionData>>> dateMap) {
        if (dateMap == null)
            return;

        for (Map<String, List<OptionData>> strikeMap : dateMap.values()) {
            if (strikeMap == null)
                continue;

            for (List<OptionData> options : strikeMap.values()) {
                if (options == null)
                    continue;

                options.removeIf(option -> Math.abs(option.getDelta()) > 10);
            }
        }
    }

    /**
     * Returns true if the response contains at least one option expiration
     * (either CALL or PUT). A non-null response with empty maps indicates the
     * symbol is not optionable.
     */
    public boolean hasOptions() {
        return hasOptionsInMap(callExpDateMap) || hasOptionsInMap(putExpDateMap);
    }

    private boolean hasOptionsInMap(Map<ExpirationDateKey, Map<String, List<OptionData>>> dateMap) {
        if (dateMap == null || dateMap.isEmpty()) {
            return false;
        }
        return dateMap.values().stream()
                .anyMatch(strikeMap -> strikeMap != null && !strikeMap.isEmpty());
    }

    /**
     * Returns all expiry dates where DTE falls within the specified range
     * (inclusive).
     *
     * @param minDTE Minimum days to expiration
     * @param maxDTE Maximum days to expiration
     * @return List of expiry date strings (YYYY-MM-DD format)
     */
    public List<String> getExpiryDatesInRange(int targetDays, int minDTE, int maxDTE) {
        if (targetDays > 0) {
            return List.of(this.getExpiryDateBasedOnDTE(targetDays));
        } else {
            Map<ExpirationDateKey, Map<String, List<OptionData>>> callMap = this.getCallExpDateMap();
            Map<ExpirationDateKey, Map<String, List<OptionData>>> putMap = this.getPutExpDateMap();

            List<String> expiryDates = Stream.of(putMap, callMap)
                    .filter(m -> m != null && !m.isEmpty())
                    .flatMap(m -> m.keySet().stream())
                    .distinct()
                    .filter(key -> key.getDaysToExpiry() >= minDTE && key.getDaysToExpiry() <= maxDTE)
                    .sorted(Comparator.comparingInt(ExpirationDateKey::getDaysToExpiry))
                    .map(ExpirationDateKey::getDate)
                    .toList();
            log.debug("[{}] Found {} expiry dates in range [{}-{}]: {}",
                    this.symbol, expiryDates.size(), minDTE, maxDTE, expiryDates);
            return expiryDates;
        }
    }

    /**
     * Returns all expiry dates matching DTE expressions or targetDays.
     *
     * @param targetDays     if > 0, returns the nearest expiry
     * @param dteExpressions math expressions targeting DTE
     * @return List of expiry date strings (YYYY-MM-DD format)
     */
    public List<String> getExpiryDatesMatching(int targetDays, List<MathExpression> dteExpressions) {
        if (targetDays > 0) {
            return List.of(this.getExpiryDateBasedOnDTE(targetDays));
        }

        Map<ExpirationDateKey, Map<String, List<OptionData>>> callMap = this.getCallExpDateMap();
        Map<ExpirationDateKey, Map<String, List<OptionData>>> putMap = this.getPutExpDateMap();

        List<String> expiryDates = Stream.of(putMap, callMap)
                .filter(m -> m != null && !m.isEmpty())
                .flatMap(m -> m.keySet().stream())
                .distinct()
                .filter(key -> {
                    if (CollectionUtils.isEmpty(dteExpressions)) {
                        return true;
                    }
                    return MathExpressionEvaluator.evaluateAll(
                            dteExpressions,
                            var -> "DTE".equalsIgnoreCase(var)
                                    ? (double) key.getDaysToExpiry() : null);
                })
                .sorted(Comparator.comparingInt(ExpirationDateKey::getDaysToExpiry))
                .map(ExpirationDateKey::getDate)
                .toList();

        log.debug("[{}] Found {} expiry dates matching DTE expressions {}: {}",
                this.symbol, expiryDates.size(), dteExpressions, expiryDates);
        return expiryDates;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpirationDateKey {
        private String date;
        private int daysToExpiry;

        // Constructor for Jackson Key Deserialization
        public ExpirationDateKey(String source) {
            if (source != null && source.contains(":")) {
                String[] parts = source.split(":");
                this.date = parts[0];
                this.daysToExpiry = Integer.parseInt(parts[1]);
            } else {
                this.date = source;
                this.daysToExpiry = 0;
            }
        }

        // This is required for the Map to look up keys correctly
        @Override
        public String toString() {
            return date + ":" + daysToExpiry;
        }
    }

    /**
     * Individual option contract data from Schwab's option chain response.
     * <p>
     * All fields returned by the Schwab API are declared here so developers can inspect the complete
     * contract schema in IDE autocomplete. Fields not used by strategy filtering, trade construction,
     * or IV collection are annotated with {@link JsonIgnore} to avoid allocating thousands of unused
     * {@link String} and {@link List} objects per option chain. Remove {@code @JsonIgnore} on any
     * field if needed by new strategy logic.
     */
    @Data
    @ToString(of = {"symbol", "mark"})
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OptionData {
        public String putCall;
        public String symbol;
        @JsonIgnore public String description;
        @JsonIgnore public String exchangeName;
        public double bid;
        public double ask;
        public double last;
        public double mark;
        @JsonIgnore public int bidSize;
        @JsonIgnore public int askSize;
        @JsonIgnore public String bidAskSize;
        @JsonIgnore public double lastSize;
        @JsonIgnore public double highPrice;
        @JsonIgnore public double lowPrice;
        @JsonIgnore public double openPrice;
        @JsonIgnore public double closePrice;
        public int totalVolume;
        @JsonIgnore public long tradeTimeInLong;
        public long quoteTimeInLong;
        @JsonIgnore public double netChange;
        public double volatility;
        public double delta;
        public double gamma;
        public double theta;
        public double vega;
        public double rho;
        public int openInterest;
        public double timeValue;
        @JsonIgnore public double theoreticalOptionValue;
        @JsonIgnore public double theoreticalVolatility;
        @JsonIgnore public List<OptionDeliverable> optionDeliverablesList;
        public double strikePrice;
        public String expirationDate;
        public int daysToExpiration;
        @JsonIgnore public String expirationType;
        @JsonIgnore public long lastTradingDay;
        @JsonIgnore public int multiplier;
        @JsonIgnore public String settlementType;
        @JsonIgnore public String deliverableNote;
        @JsonIgnore public double percentChange;
        @JsonIgnore public double markChange;
        @JsonIgnore public double markPercentChange;
        public double intrinsicValue;
        public double extrinsicValue;
        @JsonIgnore public String optionRoot;
        @JsonIgnore public String exerciseType;
        @JsonIgnore public double high52Week; // 52-week high for the option
        @JsonIgnore public double low52Week; // 52-week low for the option
        @JsonIgnore public boolean pennyPilot;
        @JsonIgnore public boolean inTheMoney;
        @JsonIgnore public boolean mini;
        @JsonIgnore public boolean nonStandard;

        public double getAbsDelta() {
            return Math.abs(this.getDelta());
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString(of = {"symbol"})
    public static class OptionDeliverable {
        public String symbol;
        public String assetType;
        public int deliverableUnits;
        public String currencyType;
    }
}
