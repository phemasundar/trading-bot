package com.hemasundar.ui.views;

import com.hemasundar.dto.ExecutionResult;
import com.hemasundar.dto.StrategyResult;
import com.hemasundar.dto.Trade;
import com.hemasundar.options.models.*;
import com.hemasundar.options.strategies.StrategyType;
import com.hemasundar.services.StrategyExecutionService;
import com.hemasundar.ui.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.Lumo;
import lombok.extern.log4j.Log4j2;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Route(value = "execute", layout = MainLayout.class)
@PageTitle("Execute Strategy")
@Log4j2
public class ExecuteStrategyView extends VerticalLayout {

    private final StrategyExecutionService executionService;

    // Core Config Inputs
    private TextField securitiesField;
    private TextField aliasField;
    private ComboBox<StrategyType> strategyTypeComboBox;
    private IntegerField maxTradesToSend;

    // Common Filters
    private IntegerField targetDTE;
    private IntegerField minDTE;
    private IntegerField maxDTE;
    private NumberField maxLossLimit;
    private NumberField minReturnOnRisk;
    private NumberField minHistoricalVolatility;
    private NumberField maxBreakEvenPercentage;
    private NumberField maxUpperBreakevenDelta;
    private NumberField maxNetExtrinsicValueToPricePercentage;
    private Checkbox ignoreEarnings;

    // Dynamic Form Container
    private VerticalLayout specificFieldsContainer;

    // UI State for Progress
    private Div customProgressBar;
    private Div progressFill;
    private Span statusText;

    // Specific filter state fields
    private Object specificBuilder;

    // Results display
    private Div resultsContainer;

    public ExecuteStrategyView(StrategyExecutionService executionService) {
        this.executionService = executionService;

        // Match dashboard theme
        getElement().getThemeList().add(Lumo.DARK);

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        addClassName("main-layout");

        // ── Strategy Configuration Panel (matches dashboard style) ──
        add(createConfigPanel());
        add(createCommonFiltersPanel());
        add(createSpecificFiltersPanel());
        add(createExecutionPanel());
        add(createResultsPanel());

        // Set default AFTER all panels exist (setValue fires the listener which needs
        // specificFieldsContainer)
        strategyTypeComboBox.setValue(StrategyType.PUT_CREDIT_SPREAD);

        // Load recent results on startup
        loadRecentExecutions();
    }

