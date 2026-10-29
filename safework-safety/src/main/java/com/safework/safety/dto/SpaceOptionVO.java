package com.safework.safety.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SpaceOptionVO {
    private Long id;
    private String spaceCode;
    private String spaceName;
    private String location;
    private String riskLevel;
}
