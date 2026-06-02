package com.test.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CategoryAmountDTO {

    private String category;

    private BigDecimal amount;
}