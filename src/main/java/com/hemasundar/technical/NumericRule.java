package com.hemasundar.technical;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NumericRule {
    private RelationalOperator operator;
    private double value;

    public boolean evaluate(double actualValue) {
        if (operator == null) return false;
        return operator.evaluate(actualValue, this.value);
    }
    
    @Override
    public String toString() {
        return operator.getSymbol() + " " + (value % 1 == 0 ? String.format("%.0f", value) : String.valueOf(value));
    }
}
