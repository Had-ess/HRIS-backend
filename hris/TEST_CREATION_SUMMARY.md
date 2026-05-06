# HRIS Backend Test Suite Snapshot

This file previously described a generated "test creation" pass. It now serves as a lightweight, current-state snapshot of the checked-in backend test suite.

## Current State
- Test root: `src/test/java/com/hris`
- Checked-in `*Test.java` files: 32
- Dominant pattern: JUnit 5 + Mockito unit tests
- Additional controller coverage: targeted `@WebMvcTest` MVC slice tests

## Current Package Coverage
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

## Representative Test Classes

### Services and Business Rules
- `admin/service/AdminRequestServiceTest`
- `approval/service/ApprovalRouterTest`
- `approval/service/ApprovalStepServiceTest`
- `approval/service/ApprovalViewServiceTest`
- `auth/service/AccountProvisioningServiceTest`
- `auth/service/AdminUserServiceTest`
- `auth/service/DepartmentServiceTest`
- `auth/service/EmployeeOnboardingServiceTest`
- `auth/service/EmployeeServiceTest`
- `auth/service/PermissionServiceTest`
- `auth/service/RolePermissionServiceTest`
- `auth/service/RoleServiceTest`
- `auth/service/UserRoleAssignmentServiceTest`
- `auth/service/UserServiceTest`
- `dashboard/service/DashboardServiceTest`
- `leave/service/FileStorageServiceTest`
- `leave/service/LeaveRequestServiceTest`
- `notification/service/NotificationConsumerTest`
- `notification/service/NotificationPublisherTest`
- `organisation/service/ProjectServiceTest`

### Web Layer
- `approval/controller/ApprovalStepControllerTest`
- `auth/controller/DepartmentControllerTest`
- `dashboard/controller/DashboardControllerTest`
- `leave/controller/LeaveRequestAttachmentControllerTest`
- `leave/controller/LeaveRequestControllerTest`
- `notification/controller/NotificationControllerTest`
- `organisation/controller/WorkScheduleControllerTest`

### Entities and Shared Infrastructure
- `admin/entity/AdminRequestTest`
- `approval/entity/ApprovalStepTest`
- `common/GlobalExceptionHandlerTest`
- `leave/entity/LeaveBalanceTest`
- `security/JwtAuthenticationFilterTest`
- `support/TestAuthenticationFactory.java`

## Current Constraints
- No checked-in `@SpringBootTest` integration suite
- No checked-in `src/test/resources`
- No configured JaCoCo coverage plugin
- No Maven wrapper

## Run Commands

Run commands from `backend/hris/`.

```bash
mvn test
```

```bash
mvn -Dtest=RoleServiceTest test
```

```bash
mvn -Dtest=JwtAuthenticationFilterTest,ProjectServiceTest,RoleServiceTest,LeaveRequestServiceTest test
```

For the fuller explanation of test layout and current limitations, see `COMPREHENSIVE_TEST_SUITE_SUMMARY.md`.
