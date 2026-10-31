package com.safework.safety.emergency;

import lombok.Data;

@Data
public class EventQueryDTO {
    private long current = 1;
    private long size = 10;
    private String keyword;
    private String status;
    private String accidentType;
}
