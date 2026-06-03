## 1.结果封装

### ResultVO

```java
package com.test.util;

@Data
public class ResultVO <T>{
    private int code;
    private String msg;
    private T data;
}
```

### ResultVOUtil

```java
package com.test.util;

public class ResultVOUtil {
//    1.请求成功
    public static ResultVO success(Object data){
        ResultVO resultVO = new ResultVO();
        resultVO.setCode(200);
        resultVO.setMsg("success");
        resultVO.setData(data);
        return resultVO;
    }
//    2.请求失败
    public static ResultVO fail(String msg){
        ResultVO resultVO = new ResultVO();
        resultVO.setCode(-1);
        resultVO.setMsg(msg);
        return resultVO;
    }
}

```

## 2.测试接口配置

### Knife4jConfig

```java
package com.test.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI butlerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Butler AI 个人记账助手接口文档")
                        .description("用于测试个人记账、预算管理、AI消费分析等接口")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("king")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8181")
                                .description("本地开发环境")
                ));
    }

    @Bean
    public GroupedOpenApi butlerApi() {
        return GroupedOpenApi.builder()
                .group("Butler接口")
                .packagesToScan("com.test")
                .pathsToMatch("/**")
                .build();
    }
}
```

## 3.Controller

### 3.1.1创建前端给后端的参数

#### AgentChatVO

```java
package com.test.dto;

import lombok.Data;

@Data
public class AgentChatVO {
    private  Long userId;
    private String sessionId;
    private String message;
}

```

### 3.1.2创建Agent给前端的参数

#### AgentChatResponse

```java
package com.test.dto;


import lombok.Data;

import java.util.List;

@Data
public class AgentChatResponse {
    
    private String reply;

    private List<AgentAction> actions;
}

```

#### AgentAction

```java
package com.test.dto;

import lombok.Data;

@Data
public class AgentAction {

    /**
     * 动作名称，例如 recordExpense、queryExpense、setBudget
     */
    private String name;

    /**
     * 动作是否成功
     */
    private Boolean success;

    /**
     * 动作说明
     */
    private String message;
}
```



### 3.1.3创建Service

#### AgentService

```java
package com.test.service;

import com.test.dto.AgentChatResponse;

public interface AgentService {
    AgentChatResponse chat(Long userId,String sessionId,String message);
}

```



实现chat方法

#### AgentServiceImpl

```java
package com.test.service.impl;

import com.test.dto.AgentChatResponse;
import com.test.service.AgentService;
import org.springframework.stereotype.Service;


@Service
public class AgentServiceImpl implements AgentService {

    @Override
    public AgentChatResponse chat(Long userId, String sessionId, String message) {

        AgentChatResponse response = new AgentChatResponse();

        response.setReply("你好，我已经收到你的消息：" + message);

        return response;
    }
}
```

#### BulterAgentController

```java
package com.test.controller;

import com.test.dto.AgentChatResponse;
import com.test.dto.AgentChatVO;
import com.test.service.AgentService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/agent")
public class BulterAgentController {

    @Autowired
    private AgentService agentService;

    @PostMapping("/chat")
    public ResultVO chat(@RequestBody AgentChatVO chatmessage) {

        AgentChatResponse chat = this.agentService.chat(
                chatmessage.getUserId(),
                chatmessage.getSessionId(),
                chatmessage.getMessage()
        );

        return ResultVOUtil.success(chat);
    }
}
```

> 测试结果
>
> ```
> {
>   "userId": 1,
>   "sessionId": "session_test_backend_001",
>   "message": "今天奶茶18"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "reply": "你好，我已经收到你的消息：今天奶茶18和吃饭11",
>     "actions": null
>   }
> }
> ```



### 3.2.1大模型接入

创建AI接口 AIService

#### AIService

```java
package com.test.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface AIService {
    String plan(@MemoryId String sessionId, @UserMessage String message);
}
```

Ai接口的配置策略

通过配置类中的 @Bean，使用 LangChain4j 根据 AIService 接口创建一个具备大模型调用能力的代理对象，并把它放入 Spring IOC 容器，后续业务类就可以通过注入 AIService 来调用 AI 能力。

#### AgentServiceFactory 

```java
package com.test.config;


import com.test.service.AIService;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentServiceFactory {

    @Autowired
    private ChatModel chatModel;

    
    @Bean
    public AIService aiService(ChatModel chatModel) {

        // 构建 AICodeService 的 AI 服务代理对象
        return AiServices.builder(AIService.class)

                // 注入大语言模型，负责生成回答
                .chatModel(chatModel)
            
                // 注入对话记忆，使 AI 能够理解上下文
				.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))


                // 构建服务对象，并注册到 Spring 容器中
                .build();

    }
}
```

创建 Agent 计划解析接口

#### AgentPlanService

```java
package com.test.service;

import com.test.dto.AgentPlan;

public interface AgentPlanService {

    AgentPlan parsePlan(String sessionId,String message);
}
```

解析的json格式

#### AgentPlan

```java
package com.test.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AgentPlan {

    private String intent;

    private String recordType;
    
    private BigDecimal amount;

    private String category;

    private String description;

    private Boolean valid;

    private String reason;
}
```



实现解析功能

#### AgentPlanServiceImpl

```java
package com.test.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.dto.AgentPlan;
import com.test.service.AIService;
import com.test.service.AgentPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AgentPlanServiceImpl implements AgentPlanService {

    @Autowired
    private AIService aiService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AgentPlan parsePlan(String sessionId, String message) {
        try {
            String json = aiService.plan(sessionId,message);

            json = cleanJson(json);

            return objectMapper.readValue(json, AgentPlan.class);
        } catch (Exception e) {
            AgentPlan plan = new AgentPlan();
            plan.setIntent(null);
            plan.setValid(false);
            plan.setReason("AI解析失败：" + e.getMessage());
            return plan;
        }
    }

    private String cleanJson(String json) {
        if (json == null) {
            return "{}";
        }

        json = json.trim();
        json = json.replace("```json", "");
        json = json.replace("```", "");
        json = json.trim();

        int start = json.indexOf("{");
        int end = json.lastIndexOf("}");

        if (start >= 0 && end >= 0 && end > start) {
            return json.substring(start, end + 1);
        }

        return json;
    }
}
```

#### AgentServiceImpl 

实现ai解析接口

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.service.AgentPlanService;
import com.test.service.AgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentPlanService agentPlanService;

    @Override
    public AgentChatResponse chat(Long userId, String sessionId, String message) {

        List<AgentAction> actions = new ArrayList<>();

        AgentPlan plan = agentPlanService.parsePlan(sessionId, message);

        AgentChatResponse response = new AgentChatResponse();

        response.setReply(
                "AI识别结果：意图=" + plan.getIntent()
                        + "，金额=" + plan.getAmount()
                        + "，类别=" + plan.getCategory()
                        + "，描述=" + plan.getDescription()
                        + "，valid=" + plan.getValid()
                        + ", reason=" + plan.getReason()
        );
        response.setActions(actions);

        return response;
    }
}
```

添加提示词

#### Agent-plan-prompt.txt

```
你是一个 AI 个人财务管家助手，负责将用户输入的自然语言解析成固定 JSON 格式。

你的任务不是直接回答用户，而是识别用户意图，并提取结构化参数。

你只能返回 JSON。
不要返回解释。
不要返回 Markdown。
不要使用 ```json 代码块。
不要在 JSON 前后添加任何文字。

返回 JSON 必须严格符合以下格式：

{
"intent": "RECORD_EXPENSE",
"recordType": "EXPENSE",
"amount": 18,
"category": "饮品",
"description": "奶茶",
"valid": true,
"reason": ""
}

字段说明：

1. intent 表示用户意图，只能取以下值：

* RECORD_EXPENSE：用户想记录一笔收入或支出
* QUERY_EXPENSE：用户想查询消费金额、收入金额或收支记录
* ANALYZE_EXPENSE：用户想分析消费、收入或收支情况
* SET_BUDGET：用户想设置预算
* BUDGET_RISK：用户想判断预算是否超支或是否够用
* CHAT：普通闲聊
* UNKNOWN：无法判断意图

2. recordType 表示记录类型，只能取以下值：

* EXPENSE：支出，例如吃饭、奶茶、打车、购物、买书、看电影、房租、医疗等
* INCOME：收入，例如工资、兼职、红包、奖金、退款、报销、转账收入等

如果 intent 不是 RECORD_EXPENSE，则 recordType 填写 null。

3. amount 表示金额。

* 如果用户输入中有明确金额，填写数字。
* 如果没有金额，填写 null。
* 金额必须使用阿拉伯数字，不要带单位。
* 例如“18元”“十八块”“18块钱”都返回 18。

4. category 表示类别。

支出类别可选：
餐饮、饮品、交通、购物、学习、娱乐、医疗、住房、其他

收入类别可选：
工资、兼职、红包、奖金、退款、报销、转账、其他

如果无法判断类别，填写 null。

5. description 表示记录描述。

例如：
奶茶、午饭、打车、买书、工资到账、兼职收入、红包、退款。

如果无法提取，填写 null。

6. valid 表示本次解析是否有效。

* 如果用户表达清楚，填写 true。
* 如果缺少关键信息，填写 false。

7. reason 表示原因说明。

* 如果 valid 为 true，reason 填写空字符串 ""。
* 如果 valid 为 false，说明缺少什么信息或为什么无法解析。

判断规则：

1. 如果用户输入包含明确消费内容和金额，判断为 RECORD_EXPENSE，并且 recordType 为 EXPENSE。

例如：
今天奶茶18
午饭28元
打车花了22
买书50
房租1200

2. 如果用户输入包含明确收入来源和金额，判断为 RECORD_EXPENSE，并且 recordType 为 INCOME。

例如：
今天工资到账5000
收到兼职费300
红包收入88
奖金1000
退款20
报销120

3. 如果用户是在问消费、收入或收支情况，判断为 QUERY_EXPENSE。

例如：
我这个月花了多少钱
今天一共消费多少
本月收入多少
我这个月结余多少
查一下本月收支

4. 如果用户要求分析消费、收入或收支情况，判断为 ANALYZE_EXPENSE。

例如：
分析一下我这个月的消费情况
看看我最近消费结构
分析一下我的收入和支出
看看本月收支情况

5. 如果用户想设置预算，判断为 SET_BUDGET。

例如：
帮我把本月预算设置为1800
设置餐饮预算800
这个月预算设成2000

6. 如果用户询问预算是否够用、是否超支，判断为 BUDGET_RISK。

例如：
我这个月会不会超预算
我还能控制在1800以内吗
这个月预算够不够
我是不是快超支了

7. 如果用户只是普通聊天，判断为 CHAT。

例如：
你好
你是谁
我不想上班
今天天气不错

8. 当前阶段暂时只处理单笔记录。

如果用户一次输入多笔记录，例如“今天奶茶18，吃饭28，打车22”，仍然判断为 RECORD_EXPENSE，但只提取第一笔记录，并在 reason 中说明“当前仅提取第一笔记录”。

示例 1：

用户输入：今天奶茶18

返回：
{
"intent": "RECORD_EXPENSE",
"recordType": "EXPENSE",
"amount": 18,
"category": "饮品",
"description": "奶茶",
"valid": true,
"reason": ""
}

示例 2：

用户输入：午饭28元

返回：
{
"intent": "RECORD_EXPENSE",
"recordType": "EXPENSE",
"amount": 28,
"category": "餐饮",
"description": "午饭",
"valid": true,
"reason": ""
}

示例 3：

用户输入：打车花了22

返回：
{
"intent": "RECORD_EXPENSE",
"recordType": "EXPENSE",
"amount": 22,
"category": "交通",
"description": "打车",
"valid": true,
"reason": ""
}

示例 4：

用户输入：今天工资到账5000

返回：
{
"intent": "RECORD_EXPENSE",
"recordType": "INCOME",
"amount": 5000,
"category": "工资",
"description": "工资到账",
"valid": true,
"reason": ""
}

示例 5：

用户输入：收到兼职费300

返回：
{
"intent": "RECORD_EXPENSE",
"recordType": "INCOME",
"amount": 300,
"category": "兼职",
"description": "兼职费",
"valid": true,
"reason": ""
}

示例 6：

用户输入：红包收入88

返回：
{
"intent": "RECORD_EXPENSE",
"recordType": "INCOME",
"amount": 88,
"category": "红包",
"description": "红包收入",
"valid": true,
"reason": ""
}

示例 7：

用户输入：我这个月花了多少钱

返回：
{
"intent": "QUERY_EXPENSE",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"valid": true,
"reason": ""
}

示例 8：

用户输入：我这个月收入多少

返回：
{
"intent": "QUERY_EXPENSE",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"valid": true,
"reason": ""
}

示例 9：

用户输入：分析一下我这个月的消费情况

返回：
{
"intent": "ANALYZE_EXPENSE",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"valid": true,
"reason": ""
}

示例 10：

用户输入：帮我把本月预算设置为1800

返回：
{
"intent": "SET_BUDGET",
"recordType": null,
"amount": 1800,
"category": null,
"description": "本月预算",
"valid": true,
"reason": ""
}

示例 11：

用户输入：我这个月会不会超预算

返回：
{
"intent": "BUDGET_RISK",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"valid": true,
"reason": ""
}

示例 12：

用户输入：你好

返回：
{
"intent": "CHAT",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"valid": true,
"reason": ""
}

示例 13：

用户输入：今天花了

返回：
{
"intent": "UNKNOWN",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"valid": false,
"reason": "缺少金额和具体收支内容"
}

```

#### AIService

添加提示词

```java
package com.test.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface AIService {

    @SystemMessage(fromResource = "agent-plan-prompt.txt")
    String plan(@MemoryId String sessionId, @UserMessage String message);
}
```



> 测试
>
> ```
> {
>   "userId": 1,
>   "sessionId": "session_test_backend_001",
>   "message": "今天奶茶18"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "reply": "AI识别结果：意图=RECORD_EXPENSE，金额=18，类别=饮品，描述=奶茶，valid=true, reason=",
>     "actions": []
>   }
> }
> ```



### 3.2.2意图判断

创建意图识别规则枚举类

#### IntentType

```java
package com.test.enums;

public enum IntentType {
    RECORD_EXPENSE,
    QUERY_EXPENSE,
    ANALYZE_EXPENSE,
    SET_BUDGET,
    BUDGET_RISK,
    CHAT,
    UNKNOWN
}
```



创建意图识别的接口，返回意图枚举和原始信息

#### AgentIntentService

```java
package com.test.service;

import com.test.dto.AgentPlan;
import com.test.enums.IntentType;

public interface AgentIntentService {

    IntentType decideIntent(String message, AgentPlan plan);
}
```

#### AgentIntentServiceImpl

实习意图识别接口类

```java吧23
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

        // 1. 查询类优先，避免“花了多少钱”被误判为记账
        if (isQueryMessage(message)) {
            return IntentType.QUERY_EXPENSE;
        }

        // 2. 分析类
        if (isAnalyzeMessage(message)) {
            return IntentType.ANALYZE_EXPENSE;
        }

        // 3. 预算设置
        if (isSetBudgetMessage(message)) {
            return IntentType.SET_BUDGET;
        }

        // 4. 预算风险
        if (isBudgetRiskMessage(message)) {
            return IntentType.BUDGET_RISK;
        }

        // 5. AI 解析成功时，参考 AI intent
        if (plan != null && Boolean.TRUE.equals(plan.getValid())) {
            String intent = plan.getIntent();

            if ("RECORD_EXPENSE".equals(intent)) {
                return IntentType.RECORD_EXPENSE;
            }
            if ("QUERY_EXPENSE".equals(intent)) {
                return IntentType.QUERY_EXPENSE;
            }
            if ("ANALYZE_EXPENSE".equals(intent)) {
                return IntentType.ANALYZE_EXPENSE;
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
                || message.contains("还能控制");
    }

    private boolean isRecordExpenseMessage(String message) {
        return message.matches(".*\\d+.*")
                && !isQueryMessage(message);
    }
}
```



在实现类中加入意图识别，返回最终的意图

#### AgentServiceImpl

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.enums.IntentType;
import com.test.service.AgentIntentService;
import com.test.service.AgentPlanService;
import com.test.service.AgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentPlanService agentPlanService;

    @Autowired
    private AgentIntentService agentIntentService;

    @Override
    public AgentChatResponse chat(Long userId, String sessionId, String message) {

        List<AgentAction> actions = new ArrayList<>();

        // 1. AI 解析用户输入
        AgentPlan plan = agentPlanService.parsePlan(sessionId, message);

        // 2. 规则 + AI 结果，判断最终意图
        IntentType finalIntent = agentIntentService.decideIntent(message, plan);

        AgentChatResponse response = new AgentChatResponse();

        response.setReply(
                "AI识别结果：意图=" + plan.getIntent()
                        + "，金额=" + plan.getAmount()
                        + "，类别=" + plan.getCategory()
                        + "，描述=" + plan.getDescription()
                        + "，valid=" + plan.getValid()
                        + "，最终意图=" + finalIntent
        );

        response.setActions(actions);

        return response;
    }
}
```

### 3.3.1根据最终意图 finalIntent 分发到 Handler



创建根据意图去执行方法的Handler类

创建记账调度器

#### AgentRecordHandler

DAO还没完善，这里先模拟存储成功

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentRecordHandler {

    public AgentChatResponse handle(Long userId,
                                    String sessionId,
                                    String message,
                                    AgentPlan plan,
                                    List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        // 1. 判断 AI 是否解析成功
        if (plan == null || !Boolean.TRUE.equals(plan.getValid())) {
            response.setReply("我没有识别出完整的记账信息，请换一种说法，例如：今天奶茶18。");
            response.setActions(actions);
            return response;
        }

        // 2. 判断金额是否存在
        if (plan.getAmount() == null) {
            response.setReply("我识别到你想记账，但没有识别到金额，请补充金额。");
            response.setActions(actions);
            return response;
        }

        // 3. 类别兜底
        String category = plan.getCategory();
        if (category == null || category.trim().isEmpty()) {
            category = "其他";
        }

        // 4. 描述兜底
        String description = plan.getDescription();
        if (description == null || description.trim().isEmpty()) {
            description = "日常消费";
        }

        // 5. 先不写数据库，先模拟执行成功
        AgentAction action = new AgentAction();
        action.setName("recordExpense");
        action.setSuccess(true);
        action.setMessage("记账动作已识别，当前为模拟保存");

        actions.add(action);

        response.setReply("好嘞，已识别到一笔消费："
                + category
                + plan.getAmount()
                + "元，"
                + description
                + "。");

        response.setActions(actions);

        return response;
    }
}
```



把意图分发写入AgentServiceImpl 实现类

#### AgentServiceImpl

使用Switch去判断类型

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.enums.IntentType;
import com.test.service.AgentIntentService;
import com.test.service.AgentPlanService;
import com.test.service.AgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentPlanService agentPlanService;

    @Autowired
    private AgentIntentService agentIntentService;

    @Autowired
    private AgentRecordHandler agentRecordHandler;

    @Override
    public AgentChatResponse chat(Long userId, String sessionId, String message) {

        List<AgentAction> actions = new ArrayList<>();

        // 1. AI 解析计划
        AgentPlan plan = agentPlanService.parsePlan(sessionId, message);

        // 2. 后端最终意图裁决
        IntentType finalIntent = agentIntentService.decideIntent(message, plan);

        System.out.println("最终意图：" + finalIntent);

        // 3. 根据最终意图分发
        switch (finalIntent) {
            case RECORD_EXPENSE:
                return agentRecordHandler.handle(userId, sessionId, message, plan, actions);

            case QUERY_EXPENSE:
                return buildTempResponse("已识别为消费查询，后续接入查询 Handler。", actions);

            case ANALYZE_EXPENSE:
                return buildTempResponse("已识别为消费分析，后续接入分析 Handler。", actions);

            case SET_BUDGET:
                return buildTempResponse("已识别为预算设置，后续接入预算 Handler。", actions);

            case BUDGET_RISK:
                return buildTempResponse("已识别为预算风险分析，后续接入预算风险 Handler。", actions);

            case CHAT:
            default:
                return buildTempResponse("已识别为普通聊天，后续接入闲聊回复。", actions);
        }
    }

    private AgentChatResponse buildTempResponse(String reply, List<AgentAction> actions) {
        AgentChatResponse response = new AgentChatResponse();
        response.setReply(reply);
        response.setActions(actions);
        return response;
    }
}
```



>测试
>
>```
>{
>  "userId": 1,
>  "sessionId": "session_test_backend_001",
>  "message": "今天奶茶18"
>}
>```
>
>```
>{
>  "code": 200,
>  "msg": "success",
>  "data": {
>    "reply": "好嘞，已识别到一笔消费：饮品18元，奶茶。",
>    "actions": [
>      {
>        "name": "recordExpense",
>        "success": true,
>        "message": "记账动作已识别，当前为模拟保存"
>      }
>    ]
>  }
>}
>```



## 4.数据库表

创建数据库表



## 5.业务实现

### 5.1.1记账功能

#### ExpenseRecord 

创建记账实体类ExpenseRecord

```java
package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("expense_record")
public class ExpenseRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private BigDecimal amount;

    private Long categoryId;

    private String category;

    /**
     * EXPENSE 支出，INCOME 收入
     */
    private String recordType;

    private String description;

    /**
     * 记账日期：例如 2026-05-29
     */
    private LocalDate expenseTime;

    /**
     * 具体发生时间，可选
     */
    private LocalDateTime expenseDatetime;

    private Long accountId;

    /**
     * AGENT、MANUAL、IMPORT
     */
    private String sourceType;

    private String sourceText;

    private String sessionId;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
```



#### ExpenseRecord

对接数据库继续操作的记账mapper

```java
package com.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.test.entity.ExpenseRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExpenseRecordMapper extends BaseMapper<ExpenseRecord> {
}
```



#### BulterAgentApplication

启动类添加扫mapper

```java
package com.test;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.test.mapper")
public class BulterAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(BulterAgentApplication.class, args);
    }

}
```



#### ExpenseRecordService

创建记账功能service接口

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.ExpenseRecord;

import java.math.BigDecimal;
import java.time.LocalDate;

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
}
```



#### ExpenseRecordServiceImpl

实现service接口

```java
package com.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.entity.ExpenseRecord;
import com.test.mapper.ExpenseRecordMapper;
import com.test.service.ExpenseRecordService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class ExpenseRecordServiceImpl
        extends ServiceImpl<ExpenseRecordMapper, ExpenseRecord>
        implements ExpenseRecordService {

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
//        参数校验
        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("金额必须大于0");
        }

        if (category == null || category.trim().isEmpty()) {
            category = "其他";
        }

        if (description == null || description.trim().isEmpty()) {
            description = "日常消费";
        }

        if (expenseTime == null) {
            expenseTime = LocalDate.now();
        }

        ExpenseRecord record = new ExpenseRecord();

        record.setUserId(userId);
        record.setAmount(amount);
        record.setCategory(category);
        record.setRecordType("EXPENSE");
        record.setDescription(description);
        record.setExpenseTime(expenseTime);
        record.setExpenseDatetime(LocalDateTime.now());
        record.setSourceType("AGENT");
        record.setSourceText(sourceText);
        record.setSessionId(sessionId);
        record.setDeleted(0);

        this.save(record);

        return record.getId();
    }
}
```



> 测试
>
> ```
> {
>   "userId": 1,
>   "sessionId": "session_test_backend_001",
>   "message": "今天工资到账5000"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "reply": "好嘞，已识别到一笔消费：工资5000元，工资到账。",
>     "actions": [
>       {
>         "name": "recordExpense",
>         "success": true,
>         "message": "记账动作已识别，当前为模拟保存"
>       }
>     ]
>   }
> }
> ```
>
> ```
> {
>   "userId": 1,
>   "sessionId": "session_test_backend_001",
>   "message": "今天奶茶18"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "reply": "好嘞，已识别到一笔消费：饮品18元，奶茶。",
>     "actions": [
>       {
>         "name": "recordExpense",
>         "success": true,
>         "message": "记账动作已识别，当前为模拟保存"
>       }
>     ]
>   }
> }
> ```



兜底
ExpenseRecordServiceImpl



```
package com.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.entity.ExpenseRecord;
import com.test.mapper.ExpenseRecordMapper;
import com.test.service.ExpenseRecordService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class ExpenseRecordServiceImpl
        extends ServiceImpl<ExpenseRecordMapper, ExpenseRecord>
        implements ExpenseRecordService {

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
}
```

接入数据库业务

#### AgentRecordHandler

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.service.ExpenseRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class AgentRecordHandler {

    @Autowired
    private ExpenseRecordService expenseRecordService;

    public AgentChatResponse handle(Long userId,
                                    String sessionId,
                                    String message,
                                    AgentPlan plan,
                                    List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        // 1. 判断 AI 是否解析成功
        if (plan == null || !Boolean.TRUE.equals(plan.getValid())) {
            response.setReply("我没有识别出完整的记账信息，请换一种说法，例如：今天奶茶18。");
            response.setActions(actions);
            return response;
        }

        // 2. 判断金额是否存在
        if (plan.getAmount() == null) {
            response.setReply("我识别到你想记账，但没有识别到金额，请补充金额。");
            response.setActions(actions);
            return response;
        }

        // 3. 类别兜底
        String category = plan.getCategory();
        if (category == null || category.trim().isEmpty()) {
            category = "其他";
        }

        // 4. 描述兜底
        String description = plan.getDescription();
        if (description == null || description.trim().isEmpty()) {
            description = "日常记录";
        }

        // 5. 收支类型兜底
        String recordType = plan.getRecordType();
        if (recordType == null || recordType.trim().isEmpty()) {
            recordType = "EXPENSE";
        }

        // 6. 调用业务 Service，真正保存到数据库
        Long recordId = expenseRecordService.createRecord(
                userId,
                plan.getAmount(),
                category,
                recordType,
                description,
                LocalDate.now(),
                "AGENT",
                message,
                sessionId
        );

        // 7. 组装 action
        AgentAction action = new AgentAction();

        if ("INCOME".equalsIgnoreCase(recordType)) {
            action.setName("recordIncome");
            action.setMessage("收入记录已保存，记录ID：" + recordId);
        } else {
            action.setName("recordExpense");
            action.setMessage("支出记录已保存，记录ID：" + recordId);
        }

        action.setSuccess(true);
        actions.add(action);

        // 8. 组装回复
        String typeText = "INCOME".equalsIgnoreCase(recordType) ? "收入" : "支出";

        response.setReply("好嘞，已帮你记下一笔"
                + typeText
                + "："
                + category
                + plan.getAmount()
                + "元，"
                + description
                + "。");

        response.setActions(actions);

        return response;
    }
}
```



> 测试
>
> ```
> {
>   "userId": 1,
>   "sessionId": "session_test_backend_001",
>   "message": "今天奶茶18"
> }
> ```
>
> 
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "reply": "好嘞，已帮你记下一笔支出：饮品18元，奶茶。",
>     "actions": [
>       {
>         "name": "recordExpense",
>         "success": true,
>         "message": "支出记录已保存，记录ID：1"
>       }
>     ]
>   }
> }
> ```
>
> ```
> {
>   "userId": 1,
>   "sessionId": "session_test_backend_001",
>   "message": "今天工资到账5000"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "reply": "好嘞，已帮你记下一笔收入：工资5000元，工资到账。",
>     "actions": [
>       {
>         "name": "recordIncome",
>         "success": true,
>         "message": "收入记录已保存，记录ID：2"
>       }
>     ]
>   }
> }
> ```



### 5.2.1查询功能

在ExpenseRecordService里添加查询方法

#### ExpenseRecordService

添加sumAmountByDateRange方法

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.ExpenseRecord;

import java.math.BigDecimal;
import java.time.LocalDate;

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
}
```

在实现类中实现方法

#### ExpenseRecordServiceImpl.sumAmountByDateRange

```
@Override
public BigDecimal sumAmountByDateRange(Long userId,
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
```



创建查询调度器，根据 意图去分发查询功能

#### AgentStatisticsHandler

创建AgentStatisticsHandler

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.service.ExpenseRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class AgentStatisticsHandler {

    @Autowired
    private ExpenseRecordService expenseRecordService;

    public AgentChatResponse handleQuery(Long userId,
                                         String message,
                                         List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        if (userId == null) {
            response.setReply("用户ID不能为空，无法查询消费记录。");
            response.setActions(actions);
            return response;
        }

        LocalDate now = LocalDate.now();
        LocalDate startDate = now.withDayOfMonth(1);
        LocalDate endDate = now.withDayOfMonth(now.lengthOfMonth());

        BigDecimal totalExpense = expenseRecordService.sumAmountByDateRange(
                userId,
                "EXPENSE",
                startDate,
                endDate
        );

        BigDecimal totalIncome = expenseRecordService.sumAmountByDateRange(
                userId,
                "INCOME",
                startDate,
                endDate
        );

        BigDecimal balance = totalIncome.subtract(totalExpense);

        AgentAction action = new AgentAction();
        action.setName("queryExpense");
        action.setSuccess(true);
        action.setMessage("消费查询成功");
        actions.add(action);

        if (message != null && message.contains("收入")) {
            response.setReply("你本月收入共 " + totalIncome + " 元。");
        } else if (message != null && message.contains("结余")) {
            response.setReply("你本月收入共 " + totalIncome
                    + " 元，支出共 " + totalExpense
                    + " 元，结余 " + balance + " 元。");
        } else {
            response.setReply("你本月支出共 " + totalExpense + " 元。");
        }

        response.setActions(actions);

        return response;
    }
}
```



修改AgentServiceImpl,把写好的调度器写入实现类

#### AgentServiceImpl

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.enums.IntentType;
import com.test.service.AgentIntentService;
import com.test.service.AgentPlanService;
import com.test.service.AgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentPlanService agentPlanService;

    @Autowired
    private AgentIntentService agentIntentService;

    @Autowired
    private AgentRecordHandler agentRecordHandler;
    @Autowired
    private AgentStatisticsHandler agentStatisticsHandler;

    @Override
    public AgentChatResponse chat(Long userId, String sessionId, String message) {

        List<AgentAction> actions = new ArrayList<>();

        // 1. AI 解析计划
        AgentPlan plan = agentPlanService.parsePlan(sessionId, message);

        // 2. 后端最终意图裁决
        IntentType finalIntent = agentIntentService.decideIntent(message, plan);


        // 3. 根据最终意图分发
        switch (finalIntent) {
            case RECORD_EXPENSE:
                return agentRecordHandler.handle(userId, sessionId, message, plan, actions);

            case QUERY_EXPENSE:
                return agentStatisticsHandler.handleQuery(userId, message, actions);

            case ANALYZE_EXPENSE:
                return buildTempResponse("已识别为消费分析，后续接入分析 Handler。", actions);

            case SET_BUDGET:
                return buildTempResponse("已识别为预算设置，后续接入预算 Handler。", actions);

            case BUDGET_RISK:
                return buildTempResponse("已识别为预算风险分析，后续接入预算风险 Handler。", actions);

            case CHAT:
            default:
                return buildTempResponse("已识别为普通聊天，后续接入闲聊回复。", actions);
        }
    }

    private AgentChatResponse buildTempResponse(String reply, List<AgentAction> actions) {
        AgentChatResponse response = new AgentChatResponse();
        response.setReply(reply);
        response.setActions(actions);
        return response;
    }
}
```



> 测试
>
> {
>   "userId": 1,
>   "sessionId": "session_test_backend_001",
>   "message": "今天花了多少钱呀"
> }
>
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "reply": "你本月支出共 18.00 元。",
>     "actions": [
>       {
>         "name": "queryExpense",
>         "success": true,
>         "message": "消费查询成功"
>       }
>     ]
>   }
> }

> 问题：现在只能查当月支出



增加时间范围查询

#### AgentStatisticsHandler 

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
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
        if (message != null && message.contains("收入")) {
            response.setReply("你" + timeText + "收入共 " + totalIncome + " 元。");
        } else if (message != null && message.contains("结余")) {
            response.setReply("你" + timeText + "收入共 " + totalIncome
                    + " 元，支出共 " + totalExpense
                    + " 元，结余 " + balance + " 元。");
        } else {
            response.setReply("你" + timeText + "支出共 " + totalExpense + " 元。");
        }

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
}
```



> 测试
>
> ```
> {
>   "userId": 1,
>   "sessionId": "session_test_backend_001",
>   "message": "这个月结余多少"
> }
> ```
>
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "reply": "你本月收入共 5000.00 元，支出共 18.00 元，结余 4982.00 元。",
>     "actions": [
>       {
>         "name": "queryExpense",
>         "success": true,
>         "message": "查询成功"
>       }
>     ]
>   }
> }

### 5.3.1消费分析功能

创建实体类CategoryAmountDTO

#### CategoryAmountDTO

```java
package com.test.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CategoryAmountDTO {

    private String category;

    private BigDecimal amount;
}
```

#### ExpenseRecordMapper

在mapper中添加分类统计的方法

>按照 category 分组
>统计每个分类的 amount 总和
>按金额从高到低排序

```
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
```



#### ExpenseRecordService 

在service层中实现sumAmountGroupByCategory方法的接口

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.CategoryAmountDTO;
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
}
```



#### ExpenseRecordServiceImpl.sumAmountGroupByCategory

实现接口

```java
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
```



#### AgentStatisticsHandler 

在查询调度器中添加分析方法

```java
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
```



#### AgentServiceImpl

把分析功能放入AgentServiceImpl中替换功能调度

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.enums.IntentType;
import com.test.service.AgentIntentService;
import com.test.service.AgentPlanService;
import com.test.service.AgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentPlanService agentPlanService;

    @Autowired
    private AgentIntentService agentIntentService;

    @Autowired
    private AgentRecordHandler agentRecordHandler;
    @Autowired
    private AgentStatisticsHandler agentStatisticsHandler;

    @Override
    public AgentChatResponse chat(Long userId, String sessionId, String message) {

        List<AgentAction> actions = new ArrayList<>();

        // 1. AI 解析计划
        AgentPlan plan = agentPlanService.parsePlan(sessionId, message);

        // 2. 后端最终意图裁决
        IntentType finalIntent = agentIntentService.decideIntent(message, plan);


        // 3. 根据最终意图分发
        switch (finalIntent) {
            case RECORD_EXPENSE:
                return agentRecordHandler.handle(userId, sessionId, message, plan, actions);

            case QUERY_EXPENSE:
                return agentStatisticsHandler.handleQuery(userId, message, actions);

            case ANALYZE_EXPENSE:
                return agentStatisticsHandler.handleAnalyze(userId, message, actions);
            
                case SET_BUDGET:
                return buildTempResponse("已识别为预算设置，后续接入预算 Handler。", actions);

            case BUDGET_RISK:
                return buildTempResponse("已识别为预算风险分析，后续接入预算风险 Handler。", actions);

            case CHAT:
            default:
                return buildTempResponse("已识别为普通聊天，后续接入闲聊回复。", actions);
        }
    }

    private AgentChatResponse buildTempResponse(String reply, List<AgentAction> actions) {
        AgentChatResponse response = new AgentChatResponse();
        response.setReply(reply);
        response.setActions(actions);
        return response;
    }
}
```



> 测试
>
> ```
> {
>   "userId": 1,
>   "sessionId": "session_test_backend_001",
>   "message": "分析一下我这个月的消费情况"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "reply": "你本月支出共 18.00 元。 分类来看，饮品 18.00 元，占比 100.00%。",
>     "actions": [
>       {
>         "name": "analyzeExpense",
>         "success": true,
>         "message": "消费分析成功"
>       }
>     ]
>   }
> }
> ```
>
> 

### 5.4.1预算功能

#### Budget

预算实体类的创建

```java
package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("budget")
public class Budget {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 预算月份，例如 2026-05
     */
    private String budgetMonth;

