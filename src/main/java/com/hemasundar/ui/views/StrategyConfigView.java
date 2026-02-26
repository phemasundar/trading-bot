package com.hemasundar.ui.views;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hemasundar.config.StrategiesConfig;
import com.hemasundar.config.StrategiesConfig.*;
import com.hemasundar.ui.MainLayout;
import com.hemasundar.utils.FilePaths;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.Lumo;
import lombok.extern.log4j.Log4j2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Read-only viewer for strategies-config.json.
 * Displays options strategies, technical screeners, and indicator settings
 * in collapsible, UI-friendly cards matching the dashboard theme.
 */
@Route(value = "config", layout = MainLayout.class)
@PageTitle("Strategy Configuration")
@Log4j2
public class StrategyConfigView extends VerticalLayout {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public StrategyConfigView() {
        getElement().getThemeList().add(Lumo.DARK);
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        addClassName("main-layout");

        try {
            StrategiesConfig config = loadConfig();
            add(createOptionsStrategiesSection(config.getOptionsStrategies()));
            add(createScreenersSection(config.getTechnicalScreeners()));
            if (config.getTechnicalIndicators() != null) {
                add(createIndicatorsCard(config.getTechnicalIndicators()));
            }
        } catch (Exception e) {
            log.error("Failed to load strategies config", e);
            Span error = new Span("Failed to load configuration: " + e.getMessage());
            error.getStyle().set("color", "var(--lumo-error-color)");
            add(error);
        }
    }

    private StrategiesConfig loadConfig() throws Exception {
        Path configPath = FilePaths.strategiesConfig;
        String json = Files.readString(configPath);
        return MAPPER.readValue(json, StrategiesConfig.class);
    }

    // ==================== Options Strategies Section ====================

