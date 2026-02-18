# Trading Bot Dashboard

A standalone static dashboard for visualizing automated trading strategy execution results.

## Setup

1.  **Initialize Git Repository**
    ```bash
    git init
    git add .
    git commit -m "Initial commit"
    ```

2.  **Create GitHub Repository**
    - Create a new repository on GitHub (e.g., `trading-bot-dashboard`).
    - Push your local code:
      ```bash
      git remote add origin https://github.com/YOUR_USERNAME/trading-bot-dashboard.git
      git branch -M main
      git push -u origin main
      ```

3.  **Configure GitHub Pages**
    - Go to **Settings** > **Pages**.
    - Under **Build and deployment**, select **Source** as **GitHub Actions**.

4.  **Add Secrets**
    - Go to **Settings** > **Secrets and variables** > **Actions**.
    - Add the following repository secrets (copy values from your main trading bot repo):
        - `SUPABASE_URL`
        - `SUPABASE_ANON_KEY`

5.  **Deploy**
    - The `Deploy Dashboard to GitHub Pages` workflow should run automatically on push.
    - Once completed, your dashboard will be live at `https://YOUR_USERNAME.github.io/trading-bot-dashboard/`.

## Architecture

- **Frontend**: Plain HTML/CSS/JS (no build step required).
- **Data Source**: Fetches directly from Supabase `latest_strategy_results` table.
- **Deployment**: GitHub Actions injects Supabase credentials into `app.js` during deployment.
