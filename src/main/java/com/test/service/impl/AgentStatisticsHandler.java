package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.CategoryAmountDTO;
import com.test.service.AgentReplyService;
import com.test.service.ExpenseRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Service
public class AgentStatisticsHandler {

    @Autowired
    private ExpenseRecordService expenseRecordService;

    @Autowired
    private AgentReplyService agentReplyService;

    /*
    * 查询功能执行
    * */
    public AgentChatResponse handleQuery(Long userId,
                                         String message,
                                         List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        if (userId == null) {
            response.setReply("用户ID不能为空，无法查询消费记录。");
            response.setActions(actions);
            return response;
        }

        // 1. 根据用户原话解析查询时间范围
        DateRange dateRange = resolveDateRange(message);

        LocalDate startDate = dateRange.getStartDate();
        LocalDate endDate = dateRange.getEndDate();
        String timeText = dateRange.getText();

        // 2. 查询支出总额
        BigDecimal totalExpense = expenseRecordService.sumAmountByDateRange(
                userId,
                "EXPENSE",
                startDate,
                endDate
        );

        // 3. 查询收入总额
        BigDecimal totalIncome = expenseRecordService.sumAmountByDateRange(
                userId,
                "INCOME",
                startDate,
                endDate
        );

        // 4. 计算结余
        BigDecimal balance = totalIncome.subtract(totalExpense);

        // 5. 添加动作记录
        AgentAction action = new AgentAction();
        action.setName("queryExpense");
        action.setSuccess(true);
        action.setMessage("查询成功");
        actions.add(action);

        // 6. 根据用户问法返回不同内容
        String reply = agentReplyService.generateQueryReply(
                "statistics_reply_" + userId,
                message,
                timeText,
                totalExpense,
                totalIncome,
                balance
        );

        response.setReply(reply);

        response.setActions(actions);

        return response;
    }

    /**
     * 根据用户输入解析查询时间范围
     */
    private DateRange resolveDateRange(String message) {
        LocalDate now = LocalDate.now();

        if (message == null || message.trim().isEmpty()) {
            return getMonthRange(now);
        }

        // 今天
        if (message.contains("今天") || message.contains("今日")) {
            return new DateRange(now, now, "今天");
        }

        // 昨天
        if (message.contains("昨天")) {
            LocalDate yesterday = now.minusDays(1);
            return new DateRange(yesterday, yesterday, "昨天");
        }

        // 本周 / 这周
        if (message.contains("本周") || message.contains("这周")) {
            LocalDate startDate = now.with(DayOfWeek.MONDAY);
            LocalDate endDate = now.with(DayOfWeek.SUNDAY);
            return new DateRange(startDate, endDate, "本周");
        }

        // 上周
        if (message.contains("上周")) {
            LocalDate lastWeek = now.minusWeeks(1);
            LocalDate startDate = lastWeek.with(DayOfWeek.MONDAY);
            LocalDate endDate = lastWeek.with(DayOfWeek.SUNDAY);
            return new DateRange(startDate, endDate, "上周");
        }

        // 本月 / 这个月
        if (message.contains("本月")
                || message.contains("这个月")
                || message.contains("这月")) {
            return getMonthRange(now);
        }

        // 上个月 / 上月
        if (message.contains("上个月") || message.contains("上月")) {
            LocalDate lastMonth = now.minusMonths(1);
            LocalDate startDate = lastMonth.withDayOfMonth(1);
            LocalDate endDate = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth());
            return new DateRange(startDate, endDate, "上月");
        }

        // 默认查本月
        return getMonthRange(now);
    }

    /**
     * 获取本月范围
     */
    private DateRange getMonthRange(LocalDate now) {
        LocalDate startDate = now.withDayOfMonth(1);
        LocalDate endDate = now.withDayOfMonth(now.lengthOfMonth());
        return new DateRange(startDate, endDate, "本月");
    }

    /**
     * 查询时间范围对象
     */
    private static class DateRange {

        private final LocalDate startDate;

        private final LocalDate endDate;

        private final String text;

        public DateRange(LocalDate startDate, LocalDate endDate, String text) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.text = text;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public LocalDate getEndDate() {
            return endDate;
        }

        public String getText() {
            return text;
        }
    }

    /*
    * 分析功能执行
    * */
    public AgentChatResponse handleAnalyze(Long userId,
                                           String message,
                                           List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        if (userId == null) {
            response.setReply("用户ID不能为空，无法分析消费情况。");
            response.setActions(actions);
            return response;
        }

        DateRange dateRange = resolveDateRange(message);

        LocalDate startDate = dateRange.getStartDate();
        LocalDate endDate = dateRange.getEndDate();
        String timeText = dateRange.getText();

        BigDecimal totalExpense = expenseRecordService.sumAmountByDateRange(
                userId,
                "EXPENSE",
                startDate,
                endDate
        );

        List<CategoryAmountDTO> categoryList = expenseRecordService.sumAmountGroupByCategory(
                userId,
                "EXPENSE",
                startDate,
                endDate
        );

        AgentAction action = new AgentAction();
        action.setName("analyzeExpense");
        action.setSuccess(true);
        action.setMessage("消费分析成功");
        actions.add(action);

        if (totalExpense.compareTo(BigDecimal.ZERO) == 0) {
            response.setReply("你" + timeText + "还没有支出记录，暂时无法生成消费分析。");
            response.setActions(actions);
            return response;
        }

        StringBuilder reply = new StringBuilder();

        reply.append("你")
                .append(timeText)
                .append("支出共 ")
                .append(totalExpense)
                .append(" 元。");

        reply.append(" 分类来看，");

        for (int i = 0; i < categoryList.size(); i++) {
            CategoryAmountDTO item = categoryList.get(i);

            BigDecimal percent = item.getAmount()
                    .multiply(new BigDecimal("100"))
                    .divide(totalExpense, 2, java.math.RoundingMode.HALF_UP);

            reply.append(item.getCategory())
                    .append(" ")
                    .append(item.getAmount())
                    .append(" 元，占比 ")
                    .append(percent)
                    .append("%");

            if (i < categoryList.size() - 1) {
                reply.append("；");
            } else {
                reply.append("。");
            }
        }

        response.setReply(reply.toString());
        response.setActions(actions);

        return response;
    }
}