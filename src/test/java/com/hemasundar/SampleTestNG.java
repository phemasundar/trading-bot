package com.hemasundar;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.pojos.OptionChainResponse;
import com.hemasundar.pojos.RefreshToken;
import com.hemasundar.pojos.Securities;
import com.hemasundar.pojos.TestConfig;
import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.JavaUtils;
import com.hemasundar.pojos.CallCreditSpread;
import com.hemasundar.pojos.IronCondor;
import com.hemasundar.pojos.PutCreditSpread;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class SampleTestNG {
    public static void main(String[] args) {
        // 1. YOUR CONFIGURATION
        String redirectUri = "https://127.0.0.1";

        // 2. GET THE URL FROM BROWSER
        Scanner scanner = new Scanner(System.in);
        System.out.println("1. Log in via your browser. \nhttps://api.schwabapi.com/v1/oauth/authorize?client_id="
                + TestConfig.getInstance().appKey() + "&redirect_uri=https://127.0.0.1");
        System.out.println(
                "2. Copy the FULL URL of the page you are redirected to (the one starting with https://127.0.0.1).");
        System.out.print("Paste the full URL here: ");
        String fullUrl = scanner.nextLine();

        // 3. AUTO-EXTRACT AND CLEAN THE CODE
        String code = fullUrl.split("code=")[1].split("&")[0];
        code = code.replace("%40", "@"); // Vital fix for Schwab's encoding

        System.out.println("Extracted Code: " + code);
        System.out.println("Exchanging... (9-second timer active)");

        // 4. REST-ASSURED REQUEST
        Response response = RestAssured.given()
                .auth()
                .preemptive()
                .basic(TestConfig.getInstance().appKey(), TestConfig.getInstance().ppSecret())
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "authorization_code")
                .formParam("code", code)
                .formParam("redirect_uri", redirectUri)
                .when()
                .log().all()
                .post("https://api.schwabapi.com/v1/oauth/token");

        System.out.println("Response Code: " + response.statusCode());
        System.out.println("Response Body: \n" + response.asPrettyString());
        RefreshToken refreshToken = JavaUtils.convertJsonToPojo(response.asString(), RefreshToken.class);
    }

    @Test
    public void getOptionChainData() throws IOException {
        Securities securities = JavaUtils.convertYamlToPojo(Files.readString(FilePaths.securitiesConfig),
                Securities.class);
        securities.securities().forEach(currentSecurity -> {
            System.out.println("---------------------------------------------------------");
            System.out.println("--------------------------" + currentSecurity + "--------------------------");
            System.out.println("---------------------------------------------------------");


            OptionChainResponse optionChainResponse = ThinkOrSwinAPIs.getOptionChainResponse(currentSecurity);

            int targetDTE = 60;
            String targetExpiryDate = optionChainResponse.getExpiryDateBasedOnDTE(targetDTE);

            // Enable this to filter the trades if any earnings are there
            /*earningsCalendarResponse earningsResponse = FinnHubAPIs.getEarningsByTicker(currentSecurity,
                    LocalDate.parse(targetExpiryDate));
            if (CollectionUtils.isEmpty(earningsResponse.getEarningsCalendar())) {
                System.out.println("Skipping " + currentSecurity + " due to upcoming earnings on "
                        + earningsResponse.getEarningsCalendar().get(0).getDate());
                return;
            }*/

            Map<String, List<OptionChainResponse.OptionData>> putMap = optionChainResponse
                    .getOptionDataForASpecificExpiryDate("PUT", targetExpiryDate);

//            findValidPutCreditSpreads(putMap, optionChainResponse.getUnderlyingPrice(), 0.2, 1000, 12);

            Map<String, List<OptionChainResponse.OptionData>> callMap = optionChainResponse
                    .getOptionDataForASpecificExpiryDate("CALL", targetExpiryDate);

            findValidIronCondors(putMap, callMap, optionChainResponse.getUnderlyingPrice(), 0.15, 1000, 24);
        });

    }

    /**
     * Finds Put Credit Spreads based on 12% profit/loss ratio, $1000 max risk, and
     * 0.6 delta.
     *
     * @param putMap The map of strike prices (as Strings) to OptionData lists.
     */
    public void findValidPutCreditSpreads(Map<String, List<OptionChainResponse.OptionData>> putMap, double currentPrice,
                                          double maxDelta, double maxLossLimit, int returnOnRiskPercentage) {
        List<PutCreditSpread> spreads = getValidPutCreditSpreads(putMap, currentPrice, maxDelta, maxLossLimit,
                returnOnRiskPercentage);
        spreads.forEach(System.out::println);
    }

    public List<PutCreditSpread> getValidPutCreditSpreads(Map<String, List<OptionChainResponse.OptionData>> putMap,
                                                          double currentPrice, double maxDelta, double maxLossLimit, int returnOnRiskPercentage) {

        List<PutCreditSpread> spreads = new ArrayList<>();

        List<Double> sortedStrikes = putMap.keySet().stream()
                .map(Double::parseDouble)
                .sorted()
                .collect(Collectors.toList());

        for (int i = 0; i < sortedStrikes.size(); i++) {
            double shortStrikePrice = sortedStrikes.get(i);
            OptionChainResponse.OptionData shortPut = putMap.get(String.valueOf(shortStrikePrice)).get(0);

            if (Math.abs(shortPut.getDelta()) > maxDelta) {
                continue;
            }

            for (int j = 0; j < i; j++) {
                double longStrikePrice = sortedStrikes.get(j);
                OptionChainResponse.OptionData longPut = putMap.get(String.valueOf(longStrikePrice)).get(0);

                double netCredit = (shortPut.getBid() - longPut.getAsk()) * 100;

                if (netCredit <= 0)
                    continue;

                double strikeWidth = (shortStrikePrice - longStrikePrice) * 100;
                double maxLoss = strikeWidth - netCredit;

                if (maxLoss > maxLossLimit)
                    continue;

                double requiredProfit = maxLoss * ((double) returnOnRiskPercentage / 100);

                if (netCredit >= requiredProfit) {
                    double breakEvenPrice = shortPut.getStrikePrice() - (netCredit / 100);
                    double breakEvenPercentage = ((currentPrice - breakEvenPrice) / currentPrice) * 100;
                    double returnOnRisk = (netCredit / maxLoss) * 100;

                    spreads.add(PutCreditSpread.builder()
                            .shortPut(shortPut)
                            .longPut(longPut)
                            .netCredit(netCredit)
                            .maxLoss(maxLoss)
                            .breakEvenPrice(breakEvenPrice)
                            .breakEvenPercentage(breakEvenPercentage)
                            .returnOnRisk(returnOnRisk)
                            .build());
                }
            }
        }
        return spreads;
    }

    public List<CallCreditSpread> getValidCallCreditSpreads(Map<String, List<OptionChainResponse.OptionData>> callMap,
                                                            double currentPrice, double maxDelta, double maxLossLimit, int returnOnRiskPercentage) {
        List<CallCreditSpread> spreads = new ArrayList<>();

        List<Double> sortedStrikes = callMap.keySet().stream()
                .map(Double::parseDouble)
                .sorted()
                .collect(Collectors.toList());

        // For Call Spreads, Short Call lower strike, Long Call higher strike
        // Iterate for Short Call
        for (int i = 0; i < sortedStrikes.size(); i++) {
            double shortStrikePrice = sortedStrikes.get(i);
            OptionChainResponse.OptionData shortCall = callMap.get(String.valueOf(shortStrikePrice)).get(0);

            // Short Call should be OTM (Strike > Current Price)
            if (shortStrikePrice <= currentPrice)
                continue;

            if (Math.abs(shortCall.getDelta()) > maxDelta)
                continue;

            // Iterate for Long Call (Higher Strike)
            for (int j = i + 1; j < sortedStrikes.size(); j++) {
                double longStrikePrice = sortedStrikes.get(j);
                OptionChainResponse.OptionData longCall = callMap.get(String.valueOf(longStrikePrice)).get(0);

                double netCredit = (shortCall.getBid() - longCall.getAsk()) * 100;

                if (netCredit <= 0)
                    continue;

                double strikeWidth = (longStrikePrice - shortStrikePrice) * 100;
                double maxLoss = strikeWidth - netCredit;

                if (maxLoss > maxLossLimit)
                    continue;

                double requiredProfit = maxLoss * ((double) returnOnRiskPercentage / 100);

                if (netCredit >= requiredProfit) {
                    double breakEvenPrice = shortCall.getStrikePrice() + (netCredit / 100);
                    double breakEvenPercentage = ((breakEvenPrice - currentPrice) / currentPrice) * 100;
                    double returnOnRisk = (netCredit / maxLoss) * 100;

                    spreads.add(CallCreditSpread.builder()
                            .shortCall(shortCall)
                            .longCall(longCall)
                            .netCredit(netCredit)
                            .maxLoss(maxLoss)
                            .breakEvenPrice(breakEvenPrice)
                            .breakEvenPercentage(breakEvenPercentage)
                            .returnOnRisk(returnOnRisk)
                            .build());
                }
            }
        }
        return spreads;
    }

    public void findValidIronCondors(Map<String, List<OptionChainResponse.OptionData>> putMap,
                                     Map<String, List<OptionChainResponse.OptionData>> callMap,
                                     double currentPrice,
                                     double maxDelta, double maxLossLimit, int minExpectedReturnOnRiskPercentage) {

        // Get individual valid spreads (relaxing the individual limits slightly if
        // needed, but for now using strict same limits)
        // Pass 0 as return percentage to capture all valid spreads.
        // We will filter based on the TOTAL Iron Condor return later.
        List<PutCreditSpread> putSpreads = getValidPutCreditSpreads(putMap, currentPrice, maxDelta, maxLossLimit, 0);
        List<CallCreditSpread> callSpreads = getValidCallCreditSpreads(callMap, currentPrice, maxDelta, maxLossLimit,
                0);

        List<IronCondor> condors = new ArrayList<>();

        for (PutCreditSpread putSpread : putSpreads) {
            for (CallCreditSpread callSpread : callSpreads) {
                // Ensure no overlap (Short Put Strike < Short Call Strike is guaranteed by
                // Delta checks/OTM logic usually, but specific check is good)
                if (putSpread.getShortPut().getStrikePrice() >= callSpread.getShortCall().getStrikePrice())
                    continue;

                double totalCredit = putSpread.getNetCredit() + callSpread.getNetCredit();

                // Max Loss for Iron Condor is max(PutSpreadRisk, CallSpreadRisk) -
                // CreditReceived?
                // Actually standard calculation: Width of widest wing * 100 - Total Credit.
                // Or simplified: Max(PutWidth*100, CallWidth*100) - Total Credit.
                double putWidth = (putSpread.getShortPut().getStrikePrice() - putSpread.getLongPut().getStrikePrice())
                        * 100;
                double callWidth = (callSpread.getLongCall().getStrikePrice()
                        - callSpread.getShortCall().getStrikePrice()) * 100;
                double maxRisk = Math.max(putWidth, callWidth) - totalCredit;

                if (maxRisk > maxLossLimit)
                    continue;

                double requiredProfit = maxRisk * ((double) minExpectedReturnOnRiskPercentage / 100);

                if (totalCredit < requiredProfit)
                    continue;

                double returnOnRisk = (totalCredit / maxRisk) * 100;
                double lowerBreakEven = putSpread.getShortPut().getStrikePrice() - (totalCredit / 100);
                double upperBreakEven = callSpread.getShortCall().getStrikePrice() + (totalCredit / 100);

                double lowerBreakEvenPercentage = ((currentPrice - lowerBreakEven) / currentPrice) * 100;
                double upperBreakEvenPercentage = ((upperBreakEven - currentPrice) / currentPrice) * 100;

                condors.add(IronCondor.builder()
                        .putLeg(putSpread)
                        .callLeg(callSpread)
                        .netCredit(totalCredit)
                        .maxLoss(maxRisk)
                        .returnOnRisk(returnOnRisk)
                        .lowerBreakEven(lowerBreakEven)
                        .upperBreakEven(upperBreakEven)
                        .lowerBreakEvenPercentage(lowerBreakEvenPercentage)
                        .upperBreakEvenPercentage(upperBreakEvenPercentage)
                        .build());
            }
        }

        condors.forEach(System.out::println);
    }

}
