package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.CategoryAmountDTO;
import com.test.dto.ExpenseRecordDTO;
import com.test.dto.PageResult;
import com.test.entity.ExpenseRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRecordService extends IService<ExpenseRecord> {

    Long createRecord(Long userId,
                      BigDecimal amount,
                      String category,
                      String recordType,
                      String description,
                      LocalDate expenseTime,
                      String sourceType,
                      String sourceText,
                      String sessionId);

    BigDecimal sumAmountByDateRange(Long userId,
                                    String recordType,
                                    LocalDate startDate,
                                    LocalDate endDate);

    List<CategoryAmountDTO> sumAmountGroupByCategory(Long userId,
                                                     String recordType,
                                                     LocalDate startDate,
                                                     LocalDate endDate);

    BigDecimal sumAmountByDateRangeAndCategory(Long userId,
                                               String recordType,
                                               String category,
                                               LocalDate startDate,
                                               LocalDate endDate);

    List<ExpenseRecordDTO> listRecords(Long userId,
                                       String range,
                                       String recordType,
                                       String category);

    void deleteRecord(Long userId, Long recordId);

    void updateRecord(Long userId,
                      Long recordId,
                      BigDecimal amount,
                      String category,
                      String recordType,
                      String description,
                      LocalDate expenseTime);

    void undoLastRecord(Long userId);

    PageResult<ExpenseRecordDTO> pageRecords(Long userId,
                                             String range,
                                             String recordType,
                                             String category,
                                             Long pageNo,
                                             Long pageSize);
}