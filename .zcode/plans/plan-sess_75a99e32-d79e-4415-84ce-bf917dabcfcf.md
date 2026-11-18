# 个人商户免审核注册是否进申请表 —— 设计决策

## Context

用户在权衡：个人商户免审核，注册时要不要也写一条记录进 `merchant_org_apply` 申请表？进表的好处是查询方便。经勘察，**代码已实现「不进申请表」方案**，且查询诉求已在读侧解决，本计划结论为**维持现状、不改代码**，并记录决策理由。

## 结论与理由

**推荐维持现状：个人商户不进申请表，直接落正式主体表。**

1. **职责分离**：`merchant_org_apply` 是审核工作流表（中间态/审核人/驳回重提/取消），个人免审提交即终态，进表会产生大量无意义空字段和被污染的状态统计。
2. **防重模型不匹配**：申请表靠 `uk_credit_code` 防重，个人无信用代码；个人靠 `merchant_org.uk_id_card_hash` 防重，进申请表反而要把敏感哈希冗余到第二张全局表。
3. **审核工作台纯净性**：个人商户量级通常远大于企业，免审记录混入会稀释审核列表与 `idx_status` 索引。
4. **写放大**：现方案个人注册为单事务（provisionTenant + insert merchant_org + 签发令牌），进申请表徒增一次 insert 与 applyId 关联。
5. **可逆性**：将来个人需要初审时在申请表放开 accountType=1 是低成本演进；反之灌入后拆出是高风险不可逆操作。
6. **查询诉求已被覆盖**：用户端 `/merchant/org/mylist`（MerchantOrgSelfController.java:66-97）已合并主体（org 项）+ 申请（apply 项）；平台端查全部商户用 `GET /merchant/org/page`（merchant_org 天然含个人商户）。

## 代价（已知并接受）

- 无统一「入驻流水」：个人注册无独立申请记录，但注册即终态，`merchant_org` 已留存联系人/手机号/身份证哈希/createTime/creator，快照=终态，无信息丢失。
- 将来若需完整入驻审计轨迹，用已有 operate-log 体系或领域事件，不复用审核工作流表。

## 代码改动

无。现有实现即为推荐方案：
- `agriledger-module-merchant/.../service/org/MerchantOrgServiceImpl.java:104-114`（registerPersonal，applyId=null）
- `agriledger-module-merchant/.../controller/admin/merchant/MerchantOrgSelfController.java:66-97`（/mylist 合并查询）
- `sql/mysql/increment/20261106_03__create_merchant_org.sql`（uk_id_card_hash / uk_apply_id）
- `sql/mysql/increment/20261106_04__create_merchant_org_apply.sql`（仅受理企业，uk_credit_code）

## 验证

无需改动即无需验证。如需回归确认现状行为，可运行：
`mvn test -pl agriledger-module-merchant -am`（已有 MerchantOrgServiceImplTest / MerchantOrgApplyServiceImplTest）。

## 遗留事项（仅提示，不擅自处理）

- `E:\agriledger\AGENTS.md` 引用的 `docs/agri_saas_zcode_implementation_plan.md` 在本仓库不存在（实际位于 E:\agri4j 与 E:\sail-qdp），建议用户决定是补进本仓库还是修正指引。