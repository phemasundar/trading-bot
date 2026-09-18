package com.hemasundar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request payload for fetching calculated technical indicators for a list of symbols.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecuritiesDataRequest {
    private List<String> symbols;
}
