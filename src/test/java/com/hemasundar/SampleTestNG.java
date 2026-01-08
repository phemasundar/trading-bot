package com.hemasundar;

import com.hemasundar.apis.ThinkOrSwinAPIs;
import com.hemasundar.pojos.*;
import com.hemasundar.strategies.*;
import com.hemasundar.utils.FilePaths;
import com.hemasundar.utils.JavaUtils;
import com.hemasundar.utils.TelegramUtils;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Scanner;

public class SampleTestNG {
    public static void main(String[] args) {
        // 1. YOUR CONFIGURATION
        String redirectUri = "https://127.0.0.1";

        // 2. GET THE URL FROM BROWSER
        Scanner scanner = new Scanner(System.in);
        System.out.println(
                "1. Log in via your browser. \nhttps://api.schwabapi.com/v1/oauth/authorize?client_id="
                        + TestConfig.getInstance().appKey()
                        + "&redirect_uri=https://127.0.0.1");
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
        List<OptionChainResponse> optionChainResponseList = securities.securities().stream()
                .map(ThinkOrSwinAPIs::getOptionChainResponse).toList();
        StrategyFilter pcsFilter = StrategyFilter.builder()
                .targetDTE(30)
                .maxDelta(0.20)
                .maxLossLimit(1000)
                .minReturnOnRisk(12)
                .ignoreEarnings(false)
                .build();
        StrategyFilter ccsFilter = StrategyFilter.builder()
                .targetDTE(30)
                .maxDelta(0.20)
                .maxLossLimit(1000)
                .minReturnOnRisk(12)
                .ignoreEarnings(false)
                .build();
        StrategyFilter icFilter = StrategyFilter.builder()
                .targetDTE(60)
                .maxDelta(0.15)
                .maxLossLimit(1000)
                .minReturnOnRisk(24)
                .build();
        StrategyFilter leapFilter = StrategyFilter.builder()
                .minDTE((int) ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.now().plusMonths(11)))
                .minDelta(0.6)
                .marginInterestRate(6.0)
                .maxOptionPricePercent(50.0) // Option premium < 50% of underlying
                .build();
        printFilteredStrategies(optionChainResponseList, new PutCreditSpreadStrategy(), pcsFilter);

        printFilteredStrategies(optionChainResponseList, new CallCreditSpreadStrategy(), ccsFilter);

        printFilteredStrategies(optionChainResponseList, new IronCondorStrategy(), icFilter);

        printFilteredStrategies(optionChainResponseList, new LongCallLeapStrategy(), leapFilter);
    }

    private static void printFilteredStrategies(List<OptionChainResponse> optionChainResponseList,
                                                AbstractTradingStrategy strategy, StrategyFilter strategyFilter) {
        System.out.println("******************************************************************\n" +
                "******************************************************************\n" +
                "************* " + strategy.getStrategyName() + " **************\n" +
                "****************************************************************\n" +
                "****************************************************************\n");

        optionChainResponseList.forEach(optionChainResponse -> {
            System.out.println("---------------------------------------------------------\n" +
                    "----------------" + optionChainResponse.getSymbol() + "--------------\n" +
                    "---------------------------------------------------------\n");

            List<TradeSetup> trades = strategy.findTrades(optionChainResponse, strategyFilter);
            trades.forEach(System.out::println);

            // Send to Telegram
            if (!trades.isEmpty()) {
                TelegramUtils.sendTradeAlerts(strategy.getStrategyName(),
                        optionChainResponse.getSymbol(), trades);
            }
        });
    }

}
