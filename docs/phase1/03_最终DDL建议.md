# 阶段 1 · 最终 DDL 建议（system_account / 关联表 / merchant 域 / oauth2 扩展）

> 改造任务：《农e链全局账号与多租户认证改造执行方案》阶段 1 交付物。
> **本文件仅为建议稿，未经授权不执行任何数据库变更。**
> 前提（已经用户确认）：`merchant_org` / `merchant_org_apply` / `merchant_user` 仓库中不存在，按全新设计；`apply_user_id` 自始关联 `system_account.id`，无历史映射迁移。
> 兼容性假设：MySQL 5.7 / 8.0 双兼容写法（不使用函数索引、CHECK 约束不依赖 8.0.16+ 强制语义、无外键）。

---

## 1. 设计总则：逻辑删除与唯一约束的共存方案（先决策，后建表）

### 1.1 问题

项目统一使用 `deleted bit(1) NOT NULL DEFAULT b'0'` + `@TableLogic`（BaseDO）。MySQL 唯一索引中：

- `bit` 列只有 0/1 两值 → 唯一键 `(mobile, deleted)` 下，**第二次逻辑删除同 mobile 的行必然撞键**；
- MySQL 不支持部分索引（partial index）；
- 唯一索引中 `NULL` 可重复，但该特性对"有效行唯一"恰好帮倒忙（有效行必须非 NULL 才能参与唯一性）。

而方案 §2.2/§2.3/§8 明确要求"数据库唯一约束保证唯一"（手机号、account+tenant、credit_code、个人身份），不能只靠 Java 先查后插。

### 1.2 备选方案对比

| 方案 | 做法 | 优点 | 缺点 / 风险 |
|---|---|---|---|
| A. 唯一键含 deleted | `UNIQUE(mobile, deleted)` | 无结构变化 | **不可行**：两次逻辑删除即撞键（bit 二值） |
| B. delete 戳列 | 新增 `delete_stamp bigint NOT NULL DEFAULT 0`，删除时置为行 id；唯一键 `(mobile, delete_stamp)` | 数学上完备，可无限次软删 | 需要覆盖 `@TableLogic` 删除行为（删除时同时更新 stamp），与全仓库 BaseDO 习惯不一致，所有新表 DO/Service 要特殊处理，心智负担大 |
| C. **状态代删（推荐）** | 新表保留 `deleted bit(1) DEFAULT 0` 列以兼容 BaseDO，但**业务规则禁止逻辑删除这四张表**：生命周期一律用 `status` 表达（账号停用、关联停用、主体停用、申请驳回/取消均为状态迁移，不删行）；唯一键直接建在业务键上（deleted 恒 0，不参与键） | 结构最简；与状态机业务天然吻合（方案本身就是全状态机语义）；无特殊删除行为 | 若未来某表确需软删，需在那时引入 delete_stamp 方案重建索引（对小表成本低）；需在代码层防误调 deleteById（Service 不暴露删除方法即可） |
| D. 物理删除 | 关联失效即 DELETE 行 | 无冲突 | 丢失审计轨迹，违反方案"不删除历史/关系可追溯"精神 |

**推荐 C**：四张新表全部满足"状态代删"的业务前提——

- `system_account`：停用/恢复是 status，不存在删除场景（账号有申请、登录历史关联）；
- `system_account_user_rel`："关联失效"= status 停用（方案 §2.3：单个租户关联失效不影响其他租户；恢复可复用同一行 UPDATE 回有效，天然幂等）；
- `merchant_org`：主体状态流转（正常/停用/注销），不删行；
- `merchant_org_apply`：驳回重提 = UPDATE 原记录（方案 §5.5），取消 = status=3，均不删行；**一 credit_code 一条申请记录**，全表唯一。

并发兜底统一模式：**唯一键 + 捕获 `DuplicateKeyException` 后重查**（方案 §8.1），Java 层先查后插仅作友好提示，不作为并发保障。

### 1.3 其他通用约定

