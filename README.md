# nasa-redis-spring-boot-aot

`nasa-redis-spring-boot-aot` 为 `nasa-redis` 补充业务侧 Spring AOT 与 GraalVM native-image 提示。它发现
Redis 任务、订阅方法、监听器泛型和非 Spring Bean 的 RediSearch 文档，使消息解码与动态调用在原生镜像中
保持与 JVM 相同的合同。

[English](README.en.md)

## 核心价值

- 保留 `@RedisJob` 与 `@Subscriber` 方法的调用入口及完整泛型参数绑定信息。
- 从监听接口实现解析消息类型，覆盖 Pub/Sub、Stream、单事件与批事件监听器。
- 在受控应用包内发现 `@RsDocument`，无需把持久化实体注册为 Spring Bean。
- 传递引入 `nasa-core-spring-boot-aot`，统一应用包发现且不固定 `com`。

## 运行架构

```text
BeanDefinition 类型                         应用基础包
      │                                        │
      ├─ @RedisJob / @Subscriber               └─ @RsDocument
      ├─ Subscribe<T>                               │
      └─ Stream / Event listeners                   │
                    └──────────┬─────────────────────┘
                               ▼
                 reflection + Jackson binding hints
                               │
                               ▼
                   nasa-redis 自带静态 hints
```

适配器只读取 Bean 类型，不创建 Bean，也不连接 Redis。库自身已有的缓存、分区提示会与本适配器合并；本项目
专注运行期只能从业务代码确定的入口。

## 兼容性与安装

- JDK 21+，Spring Boot 3.5.x / Spring Framework 6.2.x
- `nasa-redis` 2.0.2
- `nasa-core-spring-boot-aot` 1.0.4（传递依赖）

```xml
<dependency>
    <groupId>io.github.nasa-runtime</groupId>
    <artifactId>nasa-redis-spring-boot-aot</artifactId>
    <version>2.0.2</version>
</dependency>
```

正常目录结构无需配置。额外实体包可通过 Spring Boot Maven Plugin 的 AOT JVM 参数指定：

```bash
./mvnw -Dspring-boot.aot.jvmArguments="-Dnasa.aot.scan-packages=com.acme.search,dev.partner.events" \
  -Pnative native:compile
```

`process-aot` 在独立 JVM 中执行，普通 Maven `-Dnasa.aot.scan-packages=...` 不会自动进入该进程。直接调用
AOT JVM 时可使用这个底层系统属性。

## 登记内容

- 带 `@RedisJob` 或 `@Subscriber` 的 Bean 方法及业务参数 Jackson binding hints。
- `Subscribe<T>`、`StreamSubscribe<T>`、`RedisEventSingleListener<T>`、
  `RedisEventBatchListener<T>` 中可解析的 `T`。
- 应用包内全部 `@RsDocument` 类型的构造器、字段和方法。
- RedisJob、幂等计数、Stream 分区配置树和 `lua/*.lua`、`lua/job/*.lua` 资源。
- 延迟初始化带静态 logger 的 Stream 容器，保持 Spring Boot logging 与 GraalVM 的运行期初始化边界。

`RedisJobContext` 与原始 `byte[]` 由框架直接处理，不作为业务 JSON 类型登记。无法解析为具体类型的监听泛型
会跳过，应用应使用具体消息类型而不是未绑定类型变量。

## 扫描、失败与观测

扫描边界由 core 适配器从 `AutoConfigurationPackages`、`@SpringBootApplication` 和显式
`nasa.aot.scan-packages` 合并。不会扫描整个 `com`。缺少安全边界、包过宽、类资源不可读或类型依赖不完整时，
AOT 构建立即失败。

处理器不执行任务、不订阅消息、不创建 Redis 连接。可通过 AOT 构建异常与 `target/spring-aot/` 中的生成配置
核对命中的方法、实体和资源。

## 不解决的问题

本项目不提供 Redis 服务、不改变任务幂等或消息确认语义、不推断擦除后无法恢复的泛型，也不覆盖应用自定义
serializer、动态 class name 或额外 Lua 资源。相应 hints 仍需由应用补充。

## 构建与发布

```bash
mvn clean verify
```

参见 [CONTRIBUTING.md](CONTRIBUTING.md)、[SECURITY.md](SECURITY.md) 与 [RELEASE.md](RELEASE.md)。
本项目采用 Apache-2.0 OR MIT 双许可证。
