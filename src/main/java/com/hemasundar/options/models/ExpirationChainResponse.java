package com.hemasundar.options.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response POJO for Schwab Expiration Chain API.
 * Contains all available expiration dates for options on a symbol.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExpirationChainResponse {

    private String symbol;
    private String status;
    private List<ExpirationDate> expirationList;

    /**
     * Represents a single expiration date with its details.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExpirationDate {
        private String expirationDate;
        private int daysToExpiration;
        private String expirationType;
        private String settlementType;
        private String optionRoots;
        private boolean standard;
    }
}
