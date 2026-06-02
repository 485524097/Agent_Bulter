package com.test.service.impl;

import com.test.dto.AgentPlan;
import com.test.enums.IntentType;
import com.test.service.AgentIntentService;
import org.springframework.stereotype.Service;

@Service
public class AgentIntentServiceImpl implements AgentIntentService {

    @Override
    public IntentType decideIntent(String message, AgentPlan plan) {

        if (message == null || message.trim().isEmpty()) {
            return IntentType.UNKNOWN;
        }
        // 1. 撤销最近一笔
        if (isUndoRecordMessage(message)) {
            return IntentType.UNDO_RECORD;
        }

// 2. 删除指定账单
        if (isDeleteRecordMessage(message)) {
            return IntentType.DELETE_RECORD;
        }

// 3. 修改指定账单
        if (isUpdateRecordMessage(message)) {
            return IntentType.UPDATE_RECORD;
        }

// 4. 查询账单明细
        if (isListRecordMessage(message)) {
            return IntentType.LIST_RECORDS;
        }

        // 1. 预算设置优先
        // 避免“设置预算1800”被误判成预算查询
        if (isSetBudgetMessage(message)) {
            return IntentType.SET_BUDGET;
        }

        // 2. 预算查询 / 预算风险优先
        // 避免“预算查询”走到普通 QUERY_EXPENSE
        if (isBudgetQueryMessage(message) || isBudgetRiskMessage(message)) {
            return IntentType.BUDGET_RISK;
        }
        // 3. 知识库建议 / RAG 建议类
        if (isKnowledgeAdviceMessage(message)) {
            return IntentType.KNOWLEDGE_ADVICE;
        }

        // 4. 查询类
        if (isQueryMessage(message)) {
            return IntentType.QUERY_EXPENSE;
        }

        // 5. 分析类
        if (isAnalyzeMessage(message)) {
            return IntentType.ANALYZE_EXPENSE;
        }



        // 5. AI 解析成功时，参考 AI intent
        if (plan != null && Boolean.TRUE.equals(plan.getValid())) {
            String intent = plan.getIntent();
            if ("LIST_RECORDS".equals(intent)) {
                return IntentType.LIST_RECORDS;
            }
            if ("DELETE_RECORD".equals(intent)) {
                return IntentType.DELETE_RECORD;
            }
            if ("UPDATE_RECORD".equals(intent)) {
                return IntentType.UPDATE_RECORD;
            }
            if ("UNDO_RECORD".equals(intent)) {
                return IntentType.UNDO_RECORD;
            }
            if ("RECORD_EXPENSE".equals(intent)) {
                return IntentType.RECORD_EXPENSE;
            }
            if ("QUERY_EXPENSE".equals(intent)) {
                return IntentType.QUERY_EXPENSE;
            }
            if ("ANALYZE_EXPENSE".equals(intent)) {
                return IntentType.ANALYZE_EXPENSE;
            }
            if ("KNOWLEDGE_ADVICE".equals(intent)) {
                return IntentType.KNOWLEDGE_ADVICE;
            }
            if ("SET_BUDGET".equals(intent)) {
                return IntentType.SET_BUDGET;
            }
            if ("BUDGET_RISK".equals(intent)) {
                return IntentType.BUDGET_RISK;
            }
            if ("CHAT".equals(intent)) {
                return IntentType.CHAT;
            }
        }

        // 6. 最后再用简单规则判断记账
        if (isRecordExpenseMessage(message)) {
            return IntentType.RECORD_EXPENSE;
        }

        return IntentType.CHAT;
    }

    private boolean isQueryMessage(String message) {
        return message.contains("多少钱")
                || message.contains("花了多少")
                || message.contains("一共消费")
                || message.contains("总共消费")
                || message.contains("消费多少");
    }

    private boolean isAnalyzeMessage(String message) {
        return message.contains("分析")
                || message.contains("消费情况")
                || message.contains("消费结构");
    }

    private boolean isSetBudgetMessage(String message) {
        return message.contains("设置预算")
                || message.contains("预算设置")
                || message.contains("预算为")
                || message.contains("预算设置为");
    }

    private boolean isBudgetRiskMessage(String message) {
        return message.contains("超预算")
                || message.contains("超支")
                || message.contains("够不够")
                || message.contains("还能控制")
                || message.contains("会不会超")
                || message.contains("预算够不够");
    }

    private boolean isRecordExpenseMessage(String message) {
        return message.matches(".*\\d+.*")
                && !isQueryMessage(message);
    }

    private boolean isBudgetQueryMessage(String message) {
        return message.contains("预算查询")
                || message.contains("查预算")
                || message.contains("预算情况")
                || message.contains("预算是多少")
                || message.contains("预算还剩")
                || message.contains("剩余预算")
                || message.contains("还剩多少预算");
    }
    private boolean isListRecordMessage(String message) {
        return message.contains("账单明细")
                || message.contains("账单列表")
                || message.contains("消费明细")
                || message.contains("收支明细")
                || message.contains("记账记录")
                || message.contains("查看账单")
                || message.contains("查账单")
                || message.contains("今天账单")
                || message.contains("本月账单");
    }

    private boolean isDeleteRecordMessage(String message) {
        return (message.contains("删除") || message.contains("删掉"))
                && (message.contains("账单") || message.contains("记录"))
                && message.matches(".*\\d+.*");
    }

    private boolean isUpdateRecordMessage(String message) {
        return (message.contains("修改") || message.contains("改成") || message.contains("改为"))
                && (message.contains("账单") || message.contains("记录"))
                && message.matches(".*\\d+.*");
    }

    private boolean isUndoRecordMessage(String message) {
        return message.contains("撤销上一笔")
                || message.contains("撤销最近一笔")
                || message.contains("删除上一笔")
                || message.contains("删除最近一笔")
                || message.contains("取消上一笔")
                || message.contains("取消最近一笔")
                || message.contains("刚刚那笔记错了")
                || message.contains("上一笔记错了")
                || message.contains("最近一笔记错了");
    }
    private boolean isKnowledgeAdviceMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        return message.contains("怎么控制")
                || message.contains("如何控制")
                || message.contains("控制一下")
                || message.contains("控制消费")

                || message.contains("怎么省钱")
                || message.contains("如何省钱")
                || message.contains("怎么省一点")
                || message.contains("省一点")
                || message.contains("省点钱")
                || message.contains("少花点")
                || message.contains("少花钱")

                || message.contains("怎么减少")
                || message.contains("如何减少")
                || message.contains("减少消费")
                || message.contains("降低支出")

                || message.contains("消费建议")
                || message.contains("理财建议")
                || message.contains("预算建议")
                || message.contains("有什么建议")
                || message.contains("有什么办法")
                || message.contains("怎么办")

                || message.contains("太多了")
                || message.contains("花太多")
                || message.contains("买太多")

                || message.contains("买喝的")
                || message.contains("喝的")
                || message.contains("奶茶太多")
                || message.contains("饮品太多");
    }
}