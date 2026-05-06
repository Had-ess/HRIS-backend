# HRIS Backend Testing Overview

## Summary
- Tests live under `src/test/java/com/hris`
- Build tool: Maven
- Main test stack in current use:
  - JUnit 5 through `spring-boot-starter-test`
  - Mockito
  - AssertJ
  - Spring Test / MockMvc
  - Spring Security Test
- `pom.xml` also includes Testcontainers dependencies, but there are no checked-in tests currently using `@Testcontainers`

## Current Test Inventory

- Total checked-in `*Test.java` files: 32
- Current top-level test packages:
  - `admin`
  - `approval`
  - `auth`
  - `common`
  - `dashboard`
  - `leave`
  - `notification`
  - `organisation`
  - `security`
  - `support`

```text
src/test/java/com/hris/
|-- admin/
|-- approval/
|-- auth/
|-- common/
|-- dashboard/
|-- leave/
|-- notification/
|-- organisation/
|-- security/
`-- support/
```

## Test Styles In Use

### Mockito-Based Unit Tests
- Most service and helper tests use `@ExtendWith(MockitoExtension.class)`.
- This is the dominant pattern in the current suite.

Examples:
- `admin/service/AdminRequestServiceTest`
- `approval/service/ApprovalStepServiceTest`
- `auth/service/RoleServiceTest`
- `dashboard/service/DashboardServiceTest`
- `security/JwtAuthenticationFilterTest`

### Spring MVC Slice Tests
- The checked-in suite currently contains three `@WebMvcTest` classes:
  - `approval/controller/ApprovalStepControllerTest`
  - `leave/controller/LeaveRequestControllerTest`
  - `leave/controller/LeaveRequestAttachmentControllerTest`

### Entity and Support Tests
- The suite also includes targeted entity behavior tests and shared support helpers.

Examples:
- `admin/entity/AdminRequestTest`
- `approval/entity/ApprovalStepTest`
- `leave/entity/LeaveBalanceTest`
- `support/TestAuthenticationFactory.java`

## What Is Not Present

- No checked-in class currently uses:
  - `@SpringBootTest`
  - `@DataJpaTest`
  - `@Testcontainers`
  - `@ActiveProfiles`
  - `@Transactional`
- No `src/test/resources` directory is checked in.
- No `application-test.yml` or `application-test.properties` file is checked in.
- No Maven test profile is defined in `pom.xml`.
- No JaCoCo plugin is configured in `pom.xml`.
- No Maven wrapper (`mvnw`) is checked in.

## Running Tests

Run commands from `backend/hris/`.

### Run All Tests
```bash
mvn test
```

### Run One Test Class
```bash
mvn -Dtest=RoleServiceTest test
```

### Run Multiple Specific Test Classes
```bash
mvn -Dtest=JwtAuthenticationFilterTest,ProjectServiceTest,RoleServiceTest,LeaveRequestServiceTest test
```

## Reports and Output

- Maven Surefire writes reports to `target/surefire-reports/`.
- Because JaCoCo is not configured, there is no checked-in coverage-report command documented here.

## Notes

- This document describes the test suite currently checked into the repository. It does not assume the existence of local-only integration tests.
- Testcontainers artifacts are present as dependencies only. If container-based integration tests are added later, this document should be updated from the source tree rather than inferred from `pom.xml` alone.
