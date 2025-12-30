package com.hemasundar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown=true)
public class RefreshToken {
    private Integer expires_in;
    private String token_type;
    private String scope;
    private String refresh_token;
    private String access_token;
    private String id_token;
}
