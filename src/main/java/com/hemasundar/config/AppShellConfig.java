package com.hemasundar.config;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import org.springframework.stereotype.Component;

/**
 * Application shell configuration for Vaadin.
 * Configures server push for async UI updates.
 */
@Push
@Component
@com.vaadin.flow.theme.Theme(themeClass = com.vaadin.flow.theme.lumo.Lumo.class)
@com.vaadin.flow.component.dependency.CssImport("./styles/styles.css")
public class AppShellConfig implements AppShellConfigurator {
    // Configuration is done via annotations
}
