package com.hemasundar.config;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * POJO mapping for roles-config.yml.
 * Defines per-role page access configuration.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RolesConfig {

    private RolePages readonly;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RolePages {
        /** Browser paths accessible to this role. */
        @JsonProperty("allowed-pages")
        @JsonAlias({"allowedPages", "allowed_pages"})
        private List<String> allowedPages;
    }
}
