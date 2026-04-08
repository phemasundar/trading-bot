# Authentication Setup Guide (Google OAuth + Supabase)

This guide walks you through setting up the secure authentication system for the Trading Bot using **Google OAuth 2.0** and **Supabase Auth**.

## Overview

The Trading Bot uses an "Indentity-as-a-Service" model:
1. **Frontend**: Users sign in via Google using the Supabase JS client.
2. **Supabase**: Handles the OAuth handshake and issues a signed JWT (JSON Web Token).
3. **Backend**: The Spring Boot app verifies the JWT using Supabase's public keys (JWKS) to ensure the user is who they say they are.
4. **Authorization**: The backend checks the user's email against an `ALLOWED_EMAILS` allowlist.

---

## Step 1: Google Cloud Console Configuration

To allow users to sign in with Google, you must create a project in the Google Cloud Console.

### 1.1 Create a New Project
1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Click the project dropdown (top left) and select **New Project**.
3. Name it `Trading Bot Auth` (or similar) and click **Create**.

### 1.2 Configure OAuth Consent Screen
1. Navigate to **APIs & Services > OAuth consent screen**.
2. Select **External** and click **Create**.
3. Fill in the required app information:
   - **App name**: `Trading Bot`
   - **User support email**: Your email address.
   - **Developer contact info**: Your email address.
4. Click **Save and Continue** through the Scopes and Test Users screens (you don't need to add specific scopes).

### 1.3 Create OAuth 2.0 Credentials
1. Navigate to **APIs & Services > Credentials**.
2. Click **+ Create Credentials** and select **OAuth client ID**.
3. Select **Web application** as the application type.
4. Name it `Supabase Trading Bot`.
5. **Authorized Redirect URIs**:
   - You need to add the Supabase Auth callback URL.
   - Format: `https://<your-project-ref>.supabase.co/auth/v1/callback`
   - *Example*: `https://xyztopq.supabase.co/auth/v1/callback`
   - (You can find your Project Ref in your Supabase URL).
6. Click **Create**.
7. **IMPORTANT**: Copy the **Client ID** and **Client Secret**. You will need these for Supabase.

---

## Step 2: Supabase Authentication Configuration

### 2.1 Enable Google Provider
1. Go to your [Supabase Dashboard](https://supabase.com/dashboard).
2. Navigate to **Authentication > Providers**.
3. Find **Google** and toggle it to **Enabled**.
4. Paste the **Client ID** and **Client Secret** you got from the Google Cloud Console.
5. Click **Save**.

### 2.2 Configure Site URL & Redirects
1. Navigate to **Authentication > URL Configuration**.
2. **Site URL**: This is the base URL of your deployed application.
   - *Example*: `https://trading-bot-xyz.a.run.app`
   - (For local testing, you can use `http://localhost:8080`).
3. **Redirect URLs**: Add the specific path to your login page.
   - *Example*: `https://trading-bot-xyz.a.run.app/login.html`
4. Click **Save**.

---

## Step 3: Backend Configuration (Secrets)

To secure the backend and allow it to verify tokens, you must set the following environment variables (or GitHub Secrets).

### 3.1 Required Environment Variables

| Variable | Source | Purpose |
| :--- | :--- | :--- |
| `SUPABASE_URL` | Supabase > Project Settings > API | Used to derive the JWKS endpoint for JWT verification. |
| `SUPABASE_ANON_KEY` | Supabase > Project Settings > API | Used by the frontend to initialize the Supabase client. |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase > Project Settings > API | Used for administrative database operations. |
| `ALLOWED_EMAILS` | Manual | Comma-separated list of emails allowed to access the bot (e.g. `user1@gmail.com,user2@gmail.com`). |

### 3.2 GitHub Secrets for Cloud Run
Update your repository's GitHub Secrets with these identical keys. The `.github/workflows/deploy-cloud-run.yml` will automatically inject them into the production container.

---

## Step 4: Verification

1. **Local Test**: Run the app locally. Navigate to `http://localhost:8080/login.html`.
2. **Login**: Click **Continue with Google**.
3. **Authentication**: You should be redirected back to the dashboard.
4. **Log Validation**: Check your backend terminal. You should see:
   `[AUTH SUCCESS] JWT verified for user@gmail.com on /api/strategies`

> [!TIP]
> If you get an "Origin mismatch" or "Redirect URI mismatch" error during login, double-check that the redirect URI in **Google Cloud Console** exactly matches the one provided in the **Supabase Dashboard**.
