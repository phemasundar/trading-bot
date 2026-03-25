package com.hemasundar.utils;

import com.hemasundar.pojos.RefreshToken;
import com.hemasundar.pojos.TestConfig;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import lombok.extern.log4j.Log4j2;

import java.util.Scanner;

/**
 * Interactive utility class to generate a new Schwab Refresh Token.
 * Run this standard main method manually to authenticate via a browser.
 */
@Log4j2
public class SchwabTokenGenerator {

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
        System.out.println("Refresh Token: " + refreshToken.getRefresh_token());
        scanner.close();
    }
}
