# Daily-Reports setup

The app exposes two workflows:

1. **Classic sync job** (`POST /sync-jobs`): copies time entries from source to target Mite.
2. **Daily-Reports** (`POST /daily-reports/{project}/{date}/preview` and `/book`): builds daily
   booking proposals and books them into the Mite instance configured for the project.

Daily-Reports needs a one-time Google Calendar OAuth2 setup.

## Project profiles

The `{project}` path segment selects a profile from `daily-reports.profiles.*` in
`application.yml`. A profile defines:

- the **workflow type**: `calendar-devops` (Google Calendar + Azure DevOps + fill-up onto a main
  PBI) or `git-activity` (proposal derived from local git history)
- the **Mite instance** (`source` or `target`) that already-booked entries are read from and new
  entries are booked into, plus the Mite `project-id`/`service-id`
- the **rules**: daily-event summary and fixed minutes, rounding step, daily target minutes

The legacy routes without a project segment (`/daily-reports/{date}/preview`) use the profile
named by `daily-reports.default-profile`. Unknown project keys return 404.

### Git activity estimation

For `git-activity` profiles the proposal is derived from the commit history of locally
checked-out repositories (`git.repositories`, all branches, optional `git.author` filter).
Merge commits are not counted as work — their time already sits in the feature commits they
bring in, and skipping them keeps branch-slug subjects out of the booking notes. The duration
heuristic:

1. The day's commits are sorted and grouped into **sessions** — a gap larger than
   `session-gap-minutes` (default 90) starts a new session.
2. A session lasts from its first to its last commit plus `lead-in-minutes` (default 30) for
   the work before the first commit; a single-commit session counts `lead-in-minutes`.
3. The session duration is split across tickets proportionally to their commit counts. The
   ticket id comes from `ticket-pattern` (default `^([A-Z]+-\d+)`) matched against the commit
   subject; unmatched commits fall into the `fallback-ticket` bucket.
4. Per-ticket totals are rounded **up** to the profile's rounding step; the proposal note is
   `#<ticket> <subject of the ticket's latest commit>`.

The result is an estimate to be reviewed in the preview — not a time-tracking measurement.
The preview response lists the day's commits (`gitCommits`) as the evidence behind the
estimate, and the duplicate guard drops entries whose note is already booked in Mite, so
re-running preview after book proposes nothing new.

Request-body notes for git-activity profiles: `mainPbiId` is not used (it is only required for
calendar-devops profiles); `targetHours` only matters when `git.fill-up-ticket` is configured —
then the gap between the daily target and (booked + estimated) minutes is added as one entry on
that ticket. By default only what the history shows is proposed.

## Google Cloud OAuth2 setup

1. **Create a Google Cloud project** (or reuse an existing one):
   <https://console.cloud.google.com/projectcreate>

2. **Enable the Calendar API:**
   <https://console.cloud.google.com/apis/library/calendar-json.googleapis.com>
   → click "Activate".

3. **Configure the OAuth consent screen:**
   <https://console.cloud.google.com/apis/credentials/consent>
   - User type: **External**
   - App name: `mite-sync` (any name)
   - User support email: your address
   - Scopes: add `.../auth/calendar.readonly`
   - Test users: add your own Google address (otherwise the app cannot access the account)

4. **Create the OAuth client:**
   <https://console.cloud.google.com/apis/credentials>
   - "Create credentials" → "OAuth client ID"
   - Application type: **Desktop app**
   - Name: `mite-sync-desktop`
   - "Create" → download the JSON

5. **Place the JSON:**
   ```sh
   mkdir -p ~/.mite-sync
   mv ~/Downloads/client_secret_*.json ~/.mite-sync/google-client-secret.json
   ```

6. **Start the app for the first time** (`mvn spring-boot:run`).
   The first call to `/daily-reports/{date}/preview` opens a browser window for authorization.
   Sign in with the configured Google account and grant consent — tokens are persisted under
   `~/.mite-sync/google-tokens/`. Subsequent calls run without interaction.

## Azure DevOps PAT

The PAT is injected via the environment variable `DAILY_REPORTS_AZURE_DEVOPS_PAT` (see
`.env.example`). It only needs the **Work Items: Read** scope.

## Proposal store (review inbox)

Beyond the stateless `preview`/`book` pair there is a **persistent proposal store** under
`/proposals`. It lets a generated proposal sit in an inbox until it is reviewed, optionally edited,
and confirmed — the foundation for a later scheduler + web UI.

- `POST /proposals/{project}/{date}` — generate (or regenerate) the **DRAFT** proposal for a day.
  Reuses the existing preview pipeline (same body as `preview`: `mainPbiId` / `targetHours`). An
  existing DRAFT for the same day is overwritten in place.
- `GET /proposals` — list the inbox (newest report date first). `GET /proposals/{id}` — one item.
- `PUT /proposals/{id}/entries` — replace the entries of a DRAFT (manual edit). Only allowed while
  DRAFT (otherwise `409`).
- `POST /proposals/{id}/confirm` — books the entries into Mite via the existing best-effort
  pipeline and records the outcome as `BOOKED`, `PARTIALLY_BOOKED` or `FAILED`. Only allowed while
  DRAFT.
- `DELETE /proposals/{id}` — remove a proposal.

Proposals are persisted in an embedded **H2 file database** at `~/.mite-sync/db/` (in Docker,
`SPRING_DATASOURCE_URL` points at `/data/db`, bind-mounted like the Google tokens). See
[`mite-sync.http`](./mite-sync.http) for ready-to-run examples.

## Example requests

See [`mite-sync.http`](./mite-sync.http) — the IntelliJ HTTP client understands this format
directly.

## Architecture overview

```
                     ┌──────────────────────┐
                     │   /sync-jobs         │  (existing)
                     │   Source → Target    │
                     └──────────────────────┘
                                ▲
       ┌─────────────────┐      │      ┌─────────────────────┐
       │ Google Calendar │──┐   │      │  /daily-reports     │
       │   (OAuth2)      │  │   │      │     /preview        │
       └─────────────────┘  │   │      │     /book           │
                            ▼   │      └─────────────────────┘
       ┌─────────────────┐ Build      ┌─────────────────────┐
       │ Azure DevOps    │─Proposal──→│  Source Mite        │
       │   (PAT)         │            │                     │
       └─────────────────┘            └─────────────────────┘

       ┌──────────────────────────────────────────────────┐
       │  /proposals  (review inbox, H2 file db)           │
       │  generate → review/edit → confirm → book to Mite  │
       └──────────────────────────────────────────────────┘
```

---

# Getting Started

### Reference Documentation

For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/3.3.5/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/3.3.5/maven-plugin/build-image.html)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/3.3.5/reference/using/devtools.html)
* [Spring Web](https://docs.spring.io/spring-boot/3.3.5/reference/web/servlet.html)

### Guides

The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the
parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.
