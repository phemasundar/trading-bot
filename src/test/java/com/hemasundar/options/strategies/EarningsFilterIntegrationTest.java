package com.hemasundar.options.strategies;

import com.hemasundar.technical.MathExpression;
import com.hemasundar.technical.MathExpressionEvaluator;
import com.hemasundar.utils.MathExpressionParser;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarningsFilterIntegrationTest {

    @Test
    void testSafeCloseScenario_EarningsTooCloseToDte() {
        // EARNINGS_NEAREST_TO_DTE <= DTE - 5
        List<MathExpression> rules = MathExpressionParser.parseRules(List.of("EARNINGS_NEAREST_TO_DTE <= DTE - 5"));
        
        Map<String, Double> variables = new HashMap<>();
        variables.put("DTE", 45.0);
        variables.put("EARNINGS_NEAREST_TO_DTE", 42.0); // Earnings 3 days before DTE
        
        // 42 <= (45 - 5) == 42 <= 40 -> false
        assertFalse(MathExpressionEvaluator.evaluateAll(rules, variables::get));
    }

    @Test
    void testSafeCloseScenario_EarningsFarFromDte() {
        // EARNINGS_NEAREST_TO_DTE <= DTE - 5
        List<MathExpression> rules = MathExpressionParser.parseRules(List.of("EARNINGS_NEAREST_TO_DTE <= DTE - 5"));
        
        Map<String, Double> variables = new HashMap<>();
        variables.put("DTE", 45.0);
        variables.put("EARNINGS_NEAREST_TO_DTE", 35.0); // Earnings 10 days before DTE
        
        // 35 <= (45 - 5) == 35 <= 40 -> true
        assertTrue(MathExpressionEvaluator.evaluateAll(rules, variables::get));
    }

    @Test
    void testSafeCloseScenario_NoEarningsBeforeDte() {
        // EARNINGS_NEAREST_TO_DTE <= DTE - 5
        List<MathExpression> rules = MathExpressionParser.parseRules(List.of("EARNINGS_NEAREST_TO_DTE <= DTE - 5"));
        
        Map<String, Double> variables = new HashMap<>();
        variables.put("DTE", 45.0);
        variables.put("EARNINGS_NEAREST_TO_DTE", 0.0); // No earnings
        
        // 0 <= (45 - 5) == 0 <= 40 -> true
        assertTrue(MathExpressionEvaluator.evaluateAll(rules, variables::get));
    }

    @Test
    void testIvPumpScenario_EarningsUpcoming() {
        // DAYS_TO_NEXT_EARNINGS <= 14
        List<MathExpression> rules = MathExpressionParser.parseRules(List.of("DAYS_TO_NEXT_EARNINGS <= 14"));
        
        Map<String, Double> variables = new HashMap<>();
        variables.put("DAYS_TO_NEXT_EARNINGS", 10.0); // Next earnings in 10 days
        
        // 10 <= 14 -> true
        assertTrue(MathExpressionEvaluator.evaluateAll(rules, variables::get));
    }

    @Test
    void testIvPumpScenario_NoEarningsUpcoming() {
        // DAYS_TO_NEXT_EARNINGS <= 14
        List<MathExpression> rules = MathExpressionParser.parseRules(List.of("DAYS_TO_NEXT_EARNINGS <= 14"));
        
        Map<String, Double> variables = new HashMap<>();
        variables.put("DAYS_TO_NEXT_EARNINGS", 999999.0); // No earnings found
        
        // 999999 <= 14 -> false
        assertFalse(MathExpressionEvaluator.evaluateAll(rules, variables::get));
    }
    
    @Test
    void testOldIgnoreEarningsFalseEquivalent() {
        // DAYS_TO_NEXT_EARNINGS >= DTE
        List<MathExpression> rules = MathExpressionParser.parseRules(List.of("DAYS_TO_NEXT_EARNINGS >= DTE"));
        
        Map<String, Double> variables = new HashMap<>();
        variables.put("DTE", 30.0);
        
        // Scenario 1: No earnings
        variables.put("DAYS_TO_NEXT_EARNINGS", 999999.0);
        assertTrue(MathExpressionEvaluator.evaluateAll(rules, variables::get));
        
        // Scenario 2: Earnings after DTE
        variables.put("DAYS_TO_NEXT_EARNINGS", 45.0);
        assertTrue(MathExpressionEvaluator.evaluateAll(rules, variables::get));
        
        // Scenario 3: Earnings before DTE
        variables.put("DAYS_TO_NEXT_EARNINGS", 15.0);
        assertFalse(MathExpressionEvaluator.evaluateAll(rules, variables::get));
    }
}