    private Long categoryId;

    /**
     * TOTAL 表示总预算，餐饮/饮品/交通表示分类预算
     */
    private String category;

    private BigDecimal amount;

    /**
     * 预警比例，例如 80 表示使用 80% 时预警
     */
    private BigDecimal warningRate;

    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
```



#### BudgetMapper

在mapper中添加预算的方法，查询数据库

```
package com.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.test.entity.Budget;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BudgetMapper extends BaseMapper<Budget> {
}
```



#### BudgetService

创建预算方法的接口

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.Budget;

import java.math.BigDecimal;

public interface BudgetService extends IService<Budget> {

    Long setBudget(Long userId,
                   String budgetMonth,
                   String category,
                   BigDecimal amount);
}
```





#### BudgetServiceImpl

在实现类中实现预算功能

```java
package com.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.entity.Budget;
import com.test.mapper.BudgetMapper;
import com.test.service.BudgetService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class BudgetServiceImpl
        extends ServiceImpl<BudgetMapper, Budget>
        implements BudgetService {

    @Override
    public Long setBudget(Long userId,
                          String budgetMonth,
                          String category,
                          BigDecimal amount) {

        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        if (budgetMonth == null || budgetMonth.trim().isEmpty()) {
            throw new RuntimeException("预算月份不能为空");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("预算金额必须大于0");
        }

        if (category == null || category.trim().isEmpty()) {
            category = "TOTAL";
        }

        QueryWrapper<Budget> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("budget_month", budgetMonth)
                .eq("category", category)
                .eq("deleted", 0);

        Budget budget = this.getOne(queryWrapper);

        if (budget == null) {
            budget = new Budget();
            budget.setUserId(userId);
            budget.setBudgetMonth(budgetMonth);
            budget.setCategory(category);
            budget.setAmount(amount);
            budget.setWarningRate(new BigDecimal("80.00"));
            budget.setEnabled(1);
            budget.setDeleted(0);

            this.save(budget);
        } else {
            budget.setAmount(amount);
            budget.setEnabled(1);

            this.updateById(budget);
        }

        return budget.getId();
    }
}
```



#### AgentBudgetHandler

创建 预算调度器 实现有关预算的功能

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.service.BudgetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

@Service
public class AgentBudgetHandler {

    @Autowired
    private BudgetService budgetService;

    public AgentChatResponse handleSetBudget(Long userId,
                                             String message,
                                             AgentPlan plan,
                                             List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        if (userId == null) {
            response.setReply("用户ID不能为空，无法设置预算。");
            response.setActions(actions);
            return response;
        }

        if (plan == null || !Boolean.TRUE.equals(plan.getValid())) {
            response.setReply("我没有识别出完整的预算信息，请换一种说法，例如：帮我把本月预算设置为1800。");
            response.setActions(actions);
            return response;
        }

        if (plan.getAmount() == null) {
            response.setReply("我识别到你想设置预算，但没有识别到预算金额，请补充金额。");
            response.setActions(actions);
            return response;
        }

        String budgetMonth = YearMonth.now().toString();

        String category = plan.getCategory();
        if (category == null || category.trim().isEmpty()) {
            category = "TOTAL";
        }

        Long budgetId = budgetService.setBudget(
                userId,
                budgetMonth,
                category,
                plan.getAmount()
        );

        AgentAction action = new AgentAction();
        action.setName("setBudget");
        action.setSuccess(true);
        action.setMessage("预算设置成功，预算ID：" + budgetId);
        actions.add(action);

        if ("TOTAL".equals(category)) {
            response.setReply("已帮你把" + budgetMonth + "的总预算设置为 "
                    + plan.getAmount() + " 元。");
        } else {
            response.setReply("已帮你把" + budgetMonth + "的"
                    + category + "预算设置为 "
                    + plan.getAmount() + " 元。");
        }

        response.setActions(actions);

        return response;
    }
}
```





#### AgentServiceImpl

把功能写入实现类中

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.enums.IntentType;
import com.test.service.AgentIntentService;
import com.test.service.AgentPlanService;
import com.test.service.AgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentPlanService agentPlanService;

    @Autowired
    private AgentIntentService agentIntentService;

    @Autowired
    private AgentRecordHandler agentRecordHandler;
    @Autowired
    private AgentStatisticsHandler agentStatisticsHandler;
    @Autowired
    private AgentBudgetHandler agentBudgetHandler;

    @Override
    public AgentChatResponse chat(Long userId, String sessionId, String message) {

        List<AgentAction> actions = new ArrayList<>();

        // 1. AI 解析计划
        AgentPlan plan = agentPlanService.parsePlan(sessionId, message);

        // 2. 后端最终意图裁决
        IntentType finalIntent = agentIntentService.decideIntent(message, plan);


        // 3. 根据最终意图分发
        switch (finalIntent) {
            case RECORD_EXPENSE:
                return agentRecordHandler.handle(userId, sessionId, message, plan, actions);

            case QUERY_EXPENSE:
                return agentStatisticsHandler.handleQuery(userId, message, actions);

            case ANALYZE_EXPENSE:
                return agentStatisticsHandler.handleAnalyze(userId, message, actions);

            case SET_BUDGET:
                return agentBudgetHandler.handleSetBudget(userId, message, plan, actions);

            case BUDGET_RISK:
                return buildTempResponse("已识别为预算风险分析，后续接入预算风险 Handler。", actions);

            case CHAT:
            default:
                return buildTempResponse("已识别为普通聊天，后续接入闲聊回复。", actions);
        }
    }

    private AgentChatResponse buildTempResponse(String reply, List<AgentAction> actions) {
        AgentChatResponse response = new AgentChatResponse();
        response.setReply(reply);
        response.setActions(actions);
        return response;
    }
}
```



> 测试
>
> ```
> {
>   "userId": 1,
>   "sessionId": "session_test_backend_001",
>   "message": "帮我把本月预算设置为1800"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "reply": "已帮你把2026-05的总预算设置为 1800 元。",
>     "actions": [
>       {
>         "name": "setBudget",
>         "success": true,
>         "message": "预算设置成功，预算ID：1"
>       }
>     ]
>   }
> }
> ```



### 5.4.2预算风险判断能力

#### BudgetService

BudgetService添加查询预算方法

 

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.Budget;

import java.math.BigDecimal;

public interface BudgetService extends IService<Budget> {

    Long setBudget(Long userId,
                   String budgetMonth,
                   String category,
                   BigDecimal amount);

    Budget getBudget(Long userId,
                     String budgetMonth,
                     String category);
}
```

#### BudgetServiceImpl 

 在 BudgetServiceImpl 里实现 getBudget

```java
@Override
public Budget getBudget(Long userId,
                        String budgetMonth,
                        String category) {

    if (userId == null) {
        throw new RuntimeException("用户ID不能为空");
    }

    if (budgetMonth == null || budgetMonth.trim().isEmpty()) {
        throw new RuntimeException("预算月份不能为空");
    }

    if (category == null || category.trim().isEmpty()) {
        category = "TOTAL";
    }

    QueryWrapper<Budget> queryWrapper = new QueryWrapper<>();

    queryWrapper.eq("user_id", userId)
            .eq("budget_month", budgetMonth)
            .eq("category", category)
            .eq("enabled", 1)
            .eq("deleted", 0);

    return this.getOne(queryWrapper);
}
```



#### AgentBudgetRiskHandler

创建功能调度器AgentBudgetRiskHandler

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.entity.Budget;
import com.test.service.BudgetService;
import com.test.service.ExpenseRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class AgentBudgetRiskHandler {

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private ExpenseRecordService expenseRecordService;

    public AgentChatResponse handle(Long userId,
                                    String message,
                                    List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        if (userId == null) {
            response.setReply("用户ID不能为空，无法判断预算风险。");
            response.setActions(actions);
            return response;
        }

        // 1. 当前先默认判断本月总预算
        YearMonth yearMonth = YearMonth.now();
        String budgetMonth = yearMonth.toString();

        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // 2. 查询本月总预算
        Budget budget = budgetService.getBudget(
                userId,
                budgetMonth,
                "TOTAL"
        );

        if (budget == null || budget.getAmount() == null) {
            response.setReply("你还没有设置" + budgetMonth + "的总预算，请先设置预算，例如：帮我把本月预算设置为1800。");
            response.setActions(actions);
            return response;
        }

        BigDecimal budgetAmount = budget.getAmount();

        if (budgetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            response.setReply("当前预算金额无效，请重新设置预算。");
            response.setActions(actions);
            return response;
        }

        // 3. 查询本月支出
        BigDecimal usedAmount = expenseRecordService.sumAmountByDateRange(
                userId,
                "EXPENSE",
                startDate,
                endDate
        );

        // 4. 计算剩余预算
        BigDecimal remainingAmount = budgetAmount.subtract(usedAmount);

        // 5. 计算使用率
        BigDecimal usageRate = usedAmount
                .multiply(new BigDecimal("100"))
                .divide(budgetAmount, 2, RoundingMode.HALF_UP);

        // 6. 判断风险等级
        String level;
        String advice;

        if (usageRate.compareTo(new BigDecimal("100")) >= 0) {
            level = "OVER";
            advice = "已经超出预算，建议接下来控制非必要支出。";
        } else if (usageRate.compareTo(new BigDecimal("90")) >= 0) {
            level = "DANGER";
            advice = "预算使用率已经很高，接下来要谨慎消费。";
        } else if (usageRate.compareTo(new BigDecimal("80")) >= 0) {
            level = "WARNING";
            advice = "预算使用率接近预警线，建议适当控制消费。";
        } else {
            level = "NORMAL";
            advice = "目前预算比较安全，可以继续保持。";
        }

        // 7. 添加 action
        AgentAction action = new AgentAction();
        action.setName("checkBudgetRisk");
        action.setSuccess(true);
        action.setMessage("预算风险判断成功，风险等级：" + level);
        actions.add(action);

        // 8. 返回回复
        response.setReply("你" + budgetMonth + "的总预算是 "
                + budgetAmount
                + " 元，目前已支出 "
                + usedAmount
                + " 元，预算使用率为 "
                + usageRate
                + "%，剩余预算 "
                + remainingAmount
                + " 元。"
                + advice);

        response.setActions(actions);

        return response;
    }
}
```

#### AgentServiceImpl

把调度器放入AgentServiceImpl

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.enums.IntentType;
import com.test.service.AgentIntentService;
import com.test.service.AgentPlanService;
import com.test.service.AgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentPlanService agentPlanService;

    @Autowired
    private AgentIntentService agentIntentService;

    @Autowired
    private AgentRecordHandler agentRecordHandler;
    @Autowired
    private AgentStatisticsHandler agentStatisticsHandler;
    @Autowired
    private AgentBudgetHandler agentBudgetHandler;
    @Autowired
    private AgentBudgetRiskHandler agentBudgetRiskHandler;

    @Override
    public AgentChatResponse chat(Long userId, String sessionId, String message) {

        List<AgentAction> actions = new ArrayList<>();

        // 1. AI 解析计划
        AgentPlan plan = agentPlanService.parsePlan(sessionId, message);

        // 2. 后端最终意图裁决
        IntentType finalIntent = agentIntentService.decideIntent(message, plan);


        // 3. 根据最终意图分发
        switch (finalIntent) {
            case RECORD_EXPENSE:
                return agentRecordHandler.handle(userId, sessionId, message, plan, actions);

            case QUERY_EXPENSE:
                return agentStatisticsHandler.handleQuery(userId, message, actions);

            case ANALYZE_EXPENSE:
                return agentStatisticsHandler.handleAnalyze(userId, message, actions);

            case SET_BUDGET:
                return agentBudgetHandler.handleSetBudget(userId, message, plan, actions);

            case BUDGET_RISK:
                return agentBudgetRiskHandler.handle(userId, message, actions);

            case CHAT:
            default:
                return buildTempResponse("已识别为普通聊天，后续接入闲聊回复。", actions);
        }
    }

    private AgentChatResponse buildTempResponse(String reply, List<AgentAction> actions) {
        AgentChatResponse response = new AgentChatResponse();
        response.setReply(reply);
        response.setActions(actions);
        return response;
    }
}
```



> 测试
>
> ```
> {
>   "userId": 1,
>   "sessionId": "session_test_backend_001",
>   "message": "帮我把本月预算设置为1800"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "reply": "已帮你把2026-06的总预算设置为 1800 元。",
>     "actions": [
>       {
>         "name": "setBudget",
>         "success": true,
>         "message": "预算设置成功，预算ID：5"
>       }
>     ]
>   }
> }
> ```
>
> ```
> {
>   "userId": 1,
>   "sessionId": "session_test_backend_001",
>   "message": "我这个月会不会超预算"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "reply": "你2026-06的总预算是 1800.00 元，目前已支出 0.00 元，预算使用率为 0.00%，剩余预算 1800.00 元。目前预算比较安全，可以继续保持。",
>     "actions": [
>       {
>         "name": "checkBudgetRisk",
>         "success": true,
>         "message": "预算风险判断成功，风险等级：NORMAL"
>       }
>     ]
>   }
> }
> ```



### 5.5.1对话模块

在AIservice中添加chat方法，引入实现对话的方法

```java
package com.test.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface AIService {

    @SystemMessage(fromResource = "agent-plan-prompt.txt")
    String plan(@MemoryId String sessionId, @UserMessage String message);

    @SystemMessage(fromResource = "system-prompt.txt")
    String chat(@MemoryId String sessionId, @UserMessage String message);


}
```

#### system-prompt

```txt
你是“小布”，一个 AI 个人记账管家。

你的主要职责：
1. 帮助用户记录消费、查询消费、分析消费和管理预算。
2. 当用户表达记账、查询、分析等业务需求时，不要自行声称已完成操作，具体业务结果由后端系统确认。
3. 当用户只是闲聊、吐槽或说废话时，可以自然回应，但不要偏离“个人记账管家”的身份。

你的性格：
1. 亲切、自然、像朋友一样。
2. 可以适当幽默，但不要夸张表演。
3. 不要长篇角色扮演。
4. 不要模仿任何动漫、影视、游戏或真实人物角色。
5. 不要使用“小新”“蜡笔小新”“动感超人”等具体 IP 人设。
6. 不要描写复杂动作、舞台表演或夸张场景。
7. 不要大量使用 emoji。
8. 普通闲聊回复控制在 2 到 4 句话。
9. 每次回复尽量不超过 120 个中文字符。
10. 不要主动编造消费记录。
11. 不要建议用户记录虚构消费，例如“精神损耗费”“摸鱼费”“负数消费”等。
12. 如果用户情绪低落，先简单安慰，再给一个轻量建议。

回复风格要求：
1. 简洁。
2. 温和。
3. 不说教。
4. 不过度可爱。
5. 不主动展开太多内容。
6. 除非用户明确要求，否则不要列很多条建议。

示例：

用户：你好
回复：你好呀，我是小布，你的 AI 记账管家。你可以让我帮你记账、查消费，也可以随便聊两句。

用户：我不想上班
回复：懂你，有时候真的会有点提不起劲。先别太逼自己，今天先完成最重要的一件事就很好了。

用户：今天好烦
回复：辛苦了，今天可能确实不太顺。先缓一缓，别急着否定自己。

用户：你是谁
回复：我是小布，一个可以帮你记账、查消费、做消费分析的 AI 管家。
```

### 5.5.2对话功能

#### AgentChatReplyService

创建需要实现功能的service类

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentChatReplyService {

    @Autowired
    private AIService aiService;

    public AgentChatResponse chat(String sessionId,
                                  String message,
                                  List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        try {
            String reply = aiService.chat(sessionId, message);

            response.setReply(reply);
            response.setActions(actions);

            return response;
        } catch (Exception e) {
            response.setReply("我刚刚有点走神了，你可以再说一遍吗？");
            response.setActions(actions);
            return response;
        }
    }
}
```



#### AgentService

将对话添加到AgentService中

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.enums.IntentType;
import com.test.service.AgentIntentService;
import com.test.service.AgentPlanService;
import com.test.service.AgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentPlanService agentPlanService;

    @Autowired
    private AgentIntentService agentIntentService;

    @Autowired
    private AgentRecordHandler agentRecordHandler;
    @Autowired
    private AgentStatisticsHandler agentStatisticsHandler;
    @Autowired
    private AgentBudgetHandler agentBudgetHandler;
    @Autowired
    private AgentBudgetRiskHandler agentBudgetRiskHandler;
    @Autowired
    private AgentChatReplyService agentChatReplyService;

    @Override
    public AgentChatResponse chat(Long userId, String sessionId, String message) {

        List<AgentAction> actions = new ArrayList<>();

        // 1. AI 解析计划
        AgentPlan plan = agentPlanService.parsePlan(sessionId, message);

        // 2. 后端最终意图裁决
        IntentType finalIntent = agentIntentService.decideIntent(message, plan);


        // 3. 根据最终意图分发
        switch (finalIntent) {
            case RECORD_EXPENSE:
                return agentRecordHandler.handle(userId, sessionId, message, plan, actions);

            case QUERY_EXPENSE:
                return agentStatisticsHandler.handleQuery(userId, message, actions);

            case ANALYZE_EXPENSE:
                return agentStatisticsHandler.handleAnalyze(userId, message, actions);

            case SET_BUDGET:
                return agentBudgetHandler.handleSetBudget(userId, message, plan, actions);

            case BUDGET_RISK:
                return agentBudgetRiskHandler.handle(userId, message, actions);

            case CHAT:
            default:
                return agentChatReplyService.chat(sessionId, message, actions);
        }
    }

    private AgentChatResponse buildTempResponse(String reply, List<AgentAction> actions) {
        AgentChatResponse response = new AgentChatResponse();
        response.setReply(reply);
        response.setActions(actions);
        return response;
    }
}
```



>测试
>
>```
>{
>  "userId": 1,
>  "sessionId": "session_test_backend_001",
>  "message": "你好"
>}
>```
>
>```
>{
>  "code": 200,
>  "msg": "success",
>  "data": {
>    "reply": "你好呀，我是小布，你的 AI 记账管家～  \n记账、查账、分析开销，或者随便聊聊，我都在。",
>    "actions": []
>  }
>}
>```
>
>

### 5.6.1记忆功能优化

现在的ai记忆功能并不能复用同一个会话记忆，目前只是LangChain4j的内存窗口记忆
考虑先把对话记忆持久化到mysql中，后期使用redis进行二次处理



### 5.6.2对话记录持久化到 MySQL

#### AgentChatMessage 

创建信息存储的实体类

```java
package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_chat_message")
public class AgentChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionId;

    /**
     * USER、ASSISTANT、SYSTEM
     */
    private String role;

    private String content;

    /**
     * AI识别意图
     */
    private String intent;

    /**
     * 后端最终意图
     */
    private String finalIntent;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
```



#### AgentChatMessageMapper

创建mapper访问数据库

```java
package com.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.test.entity.AgentChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentChatMessageMapper extends BaseMapper<AgentChatMessage> {
}
```



#### AgentChatMessageService

创建service类实现业务功能

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.AgentChatMessage;

public interface AgentChatMessageService extends IService<AgentChatMessage> {

    void saveMessage(Long userId,
                     String sessionId,
                     String role,
                     String content,
                     String intent,
                     String finalIntent);
}
```



创建Impl去实现对应的方法

#### AgentChatMessageServiceImpl

```java
package com.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.entity.AgentChatMessage;
import com.test.mapper.AgentChatMessageMapper;
import com.test.service.AgentChatMessageService;
import org.springframework.stereotype.Service;

@Service
public class AgentChatMessageServiceImpl
        extends ServiceImpl<AgentChatMessageMapper, AgentChatMessage>
        implements AgentChatMessageService {

    @Override
    public void saveMessage(Long userId,
                            String sessionId,
                            String role,
                            String content,
                            String intent,
                            String finalIntent) {

        if (userId == null || sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }

        if (content == null || content.trim().isEmpty()) {
            return;
        }

        AgentChatMessage message = new AgentChatMessage();

        message.setUserId(userId);
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setIntent(intent);
        message.setFinalIntent(finalIntent);
        message.setDeleted(0);

        this.save(message);
    }
}
```

#### AgentServiceImpl

修改 AgentServiceImpl中chat方法，增加一个保存助手信息

```java
@Override
public AgentChatResponse chat(Long userId, String sessionId, String message) {

    List<AgentAction> actions = new ArrayList<>();

    // 1. 先保存用户消息
    agentChatMessageService.saveMessage(
            userId,
            sessionId,
            "USER",
            message,
            null,
            null
    );

    // 2. AI 解析计划
    AgentPlan plan = agentPlanService.parsePlan(sessionId, message);

    // 3. 后端最终意图裁决
    IntentType finalIntent = agentIntentService.decideIntent(message, plan);

    AgentChatResponse response;

    // 4. 根据最终意图分发
    switch (finalIntent) {
        case RECORD_EXPENSE:
            response = agentRecordHandler.handle(userId, sessionId, message, plan, actions);
            break;

        case QUERY_EXPENSE:
            response = agentStatisticsHandler.handleQuery(userId, message, actions);
            break;

        case ANALYZE_EXPENSE:
            response = agentStatisticsHandler.handleAnalyze(userId, message, actions);
            break;

        case SET_BUDGET:
            response = agentBudgetHandler.handleSetBudget(userId, message, plan, actions);
            break;

        case BUDGET_RISK:
            response = agentBudgetRiskHandler.handle(userId, message, actions);
            break;

        case CHAT:
        default:
            response = agentChatReplyService.chat(sessionId, message, actions);
            break;
    }

    // 5. 保存助手回复
    agentChatMessageService.saveMessage(
            userId,
            sessionId,
            "ASSISTANT",
            response.getReply(),
            plan == null ? null : plan.getIntent(),
            finalIntent == null ? null : finalIntent.name()
    );

    return response;
}
```

> 测试
>
> ```
> {
>   "userId": 1,
>   "sessionId": "session_test_backend_001",
>   "message": "海底捞115"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "reply": "好嘞，已帮你记下一笔支出：餐饮115元，海底捞。",
>     "actions": [
>       {
>         "name": "recordExpense",
>         "success": true,
>         "message": "支出记录已保存，记录ID：4"
>       }
>     ]
>   }
> }
> ```
>
> ```
> 3,1,session_test_backend_001,USER,海底捞115,,,2026-06-01 10:57:02,2026-06-01 10:57:02,0
> 4,1,session_test_backend_001,ASSISTANT,好嘞，已帮你记下一笔支出：餐饮115元，海底捞。,RECORD_EXPENSE,RECORD_EXPENSE,2026-06-01 10:57:04,2026-06-01 10:57:04,0
> 
> ```
>
> 



### 5.7.1Agent 动作日志持久化

把Agent的操作结果保存成日志

#### AgentActionLog 

创建对应的实体类

```java
package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_action_log")
public class AgentActionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionId;

    /**
     * 动作名称：recordExpense、recordIncome、queryExpense、setBudget 等
     */
    private String actionName;

    /**
     * 是否成功：1成功，0失败
     */
    private Integer success;

    /**
     * 动作说明
     */
    private String message;

    /**
     * 用户原始输入
     */
    private String requestText;

    /**
     * 动作结果JSON，当前可先不用
     */
    private String resultData;

    /**
     * 关联业务记录ID，例如 expense_record.id，当前可先不填
     */
    private Long relatedRecordId;

    private LocalDateTime createTime;
}
```

#### AgentActionLogMapper

创建 AgentActionLogMapper

```java
package com.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.test.entity.AgentActionLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentActionLogMapper extends BaseMapper<AgentActionLog> {
}
```

#### AgentActionLogService

创建 AgentActionLogService

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.AgentAction;
import com.test.entity.AgentActionLog;

import java.util.List;

public interface AgentActionLogService extends IService<AgentActionLog> {

    void saveActions(Long userId,
                     String sessionId,
                     String requestText,
                     List<AgentAction> actions);
}
```

#### AgentActionLogServiceImpl



```java
package com.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.dto.AgentAction;
import com.test.entity.AgentActionLog;
import com.test.mapper.AgentActionLogMapper;
import com.test.service.AgentActionLogService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentActionLogServiceImpl
        extends ServiceImpl<AgentActionLogMapper, AgentActionLog>
        implements AgentActionLogService {

    @Override
    public void saveActions(Long userId,
                            String sessionId,
                            String requestText,
                            List<AgentAction> actions) {

        if (userId == null || actions == null || actions.isEmpty()) {
            return;
        }

        for (AgentAction action : actions) {
            if (action == null) {
                continue;
            }

            AgentActionLog log = new AgentActionLog();

            log.setUserId(userId);
            log.setSessionId(sessionId);
            log.setActionName(action.getName());
            log.setSuccess(Boolean.TRUE.equals(action.getSuccess()) ? 1 : 0);
            log.setMessage(action.getMessage());
            log.setRequestText(requestText);

            this.save(log);
        }
    }
}
```



#### AgentServiceImpl

修改 AgentServiceImpl，添加操作日志保存方法

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.enums.IntentType;
import com.test.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentPlanService agentPlanService;

    @Autowired
    private AgentIntentService agentIntentService;

    @Autowired
    private AgentRecordHandler agentRecordHandler;

    @Autowired
    private AgentStatisticsHandler agentStatisticsHandler;

    @Autowired
    private AgentBudgetHandler agentBudgetHandler;

    @Autowired
    private AgentBudgetRiskHandler agentBudgetRiskHandler;

    @Autowired
    private AgentChatReplyService agentChatReplyService;

    @Autowired
    private AgentChatMessageService agentChatMessageService;

    @Autowired
    private AgentActionLogService agentActionLogService;

    @Override
    public AgentChatResponse chat(Long userId, String sessionId, String message) {

        List<AgentAction> actions = new ArrayList<>();

        // 1. 先保存用户消息
        agentChatMessageService.saveMessage(
                userId,
                sessionId,
                "USER",
                message,
                null,
                null
        );

        // 2. AI 解析计划
        AgentPlan plan = agentPlanService.parsePlan(sessionId, message);

        // 3. 后端最终意图裁决
        IntentType finalIntent = agentIntentService.decideIntent(message, plan);

        AgentChatResponse response;

        // 4. 根据最终意图分发
        switch (finalIntent) {
            case RECORD_EXPENSE:
                response = agentRecordHandler.handle(userId, sessionId, message, plan, actions);
                break;

            case QUERY_EXPENSE:
                response = agentStatisticsHandler.handleQuery(userId, message, actions);
                break;

            case ANALYZE_EXPENSE:
                response = agentStatisticsHandler.handleAnalyze(userId, message, actions);
                break;

            case SET_BUDGET:
                response = agentBudgetHandler.handleSetBudget(userId, message, plan, actions);
                break;

            case BUDGET_RISK:
                response = agentBudgetRiskHandler.handle(userId, message, actions);
                break;

            case CHAT:
            default:
                response = agentChatReplyService.chat(sessionId, message, actions);
                break;
        }

        // 5. 保存助手回复
        agentChatMessageService.saveMessage(
                userId,
                sessionId,
                "ASSISTANT",
                response.getReply(),
                plan == null ? null : plan.getIntent(),
                finalIntent == null ? null : finalIntent.name()
        );

        //6. 操作日志保存
        agentActionLogService.saveActions(
                userId,
                sessionId,
                message,
                response.getActions()
        );

        return response;
    }
}
```



> 测试
>
> ```
> {
>   "userId": 1,
>   "sessionId": "session_test_backend_001",
>   "message": "海底捞115"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "reply": "好嘞，已帮你记下一笔支出：餐饮115元，海底捞。",
>     "actions": [
>       {
>         "name": "recordExpense",
>         "success": true,
>         "message": "支出记录已保存，记录ID：5"
>       }
>     ]
>   }
> }
> ```
>
> ```
> 1,1,session_test_backend_001,recordExpense,1,支出记录已保存，记录ID：5,海底捞115,,,2026-06-01 11:05:32
> 
> ```
>
> 





### 5.8.1Redis 短期会话缓存 / 会话记忆优化

配置redis

#### application

```
server:
  port: 8181
  servlet:
    encoding:
      charset: UTF-8
      force: true

spring:
  application:
    name: Butler
  datasource:
    url: jdbc:mysql://localhost:3306/bulter_agent?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      timeout: 3000ms

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

langchain4j:
  community:
    dashscope:
      chat-model:
        api-key: ${AI_API_KEY}
        model-name: qwen-long
      embedding-model:
        api-key: ${AI_API_KEY}
        model-name: text-embedding-v4
      streaming-chat-model:
        api-key: ${AI_API_KEY}
        model-name: qwen-long

knife4j:
  enable: true
  setting:
    language: zh_cn

springdoc:
  group-configs:
    - group: 默认接口
      paths-to-match: /**
      packages-to-scan: com.test.controller

```

#### pom

pom中添加redis依赖

```java
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### 5.8.2Redis 短期会话缓存

#### AgentMemoryCacheService

创建AgentMemoryCacheService业务

```java
package com.test.service;

import java.util.List;

public interface AgentMemoryCacheService {

    void appendMessage(String sessionId, String role, String content);

    List<String> getRecentMessages(String sessionId);
}
```

实现功能

#### AgentMemoryCacheServiceImpl

```java
package com.test.service.impl;

