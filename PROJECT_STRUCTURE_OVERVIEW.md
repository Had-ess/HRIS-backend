# HRIS Backend Project Structure Overview

## Project Information
- **Framework**: Spring Boot
- **Main Package**: `com.hris`
- **Main Class**: [HrisApplication.java](src/main/java/com/hris/HrisApplication.java)
- **Architecture**: Layered architecture with Controllers, Services, Repositories, and Entities
- **Data Auditing**: JPA Auditing enabled

---

## 1. PACKAGE STRUCTURE & MAIN APPLICATION PACKAGES

The project follows a modular domain-driven design pattern with the following main packages:

```
com.hris
├── admin/           # Admin request management
├── analytics/       # Analytics, reporting, and audit logging
├── approval/        # Workflow approval system
├── auth/            # Authentication, users, employees, departments, roles
├── common/          # Cross-cutting concerns (exceptions, responses, filters)
├── config/          # Application configuration
├── leave/           # Leave management system
├── notification/    # Notification system with message queue integration
├── organisation/    # Organization structure (projects, departments, work schedules)
└── security/        # Security utilities and JWT authentication
```

---

## 2. ENTITIES & MODELS

### **2.1 Auth Module - User & Organization Management**
- **User.java** - Core user entity with authentication details
- **Employee.java** - Employee records linked to users with:
  - Employee code, hire date, job title
  - Employment status (enum: ACTIVE, INACTIVE, ON_LEAVE, etc.)
  - Contract type (enum: PERMANENT, CONTRACT, etc.)
  - Department and work schedule references
- **Department.java** - Organizational departments
- **Role.java** - User roles for authorization
- **Permission.java** - Fine-grained permissions
- **RolePermission.java** - Mapping between roles and permissions
- **UserRole.java** - Mapping between users and roles

### **2.2 Leave Module - Leave Management**
- **LeaveRequest.java** - Leave request submissions with:
  - Employee ID, leave type, start/end dates
  - Working days calculation
  - Urgency level (enum: URGENT, HIGH, MEDIUM, LOW)
  - Status tracking
- **LeaveBalance.java** - Employee leave balance records
- **LeaveType.java** - Types of leave (annual, sick, personal, etc.)
- **LeavePolicy.java** - Organization-wide leave policies
- **FileAttachment.java** - Attachments for leave requests

### **2.3 Approval Module - Workflow Management**
- **ApprovalWorkflow.java** - Workflow definitions for various subject types with:
  - Subject type (e.g., "LEAVE_REQUEST", "ADMIN_REQUEST")
  - Subject ID and workflow status
- **ApprovalStep.java** - Individual approval steps within workflows with:
  - Step sequence, approver details
  - Decision tracking and comments

### **2.4 Admin Module - Administrative Requests**
- **AdminRequest.java** - Administrative requests (access requests, etc.)
- **AdminRequestType.java** - Types of admin requests

### **2.5 Notification Module - Event-Driven Notifications**
- **Notification.java** - Notification records with metadata
- **NotificationEvent.java** - Events that trigger notifications

### **2.6 Analytics Module - Reporting & Auditing**
- **AuditLog.java** - Audit trail for system actions with:
  - Action details, user info, timestamps
  - Scope information (department, project, etc.)
- **LeaveMetrics.java** - Leave statistics and metrics
- **HeadcountMetrics.java** - Headcount and staffing metrics
- **AbsenceImpactReport.java** - Reports on absence impact
- **AnalyticsDashboard.java** - Dashboard data aggregation
- **ExportRecord.java** - Export history tracking

### **2.7 Organisation Module - Structure & Scheduling**
- **Project.java** - Projects with:
  - Project name, status, description
  - Date ranges and status tracking
- **ProjectAssignment.java** - Employee assignments to projects
- **ProjectDepartment.java** - Project-to-department mappings
- **WorkSchedule.java** - Work schedules (shift timings, working days)
- **PublicHoliday.java** - Organization public holidays

---

## 3. CONTROLLERS & REST ENDPOINTS

