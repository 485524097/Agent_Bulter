package com.test.dto;

import lombok.Data;

@Data
public class AgentAction {

    /**
     * 动作名称，例如 recordExpense、queryExpense、setBudget
     */
    private String name;

    /**
     * 动作是否成功
     */
    private Boolean success;

    /**
     * 动作说明
     */
    private String message;
}