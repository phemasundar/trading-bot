package com.hemasundar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data transfer object representing an Option Greek guide for the Learning Center.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionGreekDto {
    private String id;
    private String name;
    private String symbol;
    private String order;
    private String derivative;
    private String filename;
    private String summary;
    private String exposure;
    private String impact;
}
