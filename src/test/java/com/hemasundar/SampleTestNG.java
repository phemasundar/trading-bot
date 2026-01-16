package com.hemasundar;

import com.hemasundar.pojos.RefreshToken;
import com.hemasundar.pojos.Securities;
import com.hemasundar.pojos.TestConfig;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.options.strategies.*;
import com.hemasundar.technical.*;
import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.JavaUtils;
import com.hemasundar.utils.OptionChainCache;
import com.hemasundar.utils.TelegramUtils;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import lombok.extern.log4j.Log4j2;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Scanner;

@Log4j2
public class SampleTestNG {
        public static void main(String[] args) {
                // 1. YOUR CONFIGURATION
                String redirectUri = "https://127.0.0.1";

                // 2. GET THE URL FROM BROWSER
                Scanner scanner = new Scanner(System.in);
                System.out.println(
                                "1. Log in via your browser. \nhttps://api.schwabapi.com/v1/oauth/authorize?client_id="
                                                + TestConfig.getInstance().appKey()
                                                + "&redirect_uri=https://127.0.0.1");
                System.out.println(
                                "2. Copy the FULL URL of the page you are redirected to (the one starting with https://127.0.0.1).");
                System.out.print("Paste the full URL here: ");
                String fullUrl = scanner.nextLine();

                // 3. AUTO-EXTRACT AND CLEAN THE CODE
                String code = fullUrl.split("code=")[1].split("&")[0];
                code = code.replace("%40", "@"); // Vital fix for Schwab's encoding

                System.out.println("Extracted Code: " + code);
                System.out.println("Exchanging... (9-second timer active)");

                // 4. REST-ASSURED REQUEST
                Response response = RestAssured.given()
                                .auth()
                                .preemptive()
                                .basic(TestConfig.getInstance().appKey(), TestConfig.getInstance().ppSecret())
                                .contentType("application/x-www-form-urlencoded")
                                .formParam("grant_type", "authorization_code")
                                .formParam("code", code)
                                .formParam("redirect_uri", redirectUri)
                                .when()
                                .log().all()
                                .post("https://api.schwabapi.com/v1/oauth/token");

                System.out.println("Response Code: " + response.statusCode());
                System.out.println("Response Body: \n" + response.asPrettyString());
                RefreshToken refreshToken = JavaUtils.convertJsonToPojo(response.asString(), RefreshToken.class);
        }

        @Test
        public void getOptionChainData() throws IOException {
                // Shared cache for lazy-loading option chains (minimizes API calls)
                OptionChainCache cache = new OptionChainCache();

                // Strategy Filters
                OptionsStrategyFilter pcsFilter = OptionsStrategyFilter.builder()
                                .targetDTE(30)
                                .maxDelta(0.20)
                                .maxLossLimit(1000)
                                .minReturnOnRisk(12)
                                .ignoreEarnings(false)
                                .build();
                OptionsStrategyFilter ccsFilter = OptionsStrategyFilter.builder()
                                .targetDTE(30)
                                .maxDelta(0.20)
                                .maxLossLimit(1000)
                                .minReturnOnRisk(12)
                                .ignoreEarnings(false)
                                .build();
                OptionsStrategyFilter icFilter = OptionsStrategyFilter.builder()
                                .targetDTE(60)
                                .maxDelta(0.15)
                                .maxLossLimit(1000)
                                .minReturnOnRisk(24)
                                .ignoreEarnings(false)
                                .build();
                OptionsStrategyFilter leapFilter = OptionsStrategyFilter.builder()
                                .minDTE((int) ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.now().plusMonths(11)))
                                .minDelta(0.6)
                                .marginInterestRate(6.0)
                                .maxOptionPricePercent(40.0)
                                .build();

