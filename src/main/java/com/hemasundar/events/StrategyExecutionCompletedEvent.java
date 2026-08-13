package com.hemasundar.events;

import com.hemasundar.dto.StrategyResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Event published when a strategy execution completes successfully.
 */
@Getter
@AllArgsConstructor
public class StrategyExecutionCompletedEvent {
    private final StrategyResult result;
    private final boolean isCustomExecution;
}
