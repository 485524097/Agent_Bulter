package com.test.controller;

import com.test.service.FinancialKnowledgeService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/knowledge")
public class FinancialKnowledgeController {

    @Autowired
    private FinancialKnowledgeService financialKnowledgeService;

    @PostMapping("/embedding/refresh")
    public ResultVO refreshEmbedding() {

        financialKnowledgeService.refreshAllEmbeddings();

        return ResultVOUtil.success("知识库向量刷新成功");
    }
}