package com.hemasundar.config;

import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.JavaUtils;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Loads role-based access configuration from roles-config.yml on startup.
 */
@Log4j2
@Component
public class RolesConfigLoader {

    @Getter
    private RolesConfig config;

    @PostConstruct
    public void load() {
        try {
            String yaml = FilePaths.readResource(FilePaths.rolesConfig);
            config = JavaUtils.convertYamlToPojo(yaml, RolesConfig.class);
            log.info("Loaded roles config: readonly allowed pages = {}", getReadonlyAllowedPages());
        } catch (Exception e) {
            log.warn("Failed to load roles-config.yml, defaulting to empty readonly pages: {}", e.getMessage());
            config = new RolesConfig();
        }
    }

    /** Returns the list of allowed pages for readonly users. */
    public List<String> getReadonlyAllowedPages() {
        if (config == null || config.getReadonly() == null || config.getReadonly().getAllowedPages() == null) {
            return Collections.emptyList();
        }
        return config.getReadonly().getAllowedPages();
    }
}
