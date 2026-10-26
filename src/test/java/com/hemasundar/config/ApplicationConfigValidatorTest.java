package com.hemasundar.config;

import com.hemasundar.dto.AlertMessages;
import com.hemasundar.dto.ExecutionAlert;
import com.hemasundar.services.StrategyExecutionService;
import com.hemasundar.services.supabase.IVDataRepository;
import com.hemasundar.utils.EarningsCacheManager;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.ApplicationArguments;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class ApplicationConfigValidatorTest {

    @Mock
    private IVDataRepository ivDataRepository;

    @Mock
    private StrategyExecutionService executionService;

    @Mock
    private ApplicationArguments applicationArguments;

    private ApplicationConfigValidator validator;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        validator = new ApplicationConfigValidator(Optional.of(ivDataRepository), executionService);
    }

    @Test
    public void testValidateConfig_AllValid() {
        EarningsCacheManager.setCacheValidityDays(5);
        when(ivDataRepository.getMinIVRecords()).thenReturn(20);

        List<String> errors = validator.validateConfig();

        assertTrue(errors.isEmpty());
        verify(executionService, never()).addAlert(any(), anyString(), anyString());
    }

    @Test
    public void testValidateConfig_EarningsValidityMissing() {
        EarningsCacheManager.setCacheValidityDays(0);
        when(ivDataRepository.getMinIVRecords()).thenReturn(20);

        try {
            List<String> errors = validator.validateConfig();

            assertEquals(errors.size(), 1);
            assertEquals(errors.get(0), AlertMessages.CONFIG_MISSING_EARNINGS_VALIDITY);
            verify(executionService).addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_CONFIG, AlertMessages.CONFIG_MISSING_EARNINGS_VALIDITY);
        } finally {
            EarningsCacheManager.setCacheValidityDays(5);
        }
    }

    @Test
    public void testValidateConfig_IVMinRecordsMissing() {
        EarningsCacheManager.setCacheValidityDays(5);
        when(ivDataRepository.getMinIVRecords()).thenReturn(0);

        List<String> errors = validator.validateConfig();

        assertEquals(errors.size(), 1);
        assertEquals(errors.get(0), AlertMessages.CONFIG_MISSING_IV_RECORDS);
        verify(executionService).addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_CONFIG, AlertMessages.CONFIG_MISSING_IV_RECORDS);
    }

    @Test
    public void testValidateConfig_BothMissing() {
        EarningsCacheManager.setCacheValidityDays(0);
        when(ivDataRepository.getMinIVRecords()).thenReturn(0);

        try {
            List<String> errors = validator.validateConfig();

            assertEquals(errors.size(), 2);
            assertTrue(errors.contains(AlertMessages.CONFIG_MISSING_EARNINGS_VALIDITY));
            assertTrue(errors.contains(AlertMessages.CONFIG_MISSING_IV_RECORDS));
            verify(executionService).addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_CONFIG, AlertMessages.CONFIG_MISSING_EARNINGS_VALIDITY);
            verify(executionService).addAlert(ExecutionAlert.Severity.ERROR, AlertMessages.SRC_CONFIG, AlertMessages.CONFIG_MISSING_IV_RECORDS);
        } finally {
            EarningsCacheManager.setCacheValidityDays(5);
        }
    }

    @Test
    public void testRun_DelegatesToValidateConfig() {
        EarningsCacheManager.setCacheValidityDays(5);
        when(ivDataRepository.getMinIVRecords()).thenReturn(20);

        validator.run(applicationArguments);

        verify(executionService, never()).addAlert(any(), anyString(), anyString());
    }

    @Test
    public void testValidateConfig_NoSupabaseRepository() {
        EarningsCacheManager.setCacheValidityDays(5);
        ApplicationConfigValidator noSupabaseValidator = new ApplicationConfigValidator(Optional.empty(), executionService);

        List<String> errors = noSupabaseValidator.validateConfig();

        assertTrue(errors.isEmpty());
        verify(executionService, never()).addAlert(any(), anyString(), anyString());
    }
}