import com.test.service.AgentMemoryCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class AgentMemoryCacheServiceImpl implements AgentMemoryCacheService {

    private static final String KEY_PREFIX = "agent:chat:memory:";

    /**
     * 每个 sessionId 最多保留最近 10 条消息
     */
    private static final int MAX_MESSAGE_COUNT = 10;

    /**
     * Redis 会话缓存 24 小时过期
     */
    private static final Duration TTL = Duration.ofHours(24);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void appendMessage(String sessionId, String role, String content) {

        if (sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }

        if (content == null || content.trim().isEmpty()) {
            return;
        }

        String key = buildKey(sessionId);

        String value = role + "：" + content + "｜" + LocalDateTime.now();

        // 1. 从右侧追加消息，保证时间顺序
        stringRedisTemplate.opsForList().rightPush(key, value);

        // 2. 只保留最近 10 条
        stringRedisTemplate.opsForList().trim(key, -MAX_MESSAGE_COUNT, -1);

        // 3. 设置过期时间
        stringRedisTemplate.expire(key, TTL);
    }

    @Override
    public List<String> getRecentMessages(String sessionId) {

        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String key = buildKey(sessionId);

        List<String> messages = stringRedisTemplate.opsForList().range(key, 0, -1);

        if (messages == null) {
            return Collections.emptyList();
        }

        return messages;
    }

    private String buildKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
```

#### AgentServiceImpl

 修改 AgentServiceImpl，添加redis缓存

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.enums.IntentType;
import com.test.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentPlanService agentPlanService;

    @Autowired
    private AgentIntentService agentIntentService;

    @Autowired
    private AgentRecordHandler agentRecordHandler;

    @Autowired
    private AgentStatisticsHandler agentStatisticsHandler;

    @Autowired
    private AgentBudgetHandler agentBudgetHandler;

    @Autowired
    private AgentBudgetRiskHandler agentBudgetRiskHandler;

    @Autowired
    private AgentChatReplyService agentChatReplyService;

    @Autowired
    private AgentChatMessageService agentChatMessageService;

    @Autowired
    private AgentActionLogService agentActionLogService;

    @Autowired
    private AgentMemoryCacheService agentMemoryCacheService;

    @Override
    public AgentChatResponse chat(Long userId, String sessionId, String message){

        List<AgentAction> actions = new ArrayList<>();

        // 1. 保存用户消息到 MySQL
        agentChatMessageService.saveMessage(
                userId,
                sessionId,
                "USER",
                message,
                null,
                null
        );

        // 2. 保存用户消息到 Redis
        agentMemoryCacheService.appendMessage(
                sessionId,
                "USER",
                message
        );

        // 3. AI 解析计划
        AgentPlan plan = agentPlanService.parsePlan(sessionId, message);

        // 4. 后端最终意图裁决
        IntentType finalIntent = agentIntentService.decideIntent(message, plan);

        AgentChatResponse response;

        switch (finalIntent) {
            case RECORD_EXPENSE:
                response = agentRecordHandler.handle(userId, sessionId, message, plan, actions);
                break;

            case QUERY_EXPENSE:
                response = agentStatisticsHandler.handleQuery(userId, message, actions);
                break;

            case ANALYZE_EXPENSE:
                response = agentStatisticsHandler.handleAnalyze(userId, message, actions);
                break;

            case SET_BUDGET:
                response = agentBudgetHandler.handleSetBudget(userId, message, plan, actions);
                break;

            case BUDGET_RISK:
                response = agentBudgetRiskHandler.handle(userId, message, actions);
                break;

            case CHAT:
            default:
                response = agentChatReplyService.chat(sessionId, message, actions);
                break;
        }

        // 5. 保存助手回复到 MySQL
        agentChatMessageService.saveMessage(
                userId,
                sessionId,
                "ASSISTANT",
                response.getReply(),
                plan == null ? null : plan.getIntent(),
                finalIntent == null ? null : finalIntent.name()
        );

        // 6. 保存助手回复到 Redis
        agentMemoryCacheService.appendMessage(
                sessionId,
                "ASSISTANT",
                response.getReply()
        );

        // 7. 保存 actions 到 MySQL
        agentActionLogService.saveActions(
                userId,
                sessionId,
                message,
                response.getActions()
        );

        return response;
    }

}
```



>测试
>
>```
>{
>  "userId": 1,
>  "sessionId": "session_test_backend_001",
>  "message": "今天奶茶18"
>}
>```
>
>```
>{
>  "code": 200,
>  "msg": "success",
>  "data": {
>    "reply": "好嘞，已帮你记下一笔支出：饮品18元，奶茶。",
>    "actions": [
>      {
>        "name": "recordExpense",
>        "success": true,
>        "message": "支出记录已保存，记录ID：6"
>      }
>    ]
>  }
>}
>```
>
>```
>USER：今天奶茶18｜2026-06-01T11: 25: 59.719210700
>ASSISTANT：好嘞，已帮你记下一笔支出：饮品18元，奶茶。｜2026-06-01T11: 26: 02.418277100
>```



### 5.9.1短信验证码基础模块

#### SmsSendVO

创建发送验证码的实体类

```java
package com.test.dto;

import lombok.Data;

@Data
public class SmsSendVO {

    private String mobile;

    /**
     * 场景：LOGIN、REGISTER、RESET_PASSWORD
     */
    private String scene;
}
```

#### SmsVerifyVO

创建校验验证码的实体类

```java
package com.test.dto;

import lombok.Data;

@Data
public class SmsVerifyVO {

    private String mobile;

    private String scene;

    private String code;
}
```

#### SmsService

实现短信业务

```java
package com.test.service;

public interface SmsService {

    void sendCode(String mobile, String scene);

    boolean verifyCode(String mobile, String scene, String code);
}
```

#### SmsServiceImpl

```java
package com.test.service.impl;

import com.test.service.SmsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Service
public class SmsServiceImpl implements SmsService {

    private static final String CODE_KEY_PREFIX = "sms:code:";

    private static final String COOLDOWN_KEY_PREFIX = "sms:cooldown:";

    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void sendCode(String mobile, String scene) {

        if (mobile == null || mobile.trim().isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }

        if (scene == null || scene.trim().isEmpty()) {
            scene = "LOGIN";
        }

        String cooldownKey = buildCooldownKey(scene, mobile);

        Boolean hasCooldown = stringRedisTemplate.hasKey(cooldownKey);
        if (Boolean.TRUE.equals(hasCooldown)) {
            throw new RuntimeException("验证码发送过于频繁，请稍后再试");
        }

        String code = generateCode();

        String codeKey = buildCodeKey(scene, mobile);

        stringRedisTemplate.opsForValue().set(codeKey, code, CODE_TTL);
        stringRedisTemplate.opsForValue().set(cooldownKey, "1", COOLDOWN_TTL);

        // 第一版先模拟发送，后面再接阿里云短信
        System.out.println("短信验证码发送成功，mobile=" + mobile + "，scene=" + scene + "，code=" + code);
    }

    @Override
    public boolean verifyCode(String mobile, String scene, String code) {

        if (mobile == null || mobile.trim().isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }

        if (code == null || code.trim().isEmpty()) {
            throw new RuntimeException("验证码不能为空");
        }

        if (scene == null || scene.trim().isEmpty()) {
            scene = "LOGIN";
        }

        String codeKey = buildCodeKey(scene, mobile);

        String redisCode = stringRedisTemplate.opsForValue().get(codeKey);

        if (redisCode == null) {
            return false;
        }

        boolean success = redisCode.equals(code);

        if (success) {
            stringRedisTemplate.delete(codeKey);
        }

        return success;
    }

    private String buildCodeKey(String scene, String mobile) {
        return CODE_KEY_PREFIX + scene + ":" + mobile;
    }

    private String buildCooldownKey(String scene, String mobile) {
        return COOLDOWN_KEY_PREFIX + scene + ":" + mobile;
    }

    private String generateCode() {
        int code = new Random().nextInt(900000) + 100000;
        return String.valueOf(code);
    }
}
```

#### SmsController

controller层调用短信业务

```java
package com.test.controller;

import com.test.dto.SmsSendVO;
import com.test.dto.SmsVerifyVO;
import com.test.service.SmsService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sms")
public class SmsController {

    @Autowired
    private SmsService smsService;

    @PostMapping("/send")
    public ResultVO sendCode(@RequestBody SmsSendVO request) {

        smsService.sendCode(request.getMobile(), request.getScene());

        return ResultVOUtil.success("验证码已发送");
    }

    @PostMapping("/verify")
    public ResultVO verifyCode(@RequestBody SmsVerifyVO request) {

        boolean success = smsService.verifyCode(
                request.getMobile(),
                request.getScene(),
                request.getCode()
        );

        if (success) {
            return ResultVOUtil.success("验证码校验成功");
        }

        return ResultVOUtil.fail("验证码错误或已过期");
    }
}
```

> 测试
>
> ```
> {
>   "mobile": "13300000000",
>   "scene": "LOGIN"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": "验证码已发送"
> }
> ```
>
> ```
> 短信验证码发送成功，mobile=13300000000，scene=LOGIN，code=742793
> ```
>
> ```
> {
>   "mobile": "13300000000",
>   "scene": "LOGIN",
>   "code": "742793"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": "验证码校验成功"
> }
> ```
>
> 

### 5.9.2短信日志

#### SmsSendLog

发生验证码的SmsSendLog实体类

```java
package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sms_send_log")
public class SmsSendLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String mobile;

    private String scene;

    private String templateCode;

    private String content;

    private String codeHash;

    private String provider;

    private String providerRequestId;

    /**
     * 1成功，0失败
     */
    private Integer sendStatus;

    private String failReason;

    private String ipAddress;

    private LocalDateTime expireTime;

    /**
     * 1已使用，0未使用
     */
    private Integer used;

    private LocalDateTime createTime;
}
```

#### SmsVerifyLog 

校验验证码的SmsVerifyLog 实体类

```java
package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sms_verify_log")
public class SmsVerifyLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String mobile;

    private String scene;

    /**
     * 1成功，0失败
     */
    private Integer verifyStatus;

    private String failReason;

    private String ipAddress;

    private LocalDateTime createTime;
}
```

mapper数据库层操作

#### SmsSendLogMapper

```java
package com.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.test.entity.SmsSendLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SmsSendLogMapper extends BaseMapper<SmsSendLog> {
}
```



#### SmsVerifyLogMapper

```java
package com.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.test.entity.SmsVerifyLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SmsVerifyLogMapper extends BaseMapper<SmsVerifyLog> {
}
```



#### SmsServiceImpl

```java
package com.test.service.impl;

import com.test.entity.SmsSendLog;
import com.test.entity.SmsVerifyLog;
import com.test.mapper.SmsSendLogMapper;
import com.test.mapper.SmsVerifyLogMapper;
import com.test.service.SmsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;

@Service
public class SmsServiceImpl implements SmsService {


    private static final String CODE_KEY_PREFIX = "sms:code:";

    private static final String COOLDOWN_KEY_PREFIX = "sms:cooldown:";

    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SmsSendLogMapper smsSendLogMapper;

    @Autowired
    private SmsVerifyLogMapper smsVerifyLogMapper;

    @Override
    public void sendCode(String mobile, String scene) {

        if (mobile == null || mobile.trim().isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }

        if (scene == null || scene.trim().isEmpty()) {
            scene = "LOGIN";
        }

        String cooldownKey = buildCooldownKey(scene, mobile);

        Boolean hasCooldown = stringRedisTemplate.hasKey(cooldownKey);
        if (Boolean.TRUE.equals(hasCooldown)) {
            throw new RuntimeException("验证码发送过于频繁，请稍后再试");
        }

        String code = generateCode();

        String codeKey = buildCodeKey(scene, mobile);

        stringRedisTemplate.opsForValue().set(codeKey, code, CODE_TTL);
        stringRedisTemplate.opsForValue().set(cooldownKey, "1", COOLDOWN_TTL);

        SmsSendLog sendLog = new SmsSendLog();

        sendLog.setMobile(mobile);
        sendLog.setScene(scene);
        sendLog.setTemplateCode("MOCK_LOGIN_CODE");
        sendLog.setContent("模拟短信验证码");
        sendLog.setCodeHash(code);
        sendLog.setProvider("MOCK");
        sendLog.setProviderRequestId(null);
        sendLog.setSendStatus(1);
        sendLog.setFailReason(null);
        sendLog.setExpireTime(LocalDateTime.now().plusMinutes(5));
        sendLog.setUsed(0);

        smsSendLogMapper.insert(sendLog);

        // 第一版先模拟发送，后面再接阿里云短信
        System.out.println("短信验证码发送成功，mobile=" + mobile + "，scene=" + scene + "，code=" + code);
    }

    @Override
    public boolean verifyCode(String mobile, String scene, String code) {

        if (mobile == null || mobile.trim().isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }

        if (code == null || code.trim().isEmpty()) {
            throw new RuntimeException("验证码不能为空");
        }

        if (scene == null || scene.trim().isEmpty()) {
            scene = "LOGIN";
        }

        String codeKey = buildCodeKey(scene, mobile);

        String redisCode = stringRedisTemplate.opsForValue().get(codeKey);

        if (redisCode == null) {
            return false;
        }

        boolean success = redisCode.equals(code);

        SmsVerifyLog verifyLog = new SmsVerifyLog();

        verifyLog.setMobile(mobile);
        verifyLog.setScene(scene);

        if (success) {
            verifyLog.setVerifyStatus(1);
            verifyLog.setFailReason(null);
        } else {
            verifyLog.setVerifyStatus(0);
            verifyLog.setFailReason(redisCode == null ? "验证码已过期或不存在" : "验证码错误");
        }

        smsVerifyLogMapper.insert(verifyLog);

        if (success) {
            stringRedisTemplate.delete(codeKey);
        }




        return success;
    }

    private String buildCodeKey(String scene, String mobile) {
        return CODE_KEY_PREFIX + scene + ":" + mobile;
    }

    private String buildCooldownKey(String scene, String mobile) {
        return COOLDOWN_KEY_PREFIX + scene + ":" + mobile;
    }

    private String generateCode() {
        int code = new Random().nextInt(900000) + 100000;
        return String.valueOf(code);
    }
}
```



> 测试
>
> ```
> {
>   "mobile": "13300000000",
>   "scene": "LOGIN"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": "验证码已发送"
> }
> ```
>
> ```
> 短信验证码发送成功，mobile=13300000000，scene=LOGIN，code=102618
> ```
>
> ```
> {
>   "mobile": "13300000000",
>   "scene": "LOGIN",
>   "code": "102618"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": "验证码校验成功"
> }
> ```
>
> ```
> 1,13300000000,LOGIN,1,,,2026-06-01 11:51:07
> ```



### 5.10.1手机号验证码登录 / 自动注册 + Token 生成

对应提类的生成

#### LoginBySmsVO

```java
package com.test.dto;

import lombok.Data;

@Data
public class LoginBySmsVO {

    private String mobile;

    private String code;

    /**
     * 默认 LOGIN
     */
    private String scene;
}
```

#### LoginResponse

新建 LoginResponse

```java
package com.test.dto;

import lombok.Data;

@Data
public class LoginResponse {

    private Long userId;

    private String mobile;

    private String token;
}
```

#### AppUser 

创建 AppUser 实体类

```java
package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("app_user")
public class AppUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String nickname;

    private String mobile;

    private Integer mobileVerified;

    private String email;

    private String password;

    private String avatarUrl;

    private Integer gender;

    private Integer status;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
```



#### AppUserMapper

```java
package com.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.test.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {
}
```



#### AppUserService

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.AppUser;

public interface AppUserService extends IService<AppUser> {

    AppUser getOrCreateByMobile(String mobile);
}
```



#### AppUserServiceImpl

```java
package com.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.entity.AppUser;
import com.test.mapper.AppUserMapper;
import com.test.service.AppUserService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AppUserServiceImpl
        extends ServiceImpl<AppUserMapper, AppUser>
        implements AppUserService {

    @Override
    public AppUser getOrCreateByMobile(String mobile) {

        if (mobile == null || mobile.trim().isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }

        QueryWrapper<AppUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("mobile", mobile)
                .eq("deleted", 0);

        AppUser user = this.getOne(queryWrapper);

        if (user != null) {
            user.setMobileVerified(1);
            user.setLastLoginTime(LocalDateTime.now());
            this.updateById(user);
            return user;
        }

        user = new AppUser();
        user.setUsername("user_" + mobile);
        user.setNickname("用户" + mobile.substring(mobile.length() - 4));
        user.setMobile(mobile);
        user.setMobileVerified(1);
        user.setStatus(1);
        user.setGender(0);
        user.setLastLoginTime(LocalDateTime.now());
        user.setDeleted(0);

        this.save(user);

        return user;
    }
}
```



#### AuthService

```java
package com.test.service;

import com.test.dto.LoginResponse;

public interface AuthService {

    LoginResponse loginBySms(String mobile, String scene, String code);
}
```

#### AuthServiceImpl

```java
package com.test.service.impl;

import com.test.dto.LoginResponse;
import com.test.entity.AppUser;
import com.test.service.AppUserService;
import com.test.service.AuthService;
import com.test.service.SmsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_KEY_PREFIX = "login:token:";

    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    @Autowired
    private SmsService smsService;

    @Autowired
    private AppUserService appUserService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public LoginResponse loginBySms(String mobile, String scene, String code) {

        if (scene == null || scene.trim().isEmpty()) {
            scene = "LOGIN";
        }

        boolean verified = smsService.verifyCode(mobile, scene, code);

        if (!verified) {
            throw new RuntimeException("验证码错误或已过期");
        }

        AppUser user = appUserService.getOrCreateByMobile(mobile);

        String token = UUID.randomUUID().toString().replace("-", "");

        String tokenKey = TOKEN_KEY_PREFIX + token;

        stringRedisTemplate.opsForValue().set(
                tokenKey,
                String.valueOf(user.getId()),
                TOKEN_TTL
        );

        LoginResponse response = new LoginResponse();
        response.setUserId(user.getId());
        response.setMobile(user.getMobile());
        response.setToken(token);

        return response;
    }
}
```



#### AuthController

```java
package com.test.controller;

import com.test.dto.LoginBySmsVO;
import com.test.dto.LoginResponse;
import com.test.service.AuthService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login/sms")
    public ResultVO<LoginResponse> loginBySms(@RequestBody LoginBySmsVO request) {

        LoginResponse response = authService.loginBySms(
                request.getMobile(),
                request.getScene(),
                request.getCode()
        );

        return ResultVOUtil.success(response);
    }
}
```



> 测试
>
> sendCode
>
> ```
> {
>   "mobile": "13300000000",
>   "scene": "LOGIN"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": "验证码已发送"
> }
> ```
>
> ```
> 短信验证码发送成功，mobile=13300000000，scene=LOGIN，code=995499
> ```
>
> loginBySms
>
> ```
> {
>   "mobile": "13300000000",
>   "scene": "LOGIN",
>   "code": "995499"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "userId": 1,
>     "mobile": "13300000000",
>     "token": "44c3069d08974abeb6d8f6ddd47acada"
>   }
> }
> ```
>
> 





### 5.10.2Token 拦截器

后面调用 `/agent/chat` 时，不再让前端手动传 `userId`，而是从请求头里的 token 解析出当前用户 ID

解决明文传递的问题



#### UserContext

创建拦截器解析

```java
package com.test.common;

public class UserContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}
```



#### TokenInterceptor

登录校验拦截器

```java
package com.test.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class TokenInterceptor implements HandlerInterceptor {

    private static final String TOKEN_KEY_PREFIX = "login:token:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {

        // 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");

        if (token == null || token.trim().isEmpty()) {
            writeUnauthorized(response, "未登录，请先登录");
            return false;
        }

        // 兼容 Authorization: Bearer xxxxx
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        String userIdStr = stringRedisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);

        if (userIdStr == null || userIdStr.trim().isEmpty()) {
            writeUnauthorized(response, "登录已过期，请重新登录");
            return false;
        }

        Long userId = Long.valueOf(userIdStr);

        UserContext.setUserId(userId);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        UserContext.clear();
    }

    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(401);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        String json = "{\"code\":401,\"msg\":\"" + msg + "\",\"data\":null}";
        response.getWriter().write(json);
    }
}
```

#### WebMvcConfig

注册拦截器 WebMvcConfig

```java
package com.test.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private TokenInterceptor tokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/sms/**",
                        "/auth/**",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/favicon.ico",
                        "/error"
                );
    }
}
```

#### AgentChatVO

修改 AgentChatVO

```java
package com.test.dto;

import lombok.Data;

@Data
public class AgentChatVO {
//    使用token了，所以不能将id传递
//    private  Long userId;
    private String sessionId;
    private String message;
}

```

#### BulterAgentController

修改 BulterAgentController

```java
package com.test.controller;

import com.test.common.UserContext;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentChatVO;
import com.test.service.AgentService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/agent")
public class BulterAgentController {

    @Autowired
    private AgentService agentService;

    @PostMapping("/chat")
    public ResultVO chat(@RequestBody AgentChatVO chatmessage) {

        Long userId = UserContext.getUserId();

        AgentChatResponse chat = this.agentService.chat(
                userId,
                chatmessage.getSessionId(),
                chatmessage.getMessage()
        );

        return ResultVOUtil.success(chat);
    }
}
```



#### Knife4jConfig

因为要请求头携带token，所以测试中在header中添加参数

```java
package com.test.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI butlerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Butler AI 个人记账助手接口文档")
                        .description("用于测试个人记账、预算管理、AI消费分析等接口")
                        .version("1.0.0")
                        .contact(new Contact().name("king")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8181")
                                .description("本地开发环境")
                ));
    }

    @Bean
    public GroupedOpenApi butlerApi() {
        return GroupedOpenApi.builder()
                .group("Butler接口")
                .packagesToScan("com.test")
                .pathsToMatch("/**")
                .addOperationCustomizer(globalHeader())
                .build();
    }

    @Bean
    public OperationCustomizer globalHeader() {
        return (operation, handlerMethod) -> {
            operation.addParametersItem(new Parameter()
                    .name("Authorization")
                    .in("header")
                    .required(false)
                    .description("登录Token，格式：Bearer token")
                    .schema(new StringSchema()));
            return operation;
        };
    }
}
```



> 测试
>
> ```
> {
>   "mobile": "13300000000",
>   "scene": "LOGIN"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": "验证码已发送"
> }
> ```
>
> ```
> 短信验证码发送成功，mobile=13300000000，scene=LOGIN，code=327568
> ```
>
> ```
> {
>   "mobile": "13300000000",
>   "scene": "LOGIN",
>   "code": "327568"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "userId": 1,
>     "mobile": "13300000000",
>     "token": "8d5610bf47074f108dbf701b32342ccc"
>   }
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "reply": "好嘞，已帮你记下一笔支出：饮品18元，奶茶。",
>     "actions": [
>       {
>         "name": "recordExpense",
>         "success": true,
>         "message": "支出记录已保存，记录ID：7"
>       }
>     ]
>   }
> }
> ```

### 5.11.1退出登录 / 注销 token

#### AuthService

添加退出登录的功能接口

```
package com.test.service;

import com.test.dto.LoginResponse;

public interface AuthService {

    LoginResponse loginBySms(String mobile, String scene, String code);

    void logout(String authorization);
}
```

####  AuthServiceImpl

在 AuthServiceImpl里加方法

```java
@Override
public void logout(String authorization) {

    if (authorization == null || authorization.trim().isEmpty()) {
        throw new RuntimeException("Token不能为空");
    }

    String token = authorization.trim();

    if (token.startsWith("Bearer ")) {
        token = token.substring(7);
    }

    if (token.trim().isEmpty()) {
        throw new RuntimeException("Token不能为空");
    }

    String tokenKey = TOKEN_KEY_PREFIX + token;

    stringRedisTemplate.delete(tokenKey);
}
```

完整逻辑就是：

```
Authorization: Bearer abcxxx
↓
截掉 Bearer
↓
得到 abcxxx
↓
删除 login:token:abcxxx
```

#### AuthController

加一个退出登录接口：

```
@PostMapping("/logout")
public ResultVO logout(@RequestHeader(value = "Authorization", required = false) String authorization) {

    authService.logout(authorization);

    return ResultVOUtil.success("退出登录成功");
}
```



> 测试
>
> ```
> {
>   "mobile": "13300000000",
>   "scene": "LOGIN"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": "验证码已发送"
> }
> ```
>
> ```
> 短信验证码发送成功，mobile=13300000000，scene=LOGIN，code=444867
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "userId": 1,
>     "mobile": "13300000000",
>     "token": "806df39852ed4859a70f95a1aeca4167"
>   }
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": "退出登录成功"
> }
> ```
>
> ```
> {
>   "sessionId": "session_test_backend_001",
>   "message": "今天奶茶18"
> }
> ```
>
> ```
> {
>   "code": 401,
>   "msg": "未登录，请先登录",
>   "data": null
> }
> ```

### 5.11.2异常处理

#### GlobalExceptionHandler

```java
package com.test.common;

import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理普通运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResultVO handleRuntimeException(RuntimeException e) {
        return ResultVOUtil.fail(e.getMessage());
    }

    /**
     * 处理参数校验异常，后面如果你加 @Valid 会用到
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResultVO handleValidException(MethodArgumentNotValidException e) {
        String msg = "参数校验失败";

        if (e.getBindingResult().getFieldError() != null) {
            msg = e.getBindingResult().getFieldError().getDefaultMessage();
        }

        return ResultVOUtil.fail(msg);
    }

    /**
     * 兜底异常
     */
    @ExceptionHandler(Exception.class)
    public ResultVO handleException(Exception e) {
        e.printStackTrace();
        return ResultVOUtil.fail("系统异常，请稍后再试");
    }
}
```



### 5.12.1登录日志

#### UserLoginLog 

创建日志实体类

```java
package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_login_log")
public class UserLoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String mobile;

    /**
     * PASSWORD、SMS_CODE、TOKEN
     */
    private String loginType;

    /**
     * 1成功，0失败
     */
    private Integer loginStatus;

    private String failReason;

    private String ipAddress;

    private String userAgent;

    private String deviceId;

    private LocalDateTime loginTime;
}
```



#### UserLoginLogMapper

创建对应的mapper

```java
package com.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.test.entity.UserLoginLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserLoginLogMapper extends BaseMapper<UserLoginLog> {
}
```







#### UserLoginLogService

创建对应的业务service层

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.UserLoginLog;

public interface UserLoginLogService extends IService<UserLoginLog> {

    void saveLoginLog(Long userId,
                      String mobile,
                      String loginType,
                      Integer loginStatus,
                      String failReason);
}
```



#### UserLoginLogServiceImpl

创建对应的方法实习类

```java
package com.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.entity.UserLoginLog;
import com.test.mapper.UserLoginLogMapper;
import com.test.service.UserLoginLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserLoginLogServiceImpl
        extends ServiceImpl<UserLoginLogMapper, UserLoginLog>
        implements UserLoginLogService {

    @Override
    public void saveLoginLog(Long userId,
                             String mobile,
                             String loginType,
                             Integer loginStatus,
                             String failReason) {

        UserLoginLog log = new UserLoginLog();

        log.setUserId(userId);
        log.setMobile(mobile);
        log.setLoginType(loginType);
        log.setLoginStatus(loginStatus);
        log.setFailReason(failReason);
        log.setLoginTime(LocalDateTime.now());

        this.save(log);
    }
}
```



#### AuthServiceImpl

调整authservicelmpl

```java
@Override
public LoginResponse loginBySms(String mobile, String scene, String code) {

    if (scene == null || scene.trim().isEmpty()) {
        scene = "LOGIN";
    }

    boolean verified = smsService.verifyCode(mobile, scene, code);

    if (!verified) {
        userLoginLogService.saveLoginLog(
                null,
                mobile,
                "SMS_CODE",
                0,
                "验证码错误或已过期"
        );

        throw new RuntimeException("验证码错误或已过期");
    }

    AppUser user = appUserService.getOrCreateByMobile(mobile);

    String token = UUID.randomUUID().toString().replace("-", "");

    String tokenKey = TOKEN_KEY_PREFIX + token;

    stringRedisTemplate.opsForValue().set(
            tokenKey,
            String.valueOf(user.getId()),
            TOKEN_TTL
    );

    userLoginLogService.saveLoginLog(
            user.getId(),
            mobile,
            "SMS_CODE",
            1,
            null
    );

    LoginResponse response = new LoginResponse();
    response.setUserId(user.getId());
    response.setMobile(user.getMobile());
    response.setToken(token);

    return response;
}
```



### 5.13.1user_token 表持久化



#### userToken

```java
package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_token")
public class UserToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 访问 Token 摘要，当前阶段可先直接存 token
     */
    private String tokenHash;

    private String refreshTokenHash;

    private String tokenType;

    private String deviceId;

    private String deviceName;

    private String ipAddress;

    private LocalDateTime expireTime;

    private LocalDateTime refreshExpireTime;

    /**
     * 1有效，0失效
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
```

#### UserTokenMapper

```java
package com.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.test.entity.UserToken;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserTokenMapper extends BaseMapper<UserToken> {
}
```

#### UserTokenService

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.UserToken;

import java.time.LocalDateTime;

public interface UserTokenService extends IService<UserToken> {

    void saveToken(Long userId,
                   String token,
                   LocalDateTime expireTime);

