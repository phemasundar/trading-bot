package com.hemasundar.utils;

import com.hemasundar.technical.NumericRule;
import com.hemasundar.technical.RelationalOperator;
import org.apache.commons.lang3.StringUtils;

public class ConditionParserUtil {

    /**
     * Parses a string rule like ">= 25" into a NumericRule object.
     * Order of RelationalOperator enum matters here (e.g. ">=" evaluated before ">").
     */
    public static NumericRule parseNumericRule(String rule) {
        if (StringUtils.isBlank(rule)) {
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
