# AgriLedger（农E链）

农业 SaaS 平台后端。基于 [ruoyi-vue-pro](https://github.com/YunaiV/ruoyi-vue-pro) 脚手架二次演化的**单体多模块 Maven 工程**（非微服务、无 Spring Cloud），当前启用「系统管理 + 基础设施」两个业务模块，为后续业务模块（商户、会员等）提供底座。

- 基础包名：`com.agsccc.agriledger`，版本 `${revision}` = `2026.09-SNAPSHOT`（flatten-maven-plugin 统一管理，模块版本不改写）
- 多租户默认开启（`TenantBaseDO` + `agriledger.tenant.enable: true`），租户隔离不可绕过
- 开发规范与项目约定详见 [AGENTS.md](AGENTS.md)；排查实现时以 `agriledger-module-system` 的 dept/user 域为标准参考样例

## 技术栈

| 分类 | 选型 | 版本 |
| --- | --- | --- |
| 语言 / 运行时 | Java（编译目标 17；Docker 镜像运行时 JRE 21） | 17 |
| 核心框架 | Spring Boot | 3.5.15 |
| ORM | MyBatis-Plus，连接池 HikariCP（未引入 Druid） | 3.5.17 |
| 缓存 / 分布式锁 | Redis + Redisson（Lock4j） | Redisson 4.7.0 / Lock4j 2.2.7 |
| 定时任务 | Quartz（test/prod 启用，JDBC 集群模式） | — |
| 对象转换 | MapStruct | 1.6.3 |
| 接口文档 | springdoc-openapi + Scalar UI（未引入 Swagger UI / Knife4j） | 2.8.17 / Scalar 0.6.67 |
| 监控 / 链路 | Spring Boot Admin、Actuator、Micrometer Tracing（W3C，全量采样） | SBA 3.5.9 |
| 工具库 | Guava + Commons Lang3（**禁止引入 Hutool**，见 `agriledger-dependencies/pom.xml`） | 33.7.1-jre / 3.20.0 |
| 构建 | Maven 多模块 + flatten-maven-plugin | — |

## 模块结构

```plain
agriledger-server            启动模块（入口 AgriLedgerServerApplication，打包产物 agriledger-server.jar）
└─ agriledger-module-system      系统管理（用户/角色/部门/岗位/租户/菜单/字典/短信/邮件/通知/OAuth2/社交登录/操作日志）
   └─ agriledger-module-infra      基础设施（文件/配置/代码生成/定时任务管理/API 日志/Redis 监控/WebSocket）
      └─ agriledger-framework          agriledger-common + 14 个 starter（mybatis、redis、web、security、
                                       websocket、monitor、protection、job、mq、excel、biz-tenant、
                                       biz-data-permission、biz-ip、test）
         └─ agriledger-dependencies        BOM，统一依赖版本
```

- 根 `pom.xml` 中 bpm / pay / mall / crm / erp 等大量模块已注释停用（目录不存在），不要随意重新启用。
- 跨模块调用必须走被调方 `api` 包（`XxxApi` 接口 + `XxxApiImpl` + `dto/XxxRespDTO`），禁止直接注入其他模块的 Service/Mapper。
- URL 前缀按包路径自动映射（`WebProperties`）：`controller.admin.**` → `/admin-api`，`controller.app.**` → `/app-api`。
- 标准调用链：`Controller → Service/ServiceImpl → Mapper → DO`。

## 快速开始

### 环境要求

JDK 17+、Maven 3.6+、MySQL 8.x、Redis 5+。

### 构建与运行

```bash
# 构建（与 CI、Docker-HOWTO 一致的命令）
mvn clean package -Dmaven.test.skip=true

# 切换 profile（默认 dev）
mvn clean package -P test
mvn clean package -P prod

# 运行（dev profile）
java -jar agriledger-server/target/agriledger-server.jar
```

> 说明：`agriledger-server/src/test/.../ProjectReactor.java` 是残留的工程改名工具（引用已移除的 Hutool，无法编译），不含测试编译的构建命令可绕开它；详见 [AGENTS.md](AGENTS.md) 「测试与验证」。

### 数据库准备

> ⚠️ **仓库当前没有数据库初始化 SQL 脚本**（`sql/` 目录不存在；`script/docker/docker-compose.yml` 挂载的 `sql/mysql/*.sql` 已缺失）。需自行建库建表，表结构设计可参考 [docs/phase1/03_最终DDL建议.md](docs/phase1/03_最终DDL建议.md)。

dev 环境默认数据源（见 `agriledger-server/src/main/resources/application-dev.yaml`，可按需修改）：

- MySQL：`127.0.0.1:3306/agriledger`
- Redis：`127.0.0.1:6379`，database 2，无密码
- dev 不启用 Quartz（`QuartzAutoConfiguration` 被排除）

### 访问入口（dev，端口 8800）

| 入口 | 地址 | 说明 |
| --- | --- | --- |
| API 文档（Scalar UI） | <http://127.0.0.1:8800/scalar> | 仅 dev 开启；分组：全部 / System 系统 / Infra 基础设施 |
| OpenAPI JSON | <http://127.0.0.1:8800/v3/api-docs> | springdoc 提供 |
| 管理端 API | `/admin-api/**` | `controller.admin` 包自动映射前缀 |
| 用户端 API | `/app-api/**` | `controller.app` 包自动映射前缀 |
| Actuator | `/actuator` | health 细节需认证；dev 暴露 prometheus 端点 |
| Spring Boot Admin | `/admin` | 凭据经 `AGRI4J_MONITOR_USERNAME/PASSWORD` 注入（dev 有本地兜底账号） |
| WebSocket | `/infra/ws` | 消息经 Redis 广播 |

## Profile 与配置

配置文件：`agriledger-server/src/main/resources/application{,-dev,-test,-prod}.yaml`。Maven profile（dev / test / prod，默认 dev）经 `@spring.profiles.active@` 注入。**三个 profile 端口均为 8800**。

| | dev（默认） | test | prod |
| --- | --- | --- | --- |
| 数据源 | 本地写死 `127.0.0.1:3306/agriledger` | `${DB_URL:…ruoyi-vue-pro}`，支持部署目录 `.env` 注入 | `${DB_URL}` 等环境变量，**全部必填** |
| Redis | `127.0.0.1:6379` db 2 无密码 | `${REDIS_HOST:127.0.0.1}` 等带默认值 | `${REDIS_HOST}` 等，**全部必填** |
| Quartz | 不启用 | JDBC 集群模式（表结构需手动创建） | JDBC 集群模式（表结构需手动创建） |
| springdoc / Scalar | 开启 | 关闭 | 关闭 |
| 日志目录 | `./logs` | `${user.home}/logs` | `${user.home}/logs` |

prod 必填环境变量：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`。
其他经环境变量注入的配置项：`AGRI4J_MONITOR_USERNAME` / `AGRI4J_MONITOR_PASSWORD`（监控凭据）、`AGRI4J_API_ENCRYPT_REQUEST_KEY` / `AGRI4J_API_ENCRYPT_RESPONSE_KEY`（API 加密，默认关闭）、`SMS_CODE_HMAC_KEY`、`IDCARD_HMAC_KEY`、`MERCHANT_TENANT_PACKAGE_ID`。生产凭据一律通过最小权限的 secret 引用注入，不落盘、不进代码库。

## 模块内部分层约定

每个业务模块（`com.agsccc.agriledger.module.<模块名>`）固定布局：

```plain
api/            跨模块门面：XxxApi + XxxApiImpl + dto/
controller/
  admin/<域>/   管理端控制器 + vo/（XxxSaveReqVO / XxxPageReqVO / XxxRespVO 等）
  app/<域>/     用户端控制器（类名加 App 前缀，如 AppAuthController）
service/<域>/   XxxService 接口 + XxxServiceImpl 实现
dal/
  dataobject/   XxxDO（继承 BaseDO 或 TenantBaseDO）
  mysql/        XxxMapper extends BaseMapperX<XxxDO>
  redis/        RedisKeyConstants 缓存 key
convert/<域>/   XxxConvert（MapStruct）
enums/          含 ErrorCodeConstants.java（错误码数字分段管理）
framework/ job/ mq/ util/   模块内 Spring 配置、定时任务、消息、工具
```

- 对象转换统一用 MapStruct `XxxConvert.INSTANCE.convertXxx(...)`；接口统一返回 `CommonResult<T>`，分页统一 `PageParam` / `PageResult`。
- 领域对象（DO / DTO / VO / Query 等）按实际业务边界选用，不追求类型齐全；完整规范见 [AGENTS.md](AGENTS.md) 「领域对象规范」。

## 测试

- JUnit 5；基类在 `agriledger-framework/agriledger-spring-boot-starter-test`：`BaseDbUnitTest`（H2 内存库）、`BaseDbAndRedisUnitTest`、`BaseRedisUnitTest`（jedis-mock）、`BaseMockitoUnitTest`（纯 Mockito）。
- 写法：`@Import` 导入被测 Service，`@MockitoBean` 打桩依赖，`RandomUtils.randomPojo` 造数，`@Sql("/sql/clean.sql")` 清理。
- 运行单模块测试：

```bash
mvn test -pl agriledger-module-system -am
```

> CI（`.github/workflows/maven.yml`，push 到 master 触发，JDK 17 temurin）构建命令带 `-Dmaven.test.skip=true`，**「构建通过」≠「测试通过」**。

## Docker 与部署

- 镜像：`agriledger-server/Dockerfile`，基础镜像 `eclipse-temurin:21-jre`（编译目标仍为 Java 17），`TZ=Asia/Shanghai`，`JAVA_OPTS` 可通过 `-e` 覆盖。
- 编排：`script/docker/docker-compose.yml`（mysql:8 + redis:6 + server），用法见 [script/docker/Docker-HOWTO.md](script/docker/Docker-HOWTO.md)。
- 裸机部署脚本：`script/shell/deploy.sh`（备份 → 停止 → 上传 jar → 启动 → 健康检查）。

> ⚠️ **当前脚本与应用配置存在两处不一致，使用前需自行调整**：
> 1. `Dockerfile` 的 `EXPOSE`、docker-compose 的端口映射、`deploy.sh` 的健康检查均按 **48080** 编排，而应用三个 profile 实际端口均为 **8800**；
> 2. docker-compose 挂载的 `sql/mysql/ruoyi-vue-pro.sql`、`sql/mysql/quartz.sql` 文件缺失，mysql 容器自动初始化会失败。

## 仓库导航

| 路径 | 内容 |
| --- | --- |
| [AGENTS.md](AGENTS.md) | 开发规范与项目约定（分层、领域对象、编码规范、安全红线） |
| [docs/phase1/](docs/phase1/) | 源码分析报告、数据库分析报告、最终 DDL 建议 |
| [script/docker/](script/docker/) | docker-compose、Docker-HOWTO |
| [script/shell/](script/shell/) | deploy.sh 部署脚本 |
| [script/jenkins/](script/jenkins/) | Jenkinsfile 流水线模板 |
| [.github/workflows/maven.yml](.github/workflows/maven.yml) | CI 配置（push master 触发） |

## 致谢

本工程脚手架源自 [ruoyi-vue-pro](https://github.com/YunaiV/ruoyi-vue-pro)（芋道源码），在其基础上按农E链业务诉求裁剪与改造。
