package com.hemasundar.services;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import com.hemasundar.pojos.IVDataPoint;
import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Service to interact with Google Sheets API for storing IV data.
 * Handles authentication, sheet creation, and data appending.
 */
@Log4j2
public class GoogleSheetsService {

        private static final String APPLICATION_NAME = "Trading Bot IV Tracker";
        private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
        private static final String TOKENS_DIRECTORY_PATH = "tokens";
        private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
        private static final String CREDENTIALS_FILE_PATH = "credentials.json";

        private final Sheets sheetsService;
        private final String spreadsheetId;

        /**
         * Constructor initializes Google Sheets service with authentication.
         *
         * @param spreadsheetId Google Sheets spreadsheet ID
         * @throws IOException              if credentials file is not found
         * @throws GeneralSecurityException if there's a security issue
         */
        public GoogleSheetsService(String spreadsheetId) throws IOException, GeneralSecurityException {
                this.spreadsheetId = spreadsheetId;
                final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
                this.sheetsService = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                                .setApplicationName(APPLICATION_NAME)
                                .build();
                log.info("Google Sheets Service initialized for spreadsheet: {}", spreadsheetId);
        }

        /**
         * Creates credentials for Google Sheets API.
         * Supports two authentication methods:
         * 1. Service Account (for CI/CD) - uses GOOGLE_SERVICE_ACCOUNT_JSON env var
         * 2. OAuth2 (for local dev) - uses credentials.json file and opens browser
         *
         * @param HTTP_TRANSPORT The network HTTP Transport.
         * @return An authorized Credential object.
         * @throws IOException If credentials cannot be loaded.
         */
        private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
                // Check if running in CI/CD with service account
                String serviceAccountJson = System.getenv("GOOGLE_SERVICE_ACCOUNT_JSON");

                if (serviceAccountJson != null && !serviceAccountJson.isEmpty()) {
                        // Use Service Account authentication (GitHub Actions)
                        log.info("Using Service Account authentication for automated environment");
                        return GoogleCredential.fromStream(
                                        new ByteArrayInputStream(serviceAccountJson.getBytes()),
                                        HTTP_TRANSPORT,
                                        JSON_FACTORY)
                                        .createScoped(SCOPES);
                }

                // Use OAuth2 authentication (local development)
                log.info("Using OAuth2 authentication for local development");
                File credentialsFile = new File(CREDENTIALS_FILE_PATH);
                if (!credentialsFile.exists()) {
                        throw new IOException("Credentials file not found: " + CREDENTIALS_FILE_PATH +
                                        "\nPlease follow the setup instructions to create this file.");
                }

                GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                                JSON_FACTORY,
                                new InputStreamReader(new FileInputStream(credentialsFile)));

                // Build flow and trigger user authorization request
                GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                                .setAccessType("offline")
                                .build();

                LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
                return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        }

        /**
         * Gets or creates a sheet (tab) for a specific symbol.
         *
         * @param symbol Stock symbol
         * @return Sheet ID
         * @throws IOException if API call fails
         */
        public Integer getOrCreateSheet(String symbol) throws IOException {
                // Get existing sheets
                Spreadsheet spreadsheet = sheetsService.spreadsheets()
                                .get(spreadsheetId)
                                .execute();

                // Check if sheet already exists
                for (Sheet sheet : spreadsheet.getSheets()) {
                        if (sheet.getProperties().getTitle().equals(symbol)) {
                                log.debug("[{}] Sheet already exists with ID: {}", symbol,
                                                sheet.getProperties().getSheetId());
                                return sheet.getProperties().getSheetId();
                        }
                }

                // Create new sheet
                log.info("[{}] Creating new sheet", symbol);
                AddSheetRequest addSheetRequest = new AddSheetRequest()
                                .setProperties(new SheetProperties().setTitle(symbol));

                BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                                .setRequests(Collections.singletonList(new Request().setAddSheet(addSheetRequest)));

                BatchUpdateSpreadsheetResponse response = sheetsService.spreadsheets()
                                .batchUpdate(spreadsheetId, batchRequest)
                                .execute();

                Integer sheetId = response.getReplies().get(0).getAddSheet().getProperties().getSheetId();

                // Add header row
                addHeaderRow(symbol);

                log.info("[{}] Created new sheet with ID: {}", symbol, sheetId);
                return sheetId;
        }

        /**
         * Adds header row to a new sheet.
         *
         * @param symbol Stock symbol (sheet name)
         * @throws IOException if API call fails
         */
        private void addHeaderRow(String symbol) throws IOException {
                List<Object> headers = Arrays.asList(
                                "Date",
                                "Strike",
                                "DTE",
                                "Expiry Date",
                                "PUT IV (%)",
                                "CALL IV (%)",
                                "Underlying Price");

                ValueRange valueRange = new ValueRange()
                                .setValues(Collections.singletonList(headers));

                sheetsService.spreadsheets().values()
                                .append(spreadsheetId, symbol + "!A1", valueRange)
                                .setValueInputOption("RAW")
                                .setInsertDataOption("OVERWRITE")
                                .execute();
        }

        /**
         * Appends or updates IV data point to the symbol's sheet.
         * If an entry for today's date already exists, it will be updated instead of
         * duplicated.
         * Implements retry logic with exponential backoff for rate limit errors.
         *
         * @param dataPoint IV data point to append or update
         * @throws IOException if API call fails after retries
         */
        public void appendIVData(IVDataPoint dataPoint) throws IOException {
                String symbol = dataPoint.getSymbol();
                String todayDate = dataPoint.getCurrentDate().toString();

                // Ensure sheet exists
                getOrCreateSheet(symbol);

                // Check if today's entry already exists
                Integer existingRowIndex = findRowByDate(symbol, todayDate);

                // Prepare data row
                List<Object> row = Arrays.asList(
                                dataPoint.getCurrentDate().toString(),
                                dataPoint.getStrike(),
                                dataPoint.getDte(),
                                dataPoint.getExpiryDate(),
                                dataPoint.getAtmPutIV(),
                                dataPoint.getAtmCallIV(),
                                dataPoint.getUnderlyingPrice());

                ValueRange valueRange = new ValueRange()
                                .setValues(Collections.singletonList(row));

                // Retry logic for rate limit errors
                int maxRetries = 3;
                int retryCount = 0;

                while (retryCount <= maxRetries) {
                        try {
                                if (existingRowIndex != null) {
                                        // Update existing row
                                        String range = symbol + "!A" + existingRowIndex + ":G" + existingRowIndex;
                                        sheetsService.spreadsheets().values()
                                                        .update(spreadsheetId, range, valueRange)
                                                        .setValueInputOption("USER_ENTERED")
                                                        .execute();

                                        log.info("[{}] Updated existing entry for {} - PUT: {}%, CALL: {}%",
                                                        symbol, todayDate, dataPoint.getAtmPutIV(),
                                                        dataPoint.getAtmCallIV());
                                } else {
                                        // Append new row
                                        sheetsService.spreadsheets().values()
                                                        .append(spreadsheetId, symbol + "!A:G", valueRange)
                                                        .setValueInputOption("USER_ENTERED")
                                                        .setInsertDataOption("INSERT_ROWS")
                                                        .execute();

                                        log.info("[{}] Appended new entry for {} - PUT: {}%, CALL: {}%",
                                                        symbol, todayDate, dataPoint.getAtmPutIV(),
                                                        dataPoint.getAtmCallIV());
                                }

                                // Success - exit retry loop
                                break;

                        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
                                // Check if it's a rate limit error (429)
                                if (e.getStatusCode() == 429 && retryCount < maxRetries) {
                                        retryCount++;
                                        long waitTime = (long) Math.pow(2, retryCount) * 1000; // Exponential backoff:
                                                                                               // 2s, 4s, 8s
                                        log.warn("[{}] Rate limit exceeded, retry {}/{} after {}ms",
                                                        symbol, retryCount, maxRetries, waitTime);

                                        try {
                                                Thread.sleep(waitTime);
                                        } catch (InterruptedException ie) {
                                                Thread.currentThread().interrupt();
                                                throw new IOException("Interrupted while waiting for retry", ie);
                                        }
                                } else {
                                        // Not a rate limit error or max retries reached
                                        throw new IOException("Failed to write to Google Sheets: " + e.getMessage(), e);
                                }
                        }
                }
        }

        /**
         * Finds the row index for a specific date in a symbol's sheet.
         *
         * @param symbol Stock symbol (sheet name)
         * @param date   Date to search for (YYYY-MM-DD format)
         * @return Row index (1-based) if found, null otherwise
         * @throws IOException if API call fails
         */
        private Integer findRowByDate(String symbol, String date) throws IOException {
                try {
                        // Read all dates from column A
                        ValueRange response = sheetsService.spreadsheets().values()
                                        .get(spreadsheetId, symbol + "!A:A")
                                        .execute();

                        List<List<Object>> values = response.getValues();
                        if (values == null || values.isEmpty()) {
                                return null;
                        }

                        // Search for matching date (skip header row at index 0)
                        for (int i = 1; i < values.size(); i++) {
                                List<Object> row = values.get(i);
                                if (!row.isEmpty() && date.equals(row.get(0).toString())) {
                                        return i + 1; // Return 1-based row index
                                }
                        }

                        return null; // Date not found
                } catch (Exception e) {
                        log.warn("[{}] Error searching for date {}: {}", symbol, date, e.getMessage());
                        return null; // If error, treat as not found and append
                }
        }
}
