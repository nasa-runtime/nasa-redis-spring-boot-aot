# Contributing

## Environment and checks

Use JDK 21+, Maven 3.6.3+, and resolvable `nasa-redis` and core AOT dependencies.

```bash
mvn clean verify
```

## Change policy

- This repository only adapts `nasa-redis` to Spring AOT; it does not copy the Redis runtime implementation.
- New hints must map to actual dynamic paths for jobs, subscribers, listeners, RediSearch entities, or Redis assets.
- Bean discovery may read types only; it must not instantiate beans or connect to Redis.
- Non-bean types may only be scanned inside safe packages resolved by the core adapter.
- Cover new dynamic entry points with focused `RuntimeHintsPredicates` checks for methods, binding types, or resources.
- Production comments are written in Chinese and explain purpose, parameters, returns, and boundaries. Update both
  READMEs when behavior changes.

Before submitting, inspect tests, Javadoc, the dependency tree, the source jar, and the final jar. Compatibility
or failure-semantic changes need explicit migration guidance.
