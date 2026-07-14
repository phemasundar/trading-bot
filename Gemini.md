# Trading Bot - Global Context & Constraints

## Core Objective

- **Architecture**: A modular, event-driven Options Trading Bot built on Java and Spring Boot. It executes automated strategies based on dynamic technical indicators and options market data.
- **Runtime Goals**: Maintain low-latency execution, resilient error handling, and reliable asynchronous processing. Core integrations include the Charles Schwab API for market data/trading, and Supabase for persistent tracking of IV data, execution history, and filter configuration.
- **Design Philosophy**: High reliability with strict fail-safes. Technical and fundamental filters, indicator thresholds, and strategy constraints must remain externalized in configuration (e.g. JSON), utilizing dynamic mathematical expression evaluation rather than hardcoded logic.

## Coding Standards

- **Java Best Practices**: Write clean, modern Java (JDK 17+). Adhere to SOLID principles, favoring composition over inheritance and immutability where applicable.
- **Spring Boot Ecosystem**: Effectively leverage Spring's dependency injection, properties configuration, and application events. Keep business logic decoupled from framework-specific web layers.
- **Boilerplate Reduction**: Use Lombok extensively (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Slf4j`, `@UtilityClass`) in all new models, DTOs, and config classes to minimize boilerplate.
- **Null Safety**: Standardize null and emptiness checks using Apache Commons (`StringUtils.isBlank`, `CollectionUtils.isEmpty`). Ensure collections are returned as empty lists rather than nulls.
- **Testing Requirements**: Enforce a minimum of 85% instruction coverage for core business logic using TestNG and standard mocking frameworks.
  `

## Agent Behavior

- **Context Maintenance**: Keep this `GEMINI.md` file strictly under 150 lines. Do NOT append granular daily changelogs, deprecated feature notes, or stale histories here.
- **Proactive Documentation**: Always update `README.md` and specific `/docs/*.md` files when relevant changes are made to the project, without explicit prompting. Ensure documentation remains detailed and up-to-date.
- **Code Cleanliness**: Remove unused code, abandoned strategies, and legacy backward-compatibility fallbacks immediately upon refactoring. Do not retain dead code.
- **Autonomy**: Operate efficiently within these boundaries. Use standard Java/Spring tools and prioritize clean architectural boundaries in all implementations.
