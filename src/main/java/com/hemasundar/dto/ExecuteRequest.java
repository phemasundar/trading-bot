package com.hemasundar.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteRequest {
    private List<Integer> strategyIndices;
    private List<Integer> screenerIndices;
}
