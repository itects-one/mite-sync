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

`GET /profiles` lists what is configured, ordered by key — the keys to put in the `{project}`
segment, each with its workflow type, its daily target, whether it is the default, and whether it
expects a `mainPbiId`:

```json
[
  { "key": "default", "workflowType": "calendar-devops", "requiresMainPbi": true, "targetMinutes": 375, "default": true }
]
```

Mite ids, instance keys and repository paths stay out of that response — they are configuration,
not API surface.

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
  DRAFT (otherwise `409`), and every entry needs `minutes` between 1 and 1440 (otherwise `400`,
  naming the offending row as `entries[0].minutes`). See **Entry provenance** below for what
  happens to `source`.
- `POST /proposals/{id}/confirm` — books the entries into Mite via the existing best-effort
  pipeline and records the outcome as `BOOKED`, `PARTIALLY_BOOKED` or `FAILED`. Only allowed while
  DRAFT, and only with entries: a day without activity generates a valid but empty proposal, and
  confirming that would claim a booking that never happened (`409`). Such a day is a proposal not
  worth confirming — delete it, or regenerate it once the day has activity.
- `DELETE /proposals/{id}` — remove a proposal.

Proposals are persisted in an embedded **H2 file database** at `~/.mite-sync/db/` (in Docker,
`SPRING_DATASOURCE_URL` points at `/data/db`, bind-mounted like the Google tokens). See
[`mite-sync.http`](./mite-sync.http) for ready-to-run examples.

### Entry provenance

Every entry carries a `source` saying where it came from — the difference between "the app derived
this from evidence" and "a human typed this":

| `source` | Meaning |
|---|---|
| `calendar` | meeting taken from the calendar |
| `main-pbi-fill` | fill-up onto the main PBI, up to the daily target |
| `git` | derived from the commit history |
| `git-fill` | fill-up onto the configured git fill-up ticket |
| `manual` | written or changed by hand |

On `PUT /proposals/{id}/entries` the value is **derived by the server and a `source` in the request
body is ignored**. An entry that comes back with unchanged minutes, note and PBI id keeps the
provenance it was generated with — it was not touched. Everything changed or added becomes
`manual`. Editing therefore never silently erases where an entry came from, and a caller cannot
label hand-written work as derived from evidence.

## Web UI

The proposal inbox has a small single-page app at **`/`** (so `http://localhost:8080/` after
`./mvnw spring-boot:run`). It covers the whole review loop:

- **Inbox** — every proposal, newest report date first, with date, profile, status and total.
- **Generate** — pick a profile and date; the form asks for a main PBI only where the profile
  needs one, and takes the daily target from the profile unless you override it. Regenerating
  overwrites an existing `DRAFT` for that day in place, so it never produces duplicates.
- **Detail** — the entries of a proposal, editable while `DRAFT` and read-only afterwards. Each
  entry shows its `source`, so a derived entry stays distinguishable from a hand-written one.
- **Confirm** — books the stored entries and shows what was created and what failed.

Two guards are deliberate. Confirming is blocked while there are unsaved changes, because it books
what is stored and not what is on screen. And a proposal without entries can neither be saved
(the API rejects an empty list with 400) nor confirmed — to get rid of it, delete it.

Authentication is the same HTTP basic auth as for the API: the browser asks once and then sends
the credentials with every request. There is consequently no logout button — closing the browser
is the way out.

The UI is built into the jar, so a deployment needs nothing extra. For UI work with hot reload,
start the app and run `npm run dev` in `src/main/frontend`; it serves the UI on port 5173 and
proxies the API calls to the running app on 8080.

**Where the build output lands.** Vite writes into `src/main/resources/static`, not directly into
`target/classes/static`. That looks roundabout but is deliberate: an IDE compiles the module with
its own compiler, replacing `target/classes` without ever running Maven — the UI would vanish and
`/` would answer with a Whitelabel *"No static resource"* page. From the resource folder every
build copies it along, Maven's and the IDE's alike. The directory is generated, gitignored and
removed by `mvn clean`; never edit or commit it.

One case remains: a fresh clone that has never been built with Maven has no
`src/main/resources/static` yet, so an IDE-only build finds nothing to copy. One `./mvnw
generate-resources` (or any Maven build) fixes it for good.

**Recommended for IntelliJ:** enable *Build, Execution, Deployment → Build Tools → Maven → Runner
→ "Delegate IDE build/run actions to Maven"*. IDE builds then run the real Maven lifecycle
including the frontend build, and the IDE stops shadowing the build tool at all.

## Authentication

**Every endpoint requires HTTP basic authentication**, including the OpenAPI UI. Unauthenticated
requests get `401`. The write paths create real entries in Mite and the proposal store holds
persistent state, so the service must not be reachable without credentials once it runs anywhere
but localhost.

There is a single user, configured through Spring Boot's own properties and overridable by
environment variable like every other secret:

| Variable | Meaning |
|---|---|
| `SPRING_SECURITY_USER_NAME` | user name (default `user`) |
| `SPRING_SECURITY_USER_PASSWORD` | password |

They are deliberately **not** present in the committed `application.yml`: an empty placeholder
password would be a weak credential. If the password is left unset, Spring Boot generates a random
one and logs it at startup — convenient locally, but for anything long-running set both explicitly
in `.env` (see `.env.example`).

Calling an endpoint then looks like this:

```sh
curl -u "$SPRING_SECURITY_USER_NAME:$SPRING_SECURITY_USER_PASSWORD" http://localhost:8080/proposals
```

Sessions are stateless — there is nothing to keep between requests.

### Why writes need an extra header

**`POST`, `PUT` and `DELETE` are rejected with `403` unless the request carries an
`X-Requested-With` header.** The value is irrelevant; only its presence is checked:

```sh
curl -u "$USER:$PASSWORD" -X POST -H "X-Requested-With: curl" \
     http://localhost:8080/proposals/1/confirm
```

The reason is the web UI. In a browser, basic credentials are *ambient*: once entered, the browser
attaches them to every request to this origin — including one a foreign page triggers.
`POST /proposals/{id}/confirm` takes no request body, so without a guard a plain cross-site
`<form method="post">` would be enough to book real entries.

A form cannot set headers, which kills that vector. A script can, but adding a header makes the
request preflighted, and this app answers no CORS preflight — the browser stops it before it is
sent. That is also the catch: **the protection rests on the same-origin boundary, not on a secret**.
Configuring permissive CORS would reopen the hole, and the UI never needs it, being served from
this very origin.

A token-based alternative (`CookieCsrfTokenRepository`) was considered and rejected: it would force
every non-browser client to fetch a token before each write, for no gain as long as no foreign
origin is allowed.

## Example requests

See [`mite-sync.http`](./mite-sync.http) — the IntelliJ HTTP client understands this format
directly. Every request in that file needs an `Authorization` header; the IntelliJ HTTP client
can supply it from an environment file.

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
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.0/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.0/maven-plugin/build-image.html)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/4.1.0/reference/using/devtools.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.1.0/reference/web/servlet.html)

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
