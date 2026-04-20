# HRIS Backend Testing Overview

## Summary
- Tests live under `src/test/java/com/hris`
- Build tool: Maven
- Main test stack in use:
  - JUnit 5 via `spring-boot-starter-test`
  - Mockito
  - AssertJ
  - Spring Test / MockMvc
  - Spring Security Test
- Testcontainers dependencies are present in `pom.xml`, but there are no checked-in tests currently using `@Testcontainers`, container classes, or `@SpringBootTest`

## Test Layout

```text
src/test/java/com/hris/
├── admin/
│   ├── entity/
│   └── service/
├── approval/
│   ├── controller/
│   ├── entity/
│   └── service/
├── auth/
│   ├── controller/
│   └── service/
├── dashboard/
│   ├── controller/
│   └── service/
├── leave/
│   ├── controller/
│   ├── entity/
│   └── service/
├── notification/
│   └── service/
├── organisation/
│   ├── controller/
│   └── service/
├── security/
└── support/
```

## Current Test Structure

### Unit Tests
- Most tests are plain JUnit 5 + Mockito tests using `@ExtendWith(MockitoExtension.class)`.
- This covers service classes, some controller classes, entity behavior, and security helpers.

Examples:
- `admin/service/AdminRequestServiceTest`
- `approval/service/ApprovalStepServiceTest`
- `auth/service/RoleServiceTest`
- `dashboard/controller/DashboardControllerTest`
- `security/JwtAuthenticationFilterTest`

### Web Slice Test
- `leave/controller/LeaveRequestAttachmentControllerTest` uses:
  - `@WebMvcTest(controllers = LeaveRequestController.class)`
  - `MockMvc`
  - `@MockBean`
  - Spring Security test support with `user(...)`

### Controller Testing Style
- There is no single controller-test pattern across the suite.
- Current controller coverage includes:
  - Mockito-only tests that instantiate controllers directly
  - standalone `MockMvcBuilders.standaloneSetup(...)`
  - one Spring MVC slice test with `@WebMvcTest`

### Integration Tests
- No checked-in test class currently uses:
  - `@SpringBootTest`
  - `@DataJpaTest`
  - `@Testcontainers`
  - `@ActiveProfiles`
  - `@Transactional`
- There are also no `*IT` test classes in `src/test/java`.

## Naming Conventions
- All checked-in test classes currently end with `Test`.
- No separate integration-test naming convention is present.

## Running Tests

Run commands from `hris/`.

### Run All Tests
```bash
mvn test
```

### Run a Specific Test Class
```bash
mvn -Dtest=RoleServiceTest test
```

### Run Multiple Specific Test Classes
```bash
mvn -Dtest=JwtAuthenticationFilterTest,ProjectServiceTest,RoleServiceTest,LeaveRequestServiceTest test
```

## Test Support and Mocking

### Mocking Strategy
- Mockito is the default mocking approach.
- Common patterns in the suite:
  - `@Mock`
  - `@InjectMocks`
  - direct constructor wiring in tests
  - `@MockBean` in the MVC slice test

### Security Test Support
- `spring-security-test` is on the classpath.
- Current usage includes:
  - `user(...)` request post-processors for MockMvc
  - direct testing of authorization logic and controller-level access behavior

### Shared Test Utilities
- `src/test/java/com/hris/support/TestAuthenticationFactory.java`
- `src/test/java/com/hris/dashboard/controller/TestAuthenticationFactory.java`

## Test Configuration

- There is no `src/test/resources` directory checked in.
- There is no `application-test.yml` or `application-test.properties` file checked in.
- No Maven test profile is defined in `pom.xml`.

## Coverage and Reports

- `pom.xml` does not currently configure JaCoCo.
- Standard Maven Surefire reports are produced under `target/surefire-reports/` when tests run.

## Assumptions / Needs Review

- `pom.xml` includes Testcontainers modules for PostgreSQL and RabbitMQ, but the current test suite does not reference them. If container-based integration tests exist outside the checked-in source tree, this document does not cover them.
- There is no Maven wrapper (`mvnw`) checked in, so this documentation only lists `mvn` commands.
