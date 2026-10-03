package com.hemasundar.config;

import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.JavaUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Loads role-based access configuration from roles-config.yml.
 * Supports dynamic runtime reloading when the configuration file changes on disk.
 */
@Log4j2
@Component
public class RolesConfigLoader {

    private RolesConfig config;
    private long lastModifiedTime = -1L;

    @PostConstruct
    public synchronized void load() {
        try {
            Path filePath = getConfigFilePath();
            if (filePath != null) {
                lastModifiedTime = Files.getLastModifiedTime(filePath).toMillis();
            }
            String yaml = FilePaths.readResource(FilePaths.rolesConfig);
            config = JavaUtils.convertYamlToPojo(yaml, RolesConfig.class);
            log.info("Loaded roles config: readonly allowed pages = {}", getRawAllowedPages());
        } catch (Exception e) {
            log.warn("Failed to load roles-config.yml, defaulting to empty readonly pages: {}", e.getMessage());
            config = new RolesConfig();
        }
    }

    /**
     * Checks if the configuration file has changed on disk and reloads it if modified.
     */
    public synchronized void reloadIfChanged() {
        try {
            Path filePath = getConfigFilePath();
            if (filePath != null) {
                long currentModified = Files.getLastModifiedTime(filePath).toMillis();
                if (currentModified != lastModifiedTime) {
                    log.info("Detected change in roles-config.yml (timestamp {} -> {}). Reloading...",
                            lastModifiedTime, currentModified);
                    load();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check or reload roles-config.yml: {}", e.getMessage());
        }
    }

    /**
     * Force-reloads the configuration immediately.
     */
    public synchronized void reload() {
        load();
    }

    /**
     * Returns the RolesConfig, reloading if changed on disk.
     */
    public synchronized RolesConfig getConfig() {
        reloadIfChanged();
        return config;
    }

    /**
     * Returns the list of allowed pages for readonly users, refreshed dynamically if modified.
     */
    public synchronized List<String> getReadonlyAllowedPages() {
        reloadIfChanged();
        return getRawAllowedPages();
    }

    private List<String> getRawAllowedPages() {
        if (config == null || config.getReadonly() == null || config.getReadonly().getAllowedPages() == null) {
            return Collections.emptyList();
        }
        return config.getReadonly().getAllowedPages().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private Path getConfigFilePath() {
        Path localPath = Path.of("src/main/resources", FilePaths.rolesConfig);
        if (Files.exists(localPath)) {
            return localPath;
        }
        Path targetPath = Path.of("target/classes", FilePaths.rolesConfig);
        if (Files.exists(targetPath)) {
            return targetPath;
        }
        return null;
    }
}
