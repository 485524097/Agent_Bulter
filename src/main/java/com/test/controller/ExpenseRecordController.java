package com.test.controller;

import com.test.common.UserContext;
import com.test.dto.ExpenseRecordDTO;
import com.test.dto.ExpenseRecordQueryVO;
import com.test.dto.ExpenseRecordUpdateVO;
import com.test.dto.PageResult;
import com.test.service.ExpenseRecordService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/expense")
public class ExpenseRecordController {

    @Autowired
    private ExpenseRecordService expenseRecordService;

    @GetMapping("/records")
    public ResultVO<List<ExpenseRecordDTO>> listRecords(ExpenseRecordQueryVO queryVO) {

        Long userId = UserContext.getUserId();

        List<ExpenseRecordDTO> list = expenseRecordService.listRecords(
                userId,
                queryVO.getRange(),
                queryVO.getRecordType(),
                queryVO.getCategory()
        );

        return ResultVOUtil.success(list);
    }

    @DeleteMapping("/records/{recordId}")
    public ResultVO deleteRecord(@PathVariable Long recordId) {

        Long userId = UserContext.getUserId();

        expenseRecordService.deleteRecord(userId, recordId);

        return ResultVOUtil.success("账单删除成功");
    }
    @PutMapping("/records/{recordId}")
    public ResultVO updateRecord(@PathVariable Long recordId,
                                 @RequestBody ExpenseRecordUpdateVO request) {

        Long userId = UserContext.getUserId();

        expenseRecordService.updateRecord(
                userId,
                recordId,
                request.getAmount(),
                request.getCategory(),
                request.getRecordType(),
                request.getDescription(),
                request.getExpenseTime()
        );

        return ResultVOUtil.success("账单修改成功");
    }

    @PostMapping("/records/undo-last")
    public ResultVO undoLastRecord() {

        Long userId = UserContext.getUserId();

        expenseRecordService.undoLastRecord(userId);

        return ResultVOUtil.success("已撤销最近一笔账单");
    }
    @GetMapping("/records/page")
    public ResultVO<PageResult<ExpenseRecordDTO>> pageRecords(ExpenseRecordQueryVO queryVO) {

        Long userId = UserContext.getUserId();

        PageResult<ExpenseRecordDTO> pageResult = expenseRecordService.pageRecords(
                userId,
                queryVO.getRange(),
                queryVO.getRecordType(),
                queryVO.getCategory(),
                queryVO.getPageNo(),
                queryVO.getPageSize()
        );

        return ResultVOUtil.success(pageResult);
    }
}