package com.hemasundar.services;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionType;
import com.hemasundar.pojos.IVDataPoint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Service to collect At-The-Money (ATM) Implied Volatility data for stocks.
 * Finds ATM options with ~30 DTE and extracts IV for both PUT and CALL.
 */
@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class IVDataCollector {

    private static final int TARGET_DTE = 30;
    private static final int DTE_TOLERANCE = 30;

    /**
     * Collects IV data point for a given symbol.
     * Finds ATM PUT and CALL options with ~30 DTE and extracts their IV values.
     *
     * @param symbol Stock symbol
     * @return IVDataPoint with PUT and CALL IV, or null if data cannot be collected
     */
    public static IVDataPoint collectIVDataPoint(String symbol) {
        try {
            log.info("[{}] Collecting IV data", symbol);

            // Get option chain
            OptionChainResponse chain = ThinkOrSwinAPIs.getOptionChain(symbol);
            if (chain == null || chain.getUnderlyingPrice() <= 0) {
                log.warn("[{}] Invalid option chain response", symbol);
                return null;
            }

            double underlyingPrice = chain.getUnderlyingPrice();

            // Find expiry date near target DTE
            String targetExpiry = findTargetExpiry(chain);
            if (targetExpiry == null) {
                log.warn("[{}] No expiry found near {} DTE", symbol, TARGET_DTE);
                return null;
            }

            // Find ATM strike
            Double atmStrike = findATMStrike(chain, targetExpiry, underlyingPrice);
            if (atmStrike == null) {
                log.warn("[{}] No ATM strike found for expiry {}", symbol, targetExpiry);
                return null;
            }

            // Get PUT and CALL options at ATM strike
            OptionChainResponse.OptionData atmPut = findOption(chain, OptionType.PUT, targetExpiry, atmStrike);
            OptionChainResponse.OptionData atmCall = findOption(chain, OptionType.CALL, targetExpiry, atmStrike);

            if (atmPut == null || atmCall == null) {
                log.warn("[{}] Missing PUT or CALL option at strike {} for expiry {}",
                        symbol, atmStrike, targetExpiry);
                return null;
            }

            // Extract IV values
            Double putIV = extractIV(atmPut);
            Double callIV = extractIV(atmCall);

            // Get market date from quote timestamp (not today's date)
            // quoteTimeInLong is in milliseconds since epoch
            LocalDate marketDate = getMarketDateFromTimestamp(atmPut.getQuoteTimeInLong());

            log.info("[{}] Collected IV - PUT: {}%, CALL: {}% (Strike: {}, DTE: {}, Market Date: {})",
                    symbol, putIV, callIV, atmStrike, atmPut.getDaysToExpiration(), marketDate);

            return IVDataPoint.builder()
                    .symbol(symbol)
                    .currentDate(marketDate) // Use market date, not LocalDate.now()
                    .atmPutIV(putIV)
                    .atmCallIV(callIV)
                    .dte(atmPut.getDaysToExpiration())
                    .expiryDate(targetExpiry)
                    .strike(atmStrike)
                    .underlyingPrice(underlyingPrice)
                    .build();

        } catch (Exception e) {
            log.error("[{}] Error collecting IV data: {}", symbol, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Finds the expiry date closest to target DTE.
     */
    private static String findTargetExpiry(OptionChainResponse chain) {
        List<String> expiryDates = chain.getExpiryDatesInRange(
                0,
                TARGET_DTE - DTE_TOLERANCE,
                TARGET_DTE + DTE_TOLERANCE);

        if (expiryDates.isEmpty()) {
            return null;
        }

        // Return the first expiry in the range (closest to target)
        return expiryDates.get(0);
    }

    /**
     * Finds the strike price closest to the underlying price (ATM).
     */
    private static Double findATMStrike(OptionChainResponse chain, String expiryDate, double underlyingPrice) {
        // Get all CALL options for this expiry (strikes are same for PUT and CALL)
        Map<String, List<OptionChainResponse.OptionData>> callMap = chain
                .getOptionDataForASpecificExpiryDate(OptionType.CALL, expiryDate);

        if (callMap == null || callMap.isEmpty()) {
            return null;
        }

        // Find strike closest to underlying price
        return callMap.values().stream()
                .flatMap(List::stream)
                .map(OptionChainResponse.OptionData::getStrikePrice)
                .distinct()
                .min((s1, s2) -> Double.compare(
                        Math.abs(s1 - underlyingPrice),
                        Math.abs(s2 - underlyingPrice)))
                .orElse(null);
    }

    /**
     * Finds a specific option (PUT or CALL) at a given strike and expiry.
     */
    private static OptionChainResponse.OptionData findOption(
            OptionChainResponse chain,
            OptionType optionType,
            String expiryDate,
            double strike) {

        Map<String, List<OptionChainResponse.OptionData>> optionMap = chain
                .getOptionDataForASpecificExpiryDate(optionType, expiryDate);

        if (optionMap == null || optionMap.isEmpty()) {
            return null;
        }

        return optionMap.values().stream()
                .flatMap(List::stream)
                .filter(opt -> Math.abs(opt.getStrikePrice() - strike) < 0.01)
                .findFirst()
                .orElse(null);
    }

    /**
     * Extracts Implied Volatility from option data.
     * Priority: volatility field (which may be Mark IV, Mid IV, or Model IV from
     * the API)
     *
     * Note: The Schwab API returns implied volatility in the 'volatility' field.
     * The specific type (Mark/Mid/Model) depends on what the API provides.
     *
     * @param option Option data
     * @return IV as percentage (e.g., 45.5 for 45.5%), or null if not available
     */
    private static Double extractIV(OptionChainResponse.OptionData option) {
        if (option == null) {
            return null;
        }

        double iv = option.getVolatility();

        // Volatility might be in decimal form (0.455) or percentage form (45.5)
        // If it's less than 5, assume it's decimal and convert to percentage
        if (iv > 0 && iv < 5) {
            iv = iv * 100;
        }

        return iv > 0 ? iv : null;
    }

    /**
     * Converts epoch timestamp (milliseconds) to LocalDate.
     * This gives us the actual market date when the data was last updated,
     * not the current date when the collection runs.
     *
     * @param timestampMillis Epoch timestamp in milliseconds
     * @return LocalDate representing the market date
     */
    private static LocalDate getMarketDateFromTimestamp(long timestampMillis) {
        if (timestampMillis <= 0) {
            // Fallback to current date if timestamp is invalid
            return LocalDate.now();
        }

        // Convert milliseconds to Instant, then to LocalDate in system timezone
        return java.time.Instant.ofEpochMilli(timestampMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();
    }
}
