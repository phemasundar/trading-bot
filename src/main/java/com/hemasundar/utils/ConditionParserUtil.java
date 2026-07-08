package com.hemasundar.utils;

import com.hemasundar.technical.NumericRule;
import com.hemasundar.technical.RelationalOperator;

public class ConditionParserUtil {

    /**
     * Parses a string rule like ">= 25" into a NumericRule object.
     * Order of RelationalOperator enum matters here (e.g. ">=" evaluated before ">").
     */
    public static NumericRule parseNumericRule(String rule) {
        if (rule == null || rule.trim().isEmpty()) {
            return null;
        }
        rule = rule.trim();
        for (RelationalOperator op : RelationalOperator.values()) {
            if (rule.startsWith(op.getSymbol())) {
                try {
                    String valueStr = rule.substring(op.getSymbol().length()).trim().replace(",", "");
                    double val = Double.parseDouble(valueStr);
                    return new NumericRule(op, val);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid number format in rule: " + rule, e);
                }
            }
        }
        throw new IllegalArgumentException("Invalid operator in rule: " + rule);
    }
}