### **3.1 Authentication & User Management** (`/api`)
- **EmployeeController**
  - `GET /api/employees` - List all employees (paginated)
  - `POST /api/employees` - Create new employee
  - `GET /api/employees/{id}` - Get employee by ID
  - Role: `HR_ADMIN` required
  
- **UserController**
  - User management endpoints
  
- **DepartmentController**
  - Department management endpoints

### **3.2 Leave Management** (`/api/leave-requests`)
- **LeaveRequestController**
  - `POST /api/leave-requests` - Submit leave request
  - `GET /api/leave-requests` - Get leave requests (paginated)
  - `GET /api/leave-requests/{id}` - Get specific request
  - Supports file attachments for leave requests
  
- **LeaveBalanceController**
  - `GET /api/leave-balance` - View leave balance
  - Leave balance management endpoints

### **3.3 Approval Management** (`/api/approval`)
- **ApprovalStepController**
  - Workflow approval endpoints
  - Approve/reject workflow steps with comments

### **3.4 Admin Requests** (`/api/admin`)
- **AdminRequestController**
  - Handle admin-level requests
  
- **AdminRequestTypeController**
  - Manage request types

### **3.5 Analytics & Reporting** (`/api/analytics`)
- **AnalyticsController**
  - `GET /api/analytics/dashboard` - Dashboard data
  - `GET /api/analytics/audit-logs` - Audit logs
  - `GET /api/analytics/metrics` - Various metrics endpoints
  - Reporting and KPI retrieval

### **3.6 Organization & Projects** (`/api/projects`)
- **ProjectController**
  - `POST /api/projects` - Create project
  - `GET /api/projects` - List projects (paginated)
  - `GET /api/projects/{id}` - Get project details
  - Project management with assignments

---

## 4. SERVICES & BUSINESS LOGIC

### **4.1 Auth Module Services**
- **EmployeeService** - Employee lifecycle management, status changes
- **UserService** - User account operations
- **KeycloakSyncService** - Synchronization with Keycloak identity provider

### **4.2 Leave Module Services**
- **LeaveRequestService** - Leave request processing with:
  - Leave balance validation
  - Working days calculation
  - Approval workflow integration
- **LeaveBalanceService** - Balance tracking and updates
- **FileStorageService** - File attachment handling (integration with MinIO)

### **4.3 Approval Module Services**
- **ApprovalStepService** - Workflow step processing
- **ApprovalRouter** - Smart routing of approval workflows

### **4.4 Notification Module Services**
- **NotificationPublisher** - Publishes messages to RabbitMQ queue
- **NotificationConsumer** - Consumes notification messages from queue
- **DeadLetterQueueHandler** - Handles failed message retries

### **4.5 Analytics Module Services**
- **AuditLogService** - Records and retrieves audit trails
- **LeaveMetricsService** - Calculates leave statistics
- **ScopeFilterResolver** - Resolves data scoping based on user role/department

### **4.6 Organization Module Services**
- **ProjectService** - Project management operations
- **ProjectAssignmentCleanupService** - Maintenance of project assignments
- **WorkScheduleService** - Work schedule management

---

## 5. REPOSITORIES

All repositories extend `JpaRepository` for database operations. Each entity has a corresponding repository:

### **Auth Module Repositories**
- `EmployeeRepository` - Employee data access
- `UserRepository` - User data access
- `DepartmentRepository` - Department data access
- `RoleRepository` - Role data access
- `RolePermissionRepository` - Role-permission mappings
- `UserRoleRepository` - User-role mappings

### **Leave Module Repositories**
- `LeaveRequestRepository` - Leave request queries
- `LeaveBalanceRepository` - Balance lookups
- `LeaveTypeRepository` - Leave type management
- `LeavePolicyRepository` - Policy queries
- `FileAttachmentRepository` - Attachment storage references

### **Approval Module Repositories**
- `ApprovalWorkflowRepository` - Workflow data access
- `ApprovalStepRepository` - Step data access

### **Admin Module Repositories**
- `AdminRequestRepository` - Admin request data access
- `AdminRequestTypeRepository` - Admin request type management

### **Notification Module Repositories**
- `NotificationRepository` - Notification records
- `NotificationEventRepository` - Event records

