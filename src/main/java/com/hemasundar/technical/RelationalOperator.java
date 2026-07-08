package com.hemasundar.technical;

import java.util.function.BiPredicate;

public enum RelationalOperator {
    GREATER_THAN_OR_EQUAL(">=", (a, b) -> a >= b),
    LESS_THAN_OR_EQUAL("<=", (a, b) -> a <= b),
    EQUAL("==", (a, b) -> Math.abs(a - b) < 0.0001),
    GREATER_THAN(">", (a, b) -> a > b),
    LESS_THAN("<", (a, b) -> a < b);

    private final String symbol;
    private final BiPredicate<Double, Double> evaluator;

    RelationalOperator(String symbol, BiPredicate<Double, Double> evaluator) {
        this.symbol = symbol;
        this.evaluator = evaluator;
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean evaluate(double actual, double target) {
        return evaluator.test(actual, target);
    }
}