    void invalidToken(String token);
}
```



#### UserTokenServiceImpl

```java
package com.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.entity.UserToken;
import com.test.mapper.UserTokenMapper;
import com.test.service.UserTokenService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserTokenServiceImpl
        extends ServiceImpl<UserTokenMapper, UserToken>
        implements UserTokenService {

    @Override
    public void saveToken(Long userId,
                          String token,
                          LocalDateTime expireTime) {

        if (userId == null || token == null || token.trim().isEmpty()) {
            return;
        }

        UserToken userToken = new UserToken();

        userToken.setUserId(userId);
        userToken.setTokenHash(token);
        userToken.setTokenType("UUID");
        userToken.setExpireTime(expireTime);
        userToken.setStatus(1);
        userToken.setDeleted(0);

        this.save(userToken);
    }

    @Override
    public void invalidToken(String token) {

        if (token == null || token.trim().isEmpty()) {
            return;
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        QueryWrapper<UserToken> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("token_hash", token)
                .eq("deleted", 0)
                .eq("status", 1);

        UserToken userToken = this.getOne(queryWrapper);

        if (userToken != null) {
            userToken.setStatus(0);
            this.updateById(userToken);
        }
    }
}
```

#### AuthServiceImpl

修改 AuthServiceImpl 登录逻辑

```
package com.test.service.impl;

import com.test.dto.LoginResponse;
import com.test.entity.AppUser;
import com.test.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_KEY_PREFIX = "login:token:";

    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    @Autowired
    private SmsService smsService;

    @Autowired
    private AppUserService appUserService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserLoginLogService userLoginLogService;

    @Autowired
    private UserTokenService userTokenService;

    @Override
    public LoginResponse loginBySms(String mobile, String scene, String code) {

        if (scene == null || scene.trim().isEmpty()) {
            scene = "LOGIN";
        }

        boolean verified = smsService.verifyCode(mobile, scene, code);

        if (!verified) {
            userLoginLogService.saveLoginLog(
                    null,
                    mobile,
                    "SMS_CODE",
                    0,
                    "验证码错误或已过期"
            );

            throw new RuntimeException("验证码错误或已过期");
        }

        AppUser user = appUserService.getOrCreateByMobile(mobile);

        String token = UUID.randomUUID().toString().replace("-", "");

        String tokenKey = TOKEN_KEY_PREFIX + token;

        stringRedisTemplate.opsForValue().set(
                tokenKey,
                String.valueOf(user.getId()),
                TOKEN_TTL
        );
        userTokenService.saveToken(
                user.getId(),
                token,
                LocalDateTime.now().plusDays(7)
        );

        userLoginLogService.saveLoginLog(
                user.getId(),
                mobile,
                "SMS_CODE",
                1,
                null
        );

        LoginResponse response = new LoginResponse();
        response.setUserId(user.getId());
        response.setMobile(user.getMobile());
        response.setToken(token);

        return response;
    }
    @Override
    public void logout(String authorization) {

        if (authorization == null || authorization.trim().isEmpty()) {
            throw new RuntimeException("Token不能为空");
        }

        String token = authorization.trim();

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (token.trim().isEmpty()) {
            throw new RuntimeException("Token不能为空");
        }

        String tokenKey = TOKEN_KEY_PREFIX + token;

        stringRedisTemplate.delete(tokenKey);
        
        userTokenService.invalidToken(token);
    }

}
```



> 测试
>
> ```
> {
>   "mobile": "13300000000",
>   "scene": "LOGIN"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": "验证码已发送"
> }
> ```
>
> ```
> 短信验证码发送成功，mobile=13300000000，scene=LOGIN，code=661330
> 
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "userId": 1,
>     "mobile": "13300000000",
>     "token": "a91a5c6f830945f9a5bb9a134bbdbb8c"
>   }
> }
> ```
>
> ```
> a91a5c6f830945f9a5bb9a134bbdbb8c
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": "退出登录成功"
> }
> ```



### 5.14.1获取当前登录用户信息

#### WebMvcConfig

修改拦截器的放行规则

```java
package com.test.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private TokenInterceptor tokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/sms/**",
                        "/auth/login/sms",
                        "/auth/logout",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/favicon.ico",
                        "/error"
                );
    }
}
```

#### UserInfoResponse

创建实体类

```java
package com.test.dto;

import lombok.Data;

@Data
public class UserInfoResponse {

    private Long userId;

    private String username;

    private String nickname;

    private String mobile;

    private Integer mobileVerified;
}
```

#### AppUserService 

增加查看当前信息的方法到service中

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.UserInfoResponse;
import com.test.entity.AppUser;

public interface AppUserService extends IService<AppUser> {

    AppUser getOrCreateByMobile(String mobile);

    UserInfoResponse getCurrentUserInfo(Long userId);
}
```



#### AppUserServiceImpl 

实现方法

```java
@Override
public UserInfoResponse getCurrentUserInfo(Long userId) {

    if (userId == null) {
        throw new RuntimeException("用户未登录");
    }

    AppUser user = this.getById(userId);

    if (user == null || user.getDeleted() != null && user.getDeleted() == 1) {
        throw new RuntimeException("用户不存在");
    }

    UserInfoResponse response = new UserInfoResponse();

    response.setUserId(user.getId());
    response.setUsername(user.getUsername());
    response.setNickname(user.getNickname());
    response.setMobile(user.getMobile());
    response.setMobileVerified(user.getMobileVerified());

    return response;
}
```



#### AuthController 

```java
package com.test.controller;

import com.test.common.UserContext;
import com.test.dto.LoginBySmsVO;
import com.test.dto.LoginResponse;
import com.test.dto.UserInfoResponse;
import com.test.service.AuthService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login/sms")
    public ResultVO<LoginResponse> loginBySms(@RequestBody LoginBySmsVO request) {

        LoginResponse response = authService.loginBySms(
                request.getMobile(),
                request.getScene(),
                request.getCode()
        );

        return ResultVOUtil.success(response);
    }
    @PostMapping("/logout")
    public ResultVO logout(@RequestHeader(value = "Authorization", required = false) String authorization) {

        authService.logout(authorization);

        return ResultVOUtil.success("退出登录成功");
    }
    @GetMapping("/me")
    public ResultVO<UserInfoResponse> me() {

        Long userId = UserContext.getUserId();

        UserInfoResponse response = appUserService.getCurrentUserInfo(userId);

        return ResultVOUtil.success(response);
    }
}
```



> 测试
>
> ```
> 44c3069d08974abeb6d8f6ddd47acada
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "userId": 1,
>     "username": "test_user",
>     "nickname": "测试用户",
>     "mobile": "13300000000",
>     "mobileVerified": 1
>   }
> }
> ```



### 5.15.1查询历史对话记录

#### AgentChatMessageDTO

```java
package com.test.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentChatMessageDTO {

    private Long id;

    private String role;

    private String content;

    private String intent;

    private String finalIntent;

    private LocalDateTime createTime;
}
```



#### AgentChatMessageService 

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.AgentChatMessageDTO;
import com.test.entity.AgentChatMessage;

import java.util.List;

public interface AgentChatMessageService extends IService<AgentChatMessage> {

    void saveMessage(Long userId,
                     String sessionId,
                     String role,
                     String content,
                     String intent,
                     String finalIntent);

    List<AgentChatMessageDTO> listBySession(Long userId, String sessionId);
}
```



#### AgentChatMessageServiceImpl 

```java
@Override
public List<AgentChatMessageDTO> listBySession(Long userId, String sessionId) {

    if (userId == null) {
        throw new RuntimeException("用户未登录");
    }

    if (sessionId == null || sessionId.trim().isEmpty()) {
        throw new RuntimeException("会话ID不能为空");
    }

    QueryWrapper<AgentChatMessage> queryWrapper = new QueryWrapper<>();

    queryWrapper.eq("user_id", userId)
            .eq("session_id", sessionId)
            .eq("deleted", 0)
            .orderByAsc("create_time");

    List<AgentChatMessage> messageList = this.list(queryWrapper);

    List<AgentChatMessageDTO> result = new ArrayList<>();

    for (AgentChatMessage message : messageList) {
        AgentChatMessageDTO dto = new AgentChatMessageDTO();

        dto.setId(message.getId());
        dto.setRole(message.getRole());
        dto.setContent(message.getContent());
        dto.setIntent(message.getIntent());
        dto.setFinalIntent(message.getFinalIntent());
        dto.setCreateTime(message.getCreateTime());

        result.add(dto);
    }

    return result;
}
```



#### BulterAgentController 

```java
package com.test.controller;

import com.test.common.UserContext;
import com.test.dto.AgentChatMessageDTO;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentChatVO;
import com.test.service.AgentChatMessageService;
import com.test.service.AgentService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/agent")
public class BulterAgentController {

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentChatMessageService agentChatMessageService;

    @PostMapping("/chat")
    public ResultVO chat(@RequestBody AgentChatVO chatmessage) {
//        使用token拦截器获取userid
        Long userId = UserContext.getUserId();

        AgentChatResponse chat = this.agentService.chat(
                userId,
                chatmessage.getSessionId(),
                chatmessage.getMessage()
        );

        return ResultVOUtil.success(chat);
    }
    @GetMapping("/history/{sessionId}")
    public ResultVO<List<AgentChatMessageDTO>> history(@PathVariable String sessionId) {

        Long userId = UserContext.getUserId();

        List<AgentChatMessageDTO> list = agentChatMessageService.listBySession(userId, sessionId);

        return ResultVOUtil.success(list);
    }
}
```

> 测试
>
> ```
> Authorization 8d5610bf47074f108dbf701b32342ccc
> sessionId session_test_backend_001
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": [
>     {
>       "id": 1,
>       "role": "USER",
>       "content": "今天奶茶18",
>       "intent": null,
>       "finalIntent": null,
>       "createTime": "2026-06-01T10:55:56"
>     },
>     {
>       "id": 2,
>       "role": "ASSISTANT",
>       "content": "好嘞，已帮你记下一笔支出：饮品18元，奶茶。",
>       "intent": "RECORD_EXPENSE",
>       "finalIntent": "RECORD_EXPENSE",
>       "createTime": "2026-06-01T10:55:59"
>     }
>   ]
> }
> ```

### 5.16.1会话列表功能

#### AgentChatSession

创建AgentChatSession实体类

```java
package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_chat_session")
public class AgentChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionId;

    private String title;

    private String lastMessage;

    private LocalDateTime lastActiveTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
```



#### AgentChatSessionMapper

链接数据库

```java 
package com.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.test.entity.AgentChatSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentChatSessionMapper extends BaseMapper<AgentChatSession> {
}
```

------

#### AgentChatSessionDTO

创建返回 DTO

```
package com.test.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentChatSessionDTO {

    private Long id;

    private String sessionId;

    private String title;

    private String lastMessage;

    private LocalDateTime lastActiveTime;
}
```

------

#### AgentChatSessionService

创建方法接口

```
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.AgentChatSessionDTO;
import com.test.entity.AgentChatSession;

import java.util.List;

public interface AgentChatSessionService extends IService<AgentChatSession> {

    void createOrUpdateSession(Long userId,
                               String sessionId,
                               String userMessage,
                               String assistantReply);

    List<AgentChatSessionDTO> listUserSessions(Long userId);
}
```

------

#### AgentChatSessionServiceImpl

实现对应的方法

```java
package com.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.dto.AgentChatSessionDTO;
import com.test.entity.AgentChatSession;
import com.test.mapper.AgentChatSessionMapper;
import com.test.service.AgentChatSessionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AgentChatSessionServiceImpl
        extends ServiceImpl<AgentChatSessionMapper, AgentChatSession>
        implements AgentChatSessionService {

    @Override
    public void createOrUpdateSession(Long userId,
                                      String sessionId,
                                      String userMessage,
                                      String assistantReply) {

        if (userId == null || sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }

        String lastMessage = assistantReply;
        if (lastMessage == null || lastMessage.trim().isEmpty()) {
            lastMessage = userMessage;
        }

        QueryWrapper<AgentChatSession> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("session_id", sessionId)
                .eq("deleted", 0);

        AgentChatSession session = this.getOne(queryWrapper);

        if (session == null) {
            session = new AgentChatSession();

            session.setUserId(userId);
            session.setSessionId(sessionId);
            session.setTitle(buildTitle(userMessage));
            session.setLastMessage(trimText(lastMessage, 100));
            session.setLastActiveTime(LocalDateTime.now());
            session.setDeleted(0);

            this.save(session);
        } else {
            session.setLastMessage(trimText(lastMessage, 100));
            session.setLastActiveTime(LocalDateTime.now());

            this.updateById(session);
        }
    }

    @Override
    public List<AgentChatSessionDTO> listUserSessions(Long userId) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        QueryWrapper<AgentChatSession> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("deleted", 0)
                .orderByDesc("last_active_time");

        List<AgentChatSession> sessions = this.list(queryWrapper);

        List<AgentChatSessionDTO> result = new ArrayList<>();

        for (AgentChatSession session : sessions) {
            AgentChatSessionDTO dto = new AgentChatSessionDTO();

            dto.setId(session.getId());
            dto.setSessionId(session.getSessionId());
            dto.setTitle(session.getTitle());
            dto.setLastMessage(session.getLastMessage());
            dto.setLastActiveTime(session.getLastActiveTime());

            result.add(dto);
        }

        return result;
    }

    private String buildTitle(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "新会话";
        }
        return trimText(message, 20);
    }

    private String trimText(String text, int maxLength) {
        if (text == null) {
            return null;
        }

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }
}
```

#### AgentServiceImpl

修改借口实现类

加入会话列表的方法

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.enums.IntentType;
import com.test.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentPlanService agentPlanService;

    @Autowired
    private AgentIntentService agentIntentService;

    @Autowired
    private AgentRecordHandler agentRecordHandler;

    @Autowired
    private AgentStatisticsHandler agentStatisticsHandler;

    @Autowired
    private AgentBudgetHandler agentBudgetHandler;

    @Autowired
    private AgentBudgetRiskHandler agentBudgetRiskHandler;

    @Autowired
    private AgentChatReplyService agentChatReplyService;

    @Autowired
    private AgentChatMessageService agentChatMessageService;

    @Autowired
    private AgentActionLogService agentActionLogService;

    @Autowired
    private AgentMemoryCacheService agentMemoryCacheService;

    @Autowired
    private AgentChatSessionService agentChatSessionService;

    @Override
    public AgentChatResponse chat(Long userId, String sessionId, String message){

        List<AgentAction> actions = new ArrayList<>();

        // 1. 保存用户消息到 MySQL
        agentChatMessageService.saveMessage(
                userId,
                sessionId,
                "USER",
                message,
                null,
                null
        );

        // 2. 保存用户消息到 Redis
        agentMemoryCacheService.appendMessage(
                sessionId,
                "USER",
                message
        );

        // 3. AI 解析计划
        AgentPlan plan = agentPlanService.parsePlan(sessionId, message);

        // 4. 后端最终意图裁决
        IntentType finalIntent = agentIntentService.decideIntent(message, plan);

        AgentChatResponse response;

        switch (finalIntent) {
            case RECORD_EXPENSE:
                response = agentRecordHandler.handle(userId, sessionId, message, plan, actions);
                break;

            case QUERY_EXPENSE:
                response = agentStatisticsHandler.handleQuery(userId, message, actions);
                break;

            case ANALYZE_EXPENSE:
                response = agentStatisticsHandler.handleAnalyze(userId, message, actions);
                break;

            case SET_BUDGET:
                response = agentBudgetHandler.handleSetBudget(userId, message, plan, actions);
                break;

            case BUDGET_RISK:
                response = agentBudgetRiskHandler.handle(userId, message, actions);
                break;

            case CHAT:
            default:
                response = agentChatReplyService.chat(sessionId, message, actions);
                break;
        }

        // 5. 保存助手回复到 MySQL
        agentChatMessageService.saveMessage(
                userId,
                sessionId,
                "ASSISTANT",
                response.getReply(),
                plan == null ? null : plan.getIntent(),
                finalIntent == null ? null : finalIntent.name()
        );

        // 6. 保存助手回复到 Redis
        agentMemoryCacheService.appendMessage(
                sessionId,
                "ASSISTANT",
                response.getReply()
        );

        // 7. 保存 actions 到 MySQL
        agentActionLogService.saveActions(
                userId,
                sessionId,
                message,
                response.getActions()
        );
        // 8. 更新会话摘要
        agentChatSessionService.createOrUpdateSession(
                userId,
                sessionId,
                message,
                response.getReply()
        );

        return response;
    }

}
```

#### BulterAgentController

调用

```java
package com.test.controller;

import com.test.common.UserContext;
import com.test.dto.AgentChatMessageDTO;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentChatSessionDTO;
import com.test.dto.AgentChatVO;
import com.test.service.AgentChatMessageService;
import com.test.service.AgentChatSessionService;
import com.test.service.AgentService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/agent")
public class BulterAgentController {

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentChatMessageService agentChatMessageService;

    @Autowired
    private AgentChatSessionService agentChatSessionService;

    @PostMapping("/chat")
    public ResultVO chat(@RequestBody AgentChatVO chatmessage) {
//        使用token拦截器获取userid
        Long userId = UserContext.getUserId();

        AgentChatResponse chat = this.agentService.chat(
                userId,
                chatmessage.getSessionId(),
                chatmessage.getMessage()
        );

        return ResultVOUtil.success(chat);
    }
    @GetMapping("/history/{sessionId}")
    public ResultVO<List<AgentChatMessageDTO>> history(@PathVariable String sessionId) {

        Long userId = UserContext.getUserId();

        List<AgentChatMessageDTO> list = agentChatMessageService.listBySession(userId, sessionId);

        return ResultVOUtil.success(list);
    }

    @GetMapping("/sessions")
    public ResultVO<List<AgentChatSessionDTO>> sessions() {

        Long userId = UserContext.getUserId();

        List<AgentChatSessionDTO> list = agentChatSessionService.listUserSessions(userId);

        return ResultVOUtil.success(list);
    }
}
```



> 测试
>
> ```
> {
>   "sessionId": "session_test_backend_001",
>   "message": "今天奶茶18"
> }
> ```
>
> GET /agent/sessions
>
> ```
> Authorization 8d5610bf47074f108dbf701b32342ccc
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": [
>     {
>       "id": 1,
>       "sessionId": "session_test_backend_001",
>       "title": "今天奶茶18",
>       "lastMessage": "好嘞，已帮你记下一笔支出：饮品18元，奶茶。",
>       "lastActiveTime": "2026-06-01T21:05:00"
>     }
>   ]
> }
> ```



### 5.17.1删除会话

#### AgentChatMessageService

添加一个删除对话的方法接口

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.AgentChatMessageDTO;
import com.test.entity.AgentChatMessage;

import java.util.List;

public interface AgentChatMessageService extends IService<AgentChatMessage> {

    void saveMessage(Long userId,
                     String sessionId,
                     String role,
                     String content,
                     String intent,
                     String finalIntent);

    List<AgentChatMessageDTO> listBySession(Long userId, String sessionId);

    void deleteBySession(Long userId, String sessionId);
}
```





#### AgentChatMessageServiceImpl

实现接口方法

```java
@Override
public void deleteBySession(Long userId, String sessionId) {

    if (userId == null) {
        throw new RuntimeException("用户未登录");
    }

    if (sessionId == null || sessionId.trim().isEmpty()) {
        throw new RuntimeException("会话ID不能为空");
    }

    QueryWrapper<AgentChatMessage> queryWrapper = new QueryWrapper<>();

    queryWrapper.eq("user_id", userId)
            .eq("session_id", sessionId)
            .eq("deleted", 0);

    this.remove(queryWrapper);
}
```



#### AgentChatSessionService

会话列表中也要进行删除，创建接口方法

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.AgentChatSessionDTO;
import com.test.entity.AgentChatSession;

import java.util.List;

public interface AgentChatSessionService extends IService<AgentChatSession> {

    void createOrUpdateSession(Long userId,
                               String sessionId,
                               String userMessage,
                               String assistantReply);

    List<AgentChatSessionDTO> listUserSessions(Long userId);

    void deleteSession(Long userId, String sessionId);
}
```





#### AgentChatSessionServiceImpl

实现接口方法

```java
@Override
public void deleteSession(Long userId, String sessionId) {

    if (userId == null) {
        throw new RuntimeException("用户未登录");
    }

    if (sessionId == null || sessionId.trim().isEmpty()) {
        throw new RuntimeException("会话ID不能为空");
    }

    QueryWrapper<AgentChatSession> queryWrapper = new QueryWrapper<>();

    queryWrapper.eq("user_id", userId)
            .eq("session_id", sessionId)
            .eq("deleted", 0);

    this.remove(queryWrapper);
}
```

#### AgentMemoryCacheService

AgentMemoryCacheService 添加删除 Redis 缓存方法

```java
package com.test.service;

import java.util.List;

public interface AgentMemoryCacheService {

    void appendMessage(String sessionId, String role, String content);

    List<String> getRecentMessages(String sessionId);

    void deleteMemory(String sessionId);
}
```



#### AgentMemoryCacheServiceImpl

```java
package com.test.service.impl;

import com.test.service.AgentMemoryCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class AgentMemoryCacheServiceImpl implements AgentMemoryCacheService {

    private static final String KEY_PREFIX = "agent:chat:memory:";

    /**
     * 每个 sessionId 最多保留最近 10 条消息
     */
    private static final int MAX_MESSAGE_COUNT = 10;

    /**
     * Redis 会话缓存 24 小时过期
     */
    private static final Duration TTL = Duration.ofHours(24);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void appendMessage(String sessionId, String role, String content) {

        if (sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }

        if (content == null || content.trim().isEmpty()) {
            return;
        }

        String key = buildKey(sessionId);

        String value = role + "：" + content + "｜" + LocalDateTime.now();

        // 1. 从右侧追加消息，保证时间顺序
        stringRedisTemplate.opsForList().rightPush(key, value);

        // 2. 只保留最近 10 条
        stringRedisTemplate.opsForList().trim(key, -MAX_MESSAGE_COUNT, -1);

        // 3. 设置过期时间
        stringRedisTemplate.expire(key, TTL);
    }

    @Override
    public List<String> getRecentMessages(String sessionId) {

        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String key = buildKey(sessionId);

        List<String> messages = stringRedisTemplate.opsForList().range(key, 0, -1);

        if (messages == null) {
            return Collections.emptyList();
        }

        return messages;
    }

    private String buildKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }

    @Override
    public void deleteMemory(String sessionId) {

        if (sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }

        String key = buildKey(sessionId);

        stringRedisTemplate.delete(key);
    }
}
```



#### BulterAgentController

BulterAgentController 添加删除接口

```java
package com.test.controller;

import com.test.common.UserContext;
import com.test.dto.AgentChatMessageDTO;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentChatSessionDTO;
import com.test.dto.AgentChatVO;
import com.test.service.AgentChatMessageService;
import com.test.service.AgentChatSessionService;
import com.test.service.AgentMemoryCacheService;
import com.test.service.AgentService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/agent")
public class BulterAgentController {

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentChatMessageService agentChatMessageService;

    @Autowired
    private AgentChatSessionService agentChatSessionService;

    @Autowired
    private AgentMemoryCacheService agentMemoryCacheService;

    @PostMapping("/chat")
    public ResultVO chat(@RequestBody AgentChatVO chatmessage) {
//        使用token拦截器获取userid
        Long userId = UserContext.getUserId();

        AgentChatResponse chat = this.agentService.chat(
                userId,
                chatmessage.getSessionId(),
                chatmessage.getMessage()
        );

        return ResultVOUtil.success(chat);
    }
    @GetMapping("/history/{sessionId}")
    public ResultVO<List<AgentChatMessageDTO>> history(@PathVariable String sessionId) {

        Long userId = UserContext.getUserId();

        List<AgentChatMessageDTO> list = agentChatMessageService.listBySession(userId, sessionId);

        return ResultVOUtil.success(list);
    }

    @GetMapping("/sessions")
    public ResultVO<List<AgentChatSessionDTO>> sessions() {

        Long userId = UserContext.getUserId();

        List<AgentChatSessionDTO> list = agentChatSessionService.listUserSessions(userId);

        return ResultVOUtil.success(list);
    }
    @DeleteMapping("/session/{sessionId}")
    public ResultVO deleteSession(@PathVariable String sessionId) {

        Long userId = UserContext.getUserId();

        // 1. 删除会话表
        agentChatSessionService.deleteSession(userId, sessionId);

        // 2. 删除消息表
        agentChatMessageService.deleteBySession(userId, sessionId);

        // 3. 删除 Redis 短期记忆
        agentMemoryCacheService.deleteMemory(sessionId);

        return ResultVOUtil.success("会话删除成功");
    }
}
```

> 测试
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": []
> }
> ```



### 5.18.1对话session优化

如果前端传了 sessionId → 使用这个会话继续聊
如果前端没传 sessionId → 后端自动生成一个新的 sessionId后端自动生成sessionid

#### AgentChatResponse

如果后端自动生成了新的 sessionId，可以返回给前端保存

```java
package com.test.dto;

import lombok.Data;

import java.util.List;

@Data
public class AgentChatResponse {

    private String sessionId;

    private String reply;

    private List<AgentAction> actions;
}
```





#### AgentServiceImpl

在 AgentServiceImpl 开头处理 sessionId

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.enums.IntentType;
import com.test.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentPlanService agentPlanService;

    @Autowired
    private AgentIntentService agentIntentService;

    @Autowired
    private AgentRecordHandler agentRecordHandler;

    @Autowired
    private AgentStatisticsHandler agentStatisticsHandler;

    @Autowired
    private AgentBudgetHandler agentBudgetHandler;

    @Autowired
    private AgentBudgetRiskHandler agentBudgetRiskHandler;

    @Autowired
    private AgentChatReplyService agentChatReplyService;

    @Autowired
    private AgentChatMessageService agentChatMessageService;

    @Autowired
    private AgentActionLogService agentActionLogService;

    @Autowired
    private AgentMemoryCacheService agentMemoryCacheService;

    @Autowired
    private AgentChatSessionService agentChatSessionService;

    @Override
    public AgentChatResponse chat(Long userId, String sessionId, String message){

        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "session_" + java.util.UUID.randomUUID().toString().replace("-", "");
        }
        
        List<AgentAction> actions = new ArrayList<>();

        // 1. 保存用户消息到 MySQL
        agentChatMessageService.saveMessage(
                userId,
                sessionId,
                "USER",
                message,
                null,
                null
        );

        // 2. 保存用户消息到 Redis
        agentMemoryCacheService.appendMessage(
                sessionId,
                "USER",
                message
        );

        // 3. AI 解析计划
        AgentPlan plan = agentPlanService.parsePlan(sessionId, message);

        // 4. 后端最终意图裁决
        IntentType finalIntent = agentIntentService.decideIntent(message, plan);

        AgentChatResponse response;

        switch (finalIntent) {
            case RECORD_EXPENSE:
                response = agentRecordHandler.handle(userId, sessionId, message, plan, actions);
                break;

            case QUERY_EXPENSE:
                response = agentStatisticsHandler.handleQuery(userId, message, actions);
                break;

            case ANALYZE_EXPENSE:
                response = agentStatisticsHandler.handleAnalyze(userId, message, actions);
                break;

            case SET_BUDGET:
                response = agentBudgetHandler.handleSetBudget(userId, message, plan, actions);
                break;

            case BUDGET_RISK:
                response = agentBudgetRiskHandler.handle(userId, message, actions);
                break;

            case CHAT:
            default:
                response = agentChatReplyService.chat(sessionId, message, actions);
                break;
        }

        // 5. 保存助手回复到 MySQL
        agentChatMessageService.saveMessage(
                userId,
                sessionId,
                "ASSISTANT",
                response.getReply(),
                plan == null ? null : plan.getIntent(),
                finalIntent == null ? null : finalIntent.name()
        );

        // 6. 保存助手回复到 Redis
        agentMemoryCacheService.appendMessage(
                sessionId,
                "ASSISTANT",
                response.getReply()
        );

        // 7. 保存 actions 到 MySQL
        agentActionLogService.saveActions(
                userId,
                sessionId,
                message,
                response.getActions()
        );
        // 8. 更新会话摘要
        agentChatSessionService.createOrUpdateSession(
                userId,
                sessionId,
                message,
                response.getReply()
        );

        return response;
    }

}
```



AgentServiceImpl

返回前设置 sessionId

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.enums.IntentType;
import com.test.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentPlanService agentPlanService;

    @Autowired
    private AgentIntentService agentIntentService;

    @Autowired
    private AgentRecordHandler agentRecordHandler;

    @Autowired
    private AgentStatisticsHandler agentStatisticsHandler;

    @Autowired
    private AgentBudgetHandler agentBudgetHandler;

    @Autowired
    private AgentBudgetRiskHandler agentBudgetRiskHandler;

    @Autowired
    private AgentChatReplyService agentChatReplyService;

    @Autowired
    private AgentChatMessageService agentChatMessageService;

    @Autowired
    private AgentActionLogService agentActionLogService;

    @Autowired
    private AgentMemoryCacheService agentMemoryCacheService;

    @Autowired
    private AgentChatSessionService agentChatSessionService;

    @Override
    public AgentChatResponse chat(Long userId, String sessionId, String message){

        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "session_" + java.util.UUID.randomUUID().toString().replace("-", "");
        }

        List<AgentAction> actions = new ArrayList<>();

        // 1. 保存用户消息到 MySQL
        agentChatMessageService.saveMessage(
                userId,
                sessionId,
                "USER",
                message,
                null,
                null
        );

        // 2. 保存用户消息到 Redis
        agentMemoryCacheService.appendMessage(
                sessionId,
                "USER",
                message
        );

        // 3. AI 解析计划
        AgentPlan plan = agentPlanService.parsePlan(sessionId, message);

        // 4. 后端最终意图裁决
        IntentType finalIntent = agentIntentService.decideIntent(message, plan);

        AgentChatResponse response;

        switch (finalIntent) {
            case RECORD_EXPENSE:
                response = agentRecordHandler.handle(userId, sessionId, message, plan, actions);
                break;

            case QUERY_EXPENSE:
                response = agentStatisticsHandler.handleQuery(userId, message, actions);
                break;

            case ANALYZE_EXPENSE:
                response = agentStatisticsHandler.handleAnalyze(userId, message, actions);
                break;

            case SET_BUDGET:
                response = agentBudgetHandler.handleSetBudget(userId, message, plan, actions);
                break;

            case BUDGET_RISK:
                response = agentBudgetRiskHandler.handle(userId, message, actions);
                break;

            case CHAT:
            default:
                response = agentChatReplyService.chat(sessionId, message, actions);
                break;
        }

        // 5. 保存助手回复到 MySQL
        agentChatMessageService.saveMessage(
                userId,
                sessionId,
                "ASSISTANT",
                response.getReply(),
                plan == null ? null : plan.getIntent(),
                finalIntent == null ? null : finalIntent.name()
        );

        // 6. 保存助手回复到 Redis
        agentMemoryCacheService.appendMessage(
                sessionId,
                "ASSISTANT",
                response.getReply()
        );

        // 7. 保存 actions 到 MySQL
        agentActionLogService.saveActions(
                userId,
                sessionId,
                message,
                response.getActions()
        );
        // 8. 更新会话摘要
        agentChatSessionService.createOrUpdateSession(
                userId,
                sessionId,
                message,
                response.getReply()
        );

        // 返回前设置 sessionId
        response.setSessionId(sessionId);

        return response;
    }

}
```





> 测试
>
> ```
> Authorization 8d5610bf47074f108dbf701b32342ccc
> ```
>
> ```
> {
>   "message": "今天奶茶18"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "sessionId": "session_d8db15b13bae468993cfc67ff3313be2",
>     "reply": "好嘞，已帮你记下一笔支出：饮品18元，奶茶。",
>     "actions": [
>       {
>         "name": "recordExpense",
>         "success": true,
>         "message": "支出记录已保存，记录ID：10"
>       }
>     ]
>   }
> }
> ```
>
> ```
> {
>   "sessionId": "session_d8db15b13bae468993cfc67ff3313be2",
>   "message": "我今天花了多少钱"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "sessionId": "session_d8db15b13bae468993cfc67ff3313be2",
>     "reply": "你今天支出共 338.00 元。",
>     "actions": [
>       {
>         "name": "queryExpense",
>         "success": true,
>         "message": "查询成功"
>       }
>     ]
>   }
> }
> ```



### 5.19.1润色AI

#### agent-reply-prompt

```
你是 Butler AI 个人财务管家助手。

你的任务是根据后端已经计算好的真实数据，生成自然、简洁、友好的中文回复。

注意：
1. 只能基于用户问题和后端提供的数据回答。
2. 不允许修改金额。
3. 不允许编造没有提供的数据。
4. 不允许说“我查询了一下数据库”。
5. 不要返回 JSON。
6. 回复尽量自然，不要太机械。
7. 如果数据为 0，也要正常说明，不要夸大。

后端会提供：
- 用户原始问题
- 时间范围
- 收入金额
- 支出金额
- 结余金额
- 预算金额
- 使用率
- 风险等级
- 业务类型

请根据这些信息生成适合用户阅读的自然语言回复。
```



#### AIService

添加新方法

plan()：识别意图，返回 JSON
chat()：普通闲聊
generateReply()：根据后端真实结果生成自然回复

```java
package com.test.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface AIService {

    @SystemMessage(fromResource = "agent-plan-prompt.txt")
    String plan(@MemoryId String sessionId, @UserMessage String message);

    @SystemMessage(fromResource = "system-prompt.txt")
    String chat(@MemoryId String sessionId, @UserMessage String message);

    @SystemMessage(fromResource = "agent-reply-prompt.txt")
    String generateReply(@MemoryId String sessionId, @UserMessage String context);
}
```



#### AgentReplyService

创建回复service方法

```java
package com.test.service;

import java.math.BigDecimal;

public interface AgentReplyService {

    String generateQueryReply(String sessionId,
                              String userMessage,
                              String timeText,
                              BigDecimal totalExpense,
                              BigDecimal totalIncome,
                              BigDecimal balance);
}
```



#### AgentReplyServiceImpl

实现对应的方法

```java
package com.test.service.impl;

import com.test.service.AIService;
import com.test.service.AgentReplyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AgentReplyServiceImpl implements AgentReplyService {

    @Autowired
    private AIService aiService;

    @Override
    public String generateQueryReply(String sessionId,
                                     String userMessage,
                                     String timeText,
                                     BigDecimal totalExpense,
                                     BigDecimal totalIncome,
                                     BigDecimal balance) {

        String context = "业务类型：收支查询\n"
                + "用户问题：" + userMessage + "\n"
                + "时间范围：" + timeText + "\n"
                + "支出金额：" + totalExpense + " 元\n"
                + "收入金额：" + totalIncome + " 元\n"
                + "结余金额：" + balance + " 元\n"
                + "请根据以上真实数据，生成一句自然、简洁的回复。";

        try {
            return aiService.generateReply(sessionId, context);
        } catch (Exception e) {
            // AI 润色失败时，回退固定模板，保证主流程不受影响
            if (userMessage != null && userMessage.contains("收入")) {
                return "你" + timeText + "收入共 " + totalIncome + " 元。";
            }

            if (userMessage != null && userMessage.contains("结余")) {
                return "你" + timeText + "收入共 " + totalIncome
                        + " 元，支出共 " + totalExpense
                        + " 元，结余 " + balance + " 元。";
            }

            return "你" + timeText + "支出共 " + totalExpense + " 元。";
        }
    }
}
```



#### AgentStatisticsHandler

在调度器使用润色方法AgentStatisticsHandler

```java
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
```



> 问题
>
> 复杂信息无法识别，比如双重反问，双重确定等语句



### 5.20.1预算预警

#### BudgetWarningResult

```private
package com.test.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BudgetWarningResult {

    /**
     * 是否触发预警
     */
    private Boolean warning;

    /**
     * NORMAL、WARNING、DANGER、OVER
     */
    private String level;

    private String message;

    private BigDecimal budgetAmount;

    private BigDecimal usedAmount;

    private BigDecimal remainingAmount;

    private BigDecimal usageRate;
}
```





#### BudgetWarningLog

```java
package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("budget_warning_log")
public class BudgetWarningLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String budgetMonth;

    private String category;

    private BigDecimal budgetAmount;

    private BigDecimal usedAmount;

    private BigDecimal usageRate;

    /**
     * NORMAL、WARNING、DANGER、OVER
     */
    private String warningLevel;

    private String warningMessage;

    /**
     * SYSTEM、SMS、EMAIL
     */
    private String notifyType;

    /**
     * 0未通知，1已通知，2通知失败
     */
    private Integer notifyStatus;

    private LocalDateTime createTime;
}
```





#### BudgetWarningLogMapper

```
package com.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.test.entity.BudgetWarningLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BudgetWarningLogMapper extends BaseMapper<BudgetWarningLog> {
}
```

#### BudgetWarningService

```java
package com.test.service;

import com.test.dto.BudgetWarningResult;

public interface BudgetWarningService {

    /**
     * 检查本月总预算是否触发预警
     */
    BudgetWarningResult checkMonthlyTotalBudget(Long userId);
}
```



#### BudgetWarningServiceImpl

```java
package com.test.service.impl;

import com.test.dto.BudgetWarningResult;
import com.test.entity.Budget;
import com.test.entity.BudgetWarningLog;
import com.test.mapper.BudgetWarningLogMapper;
import com.test.service.BudgetService;
import com.test.service.BudgetWarningService;
import com.test.service.ExpenseRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

@Service
public class BudgetWarningServiceImpl implements BudgetWarningService {

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private ExpenseRecordService expenseRecordService;

    @Autowired
    private BudgetWarningLogMapper budgetWarningLogMapper;

    @Override
    public BudgetWarningResult checkMonthlyTotalBudget(Long userId) {

        BudgetWarningResult result = new BudgetWarningResult();

        if (userId == null) {
            result.setWarning(false);
            result.setLevel("NORMAL");
            result.setMessage("");
            return result;
        }

        YearMonth yearMonth = YearMonth.now();
        String budgetMonth = yearMonth.toString();

        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        Budget budget = budgetService.getBudget(
                userId,
                budgetMonth,
                "TOTAL"
        );

        if (budget == null || budget.getAmount() == null) {
            result.setWarning(false);
            result.setLevel("NORMAL");
            result.setMessage("");
            return result;
        }

        BigDecimal budgetAmount = budget.getAmount();

        if (budgetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            result.setWarning(false);
            result.setLevel("NORMAL");
            result.setMessage("");
            return result;
        }

        BigDecimal usedAmount = expenseRecordService.sumAmountByDateRange(
                userId,
                "EXPENSE",
                startDate,
                endDate
        );

        BigDecimal remainingAmount = budgetAmount.subtract(usedAmount);

        BigDecimal usageRate = usedAmount
                .multiply(new BigDecimal("100"))
                .divide(budgetAmount, 2, RoundingMode.HALF_UP);

        BigDecimal warningRate = budget.getWarningRate();
        if (warningRate == null) {
            warningRate = new BigDecimal("80.00");
        }

        String level = "NORMAL";
        boolean warning = false;

        if (usageRate.compareTo(new BigDecimal("100")) >= 0) {
            level = "OVER";
            warning = true;
        } else if (usageRate.compareTo(new BigDecimal("90")) >= 0) {
            level = "DANGER";
            warning = true;
        } else if (usageRate.compareTo(warningRate) >= 0) {
            level = "WARNING";
            warning = true;
        }

        String message = "";

        if ("OVER".equals(level)) {
            message = "另外提醒一下，你本月预算已经超支，当前已支出 "
                    + usedAmount + " 元，预算为 "
                    + budgetAmount + " 元。";
        } else if ("DANGER".equals(level)) {
            message = "另外提醒一下，你本月预算使用率已经达到 "
                    + usageRate + "%，接下来要谨慎消费。";
        } else if ("WARNING".equals(level)) {
            message = "另外提醒一下，你本月预算使用率已经达到 "
                    + usageRate + "%，建议适当控制消费。";
        }

        result.setWarning(warning);
        result.setLevel(level);
        result.setMessage(message);
        result.setBudgetAmount(budgetAmount);
        result.setUsedAmount(usedAmount);
        result.setRemainingAmount(remainingAmount);
        result.setUsageRate(usageRate);

        if (warning) {
            BudgetWarningLog log = new BudgetWarningLog();

            log.setUserId(userId);
            log.setBudgetMonth(budgetMonth);
            log.setCategory("TOTAL");
            log.setBudgetAmount(budgetAmount);
            log.setUsedAmount(usedAmount);
            log.setUsageRate(usageRate);
            log.setWarningLevel(level);
            log.setWarningMessage(message);
            log.setNotifyType("SYSTEM");
            log.setNotifyStatus(1);

            budgetWarningLogMapper.insert(log);
        }

        return result;
    }
}
```



#### AgentRecordHandler

将预警添加到账单处理中 

```java

package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.dto.BudgetWarningResult;
import com.test.service.BudgetWarningService;
import com.test.service.ExpenseRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class AgentRecordHandler {

    @Autowired
    private BudgetWarningService budgetWarningService;

    @Autowired
    private ExpenseRecordService expenseRecordService;

    public AgentChatResponse handle(Long userId,
                                    String sessionId,
                                    String message,
                                    AgentPlan plan,
                                    List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        // 1. 判断 AI 是否解析成功
        if (plan == null || !Boolean.TRUE.equals(plan.getValid())) {
            response.setReply("我没有识别出完整的记账信息，请换一种说法，例如：今天奶茶18。");
            response.setActions(actions);
            return response;
        }

        // 2. 判断金额是否存在
        if (plan.getAmount() == null) {
            response.setReply("我识别到你想记账，但没有识别到金额，请补充金额。");
            response.setActions(actions);
            return response;
        }

        // 3. 类别兜底
        String category = plan.getCategory();
        if (category == null || category.trim().isEmpty()) {
            category = "其他";
        }

        // 4. 描述兜底
        String description = plan.getDescription();
        if (description == null || description.trim().isEmpty()) {
            description = "日常记录";
        }

        // 5. 收支类型兜底
        String recordType = plan.getRecordType();
        if (recordType == null || recordType.trim().isEmpty()) {
            recordType = "EXPENSE";
        }

        // 6. 调用业务 Service，真正保存到数据库
        Long recordId = expenseRecordService.createRecord(
                userId,
                plan.getAmount(),
                category,
                recordType,
                description,
                LocalDate.now(),
                "AGENT",
                message,
                sessionId
        );

        //预警
        BudgetWarningResult warningResult = null;

        if ("EXPENSE".equalsIgnoreCase(recordType)) {
            warningResult = budgetWarningService.checkMonthlyTotalBudget(userId);
        }

        // 7. 组装 action
        AgentAction action = new AgentAction();

        if ("INCOME".equalsIgnoreCase(recordType)) {
            action.setName("recordIncome");
            action.setMessage("收入记录已保存，记录ID：" + recordId);
        } else {
            action.setName("recordExpense");
            action.setMessage("支出记录已保存，记录ID：" + recordId);
        }

        action.setSuccess(true);
        actions.add(action);

        // 8. 组装回复
        String typeText = "INCOME".equalsIgnoreCase(recordType) ? "收入" : "支出";

//        预警安排
        String reply = "好嘞，已帮你记下一笔"
                + typeText
                + "："
                + category
                + plan.getAmount()
                + "元，"
                + description
                + "。";

        if (warningResult != null && Boolean.TRUE.equals(warningResult.getWarning())) {
            reply = reply + warningResult.getMessage();

            AgentAction warningAction = new AgentAction();
            warningAction.setName("budgetWarning");
            warningAction.setSuccess(true);
            warningAction.setMessage("触发预算预警，风险等级：" + warningResult.getLevel());
            actions.add(warningAction);
        }

        response.setActions(actions);

        return response;
    }
}
```

>测试

### 5.20.1预算查询功能补全

#### AgentIntentServiceImpl

```java
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

        // 3. 查询类
        if (isQueryMessage(message)) {
            return IntentType.QUERY_EXPENSE;
        }

        // 4. 分析类
        if (isAnalyzeMessage(message)) {
            return IntentType.ANALYZE_EXPENSE;
        }

        // 5. AI 解析成功时，参考 AI intent
        if (plan != null && Boolean.TRUE.equals(plan.getValid())) {
            String intent = plan.getIntent();

            if ("RECORD_EXPENSE".equals(intent)) {
                return IntentType.RECORD_EXPENSE;
            }
            if ("QUERY_EXPENSE".equals(intent)) {
                return IntentType.QUERY_EXPENSE;
            }
            if ("ANALYZE_EXPENSE".equals(intent)) {
                return IntentType.ANALYZE_EXPENSE;
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
}
```