### **Analytics Module Repositories**
- `AuditLogRepository` - Audit trail queries
- `LeaveMetricsRepository` - Metrics data access
- `HeadcountMetricsRepository` - Headcount data
- `AbsenceImpactReportRepository` - Report data
- `AnalyticsDashboardRepository` - Dashboard aggregations
- `ExportRecordRepository` - Export history

### **Organisation Module Repositories**
- `ProjectRepository` - Project data access
- `ProjectAssignmentRepository` - Assignment lookups
- `ProjectDepartmentRepository` - Project-department mappings
- `WorkScheduleRepository` - Schedule queries
- `PublicHolidayRepository` - Holiday lookups

---

## 6. CONFIGURATIONS & UTILITIES

### **6.1 Core Configuration Files** (`com.hris.config`)
- **SecurityConfig.java** - Spring Security configuration with:
  - JWT validation
  - Role-based access control (RBAC)
  - CORS settings
  
- **JacksonConfig.java** - JSON serialization customization
- **MinioConfig.java** - Integration with MinIO for file storage
- **RabbitMQConfig.java** - RabbitMQ message queue configuration
- **RetryConfig.java** - Retry policies for resilience
- **SchedulingConfig.java** - Task scheduling configuration

### **6.2 Security & Authentication** (`com.hris.security`)
- **JwtAuthenticationFilter.java** - JWT token validation filter
- **KeycloakJwtConverter.java** - Converts Keycloak JWT tokens to Spring Security authorities
- **SecurityUtils.java** - Utility methods for extracting current user info

### **6.3 Common Utilities** (`com.hris.common`)
- **ApiResponse.java** - Standardized API response wrapper for all endpoints
- **GlobalExceptionHandler.java** - Centralized exception handling for consistent error responses
- **ScopeFilter.java** - Data scoping filter for multi-tenancy/department-level access control
- **Custom Exceptions**:
  - `InsufficientLeaveBalanceException` - Thrown when leave balance is insufficient
  - `StepAlreadyDecidedException` - Thrown when approval step already has a decision

### **6.4 Data Transfer Objects (DTOs)**

#### Auth Module DTOs
- `EmployeeCreateDto` - Employee creation request
- `EmployeeResponseDto` - Employee response data
- `UserResponseDto` - User response data
- `UpdateLocaleDto` - Locale/language preference update

#### Leave Module DTOs
- `CreateLeaveRequestDto` - Leave request submission
- `LeaveRequestResponseDto` - Leave request response with status
- `LeaveBalanceDto` - Balance information
- `NotificationParamsDto` - Parameters for leave notifications

#### Approval Module DTOs
- `ApprovalDecisionDto` - Approval decision submission
- `ApprovalStepResponseDto` - Step details response
- `ApprovalWorkflowResponseDto` - Workflow status response

#### Admin Module DTOs
- `CreateAdminRequestDto` - Admin request creation
- `AdminRequestResponseDto` - Admin request response
- `AdminRequestTypeDto` - Request type information

#### Notification Module DTOs
- `NotificationResponseDto` - Notification details

#### Organisation Module DTOs
- `ProjectCreateDto` - Project creation request
- `ProjectResponseDto` - Project details response
- `ProjectAssignmentCreateDto` - Assignment creation
- `ProjectAssignmentResponseDto` - Assignment details

### **6.5 Mappers** (DTO/Entity Conversion)
- `EmployeeMapper` - Employee entity ↔ DTO conversion
- `UserMapper` - User entity ↔ DTO conversion
- `LeaveMapper` - Leave request entity ↔ DTO conversion
- `ApprovalMapper` - Approval workflow entity ↔ DTO conversion
- `AdminRequestMapper` - Admin request entity ↔ DTO conversion
- `NotificationMapper` - Notification entity ↔ DTO conversion
- `ProjectMapper` - Project entity ↔ DTO conversion

### **6.6 Enumerations (Enums)**

#### Auth Module Enums
- `EmployeeStatus` - ACTIVE, INACTIVE, ON_LEAVE, TERMINATED, etc.
- `ContractType` - PERMANENT, CONTRACT, INTERNSHIP, etc.
- `PermissionAction` - READ, WRITE, DELETE, APPROVE, etc.

