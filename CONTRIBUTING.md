# 贡献指南

## 环境与检查

需要 JDK 21+、Maven 3.6.3+，以及可解析的 `nasa-redis` 与 core AOT 依赖。

```bash
mvn clean verify
```

## 变更原则

- 本仓库只维护 `nasa-redis` 的 Spring AOT 适配，不把 Redis 运行时实现复制进来。
- 新提示必须对应任务、订阅、监听器、RediSearch 实体或 Redis 静态资源的真实动态路径。
- Bean 扫描只能读取类型，不能实例化 Bean 或建立 Redis 连接。
- 非 Bean 类型只能在 core 适配器确定的安全应用包内扫描。
- 新增动态入口时应使用 `RuntimeHintsPredicates` 验证方法、绑定类型或资源。
- 源码注释使用中文说明业务作用、参数、返回与边界；行为变化同步维护中英文 README。

提交前核对测试、Javadoc、依赖树、sources jar 与最终 jar 内容。兼容范围或失败语义变化需要明确迁移说明。
