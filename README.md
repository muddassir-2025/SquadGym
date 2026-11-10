# SquadGym

A production-oriented gym management system for Android: **manual attendance, memberships,
leaderboards and staff administration** — built as a real client/server product, not a mock.

| | |
|---|---|
| **Android app** | Kotlin 2.2 · Jetpack Compose · Material 3 · MVVM · Coroutines/Flow · Retrofit/OkHttp · Room · Coil · WorkManager · Android Keystore |
| **Backend** | Node.js 22 · Express 5 · TypeScript · PostgreSQL (Neon) · SQL migrations · JWT sessions · S3-compatible object storage |
| **Docs** | [Plan & decisions](docs/PLAN.md) · [Setup](docs/SETUP.md) · [API reference](docs/API.md) · [Deployment](docs/DEPLOY.md) |

> **Scope of this release:** manual attendance only. Face recognition, biometrics, QR scanning and
> camera-attendance workflows are intentionally **not** implemented — the attendance dashboard is
> fully self-sufficient. No payment processing.

---

## What works end-to-end

**Super Admin**
- Gym dashboard with real comparisons, interactive analytics and occupancy
- Create / edit / deactivate Admins, fine-grained permissions, audit viewer
- Gym settings: timezone, expiry/archive rules, undo windows, operating days, shifts, plans

**Admin**
- Today's stats (In Gym Now, Total Members, Today's Attendance) with honest day-over-day deltas
- Weekly / monthly / custom attendance chart (daily attendance, new members, total members)
- **Take Attendance**: search, All/Present/Absent tabs, shift/gender/plan filters, one-tap mark,
  server-enforced 5-minute **Undo**, audited corrections, manual checkout, pagination
- Member directory with 4 tabs (All, Expiring Soon, Expired, Archived), filters, sorting, search
- Member profile, create (with photo + invitation), edit, archive/restore, membership history
- Renew memberships with correct date math and a 5-minute **renewal Undo**
- Weekly/monthly leaderboard with deterministic ties and streaks

**Member**
- Read-only home: gym stats, membership card, personal score/streak/attendance %, calendar
- Leaderboard with their own rank
- No administrative access — enforced by the backend, not just hidden in the UI

**Platform**
- Offline-tolerant: cached dashboards/directories, clearly labelled "showing saved data"
- Profile images cached by version (`?v=N`) with Coil disk cache; private media served through an
  authenticated endpoint
- Tokens encrypted with an Android Keystore AES-GCM key; refresh rotation; instant sign-out
- Light and dark themes, responsive Compose layout, loading/empty/error/offline states

## Repository layout

```
SquadGym/
├── app/                     # Android app (single Gradle module, feature packages)
│   └── src/main/java/com/muddassir/squadgym/
│       ├── core/            # theme, design-system components, DI, errors, utils
│       ├── data/            # DTOs, Retrofit APIs, Room cache, repositories, session
│       ├── features/        # auth, dashboard, attendance, members, leaderboard,
│       │                    # member home, superadmin, profile, splash
│       ├── navigation/      # role-aware NavHost, bottom bar, deep link
│       └── work/            # WorkManager cache refresh
├── backend/
│   ├── migrations/          # 001…005 SQL migrations (idempotent, tracked)
│   ├── src/                 # config, routes, controllers, services, repositories,
│   │                        # middleware, validators, utils, database
│   ├── scripts/smoke.mjs    # 85-check end-to-end API test
│   ├── tests/               # Vitest unit tests (timezone, scoring, renewal math)
│   └── .env.example         # every variable documented
└── docs/                    # PLAN, SETUP, API, DEPLOY
```

## Quick start (local)

```bash
# 1 ─ Backend
cd backend
cp .env.example .env          # set DATABASE_URL, JWT secrets, super admin + gym
npm install
npm run migrate               # create the schema
npm run bootstrap             # create the Super Admin + gym defaults
npm run seed                  # optional: demo members, plans, attendance history
npm run dev                   # http://localhost:8080

# 2 ─ Android
#    In the repo root, point the app at your backend (emulator loopback is the default):
#      gradle.properties → squadgym.apiBaseUrl=http://10.0.2.2:8080/
./gradlew :app:assembleDebug
```

Full instructions, Neon setup and troubleshooting: **[docs/SETUP.md](docs/SETUP.md)**.

### Demo accounts (after `npm run seed`, development only)

| Role | Email | Password |
|---|---|---|
| Super Admin | `owner@squadgym.app` (from `.env`) | from `.env` |
| Admin | `admin@squadgym.app` | `Admin@12345` |
| Member | `aarav@example.com` | `Member@12345` |

## Verification

```bash
cd backend
npm run typecheck      # 0 errors
npm test               # 34 unit tests
npm run build          # compiles to dist/
npm run smoke          # 85 end-to-end API checks against a running server

cd ..
./gradlew :app:assembleDebug   # APK builds
```

The smoke test exercises the real flows: login for all three roles, role enforcement, refresh
rotation, dashboard/chart, member CRUD + invite, duplicate-safe marking, undo, corrections,
renewal + renewal undo, leaderboard, member dashboard, super-admin settings and audit trail.

## Key decisions

The full decision log is in [docs/PLAN.md](docs/PLAN.md). The short version:

1. **All business rules live in the backend.** Eligibility, uniqueness, undo windows, scoring and
   permissions are enforced server-side; the app is a cached presentation layer.
2. **One attendance row per member per gym-local day**, guaranteed by a partial unique index
   (`WHERE status = 'active'`), so double taps and races cannot create duplicate points.
3. **Undo windows are absolute server timestamps** returned as `secondsRemaining`; they keep
   working after the app is closed or the screen changes.
4. **Membership history is immutable terms.** Renewing early starts the day after the current term,
   renewing late starts today; undo reverses exactly that change.
5. **Occupancy comparisons are never invented.** "In Gym Now" compares against a stored snapshot at
   the same time yesterday, and hides the comparison when no snapshot exists.
6. **Photos are private.** Media is streamed through an authenticated endpoint with a versioned URL
   so Coil caches correctly and a leaked key is useless.
7. **Auth is first-party with a documented seam** for Neon Auth (see `backend/src/services/authService.ts`
   and the note in PLAN.md) because hosted Neon Auth credentials are not available in this
   environment.

## Environment variables

Nothing secret is committed. Copy `backend/.env.example` → `backend/.env`; every value is listed and
explained in [docs/SETUP.md](docs/SETUP.md#environment-variables). The Android app only knows
`BuildConfig.API_BASE_URL` (set via the `squadgym.apiBaseUrl` Gradle property) — never a secret.

## License / ownership

Private project scaffolded for the SquadGym gym. Review the security notes in
[docs/DEPLOY.md](docs/DEPLOY.md) before going live.
