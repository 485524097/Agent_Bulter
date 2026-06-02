package com.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.dto.CategoryAmountDTO;
import com.test.dto.ExpenseRecordDTO;
import com.test.dto.PageResult;
import com.test.entity.ExpenseRecord;
import com.test.mapper.ExpenseRecordMapper;
import com.test.service.ExpenseRecordService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExpenseRecordServiceImpl
        extends ServiceImpl<ExpenseRecordMapper, ExpenseRecord>
        implements ExpenseRecordService {

    /*记账功能
    *
    * */
    @Override
    public Long createRecord(Long userId,
                             BigDecimal amount,
                             String category,
                             String recordType,
                             String description,
                             LocalDate expenseTime,
                             String sourceType,
                             String sourceText,
                             String sessionId) {

        // 1. 基础参数校验
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("金额必须大于0");
        }

        // 2. 字段兜底
        if (category == null || category.trim().isEmpty()) {
            category = "其他";
        }

        if (description == null || description.trim().isEmpty()) {
            description = "日常记录";
        }

        if (expenseTime == null) {
            expenseTime = LocalDate.now();
        }

        // 3. 类型标准化
        recordType = normalizeRecordType(recordType);
        sourceType = normalizeSourceType(sourceType);

        // 4. 构建实体对象
        ExpenseRecord record = new ExpenseRecord();

        record.setUserId(userId);
        record.setAmount(amount);
        record.setCategory(category);
        record.setRecordType(recordType);
        record.setDescription(description);
        record.setExpenseTime(expenseTime);
        record.setExpenseDatetime(LocalDateTime.now());
        record.setSourceType(sourceType);
        record.setSourceText(sourceText);
        record.setSessionId(sessionId);
        record.setDeleted(0);

        // 5. 保存数据库
        this.save(record);

        // 6. 返回数据库生成的ID
        return record.getId();
    }

    private String normalizeRecordType(String recordType) {
        if ("INCOME".equalsIgnoreCase(recordType)) {
            return "INCOME";
        }
        return "EXPENSE";
    }

    private String normalizeSourceType(String sourceType) {
        if ("MANUAL".equalsIgnoreCase(sourceType)) {
            return "MANUAL";
        }
        if ("IMPORT".equalsIgnoreCase(sourceType)) {
            return "IMPORT";
        }
        if ("SYSTEM".equalsIgnoreCase(sourceType)) {
            return "SYSTEM";
        }
        return "AGENT";
    }

    /*查询消费功能
     *
     * */
    @Override
    public BigDecimal sumAmountByDateRange(Long userId,
                                           String recordType,
                                           LocalDate startDate,
                                           LocalDate endDate) {

//        条件判断
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        if (startDate == null || endDate == null) {
            throw new RuntimeException("查询时间范围不能为空");
        }

        recordType = normalizeRecordType(recordType);

        QueryWrapper<ExpenseRecord> queryWrapper = new QueryWrapper<>();

        queryWrapper.select("IFNULL(SUM(amount), 0) AS amount")
                .eq("user_id", userId)
                .eq("record_type", recordType)
                .ge("expense_time", startDate)
                .le("expense_time", endDate)
                .eq("deleted", 0);

        ExpenseRecord record = this.getOne(queryWrapper);

        if (record == null || record.getAmount() == null) {
            return BigDecimal.ZERO;
        }

        return record.getAmount();
    }

    @Override
    public List<CategoryAmountDTO> sumAmountGroupByCategory(Long userId,
                                                            String recordType,
                                                            LocalDate startDate,
                                                            LocalDate endDate) {

        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        if (startDate == null || endDate == null) {
            throw new RuntimeException("查询时间范围不能为空");
        }

        recordType = normalizeRecordType(recordType);

        return this.baseMapper.sumAmountGroupByCategory(
                userId,
                recordType,
                startDate,
                endDate
        );
    }

    @Override
    public BigDecimal sumAmountByDateRangeAndCategory(Long userId,
                                                      String recordType,
                                                      String category,
                                                      LocalDate startDate,
                                                      LocalDate endDate) {

        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        if (startDate == null || endDate == null) {
            throw new RuntimeException("查询时间范围不能为空");
        }

        if (category == null || category.trim().isEmpty()) {
            throw new RuntimeException("分类不能为空");
        }

        recordType = normalizeRecordType(recordType);

        QueryWrapper<ExpenseRecord> queryWrapper = new QueryWrapper<>();

        queryWrapper.select("IFNULL(SUM(amount), 0) AS amount")
                .eq("user_id", userId)
                .eq("record_type", recordType)
                .eq("category", category)
                .ge("expense_time", startDate)
                .le("expense_time", endDate)
                .eq("deleted", 0);

        ExpenseRecord record = this.getOne(queryWrapper);

        if (record == null || record.getAmount() == null) {
            return BigDecimal.ZERO;
        }

        return record.getAmount();
    }
    @Override
    public List<ExpenseRecordDTO> listRecords(Long userId,
                                              String range,
                                              String recordType,
                                              String category) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        QueryWrapper<ExpenseRecord> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("user_id", userId)
                .eq("deleted", 0);

        LocalDate now = LocalDate.now();

        if ("today".equalsIgnoreCase(range)) {
            queryWrapper.eq("expense_time", now);
        } else if ("month".equalsIgnoreCase(range) || range == null || range.trim().isEmpty()) {
            LocalDate startDate = now.withDayOfMonth(1);
            LocalDate endDate = now.withDayOfMonth(now.lengthOfMonth());

            queryWrapper.ge("expense_time", startDate)
                    .le("expense_time", endDate);
        }

        if (recordType != null && !recordType.trim().isEmpty()) {
            queryWrapper.eq("record_type", normalizeRecordType(recordType));
        }

        if (category != null && !category.trim().isEmpty()) {
            queryWrapper.eq("category", category.trim());
        }

        queryWrapper.orderByDesc("expense_time")
                .orderByDesc("id");

        List<ExpenseRecord> records = this.list(queryWrapper);

        List<ExpenseRecordDTO> result = new ArrayList<>();

        for (ExpenseRecord record : records) {
            ExpenseRecordDTO dto = new ExpenseRecordDTO();

            dto.setId(record.getId());
            dto.setAmount(record.getAmount());
            dto.setCategory(record.getCategory());
            dto.setRecordType(record.getRecordType());
            dto.setDescription(record.getDescription());
            dto.setExpenseTime(record.getExpenseTime());
            dto.setExpenseDatetime(record.getExpenseDatetime());
            dto.setSourceType(record.getSourceType());
            dto.setSourceText(record.getSourceText());
            dto.setSessionId(record.getSessionId());

            result.add(dto);
        }

        return result;
    }

    @Override
    public void deleteRecord(Long userId, Long recordId) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        if (recordId == null) {
            throw new RuntimeException("账单ID不能为空");
        }

        ExpenseRecord record = this.getById(recordId);

        if (record == null) {
            throw new RuntimeException("账单不存在或已删除");
        }

        if (!userId.equals(record.getUserId())) {
            throw new RuntimeException("无权删除该账单");
        }

        boolean removed = this.removeById(recordId);

        if (!removed) {
            throw new RuntimeException("账单删除失败");
        }
    }

    @Override
    public void updateRecord(Long userId,
                             Long recordId,
                             BigDecimal amount,
                             String category,
                             String recordType,
                             String description,
                             LocalDate expenseTime) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        if (recordId == null) {
            throw new RuntimeException("账单ID不能为空");
        }

        ExpenseRecord record = this.getById(recordId);

        if (record == null) {
            throw new RuntimeException("账单不存在或已删除");
        }

        if (!userId.equals(record.getUserId())) {
            throw new RuntimeException("无权修改该账单");
        }

        if (amount != null) {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("金额必须大于0");
            }
            record.setAmount(amount);
        }

        if (category != null && !category.trim().isEmpty()) {
            record.setCategory(category.trim());
        }

        if (recordType != null && !recordType.trim().isEmpty()) {
            record.setRecordType(normalizeRecordType(recordType));
        }

        if (description != null && !description.trim().isEmpty()) {
            record.setDescription(description.trim());
        }

        if (expenseTime != null) {
            record.setExpenseTime(expenseTime);
        }

        boolean updated = this.updateById(record);

        if (!updated) {
            throw new RuntimeException("账单修改失败");
        }
    }
    @Override
    public void undoLastRecord(Long userId) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        QueryWrapper<ExpenseRecord> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("user_id", userId)
                .eq("deleted", 0)
                .orderByDesc("id")
                .last("LIMIT 1");

        ExpenseRecord record = this.getOne(queryWrapper, false);

        if (record == null) {
            throw new RuntimeException("暂无可撤销的账单记录");
        }

        boolean removed = this.removeById(record.getId());

        if (!removed) {
            throw new RuntimeException("撤销账单失败");
        }
    }
    @Override
    public PageResult<ExpenseRecordDTO> pageRecords(Long userId,
                                                    String range,
                                                    String recordType,
                                                    String category,
                                                    Long pageNo,
                                                    Long pageSize) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        if (pageNo == null || pageNo <= 0) {
            pageNo = 1L;
        }

        if (pageSize == null || pageSize <= 0) {
            pageSize = 10L;
        }

        if (pageSize > 100) {
            pageSize = 100L;
        }

        QueryWrapper<ExpenseRecord> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("user_id", userId)
                .eq("deleted", 0);

        LocalDate now = LocalDate.now();

        if ("today".equalsIgnoreCase(range)) {
            queryWrapper.eq("expense_time", now);
        } else if ("month".equalsIgnoreCase(range) || range == null || range.trim().isEmpty()) {
            LocalDate startDate = now.withDayOfMonth(1);
            LocalDate endDate = now.withDayOfMonth(now.lengthOfMonth());

            queryWrapper.ge("expense_time", startDate)
                    .le("expense_time", endDate);
        }

        if (recordType != null && !recordType.trim().isEmpty()) {
            queryWrapper.eq("record_type", normalizeRecordType(recordType));
        }

        if (category != null && !category.trim().isEmpty()) {
            queryWrapper.eq("category", category.trim());
        }

        queryWrapper.orderByDesc("expense_time")
                .orderByDesc("id");

        Page<ExpenseRecord> page = new Page<>(pageNo, pageSize);

        Page<ExpenseRecord> resultPage = this.page(page, queryWrapper);

        List<ExpenseRecordDTO> dtoList = new ArrayList<>();

        for (ExpenseRecord record : resultPage.getRecords()) {
            ExpenseRecordDTO dto = new ExpenseRecordDTO();

            dto.setId(record.getId());
            dto.setAmount(record.getAmount());
            dto.setCategory(record.getCategory());
            dto.setRecordType(record.getRecordType());
            dto.setDescription(record.getDescription());
            dto.setExpenseTime(record.getExpenseTime());
            dto.setExpenseDatetime(record.getExpenseDatetime());
            dto.setSourceType(record.getSourceType());
            dto.setSourceText(record.getSourceText());
            dto.setSessionId(record.getSessionId());

            dtoList.add(dto);
        }

        PageResult<ExpenseRecordDTO> pageResult = new PageResult<>();
        pageResult.setTotal(resultPage.getTotal());
        pageResult.setPageNo(resultPage.getCurrent());
        pageResult.setPageSize(resultPage.getSize());
        pageResult.setPages(resultPage.getPages());
        pageResult.setRecords(dtoList);

        return pageResult;
    }
}