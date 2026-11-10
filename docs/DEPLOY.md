# Deployment

Target topology: **Android (Play/APK) → Render (Node API) → Neon (Postgres) + Neon Object Storage.**

---

## 1. Neon PostgreSQL

1. Create the project (region closest to Render, e.g. `aws-ap-south-1` for India).
2. Copy the **pooled** connection string.
3. Nothing else to create manually — `npm run migrate` builds the schema on first deploy.

Production notes:

* Use the pooled host (`-pooler`) for the API; keep `sslmode=require`.
* The pool is capped at 10 connections by default (`src/database/pool.ts`); raise it only if your
  Neon plan allows.
* Backups/PITR are a Neon plan feature — enable them before real members data lands.

---

## 2. Object storage (profile pictures)

Any S3-compatible bucket works (Neon Object Storage, Cloudflare R2, AWS S3, MinIO).

1. Create a **private** bucket, e.g. `squadgym-media`.
2. Create an access key with read/write on that bucket.
3. Set in Render:

```ini
STORAGE_DRIVER=s3
S3_ENDPOINT=https://<your-s3-endpoint>
S3_REGION=us-east-1
S3_BUCKET=squadgym-media
S3_ACCESS_KEY_ID=…
S3_SECRET_ACCESS_KEY=…
S3_FORCE_PATH_STYLE=true
```

Do **not** make the bucket public. Images are streamed by the API through `GET /api/v1/media/...`,
which requires a session and returns immutable cache headers keyed by `?avatar=vN`.

If you later put a CDN in front of the bucket, set `MEDIA_PUBLIC_BASE_URL` and make sure the CDN
enforces signed URLs — a public bucket exposes members' photos.

---

## 3. Render

### Create the service

* **New → Web Service**, connect the repository.
* Root directory: `backend`
* Runtime: Node 22
* Build command:

  ```bash
  npm ci --include=dev && npm run build
  ```

  `--include=dev` matters: `NODE_ENV=production` is visible during the build, and plain `npm ci`
  would then skip TypeScript, so `npm run build` would fail with `tsc: not found`.

* Start command:

  ```bash
  npm start
  ```

* Health check path: `/health`

`npm start` runs `node dist/server.js`, which **applies pending migrations on boot** (guarded by a
transaction-scoped advisory lock, so concurrent instances are safe even through Neon's pooled
endpoint). Set `SKIP_MIGRATIONS=true` if you prefer a one-off job:

```bash
npm run migrate     # from a Render shell / job, root directory backend
npm run bootstrap   # first deployment only
```

### Environment variables

```
NODE_ENV=production
API_BASE_URL=https://squadgym-api.onrender.com
CORS_ORIGINS=*

DATABASE_URL=postgresql://…neon.tech/neondb?sslmode=require

JWT_ACCESS_SECRET=<48+ random bytes hex>
JWT_REFRESH_SECRET=<48+ random bytes hex>
ACCESS_TOKEN_TTL_SECONDS=900
REFRESH_TOKEN_TTL_SECONDS=2592000
BCRYPT_ROUNDS=12

SUPER_ADMIN_EMAIL=owner@yourgym.com
SUPER_ADMIN_PASSWORD=<set once, then rotate>
SUPER_ADMIN_NAME=Gym Owner
GYM_NAME=Your Gym
GYM_TIMEZONE=Asia/Kolkata

STORAGE_DRIVER=s3
S3_ENDPOINT=…
S3_REGION=…
S3_BUCKET=…
S3_ACCESS_KEY_ID=…
S3_SECRET_ACCESS_KEY=…
S3_FORCE_PATH_STYLE=true

SMTP_HOST=…
SMTP_PORT=587
SMTP_SECURE=false
SMTP_USER=…
SMTP_PASS=…
MAIL_FROM="Your Gym <no-reply@yourgym.com>"

EXPOSE_DEV_TOKENS=false
```

`NODE_ENV=production` **refuses to boot** if secrets are placeholders, `EXPOSE_DEV_TOKENS=true`, or
`S3_BUCKET` is missing while `STORAGE_DRIVER=s3`.

### First deploy

`npm start` applies migrations but **not** the bootstrap step, so the gym and Super Admin must be
created once:

```bash
# Render Shell (root: backend) — paid instances only
npm run bootstrap     # prints the Super Admin email
```