#### Leave Module Enums
- `LeaveStatus` - PENDING, APPROVED, REJECTED, CANCELLED, EXPIRED
- `LeaveTypeCode` - Annual, sick leave, personal, etc.
- `UrgencyLevel` - URGENT, HIGH, MEDIUM, LOW

#### Approval Module Enums
- `StepStatus` - PENDING, APPROVED, REJECTED, ESCALATED
- `WorkflowStatus` - IN_PROGRESS, COMPLETED, CANCELLED
- `ApprovalContext` - Context information for routing

#### Analytics Module Enums
- `AuditAction` - CREATE, UPDATE, DELETE, APPROVE, REJECT, etc.
- `RiskLevel` - LOW, MEDIUM, HIGH, CRITICAL
- `ScopeType` - DEPARTMENT, PROJECT, ORGANIZATION, INDIVIDUAL

#### Notification Module Enums
- `NotificationEventType` - LEAVE_APPROVED, LEAVE_REJECTED, LEAVE_PENDING, etc.

#### Organisation Module Enums
- `ProjectStatus` - ACTIVE, COMPLETED, ON_HOLD, CANCELLED
- `ProjectRole` - PROJECT_MANAGER, TEAM_MEMBER, STAKEHOLDER, etc.

---

## 7. INTEGRATION POINTS

### **External Integrations**
1. **Keycloak** - Identity and Access Management (IAM)
   - User authentication and synchronization
   - Role-based access control

2. **MinIO** - File Storage
   - Stores leave request attachments
   - File retrieval and management

3. **RabbitMQ** - Message Queue
   - Event-driven notification system
   - Asynchronous message processing with dead-letter queue

4. **Database** - JPA/Hibernate
   - Object-relational mapping
   - Audit trail with JPA Auditing

---

## 8. KEY DESIGN PATTERNS

1. **Layered Architecture** - Clear separation of concerns (Controller → Service → Repository)
2. **Repository Pattern** - Data access abstraction via Spring Data JPA
3. **DTO Pattern** - Data transfer between layers with MapStruct/manual mappers
4. **Exception Handling** - Centralized global exception handling
5. **Aspect-Oriented Programming** - Auditing, scope filtering via filters
6. **Event-Driven Architecture** - Asynchronous notifications via message queue
7. **Multi-tenancy** - Department/scope-based data filtering
8. **JWT Authentication** - Stateless security with token-based auth

---

## 9. DATA FLOW EXAMPLES

### Leave Request Approval Flow
```
LeaveRequestController.create()
  → LeaveRequestService.create()
    → Validate leave balance
    → Calculate working days
    → Save LeaveRequest
    → ApprovalRouter.routeForApproval()
      → Create ApprovalWorkflow
      → Create ApprovalStep(s)
      → NotificationPublisher.publishEvent(LEAVE_PENDING)
        → RabbitMQ → NotificationConsumer → Notifies managers
```

### Admin Action Audit Flow
```
Any Controller endpoint
  → GlobalExceptionHandler / ScopeFilter logs action
  → AuditLogService.logAction()
    → Save AuditLog entity
    → Update LeaveMetrics if applicable
    → AnalyticsDashboard aggregates for reporting
```

---

## 10. PROJECT STATISTICS

- **Total Packages**: 11 main packages
- **Entities**: 24 entities
- **Controllers**: 7 REST controllers
- **Services**: 14+ service classes
- **Repositories**: 30+ repository interfaces
- **DTOs**: 15+ data transfer objects
- **Enums**: 10+ enumeration types
- **Configuration Classes**: 6
- **Security/Filter Classes**: 3

---

## Technology Stack Summary
- **Framework**: Spring Boot
- **Database**: JPA/Hibernate with PostgreSQL/MySQL
- **Authentication**: JWT + Keycloak
- **Message Queue**: RabbitMQ
- **File Storage**: MinIO
- **Mapping**: Lombok, MapStruct
- **Build**: Maven (pom.xml present)
- **Architecture**: Microservices-ready modular design