                // Run strategies with default securities file
                List<String> defaultSecurities = loadSecurities(FilePaths.securitiesConfig);
                printFilteredStrategies(cache, defaultSecurities, new PutCreditSpreadStrategy(), pcsFilter);
                printFilteredStrategies(cache, defaultSecurities, new CallCreditSpreadStrategy(), ccsFilter);
                printFilteredStrategies(cache, defaultSecurities, new IronCondorStrategy(), icFilter);
                printFilteredStrategies(cache, defaultSecurities, new LongCallLeapStrategy(), leapFilter);

                // =============================================================
                // RSI & Bollinger Bands Technical Filters
                // =============================================================

                // STEP 1: Define WHAT indicators to use (with their settings)
                TechnicalIndicators indicators = TechnicalIndicators.builder()
                                .rsiFilter(RSIFilter.builder()
                                                .period(14)
                                                .oversoldThreshold(30.0) // RSI < 30 = Oversold
                                                .overboughtThreshold(70.0) // RSI > 70 = Overbought
                                                .build())
                                .bollingerFilter(BollingerBandsFilter.builder()
                                                .period(20)
                                                .standardDeviations(2.0)
                                                .build())
                                .volumeFilter(VolumeFilter.builder().build()) // Volume indicator is used
                                .build();

                // STEP 2: Define WHAT CONDITIONS to look for (separate from indicators)
                // For Bull Put Spread - looking for OVERSOLD signals
                FilterConditions oversoldConditions = FilterConditions.builder()
                                .rsiCondition(RSICondition.OVERSOLD) // RSI < 30
                                .bollingerCondition(BollingerCondition.LOWER_BAND) // Price at lower band
                                .minVolume(1_000_000L) // Minimum 1M shares
                                .build();

                // For Bear Call Spread - looking for OVERBOUGHT signals
                FilterConditions overboughtConditions = FilterConditions.builder()
                                .rsiCondition(RSICondition.OVERBOUGHT) // RSI > 70
                                .bollingerCondition(BollingerCondition.UPPER_BAND) // Price at upper band
                                .minVolume(1_000_000L) // Minimum 1M shares
                                .build();

                // STEP 3: Combine indicators + conditions into filter chains
                TechnicalFilterChain oversoldFilterChain = TechnicalFilterChain.of(indicators, oversoldConditions);
                TechnicalFilterChain overboughtFilterChain = TechnicalFilterChain.of(indicators, overboughtConditions);

                OptionsStrategyFilter rsiBBFilter = OptionsStrategyFilter.builder()
                                .targetDTE(30)
                                .maxDelta(0.35)
                                .maxLossLimit(1000)
                                .minReturnOnRisk(12)
                                .ignoreEarnings(false)
                                .build();

                // Run RSI Bollinger strategies with their own securities file
                List<String> top100Securities = loadSecurities(FilePaths.top100Config);

                // 1. OVERSOLD Checks (Bull Put Spread)
                log.info("Filtering stocks for OVERSOLD conditions...");
                List<String> oversoldStocks = top100Securities.stream()
                                .filter(symbol -> TechnicalStockValidator.validate(symbol, oversoldFilterChain))
                                .toList();
                log.info("Found {} stocks meeting OVERSOLD conditions: {}", oversoldStocks.size(), oversoldStocks);

                if (!oversoldStocks.isEmpty()) {
                        printFilteredStrategies(cache, oversoldStocks,
                                        new PutCreditSpreadStrategy(StrategyType.RSI_BOLLINGER_BULL_PUT_SPREAD),
                                        rsiBBFilter);
                }

                // 2. OVERBOUGHT Checks (Bear Call Spread)
                log.info("Filtering stocks for OVERBOUGHT conditions...");
                List<String> overboughtStocks = top100Securities.stream()
                                .filter(symbol -> TechnicalStockValidator.validate(symbol, overboughtFilterChain))
                                .toList();
                log.info("Found {} stocks meeting OVERBOUGHT conditions: {}", overboughtStocks.size(),
                                overboughtStocks);

