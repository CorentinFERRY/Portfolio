# 0002. Target Spring Boot 4.1.1

Date: 2026-10-06
Status: accepted

## Context

The backend is a new Spring Boot application, so the Boot generation chosen now
fixes the Java baseline, the starter names and the package layout for the whole
project. Spring Boot 4 is recent enough that most references still describe
Spring Boot 3, whose imports and starter names differ.

## Decision

Use Spring Boot 4.1.1, resolved from the Initializr with the plain version
`4.1.1`.

## Why

The Spring Boot support policy on spring.io lists these end-of-support dates for
the candidate lines:

- 3.5.x: OSS support ends 2026-06, enterprise support ends 2032-06 (initial
  release 2025-05).
- 4.0.x: OSS support ends 2026-12, enterprise support ends 2027-12 (initial
  release 2025-11).
- 4.1.x: OSS support ends 2027-07, enterprise support ends 2028-07 (initial
  release 2026-06).

At the date of this decision the 3.5.x OSS support window has already ended, and
4.1.x is the released generation with the longest remaining OSS support.

Facts met while scaffolding, recorded because they cost time to rediscover:

- The Initializr metadata id `4.1.1.RELEASE` is not published on Maven Central,
  so the plain `4.1.1` has to be passed.
- The Boot 4 webmvc starter is `spring-boot-starter-webmvc` (Boot 3 used
  `spring-boot-starter-web`).
- The test starters are split: this project uses
  `spring-boot-starter-webmvc-test` and `spring-boot-starter-security-test`
  rather than a single `spring-boot-starter-test`.
- `AutoConfigureMockMvc` lives in
  `org.springframework.boot.webmvc.test.autoconfigure` (Boot 3 used
  `org.springframework.boot.test.autoconfigure.web.servlet`).
- The Maven wrapper committed by the Initializr is script-only (`mvnw`,
  `mvnw.cmd` and `.mvn/wrapper/maven-wrapper.properties`), with no
  `maven-wrapper.jar`.

Alternatives rejected:

- 3.5.x: its OSS support already ended on 2026-06.
- 4.0.x: released earlier but with a shorter remaining OSS support than 4.1.x.

## Consequences

- Imports and APIs must be checked against the generated jars and the spring.io
  documentation, not against Spring Boot 3 knowledge.
- Adding a dependency or test support uses the Boot 4 artifact names, since the
  webmvc starter was renamed and the test starters were split.
- Moving to the next generation repeats the same support-policy check.
