import fs from 'fs';
const r = JSON.parse(fs.readFileSync('C:/Users/Youssef/.gemini/antigravity/scratch/pfe/backend/hris/.understand-anything/tmp/ua-file-extract-results-1.json','utf8'));
const input = JSON.parse(fs.readFileSync('C:/Users/Youssef/.gemini/antigravity/scratch/pfe/backend/hris/.understand-anything/tmp/batch-input-1.json','utf8'));
const imports = input.batchImportData;

const fileMeta = {
  'src/main/java/com/hris/access/controller/AccessController.java': {
    summary: 'REST controller exposing /access endpoints so the current user can fetch effective permissions and dynamic navigation menus.',
    tags: ['api-handler','controller','access-control','rest'], complexity:'simple'
  },
  'src/main/java/com/hris/access/dto/AccessMeResponseDto.java': {
    summary: 'DTO returned by the access /me endpoint, bundling user permissions and navigation tree for the front-end shell.',
    tags: ['dto','access-control','response-model'], complexity:'simple'
  },
  'src/main/java/com/hris/access/dto/AccessPermissionDto.java': {
    summary: 'DTO describing a single resolved permission with scope and originating profile, sent to the front-end.',
    tags: ['dto','access-control','permission'], complexity:'simple'
  },
  'src/main/java/com/hris/access/dto/NavigationItemDto.java': {
    summary: 'DTO representing a single navigation menu item (label, route, icon) gated by permissions.',
    tags: ['dto','navigation','ui-model'], complexity:'simple'
  },
  'src/main/java/com/hris/access/dto/NavigationSectionDto.java': {
    summary: 'DTO grouping navigation items into a labeled section of the application sidebar.',
    tags: ['dto','navigation','ui-model'], complexity:'simple'
  },
  'src/main/java/com/hris/access/enums/StructuralEventType.java': {
    summary: 'Enum classifying structural change events (team, department, employee, project) that trigger access-profile recomputation.',
    tags: ['enum','event','access-control'], complexity:'simple'
  },
  'src/main/java/com/hris/access/event/StructuralChangeEvent.java': {
    summary: 'Domain event payload published when organisational structure changes, consumed by the access bootstrap migration logic.',
    tags: ['event','access-control','domain-event'], complexity:'simple'
  },
  'src/main/java/com/hris/access/service/AccessResolutionService.java': {
    summary: 'Core authorization engine computing a user effective permissions, department scopes, and navigation tree by combining access profiles, manual grants, and team-hierarchy data.',
    tags: ['service','access-control','authorization','core'], complexity:'complex',
    languageNotes:'Heavy use of Java streams and CollectionUtils to merge multi-source permission sets; this is the central authorization resolver referenced by the security layer.'
  },
  'src/main/java/com/hris/access/service/ProfileBootstrapMigration.java': {
    summary: 'Idempotent migration runner that replays employees, department heads, team chain leads, and project managers into the access-profile assignment tables.',
    tags: ['service','migration','bootstrap','access-control'], complexity:'moderate'
  },
  'src/main/java/com/hris/analytics/entity/HeadcountFact.java': {
    summary: 'JPA entity persisting headcount snapshots per department and date for analytics dashboards.',
    tags: ['entity','analytics','jpa','fact-table'], complexity:'simple'
  },
  'src/main/java/com/hris/analytics/repository/HeadcountFactRepository.java': {
    summary: 'Spring Data JPA repository for HeadcountFact rows with bulk delete and recent-snapshot lookup.',
    tags: ['repository','analytics','data-access'], complexity:'simple'
  },
  'src/main/java/com/hris/analytics/service/AnalyticsAggregationService.java': {
    summary: 'Scheduled aggregation service that rebuilds analytics snapshots (headcount, leave distribution, approval bottlenecks, project absences) from operational tables.',
    tags: ['service','analytics','aggregation','reporting'], complexity:'complex'
  },
  'src/main/java/com/hris/approval/service/TeamHierarchyResolver.java': {
    summary: 'Resolves the chain of approvers above a leave requester by walking the team hierarchy upward through team leads.',
    tags: ['service','approval','team-hierarchy','workflow'], complexity:'moderate'
  },
  'src/main/java/com/hris/auth/controller/EmployeeController.java': {
    summary: 'REST controller for employee CRUD, onboarding triggers, activation email resend, and listings, secured by permission checks.',
    tags: ['api-handler','controller','employee','rest'], complexity:'moderate'
  },
  'src/main/java/com/hris/auth/dto/AccountProvisioningRequest.java': {
    summary: 'Inbound DTO carrying credentials and profile data to provision a new Keycloak account alongside an HRIS employee.',
    tags: ['dto','provisioning','request-model'], complexity:'simple'
  },
  'src/main/java/com/hris/auth/dto/DepartmentDto.java': {
    summary: 'DTO carrying department identity, name, and head metadata between the API and clients.',
    tags: ['dto','department','response-model'], complexity:'simple'
  },
  'src/main/java/com/hris/auth/dto/EmployeeCreateDto.java': {
    summary: 'Validated inbound DTO with all fields required to create a new employee including identity, contract, and assignment data.',
    tags: ['dto','employee','validation','request-model'], complexity:'simple'
  },
  'src/main/java/com/hris/auth/dto/EmployeeResponseDto.java': {
    summary: 'Outbound DTO exposing employee details enriched with department, manager, and account status fields.',
    tags: ['dto','employee','response-model'], complexity:'simple'
  },
  'src/main/java/com/hris/auth/dto/EmployeeUpdateDto.java': {
    summary: 'Inbound DTO for partial employee updates with optional, validated fields.',
    tags: ['dto','employee','validation','update'], complexity:'simple'
  },
  'src/main/java/com/hris/auth/entity/Department.java': {
    summary: 'JPA entity representing an organisational department with hierarchy, head reference, and active flag.',
    tags: ['entity','department','jpa','organisation'], complexity:'simple'
  },
  'src/main/java/com/hris/auth/entity/Employee.java': {
    summary: 'Core JPA entity capturing employee identity, department, manager, contract, and account-status fields used across the HRIS.',
    tags: ['entity','employee','jpa','core-model'], complexity:'moderate'
  },
  'src/main/java/com/hris/auth/entity/EmployeeDepartmentHistory.java': {
    summary: 'JPA history entity recording each department transfer of an employee with timestamps and reason.',
    tags: ['entity','history','department','audit'], complexity:'simple'
  },
  'src/main/java/com/hris/auth/entity/EmployeeStatusHistory.java': {
    summary: 'JPA history entity logging changes to an employee status (active, suspended, terminated) over time.',
    tags: ['entity','history','status','audit'], complexity:'simple'
  },
  'src/main/java/com/hris/auth/enums/AccountStatus.java': {
    summary: 'Enum capturing Keycloak-mirrored account lifecycle states (PENDING, ACTIVE, DISABLED).',
    tags: ['enum','account','status'], complexity:'simple'
  },
  'src/main/java/com/hris/auth/enums/ContractType.java': {
    summary: 'Enum enumerating supported employee contract types (CDI, CDD, internship, freelance).',
    tags: ['enum','contract','employee'], complexity:'simple'
  },
  'src/main/java/com/hris/auth/enums/EmployeeStatus.java': {
    summary: 'Enum representing employee operational states such as ACTIVE, ON_LEAVE, or TERMINATED.',
    tags: ['enum','employee','status'], complexity:'simple'
  },
  'src/main/java/com/hris/auth/mapper/DepartmentMapper.java': {
    summary: 'Mapper utility converting Department entities to DepartmentDto for API responses.',
    tags: ['mapper','department','dto-conversion'], complexity:'simple'
  },
  'src/main/java/com/hris/auth/mapper/EmployeeMapper.java': {
    summary: 'Mapper component converting Employee entities to outbound DTOs and applying create/update DTOs onto entities.',
    tags: ['mapper','employee','dto-conversion'], complexity:'simple'
  },
  'src/main/java/com/hris/auth/repository/DepartmentRepository.java': {
    summary: 'Spring Data JPA repository for Department with name uniqueness checks and head-based lookups.',
    tags: ['repository','department','data-access'], complexity:'simple'
  },
  'src/main/java/com/hris/auth/repository/EmployeeDepartmentHistoryRepository.java': {
    summary: 'Spring Data JPA repository for querying department-transfer history records.',
    tags: ['repository','history','data-access'], complexity:'simple'
  },
  'src/main/java/com/hris/auth/repository/EmployeeRepository.java': {
    summary: 'Spring Data JPA repository for Employee with rich finder methods used across auth, leave, and approval modules.',
    tags: ['repository','employee','data-access','core'], complexity:'moderate'
  },
  'src/main/java/com/hris/auth/repository/EmployeeStatusHistoryRepository.java': {
    summary: 'Spring Data JPA repository providing access to employee status change history.',
    tags: ['repository','history','data-access'], complexity:'simple'
  },
  'src/main/java/com/hris/auth/service/DepartmentService.java': {
    summary: 'Department management service handling CRUD, deactivation, reactivation, deletion guards, and DTO assembly for the HR console.',
    tags: ['service','department','crud','organisation'], complexity:'complex'
  },
  'src/main/java/com/hris/auth/service/EmployeeHistoryService.java': {
    summary: 'Service that records hire events, department transfers, and status changes into the corresponding history tables.',
    tags: ['service','history','audit','employee'], complexity:'moderate'
  },
  'src/main/java/com/hris/auth/service/EmployeeOnboardingService.java': {
    summary: 'Orchestrates new-employee onboarding: persists employee, provisions Keycloak account, assigns default access profile, and dispatches activation email.',
    tags: ['service','onboarding','employee','provisioning'], complexity:'moderate'
  },
};

