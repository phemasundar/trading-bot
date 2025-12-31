package com.hemasundar;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.pojos.OptionChainResponse;
import com.hemasundar.pojos.RefreshToken;
import com.hemasundar.pojos.Securities;
import com.hemasundar.pojos.TestConfig;
import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.JavaUtils;
import com.hemasundar.pojos.*;
import com.hemasundar.strategies.CallCreditSpreadStrategy;
import com.hemasundar.strategies.IronCondorStrategy;
import com.hemasundar.strategies.PutCreditSpreadStrategy;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Scanner;

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

            // Enable this to filter the trades if any earnings are there
            /*
             * earningsCalendarResponse earningsResponse =
             * FinnHubAPIs.getEarningsByTicker(currentSecurity,
             * LocalDate.parse(targetExpiryDate));
             * if (CollectionUtils.isEmpty(earningsResponse.getEarningsCalendar())) {
             * System.out.println("Skipping " + currentSecurity +
             * " due to upcoming earnings on "
             * + earningsResponse.getEarningsCalendar().get(0).getDate());
             * return;
             * }
             */

            // 1. Put Credit Spreads
            StrategyFilter pcsFilter = StrategyFilter.builder()
                    .targetDTE(30)
                    .maxDelta(0.20)
                    .maxLossLimit(1000)
                    .minReturnOnRisk(12)
                    .ignoreEarnings(false)
                    .build();
//            new PutCreditSpreadStrategy().findTrades(optionChainResponse, pcsFilter).forEach(System.out::println);

            // 2. Call Credit Spreads
            StrategyFilter ccsFilter = StrategyFilter.builder()
                    .targetDTE(30)
                    .maxDelta(0.20)
                    .maxLossLimit(1000)
                    .minReturnOnRisk(12)
                    .ignoreEarnings(false)
                    .build();
//            new CallCreditSpreadStrategy().findTrades(optionChainResponse, ccsFilter).forEach(System.out::println);

            // 3. Iron Condors
            StrategyFilter icFilter = StrategyFilter.builder()
                    .targetDTE(60)
                    .maxDelta(0.15)
                    .maxLossLimit(1000)
                    .minReturnOnRisk(24)
                    .build();
            new IronCondorStrategy().findTrades(optionChainResponse, icFilter).forEach(System.out::println);
        });

    }

}
