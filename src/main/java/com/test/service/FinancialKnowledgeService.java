package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.FinancialKnowledge;

import java.util.List;

public interface FinancialKnowledgeService extends IService<FinancialKnowledge> {

    List<FinancialKnowledge> searchKnowledge(String question, Integer limit);

    void refreshAllEmbeddings();

    List<FinancialKnowledge> semanticSearchKnowledge(String question, Integer limit);
}