const nodes = [];
const edges = [];

for (const f of r.results) {
  const m = fileMeta[f.path] || {summary:'Java source file in the HRIS backend.', tags:['java','code'], complexity:'simple'};
  const name = f.path.split('/').pop();
  const node = {
    id: 'file:' + f.path,
    type: 'file',
    name,
    filePath: f.path,
    summary: m.summary,
    tags: m.tags,
    complexity: m.complexity
  };
  if (m.languageNotes) node.languageNotes = m.languageNotes;
  nodes.push(node);
}

const classSummaryMap = {
  'AccessController':'REST controller exposing /access/me and navigation lookup endpoints.',
  'AccessResolutionService':'Central authorization service computing effective permissions, scopes, and navigation per user.',
  'ProfileBootstrapMigration':'Startup component that backfills access profile assignments from existing org data.',
  'HeadcountFact':'JPA entity storing per-department, per-date headcount snapshots.',
  'HeadcountFactRepository':'Repository for HeadcountFact bulk operations.',
  'AnalyticsAggregationService':'Aggregates operational tables into analytics fact tables and snapshots.',
  'TeamHierarchyResolver':'Resolves the upward chain of team leads above a given employee.',
  'EmployeeController':'REST endpoints for employee CRUD, onboarding, and listing.',
  'Department':'JPA entity representing a department with hierarchy and head linkage.',
  'Employee':'Core JPA entity for an HRIS employee.',
  'EmployeeDepartmentHistory':'JPA entity tracking department transfers per employee.',
  'EmployeeStatusHistory':'JPA entity tracking status changes per employee.',
  'DepartmentMapper':'Mapper converting Department entities to DepartmentDto.',
  'EmployeeMapper':'Mapper between Employee entities and DTOs.',
  'DepartmentRepository':'Spring Data repository for Department.',
  'EmployeeDepartmentHistoryRepository':'Spring Data repository for department transfer history.',
  'EmployeeRepository':'Spring Data repository for Employee with custom finders.',
  'EmployeeStatusHistoryRepository':'Spring Data repository for status history.',
  'DepartmentService':'Service implementing department lifecycle operations.',
  'EmployeeHistoryService':'Service that records employee history events.',
  'EmployeeOnboardingService':'Orchestrates onboarding of new employees including Keycloak provisioning.'
};
function classTags(cname) {
  if (/Controller$/.test(cname)) return ['controller','rest','api-handler'];
  if (/Service$/.test(cname)) return ['service','business-logic'];
  if (/Repository$/.test(cname)) return ['repository','data-access','jpa'];
  if (/Mapper$/.test(cname)) return ['mapper','dto-conversion'];
  if (/History$/.test(cname)) return ['entity','history','audit'];
  if (/Fact$/.test(cname)) return ['entity','analytics','fact-table'];
  if (/Resolver$/.test(cname)) return ['service','resolver','workflow'];
  if (/Migration$/.test(cname)) return ['service','migration','bootstrap'];
  return ['entity','jpa','data-model'];
}

