package com.hemasundar.utils;

import com.hemasundar.config.properties.SchwabConfig;
import com.hemasundar.pojos.RefreshToken;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.Scanner;

/**
 * Interactive utility class to generate a new Schwab Refresh Token.
 * Run via ScheduledJobRunner with --app.job.name=GENERATE_TOKEN
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class SchwabTokenGenerator {

    private final SchwabConfig schwabConfig;

    public void runInteractiveGenerator() {
        // 1. YOUR CONFIGURATION
        String redirectUri = "https://127.0.0.1";

        // 2. GET THE URL FROM BROWSER
        Scanner scanner = new Scanner(System.in);
        System.out.println(
                "1. Log in via your browser. \nhttps://api.schwabapi.com/v1/oauth/authorize?client_id="
                        + schwabConfig.getAppKey()
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
                .basic(schwabConfig.getAppKey(), schwabConfig.getAppSecret())
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
        System.out.println("Refresh Token: " + refreshToken.getRefresh_token());
        scanner.close();
    }
}
