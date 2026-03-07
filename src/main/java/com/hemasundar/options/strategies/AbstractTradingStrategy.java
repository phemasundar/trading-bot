package com.hemasundar.options.strategies;

import com.hemasundar.options.config.OptionsStrategyFilter;
import com.hemasundar.options.model.TradeSetup;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Abstract base class for all trading strategies.
 * Provides common filter implementations.
 */
public abstract class AbstractTradingStrategy<T extends TradeSetup, F extends OptionsStrategyFilter> {

    /**
     * Common filter for maximum loss.
     */
    protected Predicate<T> commonMaxLossFilter(F filter, Function<T, Double> maxLossExtractor) {
        return candidate -> {
            if (filter.getMaxLossLimit() == null) return true;
            return maxLossExtractor.apply(candidate) <= filter.getMaxLossLimit();
        };
    }

    /**
     * Common filter for minimum return on risk.
     */
    protected Predicate<T> commonMinReturnOnRiskFilter(F filter, Function<T, Double> profitExtractor, Function<T, Double> maxLossExtractor) {
        return candidate -> {
            if (filter.getMinReturnOnRisk() == null) return true;
            double profit = profitExtractor.apply(candidate);
            double maxLoss = maxLossExtractor.apply(candidate);
            if (maxLoss <= 0) return true;
            return (profit / maxLoss) * 100 >= filter.getMinReturnOnRisk();
        };
    }

    /**
     * Common filter for maximum total debit.
     */
    protected Predicate<T> commonMaxTotalDebitFilter(F filter, Function<T, Double> debitExtractor) {
        return candidate -> {
            if (filter.getMaxTotalDebit() == null) return true;
            return debitExtractor.apply(candidate) <= filter.getMaxTotalDebit();
        };
    }

    /**
     * Common filter for maximum total credit.
     */
    protected Predicate<T> commonMaxTotalCreditFilter(F filter, Function<T, Double> creditExtractor) {
        return candidate -> {
            if (filter.getMaxTotalCredit() == null) return true;
            return creditExtractor.apply(candidate) <= filter.getMaxTotalCredit();
        };
    }

    /**
     * Common filter for minimum total credit.
     */
    protected Predicate<T> commonMinTotalCreditFilter(F filter, Function<T, Double> creditExtractor) {
        return candidate -> {
            if (filter.getMinTotalCredit() == null) return true;
            return creditExtractor.apply(candidate) >= filter.getMinTotalCredit();
        };
    }

    /**
     * Common filter for maximum net extrinsic value to price percentage.
     */
    protected Predicate<T> commonMaxNetExtrinsicValueToPricePercentageFilter(F filter, Function<T, Double> percentageExtractor) {
        return candidate -> {
            if (filter.getMaxNetExtrinsicValueToPricePercentage() == null) return true;
            return percentageExtractor.apply(candidate) <= filter.getMaxNetExtrinsicValueToPricePercentage();
        };
    }

    /**
     * Common filter for minimum net extrinsic value to price percentage.
     */
    protected Predicate<T> commonMinNetExtrinsicValueToPricePercentageFilter(F filter, Function<T, Double> percentageExtractor) {
        return candidate -> {
            if (filter.getMinNetExtrinsicValueToPricePercentage() == null) return true;
            return percentageExtractor.apply(candidate) >= filter.getMinNetExtrinsicValueToPricePercentage();
        };
    }
}
