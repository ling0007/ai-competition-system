# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
always use Chinese for all communication.
## Build & Run

### Backend (Spring Boot)

```bash
# Build and run tests (requires Java 21 — set JAVA_HOME if the system default is Java 8)
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.8.9-hotspot"
./mvnw clean package

# Skip tests
./mvnw clean package -DskipTests

# Run the app (defaults to port 8080, DB at localhost:3306/ai_competition_db)
./mvnw spring-boot:run

# Run tests only
./mvnw test
```

### Frontend (Vue 3 + Vite)

The frontend is at `E:\aicompetition\` (parent of this backend repo).

```bash
cd E:\aicompetition

# Dev server (defaults to port 5173, proxies /api but uses direct localhost:8080 via VITE_API_BASE_URL)
npm run dev

# Production build
npm run build
```

The project uses the Maven wrapper (`mvnw` / `mvnw.cmd` on Windows). Database credentials default to `root:root` and can be overridden via `DB_USERNAME` / `DB_PASSWORD` environment variables.


## Architecture

This is an **AI Competition Material Management System** — a Spring Boot 3.5.13 monolith (Java 21) that helps student teams manage competition project submissions with AI-assisted checking.

### Technology Stack

**Backend** (this directory):
- **Spring Boot 3.5.13** (spring-boot-starter-web, spring-boot-starter-validation)
- **MyBatis-Plus 3.5.7** — all DB access through `BaseMapper`-extending interfaces; `@TableLogic` logical delete on every entity
- **MySQL + Flyway** — schema and seed data in `src/main/resources/db/migration/` (V1 schema, V2 views/triggers/procedures, V3 demo data)
- **Apache Tika 3.1.0** — text extraction from DOCX/PDF/XLSX files for LLM consumption
- **Lombok** — `@Data` on entities and DTOs; constructor injection on services/controllers (no `@Autowired`)

**Frontend** (parent directory: `E:\aicompetition\`):
- **Vue 3.5** (Composition API with `<script setup>`)
- **Vite 8** + **Element Plus 2.13** (UI components: el-select, el-date-picker, el-descriptions, etc.)
- **Axios** with a 60-second timeout; mock API usable via `VITE_USE_MOCK=true` in `.env`
- **SCSS** for component-scoped styles

### Package Layout (standard layered architecture)

```
com.eliza.aicompetition
├── AicompetitionApplication.java   # @SpringBootApplication + @MapperScan
├── config/         # LlmProperties (@ConfigurationProperties), LlmConfig (RestTemplate), WebMvcConfig (CORS)
├── controller/     # REST controllers (Notice, Project, Material, Agent, Dashboard)
├── service/        # Business logic (NoticeService, ProjectService, MaterialService, AgentService) + AiService (LLM client)
├── mapper/         # MyBatis-Plus mapper interfaces (extend BaseMapper<T>)
├── entity/         # DB entities (10 tables, all with is_deleted logical delete)
├── dto/            # Request/response DTOs (per-feature sub-packages: notice, project, material, agent, ai)
├── common/         # ApiResponse<T> record + FileTextExtractor (Tika) + PdfOcrExtractor (multimodal LLM fallback)
└── exception/      # BusinessException (RuntimeException) + GlobalExceptionHandler (@RestControllerAdvice)
```

Custom SQL queries live in `src/main/resources/mapper/*.xml` (currently ProjectMemberMapper, ProjectMaterialMapper, ReviewRecordMapper).

### Domain Model

The system revolves around a **Notice → Project → Material** workflow:

1. `CompetitionNotice` — a competition announcement uploaded by admin/teacher, with AI-parsed summary (`aiSummary`)
2. `MaterialRequirement` — each notice specifies required materials (e.g., "Project Application", "Team Member Sheet")
3. `CompetitionProject` — a team's submission against a notice, tracking `status` (draft/incomplete/ready) and `completionRate`
4. `ProjectMaterial` — each required material per project, with **version tracking** (`versionNo`). Status: pending/submitted/rejected
5. `ProjectMember` — users assigned to a project with role: leader/member/advisor
6. `ReviewRecord` — AI or teacher review results (pass/reject/warning)
7. `NotifyMessage` — system messages to users (e.g., missing materials reminder)
8. `AgentTaskLog` — audit log of AI agent tool invocations
9. `SysUser` — users with role: student/teacher/admin
10. `FileAsset` — file storage. Currently stores `fileBlob` (LONGBLOB) directly; `storagePath` exists for future MinIO migration

### Database Design Notes

- **Triggers** (`V2__create_views_triggers_procedures.sql`): `project_material` INSERT/UPDATE triggers auto-call `sp_refresh_project_progress` to recalculate completion rate and status.
- **Views**: `v_project_progress`, `v_notice_material_summary`, `v_project_material_detail` — used for reporting but not directly accessed from Java (Java rebuilds the same logic in services/mapper XML).
- **Bootstrap**: `00_create_database.sql` creates the database; Flyway manages schema + seed data from there.
- **Verification**: `V4__verification_queries.sql` contains manual SQL verification queries (not run by Flyway).

### API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/dashboard/bootstrap` | Aggregated bootstrap data (notices, users, latest project, AI review) |
| POST | `/notice/upload` | Upload a notice (multipart file + metadata) |
| POST | `/notice/parse/{noticeId}` | Trigger AI parsing of a notice |
| POST | `/project/create` | Create a project (validated request body) |
| GET | `/project/detail/{projectId}` | Full project detail with members, materials, reviews |
| GET | `/project/progress/{projectId}` | Progress summary (completion rate, missing materials) |
| POST | `/material/upload` | Upload a material file for a project requirement |
| POST | `/agent/check-material/{projectId}` | Trigger AI material completeness check |

### LLM Integration

AI features are powered by **DashScope** (Alibaba Cloud) via its OpenAI-compatible API:

- **Base URL**: `https://dashscope.aliyuncs.com/compatible-mode/v1`
- **Model**: `qwen-turbo`
- **API Key**: From `DASHSCOPE_API_KEY` environment variable
- **Config**: `llm.api.*` properties in `application.properties`, bound to `LlmProperties` record
- **HTTP client**: `RestTemplate` bean configured in `LlmConfig` (30s connect, 120s read timeout)

Two AI tools exist:

| Tool | Trigger | Service Method | Behavior |
|------|---------|---------------|----------|
| `parseNoticeTool` | `POST /notice/parse/{noticeId}` | `AiService.parseNotice()` | Sends notice text → LLM returns structured JSON with organizer, deadline, target group, key points, and material requirements |
| `checkMaterialTool` | `POST /agent/check-material/{projectId}` | `AiService.checkMaterial()` | Extracts text from uploaded DOCX/PDF/XLSX files via Tika → sends to LLM with project context → returns pass/warning/reject + detailed Chinese review |

**Error handling**: If the LLM API is unreachable or `DASHSCOPE_API_KEY` is unset, both tools return degraded fallback results (Chinese message about AI unavailability). Transactions always complete — AI failures never throw exceptions. A `@PostConstruct` check logs a warning at startup if the API key is missing.

**Key files**:
- `AiService.java` — LLM client with prompt builders, JSON parsing (`cleanJson()` strips markdown fences), and fallback logic
- `FileTextExtractor.java` — Tika-based text extraction from binary files (100K char limit, never throws). On PDF failure, delegates to PdfOcrExtractor.
- `PdfOcrExtractor.java` — renders PDF pages as images (PDFBox, 150 DPI, max 3 pages) → base64 → sends to `qwen-vl-max` multimodal model for OCR. Handles scanned/image PDFs that Tika cannot extract.
- `LlmProperties.java` — `@ConfigurationProperties(prefix = "llm.api")` record
- `LlmConfig.java` — provides `RestTemplate` bean with timeouts
- `dto/ai/AiParseResult.java` — structured notice parse result (organizer, deadline, materials list)
- `dto/ai/AiCheckResult.java` — structured check result (reviewResult, reviewComment)
- `dto/notice/NoticeParseResponse.java` — parse response including all LLM-extracted fields (organizer, deadline, targetGroup, aiSummary, materialRequirements)

**PDF text extraction pipeline**: `FileTextExtractor.extractText()` → Tika → if PDF and Tika returned error string → `PdfOcrExtractor.ocrPdf()` → renders pages to PNGs → calls `qwen-vl-max` vision model. Both stages are fail-safe (never throw).

### Frontend Architecture

The Vue 3 frontend lives at `E:\aicompetition\` (parent of the backend repo). Key architectural points:

- **API client** (`src/api/client.js`): Axios instance with base URL from `VITE_API_BASE_URL` (default `http://localhost:8080`). Mock mode controlled by `VITE_USE_MOCK=true` in `.env` — every API function in `competition.js` has a mock/real branch.
- **Dashboard bootstrap**: `GET /dashboard/bootstrap` loads all reference data in one request (notices, users, latest project, AI review). The `App.vue` calls this on mount and passes data down to child panels as props.
- **Four dashboard panels**: `NoticeUploadPanel`, `ProjectCreatePanel`, `ProgressOverviewPanel`, `MaterialCheckPanel` — each wrapped in a shared `FeaturePanel` shell.
- **State management**: No Vuex/Pinia — parent `App.vue` holds the single source of truth (notice, project, progress, aiCheck) and passes them as props. Panels emit events upward (`@upload`, `@parse`, `@create`, `@upload-material`, `@check-material`).
- **Shared utilities**: `src/utils/format.js` (date formatting, `toIsoLocalDateTime`), `src/utils/status.js` (status → tag color/label mapping).
- **Mock service**: `src/mock/competitionService.js` — full in-memory mock that simulates all API responses with delays, used when `VITE_USE_MOCK=true`.

### Key Patterns

- **Response envelope**: Every controller returns `ApiResponse<T>` with `code`, `message`, `data`, `timestamp`. Success = code 200, business errors = 400, system errors = 500.
- **Error handling**: `BusinessException` for expected errors; `GlobalExceptionHandler` catches validation errors, missing params, and unhandled exceptions uniformly.
- **Service cross-references**: `ProjectService` depends on `NoticeService`; `MaterialService` depends on both `ProjectService` and `NoticeService`. Circular injection is avoided since `NoticeService` doesn't depend on higher-level services.
- **Transaction boundaries**: `@Transactional` on service methods that write to multiple tables (upload, parse, create project, upload material, check material).
- **Progress recalculation**: Java service methods call `refreshProjectProgress()` explicitly; DB triggers provide a safety net for direct SQL operations.
