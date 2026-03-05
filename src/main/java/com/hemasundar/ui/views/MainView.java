package com.hemasundar.ui.views;

import com.hemasundar.dto.ExecutionResult;
import com.hemasundar.dto.StrategyResult;
import com.hemasundar.dto.Trade;

import com.hemasundar.options.models.OptionsConfig;
import com.hemasundar.services.StrategyExecutionService;
import com.hemasundar.ui.components.ResultCardBuilder;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Main view of the Trading Bot web UI.
 * Provides strategy selection, execution, and results display.
 */
import com.vaadin.flow.theme.lumo.Lumo;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;

import com.hemasundar.ui.MainLayout;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Trading Bot Dashboard")

@Log4j2
public class MainView extends VerticalLayout {

    private final StrategyExecutionService strategyService;

    // UI Components
    private CheckboxGroup<String> strategySelector;
    private Button executeButton;
    private Button stopButton;
    private Button selectAllButton;
    private Button clearAllButton;
    private ProgressBar progressBar;
    private Div resultsContainer;

    // Data
    private List<OptionsConfig> availableStrategies;

    @Autowired
    public MainView(StrategyExecutionService strategyService) {
        this.strategyService = strategyService;

        // Apply Dark Theme
        getElement().getThemeList().add(Lumo.DARK);

        try {
            // Load available strategies
            availableStrategies = strategyService.getEnabledStrategies();

            setSizeFull();
            setPadding(true);
            setSpacing(true);
            addClassName("main-layout");

            add(createStrategySelector());
            add(createExecutionPanel());
            add(createResultsDisplay());

            // Load latest results on startup
            loadLatestResults();

            // Restore progress bar if execution is still running (e.g., page refresh)
            if (strategyService.isExecutionRunning()) {
                executionStartTime = strategyService.getExecutionStartTimeMs();
                setUIBusy(true, 0);
                if (progressStrategyCount != null) {
                    progressStrategyCount.setText("Execution in progress...");
                }
            }

        } catch (IOException e) {
            log.error("Failed to initialize MainView: {}", e.getMessage());
            Notification.show("Failed to load strategies: " + e.getMessage(),
                    5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * Creates the strategy selection component
     */
    private Component createStrategySelector() {
        VerticalLayout configPanel = new VerticalLayout();
        configPanel.addClassName("strategy-config-panel");
        configPanel.setSpacing(true);
        configPanel.setPadding(true);
        configPanel.setWidthFull();

        // â”€â”€ Header: Title + Subtitle â”€â”€
        H2 title = new H2("Strategy Configuration");
        title.addClassName("section-title");
        title.getStyle().set("margin", "0");

        Span subtitle = new Span("Select active strategies to scan the market for opportunities.");
        subtitle.addClassName("section-subtitle");

        // â”€â”€ Action buttons row: Execute + Stop â”€â”€
        executeButton = new Button("Execute Strategies");
        executeButton.setIcon(com.vaadin.flow.component.icon.VaadinIcon.BOLT.create());
        executeButton.addClassName("stitch-button-primary");
        executeButton.addClickListener(e -> executeStrategies());

        stopButton = new Button("Stop");
        stopButton.setIcon(com.vaadin.flow.component.icon.VaadinIcon.STOP.create());
        stopButton.addClassName("stitch-button-danger");
        stopButton.setVisible(false);
        stopButton.addClickListener(e -> {
            strategyService.cancelExecution();
            stopButton.setEnabled(false);
            stopButton.setText("Stopping...");
            if (statusText != null) {
                statusText.setText("Stopping...");
            }
        });

        HorizontalLayout executeRow = new HorizontalLayout(executeButton, stopButton);
        executeRow.setSpacing(true);
        executeRow.setPadding(false);

        // â”€â”€ Active Strategies label â”€â”€
        Span activeLabel = new Span("ACTIVE STRATEGIES:");
        activeLabel.getStyle().set("font-size", "0.75rem");
        activeLabel.getStyle().set("font-weight", "600");
        activeLabel.getStyle().set("color", "var(--stitch-text-secondary)");
        activeLabel.getStyle().set("letter-spacing", "0.05em");

        // â”€â”€ Strategy checkbox group (horizontal wrapping via Vaadin theme) â”€â”€
        strategySelector = new CheckboxGroup<>();
        List<String> strategyNames = IntStream.range(0, availableStrategies.size())
                .mapToObj(i -> {
                    OptionsConfig config = availableStrategies.get(i);
                    return config.getName();
                })
                .collect(Collectors.toList());
        strategySelector.setItems(strategyNames);
        strategySelector.addClassName("strategy-selector");
        strategySelector.setWidthFull();

        // â”€â”€ Select All / Clear All buttons â”€â”€
        selectAllButton = new Button("Select All", e -> strategySelector.setValue(new HashSet<>(strategyNames)));
        selectAllButton.addClassName("stitch-button-secondary");

        clearAllButton = new Button("Clear All", e -> strategySelector.clear());
        clearAllButton.addClassName("stitch-button-secondary");

        HorizontalLayout actionButtons = new HorizontalLayout(selectAllButton, clearAllButton);
        actionButtons.setSpacing(true);
        actionButtons.setPadding(false);

        // â”€â”€ Assemble: everything stacks vertically â”€â”€
        configPanel.add(title, subtitle, executeRow, activeLabel, strategySelector, actionButtons);

        return configPanel;
    }

    /**
     * Creates the execution control panel
     */
    private Component createExecutionPanel() {
        // Custom Progress Bar Container (Hidden by default)
        Div progressContainer = new Div();
        progressContainer.addClassName("scan-status-container");
        progressContainer.setVisible(false);
        progressContainer.setWidthFull();

        // Progress Bar Background & Fill
        Div progressBg = new Div();
        progressBg.addClassName("scan-progress-bar-bg");

        Div progressFill = new Div();
        progressFill.addClassName("scan-progress-bar-fill");
        progressFill.getStyle().set("width", "0%");

        Div progressGlow = new Div();
        progressGlow.addClassName("scan-progress-bar-glow");
        progressFill.add(progressGlow);

        progressBg.add(progressFill);

        // Content (Text & Indicators)
        Div content = new Div();
        content.addClassName("scan-status-content");

        // Left Section: Pulse + Text + Count
        HorizontalLayout left = new HorizontalLayout();
        left.setAlignItems(Alignment.CENTER);
        left.setSpacing(true);

        Div pulsingDot = new Div();
        pulsingDot.addClassName("pulsing-dot");

        Span statusText = new Span("Scanning markets...");
        statusText.addClassName("scan-status-text");
        statusText.getStyle().set("color", "var(--stitch-primary)");
        statusText.getStyle().set("font-weight", "500");

        progressStrategyCount = new Span("Processing strategy 0 of 0");
        progressStrategyCount.addClassName("scan-status-subtext");
        progressStrategyCount.getStyle().set("color", "var(--stitch-text-secondary)");
        progressStrategyCount.getStyle().set("font-size", "0.875rem");
        progressStrategyCount.getStyle().set("margin-left", "12px");

        left.add(pulsingDot, statusText, progressStrategyCount);

        // Right Section: Timer
        HorizontalLayout right = new HorizontalLayout();
        right.setAlignItems(Alignment.CENTER);
        Span elapsedLabel = new Span("Elapsed time: ");
        elapsedLabel.addClassName("scan-timer-label");
        elapsedLabel.getStyle().set("color", "var(--stitch-text-secondary)");
        elapsedLabel.getStyle().set("font-size", "0.875rem");

        progressTimer = new Span("0s");
        progressTimer.addClassName("scan-timer-value");
        progressTimer.getStyle().set("color", "var(--stitch-text-primary)");
        progressTimer.getStyle().set("font-weight", "600");
        progressTimer.getStyle().set("font-family", "monospace");

        right.add(elapsedLabel, progressTimer);

        content.add(left, right);
        progressContainer.add(content, progressBg);

        // Assign to class field for updates
        this.customProgressBar = progressContainer;
        this.progressFill = progressFill;
        this.statusText = statusText;

        return progressContainer;
    }

    // Class fields for custom progress bar
    private Div customProgressBar;
    private Div progressFill;
    private Span statusText;
    private Span progressTimer;
    private Span progressStrategyCount;
    private java.util.Timer elapsedTimer;
    private long executionStartTime;

    /**
     * Sets the UI to busy or ready state
     */
    private void setUIBusy(boolean busy, int strategyCount) {
        if (customProgressBar == null)
            return;

        customProgressBar.setVisible(busy);
        strategySelector.setEnabled(!busy);
        selectAllButton.setEnabled(!busy);
        clearAllButton.setEnabled(!busy);

        if (busy) {
            progressFill.getStyle().set("width", "5%");
            if (progressTimer != null) {
                executionStartTime = System.currentTimeMillis();
                progressTimer.setText("0s");
                startElapsedTimer();
            }
            if (progressStrategyCount != null) {
                // Stitch: Processing strategy 5 of 12
                progressStrategyCount.setText("Processing strategy 1 of " + strategyCount);
            }
            if (statusText != null) {
                // Stitch: Scanning markets...
                statusText.setText("Scanning markets...");
            }

            // Disable main button & Show "Running..."
            executeButton.setEnabled(false);
            executeButton.setText("Running...");
            executeButton.setIcon(null);
            executeButton.addClassName("cursor-not-allowed");
            stopButton.setVisible(true);
            stopButton.setEnabled(true);
            stopButton.setText("Stop");
        } else {
            stopElapsedTimer();
            progressFill.getStyle().set("width", "100%");
            // Enable main button
            executeButton.setEnabled(true);
            executeButton.setText("Execute Strategies");
            executeButton.setIcon(com.vaadin.flow.component.icon.VaadinIcon.BOLT.create());
            executeButton.removeClassName("cursor-not-allowed");
            stopButton.setVisible(false);
        }
    }

    private void startElapsedTimer() {
        stopElapsedTimer();
        UI ui = UI.getCurrent();
        elapsedTimer = new java.util.Timer(true);
        elapsedTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                long elapsed = (System.currentTimeMillis() - executionStartTime) / 1000;
                long minutes = elapsed / 60;
                long seconds = elapsed % 60;
                String text = minutes > 0 ? String.format("%dm %ds", minutes, seconds) : seconds + "s";
                if (ui != null) {
                    ui.access(() -> {
                        progressTimer.setText(text);
                        // Check if execution finished (handles page refresh case)
                        if (!strategyService.isExecutionRunning()) {
                            stopElapsedTimer();
                            setUIBusy(false, 0);
                            loadLatestResults();
                            Notification.show("Execution completed",
                                    3000, Notification.Position.BOTTOM_END)
                                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                        }
                    });
                }
            }
        }, 1000, 1000);
    }

