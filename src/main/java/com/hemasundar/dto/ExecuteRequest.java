package com.hemasundar.dto;

import lombok.Value;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import java.util.List;

@Value
@Builder(toBuilder = true)
@Jacksonized
public class ExecuteRequest {
    private List<Integer> strategyIndices;
    private List<Integer> screenerIndices;
}
