# Collide Control Plane

Java/Spring Boot service for **auth, rooms, roles, and invites** — the Control Plane of
the Collide stack. It issues the JWTs that the Node sync server and the React frontend
both consume. Full design + diagrams: [docs/AUTH.md](docs/AUTH.md).

## Run locally

Needs Java 17+ and a Postgres. Fastest path (from `collab/collab/`):

```bash
docker compose up --build          # postgres + redis + control on :8080
```

Or run the service directly against a local Postgres:

```bash
# start just the database
docker compose up -d postgres
# from collab/collab/control
./gradlew bootRun
```

Flyway applies `V1__init.sql` + `V2__auth.sql` on startup. Health: `GET /actuator/health`.
API reference: `GET /openapi.yaml` (or [src/main/resources/static/openapi.yaml](src/main/resources/static/openapi.yaml)).

## Auth endpoints

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/api/auth/signup` | public | Email + password signup |
| POST | `/api/auth/login` | public | Email + password login |
| POST | `/api/auth/google` | public | Continue with Google (ID token) |
| POST | `/api/auth/refresh` | public | Rotate refresh → new access token |
| POST | `/api/auth/logout` | public | Revoke one refresh token |
| POST | `/api/auth/logout-all` | bearer | Revoke all sessions |
| GET | `/api/auth/me` | bearer | Current user |
| PATCH | `/api/auth/change-password` | bearer | Change password |
| PATCH | `/api/auth/update-profile` | bearer | Update name/avatar |
| DELETE | `/api/auth/delete-account` | bearer | Soft-delete account |

## Tests

```bash
./gradlew test
```

Unit tests (JWT, password policy, refresh rotation/reuse, auth flows) run anywhere.
Integration tests use Testcontainers and **skip automatically when Docker is unavailable**.

## Configuration

Everything is env-overridable — see the table in [docs/AUTH.md](docs/AUTH.md#7-environment-variables).
The one hard rule: `JWT_SECRET` must be **byte-identical** to the sync server's
`JWT_SECRET`, or tokens minted here won't authenticate the collaboration WebSocket.
