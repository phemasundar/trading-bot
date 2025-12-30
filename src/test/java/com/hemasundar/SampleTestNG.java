package com.hemasundar;

import com.hemasundar.pojos.OptionChainResponse;
import com.hemasundar.pojos.RefreshToken;
import com.hemasundar.pojos.Securities;
import com.hemasundar.pojos.TestConfig;
import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.JavaUtils;
import com.hemasundar.utils.TokenProvider;
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
            OptionChainResponse optionChainResponse = getOptionChainResponse(currentSecurity);

            String targetExpiryDate = optionChainResponse.getExpiryDateBasedOnDTE(30);

            Map<String, List<OptionChainResponse.OptionData>> putMap = optionChainResponse
                    .getOptionDataForASpecificExpiryDate("PUT", targetExpiryDate);

            findValidPutCreditSpreads(putMap, optionChainResponse.getUnderlyingPrice(), 0.2, 1000, 12);
        });

    }

    private static OptionChainResponse getOptionChainResponse(String symbol) {
        Response response = RestAssured.given()
                .header("accept", "application/json")
                .header("Authorization", "Bearer " + TokenProvider.INSTANCE.getAccessToken())
                .queryParam("symbol", symbol)
                .queryParam("strategy", "SINGLE")
                // .log().all()
                .get("https://api.schwabapi.com/marketdata/v1/chains");

        System.out.println("Response code for Option chain API: " + response.getStatusCode());
        // System.out.println("Response: \n" + response.asPrettyString());

        OptionChainResponse optionChainResponse = JavaUtils.convertJsonToPojo(response.asString(),
                OptionChainResponse.class);
        System.out.println("Current Maket Price: " + optionChainResponse.getUnderlyingPrice());
        return optionChainResponse;
    }

    /**
     * Finds Put Credit Spreads based on 12% profit/loss ratio, $1000 max risk, and
     * 0.6 delta.
     *
     * @param putMap The map of strike prices (as Strings) to OptionData lists.
     */
    public void findValidPutCreditSpreads(Map<String, List<OptionChainResponse.OptionData>> putMap, double currentPrice,
            double maxDelta, double maxLossLimit, int returnOnRiskPercentage) {

        List<PutCreditSpread> spreads = new ArrayList<>();

        /*
         * 1. Convert the Map keys into a sorted list of numeric strike prices
         * (Ascending).
         * Sorting allows us to easily distinguish between the higher (Short) and lower
         * (Long) strikes.
         */
        List<Double> sortedStrikes = putMap.keySet().stream()
                .map(Double::parseDouble)
                .sorted()
                .collect(Collectors.toList());

        /*
         * 2. Outer loop: Iterate through potential 'Short Put' strikes.
         * In a Put Credit Spread, the Short Put is the one with the HIGHER strike
         * price.
         */
        for (int i = 0; i < sortedStrikes.size(); i++) {
            double shortStrikePrice = sortedStrikes.get(i);

            /*
             * 3. Retrieve the OptionData for the Short Put. We assume the list contains at
             * least one entry.
             */
            OptionChainResponse.OptionData shortPut = putMap.get(String.valueOf(shortStrikePrice)).get(0);

            // 4. Filter: Short PUT Delta must be more than 0.6 in absolute terms.
            // We use Math.abs to treat -0.65 as 0.65.
            if (Math.abs(shortPut.getDelta()) > maxDelta) {
                continue; // Skip strikes that don't meet the aggressiveness requirement.
            }

            // 5. Inner loop: Iterate through potential 'Long Put' strikes.
            // The Long Put must have a LOWER strike price than the Short Put (indices 0 to
            // i-1).
            for (int j = 0; j < i; j++) {
                double longStrikePrice = sortedStrikes.get(j);
                OptionChainResponse.OptionData longPut = putMap.get(String.valueOf(longStrikePrice)).get(0);

                // 6. Calculate Net Credit (Max Profit).
                // We sell the Short at the 'Bid' and buy the Long at the 'Ask'.
                // Formula: (Bid of Short - Ask of Long) * 100
                double netCredit = (shortPut.getBid() - longPut.getAsk()) * 100;

                // 7. Ignore invalid data: If netCredit is zero or negative, it's not a credit
                // spread.
                if (netCredit <= 0)
                    continue;

                // 8. Calculate Max Loss.
                // Formula: ((Short Strike - Long Strike) * 100) - Net Credit.
                double strikeWidth = (shortStrikePrice - longStrikePrice) * 100;
                double maxLoss = strikeWidth - netCredit;

                // 9. Filter: Max Loss should not be more than $1000.
                if (maxLoss > maxLossLimit)
                    continue;

                // 10. Filter: Max Profit must be at least 12% of Max Loss.
                // Formula: netCredit >= (maxLoss * 0.12)
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

        spreads.forEach(System.out::println);
    }

}