for (const f of r.results) {
  for (const c of (f.classes||[])) {
    const span = c.endLine - c.startLine;
    if ((c.methods||[]).length < 2 && span < 20) continue;
    const id = 'class:' + f.path + ':' + c.name;
    const cmplx = span > 200 ? 'complex' : span > 50 ? 'moderate' : 'simple';
    nodes.push({
      id, type:'class', name: c.name, filePath: f.path,
      lineRange:[c.startLine, c.endLine],
      summary: classSummaryMap[c.name] || (c.name + ' class.'),
      tags: classTags(c.name),
      complexity: cmplx
    });
    edges.push({source:'file:'+f.path, target:id, type:'contains', direction:'forward', weight:1.0});
    if ((f.exports||[]).some(e => e.name === c.name)) {
      edges.push({source:'file:'+f.path, target:id, type:'exports', direction:'forward', weight:0.8});
    }
  }
  for (const fn of (f.functions||[])) {
    const span = fn.endLine - fn.startLine;
    if (span < 10) continue;
    const id = 'function:' + f.path + ':' + fn.name;
    nodes.push({
      id, type:'function', name: fn.name, filePath: f.path,
      lineRange:[fn.startLine, fn.endLine],
      summary: 'Method ' + fn.name + ' in ' + f.path.split('/').pop() + '.',
      tags: ['method','java','business-logic'],
      complexity: span > 40 ? 'moderate' : 'simple'
    });
    edges.push({source:'file:'+f.path, target:id, type:'contains', direction:'forward', weight:1.0});
  }
}

