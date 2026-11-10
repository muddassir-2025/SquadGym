# Setup

Everything needed to run SquadGym locally and point the Android app at it.

---

## 1. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Node.js | 20+ (22 recommended) | `node --version` |
| PostgreSQL | 14+ | Neon recommended; a local Postgres works too |
| JDK | 17/21 | Android Studio's bundled JBR works |
| Android SDK | compileSdk 37 | Platforms 37.0/37.1 installed by Android Studio |
| Android Studio | recent | Ladybug+ recommended |

No Docker required. No `.env` file is committed — you create it.

---

## 2. Create the Neon database

1. Create a project at <https://console.neon.tech>.
2. **Dashboard → Connect** → copy the **pooled** connection string:
   `postgresql://USER:PASSWORD@ep-xxxx-pooler.REGION.aws.neon.tech/neondb?sslmode=require`
3. Keep `sslmode=require`. The API enables TLS automatically for `neon.tech` hosts.

The migrations need the `pgcrypto` and `citext` extensions — both are available on Neon.

---

## 3. Backend

```bash
cd backend
cp .env.example .env
```

Edit `.env` and set at minimum:

```ini
DATABASE_URL=postgresql://…            # from Neon
JWT_ACCESS_SECRET=<64+ random chars>
JWT_REFRESH_SECRET=<64+ random chars>
SUPER_ADMIN_EMAIL=you@yourgym.com
SUPER_ADMIN_PASSWORD=<strong password>
SUPER_ADMIN_NAME=Gym Owner
GYM_NAME=Your Gym
GYM_TIMEZONE=Asia/Kolkata
```

Generate secrets:

```bash
node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"
```

Install, migrate, bootstrap:

```bash
npm install
npm run migrate      # applies 001…005, tracked in schema_migrations
npm run bootstrap    # gym + schedule + shifts + plans + the single Super Admin
npm run dev          # http://localhost:8080
```

Health checks:

```bash
curl http://localhost:8080/health     # {"status":"ok"}
curl http://localhost:8080/ready      # verifies the database
```

### Optional: demo data

```bash
npm run seed
```

Creates one Admin, twelve members (one expired, one archived, two expiring soon), 28 days of
attendance history, active gym sessions and a yesterday occupancy snapshot. **Development only** —
the seed refuses to run with `NODE_ENV=production`.

### Try the API

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@squadgym.app","password":"Admin@12345"}'
```

Run the full 85-check flow:

```bash
npm run smoke
```

---

## 4. Environment variables

All variables live in `backend/.env` (copy of `backend/.env.example`).

### Runtime

| Variable | Default | Purpose |
|---|---|---|
| `NODE_ENV` | `development` | `production` enables strict checks (secrets, dev-token exposure). |
| `PORT` | `8080` | HTTP port. Render injects its own. |
| `API_BASE_URL` | `http://localhost:8080` | Public base URL, used to build absolute media URLs. |
| `CORS_ORIGINS` | `*` | Comma-separated allowed origins (`*` for dev). |

### Database

| Variable | Required | Purpose |
|---|---|---|
| `DATABASE_URL` | ✅ | Neon pooled connection string with `sslmode=require`. |

### Authentication

| Variable | Default | Purpose |
|---|---|---|
| `JWT_ACCESS_SECRET` | dev placeholder | Signs 15-minute access tokens. **Must** be ≥32 chars; production refuses the placeholder. |
| `JWT_REFRESH_SECRET` | dev placeholder | Reserved for refresh-token signing/rotation. |
| `ACCESS_TOKEN_TTL_SECONDS` | `900` | Access-token lifetime. |
| `REFRESH_TOKEN_TTL_SECONDS` | `2592000` | Refresh lifetime (30 days), rotated on every use. |
| `BCRYPT_ROUNDS` | `12` | Password hashing cost. |

### First-run bootstrap

| Variable | Default | Purpose |
|---|---|---|
| `SUPER_ADMIN_EMAIL` | `owner@squadgym.app` | The only Super Admin. No public path creates one. |
| `SUPER_ADMIN_PASSWORD` | placeholder | Used **only** when the account is first created. |
| `SUPER_ADMIN_NAME` | `Gym Owner` | Display name. |
| `GYM_NAME` | `Squad Gym` | Gym name. |
| `GYM_TIMEZONE` | `Asia/Kolkata` | IANA timezone used for every calendar day, streak, leaderboard period and expiry. |

### Object storage

| Variable | Default | Purpose |
|---|---|---|
| `STORAGE_DRIVER` | `local` | `local` (disk, dev) or `s3` (Neon Object Storage / R2 / MinIO / S3). |
| `LOCAL_UPLOAD_DIR` | `var/uploads` | Local driver root. |
| `S3_ENDPOINT` | – | S3-compatible endpoint for Neon Object Storage. |
| `S3_REGION` | `us-east-1` | Bucket region. |
| `S3_BUCKET` | – | Private bucket name. |
| `S3_ACCESS_KEY_ID` / `S3_SECRET_ACCESS_KEY` | – | Bucket credentials. |
| `S3_FORCE_PATH_STYLE` | `true` | Required by most S3-compatible services. |
| `MEDIA_PUBLIC_BASE_URL` | – | Optional CDN base. Leave empty to stream media through the authenticated API. |
| `MAX_UPLOAD_BYTES` | `5242880` | 5 MB upload cap. |

