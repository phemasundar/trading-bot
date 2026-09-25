package com.hemasundar.services;

import com.hemasundar.apis.ThinkOrSwimAPIs;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionChainResponse.ExpirationDateKey;
import com.hemasundar.options.models.OptionType;
import com.hemasundar.pojos.IVDataPoint;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Collects constant-maturity 30-day ATM Implied Volatility for stocks.
 *
 * <p>Finds two expiration cycles bracketing 30 DTE, extracts blended Call+Put
 * ATM IV from each, and applies CBOE-style total variance interpolation to
 * produce a synthetic σ₃₀ free of maturity drift and roll jumps.
 *
 * <p>Falls back to single-expiry blended IV when bracketing is unavailable.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class IVDataCollector {

    private final ThinkOrSwimAPIs ThinkOrSwimAPIs;

    private static final int TARGET_DTE = 30;
    /** Minimum DTE to avoid gamma noise from ultra-short expiries. */
    private static final int MIN_DTE_THRESHOLD = 7;

    /**
     * Collects a constant-maturity 30-day IV data point for a given symbol.
     *
     * <p>Flow: chain → bracket expirations around 30 DTE → extract blended ATM IV
     * per expiration → variance-interpolate to σ₃₀ → IVDataPoint.
     *
     * @param symbol Stock symbol
     * @return IVDataPoint with synthetic 30-day IV, or null if data cannot be collected
     */
    public IVDataPoint collectIVDataPoint(String symbol) {
        try {
            log.info("[{}] Collecting IV data", symbol);

            OptionChainResponse chain = ThinkOrSwimAPIs.getOptionChain(symbol);
            if (chain == null) {
                log.warn("[{}] Invalid option chain response", symbol);
                return null;
            }

            if (!chain.hasOptions()) {
                log.info("[{}] No options available - skipping", symbol);
                return IVDataPoint.builder()
                        .symbol(symbol)
                        .noOptions(true)
                        .build();
            }

            double underlyingPrice = chain.getUnderlyingPrice();
            if (underlyingPrice <= 0) {
                log.warn("[{}] Invalid underlying price in option chain response", symbol);
                return null;
            }

            ExpiryBracket bracket = findBracketingExpiries(chain);
            if (bracket == null) {
                log.warn("[{}] No usable expiry found near {} DTE", symbol, TARGET_DTE);
                return null;
            }

            Double sigma30;
            ExpirationDateKey primaryExpiry;

            if (bracket.isInterpolationNeeded()) {
                Double sigma1 = extractBlendedATMIV(chain, bracket.getNearTerm(), underlyingPrice, symbol);
                Double sigma2 = extractBlendedATMIV(chain, bracket.getNextTerm(), underlyingPrice, symbol);

                if (sigma1 != null && sigma2 != null) {
                    sigma30 = interpolate30DayIV(sigma1, bracket.getNearTerm().getDaysToExpiry(),
                            sigma2, bracket.getNextTerm().getDaysToExpiry());
                    log.info("[{}] Interpolated 30-day IV: {}% (T1: DTE={} σ={}%, T2: DTE={} σ={}%)",
                            symbol, String.format("%.2f", sigma30),
                            bracket.getNearTerm().getDaysToExpiry(), String.format("%.2f", sigma1),
                            bracket.getNextTerm().getDaysToExpiry(), String.format("%.2f", sigma2));
                } else if (sigma1 != null) {
                    sigma30 = sigma1;
                    log.info("[{}] Fallback to near-term IV: {}% (DTE={})", symbol,
                            String.format("%.2f", sigma30), bracket.getNearTerm().getDaysToExpiry());
                } else if (sigma2 != null) {
                    sigma30 = sigma2;
                    log.info("[{}] Fallback to next-term IV: {}% (DTE={})", symbol,
                            String.format("%.2f", sigma30), bracket.getNextTerm().getDaysToExpiry());
                } else {
                    log.warn("[{}] Failed to extract IV from either bracketing expiry", symbol);
                    return null;
                }
                primaryExpiry = bracket.getNearTerm();
            } else {
                // Single expiry (exact 30 DTE or only one side available)
                ExpirationDateKey singleExpiry = bracket.getNearTerm() != null
                        ? bracket.getNearTerm() : bracket.getNextTerm();
                sigma30 = extractBlendedATMIV(chain, singleExpiry, underlyingPrice, symbol);
                if (sigma30 == null) {
                    log.warn("[{}] Failed to extract IV from single expiry DTE={}",
                            symbol, singleExpiry.getDaysToExpiry());
                    return null;
                }
                log.info("[{}] Single-expiry IV: {}% (DTE={})", symbol,
                        String.format("%.2f", sigma30), singleExpiry.getDaysToExpiry());
                primaryExpiry = singleExpiry;
            }

            // Extract market date from an option quote timestamp
            long quoteTimestamp = getQuoteTimestamp(chain, primaryExpiry, underlyingPrice);
            LocalDate marketDate = getMarketDateFromTimestamp(quoteTimestamp);

            return IVDataPoint.builder()
                    .symbol(symbol)
                    .currentDate(marketDate)
                    .atmPutIV(sigma30)
                    .atmCallIV(sigma30)
                    .dte(TARGET_DTE)
                    .expiryDate(primaryExpiry.getDate())
                    .strike(findATMStrike(chain, primaryExpiry.getDate(), underlyingPrice))
                    .underlyingPrice(underlyingPrice)
                    .noOptions(false)
                    .build();

        } catch (Exception e) {
            log.error("[{}] Error collecting IV data: {}", symbol, e.getMessage(), e);
            return null;
        }
    }

    // ---- Expiry Bracketing ----

    /**
     * Holds the two expiration cycles that bracket 30 DTE.
     */
    @Value
    static class ExpiryBracket {
        ExpirationDateKey nearTerm;  // DTE ≤ 30 (nullable)
        ExpirationDateKey nextTerm;  // DTE > 30 (nullable)
        boolean interpolationNeeded;
    }

    /**
     * Finds two expirations bracketing 30 DTE from the in-memory option chain.
     *
     * <p>Near-term (T₁): largest DTE ≤ 30 with DTE ≥ 7 (relaxes to ≥ 1 if none found).
     * Next-term (T₂): smallest DTE &gt; 30.
     * If an expiration lands on exactly 30 DTE, returns it as a single-expiry bracket.
     *
     * @return ExpiryBracket, or null if no usable expiry exists
     */
    ExpiryBracket findBracketingExpiries(OptionChainResponse chain) {
        List<ExpirationDateKey> allKeys = Stream.of(chain.getCallExpDateMap(), chain.getPutExpDateMap())
                .filter(m -> m != null && !m.isEmpty())
                .flatMap(m -> m.keySet().stream())
                .distinct()
                .filter(k -> k.getDaysToExpiry() > 0)
                .sorted(Comparator.comparingInt(ExpirationDateKey::getDaysToExpiry))
                .toList();

        if (allKeys.isEmpty()) {
            return null;
        }

        // Check for exact 30 DTE match
        ExpirationDateKey exact30 = allKeys.stream()
                .filter(k -> k.getDaysToExpiry() == TARGET_DTE)
                .findFirst().orElse(null);
        if (exact30 != null) {
            return new ExpiryBracket(exact30, null, false);
        }

        // Find T₁: largest DTE ≤ 30 with DTE ≥ MIN_DTE_THRESHOLD
        ExpirationDateKey nearTerm = allKeys.stream()
                .filter(k -> k.getDaysToExpiry() <= TARGET_DTE && k.getDaysToExpiry() >= MIN_DTE_THRESHOLD)
                .max(Comparator.comparingInt(ExpirationDateKey::getDaysToExpiry))
                .orElse(null);

        // Relax threshold if no expiry in [7, 30]
        if (nearTerm == null) {
            nearTerm = allKeys.stream()
                    .filter(k -> k.getDaysToExpiry() <= TARGET_DTE && k.getDaysToExpiry() >= 1)
                    .max(Comparator.comparingInt(ExpirationDateKey::getDaysToExpiry))
                    .orElse(null);
        }

        // Find T₂: smallest DTE > 30
        ExpirationDateKey nextTerm = allKeys.stream()
                .filter(k -> k.getDaysToExpiry() > TARGET_DTE)
                .min(Comparator.comparingInt(ExpirationDateKey::getDaysToExpiry))
                .orElse(null);

        if (nearTerm != null && nextTerm != null) {
            return new ExpiryBracket(nearTerm, nextTerm, true);
        } else if (nearTerm != null) {
            return new ExpiryBracket(nearTerm, null, false);
        } else if (nextTerm != null) {
            return new ExpiryBracket(null, nextTerm, false);
        }
        return null;
    }

    // ---- Blended ATM IV Extraction ----

    /**
     * Extracts the blended Call+Put ATM IV for a single expiration cycle.
     *
     * <p>Finds the ATM strike, validates quote liquidity (bid &gt; 0, ask ≥ bid,
     * volatility &gt; 0), and blends Call and Put IVs. Falls back to one side if
     * the other fails liquidity checks.
     *
     * @return blended IV as percentage (e.g. 32.45), or null if extraction fails
     */
    Double extractBlendedATMIV(OptionChainResponse chain, ExpirationDateKey expiryKey,
                                       double underlyingPrice, String symbol) {
        String expiryDate = expiryKey.getDate();

        Double atmStrike = findATMStrike(chain, expiryDate, underlyingPrice);
        if (atmStrike == null) {
            log.debug("[{}] No ATM strike found for expiry {} (DTE={})",
                    symbol, expiryDate, expiryKey.getDaysToExpiry());
            return null;
        }

        OptionChainResponse.OptionData call = findOption(chain, OptionType.CALL, expiryDate, atmStrike);
        OptionChainResponse.OptionData put = findOption(chain, OptionType.PUT, expiryDate, atmStrike);

        Double callIV = extractValidatedIV(call);
        Double putIV = extractValidatedIV(put);

        if (callIV != null && putIV != null) {
            return (callIV + putIV) / 2.0;
        } else if (callIV != null) {
            log.debug("[{}] PUT failed liquidity check at strike {} DTE={}, using CALL IV only",
                    symbol, atmStrike, expiryKey.getDaysToExpiry());
            return callIV;
        } else if (putIV != null) {
            log.debug("[{}] CALL failed liquidity check at strike {} DTE={}, using PUT IV only",
                    symbol, atmStrike, expiryKey.getDaysToExpiry());
            return putIV;
        }

        log.debug("[{}] Both PUT and CALL failed liquidity check at strike {} DTE={}",
                symbol, atmStrike, expiryKey.getDaysToExpiry());
        return null;
    }

    /**
     * Extracts IV from an option contract after validating quote liquidity.
     *
     * <p>Requires bid &gt; 0, ask ≥ bid, and volatility &gt; 0 to filter stale or
     * distorted quotes.
     *
     * @return IV as percentage, or null if the quote fails validation
     */
    private Double extractValidatedIV(OptionChainResponse.OptionData option) {
        if (option == null) {
            return null;
        }
        if (option.getBid() <= 0 || option.getAsk() < option.getBid()) {
            return null;
        }
        return extractIV(option);
    }

    // ---- Variance Interpolation ----

    /**
     * Applies constant-maturity total variance interpolation to produce a synthetic 30-day IV.
     *
     * <p>Uses the CBOE VIX-style formula where total variance (σ² × DTE) is additive:
     * <pre>
     * w₁ = (DTE₂ - 30) / (DTE₂ - DTE₁)
     * w₂ = (30 - DTE₁) / (DTE₂ - DTE₁)
     * σ₃₀ = sqrt( (σ₁² × DTE₁ × w₁ + σ₂² × DTE₂ × w₂) / 30 )
     * </pre>
     *
     * @param sigma1 blended ATM IV for near-term expiration (percentage)
     * @param dte1   near-term DTE
     * @param sigma2 blended ATM IV for next-term expiration (percentage)
     * @param dte2   next-term DTE
     * @return interpolated 30-day IV as percentage, or null on edge-case failure
     */
    static Double interpolate30DayIV(double sigma1, int dte1, double sigma2, int dte2) {
        if (dte1 == dte2) {
            return sigma1;
        }
        if (dte1 == TARGET_DTE) {
            return sigma1;
        }
        if (dte2 == TARGET_DTE) {
            return sigma2;
        }

        double dteDiff = dte2 - dte1;
        double w1 = (dte2 - TARGET_DTE) / dteDiff;
        double w2 = (TARGET_DTE - dte1) / dteDiff;

        double totalVariance = (sigma1 * sigma1 * dte1 * w1) + (sigma2 * sigma2 * dte2 * w2);
        double sigma30Squared = totalVariance / TARGET_DTE;

        if (sigma30Squared <= 0 || Double.isNaN(sigma30Squared)) {
            return null;
        }

        return Math.sqrt(sigma30Squared);
    }

    // ---- Existing helpers (preserved) ----

    /**
     * Finds the strike price closest to the underlying price (ATM) for a given expiry.
     */
    private Double findATMStrike(OptionChainResponse chain, String expiryDate, double underlyingPrice) {
        Map<String, List<OptionChainResponse.OptionData>> callMap = chain
                .getOptionDataForASpecificExpiryDate(OptionType.CALL, expiryDate);

        if (MapUtils.isEmpty(callMap)) {
            // Try PUT map as fallback (some expiries may only have PUTs in edge cases)
            Map<String, List<OptionChainResponse.OptionData>> putMap = chain
                    .getOptionDataForASpecificExpiryDate(OptionType.PUT, expiryDate);
            if (MapUtils.isEmpty(putMap)) {
                return null;
            }
            return findClosestStrike(putMap, underlyingPrice);
        }

        return findClosestStrike(callMap, underlyingPrice);
    }

    private Double findClosestStrike(Map<String, List<OptionChainResponse.OptionData>> optionMap,
                                     double underlyingPrice) {
        return optionMap.values().stream()
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
    private OptionChainResponse.OptionData findOption(
            OptionChainResponse chain,
            OptionType optionType,
            String expiryDate,
            double strike) {

        Map<String, List<OptionChainResponse.OptionData>> optionMap = chain
                .getOptionDataForASpecificExpiryDate(optionType, expiryDate);

        if (MapUtils.isEmpty(optionMap)) {
            return null;
        }

        return optionMap.values().stream()
                .flatMap(List::stream)
                .filter(opt -> Math.abs(opt.getStrikePrice() - strike) < 0.01)
                .findFirst()
                .orElse(null);
    }

    /**
     * Extracts Implied Volatility from option data with decimal/percentage normalization.
     *
     * @param option Option data
     * @return IV as percentage (e.g., 45.5 for 45.5%), or null if not available
     */
    private Double extractIV(OptionChainResponse.OptionData option) {
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
     * Gets a quote timestamp from an option at the primary expiry for market date extraction.
     */
    private long getQuoteTimestamp(OptionChainResponse chain, ExpirationDateKey expiryKey,
                                   double underlyingPrice) {
        Double strike = findATMStrike(chain, expiryKey.getDate(), underlyingPrice);
        if (strike != null) {
            OptionChainResponse.OptionData opt = findOption(chain, OptionType.PUT,
                    expiryKey.getDate(), strike);
            if (opt != null && opt.getQuoteTimeInLong() > 0) {
                return opt.getQuoteTimeInLong();
            }
            opt = findOption(chain, OptionType.CALL, expiryKey.getDate(), strike);
            if (opt != null && opt.getQuoteTimeInLong() > 0) {
                return opt.getQuoteTimeInLong();
            }
        }
        return 0;
    }

    /**
     * Converts epoch timestamp (milliseconds) to LocalDate.
     * This gives us the actual market date when the data was last updated,
     * not the current date when the collection runs.
     *
     * @param timestampMillis Epoch timestamp in milliseconds
     * @return LocalDate representing the market date
     */
    private LocalDate getMarketDateFromTimestamp(long timestampMillis) {
        if (timestampMillis <= 0) {
            return LocalDate.now();
        }

        return java.time.Instant.ofEpochMilli(timestampMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();
    }
}
