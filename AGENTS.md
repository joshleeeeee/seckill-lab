# AGENTS.md
Guide for autonomous coding agents in `/home/josh/projects/seckill-lab`.
Use this as the default operating contract for build/test workflow and code conventions.

## 1) Repository Overview
- Build system: Maven multi-module (active module: `seckill-app`)
- Runtime baseline: Java 25
- Framework baseline: Spring Boot 4.0.x
- Test stack: JUnit 5 + AssertJ
- Stage model: `v0` intentionally naive, later stages harden behavior
- Docs policy: Chinese docs are current source of truth
## 2) Required Tools
- JDK 25 in `JAVA_HOME`
- Maven 3.9+
- Docker + Docker Compose

Quick check:
```bash
java -version
mvn -version
docker --version
docker compose version
```
## 3) Build / Run / Test Commands
Run commands from repo root.
### 3.1 Infrastructure
```bash
docker compose -f deploy/docker-compose.yml up -d
docker compose -f deploy/docker-compose.yml down
```
### 3.2 Build and run
```bash
mvn -pl seckill-app compile
mvn -pl seckill-app clean package
mvn -pl seckill-app spring-boot:run
mvn -pl seckill-app spring-boot:run -Dspring-boot.run.profiles=v1
```
### 3.3 Tests (including single-test workflow)
Run all module tests (CI equivalent):
```bash
mvn -pl seckill-app test
```
Run one test class:
```bash
mvn -pl seckill-app -Dtest=V0NaiveSeckillServiceTest test
```
Run one test method:
```bash
mvn -pl seckill-app -Dtest=V0NaiveSeckillServiceTest#allowsDuplicateOrdersForSameUser test
```
Run Spring context smoke test only:
```bash
mvn -pl seckill-app -Dtest=SeckillLabApplicationTests test
```
### 3.4 v0 demo helper
```bash
bash scripts/v0-burst.sh 1001 300 60
```

### 3.5 Common local issues
- `release version 25 not supported`: your `JAVA_HOME` is not JDK 25.
- `Connection refused` to MySQL/Redis/RabbitMQ: start Docker compose first.
- Port conflicts (`8081/8082/8083/8084`): stop local services or remap ports.
- Profile confusion: default is `v0`; DB behavior requires `-Dspring-boot.run.profiles=v1`.
- If single-test selection fails, verify class/method names exactly match source.

## 4) Lint and Quality Gates
- No dedicated lint task is configured (no Checkstyle/Spotless/PMD yet).
- Treat `mvn -pl seckill-app test` as required local quality gate.
- Do not introduce tooling-only churn unless explicitly requested.
## 5) Code Style Guide
### 5.1 Java and Spring conventions
- Keep code Java 25 compatible.
- One top-level type per file.
- Prefer immutable DTOs using `record`.
- Prefer constructor injection with `final` fields.
- Avoid field injection (`@Autowired` fields).
- Keep controllers thin and business logic in services.
### 5.2 Imports
- No wildcard imports.
- Keep grouped imports in this order:
  1) project (`com.seckill.lab...`)
  2) third-party
  3) `java.*`
- Static imports are fine in tests (`assertThat`, etc.).
### 5.3 Formatting
- 4 spaces, no tabs.
- Keep methods focused; extract helpers when branching expands.
- Wrap long parameter lists and constructor calls like existing code.
- Add comments only for non-obvious intent.
### 5.4 Naming
- Packages: lowercase.
- Types: PascalCase.
- Methods/fields: lowerCamelCase.
- Constants: UPPER_SNAKE_CASE.
- Tests: `*Test`; method names should describe expected behavior.
### 5.5 API and validation
- Return `ApiResponse<T>` from controllers consistently.
- Put validation at boundaries using Jakarta validation.
- Use `@Valid` request-body validation and `@Validated` on controllers.
### 5.6 Error handling
- Centralize HTTP error mapping in `GlobalExceptionHandler`.
- Do not swallow exceptions.
- Avoid broad `catch (Exception)` in core logic unless adding context + rethrow.
- Keep error messages stable and user-readable.
### 5.7 Stage semantics (teaching repo rule)
- Preserve stage intent (`v0`, `v1`, ...).
- Do not accidentally “fix” intentional `v0` weaknesses unless task requires.
- Keep changes aligned with the stage currently being taught.
### 5.8 Profiles and config
- Keep shared defaults in `application.yml` minimal.
- Put stage-specific behavior in `application-v*.yml`.
- Do not change default profile (`v0`) unless explicitly requested.
### 5.9 Database and migrations
- Use Flyway for schema changes under `seckill-app/src/main/resources/db/migration`.
- Prefer additive migrations for teaching continuity.
- Avoid destructive schema changes unless explicitly requested.
### 5.10 Documentation
- Maintain docs in Chinese first.
- Keep commands executable and paths clickable.
- Update docs when behavior, startup flow, or test flow changes.
## 6) Testing Conventions
- Prefer unit tests for service/domain behavior.
- Use `@SpringBootTest` only when Spring context is required.
- Assert behavior/outcomes, not implementation internals.
- Keep tests deterministic and fast.
- For concurrency demos, document expected non-deterministic behavior.
## 7) Agent Workflow Checklist
Before coding:
- Identify target stage and read related docs in `docs/`.
- Confirm whether task is demo behavior or hardening behavior.
During coding:
- Keep diffs minimal and local.
- Follow existing package layout and naming conventions.
After coding:
- Run targeted single test first.
- Then run `mvn -pl seckill-app test`.
- Update docs if commands/outputs/behavior changed.

PR hygiene:
- Keep changes focused; avoid unrelated refactors.
- Prefer small, reviewable diffs tied to one stage objective.
- Mention which tests were executed and their outcomes.

## 8) Cursor / Copilot Rule Files
Checked for additional instruction files:
- `.cursor/rules/`
- `.cursorrules`
- `.github/copilot-instructions.md`
Current status: none of the above files exist in this repository.
