package com.hemasundar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Data transfer object representing an option strategy guide for the Learning Center.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyDescriptionDto {
    private String id;
    private String name;
    private String filename;
    private String category;
    private String bias;
    private String summary;
    private Map<String, String> greeks;
}
