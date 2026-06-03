```sql
CREATE DATABASE IF NOT EXISTS bulter_agent
DEFAULT CHARACTER SET utf8mb4
DEFAULT COLLATE utf8mb4_unicode_ci;

USE bulter_agent;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 1. 用户表
-- =====================================================
DROP TABLE IF EXISTS app_user;

CREATE TABLE app_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',

    username VARCHAR(50) DEFAULT NULL COMMENT '用户名',
    nickname VARCHAR(50) DEFAULT NULL COMMENT '昵称',

    mobile VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    mobile_verified TINYINT NOT NULL DEFAULT 0 COMMENT '手机号是否验证：1已验证，0未验证',

    email VARCHAR(100) DEFAULT NULL COMMENT '邮箱',

    password VARCHAR(255) DEFAULT NULL COMMENT '密码，当前阶段可为空或存加密密码',

    avatar_url VARCHAR(500) DEFAULT NULL COMMENT '头像地址',

    gender TINYINT DEFAULT 0 COMMENT '性别：1男，2女，0未知',

    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常，0禁用',

    last_login_time DATETIME DEFAULT NULL COMMENT '最后登录时间',
    last_login_ip VARCHAR(100) DEFAULT NULL COMMENT '最后登录IP',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    UNIQUE KEY uk_mobile (mobile),
    INDEX idx_status (status),
    INDEX idx_last_login_time (last_login_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '用户表';


-- =====================================================
-- 2. 收支分类表
-- user_id = 0 表示系统默认分类
-- =====================================================
DROP TABLE IF EXISTS finance_category;

CREATE TABLE finance_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分类ID',

    user_id BIGINT NOT NULL DEFAULT 0 COMMENT '用户ID，0表示系统默认分类',

    category_name VARCHAR(50) NOT NULL COMMENT '分类名称：餐饮、饮品、交通等',

    record_type VARCHAR(20) NOT NULL COMMENT '类型：EXPENSE支出，INCOME收入',

    icon VARCHAR(100) DEFAULT NULL COMMENT '分类图标，可选',

    sort INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',

    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0禁用',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    UNIQUE KEY uk_user_type_name (user_id, record_type, category_name),
    INDEX idx_user_type (user_id, record_type),
    INDEX idx_enabled (enabled)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '收支分类表';


-- =====================================================
-- 3. 账户表
-- 用于后续区分现金、微信、支付宝、银行卡等
-- 当前阶段可以先不用
-- =====================================================
DROP TABLE IF EXISTS finance_account;

CREATE TABLE finance_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '账户ID',

    user_id BIGINT NOT NULL COMMENT '用户ID',

    account_name VARCHAR(50) NOT NULL COMMENT '账户名称：默认账户、现金、微信、支付宝、银行卡',

    account_type VARCHAR(30) DEFAULT NULL COMMENT '账户类型：CASH、WECHAT、ALIPAY、BANK_CARD、OTHER',

    balance DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '账户余额，可选',

    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0禁用',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    UNIQUE KEY uk_user_account_name (user_id, account_name),
    INDEX idx_user_id (user_id),
    INDEX idx_enabled (enabled)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '账户表';


-- =====================================================
-- 4. 收入支出记录表
-- 当前 Agent 记账最核心的表
-- =====================================================
DROP TABLE IF EXISTS expense_record;

CREATE TABLE expense_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    user_id BIGINT NOT NULL COMMENT '用户ID',

    amount DECIMAL(10, 2) NOT NULL COMMENT '金额',

    category_id BIGINT DEFAULT NULL COMMENT '分类ID，可选，关联 finance_category.id',

    category VARCHAR(50) NOT NULL COMMENT '分类名称冗余字段：餐饮、饮品、交通等',

    record_type VARCHAR(20) NOT NULL DEFAULT 'EXPENSE' COMMENT '记录类型：EXPENSE支出，INCOME收入',

    description VARCHAR(255) DEFAULT NULL COMMENT '记录描述，例如奶茶、午饭、打车',

    expense_time DATE NOT NULL COMMENT '记账日期，用于日历和月度统计',

    expense_datetime DATETIME DEFAULT NULL COMMENT '具体发生时间，可选',

    account_id BIGINT DEFAULT NULL COMMENT '账户ID，可选，关联 finance_account.id',

    source_type VARCHAR(30) NOT NULL DEFAULT 'AGENT' COMMENT '来源类型：AGENT、MANUAL、IMPORT',

    source_text VARCHAR(500) DEFAULT NULL COMMENT '原始输入文本，例如：今天奶茶18',

    session_id VARCHAR(100) DEFAULT NULL COMMENT 'Agent会话ID，方便追踪是哪次对话生成的记录',

    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    INDEX idx_user_id (user_id),
    INDEX idx_user_time (user_id, expense_time),
    INDEX idx_user_record_type (user_id, record_type),
    INDEX idx_user_category (user_id, category),
    INDEX idx_user_source_type (user_id, source_type),
    INDEX idx_session_id (session_id),
    INDEX idx_account_id (account_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '收入支出记录表';


-- =====================================================
-- 5. 预算表
-- category = TOTAL 表示月度总预算
-- =====================================================
DROP TABLE IF EXISTS budget;

CREATE TABLE budget (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '预算ID',

    user_id BIGINT NOT NULL COMMENT '用户ID',

    budget_month VARCHAR(7) NOT NULL COMMENT '预算月份，格式：yyyy-MM，例如 2026-05',

    category_id BIGINT DEFAULT NULL COMMENT '分类ID，可选',

    category VARCHAR(50) NOT NULL COMMENT '预算类别：TOTAL表示总预算，餐饮、交通等表示分类预算',

    amount DECIMAL(10, 2) NOT NULL COMMENT '预算金额',

    warning_rate DECIMAL(5, 2) NOT NULL DEFAULT 80.00 COMMENT '预警比例，例如80表示使用80%时预警',

    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0禁用',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    UNIQUE KEY uk_user_month_category (user_id, budget_month, category),

    INDEX idx_user_month (user_id, budget_month),
    INDEX idx_user_category (user_id, category),
    INDEX idx_enabled (enabled)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '预算表';


-- =====================================================
-- 6. 预算预警日志表
-- 后续短信提醒、Redis异步预警时使用
-- =====================================================
DROP TABLE IF EXISTS budget_warning_log;

CREATE TABLE budget_warning_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    user_id BIGINT NOT NULL COMMENT '用户ID',

    budget_month VARCHAR(7) NOT NULL COMMENT '预算月份，格式：yyyy-MM',

    category VARCHAR(50) NOT NULL COMMENT '预算类别：TOTAL或具体分类',

    budget_amount DECIMAL(10, 2) DEFAULT 0.00 COMMENT '预算金额',

    used_amount DECIMAL(10, 2) DEFAULT 0.00 COMMENT '已使用金额',

    usage_rate DECIMAL(6, 2) DEFAULT 0.00 COMMENT '使用率',

    warning_level VARCHAR(30) NOT NULL COMMENT '预警等级：NORMAL、WARNING、DANGER、OVER',

    warning_message VARCHAR(500) DEFAULT NULL COMMENT '预警文案',

    notify_type VARCHAR(30) DEFAULT NULL COMMENT '通知方式：SYSTEM、SMS、EMAIL',

    notify_status TINYINT NOT NULL DEFAULT 0 COMMENT '通知状态：0未通知，1已通知，2通知失败',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_user_month (user_id, budget_month),
    INDEX idx_warning_level (warning_level),
    INDEX idx_notify_status (notify_status),
    INDEX idx_create_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '预算预警日志表';


-- =====================================================
-- 7. Agent 会话表
-- 当前 LangChain4j 使用内存记忆，后续可扩展为数据库持久化
-- =====================================================
DROP TABLE IF EXISTS agent_chat_session;

CREATE TABLE agent_chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    user_id BIGINT NOT NULL COMMENT '用户ID',

    session_id VARCHAR(100) NOT NULL COMMENT '会话ID',

    title VARCHAR(100) DEFAULT NULL COMMENT '会话标题',

    last_message VARCHAR(500) DEFAULT NULL COMMENT '最后一条消息摘要',

    last_active_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    UNIQUE KEY uk_session_id (session_id),
    INDEX idx_user_id (user_id),
    INDEX idx_user_last_time (user_id, last_active_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent会话表';


-- =====================================================
-- 8. Agent 对话消息表
-- =====================================================
DROP TABLE IF EXISTS agent_chat_message;

CREATE TABLE agent_chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    user_id BIGINT NOT NULL COMMENT '用户ID',

    session_id VARCHAR(100) NOT NULL COMMENT '会话ID',

    role VARCHAR(20) NOT NULL COMMENT '消息角色：USER、ASSISTANT、SYSTEM',

    content TEXT NOT NULL COMMENT '消息内容',

    intent VARCHAR(50) DEFAULT NULL COMMENT 'AI识别意图',

    final_intent VARCHAR(50) DEFAULT NULL COMMENT '后端最终意图',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    INDEX idx_user_session (user_id, session_id),
    INDEX idx_session_time (session_id, create_time),
    INDEX idx_intent (intent),
    INDEX idx_final_intent (final_intent)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent对话消息表';


-- =====================================================
-- 9. Agent 执行动作日志表
-- 记录 Agent 是否真的执行了记账、查询、预算等动作
-- =====================================================
DROP TABLE IF EXISTS agent_action_log;

CREATE TABLE agent_action_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    user_id BIGINT NOT NULL COMMENT '用户ID',

    session_id VARCHAR(100) DEFAULT NULL COMMENT '会话ID',

    action_name VARCHAR(50) NOT NULL COMMENT '动作名称：recordExpense、queryExpense、setBudget等',

    success TINYINT NOT NULL DEFAULT 1 COMMENT '是否成功：1成功，0失败',

    message VARCHAR(500) DEFAULT NULL COMMENT '动作说明',

    request_text VARCHAR(500) DEFAULT NULL COMMENT '用户原始输入',

    result_data TEXT DEFAULT NULL COMMENT '动作结果JSON，可选',

    related_record_id BIGINT DEFAULT NULL COMMENT '关联的记录ID，例如expense_record.id',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_user_session (user_id, session_id),
    INDEX idx_action_name (action_name),
    INDEX idx_related_record_id (related_record_id),
    INDEX idx_create_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent动作日志表';


-- =====================================================
-- 10. Agent 意图规则表
-- 后续做“动态规则 + Redis缓存”时使用
-- =====================================================
DROP TABLE IF EXISTS agent_intent_rule;

CREATE TABLE agent_intent_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    intent VARCHAR(50) NOT NULL COMMENT '意图类型，例如 QUERY_EXPENSE、SET_BUDGET',

    keyword VARCHAR(100) NOT NULL COMMENT '关键词，例如 花了多少钱、本月开销',

    match_type VARCHAR(20) NOT NULL DEFAULT 'CONTAINS' COMMENT '匹配类型：CONTAINS包含，EQUALS完全匹配，REGEX正则',

    priority INT NOT NULL DEFAULT 0 COMMENT '优先级，数字越大越优先',

    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0禁用',

    source VARCHAR(50) NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL手动添加，AI_RECOMMEND AI推荐，SYSTEM系统内置',

    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    UNIQUE KEY uk_intent_keyword (intent, keyword),

    INDEX idx_intent (intent),
    INDEX idx_enabled (enabled),
    INDEX idx_priority (priority)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent意图识别规则表';


-- =====================================================
-- 11. Agent 意图规则候选表
-- AI总结用户常用表达后，可先进入候选表，人工确认后再加入正式规则
-- =====================================================
DROP TABLE IF EXISTS agent_intent_rule_candidate;

CREATE TABLE agent_intent_rule_candidate (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    intent VARCHAR(50) NOT NULL COMMENT '建议意图类型',

    keyword VARCHAR(100) NOT NULL COMMENT '候选关键词',

    hit_count INT NOT NULL DEFAULT 1 COMMENT '命中次数',

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING待审核，APPROVED已通过，REJECTED已拒绝',

    reason VARCHAR(500) DEFAULT NULL COMMENT '推荐原因',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_intent_keyword_candidate (intent, keyword),

    INDEX idx_status (status),
    INDEX idx_intent (intent)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent意图规则候选表';


-- =====================================================
-- 12. 分析报告表
-- 后续做日报、周报、月报时使用
-- =====================================================
DROP TABLE IF EXISTS analysis_report;

CREATE TABLE analysis_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    user_id BIGINT NOT NULL COMMENT '用户ID',

    report_type VARCHAR(20) NOT NULL COMMENT '报告类型：DAY日报，WEEK周报，MONTH月报',

    report_period VARCHAR(20) NOT NULL COMMENT '报告周期，例如 2026-05',

    total_expense DECIMAL(10, 2) DEFAULT 0.00 COMMENT '周期总支出',

    total_income DECIMAL(10, 2) DEFAULT 0.00 COMMENT '周期总收入',

    balance DECIMAL(10, 2) DEFAULT 0.00 COMMENT '结余：收入 - 支出',

    content TEXT COMMENT '报告内容',

    source VARCHAR(50) NOT NULL DEFAULT 'SYSTEM' COMMENT '来源：SYSTEM系统生成，AI_AGENT AI生成',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    UNIQUE KEY uk_user_report_period (user_id, report_type, report_period),

    INDEX idx_user_report (user_id, report_type, report_period)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '消费分析报告表';


-- =====================================================
-- 13. 用户登录日志表
-- =====================================================
DROP TABLE IF EXISTS user_login_log;

CREATE TABLE user_login_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    user_id BIGINT DEFAULT NULL COMMENT '用户ID，登录失败时可能为空',

    mobile VARCHAR(20) DEFAULT NULL COMMENT '手机号',

    login_type VARCHAR(30) NOT NULL COMMENT '登录方式：PASSWORD、SMS_CODE、TOKEN',

    login_status TINYINT NOT NULL COMMENT '登录状态：1成功，0失败',

    fail_reason VARCHAR(255) DEFAULT NULL COMMENT '失败原因',

    ip_address VARCHAR(100) DEFAULT NULL COMMENT '登录IP',

    user_agent VARCHAR(500) DEFAULT NULL COMMENT '浏览器或客户端信息',

    device_id VARCHAR(100) DEFAULT NULL COMMENT '设备ID，可选',

    login_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',

    INDEX idx_user_id (user_id),
    INDEX idx_mobile (mobile),
    INDEX idx_login_time (login_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '用户登录日志表';


-- =====================================================
-- 14. 用户 Token 表
-- 建议存 token_hash，不存明文 token
-- =====================================================
DROP TABLE IF EXISTS user_token;

CREATE TABLE user_token (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    user_id BIGINT NOT NULL COMMENT '用户ID',

    token_hash VARCHAR(128) NOT NULL COMMENT '访问Token摘要，不建议存明文Token',

    refresh_token_hash VARCHAR(128) DEFAULT NULL COMMENT '刷新Token摘要',

    token_type VARCHAR(30) NOT NULL DEFAULT 'JWT' COMMENT 'Token类型：JWT、SESSION',

    device_id VARCHAR(100) DEFAULT NULL COMMENT '设备ID',

    device_name VARCHAR(100) DEFAULT NULL COMMENT '设备名称',

    ip_address VARCHAR(100) DEFAULT NULL COMMENT '登录IP',

    expire_time DATETIME NOT NULL COMMENT '过期时间',

    refresh_expire_time DATETIME DEFAULT NULL COMMENT '刷新Token过期时间',

    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1有效，0失效',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    UNIQUE KEY uk_token_hash (token_hash),
    UNIQUE KEY uk_refresh_token_hash (refresh_token_hash),

    INDEX idx_user_id (user_id),
    INDEX idx_expire_time (expire_time),
    INDEX idx_device_id (device_id),
    INDEX idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '用户Token表';


-- =====================================================
-- 15. 短信发送日志表
-- 验证码建议放Redis，这里只存发送记录和code_hash
-- =====================================================
DROP TABLE IF EXISTS sms_send_log;

CREATE TABLE sms_send_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    mobile VARCHAR(20) NOT NULL COMMENT '手机号',

    scene VARCHAR(50) NOT NULL COMMENT '短信场景：REGISTER、LOGIN、RESET_PASSWORD、BUDGET_WARNING',

    template_code VARCHAR(100) DEFAULT NULL COMMENT '短信模板编号',

    content VARCHAR(500) DEFAULT NULL COMMENT '短信内容，可选，不建议存完整验证码',

    code_hash VARCHAR(255) DEFAULT NULL COMMENT '验证码哈希值，可选，不建议明文存验证码',

    provider VARCHAR(50) DEFAULT NULL COMMENT '短信服务商：ALIYUN、TENCENT等',

    provider_request_id VARCHAR(100) DEFAULT NULL COMMENT '短信服务商请求ID',

    send_status TINYINT NOT NULL DEFAULT 0 COMMENT '发送状态：1成功，0失败',

    fail_reason VARCHAR(500) DEFAULT NULL COMMENT '失败原因',

    ip_address VARCHAR(100) DEFAULT NULL COMMENT '请求IP',

    expire_time DATETIME DEFAULT NULL COMMENT '验证码过期时间',

    used TINYINT NOT NULL DEFAULT 0 COMMENT '是否已使用：1已使用，0未使用',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_mobile (mobile),
    INDEX idx_scene (scene),
    INDEX idx_create_time (create_time),
    INDEX idx_mobile_scene_time (mobile, scene, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '短信发送日志表';


-- =====================================================
-- 16. 短信验证码校验日志表
-- =====================================================
DROP TABLE IF EXISTS sms_verify_log;

CREATE TABLE sms_verify_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    mobile VARCHAR(20) NOT NULL COMMENT '手机号',

    scene VARCHAR(50) NOT NULL COMMENT '短信场景：REGISTER、LOGIN、RESET_PASSWORD',

    verify_status TINYINT NOT NULL COMMENT '验证状态：1成功，0失败',

    fail_reason VARCHAR(255) DEFAULT NULL COMMENT '失败原因：验证码错误、验证码过期、次数过多等',

    ip_address VARCHAR(100) DEFAULT NULL COMMENT '请求IP',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_mobile (mobile),
    INDEX idx_scene (scene),
    INDEX idx_create_time (create_time),
    INDEX idx_mobile_scene_time (mobile, scene, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '短信验证码校验日志表';


-- =====================================================
-- 17. 系统配置表
-- 后续可把短信过期时间、预算预警阈值、Agent配置放进来
-- =====================================================
DROP TABLE IF EXISTS system_config;

CREATE TABLE system_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    config_key VARCHAR(100) NOT NULL COMMENT '配置键',

    config_value VARCHAR(500) NOT NULL COMMENT '配置值',

    config_group VARCHAR(50) DEFAULT 'DEFAULT' COMMENT '配置分组',

    description VARCHAR(255) DEFAULT NULL COMMENT '配置说明',

    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0禁用',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    UNIQUE KEY uk_config_key (config_key),
    INDEX idx_config_group (config_group),
    INDEX idx_enabled (enabled)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '系统配置表';


-- =====================================================
-- 18. Redis 缓存版本表
-- 可选，用于缓存刷新和版本控制
-- =====================================================
DROP TABLE IF EXISTS redis_cache_version;

CREATE TABLE redis_cache_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    cache_key VARCHAR(100) NOT NULL COMMENT '缓存业务键，例如 AGENT_INTENT_RULE',

    version BIGINT NOT NULL DEFAULT 1 COMMENT '缓存版本号',

    description VARCHAR(255) DEFAULT NULL COMMENT '说明',

    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_cache_key (cache_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Redis缓存版本表';


-- =====================================================
-- 19. 系统操作日志表
-- 后台管理、规则修改、短信发送、预算修改等可记录
-- =====================================================
DROP TABLE IF EXISTS operation_log;

CREATE TABLE operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    user_id BIGINT DEFAULT NULL COMMENT '操作用户ID',

    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型：CREATE、UPDATE、DELETE、LOGIN、SEND_SMS等',

    module_name VARCHAR(50) NOT NULL COMMENT '模块名称：EXPENSE、BUDGET、AGENT_RULE、SMS等',

    request_uri VARCHAR(255) DEFAULT NULL COMMENT '请求路径',

    request_method VARCHAR(20) DEFAULT NULL COMMENT '请求方法',

    request_params TEXT DEFAULT NULL COMMENT '请求参数',

    operation_result TINYINT NOT NULL DEFAULT 1 COMMENT '操作结果：1成功，0失败',

    error_message VARCHAR(500) DEFAULT NULL COMMENT '错误信息',

    ip_address VARCHAR(100) DEFAULT NULL COMMENT 'IP地址',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_user_id (user_id),
    INDEX idx_module_name (module_name),
    INDEX idx_operation_type (operation_type),
    INDEX idx_create_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '系统操作日志表';


-- =====================================================
-- 初始化数据
-- =====================================================

-- 1. 初始化测试用户
INSERT INTO app_user (id, username, nickname, mobile, mobile_verified, email, password)
VALUES (1, 'test_user', '测试用户', '13300000000', 1, 'test@example.com', NULL)
ON DUPLICATE KEY UPDATE
    username = VALUES(username),
    nickname = VALUES(nickname),
    mobile_verified = VALUES(mobile_verified),
    email = VALUES(email),
    update_time = CURRENT_TIMESTAMP;


-- 2. 初始化系统默认分类
INSERT INTO finance_category (user_id, category_name, record_type, sort)
VALUES
    (0, '餐饮', 'EXPENSE', 1),
    (0, '饮品', 'EXPENSE', 2),
    (0, '交通', 'EXPENSE', 3),
    (0, '购物', 'EXPENSE', 4),
    (0, '学习', 'EXPENSE', 5),
    (0, '娱乐', 'EXPENSE', 6),
    (0, '医疗', 'EXPENSE', 7),
    (0, '住房', 'EXPENSE', 8),
    (0, '其他', 'EXPENSE', 99),

    (0, '工资', 'INCOME', 1),
    (0, '兼职', 'INCOME', 2),
    (0, '红包', 'INCOME', 3),
    (0, '奖金', 'INCOME', 4),
    (0, '退款', 'INCOME', 5),
    (0, '其他', 'INCOME', 99)
ON DUPLICATE KEY UPDATE
    sort = VALUES(sort),
    update_time = CURRENT_TIMESTAMP;


-- 3. 初始化默认账户
INSERT INTO finance_account (user_id, account_name, account_type, balance)
VALUES
    (1, '默认账户', 'OTHER', 0.00)
ON DUPLICATE KEY UPDATE
    account_type = VALUES(account_type),
    update_time = CURRENT_TIMESTAMP;


-- 4. 初始化测试预算
INSERT INTO budget (user_id, budget_month, category, amount, warning_rate)
VALUES
    (1, '2026-05', 'TOTAL', 1800.00, 80.00),
    (1, '2026-05', '餐饮', 800.00, 80.00),
    (1, '2026-05', '饮品', 300.00, 80.00),
    (1, '2026-05', '交通', 300.00, 80.00)
ON DUPLICATE KEY UPDATE
    amount = VALUES(amount),
    warning_rate = VALUES(warning_rate),
    update_time = CURRENT_TIMESTAMP;


-- 5. 初始化 Agent 意图规则
INSERT INTO agent_intent_rule (intent, keyword, match_type, priority, source, remark)
VALUES
    ('QUERY_EXPENSE', '多少钱', 'CONTAINS', 100, 'SYSTEM', '消费查询关键词'),
    ('QUERY_EXPENSE', '花了多少', 'CONTAINS', 100, 'SYSTEM', '消费查询关键词'),
    ('QUERY_EXPENSE', '一共消费', 'CONTAINS', 100, 'SYSTEM', '消费查询关键词'),
    ('QUERY_EXPENSE', '总共消费', 'CONTAINS', 100, 'SYSTEM', '消费查询关键词'),
    ('QUERY_EXPENSE', '消费多少', 'CONTAINS', 100, 'SYSTEM', '消费查询关键词'),
    ('QUERY_EXPENSE', '本月开销', 'CONTAINS', 90, 'SYSTEM', '消费查询关键词'),

    ('ANALYZE_EXPENSE', '分析', 'CONTAINS', 100, 'SYSTEM', '消费分析关键词'),
    ('ANALYZE_EXPENSE', '消费情况', 'CONTAINS', 100, 'SYSTEM', '消费分析关键词'),
    ('ANALYZE_EXPENSE', '消费结构', 'CONTAINS', 100, 'SYSTEM', '消费分析关键词'),

    ('SET_BUDGET', '设置预算', 'CONTAINS', 100, 'SYSTEM', '预算设置关键词'),
    ('SET_BUDGET', '预算设置', 'CONTAINS', 100, 'SYSTEM', '预算设置关键词'),
    ('SET_BUDGET', '预算为', 'CONTAINS', 90, 'SYSTEM', '预算设置关键词'),
    ('SET_BUDGET', '预算设置为', 'CONTAINS', 100, 'SYSTEM', '预算设置关键词'),

    ('BUDGET_RISK', '超预算', 'CONTAINS', 100, 'SYSTEM', '预算风险关键词'),
    ('BUDGET_RISK', '超支', 'CONTAINS', 100, 'SYSTEM', '预算风险关键词'),
    ('BUDGET_RISK', '够不够', 'CONTAINS', 90, 'SYSTEM', '预算风险关键词'),
    ('BUDGET_RISK', '还能控制', 'CONTAINS', 90, 'SYSTEM', '预算风险关键词')
ON DUPLICATE KEY UPDATE
    priority = VALUES(priority),
    remark = VALUES(remark),
    update_time = CURRENT_TIMESTAMP;


-- 6. 初始化系统配置
INSERT INTO system_config (config_key, config_value, config_group, description)
VALUES
    ('sms.code.expire.minutes', '5', 'SMS', '短信验证码有效期，单位分钟'),
    ('sms.send.cooldown.seconds', '60', 'SMS', '同一手机号短信发送冷却时间，单位秒'),
    ('sms.send.max.per.day', '10', 'SMS', '同一手机号每日最大发送次数'),
    ('budget.warning.rate.default', '80', 'BUDGET', '默认预算预警比例'),
    ('agent.memory.max.messages', '10', 'AGENT', 'Agent会话记忆窗口大小'),
    ('agent.intent.rule.cache.version', '1', 'AGENT', 'Agent意图规则缓存版本')
ON DUPLICATE KEY UPDATE
    config_value = VALUES(config_value),
    update_time = CURRENT_TIMESTAMP;


-- 7. 初始化 Redis 缓存版本
INSERT INTO redis_cache_version (cache_key, version, description)
VALUES
    ('AGENT_INTENT_RULE', 1, 'Agent意图规则缓存版本'),
    ('SYSTEM_CONFIG', 1, '系统配置缓存版本'),
    ('BUDGET_CONFIG', 1, '预算配置缓存版本')
ON DUPLICATE KEY UPDATE
    version = VALUES(version),
    update_time = CURRENT_TIMESTAMP;

SET FOREIGN_KEY_CHECKS = 1;
```

