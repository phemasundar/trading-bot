package com.hemasundar.ui;

import com.hemasundar.ui.views.ExecuteStrategyView;
import com.hemasundar.ui.views.MainView;
import com.hemasundar.ui.views.StrategyConfigView;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.Lumo;

public class MainLayout extends AppLayout {

    public MainLayout() {
        getElement().getThemeList().add(Lumo.DARK);

        createHeader();
        createDrawer();
    }

    private void createHeader() {
        H1 title = new H1("Option Trades Finder");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)");
        title.getStyle().set("margin", "0");

        Span statusLabel = new Span("Market Open");
        statusLabel.getElement().getThemeList().add("badge success");

        HorizontalLayout header = new HorizontalLayout(new DrawerToggle(), title, statusLabel);
        header.addClassName("header");
        header.setWidth("100%");
        header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        header.setFlexGrow(1, title);
        header.setPadding(true);

        addToNavbar(header);
    }

    private void createDrawer() {
        Div navContainer = new Div();
        navContainer.getStyle().set("display", "flex");
        navContainer.getStyle().set("flex-direction", "column");
        navContainer.getStyle().set("padding", "var(--lumo-space-s)");
        navContainer.getStyle().set("gap", "4px");
        navContainer.getStyle().set("overflow", "hidden");
        navContainer.getStyle().set("width", "100%");
        navContainer.getStyle().set("box-sizing", "border-box");

        navContainer.add(createNavItem(VaadinIcon.DASHBOARD, "Dashboard", MainView.class));
        navContainer.add(createNavItem(VaadinIcon.PLAY, "Execute", ExecuteStrategyView.class));
        navContainer.add(createNavItem(VaadinIcon.COG, "Config", StrategyConfigView.class));

        addToDrawer(navContainer);
    }

    private RouterLink createNavItem(VaadinIcon vaadinIcon,
            String label,
            Class<? extends com.vaadin.flow.component.Component> view) {
        RouterLink link = new RouterLink();
        link.setRoute(view);

        Icon icon = vaadinIcon.create();
        icon.setSize("18px");
        icon.getStyle().set("flex-shrink", "0");

        Span text = new Span(label);
        text.getStyle().set("font-size", "0.85rem");
        text.getStyle().set("overflow", "hidden");
        text.getStyle().set("text-overflow", "ellipsis");

        Div content = new Div(icon, text);
        content.getStyle().set("display", "flex");
        content.getStyle().set("align-items", "center");
        content.getStyle().set("gap", "10px");

        link.add(content);
        link.getStyle().set("text-decoration", "none");
        link.getStyle().set("color", "var(--lumo-body-text-color)");
        link.getStyle().set("display", "block");
        link.getStyle().set("padding", "10px 8px");
        link.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        link.getStyle().set("overflow", "hidden");
        return link;
    }
}
