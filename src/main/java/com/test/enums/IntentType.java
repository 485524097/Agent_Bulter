package com.test.enums;

public enum IntentType {
    RECORD_EXPENSE,
    QUERY_EXPENSE,
    ANALYZE_EXPENSE,
    SET_BUDGET,
    BUDGET_RISK,

    /**
     * 查询账单明细
     */
    LIST_RECORDS,

    /**
     * 删除指定账单
     */
    DELETE_RECORD,

    /**
     * 修改指定账单
     */
    UPDATE_RECORD,

    /**
     * 撤销最近一笔账单
     */
    UNDO_RECORD,

    CHAT,
    UNKNOWN
}