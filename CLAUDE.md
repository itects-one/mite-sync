# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test

Maven wrapper (`./mvnw`) is committed; prefer it over a system `mvn`.

- Run the app locally: `./mvnw spring-boot:run` (listens on 8080)
- Build jar: `./mvnw package`
- Full verify (tests + JaCoCo coverage check): `./mvnw verify`
- Single test class: `./mvnw test -Dtest=BookingProposalServiceTest`
- Single test method: `./mvnw test -Dtest=BookingProposalServiceTest#methodName`
- Docker build: `docker build -t mite-sync .` (multi-stage, Maven 3.9 + Temurin 25)
- Docker Compose (uses Docker Hub image by default): `docker compose up` — needs `.env` (see `.env.example`). Port 8888 only needs to be exposed during the first Google OAuth setup.

JaCoCo enforces **≥80% instruction coverage at BUNDLE level** as part of `verify`. `GoogleCalendarService` and `AzureDevOpsService` are excluded (external I/O). When adding code in other classes, expect the coverage gate to fail builds if untested.

Java version is pinned to **25** (`.java-version`, `pom.xml`). Spring Boot **4.1.0**.

Spring Boot 4 changed three things this repo depends on. Keep them in mind when adding code:
- **Jackson 3** lives under `tools.jackson.*` (group id `tools.jackson.core`), not `com.fasterxml.jackson.*`. Annotations stay under `com.fasterxml.jackson.annotation`.
- **`@MockBean`/`@SpyBean` are gone.** Use `@MockitoBean`/`@MockitoSpyBean` from `org.springframework.test.context.bean.override.mockito`.
- **Test slices moved into their own modules** and `spring-boot-starter-test` no longer brings them: `@WebMvcTest` is `org.springframework.boot.webmvc.test.autoconfigure` (artifact `spring-boot-webmvc-test`), `@DataJpaTest` is `org.springframework.boot.data.jpa.test.autoconfigure` (artifact `spring-boot-data-jpa-test`). Adding a new slice means adding its module to `pom.xml`.

## Big-Picture Architecture

The app exposes two independent workflows over the Mite time-tracking API, both as REST endpoints (no UI). On top of them sits a **persistent proposal store** (`/proposals`, package `persistence` + `service.ProposalService` + `web.controller.ProposalController`): an embedded H2 file database (`~/.mite-sync/db/`, `spring.datasource` in `application.yml`; in Docker overridden to `/data/db` via `SPRING_DATASOURCE_URL`) that lets a generated proposal be reviewed/edited/confirmed as an inbox item. It reuses the existing `DailyReportFacade` (`preview` to generate a DRAFT, `book` to confirm) and only adds the state machine (`DRAFT → BOOKED | PARTIALLY_BOOKED | FAILED`; edit/confirm require DRAFT → 409) plus entry provenance: an entry's `source` (values in `web.model.EntrySource`) is **derived server-side** on edit — unchanged entries inherit the stored value, changed or added ones become `manual`, and a client-supplied `source` is ignored so provenance cannot be spoofed. This is stage 1 of a larger vision (web UI + scheduler + in-app AI agent). See `HELP.md` and `mite-sync.http` for example payloads.

### `ProposalGuard` — not wired up yet

`service.ProposalGuard` + `DayEvidence` + `GuardResult` are the deterministic half of issue #16 (agent-composed proposals), landed ahead of the agent itself and **currently without a caller**. Do not delete them as dead code; the agent PR wires them in.

The guard exists because the rule-based composers *cannot* invent a ticket — every note they build comes from a commit subject or a work item that was just read — while a model can, and an entry booking billed time onto untouched work survives review precisely because it reads like the others. So it checks that every `#<ticket>` in a proposed note occurs in the day's evidence or in the profile's configured ids (main PBI, meeting collector, fill-up ticket), plus entry sanity, the duplicate guard against already-booked notes, and the day's total against the target.

Design decisions worth keeping: it reports **all** violations rather than the first; it never repairs anything (the caller decides — intended handling is to fall back to the rule-based proposal and surface the violations through the existing `warnings` channel, never a silent DRAFT); overshooting the target is a violation but **undershooting is not**, because `git-activity` books only what the history shows; and the total check switches off with a negative tolerance, since long days are legitimate on some profiles. A note without a `#` prefix is not checked for a ticket at all — that case belongs to #21, not here.

### Two Mite instances, two directions

There are **two** `MiteClient` beans (`MiteSyncConfig`):
- `sourceMiteClient` → the billing-relevant Mite where hours actually must end up
- `targetMiteClient` → a mirror Mite instance

