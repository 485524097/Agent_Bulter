package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.ExpenseRecordDTO;
import com.test.service.ExpenseRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentRecordManageHandler {

    @Autowired
    private ExpenseRecordService expenseRecordService;

    /**
     * 查询账单明细
     */
    public AgentChatResponse handleList(Long userId,
                                        String message,
                                        List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        String range = resolveRange(message);
        String recordType = resolveRecordType(message);
        String category = resolveCategory(message);

        List<ExpenseRecordDTO> records = expenseRecordService.listRecords(
                userId,
                range,
                recordType,
                category
        );

        AgentAction action = new AgentAction();
        action.setName("listRecords");
        action.setSuccess(true);
        action.setMessage("账单明细查询成功，共 " + records.size() + " 条");
        actions.add(action);

        if (records.isEmpty()) {
            response.setReply("暂时没有查到符合条件的账单记录。");
            response.setActions(actions);
            return response;
        }

        StringBuilder reply = new StringBuilder();

        reply.append("查到了 ")
                .append(records.size())
                .append(" 条账单记录。");

        reply.append("最近几条是：");

        int limit = Math.min(records.size(), 5);

        for (int i = 0; i < limit; i++) {
            ExpenseRecordDTO record = records.get(i);

            reply.append("ID ")
                    .append(record.getId())
                    .append("，")
                    .append("[").append(record.getRecordType()).append("] ")
                    .append(record.getCategory())
                    .append(record.getAmount())
                    .append("元，")
                    .append(record.getDescription());

            if (i < limit - 1) {
                reply.append("；");
            } else {
                reply.append("。");
            }
        }

        if (records.size() > 5) {
            reply.append("其余记录可以在账单列表中继续查看。");
        }

        response.setReply(reply.toString());
        response.setActions(actions);

        return response;
    }

    /**
     * 删除指定账单
     */
    public AgentChatResponse handleDelete(Long userId,
                                          String message,
                                          List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        Long recordId = extractRecordId(message);

        if (recordId == null) {
            response.setReply("我还不知道你要删除哪一条账单，请告诉我账单ID，例如：删除账单18。");
            response.setActions(actions);
            return response;
        }

        expenseRecordService.deleteRecord(userId, recordId);

        AgentAction action = new AgentAction();
        action.setName("deleteRecord");
        action.setSuccess(true);
        action.setMessage("账单删除成功，账单ID：" + recordId);
        actions.add(action);

        response.setReply("已帮你删除账单 ID：" + recordId + "。");
        response.setActions(actions);

        return response;
    }

    /**
     * 修改指定账单
     */
    public AgentChatResponse handleUpdate(Long userId,
                                          String message,
                                          List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        Long recordId = extractRecordId(message);

        if (recordId == null) {
            response.setReply("我还不知道你要修改哪一条账单，请告诉我账单ID，例如：把账单18改成20元。");
            response.setActions(actions);
            return response;
        }

        BigDecimal amount = extractUpdateAmount(message);
        String category = resolveCategory(message);
        String recordType = resolveRecordType(message);
        String description = extractDescription(message);

        if (amount == null && category == null && recordType == null && description == null) {
            response.setReply("我知道你想修改账单 " + recordId + "，但没有识别到要修改的内容。你可以说：把账单18改成20元。");
            response.setActions(actions);
            return response;
        }

        expenseRecordService.updateRecord(
                userId,
                recordId,
                amount,
                category,
                recordType,
                description,
                null
        );

        AgentAction action = new AgentAction();
        action.setName("updateRecord");
        action.setSuccess(true);
        action.setMessage("账单修改成功，账单ID：" + recordId);
        actions.add(action);

        response.setReply("已帮你修改账单 ID：" + recordId + "。");
        response.setActions(actions);

        return response;
    }

    /**
     * 撤销最近一笔
     */
    public AgentChatResponse handleUndo(Long userId,
                                        String message,
                                        List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        expenseRecordService.undoLastRecord(userId);

        AgentAction action = new AgentAction();
        action.setName("undoLastRecord");
        action.setSuccess(true);
        action.setMessage("最近一笔账单已撤销");
        actions.add(action);

        response.setReply("已帮你撤销最近一笔账单。");
        response.setActions(actions);

        return response;
    }

    private String resolveRange(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "month";
        }

        if (message.contains("今天") || message.contains("今日")) {
            return "today";
        }

        if (message.contains("全部") || message.contains("所有")) {
            return "all";
        }

        return "month";
    }

    private String resolveRecordType(String message) {
        if (message == null) {
            return null;
        }

        if (message.contains("收入")) {
            return "INCOME";
        }

        if (message.contains("支出") || message.contains("消费") || message.contains("花")) {
            return "EXPENSE";
        }

        return null;
    }

    private String resolveCategory(String message) {
        if (message == null) {
            return null;
        }

        if (message.contains("餐饮") || message.contains("吃饭") || message.contains("午饭") || message.contains("晚饭")) {
            return "餐饮";
        }

        if (message.contains("饮品") || message.contains("奶茶") || message.contains("咖啡")) {
            return "饮品";
        }

        if (message.contains("交通") || message.contains("打车") || message.contains("地铁") || message.contains("公交")) {
            return "交通";
        }

        if (message.contains("购物") || message.contains("买东西")) {
            return "购物";
        }

        if (message.contains("学习") || message.contains("买书") || message.contains("课程")) {
            return "学习";
        }

        if (message.contains("娱乐") || message.contains("电影") || message.contains("游戏")) {
            return "娱乐";
        }

        if (message.contains("医疗") || message.contains("看病") || message.contains("买药")) {
            return "医疗";
        }

        if (message.contains("住房") || message.contains("房租")) {
            return "住房";
        }

        return null;
    }

    /**
     * 从“删除账单18”“修改记录20”“把账单18改成30元”中提取账单ID。
     * 默认取第一个数字作为 recordId。
     */
    private Long extractRecordId(String message) {
        if (message == null || message.trim().isEmpty()) {
            return null;
        }

        Pattern pattern = Pattern.compile("(\\d+)");
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            return Long.valueOf(matcher.group(1));
        }

        return null;
    }

    /**
     * 修改金额：默认取第二个数字作为新金额。
     * 例如：把账单18改成20元
     * 第一个数字 18 是账单ID，第二个数字 20 是金额。
     */
    private BigDecimal extractUpdateAmount(String message) {
        if (message == null || message.trim().isEmpty()) {
            return null;
        }

        Pattern pattern = Pattern.compile("(\\d+(\\.\\d+)?)");
        Matcher matcher = pattern.matcher(message);

        List<String> numbers = new ArrayList<>();

        while (matcher.find()) {
            numbers.add(matcher.group(1));
        }

        if (numbers.size() >= 2) {
            return new BigDecimal(numbers.get(1));
        }

        return null;
    }

    /**
     * 简单提取描述。
     * 例如：把账单18描述改为奶茶
     */
    private String extractDescription(String message) {
        if (message == null) {
            return null;
        }

        if (message.contains("描述改为")) {
            return message.substring(message.indexOf("描述改为") + 4).trim();
        }

        if (message.contains("备注改为")) {
            return message.substring(message.indexOf("备注改为") + 4).trim();
        }

        return null;
    }
}