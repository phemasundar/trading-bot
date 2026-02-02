package com.hemasundar.services;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import com.hemasundar.pojos.IVDataPoint;
import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Service to interact with Google Sheets API for storing IV data.
 * Uses Service Account authentication for both local and CI/CD environments.
 */
@Log4j2
public class GoogleSheetsService {

        private static final String APPLICATION_NAME = "Trading Bot IV Tracker";
        private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
        private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);

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
         * Creates credentials for Google Sheets API using Service Account.
         * Authentication sources (in order of priority):
         * 1. GOOGLE_SERVICE_ACCOUNT_JSON environment variable (for CI/CD)
         * 2. service-account.json file in project root (for local development)
         *
         * @param HTTP_TRANSPORT The network HTTP Transport.
         * @return An authorized Credential object.
         * @throws IOException If credentials cannot be loaded.
         */
        private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
                String serviceAccountJson = System.getenv("GOOGLE_SERVICE_ACCOUNT_JSON");

                if (serviceAccountJson != null && !serviceAccountJson.isEmpty()) {
                        // Use environment variable (GitHub Actions / CI/CD)
                        log.info("Using Service Account authentication from environment variable");
                        return GoogleCredential.fromStream(
                                        new ByteArrayInputStream(serviceAccountJson.getBytes()),
                                        HTTP_TRANSPORT,
                                        JSON_FACTORY)
                                        .createScoped(SCOPES);
                }

                // Fall back to file (local development)
                java.io.File serviceAccountFile = new java.io.File("service-account.json");
                if (serviceAccountFile.exists()) {
                        log.info("Using Service Account authentication from service-account.json file");
                        try (java.io.FileInputStream fis = new java.io.FileInputStream(serviceAccountFile)) {
                                return GoogleCredential.fromStream(fis, HTTP_TRANSPORT, JSON_FACTORY)
                                                .createScoped(SCOPES);
                        }
                }

                // Neither environment variable nor file found
                throw new IOException("Service Account credentials not found.\n" +
                                "Please either:\n" +
                                "  1. Set GOOGLE_SERVICE_ACCOUNT_JSON environment variable, OR\n" +
                                "  2. Place service-account.json file in project root directory\n" +
                                "See SETUP_GUIDE.md for detailed instructions.");
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
