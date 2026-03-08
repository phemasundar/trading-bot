package com.hemasundar.options.strategies;

import com.hemasundar.apis.FinnHubAPIs;
import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.cache.PriceHistoryCache;
import com.hemasundar.options.models.OptionChainResponse;
import com.hemasundar.options.models.OptionsStrategyFilter;
import com.hemasundar.options.models.TradeSetup;
import com.hemasundar.options.strategies.AbstractTradingStrategy;
import com.hemasundar.options.strategies.StrategyType;
import com.hemasundar.pojos.EarningsCalendarResponse;
import com.hemasundar.pojos.PriceHistoryResponse;
import com.hemasundar.utils.VolatilityCalculator;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.mockito.MockedStatic;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.testng.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class AbstractTradingStrategyTest {

    private DummyStrategy strategy;
    private MockedStatic<FinnHubAPIs> mockedFinnHub;
    private MockedStatic<ThinkOrSwinAPIs> mockedThinkOrSwim;
    private MockedStatic<VolatilityCalculator> mockedVolCalc;

    // A concrete subclass strictly for testing AbstractTradingStrategy logic
    private static class DummyStrategy extends AbstractTradingStrategy {

        public DummyStrategy() {
            super(StrategyType.PUT_CREDIT_SPREAD); // Use any valid type
        }

        @Override
        protected List<TradeSetup> findValidTrades(OptionChainResponse chain, String expiryDate,
                OptionsStrategyFilter filter) {
            // Simulated dummy trade for successful paths
            return Collections.singletonList(new DummyTrade());
        }

        // Public wrappers to access protected filter methods
        public <T> Predicate<T> callCommonMaxLossFilter(OptionsStrategyFilter filter, Function<T, Double> extractor) {
            return commonMaxLossFilter(filter, extractor);
        }

        public <T> Predicate<T> callCommonMinReturnOnRiskFilter(OptionsStrategyFilter filter, Function<T, Double> pExt,
                Function<T, Double> lExt) {
            return commonMinReturnOnRiskFilter(filter, pExt, lExt);
        }

        public Predicate<TradeSetup> callCommonMaxNetExtrinsicValueToPricePercentageFilter(
                OptionsStrategyFilter filter) {
            return commonMaxNetExtrinsicValueToPricePercentageFilter(filter);
        }

        public Predicate<TradeSetup> callCommonMinNetExtrinsicValueToPricePercentageFilter(
                OptionsStrategyFilter filter) {
            return commonMinNetExtrinsicValueToPricePercentageFilter(filter);
        }

        public <T> Predicate<T> callCommonMaxTotalDebitFilter(OptionsStrategyFilter filter,
                Function<T, Double> extractor) {
            return commonMaxTotalDebitFilter(filter, extractor);
        }

        public <T> Predicate<T> callCommonMaxTotalCreditFilter(OptionsStrategyFilter filter,
                Function<T, Double> extractor) {
            return commonMaxTotalCreditFilter(filter, extractor);
        }

        public <T> Predicate<T> callCommonMinTotalCreditFilter(OptionsStrategyFilter filter,
                Function<T, Double> extractor) {
            return commonMinTotalCreditFilter(filter, extractor);
        }
    }

    // Dummy concrete core model
    private static class DummyTrade implements TradeSetup {
        @Override
        public double getNetCredit() {
            return 0;
        }

        @Override
        public double getMaxLoss() {
            return 0;
        }

        @Override
        public double getReturnOnRisk() {
            return 0;
        }

        @Override
        public double getBreakEvenPrice() {
            return 0;
        }

        @Override
        public double getBreakEvenPercentage() {
            return 0;
        }

        @Override
        public String getExpiryDate() {
            return null;
        }

        @Override
        public double getCurrentPrice() {
            return 0;
        }

        @Override
        public int getDaysToExpiration() {
            return 0;
        }

        @Override
        public List<com.hemasundar.options.models.TradeLeg> getLegs() {
            return null;
        }

        @Override
        public double getNetExtrinsicValue() {
            return 0;
        }
    }

    @BeforeMethod
    public void setup() {
        strategy = new DummyStrategy();
        mockedFinnHub = mockStatic(FinnHubAPIs.class);
        mockedThinkOrSwim = mockStatic(ThinkOrSwinAPIs.class);
        mockedVolCalc = mockStatic(VolatilityCalculator.class);
        PriceHistoryCache.getInstance().clear();
    }

    @AfterMethod
    public void teardown() {
        mockedFinnHub.close();
        mockedThinkOrSwim.close();
        mockedVolCalc.close();
    }

    @Test
    public void testGetStrategyName() {
        assertEquals(StrategyType.PUT_CREDIT_SPREAD.getDisplayName(), strategy.getStrategyName());
    }

    @Test
    public void testCommonMaxLossFilter() {
        OptionsStrategyFilter filter = new OptionsStrategyFilter();
        filter.setMaxLossLimit(100.0);

        Predicate<String> predicate = strategy.callCommonMaxLossFilter(filter, str -> Double.parseDouble(str));

        assertTrue(predicate.test("50.0"));
        assertTrue(predicate.test("100.0"));
        assertFalse(predicate.test("150.0"));
    }

    @Test
    public void testCommonMinReturnOnRiskFilter() {
        OptionsStrategyFilter filter = new OptionsStrategyFilter();
        filter.setMinReturnOnRisk(10); // 10% min return

        // Return on Risk = profit / maxLoss
        Predicate<Double[]> predicate = strategy.callCommonMinReturnOnRiskFilter(
                filter,
                arr -> arr[0], // profit
                arr -> arr[1] // maxLoss
        );

        assertTrue(predicate.test(new Double[] { 15.0, 100.0 })); // 15% > 10%
        assertTrue(predicate.test(new Double[] { 10.0, 100.0 })); // 10% == 10%
        assertFalse(predicate.test(new Double[] { 5.0, 100.0 })); // 5% < 10%
    }

    @Test
    public void testCommonMaxTotalDebitFilter() {
        OptionsStrategyFilter filter = new OptionsStrategyFilter();
        filter.setMaxTotalDebit(50.0);
        Predicate<Double> predicate = strategy.callCommonMaxTotalDebitFilter(filter, val -> val);
        assertTrue(predicate.test(20.0));
        assertFalse(predicate.test(60.0));
    }

    @Test
    public void testCommonMaxTotalCreditFilter() {
        OptionsStrategyFilter filter = new OptionsStrategyFilter();
        filter.setMaxTotalCredit(50.0);
        Predicate<Double> predicate = strategy.callCommonMaxTotalCreditFilter(filter, val -> val);
        assertTrue(predicate.test(20.0));
        assertFalse(predicate.test(60.0));
    }

    @Test
    public void testCommonMinTotalCreditFilter() {
        OptionsStrategyFilter filter = new OptionsStrategyFilter();
        filter.setMinTotalCredit(50.0);
        Predicate<Double> predicate = strategy.callCommonMinTotalCreditFilter(filter, val -> val);
        assertFalse(predicate.test(20.0));
        assertTrue(predicate.test(60.0));
    }

    @Test
    public void testCommonMaxNetExtrinsicValueToPricePercentageFilter() {
        OptionsStrategyFilter filter = new OptionsStrategyFilter();
        filter.setMaxNetExtrinsicValueToPricePercentage(5.0);

        Predicate<TradeSetup> predicate = strategy.callCommonMaxNetExtrinsicValueToPricePercentageFilter(filter);

        TradeSetup t1 = mock(TradeSetup.class);
        when(t1.getAnulizedNetExtrinsicValueToCapitalPercentage()).thenReturn(4.0);
        assertTrue(predicate.test(t1));

        TradeSetup t2 = mock(TradeSetup.class);
        when(t2.getAnulizedNetExtrinsicValueToCapitalPercentage()).thenReturn(6.0);
        assertFalse(predicate.test(t2));
    }

    @Test
    public void testCommonMinNetExtrinsicValueToPricePercentageFilter() {
        OptionsStrategyFilter filter = new OptionsStrategyFilter();
        filter.setMinNetExtrinsicValueToPricePercentage(5.0);

        Predicate<TradeSetup> predicate = strategy.callCommonMinNetExtrinsicValueToPricePercentageFilter(filter);

        TradeSetup t1 = mock(TradeSetup.class);
        when(t1.getAnulizedNetExtrinsicValueToCapitalPercentage()).thenReturn(6.0);
        assertTrue(predicate.test(t1));

        TradeSetup t2 = mock(TradeSetup.class);
        when(t2.getAnulizedNetExtrinsicValueToCapitalPercentage()).thenReturn(4.0);
        assertFalse(predicate.test(t2));
    }

    @Test
    public void testFindTrades_NoVolatilityThreshold() {
        OptionChainResponse chain = mock(OptionChainResponse.class);
        when(chain.getSymbol()).thenReturn("AAPL");
        when(chain.getExpiryDatesInRange(anyInt(), anyInt(), anyInt())).thenReturn(Arrays.asList("2024-01-01"));

        OptionsStrategyFilter filter = new OptionsStrategyFilter();
        // minHistoricalVolatility is null by default

        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertEquals(1, trades.size());
    }

    @Test
    public void testFindTrades_FailsHistoricalVolatility() {
        OptionChainResponse chain = mock(OptionChainResponse.class);
        when(chain.getSymbol()).thenReturn("AAPL");

        // Set Volatility to fail
        PriceHistoryResponse priceHistory = new PriceHistoryResponse();
        mockedThinkOrSwim.when(() -> ThinkOrSwinAPIs.getYearlyPriceHistory("AAPL", 1)).thenReturn(priceHistory);
        mockedVolCalc.when(() -> VolatilityCalculator.calculateAnnualizedVolatility(priceHistory)).thenReturn(15.0);

        OptionsStrategyFilter filter = new OptionsStrategyFilter();
        filter.setMinHistoricalVolatility(20.0); // 15.0 < 20.0

        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertTrue(trades.isEmpty());
    }

    @Test
    public void testFindTrades_PassesHistoricalVolatilityAndCaches() {
        OptionChainResponse chain = mock(OptionChainResponse.class);
        when(chain.getSymbol()).thenReturn("AAPL");
        when(chain.getExpiryDatesInRange(anyInt(), anyInt(), anyInt())).thenReturn(Arrays.asList("2024-01-01"));

        // Setup API to return data
        PriceHistoryResponse priceHistory = new PriceHistoryResponse();
        mockedThinkOrSwim.when(() -> ThinkOrSwinAPIs.getYearlyPriceHistory("AAPL", 1)).thenReturn(priceHistory);
        mockedVolCalc.when(() -> VolatilityCalculator.calculateAnnualizedVolatility(priceHistory)).thenReturn(25.0);

        OptionsStrategyFilter filter = new OptionsStrategyFilter();
        filter.setMinHistoricalVolatility(20.0); // 25.0 > 20.0

        // Call #1 (cache miss)
        List<TradeSetup> trades1 = strategy.findTrades(chain, filter);
        assertEquals(1, trades1.size());
        mockedThinkOrSwim.verify(() -> ThinkOrSwinAPIs.getYearlyPriceHistory("AAPL", 1), times(1));

        // Call #2 (cache hit)
        List<TradeSetup> trades2 = strategy.findTrades(chain, filter);
        assertEquals(1, trades2.size());
        mockedThinkOrSwim.verify(() -> ThinkOrSwinAPIs.getYearlyPriceHistory("AAPL", 1), times(1)); // Should not
                                                                                                    // increase
    }

    @Test
    public void testFindTrades_VolatilityCalculationException_FailsOpen() {
        OptionChainResponse chain = mock(OptionChainResponse.class);
        when(chain.getSymbol()).thenReturn("AAPL");
        when(chain.getExpiryDatesInRange(anyInt(), anyInt(), anyInt())).thenReturn(Arrays.asList("2024-01-01"));

        // Throws exception during API call
        mockedThinkOrSwim.when(() -> ThinkOrSwinAPIs.getYearlyPriceHistory("AAPL", 1))
                .thenThrow(new RuntimeException("API Error"));

        OptionsStrategyFilter filter = new OptionsStrategyFilter();
        filter.setMinHistoricalVolatility(20.0);

        // Should return true (Fail-Open)
        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertEquals(1, trades.size());
    }

    @Test
    public void testFindTrades_NoExpiryDates() {
        OptionChainResponse chain = mock(OptionChainResponse.class);
        when(chain.getSymbol()).thenReturn("AAPL");
        when(chain.getExpiryDatesInRange(anyInt(), anyInt(), anyInt())).thenReturn(Collections.emptyList());

        OptionsStrategyFilter filter = new OptionsStrategyFilter();

        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertTrue(trades.isEmpty());
    }

    @Test
    public void testFindTrades_IgnoresEarnings() throws Exception {
        OptionChainResponse chain = mock(OptionChainResponse.class);
        when(chain.getSymbol()).thenReturn("AAPL");
        when(chain.getExpiryDatesInRange(anyInt(), anyInt(), anyInt())).thenReturn(Arrays.asList("2024-01-01"));

        OptionsStrategyFilter filter = new OptionsStrategyFilter();
        filter.setIgnoreEarnings(true); // Ignore Earnings

        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertEquals(1, trades.size());
        mockedFinnHub.verifyNoInteractions(); // Should not even call FinnHub
    }

    @Test
    public void testFindTrades_EarningsFound_SkipsExpiry() throws Exception {
        OptionChainResponse chain = mock(OptionChainResponse.class);
        when(chain.getSymbol()).thenReturn("AAPL");
        when(chain.getExpiryDatesInRange(anyInt(), anyInt(), anyInt())).thenReturn(Arrays.asList("2024-01-01"));

        EarningsCalendarResponse earningsResponse = new EarningsCalendarResponse();
        EarningsCalendarResponse.EarningCalendar entry = new EarningsCalendarResponse.EarningCalendar();
        entry.setDate(LocalDate.of(2024, 1, 1));
        earningsResponse.setEarningsCalendar(Arrays.asList(entry));

        mockedFinnHub.when(() -> FinnHubAPIs.getEarningsByTicker(eq("AAPL"), any(LocalDate.class)))
                .thenReturn(earningsResponse);

        OptionsStrategyFilter filter = new OptionsStrategyFilter();
        filter.setIgnoreEarnings(false);

        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertTrue(trades.isEmpty()); // Should skip this expiry
    }

    @Test
    public void testFindTrades_EarningsError_AllowsExpiry() throws Exception {
        OptionChainResponse chain = mock(OptionChainResponse.class);
        when(chain.getSymbol()).thenReturn("AAPL");
        when(chain.getExpiryDatesInRange(anyInt(), anyInt(), anyInt())).thenReturn(Arrays.asList("2024-01-01"));

        mockedFinnHub.when(() -> FinnHubAPIs.getEarningsByTicker(eq("AAPL"), any(LocalDate.class)))
                .thenThrow(new RuntimeException("FinnHub Error"));

        OptionsStrategyFilter filter = new OptionsStrategyFilter();
        filter.setIgnoreEarnings(false);

        List<TradeSetup> trades = strategy.findTrades(chain, filter);
        assertEquals(1, trades.size()); // Exception should be caught and expiry processed
    }

}