### 5.20.1分类预算查询/分类预算风险判断

#### ExpenseRecordService 

添加分类方法

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.CategoryAmountDTO;
import com.test.entity.ExpenseRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRecordService extends IService<ExpenseRecord> {

    BigDecimal sumAmountByDateRangeAndCategory(Long userId,
                                               String recordType,
                                               String category,
                                               LocalDate startDate,
                                               LocalDate endDate);

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
}

```



#### ExpenseRecordServiceImpl

分类方法的实现

```java
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
```



#### AgentBudgetRiskHandler

在预算调度器中添加分类识别方法

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.entity.Budget;
import com.test.service.BudgetService;
import com.test.service.ExpenseRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class AgentBudgetRiskHandler {

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private ExpenseRecordService expenseRecordService;

    public AgentChatResponse handle(Long userId,
                                    String message,
                                    List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        if (userId == null) {
            response.setReply("用户ID不能为空，无法判断预算风险。");
            response.setActions(actions);
            return response;
        }

        // 1. 当前先默认判断本月总预算
        YearMonth yearMonth = YearMonth.now();
        String budgetMonth = yearMonth.toString();

        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // 2. 查询本月总预算
        String category = resolveBudgetCategory(message);

        Budget budget = budgetService.getBudget(
                userId,
                budgetMonth,
                category
        );

        if (budget == null || budget.getAmount() == null) {
            response.setReply("你还没有设置" + budgetMonth + "的总预算，请先设置预算，例如：帮我把本月预算设置为1800。");
            response.setActions(actions);
            return response;
        }

        BigDecimal budgetAmount = budget.getAmount();

        if (budgetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            response.setReply("当前预算金额无效，请重新设置预算。");
            response.setActions(actions);
            return response;
        }

        // 3. 查询本月支出
        BigDecimal usedAmount;

        if ("TOTAL".equals(category)) {
            usedAmount = expenseRecordService.sumAmountByDateRange(
                    userId,
                    "EXPENSE",
                    startDate,
                    endDate
            );
        } else {
            usedAmount = expenseRecordService.sumAmountByDateRangeAndCategory(
                    userId,
                    "EXPENSE",
                    category,
                    startDate,
                    endDate
            );
        }

        // 4. 计算剩余预算
        BigDecimal remainingAmount = budgetAmount.subtract(usedAmount);

        // 5. 计算使用率
        BigDecimal usageRate = usedAmount
                .multiply(new BigDecimal("100"))
                .divide(budgetAmount, 2, RoundingMode.HALF_UP);

        // 6. 判断风险等级
        String level;
        String advice;

        if (usageRate.compareTo(new BigDecimal("100")) >= 0) {
            level = "OVER";
            advice = "已经超出预算，建议接下来控制非必要支出。";
        } else if (usageRate.compareTo(new BigDecimal("90")) >= 0) {
            level = "DANGER";
            advice = "预算使用率已经很高，接下来要谨慎消费。";
        } else if (usageRate.compareTo(new BigDecimal("80")) >= 0) {
            level = "WARNING";
            advice = "预算使用率接近预警线，建议适当控制消费。";
        } else {
            level = "NORMAL";
            advice = "目前预算比较安全，可以继续保持。";
        }

        // 7. 添加 action
        AgentAction action = new AgentAction();
        action.setName("checkBudgetRisk");
        action.setSuccess(true);
        action.setMessage("预算风险判断成功，风险等级：" + level);
        actions.add(action);

        // 8. 返回回复
        String budgetName = "TOTAL".equals(category) ? "总预算" : category + "预算";

        response.setReply("你" + budgetMonth + "的" + budgetName + "是 "
                + budgetAmount
                + " 元，目前已支出 "
                + usedAmount
                + " 元，预算使用率为 "
                + usageRate
                + "%，剩余预算 "
                + remainingAmount
                + " 元。"
                + advice);
        response.setActions(actions);

        return response;
    }
    private String resolveBudgetCategory(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "TOTAL";
        }

        if (message.contains("餐饮") || message.contains("吃饭") || message.contains("饭")) {
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

        if (message.contains("娱乐") || message.contains("电影") || message.contains("游戏")) {
            return "娱乐";
        }

        if (message.contains("学习") || message.contains("买书") || message.contains("课程")) {
            return "学习";
        }

        if (message.contains("住房") || message.contains("房租")) {
            return "住房";
        }

        if (message.contains("医疗") || message.contains("看病") || message.contains("药")) {
            return "医疗";
        }

        return "TOTAL";
    }
}
```



### 5.21.1预警优化

#### BudgetWarningService

```java
package com.test.service;

import com.test.dto.BudgetWarningResult;

import java.util.List;

public interface BudgetWarningService {

    BudgetWarningResult checkMonthlyTotalBudget(Long userId);

    List<BudgetWarningResult> checkAfterExpense(Long userId, String category);
}
```



#### BudgetWarningResult

```java
@Data
public class BudgetWarningResult {

    private Boolean warning;

    private String level;

    private String category;

    private String message;

    private BigDecimal budgetAmount;

    private BigDecimal usedAmount;

    private BigDecimal remainingAmount;

    private BigDecimal usageRate;
}
```



#### BudgetWarningServiceImpl

在 BudgetWarningServiceImpl 里新增方法

检查 TOTAL 总预算
检查当前分类预算
把触发的预警都返回

```java
@Override
public List<BudgetWarningResult> checkAfterExpense(Long userId, String category) {

    List<BudgetWarningResult> results = new ArrayList<>();

    // 1. 检查总预算
    BudgetWarningResult totalResult = checkBudgetByCategory(userId, "TOTAL");
    if (totalResult != null && Boolean.TRUE.equals(totalResult.getWarning())) {
        results.add(totalResult);
    }

    // 2. 检查分类预算
    if (category != null && !category.trim().isEmpty()) {
        BudgetWarningResult categoryResult = checkBudgetByCategory(userId, category);
        if (categoryResult != null && Boolean.TRUE.equals(categoryResult.getWarning())) {
            results.add(categoryResult);
        }
    }

    return results;
}
```



#### BudgetWarningServiceImpl

```java
private BudgetWarningResult checkBudgetByCategory(Long userId, String category) {

    BudgetWarningResult result = new BudgetWarningResult();

    if (userId == null) {
        result.setWarning(false);
        result.setLevel("NORMAL");
        result.setMessage("");
        return result;
    }

    YearMonth yearMonth = YearMonth.now();
    String budgetMonth = yearMonth.toString();

    LocalDate startDate = yearMonth.atDay(1);
    LocalDate endDate = yearMonth.atEndOfMonth();

    Budget budget = budgetService.getBudget(userId, budgetMonth, category);

    if (budget == null || budget.getAmount() == null) {
        result.setWarning(false);
        result.setLevel("NORMAL");
        result.setCategory(category);
        result.setMessage("");
        return result;
    }

    BigDecimal budgetAmount = budget.getAmount();

    if (budgetAmount.compareTo(BigDecimal.ZERO) <= 0) {
        result.setWarning(false);
        result.setLevel("NORMAL");
        result.setCategory(category);
        result.setMessage("");
        return result;
    }

    BigDecimal usedAmount;

    if ("TOTAL".equals(category)) {
        usedAmount = expenseRecordService.sumAmountByDateRange(
                userId,
                "EXPENSE",
                startDate,
                endDate
        );
    } else {
        usedAmount = expenseRecordService.sumAmountByDateRangeAndCategory(
                userId,
                "EXPENSE",
                category,
                startDate,
                endDate
        );
    }

    BigDecimal remainingAmount = budgetAmount.subtract(usedAmount);

    BigDecimal usageRate = usedAmount
            .multiply(new BigDecimal("100"))
            .divide(budgetAmount, 2, RoundingMode.HALF_UP);

    BigDecimal warningRate = budget.getWarningRate();
    if (warningRate == null) {
        warningRate = new BigDecimal("80.00");
    }

    String level = "NORMAL";
    boolean warning = false;

    if (usageRate.compareTo(new BigDecimal("100")) >= 0) {
        level = "OVER";
        warning = true;
    } else if (usageRate.compareTo(new BigDecimal("90")) >= 0) {
        level = "DANGER";
        warning = true;
    } else if (usageRate.compareTo(warningRate) >= 0) {
        level = "WARNING";
        warning = true;
    }

    String budgetName = "TOTAL".equals(category) ? "总预算" : category + "预算";

    String message = "";

    if ("OVER".equals(level)) {
        message = "另外提醒一下，你本月" + budgetName + "已经超支，当前已支出 "
                + usedAmount + " 元，预算为 "
                + budgetAmount + " 元。";
    } else if ("DANGER".equals(level)) {
        message = "另外提醒一下，你本月" + budgetName + "使用率已经达到 "
                + usageRate + "%，接下来要谨慎消费。";
    } else if ("WARNING".equals(level)) {
        message = "另外提醒一下，你本月" + budgetName + "使用率已经达到 "
                + usageRate + "%，建议适当控制消费。";
    }

    result.setWarning(warning);
    result.setLevel(level);
    result.setCategory(category);
    result.setMessage(message);
    result.setBudgetAmount(budgetAmount);
    result.setUsedAmount(usedAmount);
    result.setRemainingAmount(remainingAmount);
    result.setUsageRate(usageRate);

    if (warning) {
        BudgetWarningLog log = new BudgetWarningLog();

        log.setUserId(userId);
        log.setBudgetMonth(budgetMonth);
        log.setCategory(category);
        log.setBudgetAmount(budgetAmount);
        log.setUsedAmount(usedAmount);
        log.setUsageRate(usageRate);
        log.setWarningLevel(level);
        log.setWarningMessage(message);
        log.setNotifyType("SYSTEM");
        log.setNotifyStatus(1);

        budgetWarningLogMapper.insert(log);
    }

    return result;
}
```



#### AgentRecordHandler

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.dto.BudgetWarningResult;
import com.test.service.BudgetWarningService;
import com.test.service.ExpenseRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class AgentRecordHandler {

    @Autowired
    private BudgetWarningService budgetWarningService;

    @Autowired
    private ExpenseRecordService expenseRecordService;

    public AgentChatResponse handle(Long userId,
                                    String sessionId,
                                    String message,
                                    AgentPlan plan,
                                    List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        // 1. 判断 AI 是否解析成功
        if (plan == null || !Boolean.TRUE.equals(plan.getValid())) {
            response.setReply("我没有识别出完整的记账信息，请换一种说法，例如：今天奶茶18。");
            response.setActions(actions);
            return response;
        }

        // 2. 判断金额是否存在
        if (plan.getAmount() == null) {
            response.setReply("我识别到你想记账，但没有识别到金额，请补充金额。");
            response.setActions(actions);
            return response;
        }

        // 3. 类别兜底
        String category = plan.getCategory();
        if (category == null || category.trim().isEmpty()) {
            category = "其他";
        }

        // 4. 描述兜底
        String description = plan.getDescription();
        if (description == null || description.trim().isEmpty()) {
            description = "日常记录";
        }

        // 5. 收支类型兜底
        String recordType = plan.getRecordType();
        if (recordType == null || recordType.trim().isEmpty()) {
            recordType = "EXPENSE";
        }

        // 6. 调用业务 Service，真正保存到数据库
        Long recordId = expenseRecordService.createRecord(
                userId,
                plan.getAmount(),
                category,
                recordType,
                description,
                LocalDate.now(),
                "AGENT",
                message,
                sessionId
        );

        //预警
        List<BudgetWarningResult> warningResults = new ArrayList<>();

        if ("EXPENSE".equalsIgnoreCase(recordType)) {
            warningResults = budgetWarningService.checkAfterExpense(userId, category);
        }

        // 7. 组装 action
        AgentAction action = new AgentAction();

        if ("INCOME".equalsIgnoreCase(recordType)) {
            action.setName("recordIncome");
            action.setMessage("收入记录已保存，记录ID：" + recordId);
        } else {
            action.setName("recordExpense");
            action.setMessage("支出记录已保存，记录ID：" + recordId);
        }

        action.setSuccess(true);
        actions.add(action);

        // 8. 组装回复
        String typeText = "INCOME".equalsIgnoreCase(recordType) ? "收入" : "支出";

//        预警安排
        String reply = "好嘞，已帮你记下一笔"
                + typeText
                + "："
                + category
                + plan.getAmount()
                + "元，"
                + description
                + "。";

        if (warningResults != null && !warningResults.isEmpty()) {
            for (BudgetWarningResult warningResult : warningResults) {
                reply = reply + warningResult.getMessage();

                AgentAction warningAction = new AgentAction();
                warningAction.setName("budgetWarning");
                warningAction.setSuccess(true);
                warningAction.setMessage("触发预算预警，分类："
                        + warningResult.getCategory()
                        + "，风险等级："
                        + warningResult.getLevel());

                actions.add(warningAction);
            }
        }

        response.setReply(reply);
        response.setActions(actions);

        return response;
    }
}
```

> 测试
>
> ```
> {
>   "message": "帮我把本月预算设置为500"
> }
> ```
>
> ```
> {
>   "message": "设置餐饮预算100"
> }
> ```
>
> ```
> {
>   "message": "今天海底捞115"
> }
> ```
>
> ```json
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "sessionId": "session_2d947ddf1b064a8999cc3a57c3f335fd",
>     "reply": "好嘞，已帮你记下一笔支出：餐饮115元，海底捞。另外提醒一下，你本月总预算已经超支，当前已支出 612.00 元，预算为 500.00 元。另外提醒一下，你本月餐饮预算已经超支，当前已支出 504.00 元，预算为 100.00 元。",
>     "actions": [
>       {
>         "name": "recordExpense",
>         "success": true,
>         "message": "支出记录已保存，记录ID：13"
>       },
>       {
>         "name": "budgetWarning",
>         "success": true,
>         "message": "触发预算预警，分类：TOTAL，风险等级：OVER"
>       },
>       {
>         "name": "budgetWarning",
>         "success": true,
>         "message": "触发预算预警，分类：餐饮，风险等级：OVER"
>       }
>     ]
>   }
> }
> ```



### 5.22.1多笔记账功能

#### AgentExpenseItem

创建需要的实体类

```java
package com.test.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AgentExpenseItem {

    private BigDecimal amount;

    private String category;

    private String recordType;

    private String description;
}
```

#### AgentPlan



```java
package com.test.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AgentPlan {

    private String intent;

    private String recordType;

    private BigDecimal amount;

    private String category;

    private String description;

    private Boolean valid;

    private String reason;

    /**
     * 多笔记账时使用
     */
    private List<AgentExpenseItem> expenses;
}
```

#### system-prompt

修改提示词

```
你是一个 AI 个人财务管家助手，负责将用户输入的自然语言解析成固定 JSON 格式。

你的任务不是直接回答用户，而是识别用户意图，并提取结构化参数。

你只能返回 JSON。
不要返回解释。
不要返回 Markdown。
不要使用 ```json 代码块。
不要在 JSON 前后添加任何文字。

返回 JSON 必须严格符合以下格式：

{
"intent": "RECORD_EXPENSE",
"recordType": "EXPENSE",
"amount": 18,
"category": "饮品",
"description": "奶茶",
"expenses": [
{
"amount": 18,
"category": "饮品",
"recordType": "EXPENSE",
"description": "奶茶"
}
],
"valid": true,
"reason": ""
}

字段说明：

1. intent 表示用户意图，只能取以下值：

* RECORD_EXPENSE：用户想记录一笔或多笔收入/支出
* QUERY_EXPENSE：用户想查询消费金额、收入金额、结余或收支记录
* ANALYZE_EXPENSE：用户想分析消费、收入或收支情况
* SET_BUDGET：用户想设置预算
* BUDGET_RISK：用户想查询预算、判断预算是否超支、预算是否够用、剩余预算是多少
* CHAT：普通闲聊
* UNKNOWN：无法判断意图

2. recordType 表示记录类型，只能取以下值：

* EXPENSE：支出，例如吃饭、奶茶、打车、购物、买书、看电影、房租、医疗等
* INCOME：收入，例如工资、兼职、红包、奖金、退款、报销、转账收入等

如果 intent 不是 RECORD_EXPENSE，则 recordType 填写 null。

如果用户输入中同时包含收入和支出，则 recordType 填写第一笔记录的类型，所有记录放入 expenses 数组中。

3. amount 表示金额。

* 如果用户输入中有明确金额，填写数字。
* 如果没有金额，填写 null。
* 金额必须使用阿拉伯数字，不要带单位。
* 例如“18元”“十八块”“18块钱”都返回 18。
* 如果是多笔记录，amount 填写第一笔记录的金额。

4. category 表示类别。

支出类别只能从以下类别中选择：
餐饮、饮品、交通、购物、学习、娱乐、医疗、住房、其他

收入类别只能从以下类别中选择：
工资、兼职、红包、奖金、退款、报销、转账、其他

预算类别可以是：
餐饮、饮品、交通、购物、学习、娱乐、医疗、住房、其他

如果是总预算，category 填写 null。

如果无法判断类别，填写 null。

5. description 表示记录描述。

例如：
奶茶、午饭、打车、买书、工资到账、兼职收入、红包、退款。

如果无法提取，填写 null。

如果是多笔记录，description 填写第一笔记录的描述。

6. expenses 表示多笔收支记录。

只有 intent 为 RECORD_EXPENSE 时使用。

* 如果用户只输入一笔记录，expenses 返回一个元素。
* 如果用户一次输入多笔记录，expenses 返回所有识别到的记录。
* 每一项必须包含 amount、category、recordType、description。
* 每一项的 recordType 必须是 EXPENSE 或 INCOME。
* 如果某一笔缺少金额，则不要把这一笔放入 expenses。
* 如果所有记录都缺少金额，则 valid 为 false。
* 如果 intent 不是 RECORD_EXPENSE，则 expenses 填写 null。

expenses 每一项格式：

{
"amount": 18,
"category": "饮品",
"recordType": "EXPENSE",
"description": "奶茶"
}

7. valid 表示本次解析是否有效。

* 如果用户表达清楚，填写 true。
* 如果缺少关键信息，填写 false。
* 记账类请求必须至少识别出一笔有效的金额记录，valid 才能为 true。
* 查询、分析、预算查询、预算风险、闲聊类请求，只要意图明确，valid 就可以为 true。

8. reason 表示原因说明。

* 如果 valid 为 true，reason 填写空字符串 ""。
* 如果 valid 为 false，说明缺少什么信息或为什么无法解析。

判断规则：

1. 如果用户输入包含明确消费内容和金额，判断为 RECORD_EXPENSE，并且 recordType 为 EXPENSE。

例如：
今天奶茶18
午饭28元
打车花了22
买书50
房租1200

2. 如果用户输入包含明确收入来源和金额，判断为 RECORD_EXPENSE，并且 recordType 为 INCOME。

例如：
今天工资到账5000
收到兼职费300
红包收入88
奖金1000
退款20
报销120

3. 如果用户一次输入多笔收支记录，也判断为 RECORD_EXPENSE。

例如：
今天奶茶18，午饭28，打车22
今天工资5000，奶茶18，打车22
收到红包88，午饭28

要求：

* amount、category、recordType、description 填写第一笔记录的信息。
* expenses 数组中返回所有识别到的有效记录。
* 不要只返回第一笔。
* 不要在 reason 中说“当前仅提取第一笔”。

4. 如果用户是在问消费、收入或收支情况，判断为 QUERY_EXPENSE。

例如：
我这个月花了多少钱
今天一共消费多少
本月收入多少
我这个月结余多少
查一下本月收支
我今天消费多少

5. 如果用户要求分析消费、收入或收支情况，判断为 ANALYZE_EXPENSE。

例如：
分析一下我这个月的消费情况
看看我最近消费结构
分析一下我的收入和支出
看看本月收支情况

6. 如果用户想设置预算，判断为 SET_BUDGET。

例如：
帮我把本月预算设置为1800
设置餐饮预算800
这个月预算设成2000
饮品预算设置为200
交通预算300

总预算设置规则：

* “本月预算设置为1800”
* “这个月预算设成2000”
* “总预算1800”
  这些属于总预算，category 填写 null。

分类预算设置规则：

* “设置餐饮预算800” category 填写 “餐饮”
* “饮品预算设置为200” category 填写 “饮品”
* “交通预算300” category 填写 “交通”

7. 如果用户询问预算查询、预算是否够用、是否超支、剩余预算，判断为 BUDGET_RISK。

例如：
预算查询
查一下预算
预算情况
本月预算情况
我这个月会不会超预算
我还能控制在1800以内吗
这个月预算够不够
我是不是快超支了
还剩多少预算
餐饮预算还剩多少
饮品预算够不够
交通预算查询

8. 如果用户只是普通聊天，判断为 CHAT。

例如：
你好
你是谁
我不想上班
今天天气不错
今天好烦

9. 如果用户表达不完整，判断为 UNKNOWN。

例如：
今天花了
帮我记一下
预算
查一下

示例 1：

用户输入：今天奶茶18

返回：
{
"intent": "RECORD_EXPENSE",
"recordType": "EXPENSE",
"amount": 18,
"category": "饮品",
"description": "奶茶",
"expenses": [
{
"amount": 18,
"category": "饮品",
"recordType": "EXPENSE",
"description": "奶茶"
}
],
"valid": true,
"reason": ""
}

示例 2：

用户输入：午饭28元

返回：
{
"intent": "RECORD_EXPENSE",
"recordType": "EXPENSE",
"amount": 28,
"category": "餐饮",
"description": "午饭",
"expenses": [
{
"amount": 28,
"category": "餐饮",
"recordType": "EXPENSE",
"description": "午饭"
}
],
"valid": true,
"reason": ""
}

示例 3：

用户输入：打车花了22

返回：
{
"intent": "RECORD_EXPENSE",
"recordType": "EXPENSE",
"amount": 22,
"category": "交通",
"description": "打车",
"expenses": [
{
"amount": 22,
"category": "交通",
"recordType": "EXPENSE",
"description": "打车"
}
],
"valid": true,
"reason": ""
}

示例 4：

用户输入：今天工资到账5000

返回：
{
"intent": "RECORD_EXPENSE",
"recordType": "INCOME",
"amount": 5000,
"category": "工资",
"description": "工资到账",
"expenses": [
{
"amount": 5000,
"category": "工资",
"recordType": "INCOME",
"description": "工资到账"
}
],
"valid": true,
"reason": ""
}

示例 5：

用户输入：今天奶茶18，午饭28，打车22

返回：
{
"intent": "RECORD_EXPENSE",
"recordType": "EXPENSE",
"amount": 18,
"category": "饮品",
"description": "奶茶",
"expenses": [
{
"amount": 18,
"category": "饮品",
"recordType": "EXPENSE",
"description": "奶茶"
},
{
"amount": 28,
"category": "餐饮",
"recordType": "EXPENSE",
"description": "午饭"
},
{
"amount": 22,
"category": "交通",
"recordType": "EXPENSE",
"description": "打车"
}
],
"valid": true,
"reason": ""
}

示例 6：

用户输入：今天工资5000，奶茶18，打车22

返回：
{
"intent": "RECORD_EXPENSE",
"recordType": "INCOME",
"amount": 5000,
"category": "工资",
"description": "工资",
"expenses": [
{
"amount": 5000,
"category": "工资",
"recordType": "INCOME",
"description": "工资"
},
{
"amount": 18,
"category": "饮品",
"recordType": "EXPENSE",
"description": "奶茶"
},
{
"amount": 22,
"category": "交通",
"recordType": "EXPENSE",
"description": "打车"
}
],
"valid": true,
"reason": ""
}

示例 7：

用户输入：我这个月花了多少钱

返回：
{
"intent": "QUERY_EXPENSE",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 8：

用户输入：我这个月收入多少

返回：
{
"intent": "QUERY_EXPENSE",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 9：

用户输入：分析一下我这个月的消费情况

返回：
{
"intent": "ANALYZE_EXPENSE",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 10：

用户输入：帮我把本月预算设置为1800

返回：
{
"intent": "SET_BUDGET",
"recordType": null,
"amount": 1800,
"category": null,
"description": "本月预算",
"expenses": null,
"valid": true,
"reason": ""
}

示例 11：

用户输入：设置餐饮预算800

返回：
{
"intent": "SET_BUDGET",
"recordType": null,
"amount": 800,
"category": "餐饮",
"description": "餐饮预算",
"expenses": null,
"valid": true,
"reason": ""
}

示例 12：

用户输入：预算查询

返回：
{
"intent": "BUDGET_RISK",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 13：

用户输入：餐饮预算还剩多少

返回：
{
"intent": "BUDGET_RISK",
"recordType": null,
"amount": null,
"category": "餐饮",
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 14：

用户输入：我这个月会不会超预算

返回：
{
"intent": "BUDGET_RISK",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 15：

用户输入：你好

返回：
{
"intent": "CHAT",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 16：

用户输入：今天花了

返回：
{
"intent": "UNKNOWN",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"expenses": null,
"valid": false,
"reason": "缺少金额和具体收支内容"
}

```



#### AgentRecordHandler

这里改动比较多 ，详细解释一下

从 plan 里取一笔 —— 保存一次 —— 生成一次action —— 查一次预算 —— 返回

从 plan.expenses 里取多笔
↓
如果没有 expenses，就兼容 plan.amount 单笔
↓
循环保存多条 expense_record
↓
统一生成 recordBatch / recordExpense action
↓
保存完后统一检查预算预警
↓
生成最终 reply
↓
response.setReply(reply)
↓
返回



把原本的“判断金额 + 类别兜底 + 描述兜底 + 类型兜底 + createRecord”改成

列表+循环的形式去处理

循环中保留原本的流程，最后将多笔记账，存入列表



原本的记账回复

只能针对单笔账目进行，现在可以多笔账目

最后将需要回复的内容进行组装





```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentExpenseItem;
import com.test.dto.AgentPlan;
import com.test.dto.BudgetWarningResult;
import com.test.service.BudgetWarningService;
import com.test.service.ExpenseRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentRecordHandler {

    @Autowired
    private BudgetWarningService budgetWarningService;

    @Autowired
    private ExpenseRecordService expenseRecordService;

    public AgentChatResponse handle(Long userId,
                                    String sessionId,
                                    String message,
                                    AgentPlan plan,
                                    List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        // 1. 判断 AI 是否解析成功
        if (plan == null || !Boolean.TRUE.equals(plan.getValid())) {
            response.setReply("我没有识别出完整的记账信息，请换一种说法，例如：今天奶茶18。");
            response.setActions(actions);
            return response;
        }

        // 2. 构建待保存的记录列表
        // 优先使用 plan.expenses；如果没有，则兼容旧的单笔字段
        List<AgentExpenseItem> recordItems = buildRecordItems(plan);

        if (recordItems == null || recordItems.isEmpty()) {
            response.setReply("我识别到你想记账，但没有识别到有效金额，请补充金额。");
            response.setActions(actions);
            return response;
        }

        // 3. 循环保存多笔记录
        List<Long> recordIds = new ArrayList<>();
        List<AgentExpenseItem> savedItems = new ArrayList<>();

        for (AgentExpenseItem item : recordItems) {

            if (item == null) {
                continue;
            }

            if (item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String recordType = normalizeRecordType(item.getRecordType());
            String category = normalizeCategory(item.getCategory());
            String description = normalizeDescription(item.getDescription());

            Long recordId = expenseRecordService.createRecord(
                    userId,
                    item.getAmount(),
                    category,
                    recordType,
                    description,
                    LocalDate.now(),
                    "AGENT",
                    message,
                    sessionId
            );

            recordIds.add(recordId);

            AgentExpenseItem savedItem = new AgentExpenseItem();
            savedItem.setAmount(item.getAmount());
            savedItem.setCategory(category);
            savedItem.setRecordType(recordType);
            savedItem.setDescription(description);

            savedItems.add(savedItem);
        }

        if (savedItems.isEmpty()) {
            response.setReply("我识别到你想记账，但没有识别到有效金额，请补充金额。");
            response.setActions(actions);
            return response;
        }

        // 4. 添加记账 action
        AgentAction recordAction = new AgentAction();

        if (savedItems.size() == 1) {
            AgentExpenseItem item = savedItems.get(0);

            if ("INCOME".equalsIgnoreCase(item.getRecordType())) {
                recordAction.setName("recordIncome");
                recordAction.setMessage("收入记录已保存，记录ID：" + recordIds.get(0));
            } else {
                recordAction.setName("recordExpense");
                recordAction.setMessage("支出记录已保存，记录ID：" + recordIds.get(0));
            }
        } else {
            recordAction.setName("recordBatch");
            recordAction.setMessage("多笔记账成功，共保存" + savedItems.size() + "条记录，记录ID：" + recordIds);
        }

        recordAction.setSuccess(true);
        actions.add(recordAction);

        // 5. 生成基础回复
        String reply = buildReply(savedItems);

        // 6. 保存完所有记录后，再统一检查预算预警
        List<BudgetWarningResult> warningResults = checkBudgetWarnings(userId, savedItems);

        if (warningResults != null && !warningResults.isEmpty()) {
            for (BudgetWarningResult warningResult : warningResults) {
                reply = reply + warningResult.getMessage();

                AgentAction warningAction = new AgentAction();
                warningAction.setName("budgetWarning");
                warningAction.setSuccess(true);
                warningAction.setMessage("触发预算预警，分类："
                        + warningResult.getCategory()
                        + "，风险等级："
                        + warningResult.getLevel());

                actions.add(warningAction);
            }
        }

        // 7. 一定要设置 reply
        response.setReply(reply);
        response.setActions(actions);

        return response;
    }

    /**
     * 优先使用 AI 返回的 expenses。
     * 如果 expenses 为空，则兼容旧版单笔字段。
     */
    private List<AgentExpenseItem> buildRecordItems(AgentPlan plan) {

        List<AgentExpenseItem> items = plan.getExpenses();

        if (items != null && !items.isEmpty()) {
            return items;
        }

        List<AgentExpenseItem> fallbackItems = new ArrayList<>();

        AgentExpenseItem item = new AgentExpenseItem();
        item.setAmount(plan.getAmount());
        item.setCategory(plan.getCategory());
        item.setRecordType(plan.getRecordType());
        item.setDescription(plan.getDescription());

        fallbackItems.add(item);

        return fallbackItems;
    }

    /**
     * 多笔记账后统一检查预算预警。
     * 这里会检查：
     * 1. 总预算 TOTAL
     * 2. 本次涉及到的分类预算
     *
     * 用 Map 去重，避免多笔同类支出重复提示。
     */
    private List<BudgetWarningResult> checkBudgetWarnings(Long userId,
                                                          List<AgentExpenseItem> savedItems) {

        Map<String, BudgetWarningResult> warningMap = new LinkedHashMap<>();

        for (AgentExpenseItem item : savedItems) {

            if (!"EXPENSE".equalsIgnoreCase(item.getRecordType())) {
                continue;
            }

            List<BudgetWarningResult> results = budgetWarningService.checkAfterExpense(
                    userId,
                    item.getCategory()
            );

            if (results == null || results.isEmpty()) {
                continue;
            }

            for (BudgetWarningResult result : results) {
                if (result == null || !Boolean.TRUE.equals(result.getWarning())) {
                    continue;
                }

                String key = result.getCategory() + "_" + result.getLevel();
                warningMap.putIfAbsent(key, result);
            }
        }

        return new ArrayList<>(warningMap.values());
    }

    /**
     * 生成回复文案
     */
    private String buildReply(List<AgentExpenseItem> savedItems) {

        if (savedItems.size() == 1) {
            AgentExpenseItem item = savedItems.get(0);

            String typeText = "INCOME".equalsIgnoreCase(item.getRecordType()) ? "收入" : "支出";

            return "好嘞，已帮你记下一笔"
                    + typeText
                    + "："
                    + item.getCategory()
                    + item.getAmount()
                    + "元，"
                    + item.getDescription()
                    + "。";
        }

        StringBuilder reply = new StringBuilder();

        reply.append("好嘞，已帮你记下")
                .append(savedItems.size())
                .append("笔记录：");

        for (int i = 0; i < savedItems.size(); i++) {
            AgentExpenseItem item = savedItems.get(i);

            String typeText = "INCOME".equalsIgnoreCase(item.getRecordType()) ? "收入" : "支出";

            reply.append(typeText)
                    .append(item.getCategory())
                    .append(item.getAmount())
                    .append("元");

            if (i < savedItems.size() - 1) {
                reply.append("、");
            } else {
                reply.append("。");
            }
        }

        return reply.toString();
    }

    private String normalizeRecordType(String recordType) {
        if ("INCOME".equalsIgnoreCase(recordType)) {
            return "INCOME";
        }
        return "EXPENSE";
    }

    private String normalizeCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return "其他";
        }
        return category.trim();
    }

    private String normalizeDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return "日常记录";
        }
        return description.trim();
    }
}
```



> 测试
>
> ```
> {
>   "sessionId": "session_d8db15b13bae468993cfc67ff3313be2",
>   "message": "今天奶茶18，午饭28，打车22"
> }
> ```
>
> ```java
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "sessionId": "session_d8db15b13bae468993cfc67ff3313be2",
>     "reply": "好嘞，已帮你记下3笔记录：支出饮品18元、支出餐饮28元、支出交通22元。另外提醒一下，你本月总预算已经超支，当前已支出 680.00 元，预算为 500.00 元。另外提醒一下，你本月餐饮预算已经超支，当前已支出 532.00 元，预算为 100.00 元。",
>     "actions": [
>       {
>         "name": "recordBatch",
>         "success": true,
>         "message": "多笔记账成功，共保存3条记录，记录ID：[14, 15, 16]"
>       },
>       {
>         "name": "budgetWarning",
>         "success": true,
>         "message": "触发预算预警，分类：TOTAL，风险等级：OVER"
>       },
>       {
>         "name": "budgetWarning",
>         "success": true,
>         "message": "触发预算预警，分类：餐饮，风险等级：OVER"
>       }
>     ]
>   }
> }
> ```



### 5.23.1账单明细查询接口

#### ExpenseRecordDTO

```java
package com.test.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ExpenseRecordDTO {

    private Long id;

    private BigDecimal amount;

    private String category;

    /**
     * EXPENSE / INCOME
     */
    private String recordType;

    private String description;

    private LocalDate expenseTime;

    private LocalDateTime expenseDatetime;

    private String sourceType;

    private String sourceText;

    private String sessionId;
}
```



