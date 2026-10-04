# Security Policy

## Supported releases

The current release line receives security updates. Advisories for `nasa-redis`, Redis, Spring Boot, Jedis, and
other transitive dependencies remain owned by their projects. Keep the target library and adapter versions aligned.

## Private reporting

Use **Security → Report a vulnerability** in the GitHub repository. Do not publish uncoordinated exploit details in
an issue, discussion, or pull request. Include affected versions, configuration, reproduction, impact, and possible
mitigations, without real credentials or production data.

## Boundary

This project only generates reachability hints. It holds no Redis credential, opens no connection, and does not
change job idempotency, message acknowledgement, or data isolation. Users remain responsible for application scan
scope, trust in deserialized types, and native deployment policy.
