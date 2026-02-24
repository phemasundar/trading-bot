package com.hemasundar.ui.views;

import com.hemasundar.dto.ExecutionResult;
import com.hemasundar.dto.StrategyResult;
import com.hemasundar.dto.Trade;
import com.hemasundar.options.models.OptionsConfig;
import com.hemasundar.services.StrategyExecutionService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
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

@Route("")
@PageTitle("Trading Bot Dashboard")

@Log4j2
public class MainView extends com.vaadin.flow.component.applayout.AppLayout {

    private final StrategyExecutionService strategyService;

    // UI Components
    private CheckboxGroup<String> strategySelector;
    private Button executeButton;
    private Button stopButton;
    private Button selectAllButton;
    private Button clearAllButton;
    private ProgressBar progressBar;
    private Div resultsContainer;
    private Span statusLabel;

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

            // Create Main Layout
            addToNavbar(createHeader());
            addToDrawer(createSidebar());

            // Main Content Area
            VerticalLayout mainContent = new VerticalLayout();
            mainContent.setSizeFull();
            mainContent.setPadding(true);
            mainContent.setSpacing(true);
            mainContent.addClassName("main-layout");

            mainContent.add(createStrategySelector());
            mainContent.add(createExecutionPanel());
            mainContent.add(createResultsDisplay());

            setContent(mainContent);

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
     * Creates the page header
     */
    private Component createHeader() {
        H1 title = new H1("Option Trades Finder");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)");
        title.getStyle().set("margin", "0");

        statusLabel = new Span("Market Open");
        statusLabel.getElement().getThemeList().add("badge success");

        HorizontalLayout header = new HorizontalLayout(new com.vaadin.flow.component.applayout.DrawerToggle(), title,
                statusLabel);
        header.addClassName("header");
        header.setWidth("100%");
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        header.setFlexGrow(1, title);
        header.setPadding(true);

