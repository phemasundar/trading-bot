# Trading Bot - Global Context & Constraints

## Core Objective

- **Architecture**: A modular, event-driven Options Trading Bot built on Java and Spring Boot. It executes automated strategies based on dynamic technical indicators and options market data. Frontend logic is modularized into domain-focused JS modules under `static/js/` aggregated via `app.js`. Strategy definitions reside in `strategies-config.yml`, while static Greek polarities by strategy type are centralized in `strategy-greeks.yml`.
- **Runtime Goals**: Maintain low-latency execution, resilient error handling, and reliable asynchronous processing. Core integrations include the Charles Schwab API for market data/trading, and Supabase for persistent tracking of IV data (used for IV Rank and IV Percentile filters with >=20 historical records required), execution history, historical trade deduplication (`historical_trades`), and filter configuration.
- **Design Philosophy**: High reliability with strict fail-safes. Technical and fundamental filters, indicator thresholds, and strategy constraints must remain externalized in configuration (e.g. YAML), utilizing dynamic mathematical expression evaluation rather than hardcoded logic. Strategy static Greeks are decoupled into `strategy-greeks.yml` to prevent configuration bloat.
- **Timezone Convention**: All timestamps across the app use **`America/Chicago` (CDT/CST)** — Dallas, TX local time. Enforced via `spring.jackson.time-zone=America/Chicago` in `application.properties` and `-Duser.timezone=America/Chicago` JVM arg in `ci.yml`. Market data zone (`America/New_York`) is used only for NYSE trading-day boundary logic in `TradeHashUtil` and `TechnicalIndicatorUtils`.

## Coding Standards

- **Java Best Practices**: Write clean, modern Java (JDK 21+). Adhere to SOLID principles, favoring composition over inheritance and immutability where applicable.
- **Spring Boot Ecosystem**: Effectively leverage Spring's dependency injection, properties configuration, and application events. Keep business logic decoupled from framework-specific web layers.
- **Boilerplate Reduction**: Use Lombok extensively (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Slf4j`, `@UtilityClass`) in all new models, DTOs, and config classes to minimize boilerplate.
- **Null Safety**: Standardize null and emptiness checks using Apache Commons (`StringUtils.isBlank`, `CollectionUtils.isEmpty`). Ensure collections are returned as empty lists rather than nulls.
- **Testing Requirements**: Enforce a minimum of 85% instruction coverage for core business logic using TestNG and standard mocking frameworks.
- **Strategy Identity**: Strategy IDs are decoupled from securities files (`Alias [- TermType]`) to guarantee deterministic and stable trade hashes in `historical_trades`. Ticker universe configs (`securitiesFile`, `securities`) and technical filter configurations (`technicalFilters`) are persisted in `filterConfig` and formatted cleanly into human-readable representations in Filter Details (preventing `[object Object]` serialization).

## Agent Behavior

- **Context Maintenance**: Keep this `GEMINI.md` file strictly under 150 lines. Do NOT append granular daily changelogs, deprecated feature notes, or stale histories here.
- **Proactive Documentation**: Always update `README.md` and specific `/docs/*.md` files when relevant changes are made to the project, without explicit prompting. Ensure documentation remains detailed and up-to-date.
- **Code Cleanliness**: Remove unused code, abandoned strategies, and legacy backward-compatibility fallbacks immediately upon refactoring. Do not retain dead code.
- **Autonomy**: Operate efficiently within these boundaries. Use standard Java/Spring tools and prioritize clean architectural boundaries in all implementations.
