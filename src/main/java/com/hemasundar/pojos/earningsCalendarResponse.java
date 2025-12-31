package com.hemasundar.pojos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class earningsCalendarResponse {

    private List<EarningCalendar> earningsCalendar;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EarningCalendar {
        private LocalDate date;
        private String epsActual;
        private String epsEstimate;
        private String hour;
        private Integer quarter;
        private String revenueActual;
        private Long revenueEstimate;
        private String symbol;
        private Integer year;
    }
}
