import fs from 'fs';

const extractPath = 'C:/Users/Youssef/.gemini/antigravity/scratch/pfe/backend/hris/.understand-anything/tmp/ua-file-extract-results-2.json';
const inputPath = 'C:/Users/Youssef/.gemini/antigravity/scratch/pfe/backend/hris/.understand-anything/tmp/batch-input-2.json';
const extract = JSON.parse(fs.readFileSync(extractPath, 'utf8'));
const input = JSON.parse(fs.readFileSync(inputPath, 'utf8'));
const importData = input.batchImportData || {};

const nodes = [];
const edges = [];

const fileMeta = {
  'src/main/java/com/hris/auth/service/EmployeeService.java': {
    summary: 'Service managing the employee lifecycle including profile retrieval, updates, supervisor validation, and leave balance initialization with scope-based access control.',
    tags: ['service','employee','crud','scope-aware','business-logic'],
    complexity: 'complex'
  },
  'src/main/java/com/hris/leave/entity/LeavePolicy.java': {
    summary: 'JPA entity mapping leave policy rules per contract type, defining accrual configuration for leave balances.',
    tags: ['entity','jpa','leave','policy','data-model'],
    complexity: 'simple'
  },
  'src/main/java/com/hris/leave/repository/LeavePolicyRepository.java': {
    summary: 'Spring Data JPA repository for LeavePolicy with contract-type lookup methods.',
    tags: ['repository','jpa','leave','data-access'],
    complexity: 'simple'
  },
  'src/main/java/com/hris/leave/service/LeaveApprovalWorkflowService.java': {
    summary: 'Core leave approval workflow engine that resolves approver hierarchy from team chain, department, and project scopes and instantiates multi-step ApprovalWorkflow snapshots for leave requests.',
    tags: ['service','workflow','approval','hierarchy-resolver','business-logic'],
    complexity: 'complex'
  },
  'src/main/java/com/hris/organisation/controller/ProjectController.java': {
    summary: 'REST controller exposing project, project-team, assignment, and department-link endpoints, guarded by permission checks and returning ApiResponse / PageResponse envelopes.',
    tags: ['api-handler','controller','project','rest','authorization'],
    complexity: 'moderate'
  },
  'src/main/java/com/hris/organisation/dto/ProjectAssignmentCreateDto.java': { summary: 'Request DTO carrying the employee, role, and date range when assigning an employee to a project.', tags: ['dto','project','assignment','request'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/dto/ProjectAssignmentResponseDto.java': { summary: 'Response DTO returning the persisted project assignment including identifiers, dates, and active flag.', tags: ['dto','project','assignment','response'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/dto/ProjectAssignmentViewDto.java': { summary: 'Aggregated view DTO joining employee, project, and role information for assignment listings.', tags: ['dto','project','assignment','view'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/dto/ProjectCreateDto.java': { summary: 'Request DTO for creating or updating a project with code, name, description, and date range.', tags: ['dto','project','request'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/dto/ProjectDepartmentAssignDto.java': { summary: 'Request DTO linking a department to a project.', tags: ['dto','project','department','request'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/dto/ProjectDepartmentResponseDto.java': { summary: 'Response DTO returning a project-department link with department metadata.', tags: ['dto','project','department','response'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/dto/ProjectResponseDto.java': { summary: 'Response DTO describing a project with code, name, status, and dates.', tags: ['dto','project','response'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/dto/ProjectTeamCreateDto.java': { summary: 'Request DTO for creating a project-scoped team including leader, parent supervisor, and date window.', tags: ['dto','project','team','request'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/dto/ProjectTeamResponseDto.java': { summary: 'Response DTO returning a project-team including code, name, and leadership references.', tags: ['dto','project','team','response'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/entity/Project.java': { summary: 'JPA entity representing a project with code, status, lifecycle dates, and relationships to assignments and teams.', tags: ['entity','jpa','project','data-model'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/entity/ProjectAssignment.java': { summary: 'JPA entity linking an employee to a project with a role and effective date range.', tags: ['entity','jpa','project','assignment','data-model'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/entity/ProjectDepartment.java': { summary: 'JPA join entity associating a department with a project to scope project visibility.', tags: ['entity','jpa','project','department','data-model'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/entity/Team.java': { summary: 'JPA entity representing an organisational team with leader, parent team, and project link references.', tags: ['entity','jpa','team','data-model'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/entity/TeamProjectLink.java': { summary: 'JPA entity binding a team to a project with effective dates, used by approval routing.', tags: ['entity','jpa','team','project','data-model'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/enums/ProjectRole.java': { summary: 'Enumeration of roles an employee can hold on a project assignment.', tags: ['enum','project','role'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/enums/ProjectStatus.java': { summary: 'Enumeration of project lifecycle states (active, archived, etc.).', tags: ['enum','project','status'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/hierarchy/entity/TeamHierarchyRelation.java': { summary: 'JPA entity modelling a parent-child relation between teams or employees with effective dating used for approval chains.', tags: ['entity','jpa','team','hierarchy','data-model'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/hierarchy/entity/TeamHierarchyStatus.java': { summary: 'Enumeration of statuses (active, ended) for a team hierarchy relation.', tags: ['enum','team','hierarchy','status'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/hierarchy/repository/TeamHierarchyRelationRepository.java': { summary: 'Spring Data repository for TeamHierarchyRelation supporting hierarchy traversal queries.', tags: ['repository','jpa','team','hierarchy','data-access'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/hierarchy/service/TeamHierarchyService.java': { summary: 'Service that builds, validates, and mutates the team hierarchy graph, ensuring no cycles, computing levels, and emitting designation and ended notification events.',
    tags: ['service','team','hierarchy','graph','workflow'], complexity: 'complex' },
  'src/main/java/com/hris/organisation/mapper/ProjectMapper.java': { summary: 'MapStruct mapper translating Project entities to DTOs.', tags: ['mapper','project','mapstruct'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/repository/ProjectAssignmentRepository.java': { summary: 'Spring Data repository for ProjectAssignment exposing scope-filtered queries used by approval routing and project administration.',
    tags: ['repository','jpa','project','assignment','data-access'], complexity: 'complex' },
  'src/main/java/com/hris/organisation/repository/ProjectDepartmentRepository.java': { summary: 'Spring Data repository for ProjectDepartment links with project/department lookup queries.', tags: ['repository','jpa','project','department','data-access'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/repository/ProjectRepository.java': { summary: 'Spring Data repository for Project entities with scope and status-aware queries.', tags: ['repository','jpa','project','data-access'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/repository/TeamProjectLinkRepository.java': { summary: 'Spring Data repository for TeamProjectLink supporting effective-date queries used during approval routing.', tags: ['repository','jpa','team','project','data-access'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/repository/TeamRepository.java': { summary: 'Spring Data repository for Team entities with code, leader, and parent lookups.', tags: ['repository','jpa','team','data-access'], complexity: 'simple' },
  'src/main/java/com/hris/organisation/service/ProjectService.java': { summary: 'Comprehensive service orchestrating project CRUD, team creation, employee and department assignments, scope-based read filtering, and notification publication for project leads.',
    tags: ['service','project','crud','workflow','business-logic'], complexity: 'complex' },
  'src/main/java/com/hris/organisation/service/TeamService.java': { summary: 'Service handling team CRUD with code normalization, leader and parent validation, and scope-aware list queries.',
    tags: ['service','team','crud','validation','business-logic'], complexity: 'complex' },
  'src/main/java/com/hris/security/service/AccessScopeService.java': { summary: 'Security helper resolving the caller effective permissions, approval scope, manager visibility, and department or project scope references used to filter business reads.',
    tags: ['security','scope','authorization','service','permission'], complexity: 'complex' }
};

const files = input.files;

for (const f of files) {
  const fp = f.path;
  const meta = fileMeta[fp] || { summary: 'Java source file.', tags: ['java','code'], complexity: 'simple' };
  const name = fp.split('/').pop();
  nodes.push({
    id: `file:${fp}`,
    type: 'file',
    name,
    filePath: fp,
    summary: meta.summary,
    tags: meta.tags,
    complexity: meta.complexity
  });
}

function isSignificant(fn) {
  const span = (fn.endLine || 0) - (fn.startLine || 0);
  return span >= 10;
}

const fnSummaries = {
  'EmployeeService:getAll': 'Lists employees with pagination, applying scope-based filtering for the current caller.',
  'EmployeeService:getProfileSummary': 'Assembles an employee profile summary including leave balances and department history.',
  'EmployeeService:getById': 'Loads a single employee, enforcing scoped read access.',
  'EmployeeService:update': 'Updates employee fields with supervisor validation, history capture, and audit logging.',
  'EmployeeService:delete': 'Soft-deletes an employee and records the status change in history.',
  'EmployeeService:initializeLeaveBalancesForNewEmployee': 'Creates initial leave balances for a newly onboarded employee based on contract-type leave policies.',
  'EmployeeService:resolveReadScope': 'Resolves the department scope a caller is allowed to read employees within.',
  'LeaveApprovalWorkflowService:instantiate': 'Builds and persists an ApprovalWorkflow for a leave request including all approval steps.',
  'LeaveApprovalWorkflowService:resolveTeamId': 'Determines which team identifier governs approval routing for a given leave request.',
  'LeaveApprovalWorkflowService:resolveRoute': 'Resolves the ordered list of approvers by walking the team hierarchy and project links.',
  'LeaveApprovalWorkflowService:resolveProfileBasedEscalation': 'Computes fallback approvers based on access profiles when hierarchy resolution yields none.',
  'LeaveApprovalWorkflowService:toHierarchyApprovers': 'Maps hierarchy relations and team chain heads into approver entries with metadata.',
  'LeaveApprovalWorkflowService:resolveFallbackApprovers': 'Resolves the configured fallback approvers when the primary chain is empty.',
  'LeaveApprovalWorkflowService:buildSteps': 'Builds ApprovalStep entities from the resolved approver list in the configured order.',
  'LeaveApprovalWorkflowService:buildWorkflowSnapshot': 'Serializes the workflow context into a snapshot for audit replay.',
  'ProjectController:getAll': 'Lists projects with pagination, gated by project read permissions.',
  'ProjectController:create': 'Creates a new project after validating the request.',
  'ProjectController:update': 'Updates an existing project after permission and validation checks.',
  'ProjectController:deactivate': 'Marks a project as inactive without deleting historical data.',
  'ProjectController:hardDelete': 'Hard-deletes a project after admin authorization, removing related assignments.',
  'ProjectController:createTeam': 'Creates a new team within a project including leader and parent supervisor.',
  'ProjectController:assignEmployee': 'Assigns an employee to a project with a role and effective dates.',
  'ProjectController:removeAssignment': 'Ends an existing project assignment for an employee.',
  'ProjectController:assignDepartment': 'Links a department to a project to broaden its scope.',
  'ProjectController:removeDepartment': 'Unlinks a department from a project.',
  'TeamHierarchyService:getHierarchy': 'Loads the active team hierarchy as a node-and-children DTO tree.',
  'TeamHierarchyService:create': 'Creates a new parent-child hierarchy relation after validation.',
  'TeamHierarchyService:update': 'Updates an existing hierarchy relation effective dates or parent.',
  'TeamHierarchyService:endRelation': 'Ends a hierarchy relation on a given date and emits a notification.',
  'TeamHierarchyService:validateMutation': 'Validates a hierarchy mutation against overlap and cycle constraints.',
  'TeamHierarchyService:ensureNoCycle': 'Walks the parent chain to ensure the proposed relation introduces no cycle.',
  'TeamHierarchyService:computeLevels': 'Computes a depth level for every node in the hierarchy.',
  'TeamHierarchyService:computeLevel': 'Recursively computes the depth level for a single hierarchy node.',
  'TeamHierarchyService:toNodeDto': 'Maps a TeamHierarchyRelation into a DTO with resolved names and children.',
  'TeamHierarchyService:publishDesignationEvent': 'Publishes a RabbitMQ notification when a designation is set.',
  'TeamHierarchyService:publishEndedEvent': 'Publishes a notification when a hierarchy relation is ended.',
  'TeamHierarchyService:publishEvent': 'Common helper that serializes payloads and publishes notification events.',
  'ProjectService:getAll': 'Returns paginated projects filtered by the caller read scope.',
  'ProjectService:create': 'Persists a new project after validating uniqueness and dates.',
  'ProjectService:update': 'Updates a project metadata, capturing the prior snapshot for audit.',
  'ProjectService:deactivate': 'Deactivates a project while preserving historical assignments.',
  'ProjectService:hardDelete': 'Removes a project and all dependent records under admin permission.',
  'ProjectService:assignEmployee': 'Assigns an employee to a project with role validation and schedules a notification.',
  'ProjectService:createTeam': 'Creates a project-scoped team validating leader, parent supervisor, and project membership.',
  'ProjectService:removeAssignment': 'Ends a project assignment and records the change.',
  'ProjectService:assignDepartment': 'Links a department to a project, broadening its visibility.',
  'ProjectService:validateTeam': 'Validates a project-team creation request including leader and supervisor constraints.',
  'ProjectService:validateAssignment': 'Validates a project assignment request: employee existence, role, and date window.',
  'ProjectService:ensureProjectScopedManagementAccess': 'Ensures the caller has permission to manage the specific project.',
  'ProjectService:resolveReadScope': 'Resolves which projects the caller is allowed to read based on their access profile.',
  'ProjectService:buildAssignmentNotification': 'Builds the notification payload published when an employee is assigned to a project.',
  'ProjectService:normalizeAssignmentView': 'Normalises an assignment view by resolving employee and project names.',
  'ProjectService:validateLeaderParentSupervisor': 'Validates that the leader and parent supervisor on a team request are coherent.',
  'ProjectService:createTeamAssignment': 'Persists the assignment linking an employee to a newly created project team.',
  'ProjectService:toTeamDto': 'Maps a Team entity together with its project link into a response DTO.',
  'ProjectService:buildTeamCode': 'Generates a deterministic code for a new project-scoped team.',
  'ProjectService:publishProjectLeadEvent': 'Publishes a RabbitMQ event when a project lead is designated.',
  'TeamService:getAll': 'Lists teams filtered by the caller read scope.',
  'TeamService:getById': 'Loads a single team enforcing scoped read access.',
  'TeamService:create': 'Creates a new team after normalising its code and validating leader and parent.',
  'TeamService:update': 'Updates a team metadata with validation and audit snapshot.',
  'TeamService:hardDelete': 'Hard-deletes a team after verifying no dependent records remain.',
  'TeamService:validate': 'Validates a team request against business rules.',
  'TeamService:toDto': 'Maps a Team entity to its response DTO including resolved leader name.',
  'TeamService:resolveReadScope': 'Resolves the set of departments the caller may read teams within.',
  'AccessScopeService:resolveDepartmentDataScope': 'Computes the effective set of department IDs the caller may operate against for business reads.',
  'AccessScopeService:resolveDepartmentIdsFromScopeRefs': 'Resolves explicit scope references on the access profile into concrete department IDs.',
  'AccessScopeService:hasAdministrationOrHrVisibility': 'Returns true when the caller has either administration or HR-wide visibility profiles.',
  'AccessScopeService:hasManagerDepartmentVisibility': 'Determines whether the caller can see department-scoped data as a manager.',
  'AccessScopeService:addProjectDepartments': 'Adds departments linked to projects the caller manages into the scope set.'
};

function fnSummary(cls, name) {
  return fnSummaries[`${cls}:${name}`] || `Helper method ${name} in ${cls}.`;
}

for (const r of extract.results) {
  const fp = r.path;
  const fname = fp.split('/').pop();
  for (const c of (r.classes || [])) {
    const methodCount = (c.methods || []).length;
    const span = (c.endLine || 0) - (c.startLine || 0);
    if (methodCount >= 2 || span >= 20) {
      nodes.push({
        id: `class:${fp}:${c.name}`,
        type: 'class',
        name: c.name,
        filePath: fp,
        lineRange: [c.startLine, c.endLine],
        summary: `Class ${c.name} declared in ${fname}.`,
        tags: ['class','java'],
        complexity: span > 200 ? 'complex' : (span > 50 ? 'moderate' : 'simple')
      });
      edges.push({ source: `file:${fp}`, target: `class:${fp}:${c.name}`, type: 'contains', direction: 'forward', weight: 1.0 });
    }
  }
  const primaryClass = (r.classes && r.classes[0]) ? r.classes[0].name : '';
  for (const fn of (r.functions || [])) {
    if (!isSignificant(fn)) continue;
    nodes.push({
      id: `function:${fp}:${fn.name}`,
      type: 'function',
      name: fn.name,
      filePath: fp,
      lineRange: [fn.startLine, fn.endLine],
      summary: fnSummary(primaryClass, fn.name),
      tags: ['function','java','method'],
      complexity: ((fn.endLine - fn.startLine) > 60 ? 'complex' : ((fn.endLine - fn.startLine) > 25 ? 'moderate' : 'simple'))
    });
    edges.push({ source: `file:${fp}`, target: `function:${fp}:${fn.name}`, type: 'contains', direction: 'forward', weight: 1.0 });
  }
}

for (const src of Object.keys(importData)) {
  for (const tgt of importData[src]) {
    edges.push({ source: `file:${src}`, target: `file:${tgt}`, type: 'imports', direction: 'forward', weight: 0.7 });
  }
}

console.log('nodes:', nodes.length, 'edges:', edges.length);

const outDir = 'C:/Users/Youssef/.gemini/antigravity/scratch/pfe/backend/hris/.understand-anything/intermediate';
if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });

function splitAndWrite(nodes, edges) {
  const N = nodes.length, E = edges.length;
  if (N <= 60 && E <= 120) {
    fs.writeFileSync(`${outDir}/batch-2.json`, JSON.stringify({nodes, edges}, null, 2));
    return 1;
  }
  let parts = Math.ceil(Math.max(N/60, E/120));
  // bump parts until every chunk fits — uneven distribution may need more
  while (parts < 12) {
    const filesSorted = [...new Set(nodes.filter(n => n.filePath).map(n => n.filePath))].sort();
    const chunkSize = Math.ceil(filesSorted.length / parts);
    let ok = true;
    for (let k = 0; k < parts; k++) {
      const partFiles = new Set(filesSorted.slice(k*chunkSize, (k+1)*chunkSize));
      const pn = nodes.filter(n => partFiles.has(n.filePath));
      const pi = new Set(pn.map(n => n.id));
      const pe = edges.filter(e => pi.has(e.source));
      if (pn.length > 60 || pe.length > 120) { ok = false; break; }
    }
    if (ok) break;
    parts++;
  }
  const filesSorted = [...new Set(nodes.filter(n => n.filePath).map(n => n.filePath))].sort();
  const chunkSize = Math.ceil(filesSorted.length / parts);
  for (let k = 0; k < parts; k++) {
    const partFiles = new Set(filesSorted.slice(k*chunkSize, (k+1)*chunkSize));
    const partNodes = nodes.filter(n => partFiles.has(n.filePath));
    const partIds = new Set(partNodes.map(n => n.id));
    const partEdges = edges.filter(e => partIds.has(e.source));
    const out = { nodes: partNodes, edges: partEdges };
    fs.writeFileSync(`${outDir}/batch-2-part-${k+1}.json`, JSON.stringify(out, null, 2));
    console.log(`part ${k+1}: nodes=${partNodes.length} edges=${partEdges.length}`);
  }
  return parts;
}

const parts = splitAndWrite(nodes, edges);
console.log('parts written:', parts);

// Verify import count
let totalImports = 0;
for (const k of Object.keys(importData)) totalImports += importData[k].length;
const importEdgesEmitted = edges.filter(e => e.type === 'imports').length;
console.log(`import edges expected=${totalImports} emitted=${importEdgesEmitted}`);