    private void stopElapsedTimer() {
        if (elapsedTimer != null) {
            elapsedTimer.cancel();
            elapsedTimer = null;
        }
    }

    /**
     * Creates the results display area
     */
    private Component createResultsDisplay() {
        // Stitch: Execution Results header with "Updates live" badge
        HorizontalLayout headerLayout = new HorizontalLayout();
        headerLayout.setAlignItems(Alignment.CENTER);
        headerLayout.setSpacing(true);

        H3 header = new H3("Execution Results");
        header.addClassName("text-lg");

        Span badge = new Span("Updates live");
        badge.getElement().getThemeList().add("badge");
        badge.getStyle().set("font-size", "0.75rem");
        badge.getStyle().set("border", "1px solid var(--stitch-border)");

        headerLayout.add(header, badge);

        resultsContainer = new Div();
        resultsContainer.addClassName("results-container");
        resultsContainer.setWidthFull();

        // Stitch: Main container with max-width
        VerticalLayout layout = new VerticalLayout(headerLayout, resultsContainer);
        layout.setSpacing(true);
        layout.setPadding(false);
        layout.addClassName("max-w-7xl");
        layout.addClassName("mx-auto");
        layout.setWidthFull();

        return layout;
    }

    /**
     * Executes the selected strategies
     */
    private void executeStrategies() {
        Set<String> selectedNames = strategySelector.getSelectedItems();

        if (selectedNames.isEmpty()) {
            Notification.show("Please select at least one strategy",
                    3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        // Extract strategy indices from selection
        Set<Integer> indices = selectedNames.stream()
                .map(name -> {
                    // Extract index from "[0] Strategy Name (Type)" format
                    if (name.contains("[")) {
                        int start = name.indexOf('[') + 1;
                        int end = name.indexOf(']');
                        return Integer.parseInt(name.substring(start, end));
                    } else {
                        // Fallback: finding index by name (less reliable if duplicates)
                        for (int i = 0; i < availableStrategies.size(); i++) {
                            if (availableStrategies.get(i).getName().equals(name)) {
                                return i;
                            }
                        }
                        return -1;
                    }
                })
                .collect(Collectors.toSet());

        // Show loading state
        setUIBusy(true, indices.size());

        // Execute asynchronously
        UI ui = UI.getCurrent();
        log.info("Starting async execution for {} strategies", indices.size());

        CompletableFuture.runAsync(() -> {
            ExecutionResult result = null;
            Exception executionError = null;

            try {
                result = strategyService.executeStrategies(indices);
                log.info("Execution completed successfully: {} trades found", result.getTotalTradesFound());
            } catch (Exception e) {
                log.error("Strategy execution failed", e);
                executionError = e;
            }

            // Always update UI, whether success or failure
            final ExecutionResult finalResult = result;
            final Exception finalError = executionError;

            ui.access(() -> {
                log.info("Inside ui.access() callback - starting UI update");
                try {
                    if (finalError != null) {
                        log.info("Handling error case in UI");
                        // Handle error case

                        String errorMsg = finalError.getMessage() != null ? finalError.getMessage()
                                : "Unknown error occurred";
                        Notification.show("Execution failed: " + errorMsg,
                                5000, Notification.Position.MIDDLE)
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    } else if (finalResult != null) {
                        log.info("Handling success case in UI - reloading all results");
                        // Reload ALL strategy results from database (includes newly executed +
                        // existing)
                        loadLatestResults();

                        Notification.show(
                                String.format("Execution completed: %d strategies, %d trades found in %dms",
                                        finalResult.getResults().size(),
                                        finalResult.getTotalTradesFound(),
                                        finalResult.getTotalExecutionTimeMs()),
                                5000, Notification.Position.BOTTOM_END)
                                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                        log.info("Success notification shown");
                    } else {
                        log.warn("Both finalError and finalResult are null - unexpected state");
                    }
                } catch (Exception uiError) {
                    log.error("Error updating UI after execution", uiError);
                } finally {
                    log.info("In finally block - calling setUIBusy(false)");
                    // ALWAYS reset UI state, no matter what
                    setUIBusy(false, 0);
                    log.info("setUIBusy(false) completed");
                }
            });
        });
    }

    /**
     * Loads all latest strategy results from Supabase (per-strategy persistence)
     */
    private void loadLatestResults() {
        try {
            List<StrategyResult> allResults = strategyService.getAllLatestStrategyResults();

            if (!allResults.isEmpty()) {
                resultsContainer.removeAll();
                for (StrategyResult result : allResults) {
                    resultsContainer.add(ResultCardBuilder.build(result, "Standard"));
                }
                log.info("Loaded {} strategy results from database", allResults.size());
            } else {
                resultsContainer.add(new Span("No previous results. Execute strategies to see results."));
                log.info("No previous strategy results found");
            }
        } catch (Exception e) {
            log.error("Failed to load latest results: {}", e.getMessage());
            resultsContainer.add(new Span("Failed to load previous results: " + e.getMessage()));
        }
    }
}
