package com.hemasundar.options.expectedmove;

import lombok.Builder;
import lombok.Value;

/**
 * Structured output of the 3-Tier Waterfall + LEAPS Expected Move calculation for a single
 * underlying ticker and option expiration date.
 */
@Value
@Builder
public class ExpectedMoveResult {

    boolean valid;
    double spotPrice;
    double upsideMove;
    double downsideMove;
    double upperBound;
    double lowerBound;
    double upsideMovePct;
    double downsideMovePct;
    boolean lognormal;
    ExpectedMoveTier tierUsed;
    String reason;

    /**
     * Builds a terminal circuit-breaker result when no tier can produce a trustworthy Expected Move.
     */
    public static ExpectedMoveResult circuitBreaker(double spotPrice, String reason) {
        return ExpectedMoveResult.builder()
                .valid(false)
                .spotPrice(spotPrice)
                .tierUsed(ExpectedMoveTier.CIRCUIT_BREAKER)
                .reason(reason)
                .build();
    }
}
