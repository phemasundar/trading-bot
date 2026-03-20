package com.hemasundar.services;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import com.hemasundar.pojos.IVDataPoint;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class GoogleSheetsServiceTest {

    private Sheets mockSheets;
    private Sheets.Spreadsheets mockSpreadsheets;
    private Sheets.Spreadsheets.Values mockValues;
    private GoogleSheetsService googleSheetsService;
    private final String SPREADSHEET_ID = "test-spreadsheet-id";

    @BeforeMethod
    public void setUp() {
        mockSheets = mock(Sheets.class);
        mockSpreadsheets = mock(Sheets.Spreadsheets.class);
        mockValues = mock(Sheets.Spreadsheets.Values.class);
        
        when(mockSheets.spreadsheets()).thenReturn(mockSpreadsheets);
        when(mockSpreadsheets.values()).thenReturn(mockValues);
        
        googleSheetsService = new GoogleSheetsService(SPREADSHEET_ID, mockSheets);
    }

    @Test
    public void testGetOrCreateSheet_Exists() throws IOException {
        Sheets.Spreadsheets.Get mockGet = mock(Sheets.Spreadsheets.Get.class);
        when(mockSpreadsheets.get(SPREADSHEET_ID)).thenReturn(mockGet);
        
        Spreadsheet spreadsheet = new Spreadsheet();
        Sheet sheet = new Sheet().setProperties(new SheetProperties().setTitle("AAPL").setSheetId(123));
        spreadsheet.setSheets(Collections.singletonList(sheet));
        
        when(mockGet.execute()).thenReturn(spreadsheet);

        Integer sheetId = googleSheetsService.getOrCreateSheet("AAPL");
        assertEquals(sheetId, Integer.valueOf(123));
    }

    @Test
    public void testGetOrCreateSheet_CreatesNew() throws IOException {
        Sheets.Spreadsheets.Get mockGet = mock(Sheets.Spreadsheets.Get.class);
        when(mockSpreadsheets.get(SPREADSHEET_ID)).thenReturn(mockGet);
        
        Spreadsheet spreadsheet = new Spreadsheet();
        spreadsheet.setSheets(new ArrayList<>()); // No sheets exist
        when(mockGet.execute()).thenReturn(spreadsheet);

        Sheets.Spreadsheets.BatchUpdate mockBatchUpdate = mock(Sheets.Spreadsheets.BatchUpdate.class);
        when(mockSpreadsheets.batchUpdate(anyString(), any())).thenReturn(mockBatchUpdate);
        
        BatchUpdateSpreadsheetResponse response = new BatchUpdateSpreadsheetResponse();
        Response reply = new Response().setAddSheet(new AddSheetResponse().setProperties(new SheetProperties().setSheetId(456)));
        response.setReplies(Collections.singletonList(reply));
        when(mockBatchUpdate.execute()).thenReturn(response);

        // Mock for addHeaderRow internal call
        Sheets.Spreadsheets.Values.Append mockAppend = mock(Sheets.Spreadsheets.Values.Append.class);
        when(mockValues.append(anyString(), anyString(), any())).thenReturn(mockAppend);
        when(mockAppend.setValueInputOption(anyString())).thenReturn(mockAppend);
        when(mockAppend.setInsertDataOption(anyString())).thenReturn(mockAppend);
        when(mockAppend.execute()).thenReturn(new AppendValuesResponse());

        Integer sheetId = googleSheetsService.getOrCreateSheet("TSLA");
        assertEquals(sheetId, Integer.valueOf(456));
    }

    @Test
    public void testAppendIVData_AppendNew() throws IOException {
        // Mock findRowByDate returning null
        Sheets.Spreadsheets.Values.Get mockValuesGet = mock(Sheets.Spreadsheets.Values.Get.class);
        when(mockValues.get(anyString(), anyString())).thenReturn(mockValuesGet);
        ValueRange dateValues = new ValueRange();
        dateValues.setValues(Collections.emptyList());
        when(mockValuesGet.execute()).thenReturn(dateValues);

        // Mock getOrCreateSheet (recursive call but we'll mock spreadsheets().get())
        Sheets.Spreadsheets.Get mockSpreadsheetsGet = mock(Sheets.Spreadsheets.Get.class);
        when(mockSpreadsheets.get(SPREADSHEET_ID)).thenReturn(mockSpreadsheetsGet);
        Spreadsheet spreadsheet = new Spreadsheet();
        spreadsheet.setSheets(Collections.singletonList(new Sheet().setProperties(new SheetProperties().setTitle("AAPL").setSheetId(1))));
        when(mockSpreadsheetsGet.execute()).thenReturn(spreadsheet);

        // Mock append
        Sheets.Spreadsheets.Values.Append mockAppend = mock(Sheets.Spreadsheets.Values.Append.class);
        when(mockValues.append(anyString(), anyString(), any())).thenReturn(mockAppend);
        when(mockAppend.setValueInputOption(anyString())).thenReturn(mockAppend);
        when(mockAppend.setInsertDataOption(anyString())).thenReturn(mockAppend);
        when(mockAppend.execute()).thenReturn(new AppendValuesResponse());

        IVDataPoint dp = new IVDataPoint();
        dp.setSymbol("AAPL");
        dp.setCurrentDate(LocalDate.now());
        dp.setStrike(150.0);
        dp.setAtmPutIV(30.0);
        dp.setAtmCallIV(25.0);

        googleSheetsService.appendIVData(dp);
        verify(mockValues).append(eq(SPREADSHEET_ID), eq("AAPL!A:G"), any());
    }

    @Test
    public void testAppendIVData_UpdateExisting() throws IOException {
        // Mock findRowByDate returning row 5
        Sheets.Spreadsheets.Values.Get mockValuesGet = mock(Sheets.Spreadsheets.Values.Get.class);
        when(mockValues.get(anyString(), anyString())).thenReturn(mockValuesGet);
        
        List<List<Object>> values = new ArrayList<>();
        values.add(Collections.singletonList("Date")); // Header
        values.add(Collections.singletonList("2024-01-01"));
        values.add(Collections.singletonList(LocalDate.now().toString())); // Match at row 3 (0-indexed is 2)
        
        ValueRange valueRange = new ValueRange();
        valueRange.setValues(values);
        when(mockValuesGet.execute()).thenReturn(valueRange);

        // Mock getOrCreateSheet
        Sheets.Spreadsheets.Get mockSpreadsheetsGet = mock(Sheets.Spreadsheets.Get.class);
        when(mockSpreadsheets.get(SPREADSHEET_ID)).thenReturn(mockSpreadsheetsGet);
        Spreadsheet spreadsheet = new Spreadsheet();
        spreadsheet.setSheets(Collections.singletonList(new Sheet().setProperties(new SheetProperties().setTitle("AAPL"))));
        when(mockSpreadsheetsGet.execute()).thenReturn(spreadsheet);

        // Mock update
        Sheets.Spreadsheets.Values.Update mockUpdate = mock(Sheets.Spreadsheets.Values.Update.class);
        when(mockValues.update(anyString(), anyString(), any())).thenReturn(mockUpdate);
        when(mockUpdate.setValueInputOption(anyString())).thenReturn(mockUpdate);
        when(mockUpdate.execute()).thenReturn(new UpdateValuesResponse());

        IVDataPoint dp = new IVDataPoint();
        dp.setSymbol("AAPL");
        dp.setCurrentDate(LocalDate.now());
        
        googleSheetsService.appendIVData(dp);
        verify(mockValues).update(eq(SPREADSHEET_ID), eq("AAPL!A3:G3"), any());
    }

    @Test
    public void testReorderSheets() throws IOException {
        Sheets.Spreadsheets.Get mockGet = mock(Sheets.Spreadsheets.Get.class);
        when(mockSpreadsheets.get(SPREADSHEET_ID)).thenReturn(mockGet);
        
        Spreadsheet spreadsheet = new Spreadsheet();
        spreadsheet.setSheets(List.of(
            new Sheet().setProperties(new SheetProperties().setTitle("AAPL").setSheetId(1)),
            new Sheet().setProperties(new SheetProperties().setTitle("MSFT").setSheetId(2))
        ));
        when(mockGet.execute()).thenReturn(spreadsheet);

        Sheets.Spreadsheets.BatchUpdate mockBatchUpdate = mock(Sheets.Spreadsheets.BatchUpdate.class);
        when(mockSpreadsheets.batchUpdate(anyString(), any())).thenReturn(mockBatchUpdate);
        when(mockBatchUpdate.execute()).thenReturn(new BatchUpdateSpreadsheetResponse());

        googleSheetsService.reorderSheets(List.of("MSFT", "AAPL"));
        verify(mockSpreadsheets).batchUpdate(eq(SPREADSHEET_ID), any());
    }

    @Test
    public void testAppendIVData_RetryOnRateLimit() throws IOException {
        // Mock findRowByDate returning null
        Sheets.Spreadsheets.Values.Get mockValuesGet = mock(Sheets.Spreadsheets.Values.Get.class);
        when(mockValues.get(anyString(), anyString())).thenReturn(mockValuesGet);
        ValueRange dateValues = new ValueRange();
        dateValues.setValues(Collections.emptyList());
        when(mockValuesGet.execute()).thenReturn(dateValues);

        // Mock getOrCreateSheet
        Sheets.Spreadsheets.Get mockSpreadsheetsGet = mock(Sheets.Spreadsheets.Get.class);
        when(mockSpreadsheets.get(SPREADSHEET_ID)).thenReturn(mockSpreadsheetsGet);
        Spreadsheet spreadsheet = new Spreadsheet();
        spreadsheet.setSheets(Collections.singletonList(new Sheet().setProperties(new SheetProperties().setTitle("AAPL"))));
        when(mockSpreadsheetsGet.execute()).thenReturn(spreadsheet);

        // Final success mock
        Sheets.Spreadsheets.Values.Append mockAppend = mock(Sheets.Spreadsheets.Values.Append.class);
        when(mockValues.append(anyString(), anyString(), any())).thenReturn(mockAppend);
        when(mockAppend.setValueInputOption(anyString())).thenReturn(mockAppend);
        when(mockAppend.setInsertDataOption(anyString())).thenReturn(mockAppend);
        
        // Mock exception on first call, success on second
        com.google.api.client.googleapis.json.GoogleJsonResponseException limitExceeded = 
            mock(com.google.api.client.googleapis.json.GoogleJsonResponseException.class);
        when(limitExceeded.getStatusCode()).thenReturn(429);
        when(limitExceeded.getMessage()).thenReturn("Rate limit exceeded");

        when(mockAppend.execute())
            .thenThrow(limitExceeded)
            .thenReturn(new AppendValuesResponse());

        IVDataPoint dp = new IVDataPoint();
        dp.setSymbol("AAPL");
        dp.setCurrentDate(LocalDate.now());

        googleSheetsService.appendIVData(dp);
        // Should have called execute twice
        verify(mockAppend, times(2)).execute();
    }
}
