# nasa-redis-spring-boot-aot

`nasa-redis-spring-boot-aot` adds business-side Spring AOT and GraalVM native-image hints for `nasa-redis`.
It discovers Redis jobs, subscriber methods, listener generic payloads, and RediSearch documents that are not
Spring beans, preserving the same decoding and invocation contracts in a native executable.

[中文](README.md)

## Why it exists

- Preserves invocation access and full generic binding types for `@RedisJob` and `@Subscriber` methods.
- Resolves payload types from Pub/Sub, Stream, single-event, and batch-event listener interfaces.
- Finds `@RsDocument` under bounded application packages without requiring entities to become Spring beans.
- Transitively includes `nasa-core-spring-boot-aot` for shared package discovery without a fixed `com` root.

## Architecture

```text
BeanDefinition types                       application packages
      │                                        │
      ├─ @RedisJob / @Subscriber               └─ @RsDocument
      ├─ Subscribe<T>                               │
      └─ Stream / Event listeners                   │
                    └──────────┬─────────────────────┘
                               ▼
                  reflection + Jackson binding hints
                               │
                               ▼
                    nasa-redis static hints
```

The adapter reads bean types without creating beans or connecting to Redis. Existing cache and partition hints in
`nasa-redis` merge with this adapter; this project covers entry points that only application code can reveal.

## Compatibility and installation

- JDK 21+, Spring Boot 3.5.x / Spring Framework 6.2.x
- `nasa-redis` 2.0.2
- `nasa-core-spring-boot-aot` 1.0.4 as a transitive dependency

```xml
<dependency>
    <groupId>io.github.nasa-runtime</groupId>
    <artifactId>nasa-redis-spring-boot-aot</artifactId>
    <version>2.0.2</version>
</dependency>
```

Standard layouts need no configuration. Forward external entity packages to the Spring Boot Maven Plugin's AOT JVM
when needed:

```bash
./mvnw -Dspring-boot.aot.jvmArguments="-Dnasa.aot.scan-packages=com.acme.search,dev.partner.events" \
  -Pnative native:compile
```

`process-aot` runs in a separate JVM, so a plain Maven `-Dnasa.aot.scan-packages=...` is not propagated to it.
Use that underlying system property when invoking the AOT JVM directly.

## Registered contracts

- Bean methods carrying `@RedisJob` or `@Subscriber`, plus Jackson binding hints for business parameters.
- Resolvable `T` from `Subscribe<T>`, `StreamSubscribe<T>`, `RedisEventSingleListener<T>`, and
  `RedisEventBatchListener<T>`.
- Constructors, fields, and methods of every `@RsDocument` in the application scope.
- Configuration trees for RedisJob, idempotent counters, and Stream partitions, plus `lua/*.lua` and
  `lua/job/*.lua` resources.
- Runtime initialization for the Stream container with a static logger, preserving Spring Boot logging's GraalVM
  initialization boundary.

`RedisJobContext` and raw `byte[]` values are framework-owned and are not registered as business JSON types.
Unresolved listener type variables are skipped; use concrete payload types when native binding is required.

## Scanning, failure, and observability

The core adapter combines `AutoConfigurationPackages`, `@SpringBootApplication`, and explicit
`nasa.aot.scan-packages`. It never scans all of `com`. Missing safe scope, unsafe roots, unreadable class resources,
or incomplete type dependencies stop the AOT build.

The processor runs no job, subscribes to no channel, and creates no Redis connection. Use AOT exceptions and the
generated files under `target/spring-aot/` to inspect matched methods, entities, and resources.

## Non-goals

This project does not provide Redis, change idempotency or acknowledgement semantics, recover erased generic types,
or infer application-specific serializers, dynamic class names, and additional Lua resources. Applications must
provide hints for those paths.

## Build and release

```bash
mvn clean verify
```

See [CONTRIBUTING.en.md](CONTRIBUTING.en.md), [SECURITY.en.md](SECURITY.en.md), and
[RELEASE.en.md](RELEASE.en.md). The project is dual-licensed under Apache-2.0 OR MIT.