                if (!overboughtStocks.isEmpty()) {
                        printFilteredStrategies(cache, overboughtStocks,
                                        new CallCreditSpreadStrategy(StrategyType.RSI_BOLLINGER_BEAR_CALL_SPREAD),
                                        rsiBBFilter);
                }

                technicalScreening(top100Securities);

                // Print cache statistics
                cache.printStats();
        }

        /**
         * Loads securities list from a YAML file.
         */
        private List<String> loadSecurities(Path path) throws IOException {
                Securities securities = JavaUtils.convertYamlToPojo(Files.readString(path), Securities.class);
                log.info("Loading securities from: {} - Found {} symbols", path, securities.securities().size());
                return securities.securities();
        }

        /**
         * Runs strategy against securities loaded from cache (lazy-loading).
         */
        private static void printFilteredStrategies(OptionChainCache cache, List<String> symbols,
                        AbstractTradingStrategy strategy, OptionsStrategyFilter optionsStrategyFilter) {
                log.info("\n" +
                                "******************************************************************\n" +
                                "************* {} **************\n" +
                                "****************************************************************",
                                strategy.getStrategyName());

                for (String symbol : symbols) {
                        try {
                                OptionChainResponse optionChainResponse = cache.get(symbol);
                                log.info("Processing symbol: {}", symbol);

                                List<TradeSetup> trades = strategy.findTrades(optionChainResponse,
                                                optionsStrategyFilter);
                                trades.forEach(trade -> log.info("Trade: {}", trade));

                                // Send to Telegram
                                if (!trades.isEmpty()) {
                                        TelegramUtils.sendTradeAlerts(strategy.getStrategyName(), symbol, trades);
                                }
                        } catch (Exception e) {
                                log.error("Error processing {}: {}", symbol, e.getMessage());
                        }
                }
        }

        /**
         * Technical-Only Stock Screening Strategy.
         * Filters stocks based on:
         * - RSI < 30 (Oversold / Bullish Divergence signal)
         * - Price touching lower Bollinger Band
         * - Price below 20-day Moving Average
         * - Price below 50-day Moving Average
         * 
         * Prints all technical parameter values for matching stocks.
         */
        public static void technicalScreening(List<String> securities) throws IOException {
                log.info("\n" +
                                "╔═══════════════════════════════════════════════════════════════════╗\n" +
                                "║          TECHNICAL-ONLY STOCK SCREENING STRATEGY                  ║\n" +
                                "║   RSI Oversold + Lower BB + Price < MA20 + Price < MA50           ║\n" +
                                "╚═══════════════════════════════════════════════════════════════════╝");

                // Configure all technical indicators
                TechnicalIndicators indicators = TechnicalIndicators.builder()
                                .rsiFilter(RSIFilter.builder()
                                                .period(14)
                                                .oversoldThreshold(30.0) // RSI < 30 = Oversold (Bullish Divergence
                                                                         // signal)
                                                .overboughtThreshold(70.0)
                                                .build())
                                .bollingerFilter(BollingerBandsFilter.builder()
                                                .period(20)
                                                .standardDeviations(2.0)
                                                .build())
                                .ma20Filter(MovingAverageFilter.builder()
                                                .period(20)
                                                .build())
                                .ma50Filter(MovingAverageFilter.builder()
                                                .period(50)
                                                .build())
                                .build();

                // Run the screening - prints all matching stocks with their indicator values
                List<TechnicalScreener.ScreeningResult> results = TechnicalScreener.screenStocks(securities,
                                indicators);

                // Summary
                log.info("\n" +
                                "══════════════════════════════════════════════════════════════════════\n" +
                                " SCREENING COMPLETE: Found {} stocks matching ALL criteria\n" +
                                "══════════════════════════════════════════════════════════════════════",
                                results.size());

                if (!results.isEmpty()) {
                        log.info("Matching stocks: {}",
                                        results.stream().map(TechnicalScreener.ScreeningResult::getSymbol).toList());
                }
        }

}