### Email (invitations, password reset)

| Variable | Default | Purpose |
|---|---|---|
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_SECURE` | – / `587` / `false` | SMTP server. Leave empty to log emails instead of sending. |
| `SMTP_USER` / `SMTP_PASS` | – | SMTP credentials. |
| `MAIL_FROM` | `Squad Gym <no-reply@squadgym.app>` | Sender. |

### Business defaults (editable later in the app)

`DEFAULT_IN_GYM_WINDOW_MINUTES=60`, `DEFAULT_EXPIRING_SOON_DAYS=7`,
`DEFAULT_ARCHIVE_AFTER_DAYS=90`, `DEFAULT_ATTENDANCE_UNDO_SECONDS=300`,
`DEFAULT_RENEWAL_UNDO_SECONDS=300`, `OCCUPANCY_SNAPSHOT_INTERVAL_MINUTES=5`.

### Development helpers

| Variable | Default | Purpose |
|---|---|---|
| `EXPOSE_DEV_TOKENS` | `false` | Returns invitation/reset tokens in API responses when SMTP is absent. **Must stay false in production** (the server refuses to boot otherwise). |
| `SKIP_MIGRATIONS` | `false` | Set `true` to manage migrations out-of-band (CI). |
| `LOG_LEVEL` | `info` | `debug` / `info` / `warn` / `error`. |

---

## 5. Android app

### Point it at the backend

The base URL comes from a Gradle property, so nothing secret is ever in source:

```properties
# gradle.properties (repo root) — not committed for machine-specific values
squadgym.apiBaseUrl=http://10.0.2.2:8080/
```

| Where the backend runs | URL to use |
|---|---|
| Emulator → host machine | `http://10.0.2.2:8080/` |
| Physical device on the same Wi-Fi | `http://<your-LAN-IP>:8080/` |
| Render | `https://<service>.onrender.com/` |

Cleartext HTTP is allowed **only** for `10.0.2.2`, `localhost` and `127.0.0.1`
(`res/xml/network_security_config.xml`). Everything else must be HTTPS.

### Build and install

```bash
# Build
./gradlew :app:assembleDebug

# Install on a connected device/emulator
./gradlew :app:installDebug
# or
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Minimum SDK 24. Debug builds use the `.debug` application id suffix, so they can coexist with a
release build.

### Sign in

| Role | Email | Password |
|---|---|---|
| Super Admin | from `SUPER_ADMIN_EMAIL` | from `SUPER_ADMIN_PASSWORD` |
| Admin (seeded) | `admin@squadgym.app` | `Admin@12345` |
| Member (seeded) | `aarav@example.com` | `Member@12345` |

The sign-in screen shows these hints only in debug builds.

### Invitations

Adding a member without a password creates an invitation. With `EXPOSE_DEV_TOKENS=true` and no SMTP,
the API returns the code (`invitation.token`) and the app shows it on the confirmation. In
production, the code is emailed. Two ways to accept:

* **In the app:** *I have an invitation* → paste the code.
* **Deep link:** `adb shell am start -a android.intent.action.VIEW -d "squadgym://invite/<token>"`

---

## 6. Verify the whole thing

```bash
cd backend
npm run typecheck && npm test && npm run build && npm run smoke
cd ..
./gradlew :app:assembleDebug
```

Manual smoke path in the app (seeded data):

1. Sign in as Admin → dashboard shows In Gym Now / Members / Attendance with real deltas.
2. Switch the chart between Attendance / New / Total and Weekly / Monthly / Custom.
3. **Attendance** → search "aarav" → *Mark present* → the Undo banner appears with a countdown.
   Tap **Undo** → the member returns to Absent and the counts update.
4. **Members** → *Expiring* tab → open a member → **Renew** → preview dates → confirm → Undo banner.
5. **Ranking** → switch weekly/monthly.
6. Sign out, sign in as the member → personal dashboard, calendar, own rank.
7. Sign out, sign in as the Super Admin → Profile → Admin team / Activity log / Gym settings.

---

## 7. Troubleshooting

| Symptom | Fix |
|---|---|
| `ERROR: JAVA_HOME is set to an invalid directory` | Point `JAVA_HOME` at Android Studio's `jbr` folder, or let the Gradle daemon toolchain resolve it. |
| `Using kotlin.sourceSets DSL … not allowed with built-in Kotlin` | Keep `android.disallowKotlinSourceSets=false` in `gradle.properties` (required by KSP on AGP 9). |
| App shows "You're offline" | The emulator can't reach the host: verify `squadgym.apiBaseUrl=http://10.0.2.2:8080/` and that the backend is running. |
| `AUTH_REQUIRED` on every call | Access token expired and refresh failed; sign in again. |
| Migration checksum error | A previously applied migration file changed. Add a new migration instead of editing history. |
| Photos not loading | `GET /api/v1/media/...` requires the session token; confirm you're signed in. |
| Invitation token missing | Set `EXPOSE_DEV_TOKENS=true` (dev) or configure SMTP. |
| Neon connection errors | Use the pooled host and `sslmode=require`; `npm run ready`/`curl /ready` confirms connectivity. |
