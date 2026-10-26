package com.hemasundar.config;

import com.hemasundar.dto.AlertMessages;
import com.hemasundar.dto.ExecutionAlert;
import com.hemasundar.services.StrategyExecutionService;
import com.hemasundar.services.supabase.IVDataRepository;
import com.hemasundar.utils.EarningsCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Validates critical application configurations at startup and before execution.
 * Surfaces configuration failures to the UI layer and enforces fail-fast behavior.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class ApplicationConfigValidator implements ApplicationRunner {

    private final Optional<IVDataRepository> ivDataRepository;
    private final StrategyExecutionService executionService;

    @Override
    public void run(ApplicationArguments args) {
        validateConfig();
    }

    /**
     * Validates all critical configurations, adding UI error alerts for any that are missing or invalid.
     *
     * @return list of error messages, or empty list if all configurations are valid
     */
    public List<String> validateConfig() {
        List<String> errors = new ArrayList<>();
        validateEarningsCache().ifPresent(errors::add);
        validateIVData().ifPresent(errors::add);
        return errors;
    }

    /**
     * Validates earnings calendar cache validity days configuration.
     *
     * @return error message if invalid, or empty optional if valid
     */
    public Optional<String> validateEarningsCache() {
        if (EarningsCacheManager.getCacheValidityDays() <= 0) {
            String err = AlertMessages.CONFIG_MISSING_EARNINGS_VALIDITY;
            log.error("[Config Validation] {}", err);
            executionService.addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_CONFIG, err);
            return Optional.of(err);
        }
        return Optional.empty();
    }

    /**
     * Validates minimum IV records threshold configuration.
     *
     * @return error message if invalid, or empty optional if valid
     */
    public Optional<String> validateIVData() {
        if (ivDataRepository.isPresent() && ivDataRepository.get().getMinIVRecords() <= 0) {
            String err = AlertMessages.CONFIG_MISSING_IV_RECORDS;
            log.error("[Config Validation] {}", err);
            executionService.addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_CONFIG, err);
            return Optional.of(err);
        }
        return Optional.empty();
    }
}
