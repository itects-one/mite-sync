# mite-sync

Spring Boot service around the [mite](https://mite.de/) time-tracking API. It exposes two
independent REST workflows and a persistent review inbox on top of them:

1. **`POST /sync-jobs`** — copies time entries from a source Mite instance to a target Mite
   instance (delete-then-recreate for a given date range). Useful for mirroring a
   billing-relevant Mite into a private mirror Mite.
2. **`POST /daily-reports/{project}/{date}/preview`** and **`/book`** — builds a daily booking
   proposal and, after manual review, books it into Mite. The `{project}` segment selects a
   profile that decides where the proposal comes from and which Mite instance it lands in:
   `calendar-devops` combines Google Calendar events with Azure DevOps work items,
   `git-activity` derives the day's work from local git history. Routes without the segment use
   the default profile.
3. **`/proposals`** — the review inbox. A generated proposal is stored as a `DRAFT`, can be
   listed, edited and finally confirmed, which books it and records the outcome
   (`DRAFT → BOOKED | PARTIALLY_BOOKED | FAILED`). Editing and confirming are only allowed
   while the proposal is a `DRAFT`. Unlike the stateless preview/book pair it survives
   restarts — the groundwork for a scheduler and a web UI.

A small **web UI** at `/` puts a face on the inbox: generate a proposal for a profile and date,
review and correct the entries, confirm or delete — no HTTP client needed. It is a React SPA built
into the jar, so there is nothing extra to deploy.

[HELP.md](./HELP.md) describes each of them in detail.

## Stack

- Java 25, Spring Boot 4.1
- Web UI: React + Vite, built into the jar by `frontend-maven-plugin` (sources in
  `src/main/frontend`)
- Mite client: [`io.seventytwo.oss:mite-java`](https://github.com/72services/mite-java)
- Google Calendar API (OAuth2)
- Azure DevOps REST API (PAT)
- Local git history via JGit (`git-activity` workflow)
- Proposal store: embedded H2 file database under `~/.mite-sync/db/`

## Setup

1. **Configuration:** copy `.env.example` to `.env` and fill in the values (Mite hosts and API
   keys, Azure DevOps PAT, Google calendar id). `.env` is gitignored.
2. **Google OAuth:** see [HELP.md](./HELP.md) for the one-time Google Cloud Console setup and
   where to place `client_secret.json`.
3. **Run:**
   - Locally: `./mvnw spring-boot:run`
   - Docker Compose: `docker compose up` (only expose port 8888 on the first OAuth login)

Example requests live in [`mite-sync.http`](./mite-sync.http) (IntelliJ HTTP client format).

## Build & test

```sh
./mvnw verify                  # backend + frontend tests, JaCoCo coverage gate (>= 80%)
./mvnw spring-boot:run         # local start on :8080, UI included
./mvnw verify -Dfrontend.skip=true   # backend only, no Node download
docker build -t mite-sync .    # container image
```

Node is downloaded into `target/` by the build, so neither Docker nor CI needs a Node toolchain.
For UI work with hot reload, run the app and then `npm run dev` in `src/main/frontend` — it serves
the UI on :5173 and proxies the API to :8080.

## License

[MIT](./LICENSE) — Thomas Wittig, 2026
