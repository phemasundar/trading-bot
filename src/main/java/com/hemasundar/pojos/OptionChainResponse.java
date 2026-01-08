package com.hemasundar.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hemasundar.serializers.ExpirationKeyDeserializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OptionChainResponse {
    public String symbol;
    public String status;
    public Object underlying;
    public String strategy;
    public int interval;
    public boolean isDelayed;
    public boolean isIndex;
    public double interestRate;
    public double underlyingPrice;
    public double volatility;
    public int daysToExpiration;
    public double dividendYield;
    public int numberOfContracts;
    public String assetMainType;
    public String assetSubType;
    public boolean isChainTruncated;

    // Map keys: Expiration Date String (e.g., "2026-01-02:6")
    // Nested Map keys: Strike Price String (e.g., "110.0")
    @JsonDeserialize(keyUsing = ExpirationKeyDeserializer.class)
    public Map<ExpirationDateKey, Map<String, List<OptionData>>> callExpDateMap;
    @JsonDeserialize(keyUsing = ExpirationKeyDeserializer.class)
    public Map<ExpirationDateKey, Map<String, List<OptionData>>> putExpDateMap;

    public Map<String, List<OptionChainResponse.OptionData>> getOptionDataForASpecificExpiryDate(OptionType optionType, String targetExpiryDate) {
        Map<ExpirationDateKey, Map<String, List<OptionData>>> expDateMap = (optionType == OptionType.PUT) ? this.getPutExpDateMap() : this.getCallExpDateMap();
        Map<String, List<OptionChainResponse.OptionData>> putMap = expDateMap.entrySet().stream()
                .filter(entry ->
                        // Access the 'date' field within the complex key object
                        entry.getKey().getDate().equals(targetExpiryDate))
                .map(Map.Entry::getValue)
                .findFirst().orElse(Map.of());
//        System.out.println(putMap);
        return putMap;
    }

    public String getExpiryDateBasedOnDTE(int targetDays) {
        String targetExpiryDate = this.getPutExpDateMap()
                .keySet().stream()
                // Find key with minimum absolute difference from target
                .min(Comparator.comparingInt(key ->
                        Math.abs(key.getDaysToExpiry() - targetDays)))
                // Extract only the date field from that key
                .map(OptionChainResponse.ExpirationDateKey::getDate)
                .orElseThrow(() -> new RuntimeException("No Expiry Found"));
        System.out.println("Target Expiry Date: " + targetExpiryDate);
        return targetExpiryDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpirationDateKey {
        private String date;
        private int daysToExpiry;

        // This is required for the Map to look up keys correctly
        @Override
        public String toString() {
            return date + ":" + daysToExpiry;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OptionData {
        public String putCall;
        public String symbol;
        public String description;
        public String exchangeName;
        public double bid;
        public double ask;
        public double last;
        public double mark;
        public int bidSize;
        public int askSize;
        public String bidAskSize;
        public double lastSize;
        public double highPrice;
        public double lowPrice;
        public double openPrice;
        public double closePrice;
        public int totalVolume;
        public long tradeTimeInLong;
        public long quoteTimeInLong;
        public double netChange;
        public double volatility;
        public double delta;
        public double gamma;
        public double theta;
        public double vega;
        public double rho;
        public int openInterest;
        public double timeValue;
        public double theoreticalOptionValue;
        public double theoreticalVolatility;
        public List<OptionDeliverable> optionDeliverablesList;
        public double strikePrice;
        public String expirationDate;
        public int daysToExpiration;
        public String expirationType;
        public long lastTradingDay;
        public int multiplier;
        public String settlementType;
        public String deliverableNote;
        public double percentChange;
        public double markChange;
        public double markPercentChange;
        public double intrinsicValue;
        public double extrinsicValue;
        public String optionRoot;
        public String exerciseType;
        public boolean inTheMoney;
        public boolean mini;
        public boolean nonStandard;
        public boolean pennyPilot;

        public double getAbsDelta() {
            return Math.abs(this.getDelta());
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OptionDeliverable {
        public String symbol;
        public String assetType;
        public int deliverableUnits;
        public String currencyType;
    }
}

