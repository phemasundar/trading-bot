# Google Cloud Run Deployment Guide

A step-by-step guide to deploy the Trading Bot Vaadin web app to **Google Cloud Run** — fully automated via GitHub Actions after this one-time GCP setup.

---

## Prerequisites

- A Google account
- Your GitHub repository with this code

---

## Part 1: Create a GCP Project

1. Go to [https://console.cloud.google.com](https://console.cloud.google.com)
2. Click the **project dropdown** at the top (next to "Google Cloud" logo)
3. Click **"New Project"**
4. Set **Project name**: `trading-bot` (or any name you like)
5. Click **"Create"**
6. Wait ~30 seconds, then select your new project from the dropdown
7. **Copy your Project ID** — shown in the project card (e.g. `trading-bot-123456`). You'll need this later.

> **Note:** You'll be prompted to set up billing. Cloud Run has a generous free tier (**2 million requests/month**, first 180,000 CPU-seconds free), and keeping 1 instance with our settings costs roughly **$0–5/month**.

---

## Part 2: Enable Required APIs

You need to enable 3 APIs. Do this in order:

1. In the left sidebar, go to **"APIs & Services" → "Enable APIs and Services"**
2. Search for and enable each of these (click Enable for each):
   - **Cloud Run Admin API**
   - **Artifact Registry API**
   - **IAM Credentials API** (needed for the service account)

Or do it all at once — open Cloud Shell (terminal icon `>_` at the top right) and run:
```bash
gcloud services enable run.googleapis.com artifactregistry.googleapis.com iamcredentials.googleapis.com
```

---

## Part 3: Create an Artifact Registry Repository

This is where your Docker images are stored.

1. Go to **"Artifact Registry"** in the left sidebar (or search for it)
2. Click **"Create Repository"**
3. Fill in:
   - **Name**: `trading-bot`
   - **Format**: `Docker`
   - **Mode**: `Standard`
   - **Region**: `us-central1` *(must match the region in the GitHub Actions workflow)*
4. Click **"Create"**

---

## Part 4: Create a Service Account for GitHub Actions

GitHub Actions needs a GCP identity (Service Account) to push images and deploy.

### 4a. Create the Service Account

1. Go to **"IAM & Admin" → "Service Accounts"**
2. Click **"Create Service Account"**
3. Fill in:
   - **Service account name**: `github-actions-deployer`
   - **Service account ID**: auto-filled as `github-actions-deployer`
4. Click **"Create and Continue"**

### 4b. Assign Roles

On the **"Grant this service account access to project"** step, add these 3 roles (click **"Add another role"** between each):

| Role | Purpose |
|------|---------|
| **Artifact Registry Writer** | Push Docker images |
| **Cloud Run Admin** | Deploy and manage Cloud Run services |
| **Service Account User** | Allow deploying as a service account |

5. Click **"Continue"** → **"Done"**

### 4c. Create and Download a JSON Key

1. Click on the service account you just created (`github-actions-deployer@...`)
2. Go to the **"Keys"** tab
3. Click **"Add Key" → "Create new key"**
4. Select **JSON** format
5. Click **"Create"** — a `.json` file downloads automatically
6. **Keep this file safe** — you'll paste its contents into GitHub Secrets next

---

## Part 5: Add GitHub Secrets

1. Go to your GitHub repository → **"Settings"** tab
2. In the left sidebar: **"Secrets and variables" → "Actions"**
3. Click **"New repository secret"** for each of the following:

| Secret Name | Value |
|---|---|
| `GCP_PROJECT_ID` | Your GCP Project ID (e.g. `trading-bot-123456`) |
| `GCP_SA_KEY` | The **entire content** of the JSON key file you downloaded in Part 4c |
| `SUPABASE_URL` | `https://snjbkdqbmlmwjllnoyqy.supabase.co` |
| `SUPABASE_ANON_KEY` | Your Supabase publishable/anon key |
| `SUPABASE_SERVICE_ROLE_KEY` | Your Supabase service role key |
| `API_BEARER_TOKEN` | Bearer token for accessing `/api/*` endpoints |
| `REFRESH_TOKEN` | Schwab API Refresh Token |
| `APP_KEY` | Schwab API App Key |
| `PP_SECRET` | Schwab API Secret |
| `TELEGRAM_BOT_TOKEN` | Telegram Bot Token for alerts |
| `TELEGRAM_CHAT_ID` | Telegram Chat ID for alerts |

> **Tip:** To get the full JSON key content, open the downloaded `.json` file in any text editor, select all (`Ctrl+A`), and paste it as the secret value.

---

## Part 6: First Deployment

With everything set up, trigger your first deployment:

1. Push any commit to the `main` branch **OR**
2. Go to **GitHub → Actions → "Deploy to Google Cloud Run" → "Run workflow"** → click **"Run workflow"**

The workflow will take about **5–8 minutes** on the first run (Maven downloads dependencies, Vaadin builds production bundle, Docker builds image).

### Watch the progress:
1. Go to **GitHub → Actions**
2. Click on the running workflow
3. Click **"Build & Deploy to Cloud Run"** to see live logs

### Get your URL:
At the end of the workflow, the last step "Get service URL" prints:
```
✅ Deployed successfully!
🌐 URL: https://trading-bot-xxxxxxxx-uc.a.run.app
```

This URL is **permanent** — it never changes between deployments.

---

## Part 7: Verify in GCP Console

1. Go to **"Cloud Run"** in the GCP Console
2. Click on **"trading-bot"** service
3. You'll see:
   - The service URL at the top
   - Active revisions (each deployment creates a new revision)
   - CPU/Memory metrics
   - Logs tab for debugging

---

## How Releases Work (Future Deployments)

Every time you push to `main`:

```
git push origin main
```

GitHub Actions automatically:
1. Builds a new Docker image (tagged with the commit SHA)
2. Pushes to Artifact Registry
3. Deploys to Cloud Run as a new revision (zero downtime)

The old revision stays running while new one starts, then traffic switches over automatically.

---

## Cost Estimate

With the current configuration (`min-instances=1`, `max-instances=1`, 512Mi RAM, 1 CPU):

| Resource | Free Tier | Our Usage | Estimated Cost |
|---|---|---|---|
| Cloud Run CPU | 180,000 vCPU-sec/month free | ~720,000 vCPU-sec | ~$10/month |
| Cloud Run Memory | 360,000 GB-sec/month free | ~384,000 GB-sec/month | ~$1/month |
| Artifact Registry | 0.5 GB free | ~300 MB | **Free** |

> **To reduce cost to near-zero:** Change `--min-instances 1` to `--min-instances 0` in `deploy-cloud-run.yml`. This scales to zero when not used (cold start ~10–20 seconds on first request of the day). For a personal app accessed a few times a day, total cost would be **< $1/month**.

---

## Troubleshooting

### Deployment fails with "Permission denied"
- Verify the service account has all 3 roles from Part 4b
- Ensure `GCP_SA_KEY` secret contains the full JSON (including curly braces)

### App starts but shows error / blank page
- Check logs: GCP Console → Cloud Run → "trading-bot" → **Logs** tab
- Or in GitHub Actions, check the Cloud Run step output

### "Artifact Registry repository not found"
- Ensure the repository region (`us-central1`) matches the `REGISTRY` env var in `deploy-cloud-run.yml`
- Ensure the repository name is exactly `trading-bot`