for (const f of r.results) {
  const imps = imports[f.path] || [];
  for (const target of imps) {
    edges.push({
      source: 'file:'+f.path,
      target: 'file:'+target,
      type: 'imports',
      direction: 'forward',
      weight: 0.7
    });
  }
}

let totalImps = 0;
for (const k of Object.keys(imports)) totalImps += imports[k].length;
const emittedImps = edges.filter(e => e.type==='imports').length;
console.log('Expected imports:', totalImps, 'Emitted:', emittedImps);
console.log('Nodes:', nodes.length, 'Edges:', edges.length);

const parts = Math.ceil(Math.max(nodes.length/60, edges.length/120));
console.log('parts:', parts);

const sortedFiles = r.results.map(f=>f.path).sort();
const chunkSize = Math.ceil(sortedFiles.length / parts);
const partFiles = [];
for (let i=0; i<parts; i++) {
  partFiles.push(new Set(sortedFiles.slice(i*chunkSize, (i+1)*chunkSize)));
}

function nodeFile(n) { return n.filePath; }
function edgeSourceFile(e) {
  const s = e.source;
  if (s.startsWith('file:')) return s.slice(5);
  if (s.startsWith('function:') || s.startsWith('class:')) {
    const rest = s.slice(s.indexOf(':')+1);
    const lastColon = rest.lastIndexOf(':');
    return rest.slice(0, lastColon);
  }
  return null;
}

for (let i=0; i<parts; i++) {
  const fset = partFiles[i];
  const pNodes = nodes.filter(n => fset.has(nodeFile(n)));
  const pEdges = edges.filter(e => fset.has(edgeSourceFile(e)));
  const out = { nodes: pNodes, edges: pEdges };
  const fname = parts === 1
    ? 'C:/Users/Youssef/.gemini/antigravity/scratch/pfe/backend/hris/.understand-anything/intermediate/batch-1.json'
    : 'C:/Users/Youssef/.gemini/antigravity/scratch/pfe/backend/hris/.understand-anything/intermediate/batch-1-part-' + (i+1) + '.json';
  fs.writeFileSync(fname, JSON.stringify(out, null, 2));
  console.log('Wrote', fname, '- nodes:', pNodes.length, 'edges:', pEdges.length);
}
