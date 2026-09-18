package com.hemasundar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data transfer object representing a securities group discovered from the filesystem/classpath.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecuritiesGroupDto {
    private String id;
    private String fileName;
    private String displayName;
    private int symbolCount;
    private List<String> symbols;
}
