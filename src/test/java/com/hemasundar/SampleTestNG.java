package com.hemasundar;

import com.hemasundar.pojos.RefreshToken;
import com.hemasundar.pojos.Securities;
import com.hemasundar.pojos.TestConfig;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsConfig;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.config.RuntimeConfig;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

                // =============================================================
                // TECHNICAL INDICATORS SETUP (used by some strategies)
                // =============================================================

                // Define WHAT indicators to use (with their settings)
                TechnicalIndicators indicators = TechnicalIndicators.builder()
                                .rsiFilter(RSIFilter.builder()
                                                .period(14)
                                                .oversoldThreshold(30.0)
                                                .overboughtThreshold(70.0)
                                                .build())
                                .bollingerFilter(BollingerBandsFilter.builder()
                                                .period(20)
                                                .standardDeviations(2.0)
                                                .build())
                                .ma20Filter(MovingAverageFilter.builder().period(20).build())
                                .ma50Filter(MovingAverageFilter.builder().period(50).build())
                                .ma100Filter(MovingAverageFilter.builder().period(100).build())
                                .ma200Filter(MovingAverageFilter.builder().period(200).build())
                                .volumeFilter(VolumeFilter.builder().build())
                                .build();

                // Define technical filter conditions
                FilterConditions oversoldConditions = FilterConditions.builder()
                                .rsiCondition(RSICondition.BULLISH_CROSSOVER)
                                .bollingerCondition(BollingerCondition.LOWER_BAND)
                                .minVolume(1_000_000L)
                                .build();

                FilterConditions overboughtConditions = FilterConditions.builder()
                                .rsiCondition(RSICondition.BEARISH_CROSSOVER)
                                .bollingerCondition(BollingerCondition.UPPER_BAND)
                                .minVolume(1_000_000L)
                                .build();

                // Combine indicators + conditions into filter chains
                TechnicalFilterChain oversoldFilterChain = TechnicalFilterChain.of(indicators, oversoldConditions);
                TechnicalFilterChain overboughtFilterChain = TechnicalFilterChain.of(indicators, overboughtConditions);

                // =============================================================
                // OPTIONS STRATEGIES (Unified Configuration-Driven List)
                // =============================================================
                // Load securities lists first (used by different strategies)
                List<String> portfolioSecurities = loadSecurities(FilePaths.portfolioSecurities);
                List<String> top100Securities = loadSecurities(FilePaths.top100Securities);
                List<String> bullishSecurities = loadSecurities(FilePaths.bullishSecurities);

                OptionsStrategyFilter rsiBBFilter = OptionsStrategyFilter.builder()
                                .targetDTE(30)
                                .maxDelta(0.3)
                                .maxLossLimit(1000)
                                .minReturnOnRisk(12)
                                .ignoreEarnings(false)
                                .build();

                // ALL options strategies in one unified list
                List<OptionsConfig> optionsStrategies = List.of(
                                // Basic strategies (no technical filter)
                                OptionsConfig.builder()
                                                .strategy(new PutCreditSpreadStrategy())
                                                .filter(OptionsStrategyFilter.builder()
                                                                .targetDTE(30)
                                                                .maxDelta(0.20)
                                                                .maxLossLimit(1000)
                                                                .minReturnOnRisk(12)
                                                                .ignoreEarnings(false)
                                                                .build())
                                                .securities(portfolioSecurities)
                                                .build(),
                                OptionsConfig.builder()
                                                .strategy(new CallCreditSpreadStrategy())
                                                .filter(OptionsStrategyFilter.builder()
                                                                .targetDTE(30)
                                                                .maxDelta(0.20)
                                                                .maxLossLimit(1000)
                                                                .minReturnOnRisk(12)
                                                                .ignoreEarnings(false)
                                                                .build())
                                                .securities(portfolioSecurities)
                                                .build(),
                                OptionsConfig.builder()
                                                .strategy(new IronCondorStrategy())
                                                .filter(OptionsStrategyFilter.builder()
                                                                .targetDTE(60)
                                                                .maxDelta(0.15)
                                                                .maxLossLimit(1000)
                                                                .minReturnOnRisk(24)
                                                                .ignoreEarnings(false)
                                                                .build())
                                                .securities(portfolioSecurities)
                                                .build(),
                                OptionsConfig.builder()
                                                .strategy(new LongCallLeapStrategy())
                                                .filter(OptionsStrategyFilter.builder()
                                                                .minDTE((int) ChronoUnit.DAYS.between(LocalDate.now(),
                                                                                LocalDate.now().plusMonths(11)))
                                                                .minDelta(0.6)
                                                                .marginInterestRate(6.0)
                                                                .maxOptionPricePercent(40.0)
                                                                .build())
                                                .securities(portfolioSecurities)
                                                .build(),
                                // Bullish Long Put Credit Spread (no technical filter)
                                OptionsConfig.builder()
                                                .strategy(new PutCreditSpreadStrategy(
                                                                StrategyType.BULLISH_LONG_PUT_CREDIT_SPREAD))
                                                .filter(OptionsStrategyFilter.builder()
                                                                .targetDTE(180)
                                                                .maxDelta(0.20)
                                                                .maxLossLimit(2000)
                                                                .minReturnOnRisk(24)
                                                                .ignoreEarnings(true)
                                                                .build())
                                                .securities(bullishSecurities)
                                                .build(),
                                // Bullish Long Iron Condor (no technical filter)
                                OptionsConfig.builder()
                                                .strategy(new IronCondorStrategy(
                                                                StrategyType.BULLISH_LONG_IRON_CONDOR))
                                                .filter(OptionsStrategyFilter.builder()
                                                                .targetDTE(180)
                                                                .maxDelta(0.15)
                                                                .maxLossLimit(2000)
                                                                .minReturnOnRisk(36)
                                                                .ignoreEarnings(true)
                                                                .build())
                                                .securities(bullishSecurities)
                                                .build(),
                                // RSI Bollinger strategies (with technical filter)
                                OptionsConfig.builder()
                                                .strategy(new PutCreditSpreadStrategy(
                                                                StrategyType.RSI_BOLLINGER_BULL_PUT_SPREAD))
                                                .filter(rsiBBFilter)
                                                .securities(top100Securities)
                                                .technicalFilterChain(oversoldFilterChain)
                                                .build(),
                                OptionsConfig.builder()
                                                .strategy(new CallCreditSpreadStrategy(
                                                                StrategyType.RSI_BOLLINGER_BEAR_CALL_SPREAD))
                                                .filter(rsiBBFilter)
                                                .securities(top100Securities)
                                                .technicalFilterChain(overboughtFilterChain)
                                                .build(),
                                // Bullish Broken Wing Butterfly Strategy
                                OptionsConfig.builder()
                                                .strategy(new BrokenWingButterflyStrategy())
                                                .filter(OptionsStrategyFilter.builder()
                                                                .targetDTE(45)
                                                                .longCallMaxDelta(0.5) // Max Delta for Leg 1 (Long
                                                                                       // Call)
                                                                .shortCallsMaxDelta(0.2) // Max Delta for Leg 2 (Short
                                                                                         // Calls)
                                                                .maxTotalDebit(100) // Total debit <= $100
                                                                .maxLossLimit(1000) // Max loss limit
                                                                .ignoreEarnings(true)
                                                                .build())
                                                .securities(portfolioSecurities)
                                                .build());

                // Load unified runtime config (strategies + screeners)
                RuntimeConfig runtimeConfig = RuntimeConfig.load(FilePaths.runtimeConfig);

                // Run all options strategies with unified loop
                for (OptionsConfig config : optionsStrategies) {
                        // Check if strategy is enabled in runtime config
                        if (!runtimeConfig.isStrategyEnabled(config.getStrategy().getStrategyType())) {
                                log.info("Skipping disabled strategy: {}", config.getName());
                                continue;
                        }

                        log.info("Running strategy: {}", config.getName());

                        List<String> securitiesToUse = config.getSecurities();

                        // Apply technical filter if present
                        if (config.hasTechnicalFilter()) {
                                List<TechnicalScreener.ScreeningResult> results = TechnicalScreener.screenStocks(
                                                securitiesToUse, config.getTechnicalFilterChain());
                                securitiesToUse = results.stream()
                                                .map(TechnicalScreener.ScreeningResult::getSymbol)
                                                .toList();
                                log.info("[{}] Found {} stocks matching technical criteria: {}",
                                                config.getName(), securitiesToUse.size(), securitiesToUse);
                        }

                        if (!securitiesToUse.isEmpty()) {
                                Map<String, List<TradeSetup>> trades = findTradesForStrategy(cache, securitiesToUse,
                                                config.getStrategy(), config.getFilter());

                                // Send to Telegram (consistent with Technical Screener pattern)
                                if (!trades.isEmpty()) {
                                        TelegramUtils.sendTradeAlerts(config.getName(), trades);
                                }
                        }
                }

                // =============================================================
                // TECHNICAL-ONLY STOCK SCREENERS (Configuration-Driven)
                // =============================================================
                // Define all screeners as a list of configs - easy to add more
                List<ScreenerConfig> technicalScreeners = List.of(
                                ScreenerConfig.builder()
                                                .screenerType(ScreenerType.RSI_BB_BULLISH_CROSSOVER)
                                                .conditions(FilterConditions.builder()
                                                                .rsiCondition(RSICondition.BULLISH_CROSSOVER)
                                                                .bollingerCondition(BollingerCondition.LOWER_BAND)
                                                                // .requirePriceBelowMA20(true)
                                                                // .requirePriceBelowMA50(true)
                                                                .minVolume(1_000_000L)
                                                                .build())
                                                .build(),
                                ScreenerConfig.builder()
                                                .screenerType(ScreenerType.RSI_BB_BEARISH_CROSSOVER)
                                                .conditions(FilterConditions.builder()
                                                                .rsiCondition(RSICondition.BEARISH_CROSSOVER)
                                                                .bollingerCondition(BollingerCondition.UPPER_BAND)
                                                                .minVolume(1_000_000L)
                                                                .build())
                                                .build(),
                                ScreenerConfig.builder()
                                                .screenerType(ScreenerType.BELOW_200_DAY_MA)
                                                .conditions(FilterConditions.builder()
                                                                .requirePriceBelowMA200(true)
                                                                .minVolume(1_000_000L)
                                                                .build())
                                                .build()
                // Add more screeners here as needed...
                );

                // Run all screeners with single loop
                for (ScreenerConfig config : technicalScreeners) {
                        // Check if screener is enabled in runtime config
                        if (!runtimeConfig.isScreenerEnabled(config.getScreenerType())) {
                                log.info("Skipping disabled screener: {}", config.getName());
                                continue;
                        }

                        log.info("Running screener: {}", config.getName());
                        TechnicalFilterChain filterChain = TechnicalFilterChain.of(indicators, config.getConditions());
                        List<TechnicalScreener.ScreeningResult> results = TechnicalScreener.screenStocks(
                                        top100Securities, filterChain);

                        log.info("[{}] Found {} stocks matching criteria", config.getName(), results.size());

                        if (!results.isEmpty()) {
                                log.info("[{}] Matching stocks: {}", config.getName(),
                                                results.stream().map(TechnicalScreener.ScreeningResult::getSymbol)
                                                                .toList());
                                TelegramUtils.sendTechnicalScreenerAlert(config.getName(), results);
                        }
                }

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
         * Finds trades for the given strategy and filter across all symbols.
         * Returns a map of symbol -> trades for further processing (logging, Telegram,
         * etc.).
         */
        private static Map<String, List<TradeSetup>> findTradesForStrategy(OptionChainCache cache, List<String> symbols,
                        AbstractTradingStrategy strategy, OptionsStrategyFilter optionsStrategyFilter) {
                log.info("\n" +
                                "******************************************************************\n" +
                                "************* {} **************\n" +
                                "****************************************************************",
                                strategy.getStrategyName());

                Map<String, List<TradeSetup>> allTrades = new LinkedHashMap<>();

                for (String symbol : symbols) {
                        try {
                                OptionChainResponse optionChainResponse = cache.get(symbol);
                                log.info("Processing symbol: {}", symbol);

                                List<TradeSetup> trades = strategy.findTrades(optionChainResponse,
                                                optionsStrategyFilter);
                                trades.forEach(trade -> log.info("Trade: {}", trade));

                                if (!trades.isEmpty()) {
                                        allTrades.put(symbol, trades);
                                }
                        } catch (Exception e) {
                                log.error("Error processing {}: {}", symbol, e.getMessage());
                        }
                }

                return allTrades;
        }

}