Hosts and credentials come from env vars (`MITE_SYNC_SOURCE_*` / `MITE_SYNC_TARGET_*`). Never assume which client a service uses without checking. In particular:
- `MiteSyncService` (used by `/sync-jobs`) gets both clients wired by **bean name** and reads from SOURCE / writes to TARGET (delete-then-recreate).
- `MiteBookingService` (used by `/daily-reports/*`) resolves its client at call time via `MiteClientRegistry` from the requested project profile's `mite-instance` key ("source"/"target"). The default profile books into SOURCE; `/sync-jobs` later mirrors to TARGET.

### Workflow 1 — `/sync-jobs` (classic mirror)

`MiteSyncController` → `MiteSyncFacade` → `MiteSyncService`. Idempotent: deletes all TARGET entries in the date range, then recreates from SOURCE. Field translation between the two Mite instances goes through `TimeEntryConverter` (a Spring `Converter<TimeEntries.TimeEntry, TimeEntry>`), which rewrites `project-id` and `service-id` to the TARGET values from `application.yml`. Date format on this endpoint is **`dd.MM.yyyy`** (not ISO).

### Workflow 2 — `/daily-reports/{project}/{date}/preview` and `/book`

Daily reports are **profile-based**: the `{project}` path segment selects a profile from `daily-reports.profiles.*` (`DailyReportProperties` → `ProfileRegistry`). A profile defines the workflow type, the Mite instance + project/service ids, and the booking rules (daily summary/minutes, rounding step, target minutes). Legacy routes without a project segment use `daily-reports.default-profile`; unknown keys → 404 (`GlobalExceptionHandler`). `GET /profiles` (`ProfileController` → `ProfileRegistry.all()`) makes the configured keys discoverable and states per profile whether `mainPbiId` is required — it deliberately omits Mite ids, instance keys and repository paths.

