# Options Trading Bot

A high-performance trading bot for identifying and executing options strategies (Credit Spreads, Iron Condors, BWB, Zebra, LEAPs) using the Schwab API and Supabase.

## Documentation
- [Dashboard Design](docs/dashboard-design.md)
- [Deployment Guide (Cloud Run)](CLOUD_RUN_DEPLOYMENT.md)
- [Deployment Guide (Oracle Cloud)](ORACLE_CLOUD_DEPLOYMENT.md)

## Tech Stack
- **Backend**: Java 17, Spring Boot, Supabase (PostgreSQL)
- **Frontend**: Static HTML, CSS, Vanilla JS (RESTful API integration)
- **Infrastructure**: Docker, Google Cloud Run, GitHub Actions CI/CD

## Deployment Section

### 1. Prerequisites
- **Google Cloud Project**: Enabled Cloud Run, Artifact Registry, and Secret Manager.
- **Supabase Project**: Database setup with the required schema.
- **Schwab Developer Account**: API keys and app approval.
- **Telegram Bot**: API Token and Chat ID for alerts.

### 2. Authentication (Bearer Token)
The API endpoints are secured via a Bearer Token. This token is defined in `application.properties` (or as an environment variable).
- **Local Dev**: Use the token set in `src/main/resources/application.properties` under `api.bearer.token`.
- **Production**: Set the `API_BEARER_TOKEN` environment variable in your deployment environment (e.g., Cloud Run environment variables).
- **Security Check**: To ensure the token is NEVER empty in production, the `BearerTokenFilter` will reject requests if the configured token is blank or default. Always set a strong, unique UUID for this token.

### 3. Setting Production Profile
To run the application in production mode:
- **Docker/CMD**: Use the JVM flag `-Dspring.profiles.active=production`.
- **Environment Variable**: Set `SPRING_PROFILES_ACTIVE=production`.
- This will activate `application-production.properties`, which enables optimization features and specific production configurations.

### 4. Git Flow & Deployment
1.  **Develop**: Push all experimental and stable changes to the `develop` branch.
2.  **Release**: Create a PR from `develop` to `main`.
3.  **Merge**: Merging to `main` triggers the GitHub Actions workflow (if configured) to deploy to Cloud Run.
4.  **Release Tag**: Always create a Git Release on `main` to document major version changes.

---
© 2026 Options Trading Bot Team 🚀