On the free plan there is no Shell. Run it from your machine against the production database
instead — `DATABASE_URL` and the `SUPER_ADMIN_*` / `GYM_*` values must match the Render service:

```bash
cd backend
DATABASE_URL='postgresql://…-pooler.…neon.tech/neondb?sslmode=require' \
NODE_ENV=production \
SUPER_ADMIN_EMAIL=owner@yourgym.com \
SUPER_ADMIN_PASSWORD='…' \
SUPER_ADMIN_NAME='Gym Owner' \
GYM_NAME='Your Gym' \
GYM_TIMEZONE=Asia/Kolkata \
npm run bootstrap
```

`npm run seed` is development-only and refuses to run with `NODE_ENV=production` — never run it
against production.

Then sign in with `SUPER_ADMIN_EMAIL` / `SUPER_ADMIN_PASSWORD`, change the password through
*Forgot password*, and create the gym's Admins.

### Free-tier behaviour

Render free instances sleep after inactivity. Cold starts take ~30 s; the Android app shows its
offline state (cached data) rather than failing. A paid instance avoids this.

---

## 4. Android release

### Point at production

```bash
./gradlew :app:assembleRelease -Psquadgym.apiBaseUrl=https://squadgym-api.onrender.com/
```

or set `squadgym.apiBaseUrl` in `gradle.properties`. HTTPS is required — cleartext is allowed only
for the local emulator hosts.

### Signing

Create a keystore and add it as `signingConfigs.release` (not committed):

```bash
keytool -genkey -v -keystore squadgym-release.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias squadgym
```

Then build an App Bundle for Play:

```bash
./gradlew :app:bundleRelease -Psquadgym.apiBaseUrl=https://squadgym-api.onrender.com/
```

### Versioning

`versionCode` / `versionName` live in `app/build.gradle.kts`. Bump both for every store upload.

### Release checklist

- [ ] `NODE_ENV=production` with real secrets, `EXPOSE_DEV_TOKENS=false`
- [ ] Neon PITR/backups enabled, pooled connection string in use
- [ ] Private storage bucket + least-privilege keys
- [ ] SMTP configured so invitations and password resets actually arrive
- [ ] `API_BASE_URL` is the public HTTPS URL (media URLs depend on it)
- [ ] `GYM_TIMEZONE` matches the gym (this drives attendance days, streaks and leaderboards)
- [ ] Super Admin password rotated after first sign-in
- [ ] Android release build signed; `adb install` tested against production
- [ ] `/ready` returns 200 after deploy

---

## 5. Operational notes

| Area | Behaviour |
|---|---|
| Migrations | Applied automatically on boot in one transaction, serialised with `pg_advisory_xact_lock` (works with Neon's transaction-mode pooler); checksums prevent editing applied files. |
| Occupancy snapshots | A scheduler captures `In Gym Now` every 5 minutes (configurable) and on dashboard reads; these snapshots power the "vs yesterday" comparison. |
| Session expiry | Evaluated at read time (`expires_at > now()`), so "In Gym Now" is correct even if the cleanup job hasn't run. |
| Audit log | Append-only; no API deletes rows. Retention is a database concern (add a Neon scheduled job if required). |
| Rate limits | 25 auth requests / 15 min / IP; 600 API requests / min / IP. Adjust in `src/middleware/rateLimit.ts`. |
| Logs | One JSON line per request with `requestId`, status and duration; secrets are redacted. |
| Scaling | The API is stateless apart from the database; run multiple instances behind Render if needed. The snapshot job is idempotent per bucket. |

---

## 6. Security checklist

* TLS everywhere (Render + Neon enforce it; the app blocks cleartext except loopback).
* Passwords hashed with bcrypt (`BCRYPT_ROUNDS=12`); no plaintext anywhere.
* Refresh tokens stored as SHA-256 hashes, single-use, rotated, revoked on logout/password reset.
* Access tokens are 15-minute JWTs; role/permission changes apply on the next request.
* Every endpoint validates input with Zod and enforces role **and** permission server-side.
* Cross-gym access returns 404 — a member/admin can never read another gym's data.
* Photos live in a private bucket; media URLs are opaque and versioned.
* The Android client holds no secrets: only a base URL and Keystore-encrypted session tokens.
