package com.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.test.dto.CategoryAmountDTO;
import com.test.entity.ExpenseRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ExpenseRecordMapper extends BaseMapper<ExpenseRecord> {

    @Select("""
            SELECT 
                category AS category,
                IFNULL(SUM(amount), 0) AS amount
            FROM expense_record
            WHERE user_id = #{userId}
              AND record_type = #{recordType}
              AND expense_time >= #{startDate}
              AND expense_time <= #{endDate}
              AND deleted = 0
            GROUP BY category
            ORDER BY amount DESC
            """)
    List<CategoryAmountDTO> sumAmountGroupByCategory(@Param("userId") Long userId,
                                                     @Param("recordType") String recordType,
                                                     @Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);
}