#### ExpenseRecordQueryVO

```java
package com.test.dto;

import lombok.Data;

@Data
public class ExpenseRecordQueryVO {

    /**
     * today / month / all
     */
    private String range;

    /**
     * EXPENSE / INCOME
     */
    private String recordType;

    /**
     * 餐饮、饮品、交通等
     */
    private String category;
}
```

ExpenseRecordService

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.CategoryAmountDTO;
import com.test.dto.ExpenseRecordDTO;
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
}
```



#### ExpenseRecordServiceImpl

```java
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
```



#### ExpenseRecordController

```java
package com.test.controller;

import com.test.common.UserContext;
import com.test.dto.ExpenseRecordDTO;
import com.test.dto.ExpenseRecordQueryVO;
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
}
```



### 5.23.2删除账单

#### ExpenseRecordService

接口设计

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.CategoryAmountDTO;
import com.test.dto.ExpenseRecordDTO;
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
}
```



#### ExpenseRecordServiceImpl

方法实现

```java
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
```



#### ExpenseRecordController

```java
@DeleteMapping("/records/{recordId}")
public ResultVO deleteRecord(@PathVariable Long recordId) {

    Long userId = UserContext.getUserId();

    expenseRecordService.deleteRecord(userId, recordId);

    return ResultVOUtil.success("账单删除成功");
}
```



> 测试
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": "账单删除成功"
> }
> ```



### 5.23.3修改账单接口

#### ExpenseRecordUpdateVO

```java
package com.test.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseRecordUpdateVO {

    private BigDecimal amount;

    private String category;

    /**
     * EXPENSE / INCOME
     */
    private String recordType;

    private String description;

    private LocalDate expenseTime;
}
```



#### ExpenseRecordService

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.CategoryAmountDTO;
import com.test.dto.ExpenseRecordDTO;
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
}
```



#### ExpenseRecordServiceImpl

```java
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
```

#### ExpenseRecordController

```java
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
```

> 测试
>
> ```
> {
>   "amount": 20,
>   "category": "饮品",
>   "recordType": "EXPENSE",
>   "description": "奶茶",
>   "expenseTime": "2026-06-02"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": "账单修改成功"
> }
> ```
>
> 



### 5.23.4修改账单接口

#### ExpenseRecordService

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.CategoryAmountDTO;
import com.test.dto.ExpenseRecordDTO;
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
}
```



#### ExpenseRecordServiceImpl

```java
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
```



#### ExpenseRecordController

```java
@PostMapping("/records/undo-last")
public ResultVO undoLastRecord() {

    Long userId = UserContext.getUserId();

    expenseRecordService.undoLastRecord(userId);

    return ResultVOUtil.success("已撤销最近一笔账单");
}
```



> 测试
>
> ```
> {
>   "code": 200,
>   "msg": "suaccess",
>   "data": "已撤销最近一笔账单"
> }
> ```

### 5.24.1接入chat

#### IntentType

```java
package com.test.enums;

public enum IntentType {
    RECORD_EXPENSE,
    QUERY_EXPENSE,
    ANALYZE_EXPENSE,
    SET_BUDGET,
    BUDGET_RISK,

    /**
     * 查询账单明细
     */
    LIST_RECORDS,

    /**
     * 删除指定账单
     */
    DELETE_RECORD,

    /**
     * 修改指定账单
     */
    UPDATE_RECORD,

    /**
     * 撤销最近一笔账单
     */
    UNDO_RECORD,

    CHAT,
    UNKNOWN
}
```

#### AgentIntentServiceImpl

```java
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

        // 3. 查询类
        if (isQueryMessage(message)) {
            return IntentType.QUERY_EXPENSE;
        }

        // 4. 分析类
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
}
```



AgentRecordManageHandler

```java
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
```



#### AgentServiceImpl

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.enums.IntentType;
import com.test.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentPlanService agentPlanService;

    @Autowired
    private AgentIntentService agentIntentService;

    @Autowired
    private AgentRecordHandler agentRecordHandler;

    @Autowired
    private AgentStatisticsHandler agentStatisticsHandler;

    @Autowired
    private AgentBudgetHandler agentBudgetHandler;

    @Autowired
    private AgentBudgetRiskHandler agentBudgetRiskHandler;

    @Autowired
    private AgentChatReplyService agentChatReplyService;

    @Autowired
    private AgentChatMessageService agentChatMessageService;

    @Autowired
    private AgentActionLogService agentActionLogService;

    @Autowired
    private AgentMemoryCacheService agentMemoryCacheService;

    @Autowired
    private AgentChatSessionService agentChatSessionService;

    @Autowired
    private AgentRecordManageHandler agentRecordManageHandler;


    @Override
    public AgentChatResponse chat(Long userId, String sessionId, String message){

        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "session_" + java.util.UUID.randomUUID().toString().replace("-", "");
        }

        List<AgentAction> actions = new ArrayList<>();

        // 1. 保存用户消息到 MySQL
        agentChatMessageService.saveMessage(
                userId,
                sessionId,
                "USER",
                message,
                null,
                null
        );

        // 2. 保存用户消息到 Redis
        agentMemoryCacheService.appendMessage(
                sessionId,
                "USER",
                message
        );

        // 3. AI 解析计划
        AgentPlan plan = agentPlanService.parsePlan(sessionId, message);

        // 4. 后端最终意图裁决
        IntentType finalIntent = agentIntentService.decideIntent(message, plan);

        AgentChatResponse response;

        switch (finalIntent) {
            case LIST_RECORDS:
                response = agentRecordManageHandler.handleList(userId, message, actions);
                break;

            case DELETE_RECORD:
                response = agentRecordManageHandler.handleDelete(userId, message, actions);
                break;

            case UPDATE_RECORD:
                response = agentRecordManageHandler.handleUpdate(userId, message, actions);
                break;

            case UNDO_RECORD:
                response = agentRecordManageHandler.handleUndo(userId, message, actions);
                break;

            case RECORD_EXPENSE:
                response = agentRecordHandler.handle(userId, sessionId, message, plan, actions);
                break;

            case QUERY_EXPENSE:
                response = agentStatisticsHandler.handleQuery(userId, message, actions);
                break;

            case ANALYZE_EXPENSE:
                response = agentStatisticsHandler.handleAnalyze(userId, message, actions);
                break;

            case SET_BUDGET:
                response = agentBudgetHandler.handleSetBudget(userId, message, plan, actions);
                break;

            case BUDGET_RISK:
                response = agentBudgetRiskHandler.handle(userId, message, actions);
                break;

            case CHAT:
            default:
                response = agentChatReplyService.chat(sessionId, message, actions);
                break;
        }

        // 5. 保存助手回复到 MySQL
        agentChatMessageService.saveMessage(
                userId,
                sessionId,
                "ASSISTANT",
                response.getReply(),
                plan == null ? null : plan.getIntent(),
                finalIntent == null ? null : finalIntent.name()
        );

        // 6. 保存助手回复到 Redis
        agentMemoryCacheService.appendMessage(
                sessionId,
                "ASSISTANT",
                response.getReply()
        );

        // 7. 保存 actions 到 MySQL
        agentActionLogService.saveActions(
                userId,
                sessionId,
                message,
                response.getActions()
        );
        // 8. 更新会话摘要
        agentChatSessionService.createOrUpdateSession(
                userId,
                sessionId,
                message,
                response.getReply()
        );

        // 返回前设置 sessionId
        response.setSessionId(sessionId);

        return response;
    }

}
```



### 5.24.1账单分页查询

#### PageResult

```java
package com.test.dto;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {

    private Long total;

    private Long pageNo;

    private Long pageSize;

    private Long pages;

    private List<T> records;
}
```

#### ExpenseRecordQueryVO

```java
package com.test.dto;

import lombok.Data;

@Data
public class ExpenseRecordQueryVO {

    /**
     * today / month / all
     */
    private String range;

    /**
     * EXPENSE / INCOME
     */
    private String recordType;

    /**
     * 餐饮、饮品、交通等
     */
    private String category;

    /**
     * 当前页
     */
    private Long pageNo = 1L;

    /**
     * 每页大小
     */
    private Long pageSize = 10L;
}
```

#### ExpenseRecordService

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.CategoryAmountDTO;
import com.test.dto.ExpenseRecordDTO;
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
```



#### ExpenseRecordServiceImpl

```java
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
```



#### MybatisPlusConfig

```java
package com.test.common;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        return interceptor;
    }
}
```



#### ExpenseRecordController

```java
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
```

> 测试
>
> ```
> /expense/records/page?range=month&pageNo=1&pageSize=5
> ```
>
> 
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "total": 11,
>     "pageNo": 1,
>     "pageSize": 5,
>     "pages": 3,
>     "records": [
>       {
>         "id": 14,
>         "amount": 18,
>         "category": "饮品",
>         "recordType": "EXPENSE",
>         "description": "奶茶",
>         "expenseTime": "2026-06-02",
>         "expenseDatetime": "2026-06-02T11:06:14",
>         "sourceType": "AGENT",
>         "sourceText": "今天奶茶18，午饭28，打车22",
>         "sessionId": "session_d8db15b13bae468993cfc67ff3313be2"
>       },
>       {
>         "id": 13,
>         "amount": 115,
>         "category": "餐饮",
>         "recordType": "EXPENSE",
>         "description": "海底捞",
>         "expenseTime": "2026-06-02",
>         "expenseDatetime": "2026-06-02T10:38:54",
>         "sourceType": "AGENT",
>         "sourceText": "今天海底捞115",
>         "sessionId": "session_2d947ddf1b064a8999cc3a57c3f335fd"
>       },
>       {
>         "id": 12,
>         "amount": 5000,
>         "category": "工资",
>         "recordType": "INCOME",
>         "description": "工资到账",
>         "expenseTime": "2026-06-01",
>         "expenseDatetime": "2026-06-01T21:55:36",
>         "sourceType": "AGENT",
>         "sourceText": "昨天不是工资到账5000吗？",
>         "sessionId": "session_d8db15b13bae468993cfc67ff3313be2"
>       },
>       {
>         "id": 10,
>         "amount": 18,
>         "category": "饮品",
>         "recordType": "EXPENSE",
>         "description": "奶茶",
>         "expenseTime": "2026-06-01",
>         "expenseDatetime": "2026-06-01T21:41:20",
>         "sourceType": "AGENT",
>         "sourceText": "今天奶茶18",
>         "sessionId": "session_d8db15b13bae468993cfc67ff3313be2"
>       },
>       {
>         "id": 9,
>         "amount": 18,
>         "category": "饮品",
>         "recordType": "EXPENSE",
>         "description": "奶茶",
>         "expenseTime": "2026-06-01",
>         "expenseDatetime": "2026-06-01T21:05:00",
>         "sourceType": "AGENT",
>         "sourceText": "今天奶茶18",
>         "sessionId": "session_test_backend_001"
>       }
>     ]
>   }
> }
> ```
>
> 

## 6.技术升级

### 6.1.1RabbitMQ记账事件

#### pom

```
<!-- RabbitMQ -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```



#### application

前提：本地mq服务启动

```
server:
  port: 8181
  servlet:
    encoding:
      charset: UTF-8
      force: true

spring:
  application:
    name: Butler
  datasource:
    url: jdbc:mysql://localhost:3306/bulter_agent?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      timeout: 3000ms
  rabbitmq:
      host: 192.168.59.129
      port: 5672
      username: guest
      password: guest
      virtual-host: /

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

langchain4j:
  community:
    dashscope:
      chat-model:
        api-key: ${AI_API_KEY}
        model-name: qwen-long
      embedding-model:
        api-key: ${AI_API_KEY}
        model-name: text-embedding-v4
      streaming-chat-model:
        api-key: ${AI_API_KEY}
        model-name: qwen-long

knife4j:
  enable: true
  setting:
    language: zh_cn

springdoc:
  group-configs:
    - group: 默认接口
      paths-to-match: /**
      packages-to-scan: com.test.controller

```

#### RabbitMqConstants

常量类

```java
package com.test.mq;

public class RabbitMqConstants {

    /**
     * 记账事件交换机
     */
    public static final String EXPENSE_EXCHANGE = "butler.expense.exchange";

    /**
     * 记账成功队列
     */
    public static final String EXPENSE_RECORD_CREATED_QUEUE = "butler.expense.record.created.queue";

    /**
     * 记账成功路由键
     */
    public static final String EXPENSE_RECORD_CREATED_ROUTING_KEY = "expense.record.created";
}
```



#### RabbitMqConfig

mq配置类

```java
package com.test.config;

import com.test.mq.RabbitMqConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    /**
     * JSON 消息转换器
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 生产者使用 JSON 转换器
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }

    /**
     * 消费者使用 JSON 转换器
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        return factory;
    }

    @Bean
    public DirectExchange expenseExchange() {
        return new DirectExchange(
                RabbitMqConstants.EXPENSE_EXCHANGE,
                true,
                false
        );
    }

    @Bean
    public Queue expenseRecordCreatedQueue() {
        return QueueBuilder
                .durable(RabbitMqConstants.EXPENSE_RECORD_CREATED_QUEUE)
                .build();
    }

    @Bean
    public Binding expenseRecordCreatedBinding() {
        return BindingBuilder
                .bind(expenseRecordCreatedQueue())
                .to(expenseExchange())
                .with(RabbitMqConstants.EXPENSE_RECORD_CREATED_ROUTING_KEY);
    }
}
```



#### ExpenseRecordCreatedEvent

事件对象

```java
package com.test.mq.event;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExpenseRecordCreatedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long recordId;

    private Long userId;

    private String sessionId;

    /**
     * EXPENSE / INCOME
     */
    private String recordType;

    private String category;

    private BigDecimal amount;

    private String description;

    private String sourceText;

    private LocalDateTime eventTime;
}
```



#### ExpenseRecordEventProducer

消息发送器

```java
package com.test.mq.producer;

import com.test.mq.RabbitMqConstants;
import com.test.mq.event.ExpenseRecordCreatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExpenseRecordEventProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendRecordCreatedEvent(ExpenseRecordCreatedEvent event) {

        if (event == null) {
            return;
        }

        rabbitTemplate.convertAndSend(
                RabbitMqConstants.EXPENSE_EXCHANGE,
                RabbitMqConstants.EXPENSE_RECORD_CREATED_ROUTING_KEY,
                event
        );

        System.out.println("已发送记账成功 MQ 消息：" + event);
    }
}
```

#### ExpenseRecordEventConsumer

创建消费者

```java
package com.test.mq.consumer;

import com.test.mq.RabbitMqConstants;
import com.test.mq.event.ExpenseRecordCreatedEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ExpenseRecordEventConsumer {

    @RabbitListener(queues = RabbitMqConstants.EXPENSE_RECORD_CREATED_QUEUE)
    public void handleRecordCreatedEvent(ExpenseRecordCreatedEvent event) {

        System.out.println("收到记账成功 MQ 消息：");
        System.out.println("recordId = " + event.getRecordId());
        System.out.println("userId = " + event.getUserId());
        System.out.println("recordType = " + event.getRecordType());
        System.out.println("category = " + event.getCategory());
        System.out.println("amount = " + event.getAmount());
        System.out.println("description = " + event.getDescription());
    }
}
```



#### AgentRecordHandler

修改调度器

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentExpenseItem;
import com.test.dto.AgentPlan;
import com.test.dto.BudgetWarningResult;
import com.test.mq.event.ExpenseRecordCreatedEvent;
import com.test.mq.producer.ExpenseRecordEventProducer;
import com.test.service.BudgetWarningService;
import com.test.service.ExpenseRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentRecordHandler {

    @Autowired
    private BudgetWarningService budgetWarningService;

    @Autowired
    private ExpenseRecordService expenseRecordService;

    @Autowired
    private ExpenseRecordEventProducer expenseRecordEventProducer;

    public AgentChatResponse handle(Long userId,
                                    String sessionId,
                                    String message,
                                    AgentPlan plan,
                                    List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        // 1. 判断 AI 是否解析成功
        if (plan == null || !Boolean.TRUE.equals(plan.getValid())) {
            response.setReply("我没有识别出完整的记账信息，请换一种说法，例如：今天奶茶18。");
            response.setActions(actions);
            return response;
        }

        // 2. 构建待保存的记录列表
        // 优先使用 plan.expenses；如果没有，则兼容旧的单笔字段
        List<AgentExpenseItem> recordItems = buildRecordItems(plan);

        if (recordItems == null || recordItems.isEmpty()) {
            response.setReply("我识别到你想记账，但没有识别到有效金额，请补充金额。");
            response.setActions(actions);
            return response;
        }

        // 3. 循环保存多笔记录
        List<Long> recordIds = new ArrayList<>();
        List<AgentExpenseItem> savedItems = new ArrayList<>();

        for (AgentExpenseItem item : recordItems) {

            if (item == null) {
                continue;
            }

            if (item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String recordType = normalizeRecordType(item.getRecordType());
            String category = normalizeCategory(item.getCategory());
            String description = normalizeDescription(item.getDescription());

            Long recordId = expenseRecordService.createRecord(
                    userId,
                    item.getAmount(),
                    category,
                    recordType,
                    description,
                    LocalDate.now(),
                    "AGENT",
                    message,
                    sessionId
            );


            ExpenseRecordCreatedEvent event = new ExpenseRecordCreatedEvent();
            event.setRecordId(recordId);
            event.setUserId(userId);
            event.setSessionId(sessionId);
            event.setRecordType(recordType);
            event.setCategory(category);
            event.setAmount(item.getAmount());
            event.setDescription(description);
            event.setSourceText(message);
            event.setEventTime(LocalDateTime.now());

            expenseRecordEventProducer.sendRecordCreatedEvent(event);

            recordIds.add(recordId);

            AgentExpenseItem savedItem = new AgentExpenseItem();
            savedItem.setAmount(item.getAmount());
            savedItem.setCategory(category);
            savedItem.setRecordType(recordType);
            savedItem.setDescription(description);

            savedItems.add(savedItem);
        }

        if (savedItems.isEmpty()) {
            response.setReply("我识别到你想记账，但没有识别到有效金额，请补充金额。");
            response.setActions(actions);
            return response;
        }

        // 4. 添加记账 action
        AgentAction recordAction = new AgentAction();

        if (savedItems.size() == 1) {
            AgentExpenseItem item = savedItems.get(0);

            if ("INCOME".equalsIgnoreCase(item.getRecordType())) {
                recordAction.setName("recordIncome");
                recordAction.setMessage("收入记录已保存，记录ID：" + recordIds.get(0));
            } else {
                recordAction.setName("recordExpense");
                recordAction.setMessage("支出记录已保存，记录ID：" + recordIds.get(0));
            }
        } else {
            recordAction.setName("recordBatch");
            recordAction.setMessage("多笔记账成功，共保存" + savedItems.size() + "条记录，记录ID：" + recordIds);
        }

        recordAction.setSuccess(true);
        actions.add(recordAction);

        // 5. 生成基础回复
        String reply = buildReply(savedItems);

        // 6. 保存完所有记录后，再统一检查预算预警
        List<BudgetWarningResult> warningResults = checkBudgetWarnings(userId, savedItems);

        if (warningResults != null && !warningResults.isEmpty()) {
            for (BudgetWarningResult warningResult : warningResults) {
                reply = reply + warningResult.getMessage();

                AgentAction warningAction = new AgentAction();
                warningAction.setName("budgetWarning");
                warningAction.setSuccess(true);
                warningAction.setMessage("触发预算预警，分类："
                        + warningResult.getCategory()
                        + "，风险等级："
                        + warningResult.getLevel());

                actions.add(warningAction);
            }
        }

        // 7. 一定要设置 reply
        response.setReply(reply);
        response.setActions(actions);

        return response;
    }

    /**
     * 优先使用 AI 返回的 expenses。
     * 如果 expenses 为空，则兼容旧版单笔字段。
     */
    private List<AgentExpenseItem> buildRecordItems(AgentPlan plan) {

        List<AgentExpenseItem> items = plan.getExpenses();

        if (items != null && !items.isEmpty()) {
            return items;
        }

        List<AgentExpenseItem> fallbackItems = new ArrayList<>();

        AgentExpenseItem item = new AgentExpenseItem();
        item.setAmount(plan.getAmount());
        item.setCategory(plan.getCategory());
        item.setRecordType(plan.getRecordType());
        item.setDescription(plan.getDescription());

        fallbackItems.add(item);

        return fallbackItems;
    }

    /**
     * 多笔记账后统一检查预算预警。
     * 这里会检查：
     * 1. 总预算 TOTAL
     * 2. 本次涉及到的分类预算
     *
     * 用 Map 去重，避免多笔同类支出重复提示。
     */
    private List<BudgetWarningResult> checkBudgetWarnings(Long userId,
                                                          List<AgentExpenseItem> savedItems) {

        Map<String, BudgetWarningResult> warningMap = new LinkedHashMap<>();

        for (AgentExpenseItem item : savedItems) {

            if (!"EXPENSE".equalsIgnoreCase(item.getRecordType())) {
                continue;
            }

            List<BudgetWarningResult> results = budgetWarningService.checkAfterExpense(
                    userId,
                    item.getCategory()
            );

            if (results == null || results.isEmpty()) {
                continue;
            }

            for (BudgetWarningResult result : results) {
                if (result == null || !Boolean.TRUE.equals(result.getWarning())) {
                    continue;
                }

                String key = result.getCategory() + "_" + result.getLevel();
                warningMap.putIfAbsent(key, result);
            }
        }

        return new ArrayList<>(warningMap.values());
    }

    /**
     * 生成回复文案
     */
    private String buildReply(List<AgentExpenseItem> savedItems) {

        if (savedItems.size() == 1) {
            AgentExpenseItem item = savedItems.get(0);

            String typeText = "INCOME".equalsIgnoreCase(item.getRecordType()) ? "收入" : "支出";

            return "好嘞，已帮你记下一笔"
                    + typeText
                    + "："
                    + item.getCategory()
                    + item.getAmount()
                    + "元，"
                    + item.getDescription()
                    + "。";
        }

        StringBuilder reply = new StringBuilder();

        reply.append("好嘞，已帮你记下")
                .append(savedItems.size())
                .append("笔记录：");

        for (int i = 0; i < savedItems.size(); i++) {
            AgentExpenseItem item = savedItems.get(i);

            String typeText = "INCOME".equalsIgnoreCase(item.getRecordType()) ? "收入" : "支出";

            reply.append(typeText)
                    .append(item.getCategory())
                    .append(item.getAmount())
                    .append("元");

            if (i < savedItems.size() - 1) {
                reply.append("、");
            } else {
                reply.append("。");
            }
        }

        return reply.toString();
    }

    private String normalizeRecordType(String recordType) {
        if ("INCOME".equalsIgnoreCase(recordType)) {
            return "INCOME";
        }
        return "EXPENSE";
    }

    private String normalizeCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return "其他";
        }
        return category.trim();
    }

    private String normalizeDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return "日常记录";
        }
        return description.trim();
    }
}
```



> 测试
>
> ```
> {
>   "sessionId": "session_d8db15b13bae468993cfc67ff3313be2",
>   "message": "今天晚饭猪脚饭14"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "sessionId": "session_d8db15b13bae468993cfc67ff3313be2",
>     "reply": "好嘞，已帮你记下一笔支出：餐饮14元，晚饭猪脚饭。另外提醒一下，你本月总预算已经超支，当前已支出 527.00 元，预算为 500.00 元。另外提醒一下，你本月餐饮预算已经超支，当前已支出 401.00 元，预算为 100.00 元。",
>     "actions": [
>       {
>         "name": "recordExpense",
>         "success": true,
>         "message": "支出记录已保存，记录ID：21"
>       },
>       {
>         "name": "budgetWarning",
>         "success": true,
>         "message": "触发预算预警，分类：TOTAL，风险等级：OVER"
>       },
>       {
>         "name": "budgetWarning",
>         "success": true,
>         "message": "触发预算预警，分类：餐饮，风险等级：OVER"
>       }
>     ]
>   }
> }
> ```



### 6.1.2预算预警从同步改成MQ异步

#### AgentRecordHandler

修改AgentRecordHandler



```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentExpenseItem;
import com.test.dto.AgentPlan;
import com.test.dto.BudgetWarningResult;
import com.test.mq.event.ExpenseRecordCreatedEvent;
import com.test.mq.producer.ExpenseRecordEventProducer;
import com.test.service.BudgetWarningService;
import com.test.service.ExpenseRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentRecordHandler {

    @Autowired
    private BudgetWarningService budgetWarningService;

    @Autowired
    private ExpenseRecordService expenseRecordService;

    @Autowired
    private ExpenseRecordEventProducer expenseRecordEventProducer;

    public AgentChatResponse handle(Long userId,
                                    String sessionId,
                                    String message,
                                    AgentPlan plan,
                                    List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        // 1. 判断 AI 是否解析成功
        if (plan == null || !Boolean.TRUE.equals(plan.getValid())) {
            response.setReply("我没有识别出完整的记账信息，请换一种说法，例如：今天奶茶18。");
            response.setActions(actions);
            return response;
        }

        // 2. 构建待保存的记录列表
        // 优先使用 plan.expenses；如果没有，则兼容旧的单笔字段
        List<AgentExpenseItem> recordItems = buildRecordItems(plan);

        if (recordItems == null || recordItems.isEmpty()) {
            response.setReply("我识别到你想记账，但没有识别到有效金额，请补充金额。");
            response.setActions(actions);
            return response;
        }

        // 3. 循环保存多笔记录
        List<Long> recordIds = new ArrayList<>();
        List<AgentExpenseItem> savedItems = new ArrayList<>();

        for (AgentExpenseItem item : recordItems) {

            if (item == null) {
                continue;
            }

            if (item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String recordType = normalizeRecordType(item.getRecordType());
            String category = normalizeCategory(item.getCategory());
            String description = normalizeDescription(item.getDescription());

            Long recordId = expenseRecordService.createRecord(
                    userId,
                    item.getAmount(),
                    category,
                    recordType,
                    description,
                    LocalDate.now(),
                    "AGENT",
                    message,
                    sessionId
            );


            ExpenseRecordCreatedEvent event = new ExpenseRecordCreatedEvent();
            event.setRecordId(recordId);
            event.setUserId(userId);
            event.setSessionId(sessionId);
            event.setRecordType(recordType);
            event.setCategory(category);
            event.setAmount(item.getAmount());
            event.setDescription(description);
            event.setSourceText(message);
            event.setEventTime(LocalDateTime.now());

            expenseRecordEventProducer.sendRecordCreatedEvent(event);

            recordIds.add(recordId);

            AgentExpenseItem savedItem = new AgentExpenseItem();
            savedItem.setAmount(item.getAmount());
            savedItem.setCategory(category);
            savedItem.setRecordType(recordType);
            savedItem.setDescription(description);

            savedItems.add(savedItem);
        }

        if (savedItems.isEmpty()) {
            response.setReply("我识别到你想记账，但没有识别到有效金额，请补充金额。");
            response.setActions(actions);
            return response;
        }

        // 4. 添加记账 action
        AgentAction recordAction = new AgentAction();

        if (savedItems.size() == 1) {
            AgentExpenseItem item = savedItems.get(0);

            if ("INCOME".equalsIgnoreCase(item.getRecordType())) {
                recordAction.setName("recordIncome");
                recordAction.setMessage("收入记录已保存，记录ID：" + recordIds.get(0));
            } else {
                recordAction.setName("recordExpense");
                recordAction.setMessage("支出记录已保存，记录ID：" + recordIds.get(0));
            }
        } else {
            recordAction.setName("recordBatch");
            recordAction.setMessage("多笔记账成功，共保存" + savedItems.size() + "条记录，记录ID：" + recordIds);
        }

        recordAction.setSuccess(true);
        actions.add(recordAction);

        // 5. 生成基础回复
        String reply = buildReply(savedItems);

        // 6. 保存完所有记录后，再统一检查预算预警
//        List<BudgetWarningResult> warningResults = checkBudgetWarnings(userId, savedItems);
//
//        if (warningResults != null && !warningResults.isEmpty()) {
//            for (BudgetWarningResult warningResult : warningResults) {
//                reply = reply + warningResult.getMessage();
//
//                AgentAction warningAction = new AgentAction();
//                warningAction.setName("budgetWarning");
//                warningAction.setSuccess(true);
//                warningAction.setMessage("触发预算预警，分类："
//                        + warningResult.getCategory()
//                        + "，风险等级："
//                        + warningResult.getLevel());
//
//                actions.add(warningAction);
//            }
//        }

        // 7. 一定要设置 reply
        response.setReply(reply);
        response.setActions(actions);

        return response;
    }

    /**
     * 优先使用 AI 返回的 expenses。
     * 如果 expenses 为空，则兼容旧版单笔字段。
     */
    private List<AgentExpenseItem> buildRecordItems(AgentPlan plan) {

        List<AgentExpenseItem> items = plan.getExpenses();

        if (items != null && !items.isEmpty()) {
            return items;
        }

        List<AgentExpenseItem> fallbackItems = new ArrayList<>();

        AgentExpenseItem item = new AgentExpenseItem();
        item.setAmount(plan.getAmount());
        item.setCategory(plan.getCategory());
        item.setRecordType(plan.getRecordType());
        item.setDescription(plan.getDescription());

        fallbackItems.add(item);

        return fallbackItems;
    }

    /**
     * 多笔记账后统一检查预算预警。
     * 这里会检查：
     * 1. 总预算 TOTAL
     * 2. 本次涉及到的分类预算
     *
     * 用 Map 去重，避免多笔同类支出重复提示。
     */
    private List<BudgetWarningResult> checkBudgetWarnings(Long userId,
                                                          List<AgentExpenseItem> savedItems) {

        Map<String, BudgetWarningResult> warningMap = new LinkedHashMap<>();

        for (AgentExpenseItem item : savedItems) {

            if (!"EXPENSE".equalsIgnoreCase(item.getRecordType())) {
                continue;
            }

            List<BudgetWarningResult> results = budgetWarningService.checkAfterExpense(
                    userId,
                    item.getCategory()
            );

            if (results == null || results.isEmpty()) {
                continue;
            }

            for (BudgetWarningResult result : results) {
                if (result == null || !Boolean.TRUE.equals(result.getWarning())) {
                    continue;
                }

                String key = result.getCategory() + "_" + result.getLevel();
                warningMap.putIfAbsent(key, result);
            }
        }

        return new ArrayList<>(warningMap.values());
    }

    /**
     * 生成回复文案
     */
    private String buildReply(List<AgentExpenseItem> savedItems) {

        if (savedItems.size() == 1) {
            AgentExpenseItem item = savedItems.get(0);

            String typeText = "INCOME".equalsIgnoreCase(item.getRecordType()) ? "收入" : "支出";

            return "好嘞，已帮你记下一笔"
                    + typeText
                    + "："
                    + item.getCategory()
                    + item.getAmount()
                    + "元，"
                    + item.getDescription()
                    + "。";
        }

        StringBuilder reply = new StringBuilder();

        reply.append("好嘞，已帮你记下")
                .append(savedItems.size())
                .append("笔记录：");

        for (int i = 0; i < savedItems.size(); i++) {
            AgentExpenseItem item = savedItems.get(i);

            String typeText = "INCOME".equalsIgnoreCase(item.getRecordType()) ? "收入" : "支出";

            reply.append(typeText)
                    .append(item.getCategory())
                    .append(item.getAmount())
                    .append("元");

            if (i < savedItems.size() - 1) {
                reply.append("、");
            } else {
                reply.append("。");
            }
        }

        return reply.toString();
    }

    private String normalizeRecordType(String recordType) {
        if ("INCOME".equalsIgnoreCase(recordType)) {
            return "INCOME";
        }
        return "EXPENSE";
    }

    private String normalizeCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return "其他";
        }
        return category.trim();
    }

    private String normalizeDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return "日常记录";
        }
        return description.trim();
    }
}
```



#### ExpenseRecordEventConsumer

```java
package com.test.mq.consumer;