- 全部 InnoDB / utf8mb4，主键 `id bigint AUTO_INCREMENT`，审计列与全库一致（creator/create_time/updater/update_time）；
- 无外键（项目风格），引用完整性由应用层校验（如 rel 的 system_user_id 必须属于 tenant_id，在 Service 校验）；
- 全局表 DO 显式 `@TenantIgnore`（`TenantDatabaseInterceptor` 对 MP 实体默认拦截，不加注解会因表无 tenant_id 列直接 SQL 报错——证据：`<biz-tenant>/core/db/TenantDatabaseInterceptor.java:68-81`）；
- 时间列 `datetime`（与全库一致，不用 timestamp）。

---

## 2. `system_account`（全局手机号账号，全局表）

```sql
CREATE TABLE `system_account` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '全局账号编号',
    `mobile`      varchar(11)  NOT NULL COMMENT '手机号（全局唯一登录标识，已规范化：11 位数字）',
    `status`      tinyint      NOT NULL DEFAULT 0 COMMENT '账号状态（0正常 1停用）（CommonStatusEnum）',
    `creator`     varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除（本表禁用逻辑删除，恒为 0）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_mobile` (`mobile`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COMMENT = '全局手机号账号表';
```

设计说明（对照方案 §2.2 要求）：

| 要求 | 落实 |
|---|---|
| 手机号唯一约束 | `uk_mobile`（有效行唯一由"禁删行"保证，§1.2-C） |
| 不保存明文短信验证码 | 验证码仍在 `system_sms_code`（原生机制）；是否哈希化列为待确认项（02 报告 §3.4），与本表无关 |
| 不绑定单一 tenant_id | 表内无 tenant_id 列；DO 加 `@TenantIgnore`、继承 BaseDO |
| 不存储租户角色权限 | 纯身份表，仅 mobile + status |
| 不与 system_user.id 混用 | 独立主键空间；登录响应同时返回 accountId 与 userId 两个字段（01 报告 §4.2） |

> 不设 nickname/avatar 等资料字段：展示资料在各租户 `system_users.nickname`，全局层保持最小。若后续需要跨租户昵称，加列即可（可逆决策）。

## 3. `system_account_user_rel`（账号-租户用户关联，全局表）

```sql
CREATE TABLE `system_account_user_rel` (
    `id`             bigint   NOT NULL AUTO_INCREMENT COMMENT '关联编号',
    `account_id`     bigint   NOT NULL COMMENT '全局账号编号（system_account.id）',
    `tenant_id`      bigint   NOT NULL COMMENT '租户编号（system_tenant.id）',
    `system_user_id` bigint   NOT NULL COMMENT '该租户下的管理用户编号（system_users.id，须属于 tenant_id）',
    `status`         tinyint  NOT NULL DEFAULT 0 COMMENT '关联状态（0有效 1停用）（本表禁用逻辑删除，失效=停用）',
    `creator`        varchar(64) NULL DEFAULT '' COMMENT '创建者',
    `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`        varchar(64) NULL DEFAULT '' COMMENT '更新者',
    `update_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        bit(1)   NOT NULL DEFAULT b'0' COMMENT '是否删除（恒为 0）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_account_tenant` (`account_id`, `tenant_id`) USING BTREE,
    UNIQUE KEY `uk_system_user` (`system_user_id`) USING BTREE,
    INDEX `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COMMENT = '全局账号与租户管理用户关联表';
```

设计说明（对照方案 §2.3 核心约束）：

| 约束 | 落实 |
|---|---|
| 同一账号同租户仅一条有效关联 | `uk_account_tenant`（停用行也占用唯一键 → "恢复"复用原行 UPDATE status，不新增行；方案语义"一个账号可以关联多个租户，一租户一条有效关联"成立） |
| 一个 system_user 不能被多个全局账号绑定 | `uk_system_user` 全局唯一（system_users.id 本身全局唯一） |
| system_user_id 必须属于 tenant_id | 应用层校验（无外键风格）；迁移/写入侧由 02 报告 §7.4-3 核对 SQL 兜底验证 |
| 单租户关联失效不影响其他租户 | 行级 status，互不影响 |
| 跨模块读 | 该表在 system 模块，merchant 模块经 `SystemAccountApi` 访问（01 报告 §4.3） |

> 说明：`tenant_id` 在本表是**业务数据列**而非隔离列——表本身是全局表（DO @TenantIgnore），查询按 account_id 维度跨租户进行；登录后加载某租户上下文时按 account_id+tenant_id 精确取行。

## 4. `merchant_org`（正式经营主体）

```sql
CREATE TABLE `merchant_org` (
    `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主体编号',
    `account_type`       tinyint      NOT NULL COMMENT '主体类型（1 个人商户 2 企业/个体户）',
    `tenant_id`          bigint       NOT NULL COMMENT '对应租户编号（system_tenant.id，正式开通后写入，唯一）',
    `apply_id`           bigint       NULL DEFAULT NULL COMMENT '来源申请编号（merchant_org_apply.id，个人商户免审为 NULL）',
    `short_name`         varchar(64)  NOT NULL COMMENT '主体简称',
    `contact_name`       varchar(30)  NOT NULL COMMENT '联系人姓名',
    `contact_mobile`     varchar(11)  NOT NULL COMMENT '联系手机号',
    `enterprise_name`    varchar(128) NULL DEFAULT NULL COMMENT '企业名称（account_type=2 必填）',
    `credit_code`        varchar(32)  NULL DEFAULT NULL COMMENT '统一社会信用代码（account_type=2 必填，全库唯一）',
    `id_card_hash`       varchar(128) NULL DEFAULT NULL COMMENT '个人身份证哈希（HMAC-SHA256(规范化身份证号, 服务端密钥)，account_type=1 必填，唯一）',
    `status`             tinyint      NOT NULL DEFAULT 0 COMMENT '主体状态（0正常 1停用）',
    `remark`             varchar(500) NULL DEFAULT NULL COMMENT '备注',
    `creator`            varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`            varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除（恒为 0）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_tenant_id` (`tenant_id`) USING BTREE,
    UNIQUE KEY `uk_apply_id` (`apply_id`) USING BTREE,
    UNIQUE KEY `uk_credit_code` (`credit_code`) USING BTREE,
    UNIQUE KEY `uk_id_card_hash` (`id_card_hash`) USING BTREE,
    INDEX `idx_account_type` (`account_type`) USING BTREE,
    INDEX `idx_short_name` (`short_name`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COMMENT = '商户正式经营主体表';
```

设计说明（对照方案 §2.5）：

1. **一租户一主体**：`uk_tenant_id`（方案要求保留的唯一索引语义，此处为新建）。
2. **uk_apply_id**：MySQL 唯一索引允许多个 `NULL` → 个人商户（免审、无申请）不冲突；企业开通时写入申请 id，防同一申请重复开通主体（与审核侧状态机双保险）。
3. **credit_code 数据库级唯一**：方案问"是否需要增加数据库级唯一性保障"——**建议加**。审核通过链路是"再查 apply/org 的 credit_code → 建 org"，并发窗口内两个申请同信用代码通过时，`uk_credit_code`（org 表）+ `uk_credit_code`（apply 表，见 §5）双层兜底；企业名称保持普通索引（同名不同码是合法业务）。
4. **id_card_hash**：个人身份唯一性（方案 §8.4"同一身份不能创建多个有效主体"）由 `uk_id_card_hash` 数据库兜底。哈希方案建议：
   - 规范化：去首尾空格、字母 X 大写；
   - `HMAC-SHA256(规范化值, 密钥)`，密钥从环境变量注入（如 `AGRILEDGER_IDCARD_HMAC_KEY`），**不落源码/配置文件**；
   - 不存身份证原文（个人商户登记若需回显，另评审加密存储与密钥管理，属待确认项）。
5. **不设银行账户字段**（用户决定，2026-11-06）：`bank_account_name` / `bank_account_no` / `bank_name` 三字段从建表建议中移除；方案 §2.5 中"银行账户三字段同进同出、日志不得输出完整银行账号"等要求随之不再适用。若未来需要收款账户信息，建议届时单独立表并整体评审加密与脱敏方案（列预留属可逆决策）。
6. 本表归 merchant 模块；`tenant_id` 为业务归属列。**租户隔离取舍**：本表**不加 @TenantIgnore 与 TenantBaseDO 语义绑定**，而按"平台+商户双视角"设计——平台审核需要跨租户查询所有主体，商户端查询自带租户上下文。推荐 DO 继承 BaseDO + `@TenantIgnore`（全局表，Service 层显式按 tenant_id 过滤），避免 TenantLineInterceptor 自动改写 SQL 干扰审核列表；这与 `system_tenant` 表自身的处理方式一致（全局表，业务列表达归属）。

## 5. `merchant_org_apply`（企业/个体户入驻申请）

```sql
CREATE TABLE `merchant_org_apply` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '申请编号',
    `apply_user_id`   bigint       NOT NULL COMMENT '申请人全局账号编号（system_account.id）',
    `account_type`    tinyint      NOT NULL COMMENT '主体类型（2 企业/个体户；本表仅受理需审核类型）',
    `enterprise_name` varchar(128) NOT NULL COMMENT '企业名称',
    `credit_code`     varchar(32)  NOT NULL COMMENT '统一社会信用代码（全库唯一，一条信用代码一条申请记录）',
    `contact_name`    varchar(30)  NOT NULL COMMENT '联系人姓名',
    `contact_mobile`  varchar(11)  NOT NULL COMMENT '联系手机号',
    `license_url`     varchar(512) NOT NULL COMMENT '营业执照文件地址',
    `status`          tinyint      NOT NULL DEFAULT 0 COMMENT '申请状态（0待审核 1已通过 2已驳回 3已取消）',
    `org_id`          bigint       NULL DEFAULT NULL COMMENT '开通的主体编号（merchant_org.id，通过后回写）',
    `submit_count`    int          NOT NULL DEFAULT 1 COMMENT '提交次数（驳回重提 +1）',
    `audit_user_id`   bigint       NULL DEFAULT NULL COMMENT '审核人（system_users.id，平台侧）',
    `audit_time`      datetime     NULL DEFAULT NULL COMMENT '审核时间',
    `audit_remark`    varchar(500) NULL DEFAULT NULL COMMENT '审核意见/驳回原因',
    `creator`         varchar(64)  NULL DEFAULT '' COMMENT '创建者',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`         varchar(64)  NULL DEFAULT '' COMMENT '更新者',
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除（恒为 0，申请记录永不删除）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_credit_code` (`credit_code`) USING BTREE,
    INDEX `idx_apply_user` (`apply_user_id`) USING BTREE,
    INDEX `idx_status` (`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COMMENT = '商户入驻申请表（企业/个体户）';
```

设计说明（对照方案 §2.6 / §5.2-5.5）：

1. **`uk_credit_code` 全表唯一（一信用代码一条记录）**——这是驳回重提规则的**数据模型基石**：
   - 新提交：按 credit_code 查既有记录——无 → INSERT（并发下唯一键兜底，DuplicateKey → 转重查走重提逻辑）；
   - 已驳回 → **UPDATE 原记录**：新资料覆盖、status 重置 0、清空 audit_user_id/audit_time/audit_remark、org_id 保持 NULL、submit_count+1（方案 §5.5 全部满足，且天然"不新增第二条申请记录"）；
   - 已通过 / 待审核 → 拒绝；已取消 → 按方案拒绝重提；
   - 重提账号校验：`apply_user_id` 必须与原记录一致（Service 层比对，防他人凭信用代码接管申请）。
2. **同一信用代码不能重复待审核/重复开通**：申请层由"单记录状态机"保证（同一行不可能同时是两个状态）；正式层由 `merchant_org.uk_credit_code` + `uk_apply_id` 双保险；审核通过使用**条件更新**（`UPDATE ... SET status=1 WHERE id=? AND status=0`，影响行数=0 即并发冲突回滚）——数据库唯一约束负责兜底，条件更新负责互斥（方案 §8.2/§8.3）。
3. `apply_user_id` 直接关联 `system_account.id`（无历史映射，全新建表即新语义）。
4. 不新增审核记录表（方案 §5.5 明确），最近一次审核结果即行内字段。
5. 本表为 merchant 模块全局视角表（平台审核跨租户查询），处理方式同 §4 第 6 点。

## 6. 原生表调整（最小化，已随阶段 2 交付）

### 6.1 `system_oauth2_client` 种子数据（APP 专用 client）

```sql
INSERT INTO `system_oauth2_client` (...) VALUES ('app', ..., 7200, 2592000, ...);
```

- 完整语句见 `sql/mysql/increment/20261106_06__seed_oauth2_client_app.sql`：access 2 小时 / refresh 30 天，**刷新行为保持原生（不轮换，见 6.2）**；
- 有效期后续在管理端 `/admin-api/system/oauth2-client` 动态调整（缓存即时失效，01 报告 §2.3）；
- secret 仅为协议占位（内部签发走 `createAccessToken`，不校验 secret），不构成可用凭据；
- 双端隔离不依赖轮换：Web/APP 各自独立 client + 独立签发/刷新/登出入口，`removeAccessToken(token)` 只删自身，登出互不影响。

### 6.2 未采纳：`system_oauth2_refresh_token` 加列（2026-11-06 用户决定暂缓）

原建议为该表增加 `family_id`（令牌族）、`rotated`（重放标记）、`family_start_time`（绝对期限基准）三列以支撑方案 §3.3 的 APP Refresh Token 轮换、重放撤销与 90 天绝对会话期限。**用户决定：`refreshAccessToken` 保持原生行为，暂不新增三列。**

- 影响：方案 §3.3 中"轮换 / 旧 token 重放受控 / 绝对会话期限"三项验收本次不满足，列为**需求裁剪**；
- 遗留风险（已接受）：APP refresh token 在 30 天有效期内被泄露后可被持续使用，服务端无法检测重放；
- 后续启用路径：补一条 refresh token 加列迁移脚本 + 改造 `OAuth2TokenServiceImpl.refreshAccessToken`（仅对 app client 启用轮换，Web 不动），与本批脚本无耦合。

### 6.3 其余原生表变更（已确认，随阶段 2 交付）

- `system_sms_code.code` 哈希化（`varchar(6)→varchar(64)`）：**已确认随本次改造**。DDL 已落为 `sql/mysql/increment/20261106_05__alter_sms_code_hash.sql`（过渡期明文/哈希兼容，写入与校验逻辑在阶段 3 代码统一切换，历史明文行 10 分钟过期无需清洗）。

### 6.4 不做的原生表变更（明确排除）

- `system_users`：不加全局唯一索引（与多租户语义冲突、存量已违反）；结构不动（方案 §2.4）。
- `system_tenant` / `system_oauth2_refresh_token` / RBAC 各表：不动。

## 7. 脚本组织与迁移包（已随阶段 2 交付）

脚本已落至 `sql/mysql/increment/`（执行顺序 01→08，逆序回滚，总览见该目录 `README.md`）：

```
sql/mysql/increment/
├── README.md                                     # 影响范围/前置条件/执行顺序/验证/回滚限制总览
├── 20261106_01__create_system_account.sql(.rollback)          # §2
├── 20261106_02__create_system_account_user_rel.sql(.rollback) # §3
├── 20261106_03__create_merchant_org.sql(.rollback)            # §4
├── 20261106_04__create_merchant_org_apply.sql(.rollback)      # §5
├── 20261106_05__alter_sms_code_hash.sql(.rollback)            # §6.3
├── 20261106_06__seed_oauth2_client_app.sql(.rollback)         # §6.1
├── 20261106_07__backfill_account_and_rel.sql(.rollback)       # 回填（幂等，内置预检）
└── 20261106_08__verify_migration.sql                          # 核对（02 报告 §7.4 扩充）
```

**部署顺序**：DDL/种子（01-06）→ 回填（07，预检通过后）→ 核对（08）→ 应用上线（阶段 3 代码）。代码未上线前新表无写入方，回填窗口安全。**全部脚本尚未在任何数据库执行，执行需另行授权。**

## 8. 与方案 §2 要求逐条对照（验收映射）

| 方案要求 | 条目 | 状态 |
|---|---|---|
| §2.2 手机号唯一约束 | §2 `uk_mobile` | ✅ |
| §2.2 不保存明文验证码 | increment/06 列加宽 + 阶段 3 代码切换哈希（已确认） | ✅（DDL 已交付） |
| §2.2 不绑定 tenant_id / 不存租户权限 | §2 表结构 | ✅ |
| §2.3 account+tenant 有效唯一 | §3 `uk_account_tenant`（状态代删，停用行占位、恢复复用） | ✅ |
| §2.3 system_user 不被多账号绑定 | §3 `uk_system_user` | ✅ |
| §2.3 system_user 归属 tenant 校验 | 应用层 + 迁移核对 SQL | ✅ |
| §2.4 system_user 保留原生结构 | §6.3 不动 | ✅ |
| §2.5 uk_tenant_id / uk_apply_id | §4 | ✅ |
| §2.5 信用代码数据库级唯一 | §4 `uk_credit_code`（+ §5 申请层） | ✅ |
| §2.5 身份哈希安全方案 | §4 说明 4（HMAC + 环境变量密钥） | ✅ |
| §2.5 银行三字段同进同出 | 已按用户决定（2026-11-06）移除银行账户字段，需求裁剪 | ✅ 已裁剪 |
| §2.6 状态 0/1/2/3、审核字段、submit_count | §5 | ✅ |
| §2.6 uk_credit_code | §5（全表唯一 + 单记录状态机） | ✅ |
| §2.6 apply_user_id → system_account.id | §5（全新建，无迁移） | ✅ |
| §3.3 APP 2h/30d 有效期 + 双端会话隔离 | §6.1 种子（脚本 06，刷新保持原生行为） | ✅ |
| §3.3 轮换 / 重放撤销 / 90 天绝对期限 | 2026-11-06 用户决定暂不实现（§6.2 决策记录） | ⚠ 需求裁剪（遗留风险） |
| §7 可审查/可回滚迁移 | §7 目录 + 02 报告 §6.3 | ✅ |
| §8 并发（唯一约束兜底 + 条件更新） | §1.2-C 统一模式 + §5 说明 2 | ✅ |

## 9. 决策记录（已于 2026-11-06 全部确认）

> 全部确认后，阶段 2 脚本已交付至 `sql/mysql/increment/`（README.md 为总览，未执行）。

1. **唯一约束共存方案 C（状态代删）**——四张新表禁逻辑删除、生命周期全走 status（§1.2）。
2. **merchant_org DO 采用全局表（@TenantIgnore + 业务列 tenant_id）**，隔离由 Service 显式过滤（§4 说明 6）。
3. ~~bank_account_no 加密存储~~ —— 已随银行账户字段移除（2026-11-06 用户决定），不再是待确认项。
4. **system_sms_code 验证码哈希化**——确认随本次改造（§6.3，脚本 05）。
5. ~~oauth2_refresh_token 加列~~ —— **2026-11-06 用户再决定：暂不实现 Refresh Token 轮换**，`refreshAccessToken` 保持原生行为，不新增 family_id / rotated / family_start_time 三列；R0 泄露风险暂缓，列为已知遗留（§6.2）。
6. **APP 会话选型方案 A**（独立 clientId + ADMIN userType，见 01 报告 §5.1）——影响 framework 一处 userType 检查调整（种子脚本 06）。双端隔离不依赖轮换，继续有效。
