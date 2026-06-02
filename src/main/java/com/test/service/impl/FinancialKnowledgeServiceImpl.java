package com.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.entity.FinancialKnowledge;
import com.test.mapper.FinancialKnowledgeMapper;
import com.test.service.FinancialKnowledgeService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class FinancialKnowledgeServiceImpl
        extends ServiceImpl<FinancialKnowledgeMapper, FinancialKnowledge>
        implements FinancialKnowledgeService {

    @Autowired
    private EmbeddingModel embeddingModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<FinancialKnowledge> searchKnowledge(String question, Integer limit) {

        if (limit == null || limit <= 0) {
            limit = 3;
        }

        List<String> keywords = extractKeywords(question);

        QueryWrapper<FinancialKnowledge> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("enabled", 1)
                .eq("deleted", 0);

        if (!keywords.isEmpty()) {
            queryWrapper.and(wrapper -> {
                for (String keyword : keywords) {
                    wrapper.or().like("title", keyword)
                            .or().like("content", keyword)
                            .or().like("tags", keyword)
                            .or().like("category", keyword);
                }
            });
        }

        queryWrapper.orderByDesc("id")
                .last("LIMIT " + limit);

        return this.list(queryWrapper);
    }

    /**
     * 给所有知识生成 embedding
     */
    @Override
    public void refreshAllEmbeddings() {

        QueryWrapper<FinancialKnowledge> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("enabled", 1)
                .eq("deleted", 0);

        List<FinancialKnowledge> knowledgeList = this.list(queryWrapper);

        if (knowledgeList == null || knowledgeList.isEmpty()) {
            return;
        }

        for (FinancialKnowledge knowledge : knowledgeList) {

            String text = buildKnowledgeText(knowledge);

            float[] vector = generateEmbedding(text);

            try {
                String embeddingJson = objectMapper.writeValueAsString(vector);
                knowledge.setEmbedding(embeddingJson);
                this.updateById(knowledge);

                System.out.println("知识向量生成成功，id=" + knowledge.getId()
                        + "，title=" + knowledge.getTitle());

            } catch (Exception e) {
                throw new RuntimeException("知识向量保存失败，id=" + knowledge.getId());
            }
        }
    }

    /**
     * 语义检索知识库
     */
    @Override
    public List<FinancialKnowledge> semanticSearchKnowledge(String question, Integer limit) {

        if (limit == null || limit <= 0) {
            limit = 3;
        }

        if (question == null || question.trim().isEmpty()) {
            return searchKnowledge(question, limit);
        }

        float[] questionVector = generateEmbedding(question);

        QueryWrapper<FinancialKnowledge> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("enabled", 1)
                .eq("deleted", 0)
                .isNotNull("embedding");

        List<FinancialKnowledge> knowledgeList = this.list(queryWrapper);

        if (knowledgeList == null || knowledgeList.isEmpty()) {
            return searchKnowledge(question, limit);
        }

        List<FinancialKnowledge> scoredList = new ArrayList<>();

        for (FinancialKnowledge knowledge : knowledgeList) {

            try {
                float[] knowledgeVector = objectMapper.readValue(
                        knowledge.getEmbedding(),
                        float[].class
                );

                double score = cosineSimilarity(questionVector, knowledgeVector);

                knowledge.setScore(score);
                scoredList.add(knowledge);

            } catch (Exception e) {
                System.out.println("知识向量解析失败，id=" + knowledge.getId());
            }
        }

        scoredList.sort(Comparator.comparing(FinancialKnowledge::getScore).reversed());

        if (scoredList.size() > limit) {
            return scoredList.subList(0, limit);
        }

        return scoredList;
    }

    private float[] generateEmbedding(String text) {

        Response<Embedding> response = embeddingModel.embed(text);

        if (response == null || response.content() == null) {
            throw new RuntimeException("生成向量失败");
        }

        return response.content().vector();
    }

    private String buildKnowledgeText(FinancialKnowledge knowledge) {

        StringBuilder sb = new StringBuilder();

        if (knowledge.getTitle() != null) {
            sb.append("标题：").append(knowledge.getTitle()).append("\n");
        }

        if (knowledge.getCategory() != null) {
            sb.append("分类：").append(knowledge.getCategory()).append("\n");
        }

        if (knowledge.getTags() != null) {
            sb.append("标签：").append(knowledge.getTags()).append("\n");
        }

        if (knowledge.getContent() != null) {
            sb.append("内容：").append(knowledge.getContent());
        }

        return sb.toString();
    }

    private double cosineSimilarity(float[] vectorA, float[] vectorB) {

        if (vectorA == null || vectorB == null || vectorA.length != vectorB.length) {
            return 0.0;
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dot += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private List<String> extractKeywords(String question) {

        List<String> keywords = new ArrayList<>();

        if (question == null || question.trim().isEmpty()) {
            keywords.add("消费");
            keywords.add("预算");
            return keywords;
        }

        if (question.contains("奶茶") || question.contains("咖啡") || question.contains("饮品")) {
            keywords.add("饮品");
            keywords.add("奶茶");
            keywords.add("咖啡");
        }

        if (question.contains("预算") || question.contains("超支") || question.contains("超预算")) {
            keywords.add("预算");
            keywords.add("超支");
        }

        if (question.contains("购物") || question.contains("冲动")) {
            keywords.add("购物");
            keywords.add("冲动消费");
        }

        if (question.contains("餐饮") || question.contains("吃饭") || question.contains("外卖")) {
            keywords.add("餐饮");
            keywords.add("外卖");
        }

        if (question.contains("省钱") || question.contains("节省") || question.contains("控制")) {
            keywords.add("省钱");
            keywords.add("消费控制");
        }

        if (keywords.isEmpty()) {
            keywords.add("消费");
            keywords.add("预算");
            keywords.add("理财");
        }

        return keywords;
    }
}