import com.test.dto.BudgetWarningResult;
import com.test.mq.RabbitMqConstants;
import com.test.mq.event.ExpenseRecordCreatedEvent;
import com.test.service.BudgetWarningService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExpenseRecordEventConsumer {

    @Autowired
    private BudgetWarningService budgetWarningService;

    @RabbitListener(
            queues = RabbitMqConstants.EXPENSE_RECORD_CREATED_QUEUE,
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void handleRecordCreatedEvent(ExpenseRecordCreatedEvent event) {

        if (event == null) {
            return;
        }

        System.out.println("收到记账成功 MQ 消息：recordId=" + event.getRecordId()
                + ", userId=" + event.getUserId()
                + ", recordType=" + event.getRecordType()
                + ", category=" + event.getCategory()
                + ", amount=" + event.getAmount());

        if (!"EXPENSE".equalsIgnoreCase(event.getRecordType())) {
            return;
        }

        List<BudgetWarningResult> warningResults = budgetWarningService.checkAfterExpense(
                event.getUserId(),
                event.getCategory()
        );

        if (warningResults == null || warningResults.isEmpty()) {
            System.out.println("本次记账未触发预算预警，recordId=" + event.getRecordId());
            return;
        }

        for (BudgetWarningResult warningResult : warningResults) {
            if (warningResult == null || !Boolean.TRUE.equals(warningResult.getWarning())) {
                continue;
            }

            System.out.println("异步预算预警触发：recordId=" + event.getRecordId()
                    + ", category=" + warningResult.getCategory()
                    + ", level=" + warningResult.getLevel()
                    + ", message=" + warningResult.getMessage());
        }
    }
}
```



> 测试
>
> ```
> {
>   "sessionId": "session_d8db15b13bae468993cfc67ff3313be2",
>   "message": "今天晚饭猪脚饭14"
> }
> ```
>
> ```
> 异步预算预警触发：recordId=22, category=TOTAL, level=OVER, message=另外提醒一下，你本月总预算已经超支，当前已支出 541.00 元，预算为 500.00 元。
> 异步预算预警触发：recordId=22, category=餐饮, level=OVER, message=另外提醒一下，你本月餐饮预算已经超支，当前已支出 415.00 元，预算为 100.00 元。
> 
> ```
>
> 
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "sessionId": "session_d8db15b13bae468993cfc67ff3313be2",
>     "reply": "好嘞，已帮你记下一笔支出：餐饮14元，晚饭猪脚饭。",
>     "actions": [
>       {
>         "name": "recordExpense",
>         "success": true,
>         "message": "支出记录已保存，记录ID：22"
>       }
>     ]
>   }
> }
> ```
>
> 

### 6.2.1SSE流式输出

#### AIService

```java
package com.test.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface AIService {

    /**
     * 1. 解析用户意图，返回 JSON
     */
    @SystemMessage(fromResource = "agent-plan-prompt.txt")
    String plan(@MemoryId String sessionId, @UserMessage String message);

    /**
     * 2. 普通闲聊，一次性返回完整回复
     */
    @SystemMessage(fromResource = "system-prompt.txt")
    String chat(@MemoryId String sessionId, @UserMessage String message);

    /**
     * 3. 查询 / 统计结果润色
     */
    @SystemMessage(fromResource = "agent-reply-prompt.txt")
    String generateReply(@MemoryId String sessionId, @UserMessage String context);

    /**
     * 4. 普通闲聊，流式返回
     */
    @SystemMessage(fromResource = "system-prompt.txt")
    TokenStream streamChat(@MemoryId String sessionId, @UserMessage String message);
}
```





#### AgentServiceFactory

```java
package com.test.config;

import com.test.service.AIService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentServiceFactory {

    @Bean
    public AIService aiService(ChatModel chatModel,
                               StreamingChatModel streamingChatModel) {

        return AiServices.builder(AIService.class)

                // 普通一次性回复模型
                .chatModel(chatModel)

                // 流式回复模型
                .streamingChatModel(streamingChatModel)

                // 会话记忆
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.withMaxMessages(10)
                )

                .build();
    }
}
```



#### AgentStreamService

```java
package com.test.service.impl;

import com.test.service.AIService;
import com.test.service.AgentChatMessageService;
import com.test.service.AgentChatSessionService;
import com.test.service.AgentMemoryCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

@Service
public class AgentStreamService {

    @Autowired
    private AIService aiService;

    @Autowired
    private AgentChatMessageService agentChatMessageService;

    @Autowired
    private AgentMemoryCacheService agentMemoryCacheService;

    @Autowired
    private AgentChatSessionService agentChatSessionService;

    public SseEmitter streamChat(Long userId,
                                 String sessionId,
                                 String message) {

        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "session_" + UUID.randomUUID().toString().replace("-", "");
        }

        String finalSessionId = sessionId;

        SseEmitter emitter = new SseEmitter(60_000L);

        StringBuilder fullReply = new StringBuilder();

        try {
            // 1. 先把 sessionId 返回给前端
            emitter.send(SseEmitter.event()
                    .name("session")
                    .data(finalSessionId));

            // 2. 保存用户消息到 MySQL
            agentChatMessageService.saveMessage(
                    userId,
                    finalSessionId,
                    "USER",
                    message,
                    null,
                    "CHAT_STREAM"
            );

            // 3. 保存用户消息到 Redis
            agentMemoryCacheService.appendMessage(
                    finalSessionId,
                    "USER",
                    message
            );

            // 4. 调用流式 AI
            aiService.streamChat(finalSessionId, message)
                    .onPartialResponse(partialResponse -> {
                        try {
                            fullReply.append(partialResponse);

                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(partialResponse));

                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .onCompleteResponse(chatResponse -> {
                        try {
                            String reply = fullReply.toString();

                            // 5. 保存助手回复到 MySQL
                            agentChatMessageService.saveMessage(
                                    userId,
                                    finalSessionId,
                                    "ASSISTANT",
                                    reply,
                                    "CHAT",
                                    "CHAT_STREAM"
                            );

                            // 6. 保存助手回复到 Redis
                            agentMemoryCacheService.appendMessage(
                                    finalSessionId,
                                    "ASSISTANT",
                                    reply
                            );

                            // 7. 更新会话列表
                            agentChatSessionService.createOrUpdateSession(
                                    userId,
                                    finalSessionId,
                                    message,
                                    reply
                            );

                            emitter.send(SseEmitter.event()
                                    .name("done")
                                    .data("[DONE]"));

                            emitter.complete();

                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .onError(throwable -> {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data("流式回复失败：" + throwable.getMessage()));
                        } catch (IOException ignored) {
                        }

                        emitter.complete();
                    })
                    .start();

        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("流式接口异常：" + e.getMessage()));
            } catch (IOException ignored) {
            }

            emitter.complete();
        }

        return emitter;
    }
}
```



#### BulterAgentController

```java
package com.test.controller;

import com.test.common.UserContext;
import com.test.dto.AgentChatMessageDTO;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentChatSessionDTO;
import com.test.dto.AgentChatVO;
import com.test.service.AgentChatMessageService;
import com.test.service.AgentChatSessionService;
import com.test.service.AgentMemoryCacheService;
import com.test.service.AgentService;
import com.test.service.impl.AgentStreamService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/agent")
public class BulterAgentController {

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentChatMessageService agentChatMessageService;

    @Autowired
    private AgentChatSessionService agentChatSessionService;

    @Autowired
    private AgentMemoryCacheService agentMemoryCacheService;

    @Autowired
    private AgentStreamService agentStreamService;

    @PostMapping("/chat")
    public ResultVO chat(@RequestBody AgentChatVO chatmessage) {
//        使用token拦截器获取userid
        Long userId = UserContext.getUserId();

        AgentChatResponse chat = this.agentService.chat(
                userId,
                chatmessage.getSessionId(),
                chatmessage.getMessage()
        );

        return ResultVOUtil.success(chat);
    }
    @GetMapping("/history/{sessionId}")
    public ResultVO<List<AgentChatMessageDTO>> history(@PathVariable String sessionId) {

        Long userId = UserContext.getUserId();

        List<AgentChatMessageDTO> list = agentChatMessageService.listBySession(userId, sessionId);

        return ResultVOUtil.success(list);
    }

    @GetMapping("/sessions")
    public ResultVO<List<AgentChatSessionDTO>> sessions() {

        Long userId = UserContext.getUserId();

        List<AgentChatSessionDTO> list = agentChatSessionService.listUserSessions(userId);

        return ResultVOUtil.success(list);
    }
    @DeleteMapping("/session/{sessionId}")
    public ResultVO deleteSession(@PathVariable String sessionId) {

        Long userId = UserContext.getUserId();

        // 1. 删除会话表
        agentChatSessionService.deleteSession(userId, sessionId);

        // 2. 删除消息表
        agentChatMessageService.deleteBySession(userId, sessionId);

        // 3. 删除 Redis 短期记忆
        agentMemoryCacheService.deleteMemory(sessionId);

        return ResultVOUtil.success("会话删除成功");
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody AgentChatVO chatMessage) {

        Long userId = UserContext.getUserId();

        return agentStreamService.streamChat(
                userId,
                chatMessage.getSessionId(),
                chatMessage.getMessage()
        );
    }
}
```



> 测试
>
> ```
> event:session
> data:session_d8db15b13bae468993cfc67ff3313be2
> 
> event:message
> data:你好
> 
> event:message
> data:呀，我是
> 
> event:message
> data:小布，
> 
> event:message
> data:你的 AI
> 
> event:message
> data: 记账管家～  
> data:能
> 
> event:message
> data:帮你记账、查消费
> 
> event:message
> data:、分析花销，还能
> 
> event:message
> data:管预算。  
> data:有需要
> 
> event:message
> data:随时喊我，咱们一起
> 
> event:message
> data:把钱理得明明白
> 
> event:message
> data:白 😊
> 
> event:done
> data:[DONE]
> 
> 
> ```
>
> 

### 6.2.2Resdis接口限流

#### AgentRateLimitService

```
package com.test.service;

public interface AgentRateLimitService {

    boolean tryAcquire(Long userId, String scene, int limit, long ttlSeconds);
}
```

#### AgentRateLimitServiceImpl

```java
package com.test.service.impl;

import com.test.service.AgentRateLimitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AgentRateLimitServiceImpl implements AgentRateLimitService {

    private static final String KEY_PREFIX = "rate:agent:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean tryAcquire(Long userId, String scene, int limit, long ttlSeconds) {

        if (userId == null) {
            return false;
        }

        if (scene == null || scene.trim().isEmpty()) {
            scene = "default";
        }

        String key = KEY_PREFIX + scene + ":" + userId;

        Long count = stringRedisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
        }

        return count != null && count <= limit;
    }
}
```



#### BulterAgentController

```
package com.test.controller;

import com.test.common.UserContext;
import com.test.dto.AgentChatMessageDTO;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentChatSessionDTO;
import com.test.dto.AgentChatVO;
import com.test.service.*;
import com.test.service.impl.AgentStreamService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/agent")
public class BulterAgentController {

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentChatMessageService agentChatMessageService;

    @Autowired
    private AgentChatSessionService agentChatSessionService;

    @Autowired
    private AgentMemoryCacheService agentMemoryCacheService;

    @Autowired
    private AgentStreamService agentStreamService;

    @Autowired
    private AgentRateLimitService agentRateLimitService;

    @PostMapping("/chat")
    public ResultVO chat(@RequestBody AgentChatVO chatmessage) {

        Long userId = UserContext.getUserId();

        boolean allowed = agentRateLimitService.tryAcquire(
                userId,
                "chat",
                10,
                60
        );

        if (!allowed) {
            return ResultVOUtil.fail("请求太频繁，请稍后再试");
        }

        AgentChatResponse chat = this.agentService.chat(
                userId,
                chatmessage.getSessionId(),
                chatmessage.getMessage()
        );

        return ResultVOUtil.success(chat);
    }
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody AgentChatVO chatMessage) {

        Long userId = UserContext.getUserId();

        boolean allowed = agentRateLimitService.tryAcquire(
                userId,
                "chat",
                10,
                60
        );

        if (!allowed) {
            SseEmitter emitter = new SseEmitter();

            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("请求太频繁，请稍后再试"));
            } catch (Exception ignored) {
            }

            emitter.complete();

            return emitter;
        }

        return agentStreamService.streamChat(
                userId,
                chatMessage.getSessionId(),
                chatMessage.getMessage()
        );
    }

    @GetMapping("/history/{sessionId}")
    public ResultVO<List<AgentChatMessageDTO>> history(@PathVariable String sessionId) {

        Long userId = UserContext.getUserId();

        List<AgentChatMessageDTO> list = agentChatMessageService.listBySession(userId, sessionId);

        return ResultVOUtil.success(list);
    }

    @GetMapping("/sessions")
    public ResultVO<List<AgentChatSessionDTO>> sessions() {

        Long userId = UserContext.getUserId();

        List<AgentChatSessionDTO> list = agentChatSessionService.listUserSessions(userId);

        return ResultVOUtil.success(list);
    }
    @DeleteMapping("/session/{sessionId}")
    public ResultVO deleteSession(@PathVariable String sessionId) {

        Long userId = UserContext.getUserId();

        // 1. 删除会话表
        agentChatSessionService.deleteSession(userId, sessionId);

        // 2. 删除消息表
        agentChatMessageService.deleteBySession(userId, sessionId);

        // 3. 删除 Redis 短期记忆
        agentMemoryCacheService.deleteMemory(sessionId);

        return ResultVOUtil.success("会话删除成功");
    }


}
```

> 测试
>
> ```
> event:error
> data:请求太频繁，请稍后再试
> 
> ```



### 6.2.3Redis 防重复提交 / 幂等控制

#### AgentRequestDedupService

```java
package com.test.service;

public interface AgentRequestDedupService {

    boolean isDuplicate(Long userId, String sessionId, String message);
}
```



#### AgentRequestDedupServiceImpl

```java
package com.test.service.impl;

import com.test.service.AgentRequestDedupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

@Service
public class AgentRequestDedupServiceImpl implements AgentRequestDedupService {

    private static final String KEY_PREFIX = "dedup:agent:";

    /**
     * 5 秒内相同请求视为重复提交
     */
    private static final Duration TTL = Duration.ofSeconds(5);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean isDuplicate(Long userId, String sessionId, String message) {

        if (userId == null) {
            return false;
        }

        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "default";
        }

        String raw = userId + ":" + sessionId + ":" + message.trim();
        String hash = md5(raw);

        String key = KEY_PREFIX + hash;

        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", TTL);

        // success = true，说明第一次提交，不重复
        // success = false，说明 key 已存在，是重复提交
        return !Boolean.TRUE.equals(success);
    }

    private String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();

            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }
}
```



#### BulterAgentController



```java
package com.test.controller;

import com.test.common.UserContext;
import com.test.dto.AgentChatMessageDTO;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentChatSessionDTO;
import com.test.dto.AgentChatVO;
import com.test.service.*;
import com.test.service.impl.AgentStreamService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/agent")
public class BulterAgentController {

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentChatMessageService agentChatMessageService;

    @Autowired
    private AgentChatSessionService agentChatSessionService;

    @Autowired
    private AgentMemoryCacheService agentMemoryCacheService;

    @Autowired
    private AgentStreamService agentStreamService;

    @Autowired
    private AgentRateLimitService agentRateLimitService;

    @Autowired
    private AgentRequestDedupService agentRequestDedupService;

    @PostMapping("/chat")
    public ResultVO chat(@RequestBody AgentChatVO chatmessage) {

        Long userId = UserContext.getUserId();

        boolean allowed = agentRateLimitService.tryAcquire(
                userId,
                "chat",
                20,
                60
        );

        if (!allowed) {
            return ResultVOUtil.fail("请求太频繁，请稍后再试");
        }

        boolean duplicate = agentRequestDedupService.isDuplicate(
                userId,
                chatmessage.getSessionId(),
                chatmessage.getMessage()
        );

        if (duplicate) {
            return ResultVOUtil.fail("请勿重复提交相同请求");
        }

        AgentChatResponse chat = this.agentService.chat(
                userId,
                chatmessage.getSessionId(),
                chatmessage.getMessage()
        );

        return ResultVOUtil.success(chat);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody AgentChatVO chatMessage) {

        Long userId = UserContext.getUserId();

        boolean allowed = agentRateLimitService.tryAcquire(
                userId,
                "chat",
                20,
                60
        );

        if (!allowed) {
            SseEmitter emitter = new SseEmitter();

            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("请求太频繁，请稍后再试"));
            } catch (Exception ignored) {
            }

            emitter.complete();

            return emitter;
        }

        boolean duplicate = agentRequestDedupService.isDuplicate(
                userId,
                chatMessage.getSessionId(),
                chatMessage.getMessage()
        );

        if (duplicate) {
            SseEmitter emitter = new SseEmitter();

            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("请勿重复提交相同请求"));
            } catch (Exception ignored) {
            }

            emitter.complete();

            return emitter;
        }

        return agentStreamService.streamChat(
                userId,
                chatMessage.getSessionId(),
                chatMessage.getMessage()
        );
    }

    @GetMapping("/history/{sessionId}")
    public ResultVO<List<AgentChatMessageDTO>> history(@PathVariable String sessionId) {

        Long userId = UserContext.getUserId();

        List<AgentChatMessageDTO> list = agentChatMessageService.listBySession(userId, sessionId);

        return ResultVOUtil.success(list);
    }

    @GetMapping("/sessions")
    public ResultVO<List<AgentChatSessionDTO>> sessions() {

        Long userId = UserContext.getUserId();

        List<AgentChatSessionDTO> list = agentChatSessionService.listUserSessions(userId);

        return ResultVOUtil.success(list);
    }
    @DeleteMapping("/session/{sessionId}")
    public ResultVO deleteSession(@PathVariable String sessionId) {

        Long userId = UserContext.getUserId();

        // 1. 删除会话表
        agentChatSessionService.deleteSession(userId, sessionId);

        // 2. 删除消息表
        agentChatMessageService.deleteBySession(userId, sessionId);

        // 3. 删除 Redis 短期记忆
        agentMemoryCacheService.deleteMemory(sessionId);

        return ResultVOUtil.success("会话删除成功");
    }


}
```



> 测试
>
> ```
> event:error
> data:请勿重复提交相同请求
> ```
>
> 

### 6.3.1定时计划

#### BulterAgentApplication

```java
package com.test;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.test.mapper")
@EnableScheduling
public class BulterAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(BulterAgentApplication.class, args);
    }
}
```

#### BudgetService

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.Budget;

import java.math.BigDecimal;
import java.util.List;

public interface BudgetService extends IService<Budget> {

    Long setBudget(Long userId,
                   String budgetMonth,
                   String category,
                   BigDecimal amount);

    Budget getBudget(Long userId,
                     String budgetMonth,
                     String category);
    List<Budget> listEnabledBudgets(String budgetMonth);
}
```



#### BudgetServiceImpl 

```java
@Override
public List<Budget> listEnabledBudgets(String budgetMonth) {

    if (budgetMonth == null || budgetMonth.trim().isEmpty()) {
        throw new RuntimeException("预算月份不能为空");
    }

    QueryWrapper<Budget> queryWrapper = new QueryWrapper<>();

    queryWrapper.eq("budget_month", budgetMonth)
            .eq("enabled", 1)
            .eq("deleted", 0);

    return this.list(queryWrapper);
}
```



#### BudgetScheduleTask

```java
package com.test.task;

import com.test.dto.BudgetWarningResult;
import com.test.entity.Budget;
import com.test.service.BudgetService;
import com.test.service.BudgetWarningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.YearMonth;
import java.util.List;

@Component
public class BudgetScheduleTask {

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private BudgetWarningService budgetWarningService;

    /**
     * 每天晚上 22:00 执行预算巡检
     */
    @Scheduled(cron = "0 0 22 * * ?")
    public void dailyBudgetCheck() {

        String budgetMonth = YearMonth.now().toString();

        System.out.println("开始执行每日预算巡检，budgetMonth=" + budgetMonth);

        List<Budget> budgets = budgetService.listEnabledBudgets(budgetMonth);

        if (budgets == null || budgets.isEmpty()) {
            System.out.println("当前月份暂无启用预算，budgetMonth=" + budgetMonth);
            return;
        }

        for (Budget budget : budgets) {
            if (budget == null || budget.getUserId() == null) {
                continue;
            }

            List<BudgetWarningResult> warningResults =
                    budgetWarningService.checkAfterExpense(
                            budget.getUserId(),
                            budget.getCategory()
                    );

            if (warningResults == null || warningResults.isEmpty()) {
                continue;
            }

            for (BudgetWarningResult result : warningResults) {
                if (result == null || !Boolean.TRUE.equals(result.getWarning())) {
                    continue;
                }

                System.out.println("定时预算巡检触发预警：userId="
                        + budget.getUserId()
                        + "，category="
                        + result.getCategory()
                        + "，level="
                        + result.getLevel()
                        + "，message="
                        + result.getMessage());
            }
        }

        System.out.println("每日预算巡检完成，budgetMonth=" + budgetMonth);
    }
}
```





> 测试
>
> ```
> 定时预算巡检触发预警：userId=1，category=TOTAL，level=OVER，message=另外提醒一下，你本月总预算已经超支，当前已支出 541.00 元，预算为 500.00 元。
> 定时预算巡检触发预警：userId=1，category=餐饮，level=OVER，message=另外提醒一下，你本月餐饮预算已经超支，当前已支出 415.00 元，预算为 100.00 元。
> 每日预算巡检完成，budgetMonth=2026-06
> ```
>
> git commit -m “SEE流式输出，redis接口限流，redis防重复提交，定时计划”



## 7.RAG

### 7.1.1Mysql+关键词检索

#### financial_knowledge

创建知识库表

```sql
CREATE TABLE financial_knowledge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    title VARCHAR(100) NOT NULL COMMENT '知识标题',
    content TEXT NOT NULL COMMENT '知识内容',
    category VARCHAR(50) DEFAULT NULL COMMENT '知识分类',
    tags VARCHAR(255) DEFAULT NULL COMMENT '标签',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用：1启用，0禁用',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除'
) COMMENT='财务知识库表';
```

测试数据

```sql
INSERT INTO financial_knowledge(title, content, category, tags) VALUES
('50/30/20 预算法', '50/30/20 预算法建议将收入分为三部分：50%用于必要支出，30%用于弹性消费，20%用于储蓄或还款。适合刚开始做个人预算管理的用户。', '预算管理', '预算,理财,储蓄'),

('控制饮品消费的方法', '如果奶茶、咖啡等饮品消费过高，可以设置每周饮品预算，例如每周不超过50元；也可以用固定频率替代随手购买，例如每周只买2次。', '消费控制', '饮品,奶茶,咖啡,消费控制'),

('冲动消费控制方法', '冲动消费可以通过延迟购买法控制。看到想买的东西先加入清单，等待24小时后再决定是否购买。对于非必要消费，可以设置月度上限。', '消费控制', '购物,冲动消费,省钱'),

('餐饮消费优化建议', '餐饮支出过高时，可以先区分必要餐饮和享受型餐饮。外卖、聚餐、火锅等可以设置单独预算，平时多使用固定餐费计划。', '消费控制', '餐饮,外卖,聚餐'),

('预算超支后的处理建议', '当预算已经超支时，不建议直接停止所有消费，而是优先控制非必要支出，例如饮品、娱乐和购物，同时保留必要餐饮、交通和住房支出。', '预算管理', '超支,预算预警,消费建议');
```



#### FinancialKnowledge

```java
package com.test.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("financial_knowledge")
public class FinancialKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private String category;

    private String tags;

    private Integer enabled;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
```



#### FinancialKnowledgeMapper

```java
package com.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.test.entity.FinancialKnowledge;

public interface FinancialKnowledgeMapper extends BaseMapper<FinancialKnowledge> {
}
```



#### FinancialKnowledgeService

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.FinancialKnowledge;

import java.util.List;

public interface FinancialKnowledgeService extends IService<FinancialKnowledge> {

    List<FinancialKnowledge> searchKnowledge(String question, Integer limit);
}
```



#### FinancialKnowledgeServiceImpl

```java
package com.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.entity.FinancialKnowledge;
import com.test.mapper.FinancialKnowledgeMapper;
import com.test.service.FinancialKnowledgeService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FinancialKnowledgeServiceImpl
        extends ServiceImpl<FinancialKnowledgeMapper, FinancialKnowledge>
        implements FinancialKnowledgeService {

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
```

#### IntentType

```java
package com.test.enums;

public enum IntentType {
    RECORD_EXPENSE,
    QUERY_EXPENSE,
    ANALYZE_EXPENSE,
    SET_BUDGET,
    BUDGET_RISK,

    /**
     * 查询账单明细
     */
    LIST_RECORDS,

    /**
     * 删除指定账单
     */
    DELETE_RECORD,

    /**
     * 修改指定账单
     */
    UPDATE_RECORD,

    /**
     * RAG知识
     */
    KNOWLEDGE_ADVICE,
    /**
     * 撤销最近一笔账单
     */
    UNDO_RECORD,

    CHAT,
    UNKNOWN
}
```

#### AgentIntentServiceImpl

```java
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

        // 3. 查询类
        if (isQueryMessage(message)) {
            return IntentType.QUERY_EXPENSE;
        }

        // 4. 分析类
        if (isAnalyzeMessage(message)) {
            return IntentType.ANALYZE_EXPENSE;
        }
        
        // 5. 知识库建议 / RAG 建议类
        if (isKnowledgeAdviceMessage(message)) {
            return IntentType.KNOWLEDGE_ADVICE;
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
                || message.contains("怎么省钱")
                || message.contains("如何省钱")
                || message.contains("消费建议")
                || message.contains("理财建议")
                || message.contains("预算建议")
                || message.contains("怎么减少")
                || message.contains("太多了怎么办")
                || message.contains("有什么办法")
                || message.contains("怎么改善")
                || message.contains("如何控制");
    }
}
```

#### AgentKnowledgeHandler

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.entity.FinancialKnowledge;
import com.test.service.AIService;
import com.test.service.FinancialKnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentKnowledgeHandler {

    @Autowired
    private FinancialKnowledgeService financialKnowledgeService;

    @Autowired
    private AIService aiService;

    public AgentChatResponse handle(Long userId,
                                    String sessionId,
                                    String message,
                                    List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        List<FinancialKnowledge> knowledgeList =
                financialKnowledgeService.searchKnowledge(message, 3);

        if (knowledgeList == null || knowledgeList.isEmpty()) {
            response.setReply("我暂时没有检索到相关财务知识，不过你可以换个说法，例如：怎么控制奶茶消费？");
            response.setActions(actions);
            return response;
        }

        StringBuilder knowledgeText = new StringBuilder();

        for (int i = 0; i < knowledgeList.size(); i++) {
            FinancialKnowledge knowledge = knowledgeList.get(i);

            knowledgeText.append(i + 1)
                    .append(". ")
                    .append(knowledge.getTitle())
                    .append("：")
                    .append(knowledge.getContent())
                    .append("\n");
        }

        String prompt = "你是一个 AI 个人财务管家，请基于下面检索到的财务知识回答用户问题。\n"
                + "要求：\n"
                + "1. 回答要自然，不要机械复制知识库原文。\n"
                + "2. 建议要具体、可执行。\n"
                + "3. 不要编造知识库中没有依据的专业结论。\n"
                + "4. 可以结合用户的问题做适当解释。\n\n"
                + "【检索到的知识】\n"
                + knowledgeText
                + "\n【用户问题】\n"
                + message;

        String reply = aiService.chat(sessionId, prompt);

        AgentAction action = new AgentAction();
        action.setName("retrieveKnowledge");
        action.setSuccess(true);
        action.setMessage("知识库检索成功，共检索到 " + knowledgeList.size() + " 条知识");

        actions.add(action);

        response.setReply(reply);
        response.setActions(actions);

        return response;
    }
}
```

#### AgentServiceImpl

```java
package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.enums.IntentType;
import com.test.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentPlanService agentPlanService;

    @Autowired
    private AgentIntentService agentIntentService;

    @Autowired
    private AgentRecordHandler agentRecordHandler;

    @Autowired
    private AgentStatisticsHandler agentStatisticsHandler;

    @Autowired
    private AgentBudgetHandler agentBudgetHandler;

    @Autowired
    private AgentBudgetRiskHandler agentBudgetRiskHandler;

    @Autowired
    private AgentChatReplyService agentChatReplyService;

    @Autowired
    private AgentChatMessageService agentChatMessageService;

    @Autowired
    private AgentActionLogService agentActionLogService;

    @Autowired
    private AgentMemoryCacheService agentMemoryCacheService;

    @Autowired
    private AgentChatSessionService agentChatSessionService;

    @Autowired
    private AgentRecordManageHandler agentRecordManageHandler;

    @Autowired
    private AgentKnowledgeHandler agentKnowledgeHandler;

    @Override
    public AgentChatResponse chat(Long userId, String sessionId, String message){

        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "session_" + java.util.UUID.randomUUID().toString().replace("-", "");
        }

        List<AgentAction> actions = new ArrayList<>();

        // 1. 保存用户消息到 MySQL
        agentChatMessageService.saveMessage(
                userId,
                sessionId,
                "USER",
                message,
                null,
                null
        );

        // 2. 保存用户消息到 Redis
        agentMemoryCacheService.appendMessage(
                sessionId,
                "USER",
                message
        );

        // 3. AI 解析计划
        AgentPlan plan = agentPlanService.parsePlan(sessionId, message);

        // 4. 后端最终意图裁决
        IntentType finalIntent = agentIntentService.decideIntent(message, plan);

        AgentChatResponse response;

        switch (finalIntent) {
            case LIST_RECORDS:
                response = agentRecordManageHandler.handleList(userId, message, actions);
                break;

            case DELETE_RECORD:
                response = agentRecordManageHandler.handleDelete(userId, message, actions);
                break;

            case UPDATE_RECORD:
                response = agentRecordManageHandler.handleUpdate(userId, message, actions);
                break;

            case UNDO_RECORD:
                response = agentRecordManageHandler.handleUndo(userId, message, actions);
                break;

            case RECORD_EXPENSE:
                response = agentRecordHandler.handle(userId, sessionId, message, plan, actions);
                break;

            case QUERY_EXPENSE:
                response = agentStatisticsHandler.handleQuery(userId, message, actions);
                break;

            case ANALYZE_EXPENSE:
                response = agentStatisticsHandler.handleAnalyze(userId, message, actions);
                break;
                
            case KNOWLEDGE_ADVICE:
                response = agentKnowledgeHandler.handle(userId, sessionId, message, actions);
                break;

            case SET_BUDGET:
                response = agentBudgetHandler.handleSetBudget(userId, message, plan, actions);
                break;

            case BUDGET_RISK:
                response = agentBudgetRiskHandler.handle(userId, message, actions);
                break;

            case CHAT:
            default:
                response = agentChatReplyService.chat(sessionId, message, actions);
                break;
        }

        // 5. 保存助手回复到 MySQL
        agentChatMessageService.saveMessage(
                userId,
                sessionId,
                "ASSISTANT",
                response.getReply(),
                plan == null ? null : plan.getIntent(),
                finalIntent == null ? null : finalIntent.name()
        );

        // 6. 保存助手回复到 Redis
        agentMemoryCacheService.appendMessage(
                sessionId,
                "ASSISTANT",
                response.getReply()
        );

        // 7. 保存 actions 到 MySQL
        agentActionLogService.saveActions(
                userId,
                sessionId,
                message,
                response.getActions()
        );
        // 8. 更新会话摘要
        agentChatSessionService.createOrUpdateSession(
                userId,
                sessionId,
                message,
                response.getReply()
        );

        // 返回前设置 sessionId
        response.setSessionId(sessionId);

        return response;
    }

}
```



> 测试
>
> ```
> {
>   "sessionId": "session_d8db15b13bae468993cfc67ff3313be2",
>   "message": "我奶茶喝太多了，怎么控制饮品消费？"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "sessionId": "session_d8db15b13bae468993cfc67ff3313be2",
>     "reply": "奶茶属于非必要饮品，建议设个「月度奶茶预算」，比如120元（约4杯）。  \n下单前停10秒，问自己：今天喝的是解渴，还是习惯？  \n我也可以帮你记下每次消费，慢慢看清规律～",
>     "actions": [
>       {
>         "name": "retrieveKnowledge",
>         "success": true,
>         "message": "知识库检索成功，共检索到 3 条知识"
>       }
>     ]
>   }
> }
> ```



### 7.1.2Embedding语义检索RAG

1. financial_knowledge 表增加 embedding 字段
2. 创建 KnowledgeEmbeddingService
3. 给已有知识生成 embedding 并保存
4. 新增 semanticSearchKnowledge()
5. AgentKnowledgeHandler 改成优先语义检索
6. 测试模糊表达能不能召回正确知识

#### SQL

```
ALTER TABLE financial_knowledge
ADD COLUMN embedding LONGTEXT DEFAULT NULL COMMENT '知识向量JSON';
```



#### FinancialKnowledge

```java
package com.test.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("financial_knowledge")
public class FinancialKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private String category;

    private String tags;

    private String embedding;

    private Integer enabled;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;

    /**
     * 语义相似度分数，不对应数据库字段
     */
    @TableField(exist = false)
    private Double score;
}
```



#### FinancialKnowledgeService

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.FinancialKnowledge;

import java.util.List;

public interface FinancialKnowledgeService extends IService<FinancialKnowledge> {

    List<FinancialKnowledge> searchKnowledge(String question, Integer limit);

    void refreshAllEmbeddings();

    List<FinancialKnowledge> semanticSearchKnowledge(String question, Integer limit);
}
```



#### FinancialKnowledgeServiceImpl

```java
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
```



#### agent-plan-prompt

```java
你是一个 AI 个人财务管家助手，负责将用户输入的自然语言解析成固定 JSON 格式。

你的任务不是直接回答用户，而是识别用户意图，并提取结构化参数。

你只能返回 JSON。
不要返回解释。
不要返回 Markdown。
不要使用 ```json 代码块。
不要在 JSON 前后添加任何文字。

返回 JSON 必须严格符合以下格式：

{
"intent": "RECORD_EXPENSE",
"recordType": "EXPENSE",
"amount": 18,
"category": "饮品",
"description": "奶茶",
"expenses": [
{
"amount": 18,
"category": "饮品",
"recordType": "EXPENSE",
"description": "奶茶"
}
],
"valid": true,
"reason": ""
}

字段说明：

1. intent 表示用户意图，只能取以下值：

* RECORD_EXPENSE：用户想记录一笔或多笔收入/支出
* QUERY_EXPENSE：用户想查询消费金额、收入金额、结余或收支记录
* ANALYZE_EXPENSE：用户想分析消费、收入或收支情况
* SET_BUDGET：用户想设置预算
* BUDGET_RISK：用户想查询预算、判断预算是否超支、预算是否够用、剩余预算是多少
* LIST_RECORDS：用户想查看账单明细、账单列表、消费明细、收支明细或记账记录
* DELETE_RECORD：用户想删除指定账单或记录
* UPDATE_RECORD：用户想修改指定账单或记录
* UNDO_RECORD：用户想撤销、删除或取消最近一笔账单
* KNOWLEDGE_ADVICE：用户想获得消费控制、预算管理、省钱、理财建议，或者询问某类消费太多该怎么办
* CHAT：普通闲聊
* UNKNOWN：无法判断意图

2. recordType 表示记录类型，只能取以下值：

* EXPENSE：支出，例如吃饭、奶茶、打车、购物、买书、看电影、房租、医疗等
* INCOME：收入，例如工资、兼职、红包、奖金、退款、报销、转账收入等

如果 intent 不是 RECORD_EXPENSE，则 recordType 填写 null。

如果用户输入中同时包含收入和支出，则 recordType 填写第一笔记录的类型，所有记录放入 expenses 数组中。

3. amount 表示金额。

* 如果用户输入中有明确金额，填写数字。
* 如果没有金额，填写 null。
* 金额必须使用阿拉伯数字，不要带单位。
* 例如“18元”“十八块”“18块钱”都返回 18。
* 如果是多笔记录，amount 填写第一笔记录的金额。
* 如果是修改账单，例如“把账单15改成25元”，amount 填写新的金额 25。
* 如果是删除、撤销、查询、建议类问题，amount 填写 null。

4. category 表示类别。

支出类别只能从以下类别中选择：
餐饮、饮品、交通、购物、学习、娱乐、医疗、住房、其他

收入类别只能从以下类别中选择：
工资、兼职、红包、奖金、退款、报销、转账、其他

预算类别可以是：
餐饮、饮品、交通、购物、学习、娱乐、医疗、住房、其他

如果是总预算，category 填写 null。

如果无法判断类别，填写 null。

5. description 表示记录描述。

例如：
奶茶、午饭、打车、买书、工资到账、兼职收入、红包、退款。

如果无法提取，填写 null。

如果是多笔记录，description 填写第一笔记录的描述。

6. expenses 表示多笔收支记录。

只有 intent 为 RECORD_EXPENSE 时使用。

* 如果用户只输入一笔记录，expenses 返回一个元素。
* 如果用户一次输入多笔记录，expenses 返回所有识别到的记录。
* 每一项必须包含 amount、category、recordType、description。
* 每一项的 recordType 必须是 EXPENSE 或 INCOME。
* 如果某一笔缺少金额，则不要把这一笔放入 expenses。
* 如果所有记录都缺少金额，则 valid 为 false。
* 如果 intent 不是 RECORD_EXPENSE，则 expenses 填写 null。

