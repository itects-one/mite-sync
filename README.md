# mite-sync

Spring Boot service around the [mite](https://mite.de/) time-tracking API. Exposes two
independent REST workflows:

1. **`POST /sync-jobs`** — copies time entries from a source Mite instance to a target Mite
   instance (delete-then-recreate for a given date range). Useful for mirroring a
   billing-relevant Mite into a private mirror Mite.
2. **`POST /daily-reports/{date}/preview`** and **`/book`** — combines Google Calendar events
   and Azure DevOps work items into a daily booking proposal and, after manual review, books it
   into the source Mite.

## Stack

- Java 25, Spring Boot 4.1
- Mite client: [`io.seventytwo.oss:mite-java`](https://github.com/72services/mite-java)
- Google Calendar API (OAuth2)
- Azure DevOps REST API (PAT)

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
./mvnw verify              # tests + JaCoCo coverage gate (>= 80%)
./mvnw spring-boot:run     # local start on :8080
docker build -t mite-sync . # container image
```

## License

[MIT](./LICENSE) — Thomas Wittig, 2026
