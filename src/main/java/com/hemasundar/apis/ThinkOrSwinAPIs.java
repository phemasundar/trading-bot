package com.hemasundar.apis;

import com.hemasundar.pojos.OptionChainResponse;
import com.hemasundar.utils.JavaUtils;
import com.hemasundar.utils.TokenProvider;
import io.restassured.RestAssured;
import io.restassured.response.Response;

public class ThinkOrSwinAPIs {
    public static OptionChainResponse getOptionChainResponse(String symbol) {
        Response response = RestAssured.given()
                .header("accept", "application/json")
                .header("Authorization", "Bearer " + TokenProvider.INSTANCE.getAccessToken())
                .queryParam("symbol", symbol)
                .queryParam("strategy", "SINGLE")
                // .log().all()
                .get("https://api.schwabapi.com/marketdata/v1/chains");
        if (response.statusCode() != 200)
            throw new RuntimeException("Option Chain API failed");

        // System.out.println("Response code for Option chain API: " +
        // response.getStatusCode());
        // System.out.println("Response: \n" + response.asPrettyString());

        OptionChainResponse optionChainResponse = JavaUtils.convertJsonToPojo(response.asString(),
                OptionChainResponse.class);
        System.out.println("Current Maket Price: " + optionChainResponse.getUnderlyingPrice());
        return optionChainResponse;
    }

}