7. valid 表示本次解析是否有效。

* 如果用户表达清楚，填写 true。
* 如果缺少关键信息，填写 false。
* 记账类请求必须至少识别出一笔有效的金额记录，valid 才能为 true。
* 查询、分析、预算查询、账单管理、知识建议、闲聊类请求，只要意图明确，valid 就可以为 true。
* 删除、修改指定账单时，如果没有账单 ID，valid 填写 false。

8. reason 表示原因说明。

* 如果 valid 为 true，reason 填写空字符串 ""。
* 如果 valid 为 false，说明缺少什么信息或为什么无法解析。

判断规则：

1. 如果用户输入包含明确消费内容和金额，判断为 RECORD_EXPENSE，并且 recordType 为 EXPENSE。

例如：
今天奶茶18
午饭28元
打车花了22
买书50
房租1200

2. 如果用户输入包含明确收入来源和金额，判断为 RECORD_EXPENSE，并且 recordType 为 INCOME。

例如：
今天工资到账5000
收到兼职费300
红包收入88
奖金1000
退款20
报销120

3. 如果用户一次输入多笔收支记录，也判断为 RECORD_EXPENSE。

例如：
今天奶茶18，午饭28，打车22
今天工资5000，奶茶18，打车22
收到红包88，午饭28

要求：

* amount、category、recordType、description 填写第一笔记录的信息。
* expenses 数组中返回所有识别到的有效记录。
* 不要只返回第一笔。
* 不要在 reason 中说“当前仅提取第一笔”。

4. 如果用户是在问消费、收入或收支情况，判断为 QUERY_EXPENSE。

例如：
我这个月花了多少钱
今天一共消费多少
本月收入多少
我这个月结余多少
查一下本月收支
我今天消费多少

5. 如果用户要求分析消费、收入或收支情况，判断为 ANALYZE_EXPENSE。

例如：
分析一下我这个月的消费情况
看看我最近消费结构
分析一下我的收入和支出
看看本月收支情况
本月餐饮占比多少
饮品消费占比多少

注意：
如果用户是在问“怎么控制”“怎么省钱”“怎么办”“有什么建议”，不要判断为 ANALYZE_EXPENSE，而应判断为 KNOWLEDGE_ADVICE。

6. 如果用户想设置预算，判断为 SET_BUDGET。

例如：
帮我把本月预算设置为1800
设置餐饮预算800
这个月预算设成2000
饮品预算设置为200
交通预算300

总预算设置规则：

* “本月预算设置为1800”
* “这个月预算设成2000”
* “总预算1800”
  这些属于总预算，category 填写 null。

分类预算设置规则：

* “设置餐饮预算800” category 填写 “餐饮”
* “饮品预算设置为200” category 填写 “饮品”
* “交通预算300” category 填写 “交通”

7. 如果用户询问预算查询、预算是否够用、是否超支、剩余预算，判断为 BUDGET_RISK。

例如：
预算查询
查一下预算
预算情况
本月预算情况
我这个月会不会超预算
我还能控制在1800以内吗
这个月预算够不够
我是不是快超支了
还剩多少预算
餐饮预算还剩多少
饮品预算够不够
交通预算查询

8. 如果用户想查看账单明细、账单列表、消费明细、收支明细或记账记录，判断为 LIST_RECORDS。

例如：
查看本月账单
查一下账单明细
查看消费明细
今天账单
本月账单列表
查看记账记录
查一下餐饮账单
查看本月收入记录

9. 如果用户想删除指定账单，判断为 DELETE_RECORD。

例如：
删除账单17
删掉记录15
帮我删除账单20
删除这条账单18

如果用户没有提供具体账单 ID，valid 为 false。

10. 如果用户想修改指定账单，判断为 UPDATE_RECORD。

例如：
把账单15改成25元
修改账单18为30元
把记录20改为餐饮
把账单21描述改为奶茶
把账单16改成支出50元

如果用户没有提供具体账单 ID，valid 为 false。

11. 如果用户想撤销最近一笔账单，判断为 UNDO_RECORD。

例如：
撤销上一笔
撤销最近一笔
删除上一笔
删除最近一笔
取消上一笔
刚刚那笔记错了
上一笔记错了
最近一笔记错了

12. 如果用户是在询问如何控制消费、如何省钱、如何减少某类支出、某类消费太多怎么办、预算管理建议或理财建议，判断为 KNOWLEDGE_ADVICE。

例如：
我奶茶喝太多了，怎么控制饮品消费？
我老是买喝的，怎么省一点？
外卖花太多了怎么办？
有什么省钱建议？
怎么减少冲动消费？
怎么控制购物消费？
我这个月花太多了，有什么办法？
怎么改善我的消费习惯？
预算总是超支怎么办？
怎么做个人预算管理？
如何控制餐饮支出？
饮品消费太高了怎么办？
我总是买咖啡奶茶，怎么少花点？
怎么降低非必要支出？

注意：

* 只要用户的核心诉求是“建议、方法、怎么办、如何改善”，就优先判断为 KNOWLEDGE_ADVICE。
* 即使句子里包含“消费”“预算”“支出”，只要是在问建议，不要判断为 ANALYZE_EXPENSE。
* KNOWLEDGE_ADVICE 不需要提取 amount。
* 如果能判断具体消费类别，可以填写 category，例如饮品、餐饮、购物；无法判断则填 null。

13. 如果用户只是普通聊天，判断为 CHAT。

例如：
你好
你是谁
我不想上班
今天天气不错
今天好烦

14. 如果用户表达不完整，判断为 UNKNOWN。

例如：
今天花了
帮我记一下
查一下
改一下账单

示例 1：

用户输入：今天奶茶18

返回：
{
"intent": "RECORD_EXPENSE",
"recordType": "EXPENSE",
"amount": 18,
"category": "饮品",
"description": "奶茶",
"expenses": [
{
"amount": 18,
"category": "饮品",
"recordType": "EXPENSE",
"description": "奶茶"
}
],
"valid": true,
"reason": ""
}

示例 2：

用户输入：今天奶茶18，午饭28，打车22

返回：
{
"intent": "RECORD_EXPENSE",
"recordType": "EXPENSE",
"amount": 18,
"category": "饮品",
"description": "奶茶",
"expenses": [
{
"amount": 18,
"category": "饮品",
"recordType": "EXPENSE",
"description": "奶茶"
},
{
"amount": 28,
"category": "餐饮",
"recordType": "EXPENSE",
"description": "午饭"
},
{
"amount": 22,
"category": "交通",
"recordType": "EXPENSE",
"description": "打车"
}
],
"valid": true,
"reason": ""
}

示例 3：

用户输入：今天工资到账5000

返回：
{
"intent": "RECORD_EXPENSE",
"recordType": "INCOME",
"amount": 5000,
"category": "工资",
"description": "工资到账",
"expenses": [
{
"amount": 5000,
"category": "工资",
"recordType": "INCOME",
"description": "工资到账"
}
],
"valid": true,
"reason": ""
}

示例 4：

用户输入：我这个月花了多少钱

返回：
{
"intent": "QUERY_EXPENSE",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 5：

用户输入：分析一下我这个月的消费情况

返回：
{
"intent": "ANALYZE_EXPENSE",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 6：

用户输入：帮我把本月预算设置为1800

返回：
{
"intent": "SET_BUDGET",
"recordType": null,
"amount": 1800,
"category": null,
"description": "本月预算",
"expenses": null,
"valid": true,
"reason": ""
}

示例 7：

用户输入：设置餐饮预算800

返回：
{
"intent": "SET_BUDGET",
"recordType": null,
"amount": 800,
"category": "餐饮",
"description": "餐饮预算",
"expenses": null,
"valid": true,
"reason": ""
}

示例 8：

用户输入：预算查询

返回：
{
"intent": "BUDGET_RISK",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 9：

用户输入：餐饮预算还剩多少

返回：
{
"intent": "BUDGET_RISK",
"recordType": null,
"amount": null,
"category": "餐饮",
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 10：

用户输入：查看本月账单明细

返回：
{
"intent": "LIST_RECORDS",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 11：

用户输入：删除账单17

返回：
{
"intent": "DELETE_RECORD",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 12：

用户输入：把账单15改成25元

返回：
{
"intent": "UPDATE_RECORD",
"recordType": null,
"amount": 25,
"category": null,
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 13：

用户输入：撤销上一笔

返回：
{
"intent": "UNDO_RECORD",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 14：

用户输入：我奶茶喝太多了，怎么控制饮品消费？

返回：
{
"intent": "KNOWLEDGE_ADVICE",
"recordType": null,
"amount": null,
"category": "饮品",
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 15：

用户输入：我老是买喝的，怎么省一点？

返回：
{
"intent": "KNOWLEDGE_ADVICE",
"recordType": null,
"amount": null,
"category": "饮品",
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 16：

用户输入：外卖花太多了怎么办？

返回：
{
"intent": "KNOWLEDGE_ADVICE",
"recordType": null,
"amount": null,
"category": "餐饮",
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 17：

用户输入：有什么省钱建议？

返回：
{
"intent": "KNOWLEDGE_ADVICE",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 18：

用户输入：你好

返回：
{
"intent": "CHAT",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"expenses": null,
"valid": true,
"reason": ""
}

示例 19：

用户输入：今天花了

返回：
{
"intent": "UNKNOWN",
"recordType": null,
"amount": null,
"category": null,
"description": null,
"expenses": null,
"valid": false,
"reason": "缺少金额和具体收支内容"
}

```



> 测试
>
> ```
> {
>   "sessionId": "session_d8db15b13bae468993cfc67ff3313be2",
>   "message": "我老是买喝的，怎么省一点？"
> }
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": {
>     "sessionId": "session_d8db15b13bae468993cfc67ff3313be2",
>     "reply": "可以试试每周定个饮品小目标，比如只买2次、总预算50元。  \n买之前等24小时，常会发现其实没那么想喝～  \n自带水杯还能省不少，需要我帮你设个提醒吗？",
>     "actions": [
>       {
>         "name": "retrieveKnowledge",
>         "success": true,
>         "message": "知识库检索成功，共检索到 3 条知识"
>       }
>     ]
>   }
> }
> ```
>
> 

### 7.2.1AOP统一日志

#### pom

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <groupId>com.test</groupId>
    <artifactId>Bulter_Agent</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>Bulter_Agent</name>
    <description>Bulter_Agent</description>
    <url/>
    <licenses>
        <license/>
    </licenses>
    <developers>
        <developer/>
    </developers>
    <scm>
        <connection/>
        <developerConnection/>
        <tag/>
        <url/>
    </scm>
    <properties>
        <java.version>17</java.version>
        <mybatis-plus.version>3.5.6</mybatis-plus.version>
        <langchain4j.version>1.1.0</langchain4j.version>
        <langchain4j.spring.version>1.1.0-beta7</langchain4j.spring.version>
        <knife4j.version>4.5.0</knife4j.version>
    </properties>
    <dependencies>
        <!-- Spring AOP -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
        <!-- Redis -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- RabbitMQ -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-amqp</artifactId>
        </dependency>

        <!-- MyBatis-Plus 代码生成器 -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-generator</artifactId>
            <version>3.5.6</version>
        </dependency>

        <!-- Freemarker 模板引擎 -->
        <dependency>
            <groupId>org.freemarker</groupId>
            <artifactId>freemarker</artifactId>
        </dependency>
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- MyBatis-Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>



        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-spring-boot-starter</artifactId>
            <version>1.1.0-beta7</version>
        </dependency>
        <!-- LangChain4j OpenAI Spring Boot Starter -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-community-dashscope-spring-boot-starter</artifactId>
            <version>1.1.0-beta7</version>
        </dependency>

        <!-- Knife4j / Swagger -->
        <dependency>
            <groupId>com.github.xiaoymin</groupId>
            <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
            <version>${knife4j.version}</version>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

</project>

```

#### sql

```sql
CREATE TABLE agent_api_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT DEFAULT NULL COMMENT '用户ID',
    session_id VARCHAR(100) DEFAULT NULL COMMENT '会话ID',
    api_name VARCHAR(100) DEFAULT NULL COMMENT '接口名称',
    request_uri VARCHAR(255) DEFAULT NULL COMMENT '请求URI',
    request_method VARCHAR(20) DEFAULT NULL COMMENT '请求方法',
    request_content TEXT DEFAULT NULL COMMENT '请求内容',
    success TINYINT DEFAULT 1 COMMENT '是否成功：1成功，0失败',
    cost_ms BIGINT DEFAULT NULL COMMENT '耗时毫秒',
    error_msg TEXT DEFAULT NULL COMMENT '异常信息',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='Agent接口调用日志表';
```



#### AgentApiLog

```java
package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_api_log")
public class AgentApiLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionId;

    private String apiName;

    private String requestUri;

    private String requestMethod;

    private String requestContent;

    /**
     * 1 成功，0 失败
     */
    private Integer success;

    private Long costMs;

    private String errorMsg;

    private LocalDateTime createdTime;
}
```

#### AgentApiLogMapper

```java
package com.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.test.entity.AgentApiLog;

public interface AgentApiLogMapper extends BaseMapper<AgentApiLog> {
}
```

#### AgentApiLogService

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.AgentApiLog;

public interface AgentApiLogService extends IService<AgentApiLog> {
}
```

#### AgentApiLogServiceImpl

```java
package com.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.entity.AgentApiLog;
import com.test.mapper.AgentApiLogMapper;
import com.test.service.AgentApiLogService;
import org.springframework.stereotype.Service;

@Service
public class AgentApiLogServiceImpl
        extends ServiceImpl<AgentApiLogMapper, AgentApiLog>
        implements AgentApiLogService {
}
```

#### AgentApiLogAspect

```java
package com.test.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.common.UserContext;
import com.test.dto.AgentChatVO;
import com.test.entity.AgentApiLog;
import com.test.service.AgentApiLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;

@Aspect
@Component
public class AgentApiLogAspect {

    @Autowired
    private AgentApiLogService agentApiLogService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 拦截 ButlerAgentController 下所有方法
     */
    @Around("execution(* com.test.controller.BulterAgentController.*(..))")
    public Object recordAgentApiLog(ProceedingJoinPoint joinPoint) throws Throwable {

        long startTime = System.currentTimeMillis();

        AgentApiLog log = new AgentApiLog();

        fillBasicInfo(log, joinPoint);

        try {
            Object result = joinPoint.proceed();

            long cost = System.currentTimeMillis() - startTime;

            log.setSuccess(1);
            log.setCostMs(cost);
            log.setErrorMsg(null);
            log.setCreatedTime(LocalDateTime.now());

            agentApiLogService.save(log);

            return result;

        } catch (Throwable e) {

            long cost = System.currentTimeMillis() - startTime;

            log.setSuccess(0);
            log.setCostMs(cost);
            log.setErrorMsg(e.getMessage());
            log.setCreatedTime(LocalDateTime.now());

            agentApiLogService.save(log);

            throw e;
        }
    }

    private void fillBasicInfo(AgentApiLog log, ProceedingJoinPoint joinPoint) {

        try {
            log.setUserId(UserContext.getUserId());
        } catch (Exception e) {
            log.setUserId(null);
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        log.setApiName(signature.getMethod().getName());

        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();

            log.setRequestUri(request.getRequestURI());
            log.setRequestMethod(request.getMethod());
        }

        Object[] args = joinPoint.getArgs();

        if (args != null && args.length > 0) {
            for (Object arg : args) {

                if (arg instanceof AgentChatVO chatVO) {
                    log.setSessionId(chatVO.getSessionId());
                    log.setRequestContent(toJson(chatVO));
                    return;
                }

                if (arg instanceof SseEmitter) {
                    continue;
                }
            }

            log.setRequestContent(toJson(args));
        }
    }

    private String toJson(Object object) {
        try {
            String json  = objectMapper.writeValueAsString(object);

            if (json.length() > 2000) {
                return json.substring(0, 2000);
            }

            return json;
        } catch (Exception e) {
            return String.valueOf(object);
        }
    }2
}
```



> 测试
>
> 
>
> 数据库表
>
> ```
> 2,1,session_d8db15b13bae468993cfc67ff3313be2,chat,/agent/chat,1,3581,2026-06-02 22:23:46
> 1,1,session_d8db15b13bae468993cfc67ff3313be2,chat,/agent/chat,1,6316,2026-06-02 22:21:38
> 
> ```
>
> 

### 7.3.1站内通知 + WebSocket 实时推送

#### SQL

```sql
CREATE TABLE user_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    title VARCHAR(100) NOT NULL COMMENT '通知标题',
    content TEXT NOT NULL COMMENT '通知内容',
    notify_type VARCHAR(50) DEFAULT NULL COMMENT '通知类型：BUDGET_WARNING等',
    business_id BIGINT DEFAULT NULL COMMENT '业务ID，例如账单ID或预警ID',
    read_status TINYINT DEFAULT 0 COMMENT '是否已读：0未读，1已读',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'
) COMMENT='用户站内通知表';
```

#### UserNotification

```java
package com.test.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_notification")
public class UserNotification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String content;

    private String notifyType;

    private Long businessId;

    /**
     * 0未读，1已读
     */
    private Integer readStatus;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
```

#### UserNotificationMapper

```java
package com.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.test.entity.UserNotification;

public interface UserNotificationMapper extends BaseMapper<UserNotification> {
}
```

#### UserNotificationService

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.UserNotification;

public interface UserNotificationService extends IService<UserNotification> {

    Long createNotification(Long userId,
                            String title,
                            String content,
                            String notifyType,
                            Long businessId);
}
```

#### UserNotificationServiceImpl

```java
package com.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.entity.UserNotification;
import com.test.mapper.UserNotificationMapper;
import com.test.service.UserNotificationService;
import org.springframework.stereotype.Service;

@Service
public class UserNotificationServiceImpl
        extends ServiceImpl<UserNotificationMapper, UserNotification>
        implements UserNotificationService {

    @Override
    public Long createNotification(Long userId,
                                   String title,
                                   String content,
                                   String notifyType,
                                   Long businessId) {

        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        UserNotification notification = new UserNotification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setNotifyType(notifyType);
        notification.setBusinessId(businessId);
        notification.setReadStatus(0);
        notification.setDeleted(0);

        this.save(notification);

        return notification.getId();
    }
}
```

#### POM

```java
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <groupId>com.test</groupId>
    <artifactId>Bulter_Agent</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>Bulter_Agent</name>
    <description>Bulter_Agent</description>
    <url/>
    <licenses>
        <license/>
    </licenses>
    <developers>
        <developer/>
    </developers>
    <scm>
        <connection/>
        <developerConnection/>
        <tag/>
        <url/>
    </scm>
    <properties>
        <java.version>17</java.version>
        <mybatis-plus.version>3.5.6</mybatis-plus.version>
        <langchain4j.version>1.1.0</langchain4j.version>
        <langchain4j.spring.version>1.1.0-beta7</langchain4j.spring.version>
        <knife4j.version>4.5.0</knife4j.version>
    </properties>
    <dependencies>
        <!-- WebSocket -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
        <!-- Spring AOP -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
        <!-- Redis -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- RabbitMQ -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-amqp</artifactId>
        </dependency>

        <!-- MyBatis-Plus 代码生成器 -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-generator</artifactId>
            <version>3.5.6</version>
        </dependency>

        <!-- Freemarker 模板引擎 -->
        <dependency>
            <groupId>org.freemarker</groupId>
            <artifactId>freemarker</artifactId>
        </dependency>
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- MyBatis-Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>



        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-spring-boot-starter</artifactId>
            <version>1.1.0-beta7</version>
        </dependency>
        <!-- LangChain4j OpenAI Spring Boot Starter -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-community-dashscope-spring-boot-starter</artifactId>
            <version>1.1.0-beta7</version>
        </dependency>

        <!-- Knife4j / Swagger -->
        <dependency>
            <groupId>com.github.xiaoymin</groupId>
            <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
            <version>${knife4j.version}</version>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

</project>

```

#### WebSocketConfig

```java
package com.test.config;

import com.test.websocket.NotificationWebSocketHandler;
import com.test.websocket.NotificationWebSocketInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private NotificationWebSocketHandler notificationWebSocketHandler;

    @Autowired
    private NotificationWebSocketInterceptor notificationWebSocketInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, "/ws/notification")
                .addInterceptors(notificationWebSocketInterceptor)
                .setAllowedOrigins("*");
    }
}
```

#### NotificationWebSocketInterceptor

```java
package com.test.websocket;

import com.test.service.UserTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class NotificationWebSocketInterceptor implements HandshakeInterceptor {

    @Autowired
    private UserTokenService userTokenService;

    @Override
    public boolean beforeHandshake(org.springframework.http.server.ServerHttpRequest request,
                                   org.springframework.http.server.ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        String query = request.getURI().getQuery();

        if (query == null || !query.contains("token=")) {
            return false;
        }

        String token = query.replace("token=", "").trim();

        Long userId = userTokenService.getUserIdByToken(token);

        if (userId == null) {
            return false;
        }

        attributes.put("userId", userId);

        return true;
    }

    @Override
    public void afterHandshake(org.springframework.http.server.ServerHttpRequest request,
                               org.springframework.http.server.ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
```

#### NotificationWebSocketHandler

```java
package com.test.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationWebSocketHandler implements WebSocketHandler {

    private static final Map<Long, WebSocketSession> SESSION_MAP = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object userIdObj = session.getAttributes().get("userId");

        if (userIdObj instanceof Long userId) {
            SESSION_MAP.put(userId, session);
            System.out.println("WebSocket 通知连接建立，userId=" + userId);
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        // 第一版不处理客户端消息
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        removeSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        removeSession(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    public void sendToUser(Long userId, String message) {

        WebSocketSession session = SESSION_MAP.get(userId);

        if (session == null || !session.isOpen()) {
            System.out.println("用户不在线，无法实时推送，userId=" + userId);
            return;
        }

        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            System.out.println("WebSocket 推送失败，userId=" + userId + "，原因：" + e.getMessage());
        }
    }

    private void removeSession(WebSocketSession session) {
        if (session == null) {
            return;
        }

        Object userIdObj = session.getAttributes().get("userId");

        if (userIdObj instanceof Long userId) {
            SESSION_MAP.remove(userId);
            System.out.println("WebSocket 通知连接关闭，userId=" + userId);
        }
    }
}
```

#### ExpenseRecordEventConsumer

```java
package com.test.mq.consumer;

import com.test.dto.BudgetWarningResult;
import com.test.mq.RabbitMqConstants;
import com.test.mq.event.ExpenseRecordCreatedEvent;
import com.test.service.BudgetWarningService;
import com.test.service.UserNotificationService;
import com.test.service.impl.NotificationWebSocketHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExpenseRecordEventConsumer {

    @Autowired
    private BudgetWarningService budgetWarningService;

    @Autowired
    private UserNotificationService userNotificationService;

    @Autowired
    private NotificationWebSocketHandler notificationWebSocketHandler;

    @RabbitListener(
            queues = RabbitMqConstants.EXPENSE_RECORD_CREATED_QUEUE,
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void handleRecordCreatedEvent(ExpenseRecordCreatedEvent event) {

        if (event == null) {
            return;
        }

        System.out.println("收到记账成功 MQ 消息：recordId=" + event.getRecordId()
                + ", userId=" + event.getUserId()
                + ", recordType=" + event.getRecordType()
                + ", category=" + event.getCategory()
                + ", amount=" + event.getAmount());

        if (!"EXPENSE".equalsIgnoreCase(event.getRecordType())) {
            return;
        }

        List<BudgetWarningResult> warningResults = budgetWarningService.checkAfterExpense(
                event.getUserId(),
                event.getCategory()
        );

        if (warningResults == null || warningResults.isEmpty()) {
            System.out.println("本次记账未触发预算预警，recordId=" + event.getRecordId());
            return;
        }

        for (BudgetWarningResult warningResult : warningResults) {
            if (warningResult == null || !Boolean.TRUE.equals(warningResult.getWarning())) {
                continue;
            }

            System.out.println("异步预算预警触发：recordId=" + event.getRecordId()
                    + ", category=" + warningResult.getCategory()
                    + ", level=" + warningResult.getLevel()
                    + ", message=" + warningResult.getMessage());
            String title = "预算预警";
            String content = warningResult.getMessage();

            Long notificationId = userNotificationService.createNotification(
                    event.getUserId(),
                    title,
                    content,
                    "BUDGET_WARNING",
                    event.getRecordId()
            );

            String pushMessage = "{"
                    + "\"type\":\"BUDGET_WARNING\","
                    + "\"notificationId\":" + notificationId + ","
                    + "\"recordId\":" + event.getRecordId() + ","
                    + "\"category\":\"" + warningResult.getCategory() + "\","
                    + "\"level\":\"" + warningResult.getLevel() + "\","
                    + "\"content\":\"" + content.replace("\"", "\\\"") + "\""
                    + "}";

            notificationWebSocketHandler.sendToUser(event.getUserId(), pushMessage);
        }
    }
}
```

#### UserTokenService

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.UserToken;

import java.time.LocalDateTime;

public interface UserTokenService extends IService<UserToken> {

    void saveToken(Long userId,
                   String token,
                   LocalDateTime expireTime);

    void invalidToken(String token);

    Long getUserIdByToken(String token);
}
```

#### UserTokenServiceImpl

```
package com.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.entity.UserToken;
import com.test.mapper.UserTokenMapper;
import com.test.service.UserTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserTokenServiceImpl
        extends ServiceImpl<UserTokenMapper, UserToken>
        implements UserTokenService {

    private static final String TOKEN_KEY_PREFIX = "login:token:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void saveToken(Long userId,
                          String token,
                          LocalDateTime expireTime) {

        if (userId == null || token == null || token.trim().isEmpty()) {
            return;
        }

        UserToken userToken = new UserToken();

        userToken.setUserId(userId);
        userToken.setTokenHash(token);
        userToken.setTokenType("UUID");
        userToken.setExpireTime(expireTime);
        userToken.setStatus(1);
        userToken.setDeleted(0);

        this.save(userToken);
    }

    @Override
    public void invalidToken(String token) {

        if (token == null || token.trim().isEmpty()) {
            return;
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        QueryWrapper<UserToken> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("token_hash", token)
                .eq("deleted", 0)
                .eq("status", 1);

        UserToken userToken = this.getOne(queryWrapper);

        if (userToken != null) {
            userToken.setStatus(0);
            this.updateById(userToken);
        }
    }

    @Override
    public Long getUserIdByToken(String token) {

        if (token == null || token.trim().isEmpty()) {
            return null;
        }

        token = token.trim();

        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        if (token.isEmpty()) {
            return null;
        }

        // 1. 优先查 Redis，和 TokenInterceptor 保持一致
        String userIdStr = stringRedisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);

        if (userIdStr != null && !userIdStr.trim().isEmpty()) {
            return Long.valueOf(userIdStr);
        }

        // 2. Redis 没有时，再兜底查数据库
        QueryWrapper<UserToken> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("token_hash", token)
                .eq("deleted", 0)
                .eq("status", 1)
                .gt("expire_time", LocalDateTime.now());

        UserToken userToken = this.getOne(queryWrapper);

        if (userToken == null) {
            return null;
        }

        return userToken.getUserId();
    }
}
```

#### application

```
spring:
  rabbitmq:
    host: 192.168.59.129
    port: 5672
    username: butler
    password: 123456
    virtual-host: /
```



> 测试
>
> 在浏览器中F12中的console中输入下面内容
>
> ```
> const token = "8d5610bf47074f108dbf701b32342ccc";
> 
> const ws = new WebSocket("ws://localhost:8181/ws/notification?token=" + token);
> 
> ws.onopen = function () {
>     console.log("WebSocket连接成功");
> };
> 
> ws.onmessage = function (event) {
>     console.log("收到服务端推送：", event.data);
> };
> 
> ws.onerror = function (error) {
>     console.log("WebSocket连接异常：", error);
> };
> 
> ws.onclose = function () {
>     console.log("WebSocket连接关闭");
> };
> ```
>
> ```
> ƒ () {
>     console.log("WebSocket连接关闭");
> }
> VM372:6 WebSocket连接成功
> VM372:10 收到服务端推送： {"type":"BUDGET_WARNING","notificationId":1,"recordId":28,"category":"TOTAL","level":"OVER","content":"另外提醒一下，你本月总预算已经超支，当前已支出 999.00 元，预算为 500.00 元。"}
> VM372:10 收到服务端推送： {"type":"BUDGET_WARNING","notificationId":2,"recordId":28,"category":"餐饮","level":"OVER","content":"另外提醒一下，你本月餐饮预算已经超支，当前已支出 855.00 元，预算为 100.00 元。"}
> ```

### 7.3.2通知列表接口

#### UserNotificationDTO

```
package com.test.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserNotificationDTO {

    private Long id;

    private String title;

    private String content;

    private String notifyType;

    private Long businessId;

    /**
     * 0未读，1已读
     */
    private Integer readStatus;

    private LocalDateTime createdTime;
}
```

#### UserNotificationService

```java
package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.UserNotificationDTO;
import com.test.entity.UserNotification;

import java.util.List;

public interface UserNotificationService extends IService<UserNotification> {

    Long createNotification(Long userId,
                            String title,
                            String content,
                            String notifyType,
                            Long businessId);

    List<UserNotificationDTO> listNotifications(Long userId);

    Long countUnread(Long userId);

    void markRead(Long userId, Long notificationId);

    void markAllRead(Long userId);
}
```

#### UserNotificationServiceImpl

```java
package com.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.test.dto.UserNotificationDTO;
import com.test.entity.UserNotification;
import com.test.mapper.UserNotificationMapper;
import com.test.service.UserNotificationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserNotificationServiceImpl
        extends ServiceImpl<UserNotificationMapper, UserNotification>
        implements UserNotificationService {

    @Override
    public Long createNotification(Long userId,
                                   String title,
                                   String content,
                                   String notifyType,
                                   Long businessId) {

        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        UserNotification notification = new UserNotification();

        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setNotifyType(notifyType);
        notification.setBusinessId(businessId);
        notification.setReadStatus(0);
        notification.setDeleted(0);

        this.save(notification);

        return notification.getId();
    }

    @Override
    public List<UserNotificationDTO> listNotifications(Long userId) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        QueryWrapper<UserNotification> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("user_id", userId)
                .eq("deleted", 0)
                .orderByAsc("read_status")
                .orderByDesc("created_time");

        List<UserNotification> notifications = this.list(queryWrapper);

        List<UserNotificationDTO> result = new ArrayList<>();

        for (UserNotification notification : notifications) {
            UserNotificationDTO dto = new UserNotificationDTO();

            dto.setId(notification.getId());
            dto.setTitle(notification.getTitle());
            dto.setContent(notification.getContent());
            dto.setNotifyType(notification.getNotifyType());
            dto.setBusinessId(notification.getBusinessId());
            dto.setReadStatus(notification.getReadStatus());
            dto.setCreatedTime(notification.getCreatedTime());

            result.add(dto);
        }

        return result;
    }

    @Override
    public Long countUnread(Long userId) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        QueryWrapper<UserNotification> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("user_id", userId)
                .eq("read_status", 0)
                .eq("deleted", 0);

        return this.count(queryWrapper);
    }

    @Override
    public void markRead(Long userId, Long notificationId) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        if (notificationId == null) {
            throw new RuntimeException("通知ID不能为空");
        }

        QueryWrapper<UserNotification> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("id", notificationId)
                .eq("user_id", userId)
                .eq("deleted", 0);

        UserNotification notification = this.getOne(queryWrapper);

        if (notification == null) {
            throw new RuntimeException("通知不存在");
        }

        notification.setReadStatus(1);

        this.updateById(notification);
    }

    @Override
    public void markAllRead(Long userId) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        UserNotification update = new UserNotification();
        update.setReadStatus(1);

        QueryWrapper<UserNotification> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("user_id", userId)
                .eq("read_status", 0)
                .eq("deleted", 0);

        this.update(update, queryWrapper);
    }
}
```

#### UserNotificationController

```java
package com.test.controller;

import com.test.common.UserContext;
import com.test.dto.UserNotificationDTO;
import com.test.service.UserNotificationService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notification")
public class UserNotificationController {

    @Autowired
    private UserNotificationService userNotificationService;

    /**
     * 查询通知列表
     */
    @GetMapping("/list")
    public ResultVO<List<UserNotificationDTO>> listNotifications() {

        Long userId = UserContext.getUserId();

        List<UserNotificationDTO> list = userNotificationService.listNotifications(userId);

        return ResultVOUtil.success(list);
    }

    /**
     * 查询未读通知数量
     */
    @GetMapping("/unread/count")
    public ResultVO<Long> countUnread() {

        Long userId = UserContext.getUserId();

        Long count = userNotificationService.countUnread(userId);

        return ResultVOUtil.success(count);
    }

    /**
     * 标记单条通知已读
     */
    @PutMapping("/{notificationId}/read")
    public ResultVO markRead(@PathVariable Long notificationId) {

        Long userId = UserContext.getUserId();

        userNotificationService.markRead(userId, notificationId);

        return ResultVOUtil.success("通知已标记为已读");
    }

    /**
     * 一键全部已读
     */
    @PutMapping("/read/all")
    public ResultVO markAllRead() {

        Long userId = UserContext.getUserId();

        userNotificationService.markAllRead(userId);

        return ResultVOUtil.success("全部通知已标记为已读");
    }
}  
```

> 测试
>
> ```
> /notification/list
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": [
>     {
>       "id": 1,
>       "title": "预算预警",
>       "content": "另外提醒一下，你本月总预算已经超支，当前已支出 999.00 元，预算为 500.00 元。",
>       "notifyType": "BUDGET_WARNING",
>       "businessId": 28,
>       "readStatus": 0,
>       "createdTime": "2026-06-03T11:40:07"
>     },
>     {
>       "id": 2,
>       "title": "预算预警",
>       "content": "另外提醒一下，你本月餐饮预算已经超支，当前已支出 855.00 元，预算为 100.00 元。",
>       "notifyType": "BUDGET_WARNING",
>       "businessId": 28,
>       "readStatus": 0,
>       "createdTime": "2026-06-03T11:40:07"
>     }
>   ]
> }
> ```
>
> ```
> /notification/unread/count
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": 2
> }
> ```
>
> ```
> /notification/{notificationId}/read
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": "通知已标记为已读"
> }
> ```
>
> ```
> /notification/read/all
> ```
>
> ```
> {
>   "code": 200,
>   "msg": "success",
>   "data": "全部通知已标记为已读"
> }
> ```
>
> 

 
