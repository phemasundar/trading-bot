package com.hemasundar.jobs;

import com.hemasundar.config.properties.JobConfig;
import com.hemasundar.utils.SchwabTokenGenerator;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.*;

public class ScheduledJobRunnerTest {

    @Mock
    private JobConfig jobConfig;
    @Mock
    private IVDataJobService ivDataJobService;
    @Mock
    private ScreenerJobService screenerJobService;
    @Mock
    private SchwabTokenGenerator schwabTokenGenerator;

    private ScheduledJobRunner scheduledJobRunner;

    @BeforeMethod
    public void setup() {
        MockitoAnnotations.openMocks(this);
        // Manual initialization and spying to ensure correct mock injection
        scheduledJobRunner = spy(new ScheduledJobRunner(jobConfig, ivDataJobService, screenerJobService, schwabTokenGenerator));
        // Prevent System.exit from killing the test process
        doNothing().when(scheduledJobRunner).exit(anyInt());
    }

    @Test
    public void testRun_None() throws Exception {
        when(jobConfig.getName()).thenReturn("NONE");
        
        scheduledJobRunner.run();
        
        verify(ivDataJobService, never()).runIVDataCollection();
        verify(scheduledJobRunner, never()).exit(anyInt());
    }

    @Test
    public void testRun_IvData() throws Exception {
        when(jobConfig.getName()).thenReturn("IV_DATA");
        
        scheduledJobRunner.run();
        
        verify(ivDataJobService).runIVDataCollection();
        verify(scheduledJobRunner).exit(0);
    }

    @Test
    public void testRun_Screener() throws Exception {
        when(jobConfig.getName()).thenReturn("SCREENER");
        
        scheduledJobRunner.run();
        
        verify(screenerJobService).runScheduledScreeners();
        verify(scheduledJobRunner).exit(0);
    }

    @Test
    public void testRun_GenerateToken() throws Exception {
        when(jobConfig.getName()).thenReturn("GENERATE_TOKEN");
        
        scheduledJobRunner.run();
        
        verify(schwabTokenGenerator).runInteractiveGenerator();
        verify(scheduledJobRunner).exit(0);
    }

    @Test
    public void testRun_Unknown() throws Exception {
        when(jobConfig.getName()).thenReturn("UNKNOWN_JOB");
        
        scheduledJobRunner.run();
        
        verifyNoInteractions(ivDataJobService, screenerJobService, schwabTokenGenerator);
        verify(scheduledJobRunner).exit(0);
    }

    @Test
    public void testRun_ExceptionHandling() throws Exception {
        when(jobConfig.getName()).thenReturn("IV_DATA");
        doThrow(new RuntimeException("Test failure")).when(ivDataJobService).runIVDataCollection();
        
        scheduledJobRunner.run();
        
        // Should catch exception and still exit
        verify(scheduledJobRunner).exit(0);
    }
}