        return header;
    }

    private Component createSidebar() {
        VerticalLayout sidebar = new VerticalLayout();
        sidebar.setPadding(true);
        sidebar.setSpacing(true);
        sidebar.setAlignItems(Alignment.CENTER);

        // Placeholder Icons
        sidebar.add(createSidebarIcon("vaadin:dashboard"));
        sidebar.add(createSidebarIcon("vaadin:chart"));
        sidebar.add(createSidebarIcon("vaadin:time-backward"));
        sidebar.add(createSidebarIcon("vaadin:cog"));

        return sidebar;
    }

    private Component createSidebarIcon(String iconName) {
        com.vaadin.flow.component.icon.Icon icon = com.vaadin.flow.component.icon.VaadinIcon.valueOf(
                iconName.replace("vaadin:", "").toUpperCase().replace("-", "_")).create();
        Div iconDiv = new Div(icon);
        iconDiv.addClassName("sidebar-icon");
        return iconDiv;
    }

    /**
     * Creates the strategy selection component
     */
    private Component createStrategySelector() {
        // Main Container for Configuration (The white/dark bar in the screenshot)
        VerticalLayout configPanel = new VerticalLayout();
        configPanel.addClassName("strategy-config-panel");
        configPanel.setSpacing(true);
        configPanel.setPadding(true);
        configPanel.setWidthFull();

        // Top Row: Title/Description left, Execute Button right
        HorizontalLayout topRow = new HorizontalLayout();
        topRow.setWidthFull();
        topRow.setAlignItems(Alignment.CENTER);
        topRow.setJustifyContentMode(com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode.BETWEEN);

        VerticalLayout headerText = new VerticalLayout();
        headerText.setSpacing(false);
        headerText.setPadding(false);

        H2 title = new H2("Strategy Configuration");
        title.addClassName("section-title");
        Span subtitle = new Span("Select active strategies to scan the market for opportunities.");
        subtitle.addClassName("section-subtitle");
        headerText.add(title, subtitle);

        // Execute Button
        executeButton = new Button("Execute Strategies");
        executeButton.setIcon(com.vaadin.flow.component.icon.VaadinIcon.BOLT.create());
        executeButton.addClassName("stitch-button-primary");
        executeButton.addClickListener(e -> executeStrategies());

        // Stop Button (hidden by default)
        stopButton = new Button("Stop");
        stopButton.setIcon(com.vaadin.flow.component.icon.VaadinIcon.STOP.create());
        stopButton.addClassName("stitch-button-danger");
        stopButton.getStyle().set("background", "var(--lumo-error-color)");
        stopButton.getStyle().set("color", "white");
        stopButton.setVisible(false);
        stopButton.addClickListener(e -> {
            strategyService.cancelExecution();
            stopButton.setEnabled(false);
            stopButton.setText("Stopping...");
            if (statusText != null) {
                statusText.setText("Stopping...");
            }
        });

        topRow.add(headerText, executeButton, stopButton);

        // Bottom Row: Active Strategies Label + Checkboxes + Select/Clear All
        HorizontalLayout bottomRow = new HorizontalLayout();
        bottomRow.setWidthFull();
        bottomRow.setAlignItems(Alignment.CENTER);

        Span activeLabel = new Span("ACTIVE STRATEGIES:");
        activeLabel.getStyle().set("font-size", "0.75rem");
        activeLabel.getStyle().set("font-weight", "600");
        activeLabel.getStyle().set("color", "var(--stitch-text-secondary)");
        activeLabel.getStyle().set("letter-spacing", "0.05em");
        activeLabel.getStyle().set("margin-right", "12px");

        // Strategy checkbox group
        strategySelector = new CheckboxGroup<>();
        List<String> strategyNames = IntStream.range(0, availableStrategies.size())
                .mapToObj(i -> {
                    OptionsConfig config = availableStrategies.get(i);
                    // Use shortened names if possible or full names styling
                    return config.getName();
                })
                .collect(Collectors.toList());
        strategySelector.setItems(strategyNames);
        strategySelector.addClassName("strategy-selector");

        // Action Links
        selectAllButton = new Button("Select All", e -> strategySelector.setValue(new HashSet<>(strategyNames)));
        selectAllButton.addClassName("stitch-button-secondary");

        clearAllButton = new Button("Clear All", e -> strategySelector.clear());
        clearAllButton.addClassName("stitch-button-secondary");

        Div separator = new Div();
        separator.getStyle().set("width", "1px");
        separator.getStyle().set("height", "24px");
        separator.getStyle().set("background-color", "var(--stitch-border)");
        separator.getStyle().set("margin", "0 12px");

        // Assemble Bottom Row
        // We need the selector to flow, so maybe flex-wrap container
        Div selectorContainer = new Div();
        selectorContainer.addClassName("strategy-selector");
        selectorContainer.add(strategySelector);

        // This standard Vaadin CheckboxGroup creates its own layout.
        // We might just put the label, checkboxes, divider, and buttons in a big flex
        // layout.

        bottomRow.add(activeLabel, strategySelector, separator, selectAllButton, clearAllButton);
        // Note: CheckboxGroup in Vaadin is a block. We styled it to flex in CSS
        // (.strategy-selector)

        configPanel.add(topRow, bottomRow);

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
                        statusLabel.setText("Execution failed");
                        statusLabel.getStyle().set("color", "var(--lumo-error-text-color)");

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
                        statusLabel.setText("Execution completed");
                        statusLabel.getStyle().set("color", "var(--lumo-success-text-color)");

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
     * Updates the results display with execution results
     */
    private void updateResults(ExecutionResult result) {
        resultsContainer.removeAll();

        if (result.getResults().isEmpty()) {
            resultsContainer.add(new Span("No results yet. Execute strategies to see results."));
            return;
        }

        // Execution metadata
        Span timestamp = new Span("Executed at: " +
                result.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        timestamp.getStyle().set("font-weight", "bold");

        Span summary = new Span(String.format("Total: %d strategies, %d trades, %dms execution time",
                result.getResults().size(),
                result.getTotalTradesFound(),
                result.getTotalExecutionTimeMs()));

        resultsContainer.add(timestamp, summary);

        // Strategy results
        for (StrategyResult strategyResult : result.getResults()) {
            resultsContainer.add(createStrategyResultCard(strategyResult));
        }
    }

    /**
     * Creates a card for a single strategy result
     */
    private Component createStrategyResultCard(StrategyResult result) {
        Div card = new Div();
        card.addClassName("group"); // For CSS hovering/expansion
        card.addClassName("strategy-card");
        card.setWidthFull();

        // --- Header Section ---
        Div header = new Div();
        header.addClassName("p-4");
        header.addClassName("strategy-card-header");
        header.getStyle().set("display", "flex");
        header.getStyle().set("flex-wrap", "wrap");
        header.getStyle().set("justify-content", "space-between");
        header.getStyle().set("align-items", "center");
        header.getStyle().set("cursor", "pointer");
        header.getStyle().set("gap", "12px");

        // -- Left Side: Icon + Title + Params --
        HorizontalLayout leftSection = new HorizontalLayout();
        leftSection.setAlignItems(Alignment.CENTER);
        leftSection.setSpacing(true);

        // Expand/Collapse Icon
        com.vaadin.flow.component.icon.Icon arrowIcon = com.vaadin.flow.component.icon.VaadinIcon.CHEVRON_DOWN.create();
        arrowIcon.addClassName("rotate-icon");
        arrowIcon.getStyle().set("color", "var(--stitch-text-secondary)");

        // Strategy Info Container
        VerticalLayout infoLayout = new VerticalLayout();
        infoLayout.setSpacing(false);
        infoLayout.setPadding(false);

        // Title and Badge Row
        HorizontalLayout titleRow = new HorizontalLayout();
        titleRow.setAlignItems(Alignment.CENTER);

        H3 strategyName = new H3(result.getStrategyName());
        strategyName.getStyle().set("margin", "0");
        strategyName.getStyle().set("font-size", "1.125rem");
        strategyName.getStyle().set("color", "#fff");

        Span typeBadge = new Span("Standard");
        typeBadge.getElement().getThemeList().add("badge contrast");
        typeBadge.getStyle().set("font-size", "0.75rem");
        typeBadge.getStyle().set("margin-left", "8px");

        titleRow.add(strategyName, typeBadge);

        // Filter Params Subtext — read from DB-stored filter config
        String paramsText = getStrategyParamsFromJson(result.getFilterConfig());
        Span paramsSpan = new Span(paramsText);
        paramsSpan.addClassName("strategy-params-text");
        paramsSpan.getStyle().set("font-family", "monospace");
        paramsSpan.getStyle().set("font-size", "0.75rem");
        paramsSpan.getStyle().set("color", "var(--stitch-text-secondary)");
        paramsSpan.getStyle().set("opacity", "0.8");
        paramsSpan.getStyle().set("margin-top", "4px");
        paramsSpan.getStyle().set("white-space", "normal");
        paramsSpan.getStyle().set("word-break", "break-word");
        paramsSpan.getStyle().set("overflow-wrap", "break-word");
        paramsSpan.getStyle().set("width", "100%");
        paramsSpan.getStyle().set("max-width", "calc(100vw - 150px)"); // Responsive max-width to avoid breaking mobile

        infoLayout.add(titleRow, paramsSpan);
        leftSection.add(arrowIcon, infoLayout);

        // -- Right Side: Stats + Actions --
        HorizontalLayout rightSection = new HorizontalLayout();
        rightSection.setAlignItems(Alignment.CENTER);
        rightSection.setSpacing(true);

        // Stats Column (Matches Stitch: Last Run TOP, Trades Found BOTTOM)
        VerticalLayout statsLayout = new VerticalLayout();
        statsLayout.setSpacing(false);
        statsLayout.setPadding(false);
        statsLayout.setAlignItems(Alignment.END);

        // Last Run Time
        String timeAgo = "Just now";
        if (result.getUpdatedAt() != null) {
            java.time.Duration duration = java.time.Duration.between(result.getUpdatedAt(), java.time.Instant.now());
            long minutes = duration.toMinutes();
            long hours = duration.toHours();
            long days = duration.toDays();

            if (days > 0) {
                timeAgo = days + (days == 1 ? " day ago" : " days ago");
            } else if (hours > 0) {
                timeAgo = hours + (hours == 1 ? " hr ago" : " hrs ago");
            } else if (minutes > 0) {
                timeAgo = minutes + (minutes == 1 ? " min ago" : " mins ago");
            } else {
                timeAgo = "Just now";
            }
        }
        Span lastRunLabel = new Span("Last run: " + timeAgo);
        lastRunLabel.getStyle().set("font-size", "0.75rem");
        lastRunLabel.getStyle().set("color", "var(--stitch-text-secondary)");
        lastRunLabel.getStyle().set("text-align", "right");

        // Trades Found Count
        Span tradesFoundLabel = new Span("Trades found: " + result.getTradesFound());
        tradesFoundLabel.addClassName("grid-mono");
        tradesFoundLabel.getStyle().set("font-size", "0.75rem");
        tradesFoundLabel.getStyle().set("color", "var(--stitch-text-secondary)");
        tradesFoundLabel.getStyle().set("opacity", "0.5");
        tradesFoundLabel.getStyle().set("text-align", "right");

        statsLayout.add(lastRunLabel, tradesFoundLabel);

        // Divider
        Div divider = new Div();
        divider.getStyle().set("width", "1px");
        divider.getStyle().set("height", "32px");
        divider.getStyle().set("background-color", "var(--stitch-border)");
        divider.getStyle().set("margin", "0 12px");
        divider.addClassName("hidden");
        divider.addClassName("sm:block"); // Hide on small screens per Stitch

        // Download Button
        Button downloadBtn = new Button(com.vaadin.flow.component.icon.VaadinIcon.DOWNLOAD.create());
        downloadBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        downloadBtn.addClassName("stitch-icon-button");

        rightSection.add(statsLayout, divider, downloadBtn);

        header.add(leftSection, rightSection);

        // --- Content Section (Grid) ---
        Div contentContainer = new Div();
        contentContainer.addClassName("collapsible-content");

        boolean hasTrades = result.getTradesFound() > 0;
        Component grid = createTradeGrid(result.getTrades());
        contentContainer.add(grid);

        // Initial state: always collapsed
        contentContainer.getStyle().set("max-height", "0px");
        contentContainer.getStyle().set("overflow", "hidden");
        header.addClassName("collapsed");
        arrowIcon.getStyle().set("transform", "rotate(-90deg)");

        // Toggle Logic
        header.addClickListener(e -> {
            boolean isCollapsed = header.getClassNames().contains("collapsed");
            if (isCollapsed) {
                header.removeClassName("collapsed");
                contentContainer.getStyle().remove("max-height");
                contentContainer.getStyle().set("overflow", "visible");
                arrowIcon.getStyle().set("transform", "rotate(0deg)");
            } else {
                header.addClassName("collapsed");
                contentContainer.getStyle().set("overflow", "hidden");
                contentContainer.getStyle().set("max-height", "0px");
                arrowIcon.getStyle().set("transform", "rotate(-90deg)");
            }
        });

        card.add(header, contentContainer);
        return card;
    }

    /**
     * Extracts display parameters from a stored filter config JSON string.
     * Called with the filterConfig field from StrategyResult (persisted in
     * Supabase).
     *
     * @param filterConfigJson the raw JSON string of the filter, or null
     * @return human-readable parameter summary
     */
    private String getStrategyParamsFromJson(String filterConfigJson) {
        if (filterConfigJson == null || filterConfigJson.isEmpty()) {
            return "Filter data not available";
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(filterConfigJson);

            List<String> parts = new ArrayList<>();

            // DTE
            int targetDTE = root.path("targetDTE").asInt(0);
            int minDTE = root.path("minDTE").asInt(0);
            int maxDTE = root.path("maxDTE").asInt(Integer.MAX_VALUE);
            if (targetDTE > 0) {
                parts.add("DTE: " + targetDTE);
            } else if (minDTE > 0 || maxDTE < Integer.MAX_VALUE) {
                String max = maxDTE == Integer.MAX_VALUE ? "∞" : String.valueOf(maxDTE);
                parts.add("DTE: " + minDTE + "-" + max);
            }

            // Delta
            if (root.has("maxUpperBreakevenDelta") && !root.get("maxUpperBreakevenDelta").isNull()) {
                parts.add(String.format("Delta < %.2f", root.get("maxUpperBreakevenDelta").asDouble()));
            }

            // Max Loss
            if (root.has("maxLossLimit") && !root.get("maxLossLimit").isNull()) {
                parts.add(String.format("Max Loss: <$%.0f", root.get("maxLossLimit").asDouble()));
            }

            // Min RoR
            int minRoR = root.path("minReturnOnRisk").asInt(0);
            if (minRoR > 0) {
                parts.add("Min RoR: " + minRoR + "%");
            }

            // Min HV
            if (root.has("minHistoricalVolatility") && !root.get("minHistoricalVolatility").isNull()) {
                parts.add(String.format("Min HV: %.0f%%", root.get("minHistoricalVolatility").asDouble()));
            }

            // Additional Filters
            if (root.has("maxOptionPricePercent") && !root.get("maxOptionPricePercent").isNull()) {
                parts.add(String.format("Max Price %%: %.1f%%", root.get("maxOptionPricePercent").asDouble()));
            }

            if (root.has("priceVsMaxDebitRatio") && !root.get("priceVsMaxDebitRatio").isNull()) {
                parts.add(String.format("Price/Debit Ratio: %.1f", root.get("priceVsMaxDebitRatio").asDouble()));
            }

            if (root.has("maxTotalDebit") && !root.get("maxTotalDebit").isNull()) {
                parts.add(String.format("Max Debit: $%.0f", root.get("maxTotalDebit").asDouble()));
            }

            if (root.has("maxBreakEvenPercentage") && !root.get("maxBreakEvenPercentage").isNull()) {
                parts.add(String.format("Max B/E: %.1f%%", root.get("maxBreakEvenPercentage").asDouble()));
            }

            if (root.has("maxNetExtrinsicValueToPricePercentage")
                    && !root.get("maxNetExtrinsicValueToPricePercentage").isNull()) {
                parts.add(String.format("Max Extrinsic: %.1f%%",
                        root.get("maxNetExtrinsicValueToPricePercentage").asDouble()));
            }

            if (root.has("minNetExtrinsicValueToPricePercentage")
                    && !root.get("minNetExtrinsicValueToPricePercentage").isNull()) {
                parts.add(String.format("Min Extrinsic: %.1f%%",
                        root.get("minNetExtrinsicValueToPricePercentage").asDouble()));
            }

            // LEAP-specific
            if (root.has("maxCAGRForBreakEven") && !root.get("maxCAGRForBreakEven").isNull()) {
                parts.add(String.format("Max CAGR B/E: %.1f%%", root.get("maxCAGRForBreakEven").asDouble()));
            }

            if (root.has("minCostSavingsPercent") && !root.get("minCostSavingsPercent").isNull()) {
                parts.add(String.format("Min Savings: %.1f%%", root.get("minCostSavingsPercent").asDouble()));
            }

            return parts.isEmpty() ? "No specific filters configured" : String.join(", ", parts);

        } catch (Exception e) {
            log.warn("Failed to parse filter config JSON: {}", e.getMessage());
            return "Filter data not available";
        }
    }

    /**
     * Creates a grid to display trades
     */
    private Component createTradeGrid(List<Trade> trades) {
        if (trades.isEmpty()) {
            return createEmptyState();
        }

        Grid<Trade> grid = new Grid<>(Trade.class, false);
        grid.setItems(trades);
        grid.setAllRowsVisible(true);

        // Common columns
        grid.addColumn(Trade::getSymbol)
                .setHeader("TICKER")
                .setSortable(true)
                .setWidth("140px")
                .setFlexGrow(0)
                .setClassNameGenerator(item -> "grid-ticker");

        // Type + Strikes (merged: each leg on a new line with strike)
        grid.addColumn(new com.vaadin.flow.data.renderer.ComponentRenderer<>(trade -> {
            Div container = new Div();
            if (trade.getLegs() != null && !trade.getLegs().isEmpty()) {
                for (var leg : trade.getLegs()) {
                    Span line = new Span(leg.getAction() + " " + leg.getOptionType()
                            + " " + String.format("%.0f", leg.getStrike())
                            + " → $" + String.format("%.2f", leg.getPremium()));
                    line.getStyle().set("display", "block");
                    line.getStyle().set("font-size", "0.8rem");
                    container.add(line);
                }
            } else {
                container.add(new Span("—"));
            }
            if (trade.getNetExtrinsicValue() != 0) {
                Span extrinsicLine = new Span("Extrinsic: $" + String.format("%.2f", trade.getNetExtrinsicValue())
                        + " (" + String.format("%.2f", trade.getNetExtrinsicValueToPricePercentage()) + "%)");
                extrinsicLine.getStyle().set("display", "block");
                extrinsicLine.getStyle().set("font-size", "0.75rem");
                extrinsicLine.getStyle().set("color", "var(--lumo-secondary-text-color)");
                container.add(extrinsicLine);
            }
            container.getStyle().set("white-space", "normal");
            container.getStyle().set("line-height", "1.5");
            return container;
        })).setHeader("TYPE").setWidth("220px").setFlexGrow(1);

        grid.addColumn(trade -> {
            String expiry = trade.getExpiryDate();
            if (expiry != null && expiry.length() > 10) {
                expiry = expiry.substring(0, 10);
            }
            return expiry + " (" + trade.getDte() + ")";
        }).setHeader("EXPIRY").setSortable(true).setAutoWidth(true).setFlexGrow(0)
                .setClassNameGenerator(item -> "grid-mono");

        // Credit/Debit - Colored
        grid.addColumn(new com.vaadin.flow.data.renderer.ComponentRenderer<>(trade -> {
            boolean isCredit = trade.getNetCredit() >= 0;
            String amount = String.format("$%.2f", Math.abs(trade.getNetCredit()));
            Span span = new Span(amount);
            span.addClassName(isCredit ? "grid-success" : "grid-danger");
            span.addClassName("grid-mono");
            return span;
        })).setHeader(new Span("CREDIT/DEBIT")).setSortable(true).setAutoWidth(true).setFlexGrow(0);

        // Max Profit/Loss
        grid.addColumn(trade -> String.format("$%.0f", trade.getMaxLoss()))
                .setHeader("MAX LOSS").setSortable(true).setAutoWidth(true).setFlexGrow(0)
                .setClassNameGenerator(item -> "grid-mono");

        // BreakEven
        grid.addColumn(new com.vaadin.flow.data.renderer.ComponentRenderer<>(trade -> {
            Div container = new Div();
            // Line 1: BE price (percent)
            Span beLine = new Span(
                    String.format("$%.2f (%.2f%%)", trade.getBreakEvenPrice(), trade.getBreakEvenPercent()));
            beLine.getStyle().set("display", "block");
            beLine.getStyle().set("font-size", "0.8rem");
            container.add(beLine);

            // Line 2: Upper BE (only for BWB/Iron Condor)
            double upperBE = trade.getUpperBreakEvenPrice();
            if (upperBE > 0 && Math.abs(upperBE - trade.getBreakEvenPrice()) > 0.01) {
                Span upperLine = new Span(
                        String.format("Upper: $%.2f (%.2f%%)", upperBE, trade.getUpperBreakEvenPercent()));
                upperLine.getStyle().set("display", "block");
                upperLine.getStyle().set("font-size", "0.75rem");
                upperLine.getStyle().set("color", "var(--lumo-secondary-text-color)");
                container.add(upperLine);
            }
            container.getStyle().set("line-height", "1.5");
            return container;
        })).setHeader("BREAKEVEN").setAutoWidth(true).setFlexGrow(0);

        // POP % (mapped to RoR for now as we don't have POP)
        grid.addColumn(new com.vaadin.flow.data.renderer.ComponentRenderer<>(trade -> {
            double ror = trade.getReturnOnRisk();
            Span span = new Span(String.format("%.1f%%", ror));
            span.addClassName(ror > 50 ? "grid-success" : "grid-warning");

            // Simple progress bar visual
            Div barBg = new Div();
            barBg.getStyle().set("width", "60px");
            barBg.getStyle().set("height", "6px");
            barBg.getStyle().set("background-color", "rgba(255,255,255,0.1)");
            barBg.getStyle().set("border-radius", "3px");
            barBg.getStyle().set("margin-left", "10px");
            barBg.getStyle().set("overflow", "hidden");

            Div barFill = new Div();
            barFill.getStyle().set("height", "100%");
            barFill.getStyle().set("width", Math.min(ror, 100) + "%");
            barFill.getStyle().set("background-color", ror > 20 ? "var(--stitch-success)" : "var(--stitch-warning)");

            barBg.add(barFill);

            HorizontalLayout hl = new HorizontalLayout(span, barBg);
            hl.setAlignItems(Alignment.CENTER);
            return hl;
        })).setHeader("ROR %").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        // Styling
        grid.addThemeVariants(
                com.vaadin.flow.component.grid.GridVariant.LUMO_NO_BORDER,
                com.vaadin.flow.component.grid.GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setWidthFull();

        grid.addItemClickListener(e -> {
            Trade clicked = e.getItem();

            com.vaadin.flow.component.dialog.Dialog dialog = new com.vaadin.flow.component.dialog.Dialog();

            Span header = new Span("▶ " + clicked.getSymbol() + " — Trade Details");
            header.getStyle().set("font-weight", "bold");
            header.getStyle().set("font-family", "var(--lumo-font-family)");
            header.getStyle().set("font-size", "1.1rem");
            header.getStyle().set("display", "block");
            header.getStyle().set("margin-bottom", "12px");
            header.getStyle().set("color", "var(--stitch-primary)");

            Div detailsText = new Div();
            detailsText.setText(clicked.getTradeDetails() != null ? clicked.getTradeDetails() : "No details available");
            detailsText.getStyle().set("white-space", "pre-wrap");
            detailsText.getStyle().set("word-break", "break-word");
            detailsText.getStyle().set("overflow-wrap", "break-word");
            detailsText.getStyle().set("font-family", "monospace");
            detailsText.getStyle().set("color", "var(--lumo-body-text-color)");
            detailsText.getStyle().set("line-height", "1.6");

            com.vaadin.flow.component.button.Button closeBtn = new com.vaadin.flow.component.button.Button("Close",
                    ev -> dialog.close());
            closeBtn.getStyle().set("margin-top", "16px");

            VerticalLayout layout = new VerticalLayout(header, detailsText, closeBtn);
            layout.setPadding(true);
            layout.setSpacing(false);

            dialog.addOpenedChangeListener(event -> {
                if (!event.isOpened()) {
                    grid.deselectAll();
                }
            });

            dialog.add(layout);
            dialog.setWidth("100%");
            dialog.setMaxWidth("600px");
            dialog.open();
        });

        grid.setSelectionMode(Grid.SelectionMode.SINGLE);

        VerticalLayout wrapper = new VerticalLayout(grid);
        wrapper.setPadding(false);
        wrapper.setSpacing(false);
        wrapper.setWidthFull();

        return wrapper;
    }

    /**
     * Creates the custom empty state component
     */
    private Component createEmptyState() {
        Div container = new Div();
        container.addClassName("empty-state-container");

        Div iconWrapper = new Div();
        iconWrapper.addClassName("empty-state-icon-wrapper");

        com.vaadin.flow.component.icon.Icon icon = com.vaadin.flow.component.icon.VaadinIcon.SEARCH.create();
        icon.addClassName("empty-state-icon");
        iconWrapper.add(icon);

        H3 title = new H3("No trades found matching this strategy's criteria");
        title.addClassName("empty-state-title");

        Span description = new Span(
                "No opportunities were identified for the current market session. You may want to check other strategies or adjust scan parameters.");
        description.addClassName("empty-state-description");

        container.add(iconWrapper, title, description);
        return container;
    }

    /**
     * Loads all latest strategy results from Supabase (per-strategy persistence)
     */

    private void loadLatestResults() {
        try {
            List<StrategyResult> allResults = strategyService.getAllLatestStrategyResults();

            if (!allResults.isEmpty()) {
                // Display all results
                displayAllStrategyResults(allResults);
                statusLabel.setText(String.format("Loaded %d strategy results", allResults.size()));
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

    /**
     * Displays all strategy results (used for initial load)
     */
    private void displayAllStrategyResults(List<StrategyResult> results) {
        resultsContainer.removeAll();

        // Display each strategy result
        for (StrategyResult strategyResult : results) {
            resultsContainer.add(createStrategyResultCard(strategyResult));
        }
    }
}
