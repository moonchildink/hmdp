package com.hmdp.dto;


import lombok.Data;

import java.util.List;

@Data
public class ScrollDTO {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