    /**
     * Creates the top configuration panel matching the dashboard's
     * strategy-config-panel style.
     */
    private VerticalLayout createConfigPanel() {
        VerticalLayout configPanel = new VerticalLayout();
        configPanel.addClassName("strategy-config-panel");
        configPanel.setSpacing(true);
        configPanel.setPadding(true);
        configPanel.setWidthFull();

        // Top Row: Title + Execute Button (same layout as dashboard)
        HorizontalLayout topRow = new HorizontalLayout();
        topRow.setWidthFull();
        topRow.setAlignItems(FlexComponent.Alignment.CENTER);
        topRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        VerticalLayout headerText = new VerticalLayout();
        headerText.setSpacing(false);
        headerText.setPadding(false);

        H2 title = new H2("Execute Custom Strategy");
        title.addClassName("section-title");
        Span subtitle = new Span("Select a strategy, configure filters, and execute against custom securities.");
        subtitle.addClassName("section-subtitle");
        headerText.add(title, subtitle);

        Button executeButton = new Button("Execute Strategy");
        executeButton.setIcon(VaadinIcon.BOLT.create());
        executeButton.addClassName("stitch-button-primary");
        executeButton.addClickListener(e -> executeStrategy());

        topRow.add(headerText, executeButton);

        // Strategy type + alias + securities row
        FormLayout fieldsRow = new FormLayout();
        fieldsRow.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2),
                new FormLayout.ResponsiveStep("900px", 4));

        strategyTypeComboBox = new ComboBox<>("Strategy Type", StrategyType.values());
        strategyTypeComboBox.setRequiredIndicatorVisible(true);
        aliasField = new TextField("Strategy Alias");
        aliasField.setPlaceholder("e.g., Custom PCS");
        securitiesField = new TextField("Securities");
        securitiesField.setPlaceholder("e.g., AAPL, TSLA, MSFT");
        securitiesField.setRequiredIndicatorVisible(true);
        maxTradesToSend = new IntegerField("Max Trades To Send");
        maxTradesToSend.setValue(30);

        fieldsRow.add(strategyTypeComboBox, aliasField, securitiesField, maxTradesToSend);

        strategyTypeComboBox.addValueChangeListener(e -> renderSpecificFields(e.getValue()));
        // NOTE: setValue is deferred to the constructor to avoid NPE
        // (specificFieldsContainer not yet created)

        configPanel.add(topRow, fieldsRow);
        return configPanel;
    }

    /**
     * Creates the common filters section panel.
     */
    private VerticalLayout createCommonFiltersPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.addClassName("strategy-config-panel");
        panel.setSpacing(true);
        panel.setPadding(true);
        panel.setWidthFull();

        H3 sectionTitle = new H3("Common Filters");
        sectionTitle.addClassName("section-title");
        sectionTitle.getStyle().set("margin-top", "0");

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2),
                new FormLayout.ResponsiveStep("900px", 3),
                new FormLayout.ResponsiveStep("1200px", 5));

        targetDTE = new IntegerField("Target DTE");
        minDTE = new IntegerField("Min DTE");
        maxDTE = new IntegerField("Max DTE");
        maxLossLimit = new NumberField("Max Loss Limit");
        minReturnOnRisk = new NumberField("Min Return On Risk");
        minHistoricalVolatility = new NumberField("Min Historical Volatility");
        maxBreakEvenPercentage = new NumberField("Max Break Even %");
        maxUpperBreakevenDelta = new NumberField("Max Upper Breakeven Delta");
        maxNetExtrinsicValueToPricePercentage = new NumberField("Max Net Extrinsic/Price %");
        ignoreEarnings = new Checkbox("Ignore Earnings");

        form.add(targetDTE, minDTE, maxDTE, maxLossLimit,
                minReturnOnRisk, minHistoricalVolatility, maxBreakEvenPercentage,
                maxUpperBreakevenDelta, maxNetExtrinsicValueToPricePercentage, ignoreEarnings);

        panel.add(sectionTitle, form);
        return panel;
    }

    /**
     * Creates the strategy-specific filters section panel.
     */
    private VerticalLayout createSpecificFiltersPanel() {
        specificFieldsContainer = new VerticalLayout();
        specificFieldsContainer.addClassName("strategy-config-panel");
        specificFieldsContainer.setSpacing(true);
        specificFieldsContainer.setPadding(true);
        specificFieldsContainer.setWidthFull();
        return specificFieldsContainer;
    }

    /**
     * Creates the execution progress panel (matches dashboard scan-status style).
     */
    private Div createExecutionPanel() {
        Div progressContainer = new Div();
        progressContainer.addClassName("scan-status-container");
        progressContainer.setVisible(false);
        progressContainer.setWidthFull();

        // Progress Bar Background & Fill
        Div progressBg = new Div();
        progressBg.addClassName("scan-progress-bar-bg");

        progressFill = new Div();
        progressFill.addClassName("scan-progress-bar-fill");
        progressFill.getStyle().set("width", "0%");

        Div progressGlow = new Div();
        progressGlow.addClassName("scan-progress-bar-glow");
        progressFill.add(progressGlow);

        progressBg.add(progressFill);

        // Content (Text & Indicators)
        Div content = new Div();
        content.addClassName("scan-status-content");

        HorizontalLayout left = new HorizontalLayout();
        left.setAlignItems(FlexComponent.Alignment.CENTER);
        left.setSpacing(true);

        Div pulsingDot = new Div();
        pulsingDot.addClassName("pulsing-dot");

        statusText = new Span("Executing strategy...");
        statusText.addClassName("scan-status-text");
        statusText.getStyle().set("color", "var(--stitch-primary)");
        statusText.getStyle().set("font-weight", "500");

        left.add(pulsingDot, statusText);
        content.add(left);
        progressContainer.add(content, progressBg);

        this.customProgressBar = progressContainer;

        return progressContainer;
    }

    private void renderSpecificFields(StrategyType type) {
        specificFieldsContainer.removeAll();
        if (type == null)
            return;

        H3 sectionTitle = new H3("Strategy-Specific Filters: " + type);
        sectionTitle.addClassName("section-title");
        sectionTitle.getStyle().set("margin-top", "0");

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2),
                new FormLayout.ResponsiveStep("900px", 3));
        switch (type) {
            case PUT_CREDIT_SPREAD:
            case BULLISH_LONG_PUT_CREDIT_SPREAD:
            case TECH_PUT_CREDIT_SPREAD:
            case CALL_CREDIT_SPREAD:
            case TECH_CALL_CREDIT_SPREAD:
                CreditSpreadBuilder csb = new CreditSpreadBuilder();
                csb.buildUI(form);
                specificBuilder = csb;
                break;
            case IRON_CONDOR:
            case BULLISH_LONG_IRON_CONDOR:
                IronCondorBuilder icb = new IronCondorBuilder();
                icb.buildUI(form);
                specificBuilder = icb;
                break;
            case LONG_CALL_LEAP:
            case LONG_CALL_LEAP_TOP_N:
                LongCallLeapBuilder lcb = new LongCallLeapBuilder();
                lcb.buildUI(form);
                specificBuilder = lcb;
                break;
            case BULLISH_BROKEN_WING_BUTTERFLY:
                BrokenWingButterflyBuilder bwb = new BrokenWingButterflyBuilder();
                NumberField maxTotalDebit = new NumberField("Max Total Debit");
                NumberField priceVsMaxDebitRatio = new NumberField("Price vs Max Debit Ratio");
                form.add(maxTotalDebit, priceVsMaxDebitRatio);
                bwb.maxTotalDebit = maxTotalDebit;
                bwb.priceVsMaxDebitRatio = priceVsMaxDebitRatio;
                bwb.buildUI(form);
                specificBuilder = bwb;
                break;
            case BULLISH_ZEBRA:
                ZebraBuilder zb = new ZebraBuilder();
                zb.buildUI(form);
                specificBuilder = zb;
                break;
        }
        specificFieldsContainer.add(sectionTitle, form);
    }

    private void executeStrategy() {
        // Clear previous inline errors
        strategyTypeComboBox.setInvalid(false);
        securitiesField.setInvalid(false);

        boolean hasErrors = false;

        if (strategyTypeComboBox.getValue() == null) {
            strategyTypeComboBox.setErrorMessage("Strategy Type is required");
            strategyTypeComboBox.setInvalid(true);
            hasErrors = true;
        }
        if (securitiesField.getValue() == null || securitiesField.getValue().trim().isEmpty()) {
            securitiesField.setErrorMessage("At least one security is required");
            securitiesField.setInvalid(true);
            hasErrors = true;
        }

        if (hasErrors)
            return;

        customProgressBar.setVisible(true);
        progressFill.getStyle().set("width", "5%");
        statusText.setText("Executing strategy...");

        // Parse securities
        List<String> symbols = Arrays.stream(securitiesField.getValue().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .collect(Collectors.toList());

        OptionsStrategyFilter filter = buildFilter(strategyTypeComboBox.getValue());

        OptionsConfig config = OptionsConfig.builder()
                .alias(aliasField.getValue())
                .strategy(strategyTypeComboBox.getValue().createStrategy())
                .securities(symbols)
                .maxTradesToSend(maxTradesToSend.getValue() != null ? maxTradesToSend.getValue() : 30)
                .filter(filter)
                .build();

        // Populate common fields
        if (targetDTE.getValue() != null)
            filter.setTargetDTE(targetDTE.getValue());
        if (minDTE.getValue() != null)
            filter.setMinDTE(minDTE.getValue());
        if (maxDTE.getValue() != null)
            filter.setMaxDTE(maxDTE.getValue());
        if (maxLossLimit.getValue() != null)
            filter.setMaxLossLimit(maxLossLimit.getValue());
        if (minReturnOnRisk.getValue() != null)
            filter.setMinReturnOnRisk(minReturnOnRisk.getValue().intValue());
        if (minHistoricalVolatility.getValue() != null)
            filter.setMinHistoricalVolatility(minHistoricalVolatility.getValue());
        if (maxBreakEvenPercentage.getValue() != null)
            filter.setMaxBreakEvenPercentage(maxBreakEvenPercentage.getValue());
        if (maxUpperBreakevenDelta.getValue() != null)
            filter.setMaxUpperBreakevenDelta(maxUpperBreakevenDelta.getValue());
        if (maxNetExtrinsicValueToPricePercentage.getValue() != null)
            filter.setMaxNetExtrinsicValueToPricePercentage(maxNetExtrinsicValueToPricePercentage.getValue());
        filter.setIgnoreEarnings(ignoreEarnings.getValue() != null && ignoreEarnings.getValue());

        // filter is injected via builder earlier

        UI ui = UI.getCurrent();
        new Thread(() -> {
            try {
                ExecutionResult result = executionService.executeCustomStrategy(config);
                ui.access(() -> {
                    customProgressBar.setVisible(false);
                    statusText.setText("Execution completed.");
                    loadRecentExecutions(); // Refresh results list
                    Notification
                            .show("Success: Found " + result.getTotalTradesFound() + " trades in "
                                    + result.getTotalExecutionTimeMs() + "ms", 5000, Notification.Position.BOTTOM_END)
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                });
            } catch (Exception ex) {
                log.error("Execution failed", ex);
                ui.access(() -> {
                    customProgressBar.setVisible(false);
                    statusText.setText("Execution failed.");
                    Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                });
            }
        }).start();
    }

    private OptionsStrategyFilter buildFilter(StrategyType type) {
        switch (type) {
            case PUT_CREDIT_SPREAD:
            case BULLISH_LONG_PUT_CREDIT_SPREAD:
            case TECH_PUT_CREDIT_SPREAD:
            case CALL_CREDIT_SPREAD:
            case TECH_CALL_CREDIT_SPREAD:
                CreditSpreadFilter csf = new CreditSpreadFilter();
                ((CreditSpreadBuilder) specificBuilder).apply(csf);
                return csf;
            case IRON_CONDOR:
            case BULLISH_LONG_IRON_CONDOR:
                IronCondorFilter icf = new IronCondorFilter();
                ((IronCondorBuilder) specificBuilder).apply(icf);
                return icf;
            case LONG_CALL_LEAP:
            case LONG_CALL_LEAP_TOP_N:
                LongCallLeapFilter lcf = new LongCallLeapFilter();
                ((LongCallLeapBuilder) specificBuilder).apply(lcf);
                return lcf;
            case BULLISH_BROKEN_WING_BUTTERFLY:
                BrokenWingButterflyFilter bwbf = new BrokenWingButterflyFilter();
                BrokenWingButterflyBuilder bwbBuilder = (BrokenWingButterflyBuilder) specificBuilder;
                if (bwbBuilder.maxTotalDebit.getValue() != null)
                    bwbf.setMaxTotalDebit(bwbBuilder.maxTotalDebit.getValue());
                if (bwbBuilder.priceVsMaxDebitRatio.getValue() != null)
                    bwbf.setPriceVsMaxDebitRatio(bwbBuilder.priceVsMaxDebitRatio.getValue());
                bwbBuilder.apply(bwbf);
                return bwbf;
            case BULLISH_ZEBRA:
                ZebraFilter zf = new ZebraFilter();
                ((ZebraBuilder) specificBuilder).apply(zf);
                return zf;
            default:
                return new OptionsStrategyFilter();
        }
    }

    // Helper builders for inner leg fields
    // ==================== Results Display ====================

    /**
     * Creates the "Recent Executions" results panel.
     */
    private VerticalLayout createResultsPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setSpacing(true);
        panel.setPadding(true);
        panel.setWidthFull();

        HorizontalLayout headerLayout = new HorizontalLayout();
        headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        headerLayout.setSpacing(true);

        H3 header = new H3("Recent Executions");
        header.addClassName("section-title");
        header.getStyle().set("margin", "0");

        Span badge = new Span("Last 20");
        badge.getElement().getThemeList().add("badge");
        badge.getStyle().set("font-size", "0.75rem");
        badge.getStyle().set("border", "1px solid var(--stitch-border)");

        headerLayout.add(header, badge);

        resultsContainer = new Div();
        resultsContainer.addClassName("results-container");
        resultsContainer.setWidthFull();

        panel.add(headerLayout, resultsContainer);
        return panel;
    }

    /**
     * Loads the most recent custom execution results from Supabase.
     */
    private void loadRecentExecutions() {
        try {
            List<StrategyResult> results = executionService.getRecentCustomExecutions(20);
            resultsContainer.removeAll();
            if (results.isEmpty()) {
                Div emptyState = new Div();
                emptyState.addClassName("empty-state-container");
                H3 emptyTitle = new H3("No custom executions yet");
                emptyTitle.addClassName("empty-state-title");
                Span emptyDesc = new Span("Execute a strategy above to see results here.");
                emptyDesc.addClassName("empty-state-description");
                emptyState.add(emptyTitle, emptyDesc);
                resultsContainer.add(emptyState);
            } else {
                for (StrategyResult result : results) {
                    resultsContainer.add(createResultCard(result));
                }
            }
        } catch (Exception e) {
            log.error("Failed to load recent executions: {}", e.getMessage());
            resultsContainer.removeAll();
            Span errorSpan = new Span("Failed to load recent executions: " + e.getMessage());
            errorSpan.getStyle().set("color", "var(--lumo-error-color)");
            resultsContainer.add(errorSpan);
        }
    }

    /**
     * Creates a result card for a single execution — reuses the same CSS classes
     * and layout structure as the dashboard's strategy result cards.
     */
    private Component createResultCard(StrategyResult result) {
        Div card = new Div();
        card.addClassName("group");
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

        // Left: Icon + Title
        HorizontalLayout leftSection = new HorizontalLayout();
        leftSection.setAlignItems(FlexComponent.Alignment.CENTER);
        leftSection.setSpacing(true);

        com.vaadin.flow.component.icon.Icon arrowIcon = VaadinIcon.CHEVRON_DOWN.create();
        arrowIcon.addClassName("rotate-icon");
        arrowIcon.getStyle().set("color", "var(--stitch-text-secondary)");

        VerticalLayout infoLayout = new VerticalLayout();
        infoLayout.setSpacing(false);
        infoLayout.setPadding(false);

        HorizontalLayout titleRow = new HorizontalLayout();
        titleRow.setAlignItems(FlexComponent.Alignment.CENTER);

        H3 strategyName = new H3(result.getStrategyName());
        strategyName.getStyle().set("margin", "0");
        strategyName.getStyle().set("font-size", "1.125rem");
        strategyName.getStyle().set("color", "#fff");

        Span typeBadge = new Span("Custom");
        typeBadge.getElement().getThemeList().add("badge contrast");
        typeBadge.getStyle().set("font-size", "0.75rem");
        typeBadge.getStyle().set("margin-left", "8px");

        titleRow.add(strategyName, typeBadge);
        infoLayout.add(titleRow);
        leftSection.add(arrowIcon, infoLayout);

        // Right: Stats
        HorizontalLayout rightSection = new HorizontalLayout();
        rightSection.setAlignItems(FlexComponent.Alignment.CENTER);
        rightSection.setSpacing(true);

        VerticalLayout statsLayout = new VerticalLayout();
        statsLayout.setSpacing(false);
        statsLayout.setPadding(false);
        statsLayout.setAlignItems(FlexComponent.Alignment.END);

        String timeAgo = "Just now";
        if (result.getUpdatedAt() != null) {
            java.time.Duration duration = java.time.Duration.between(result.getUpdatedAt(), java.time.Instant.now());
            long minutes = duration.toMinutes();
            long hours = duration.toHours();
            long days = duration.toDays();
            if (days > 0)
                timeAgo = days + (days == 1 ? " day ago" : " days ago");
            else if (hours > 0)
                timeAgo = hours + (hours == 1 ? " hr ago" : " hrs ago");
            else if (minutes > 0)
                timeAgo = minutes + (minutes == 1 ? " min ago" : " mins ago");
        }

        Span lastRunLabel = new Span("Executed: " + timeAgo);
        lastRunLabel.getStyle().set("font-size", "0.75rem");
        lastRunLabel.getStyle().set("color", "var(--stitch-text-secondary)");
        lastRunLabel.getStyle().set("text-align", "right");

        Span tradesFoundLabel = new Span("Trades found: " + result.getTradesFound());
        tradesFoundLabel.addClassName("grid-mono");
        tradesFoundLabel.getStyle().set("font-size", "0.75rem");
        tradesFoundLabel.getStyle().set("color", "var(--stitch-text-secondary)");
        tradesFoundLabel.getStyle().set("text-align", "right");

        statsLayout.add(lastRunLabel, tradesFoundLabel);
        rightSection.add(statsLayout);

        header.add(leftSection, rightSection);

        // --- Content Section (Grid) ---
        Div contentContainer = new Div();
        contentContainer.addClassName("collapsible-content");

        Component grid = createTradeGrid(result.getTrades());
        contentContainer.add(grid);

        // Initial state: collapsed
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
     * Creates a trade grid — same column structure as the dashboard.
     */
    private Component createTradeGrid(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            Div empty = new Div();
            empty.addClassName("empty-state-container");
            H3 title = new H3("No trades found");
            title.addClassName("empty-state-title");
            empty.add(title);
            return empty;
        }

        Grid<Trade> grid = new Grid<>(Trade.class, false);
        grid.setItems(trades);
        grid.setAllRowsVisible(true);

        grid.addColumn(Trade::getSymbol)
                .setHeader("TICKER")
                .setSortable(true)
                .setWidth("140px")
                .setFlexGrow(0)
                .setClassNameGenerator(item -> "grid-ticker");

        grid.addColumn(new com.vaadin.flow.data.renderer.ComponentRenderer<>(trade -> {
            Div container = new Div();
            if (trade.getLegs() != null && !trade.getLegs().isEmpty()) {
                for (var leg : trade.getLegs()) {
                    Span line = new Span(leg.getAction() + " " + leg.getOptionType()
                            + " " + String.format("%.0f", leg.getStrike())
                            + " \u2192 $" + String.format("%.2f", leg.getPremium()));
                    line.getStyle().set("display", "block");
                    line.getStyle().set("font-size", "0.8rem");
                    container.add(line);
                }
            } else {
                container.add(new Span("\u2014"));
            }

            if (trade.getNetExtrinsicValue() != 0) {
                Span extrinsicLine = new Span("Extrinsic: $" + String.format("%.2f", trade.getNetExtrinsicValue())
                        + " (" + String.format("%.2f", trade.getAnulizedNetExtrinsicValueToCapitalPercentage()) + "%)");
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
            if (expiry != null && expiry.length() > 10)
                expiry = expiry.substring(0, 10);
            return expiry + " (" + trade.getDte() + ")";
        }).setHeader("EXPIRY").setSortable(true).setAutoWidth(true).setFlexGrow(0)
                .setClassNameGenerator(item -> "grid-mono");

        grid.addColumn(new com.vaadin.flow.data.renderer.ComponentRenderer<>(trade -> {
            boolean isCredit = trade.getNetCredit() >= 0;
            String amount = String.format("$%.2f", Math.abs(trade.getNetCredit()));
            Span span = new Span(amount);
            span.addClassName(isCredit ? "grid-success" : "grid-danger");
            span.addClassName("grid-mono");
            return span;
        })).setHeader(new Span("CREDIT/DEBIT")).setSortable(true).setAutoWidth(true).setFlexGrow(0);

        grid.addColumn(trade -> String.format("$%.0f", trade.getMaxLoss()))
                .setHeader("MAX LOSS").setSortable(true).setAutoWidth(true).setFlexGrow(0)
                .setClassNameGenerator(item -> "grid-mono");

        grid.addColumn(new com.vaadin.flow.data.renderer.ComponentRenderer<>(trade -> {
            double ror = trade.getReturnOnRisk();
            Span span = new Span(String.format("%.1f%%", ror));
            span.addClassName(ror > 50 ? "grid-success" : "grid-warning");
            return span;
        })).setHeader("ROR %").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        grid.addThemeVariants(
                com.vaadin.flow.component.grid.GridVariant.LUMO_NO_BORDER,
                com.vaadin.flow.component.grid.GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setWidthFull();

        // Click-to-show trade details dialog (same as dashboard)
        grid.addItemClickListener(e -> {
            Trade clicked = e.getItem();

            com.vaadin.flow.component.dialog.Dialog dialog = new com.vaadin.flow.component.dialog.Dialog();

            Span dialogHeader = new Span("\u25B6 " + clicked.getSymbol() + " \u2014 Trade Details");
            dialogHeader.getStyle().set("font-weight", "bold");
            dialogHeader.getStyle().set("font-family", "var(--lumo-font-family)");
            dialogHeader.getStyle().set("font-size", "1.1rem");
            dialogHeader.getStyle().set("display", "block");
            dialogHeader.getStyle().set("margin-bottom", "12px");
            dialogHeader.getStyle().set("color", "var(--stitch-primary)");

            Div detailsText = new Div();
            detailsText.setText(clicked.getTradeDetails() != null ? clicked.getTradeDetails() : "No details available");
            detailsText.getStyle().set("white-space", "pre-wrap");
            detailsText.getStyle().set("word-break", "break-word");
            detailsText.getStyle().set("overflow-wrap", "break-word");
            detailsText.getStyle().set("font-family", "monospace");
            detailsText.getStyle().set("color", "var(--lumo-body-text-color)");
            detailsText.getStyle().set("line-height", "1.6");

            Button closeBtn = new Button("Close", ev -> dialog.close());
            closeBtn.getStyle().set("margin-top", "16px");

            VerticalLayout dialogLayout = new VerticalLayout(dialogHeader, detailsText, closeBtn);
            dialogLayout.setPadding(true);
            dialogLayout.setSpacing(false);

            dialog.add(dialogLayout);
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

    // ==================== Filter Builder Inner Classes ====================

    private static class LegBuilder {
        NumberField minDelta = new NumberField("Min Delta");
        NumberField maxDelta = new NumberField("Max Delta");
        IntegerField minOpenInterest = new IntegerField("Min OI");

        void buildUI(FormLayout form, String label) {
            form.add(new H3(label + " Leg"), 3);
            form.add(minDelta, maxDelta, minOpenInterest);
        }

        LegFilter getFilter() {
            LegFilter f = new LegFilter();
            if (minDelta.getValue() != null)
                f.setMinDelta(minDelta.getValue());
            if (maxDelta.getValue() != null)
                f.setMaxDelta(maxDelta.getValue());
            if (minOpenInterest.getValue() != null)
                f.setMinOpenInterest(minOpenInterest.getValue());
            return f;
        }
    }

    private static class CreditSpreadBuilder {
        LegBuilder shortLeg = new LegBuilder();
        LegBuilder longLeg = new LegBuilder();

        void buildUI(FormLayout form) {
            shortLeg.buildUI(form, "Short");
            longLeg.buildUI(form, "Long");
        }

        void apply(CreditSpreadFilter filter) {
            filter.setShortLeg(shortLeg.getFilter());
            filter.setLongLeg(longLeg.getFilter());
        }
    }

    private static class IronCondorBuilder {
        LegBuilder putShort = new LegBuilder();
        LegBuilder putLong = new LegBuilder();
        LegBuilder callShort = new LegBuilder();
        LegBuilder callLong = new LegBuilder();

        void buildUI(FormLayout form) {
            putShort.buildUI(form, "Put Short");
            putLong.buildUI(form, "Put Long");
            callShort.buildUI(form, "Call Short");
            callLong.buildUI(form, "Call Long");
        }

        void apply(IronCondorFilter filter) {
            filter.setPutShortLeg(putShort.getFilter());
            filter.setPutLongLeg(putLong.getFilter());
            filter.setCallShortLeg(callShort.getFilter());
            filter.setCallLongLeg(callLong.getFilter());
        }
    }

    private static class BrokenWingButterflyBuilder {
        LegBuilder leg1 = new LegBuilder();
        LegBuilder leg2 = new LegBuilder();
        LegBuilder leg3 = new LegBuilder();
        NumberField maxTotalDebit;
        NumberField priceVsMaxDebitRatio;

        void buildUI(FormLayout form) {
            leg1.buildUI(form, "Leg 1 Long");
            leg2.buildUI(form, "Leg 2 Short");
            leg3.buildUI(form, "Leg 3 Long");
        }

        void apply(BrokenWingButterflyFilter filter) {
            filter.setLeg1Long(leg1.getFilter());
            filter.setLeg2Short(leg2.getFilter());
            filter.setLeg3Long(leg3.getFilter());
        }
    }

    private static class ZebraBuilder {
        LegBuilder shortCall = new LegBuilder();
        LegBuilder longCall = new LegBuilder();

        void buildUI(FormLayout form) {
            shortCall.buildUI(form, "Short Call");
            longCall.buildUI(form, "Long Call");
        }

        void apply(ZebraFilter filter) {
            filter.setShortCall(shortCall.getFilter());
            filter.setLongCall(longCall.getFilter());
        }
    }

    private static class LongCallLeapBuilder {
        NumberField marginInterestRate = new NumberField("Margin Interest Rate");
        NumberField savingsInterestRate = new NumberField("Savings Interest Rate");
        NumberField maxOptionPricePercent = new NumberField("Max Option Price %");
        NumberField maxCAGRForBreakEven = new NumberField("Max CAGR For BreakEven");
        NumberField minCostSavingsPercent = new NumberField("Min Cost Savings %");
        IntegerField topTradesCount = new IntegerField("Top Trades Count");
        LegBuilder longCall = new LegBuilder();

        void buildUI(FormLayout form) {
            form.add(new H3("LEAP Specific"), 3);
            form.add(marginInterestRate, savingsInterestRate, maxOptionPricePercent,
                    maxCAGRForBreakEven, minCostSavingsPercent, topTradesCount);
            longCall.buildUI(form, "Long Call");
        }

        void apply(LongCallLeapFilter filter) {
            if (marginInterestRate.getValue() != null)
                filter.setMarginInterestRate(marginInterestRate.getValue());
            if (savingsInterestRate.getValue() != null)
                filter.setSavingsInterestRate(savingsInterestRate.getValue());
            if (maxOptionPricePercent.getValue() != null)
                filter.setMaxOptionPricePercent(maxOptionPricePercent.getValue());
            if (maxCAGRForBreakEven.getValue() != null)
                filter.setMaxCAGRForBreakEven(maxCAGRForBreakEven.getValue());
            if (minCostSavingsPercent.getValue() != null)
                filter.setMinCostSavingsPercent(minCostSavingsPercent.getValue());
            if (topTradesCount.getValue() != null)
                filter.setTopTradesCount(topTradesCount.getValue());
            filter.setLongCall(longCall.getFilter());
        }
    }
}