    private Component createOptionsStrategiesSection(List<StrategyEntry> strategies) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);
        section.setWidthFull();

        HorizontalLayout header = new HorizontalLayout();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setSpacing(true);

        H2 title = new H2("Options Strategies");
        title.addClassName("section-title");
        title.getStyle().set("margin", "0");

        Span countBadge = new Span(strategies.size() + " strategies");
        countBadge.getElement().getThemeList().add("badge");
        countBadge.getStyle().set("font-size", "0.75rem");
        countBadge.getStyle().set("border", "1px solid var(--stitch-border)");

        long enabledCount = strategies.stream().filter(StrategyEntry::isEnabled).count();
        Span enabledBadge = new Span(enabledCount + " enabled");
        enabledBadge.getElement().getThemeList().add("badge success");
        enabledBadge.getStyle().set("font-size", "0.75rem");

        header.add(title, countBadge, enabledBadge);
        section.add(header);

        for (StrategyEntry entry : strategies) {
            section.add(createStrategyCard(entry));
        }
        return section;
    }

    @SuppressWarnings("unchecked")
    private Component createStrategyCard(StrategyEntry entry) {
        Div card = new Div();
        card.addClassName("group");
        card.addClassName("strategy-card");
        card.setWidthFull();

        // --- Header ---
        Div header = new Div();
        header.addClassName("strategy-card-header");
        header.getStyle().set("display", "flex");
        header.getStyle().set("flex-wrap", "wrap");
        header.getStyle().set("justify-content", "space-between");
        header.getStyle().set("align-items", "center");
        header.getStyle().set("cursor", "pointer");
        header.getStyle().set("padding", "12px 16px");
        header.getStyle().set("gap", "8px");

        // Left: arrow + info block (two lines)
        HorizontalLayout leftSection = new HorizontalLayout();
        leftSection.setAlignItems(FlexComponent.Alignment.CENTER);
        leftSection.setSpacing(true);

        com.vaadin.flow.component.icon.Icon arrowIcon = VaadinIcon.CHEVRON_DOWN.create();
        arrowIcon.addClassName("rotate-icon");
        arrowIcon.getStyle().set("color", "var(--stitch-text-secondary)");
        arrowIcon.getStyle().set("transition", "transform 0.2s");
        arrowIcon.getStyle().set("transform", "rotate(-90deg)");

        VerticalLayout infoBlock = new VerticalLayout();
        infoBlock.setSpacing(false);
        infoBlock.setPadding(false);

        // Line 1: Alias + type badge
        HorizontalLayout titleRow = new HorizontalLayout();
        titleRow.setAlignItems(FlexComponent.Alignment.CENTER);
        titleRow.setSpacing(true);

        H3 aliasLabel = new H3(entry.getAlias() != null ? entry.getAlias()
                : (entry.getStrategyType() != null ? entry.getStrategyType().name() : "Unknown"));
        aliasLabel.getStyle().set("margin", "0");
        aliasLabel.getStyle().set("font-size", "0.95rem");
        aliasLabel.getStyle().set("color", entry.isEnabled() ? "#fff" : "var(--stitch-text-secondary)");

        Span typeBadge = new Span(entry.getStrategyType() != null ? entry.getStrategyType().name() : "");
        typeBadge.getElement().getThemeList().add("badge contrast");
        typeBadge.getStyle().set("font-size", "0.6rem");

        titleRow.add(aliasLabel, typeBadge);

        // Line 2: Enabled/Disabled pill + Securities
        HorizontalLayout subtitleRow = new HorizontalLayout();
        subtitleRow.setAlignItems(FlexComponent.Alignment.CENTER);
        subtitleRow.setSpacing(true);

        Span statusPill = new Span(entry.isEnabled() ? "Enabled" : "Disabled");
        if (entry.isEnabled()) {
            statusPill.getElement().getThemeList().add("badge");
            statusPill.getStyle().set("background-color", "hsla(145, 65%, 42%, 0.2)");
            statusPill.getStyle().set("color", "hsl(145, 65%, 42%)");
        } else {
            statusPill.getElement().getThemeList().add("badge error");
        }
        statusPill.getStyle().set("font-size", "0.65rem");
        subtitleRow.add(statusPill);

        String securitiesText = "";
        if (entry.getSecuritiesFile() != null) {
            securitiesText = entry.getSecuritiesFile();
        }
        if (entry.getSecurities() != null && !entry.getSecurities().isEmpty()) {
            securitiesText += (securitiesText.isEmpty() ? "" : " + ") + entry.getSecurities();
        }
        if (!securitiesText.isEmpty()) {
            com.vaadin.flow.component.icon.Icon fileIcon = VaadinIcon.FILE_TEXT_O.create();
            fileIcon.setSize("12px");
            fileIcon.getStyle().set("color", "var(--stitch-text-secondary)");
            fileIcon.getStyle().set("margin-left", "8px");

            Span securitiesLabel = new Span(securitiesText);
            securitiesLabel.getStyle().set("font-size", "0.65rem");
            securitiesLabel.getStyle().set("color", "var(--stitch-text-secondary)");
            securitiesLabel.addClassName("grid-mono");

            subtitleRow.add(fileIcon, securitiesLabel);
        }

        infoBlock.add(titleRow, subtitleRow);
        leftSection.add(arrowIcon, infoBlock);

        header.add(leftSection);

        // --- Content (collapsible) ---
        Div contentContainer = new Div();
        contentContainer.addClassName("collapsible-content");
        contentContainer.getStyle().set("padding", "0 16px 16px 16px");

        // Build filter display grid
        if (entry.getFilter() instanceof Map) {
            contentContainer.add(buildFilterGrid((Map<String, Object>) entry.getFilter()));
        }

        // Technical filter (if present)
        if (entry.hasTechnicalFilter() && entry.getTechnicalFilter() instanceof Map) {
            H3 techTitle = new H3("Technical Filter");
            techTitle.getStyle().set("font-size", "0.9rem");
            techTitle.getStyle().set("color", "var(--stitch-primary)");
            techTitle.getStyle().set("margin", "16px 0 8px 0");
            contentContainer.add(techTitle);
            contentContainer.add(buildFilterGrid((Map<String, Object>) entry.getTechnicalFilter()));
        }

        // Start collapsed — use display:none for reliable hiding
        contentContainer.getStyle().set("display", "none");

        // Toggle
        header.addClickListener(e -> {
            String display = contentContainer.getElement().getStyle().get("display");
            if ("none".equals(display)) {
                contentContainer.getStyle().set("display", "block");
                arrowIcon.getStyle().set("transform", "rotate(0deg)");
            } else {
                contentContainer.getStyle().set("display", "none");
                arrowIcon.getStyle().set("transform", "rotate(-90deg)");
            }
        });

        card.add(header, contentContainer);
        return card;
    }

    // ==================== Filter Grid ====================

    /**
     * Builds a visual grid of filter key-value pairs. Recursively renders nested
     * objects (e.g., leg filters) as sub-sections.
     */
    @SuppressWarnings("unchecked")
    private Component buildFilterGrid(Map<String, Object> filterMap) {
        VerticalLayout container = new VerticalLayout();
        container.setPadding(false);
        container.setSpacing(false);
        container.setWidthFull();

        Div flatGrid = createCssGrid();

        for (Map.Entry<String, Object> kv : filterMap.entrySet()) {
            String key = kv.getKey();
            Object value = kv.getValue();

            if (value instanceof Map) {
                // Flush flat items, then render nested object as sub-section
                if (flatGrid.getElement().getChildCount() > 0) {
                    container.add(flatGrid);
                    flatGrid = createCssGrid();
                }
                H3 subTitle = new H3(formatLabel(key));
                subTitle.getStyle().set("font-size", "0.85rem");
                subTitle.getStyle().set("color", "var(--stitch-primary)");
                subTitle.getStyle().set("margin", "16px 0 4px 0");
                container.add(subTitle);
                container.add(buildFilterGrid((Map<String, Object>) value));
            } else if (value instanceof List) {
                if (flatGrid.getElement().getChildCount() > 0) {
                    container.add(flatGrid);
                    flatGrid = createCssGrid();
                }
                H3 listTitle = new H3(formatLabel(key));
                listTitle.getStyle().set("font-size", "0.85rem");
                listTitle.getStyle().set("color", "var(--stitch-primary)");
                listTitle.getStyle().set("margin", "12px 0 4px 0");
                container.add(listTitle);

                HorizontalLayout listRow = new HorizontalLayout();
                listRow.setSpacing(true);
                for (Object item : (List<?>) value) {
                    Span chip = new Span(String.valueOf(item));
                    chip.getElement().getThemeList().add("badge contrast");
                    chip.getStyle().set("font-size", "0.75rem");
                    listRow.add(chip);
                }
                container.add(listRow);
            } else {
                // Simple key-value
                flatGrid.add(createKvPair(key, value));
            }
        }

        if (flatGrid.getElement().getChildCount() > 0) {
            container.add(flatGrid);
        }
        return container;
    }

    private Div createCssGrid() {
        Div grid = new Div();
        grid.getStyle().set("display", "grid");
        grid.getStyle().set("grid-template-columns", "repeat(auto-fill, minmax(180px, 1fr))");
        grid.getStyle().set("gap", "12px 24px");
        grid.setWidthFull();
        return grid;
    }

    /**
     * Creates a single key-value display pair.
     */
    private Div createKvPair(String key, Object value) {
        Div pair = new Div();
        pair.getStyle().set("display", "flex");
        pair.getStyle().set("flex-direction", "column");
        pair.getStyle().set("gap", "2px");
        pair.getStyle().set("padding", "8px 0");

        Span label = new Span(formatLabel(key));
        label.getStyle().set("font-size", "0.7rem");
        label.getStyle().set("color", "var(--stitch-text-secondary)");
        label.getStyle().set("text-transform", "uppercase");
        label.getStyle().set("letter-spacing", "0.5px");

        Span val = new Span(formatValue(key, value));
        val.addClassName("grid-mono");
        val.getStyle().set("font-size", "1rem");
        val.getStyle().set("font-weight", "500");

        // Color booleans
        if (value instanceof Boolean) {
            val.getStyle().set("color", (Boolean) value ? "var(--stitch-success)" : "var(--stitch-text-secondary)");
        } else {
            val.getStyle().set("color", "#fff");
        }

        pair.add(label, val);
        return pair;
    }

    // ==================== Technical Screeners Section ====================

    private Component createScreenersSection(List<ScreenerEntry> screeners) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);
        section.setWidthFull();

        HorizontalLayout header = new HorizontalLayout();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setSpacing(true);

        H2 title = new H2("Technical Screeners");
        title.addClassName("section-title");
        title.getStyle().set("margin", "0");

        Span countBadge = new Span(screeners.size() + " screeners");
        countBadge.getElement().getThemeList().add("badge");
        countBadge.getStyle().set("font-size", "0.75rem");
        countBadge.getStyle().set("border", "1px solid var(--stitch-border)");

        header.add(title, countBadge);
        section.add(header);

        for (ScreenerEntry entry : screeners) {
            section.add(createScreenerCard(entry));
        }
        return section;
    }

    private Component createScreenerCard(ScreenerEntry entry) {
        Div card = new Div();
        card.addClassName("group");
        card.addClassName("strategy-card");
        card.setWidthFull();

        // Header
        Div header = new Div();
        header.addClassName("p-4");
        header.addClassName("strategy-card-header");
        header.getStyle().set("display", "flex");
        header.getStyle().set("flex-wrap", "wrap");
        header.getStyle().set("justify-content", "space-between");
        header.getStyle().set("align-items", "center");
        header.getStyle().set("cursor", "pointer");
        header.getStyle().set("gap", "12px");

        HorizontalLayout leftSection = new HorizontalLayout();
        leftSection.setAlignItems(FlexComponent.Alignment.CENTER);
        leftSection.setSpacing(true);

        com.vaadin.flow.component.icon.Icon arrowIcon = VaadinIcon.CHEVRON_DOWN.create();
        arrowIcon.addClassName("rotate-icon");
        arrowIcon.getStyle().set("color", "var(--stitch-text-secondary)");
        arrowIcon.getStyle().set("transition", "transform 0.2s");
        arrowIcon.getStyle().set("transform", "rotate(-90deg)");

        Span statusPill = new Span(entry.isEnabled() ? "Enabled" : "Disabled");
        statusPill.getElement().getThemeList().add(entry.isEnabled() ? "badge success" : "badge error");
        statusPill.getStyle().set("font-size", "0.7rem");

        H3 screenerName = new H3(entry.getScreenerType() != null ? entry.getScreenerType().name() : "Unknown");
        screenerName.getStyle().set("margin", "0");
        screenerName.getStyle().set("font-size", "1rem");
        screenerName.getStyle().set("color", "#fff");

        leftSection.add(arrowIcon, statusPill, screenerName);
        header.add(leftSection);

        // Content
        Div contentContainer = new Div();
        contentContainer.addClassName("collapsible-content");
        contentContainer.getStyle().set("padding", "0 16px 16px 16px");

        if (entry.getConditions() != null) {
            ScreenerConditionsConfig c = entry.getConditions();
            Div grid = createCssGrid();

            if (c.getRsiCondition() != null)
                grid.add(createKvPair("RSI Condition", c.getRsiCondition().name()));
            if (c.getBollingerCondition() != null)
                grid.add(createKvPair("Bollinger Condition", c.getBollingerCondition().name()));
            if (c.getMinVolume() != null)
                grid.add(createKvPair("Min Volume", c.getMinVolume()));
            if (c.isRequirePriceBelowMA200())
                grid.add(createKvPair("Price Below MA200", true));
            if (c.isRequirePriceAboveMA50())
                grid.add(createKvPair("Price Above MA50", true));
            contentContainer.add(grid);
        }

        // Start collapsed
        contentContainer.getStyle().set("display", "none");

        header.addClickListener(e -> {
            String display = contentContainer.getElement().getStyle().get("display");
            if ("none".equals(display)) {
                contentContainer.getStyle().set("display", "block");
                arrowIcon.getStyle().set("transform", "rotate(0deg)");
            } else {
                contentContainer.getStyle().set("display", "none");
                arrowIcon.getStyle().set("transform", "rotate(-90deg)");
            }
        });

        card.add(header, contentContainer);
        return card;
    }

    // ==================== Technical Indicators Card ====================

    private Component createIndicatorsCard(TechnicalIndicatorsConfig indicators) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);
        section.setWidthFull();

        H2 title = new H2("Technical Indicators");
        title.addClassName("section-title");
        title.getStyle().set("margin", "0");
        section.add(title);

        Div card = new Div();
        card.addClassName("group");
        card.addClassName("strategy-card");
        card.setWidthFull();
        card.getStyle().set("padding", "20px");

        Div grid = createCssGrid();

        grid.add(createKvPair("RSI Period", indicators.getRsiPeriod()));
        grid.add(createKvPair("Oversold Threshold", indicators.getOversoldThreshold()));
        grid.add(createKvPair("Overbought Threshold", indicators.getOverboughtThreshold()));
        grid.add(createKvPair("Bollinger Period", indicators.getBollingerPeriod()));
        grid.add(createKvPair("Bollinger Std Dev", indicators.getBollingerStdDev()));

        card.add(grid);
        section.add(card);
        return section;
    }

    // ==================== Utility Methods ====================

    /**
     * Converts camelCase field names to human-readable labels.
     * e.g., "maxLossLimit" → "Max Loss Limit"
     */
    private String formatLabel(String camelCase) {
        if (camelCase == null)
            return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append(' ');
            }
            sb.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return sb.toString();
    }

    /**
     * Formats values for display — formats booleans and large numbers.
     */
    private String formatValue(String key, Object value) {
        if (value == null)
            return "—";

        // Booleans
        if (value instanceof Boolean) {
            return (Boolean) value ? "Yes" : "No";
        }

        String str = String.valueOf(value);
        String lower = key.toLowerCase();

        // Volume formatting
        if (lower.contains("volume")) {
            try {
                long vol = Long.parseLong(str);
                if (vol >= 1_000_000)
                    return String.format("%.1fM", vol / 1_000_000.0);
                if (vol >= 1_000)
                    return String.format("%.0fK", vol / 1_000.0);
            } catch (NumberFormatException ignored) {
            }
        }
        return str;
    }
}