Two workflow types exist (switch in `DailyReportFacade`):
- **`calendar-devops`** — Google Calendar + Azure DevOps + fill-up onto a main PBI. `mainPbiId` is required, but enforced in the facade (`MissingMainPbiException` → 400), NOT via bean validation — git-activity profiles share the same request body and don't use it.
- **`git-activity`** — proposal derived purely from local git history; calendar/DevOps services are not touched (their lazy init stays untriggered). `GitActivityService` (thin JGit I/O, reads all branches of the profile's local repos with author filter, tested against temp repos; returns a `GitActivityResult` of commits **plus warnings** — an unreadable repo is skipped but reported, because an empty proposal otherwise looks the same as a moved path. The warnings travel through `DailyReportModel.warnings` and, for the store path, through `ProposalModel.warnings`, which is deliberately **not persisted**: it describes the generation run, not the proposal) → `GitActivityEstimator` (pure logic; session-based duration heuristic documented in its Javadoc and HELP.md, configured via the profile's `git.*` block; returns a `GitEstimate` of entries **plus warnings** and the facade concatenates both warning lists. Commits matching `git.non-billable-patterns` get no entry but **stay in their session**, so their share is redistributed over the session's other commits — filtering them out in `GitActivityService` instead would let the removal split a session and change the whole day. An entry from the `fallback-ticket` bucket while that key is blank has no `#<ticket>` prefix and is warned about, since it is the one entry a reviewer waves through) → `BookingProposalService.buildGitProposal` (duplicate guard against already-booked notes; opt-in fill-up to the daily target via `git.fill-up-ticket`, default is to book only what the history shows). The preview returns the day's commits in `gitCommits` as evidence.

`DailyReportController` → `DailyReportFacade`, which resolves the profile and (for `calendar-devops`) fans out to four services and composes a proposal:
1. `GoogleCalendarService` — meetings of the day (rounded up to 15-min steps)
2. `AzureDevOpsService` — WIQL queries for "changed by me today" and "open work items assigned to me". Returns a `WorkItemResult` of items **plus warnings**: a failed query must not abort the preview, but the empty list it leaves behind makes the proposal fill the whole day onto the main PBI, so an expired PAT would otherwise pass for a plausible report. The warning text stays a sentence — the response body of a failed call is a sign-in page and belongs in the log. `GoogleCalendarService` deliberately does **not** follow this pattern: it throws, so a broken calendar fails loudly instead of faking an empty day.
3. `MiteBookingService` — already-booked entries for the day (to avoid duplicates)
4. `BookingProposalService` — pure logic that combines all of the above with the user-supplied `mainPbiId` / `targetHours`

The `preview` step is read-only and **safe to re-run**. `book` actually creates entries in SOURCE Mite. Clients are expected to call `preview`, optionally edit the returned `ProposalEntryModel` list, then post it to `book`. Date format on these endpoints is **ISO (`yyyy-MM-dd`)**.

Booking rules (in `BookingProposalService`, rule values come from the profile):
- "Daily" event (configurable summary) is always booked at a fixed duration (default 15 min), regardless of calendar length.
- Other meetings are rounded up to the next 15-min step.
- Meeting entries get note `#<meeting-collector-pbi> <summary>`; remaining hours are filled onto the main PBI: `#<mainPbiId> <title>`.
- `targetHours` from the request body overrides the profile's daily target (default 375 min = 6.25 h). Already-booked minutes count toward the target.
- Duplicate guard: an entry whose note matches an already-booked Mite note (case-insensitive, trimmed) is skipped.
- `skipped` events and `declined` calendar events are filtered out; `needsAction` stays in (user removes manually before `/book`).

### External-system integration notes

- **Google Calendar OAuth**: `GoogleCalendarService` uses **lazy `ensureClient()`**, not `@PostConstruct`. `LocalServerReceiver` would otherwise block startup. First `/preview` call triggers a browser popup; refresh token is persisted to `~/.mite-sync/google-tokens/`. Setup steps live in `HELP.md`. The `skip-summaries` list must stay a **comma-separated string** in `application.yml` and be split via SpEL (`@Value("#{'${...}'.split(',')}")`) — Spring's plain `@Value` does not reliably bind YAML block lists to `List<String>`.
- **Azure DevOps**: Plain JDK `HttpClient` + Jackson — no SDK. Auth = PAT in Basic header (`":" + pat`, base64). When building the WIQL URL, project names with spaces must use **percent-encoding (`%20`)**, not form-encoding (`+`). The code does `URLEncoder.encode(project, UTF_8).replace("+", "%20")` for that reason.
- **mite-java JAXB asymmetry** (`io.seventytwo.oss:mite-java:1.1.0`): read and write types differ.
  - Read side (`TimeEntries.TimeEntry`): `getId().getValue()` → `long`, `getMinutes().getValue()` → `short`, `getNote()` → `Object` (call `.toString()` after null-check).
  - Write side (`TimeEntry` with inner classes): `Minutes.setValue(short)` requires an explicit `(short)` cast; `DateAt.setValue(LocalDate)` takes a `LocalDate`, not a String; `setNote(Object)` takes a plain `String` directly (no `Note` wrapper).
  - This is why `MiteBookingService.buildTimeEntry` and `TimeEntryConverter.convert` look more verbose than seems necessary.

### Configuration & secrets

All runtime config lives in `src/main/resources/application.yml` and is overridden via env vars at runtime (Spring relaxed binding — see `docker-compose.yml` for the mapping `MITE_SYNC_SOURCE_API_KEY` → `mite-sync.source.api-key`, etc.). The committed `application.yml` has **empty/placeholder values**; real secrets live in `.env` (gitignored) and are surfaced to the container via `docker-compose.yml`. When editing config, keep the env-var override path working — `docker-compose.yml` is the contract.

Google OAuth artifacts (`google-client-secret.json`, `google-tokens/`) live under `~/.mite-sync/` on the host and are bind-mounted into the container.

### Web UI (`src/main/frontend`)

A React + Vite SPA for the proposal inbox, served at `/`. `frontend-maven-plugin` downloads Node into `target/` (so Dockerfile and CI stay Node-free), runs `npm ci` and `npm run build` in `generate-resources` plus `npm test` (vitest) in the `test` phase. `-Dfrontend.skip=true` turns the whole frontend build off; `-DskipTests` skips vitest along with surefire, because the plugin's mojo honours `skipTests` for executions bound to `test`. The Docker image build therefore runs no tests at all — that is fine, since the `docker` job `needs: verify`, so nothing is built from a commit whose full suite has not passed. `.dockerignore` keeps the host's `node_modules` out of the build context; without it the image would inherit macOS-native binaries.

Things that are easy to get wrong here:
- **Vite's `outDir` is `src/main/resources/static`, not `target/classes/static`** — deliberately. An IDE compiles the module with its own compiler and replaces `target/classes` without running Maven, which made the UI vanish and `/` answer with a Whitelabel "No static resource" page. From the resource folder every builder copies it along. The directory is generated, gitignored, and wired into `maven-clean-plugin` so `mvn clean` removes it. Only a fresh clone never built with Maven still starts empty.
- Routing is **hash-based** (`#/proposals/{id}`) on purpose — no forwarding controller and no history-API fallback are needed, and deep links survive a reload.
- The UI adds no Java, so it does not move the JaCoCo needle; its own tests run under vitest/jsdom and are wired into `verify` via the plugin. A green Java build alone no longer proves the app works.
- Confirming is disabled while the entry list is dirty: `confirm` books what is **stored**, not what the form shows. Same reason an empty proposal can neither be saved (400 from `@NotEmpty`) nor confirmed (issue #18 would report BOOKED without booking).

### Security

`SecurityConfig` (package `config`) locks **every** endpoint behind HTTP basic auth — there is no public path, not even the OpenAPI UI. Sessions are stateless.

Token-based CSRF protection stays off, but **`RequiredHeaderCsrfFilter` rejects any `POST`/`PUT`/`DELETE` without an `X-Requested-With` header (403)**. Basic credentials are ambient in a browser, so once the UI existed a cross-site `<form method="post">` to `/proposals/{id}/confirm` (no request body, no preflight) would have booked real entries. A form cannot set headers; a script that does triggers a preflight this app never answers. **The guard therefore depends on no permissive CORS ever being configured** — that trade-off is the whole reason a token was not used, since a token would force every non-browser client to fetch one first. The filter sits *after* `AuthorizationFilter` so an unauthenticated write still gets 401 rather than a misleading 403. Controller slice tests import `ScriptedClientDefaults`, which supplies the header for every request; that the guard bites is asserted only in `SecurityConfigTest`.

The single user comes from Spring Boot's `spring.security.user.*` properties (`SPRING_SECURITY_USER_NAME` / `SPRING_SECURITY_USER_PASSWORD`). They are intentionally **absent from the committed `application.yml`**, unlike the other placeholder values: an empty placeholder password would be a weak credential, whereas leaving them unset makes Boot generate a random password and log it — failing safe rather than open.

Two things to know when writing controller tests: `@WebMvcTest` does **not** pick up `SecurityConfig` on its own (it is a plain `@Configuration`), so a slice test needs `@Import(SecurityConfig.class)` or it silently runs against Boot's default chain — where CSRF is on and every POST gets a 403. And the security slice auto-configuration lives in its own module in Boot 4: without the `spring-boot-security-test` dependency, `@WebMvcTest` applies no security filter at all. Authenticate slice tests with `@WithMockUser`.

### Validation & error handling

- Request DTOs use Jakarta validation (`@Valid` on controllers). `ProposalEntryModel.minutes` is bounded to 1..1440 — the upper bound is not cosmetic: `MiteBookingService.buildTimeEntry` casts to `short` for the Mite write API, so anything above 32767 would wrap into a negative duration. The annotation sits on the shared entry model, so both `/daily-reports/{project}/{date}/book` and `PUT /proposals/{id}/entries` are covered through `BookingRequestModel`'s `List<@Valid ProposalEntryModel>`.
- `GlobalExceptionHandler` turns `MethodArgumentNotValidException` into a flat `{field: message}` JSON body.
- Custom date-range validation: `@ValidDateRange` annotation + `DateRangeValidator` (used on `SyncJobModel`).
- `MiteBookingService.book` is **best-effort**: per-entry failures are collected in `BookingResultModel.failed` and don't abort the run.

## Conventions worth knowing

- Package root: `org.twittig.mite.mitesync`. Layering is `web.controller` → `facade` → `service` (+ `converter`, `web.model`, `web.annotation`).
- Tests mirror the main package structure under `src/test/java`. `GoogleCalendarService` and `AzureDevOpsService` are excluded from the JaCoCo gate because they hit live APIs, but they are **not untested**: both have test classes that inject a mocked transport (`ReflectionTestUtils.setField(service, "httpClient", …)`). The exclusion only means their coverage does not count — keep new external-I/O code likewise excluded when you can't reasonably mock it, and prefer to put the testable logic into a sibling pure-logic class (the way `BookingProposalService` is split out of `DailyReportFacade`).
- Javadoc and inline comments in this repo are in **English**. Match the existing style when editing.
- Trunk-based workflow: `main` is the only long-lived branch; work happens on short-lived feature branches merged via PR. CI (`.github/workflows/ci.yml`) runs `./mvnw -B verify` on every PR and push to `main` — the `verify` check is required by branch protection, so a red build blocks the merge. On push to `main`, CI additionally builds the Docker image and pushes `latest` + `sha-<short>` tags to Docker Hub (needs `DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN` repo secrets). Releases are plain git tags (`v*`): the tag adds a versioned image tag and creates a GitHub Release with generated notes. The `pom.xml` version stays at `0.0.1-SNAPSHOT` (the Dockerfile hardcodes that jar name) — the git tag is the source of truth for versions.
