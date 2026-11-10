# SquadGym — Implementation Plan

Derived from `gym_management_app.md`. This document is the build plan, the decision log, and the
status tracker for the implementation.

## 1. What is being built

A production-oriented gym management system with two deliverables in one repository:

| Deliverable | Path | Stack |
|---|---|---|
| Native Android app | `app/` | Kotlin 2.2, Jetpack Compose (Material 3), MVVM, Coroutines/Flow, Retrofit + OkHttp, Room, Coil, WorkManager, Android Keystore |
| REST API backend | `backend/` | Node.js 22, Express 5, TypeScript, PostgreSQL (Neon), SQL migrations, JWT sessions |
| Documentation | `docs/` | API contract, setup, deployment, decisions |

Manual attendance only. **No face recognition, biometrics, QR or camera-attendance in this release.**

## 2. Architecture

```
Android (Kotlin/Compose)                 Render (Node/Express/TS)              Neon
┌────────────────────────┐   HTTPS   ┌──────────────────────────┐   TLS   ┌──────────────┐
│ Compose UI  ── ViewModel│ ────────► │ routes → controllers     │ ──────► │ PostgreSQL   │
│ Repository              │           │        → services        │         │ (migrations) │
│  ├ Retrofit ApiService  │           │        → repositories    │         └──────────────┘
│  ├ Room cache (offline) │           │ middleware: auth/role/   │
│  └ Keystore token store │           │ permission/validate/limit│         ┌──────────────┐
└────────────────────────┘           │ Object storage (S3 API)  │ ──────► │ Neon Storage │
                                     └──────────────────────────┘         └──────────────┘
```

* The Android app never touches the database. All authorization, eligibility, uniqueness,
  undo-window and scoring rules are enforced server-side.
* The app is only a thin, cached, presentation layer over the API.

## 3. Decisions taken where the brief left room

| # | Decision | Reason |
|---|---|---|
| D1 | First-party auth (`users` + Argon2id-class bcrypt hashing + rotating refresh tokens) behind an `AuthProvider` seam, instead of calling hosted Neon Auth directly | Neon Auth requires a live project + keys that are not available in this environment. The seam (`src/services/auth/`) documents exactly which calls to swap when you enable Neon Auth; the token/session flow on Android stays identical. |
| D2 | Repository/`pg` + hand-written SQL migrations, no ORM | Full control over partial indexes, `AT TIME ZONE` date logic, advisory-lock transactions; migrations run identically on Neon and local Postgres. |
| D3 | Packages inside one Gradle `:app` module rather than 15 Gradle modules | Faster builds and simpler setup; the package layout matches §13 of the brief. |
| D4 | Manual DI (`AppContainer` + `viewModelFactory`) instead of Hilt | Hilt/KSP adds build risk on AGP 9 built-in Kotlin for no functional gain at this size. |
| D5 | Room via KSP if the toolchain accepts it; otherwise a hand-written `SQLiteOpenHelper` cache with the same repository interface | The cache is an implementation detail behind `LocalMemberCache`. |
| D6 | Membership history = one immutable `memberships` row per term. Early renewal creates a new row starting `old_end + 1`; expiry renewal starts on the renewal date. Undo marks the new row `reversed` and reactivates the previous row. | Preserves history, makes undo exact, and keeps coverage contiguous without mutating old dates. |
| D7 | `attendance` uses a partial unique index `(gym_id, member_id, attendance_date) WHERE status = 'active'` | Duplicate-proof at the database level, while still allowing an audited undo + re-mark. |
| D8 | Streak/missed-day math is computed in TypeScript from the gym's operating schedule (not raw `+1 day` SQL) | Correct for gyms closed on specific weekdays, membership start/end and "today is still pending". |
| D9 | Occupancy snapshots every 5 minutes (job) + on-demand capture on dashboard read. "In Gym Now" comparison uses the snapshot nearest (±15 min) to yesterday's same time; no snapshot → no comparison shown. | Implements "never fabricate historical comparisons". |
| D10 | Photos: private bucket, `member.photoVersion` is the cache key (`?v=N`). Storage driver is `s3` (Neon Object Storage) or `local` (dev disk) behind one interface. | Coil caches on URL; a new version busts cache without extra ETag plumbing. |
| D11 | Undo windows are absolute timestamps stored in the DB (`undo_expires_at`), validated on every call, returned to the client with `secondsRemaining` | Works after app restart / navigation, as required. |
| D12 | Attendance corrections after the window are a separate permission-checked, audited endpoint; audit rows are never deleted | Requirement §4.C and §5. |
| D13 | Offline writes are **not** silently queued: a failed mark returns a clear "not confirmed by server" state. WorkManager only refreshes read caches. | Requirement: never display an unconfirmed offline action as recorded. |

## 4. Stages

Status legend: `[x]` done, `[~]` in progress, `[ ]` pending.

- [x] **Stage 0 — Audit of existing project.** Fresh Compose template found (`SquadGym`, AGP 9.3.3,
  Kotlin 2.2.10, Compose BOM 2026.02.01, namespace `com.muddassir.squadgym`). Nothing overwritten.
- [x] **Stage 1 — Foundations.** Gradle catalog + dependencies, design system (light/dark), navigation
  graph, session/token storage, Retrofit layer, error model, manual DI.
- [x] **Stage 2 — Database + backend core.** Migrations, seed/bootstrap, auth, roles, permissions,
  audit log, gym settings, timezone utilities, testing harness.
- [x] **Stage 3 — Admin dashboard.** Today's stats with real comparisons, interactive chart,
  occupancy snapshots.
- [x] **Stage 4 — Member management.** List/search/filters/sorting/pagination, 4 tabs, create/edit
  (with photo), detail, archive/restore.
- [x] **Stage 5 — Manual attendance.** Summary counts, All/Present/Absent tabs, filters, mark present,
  server-enforced 5-minute undo, audited corrections, active sessions, manual checkout, `/me` stats.
- [x] **Stage 6 — Membership + renewal.** Configurable plans, renewal date math, 5-minute renewal
  undo, history, expiring/expired views, archiving config.
- [x] **Stage 7 — Leaderboard + analytics.** Weekly/monthly scoring, deterministic ties, streaks,
  personal analytics, calendar heat-map.
- [x] **Stage 8 — Super Admin.** Admin CRUD, activation, permissions, audit viewer, gym settings,
  plans, shifts, operating days.
- [x] **Stage 9 — Caching, polish, verification, docs.** Room cache, WorkManager sync, offline/empty/
  error states, `tsc` + unit tests + `assembleDebug`, README, API docs, deployment docs.

## 5. Verification gates

1. `cd backend && npm run typecheck` — zero TypeScript errors.
2. `cd backend && npm test` — unit tests (timezone dates, undo windows, streaks, scoring, renewal math).
3. `cd backend && npm run build` — compiles to `dist/`.
4. `.\gradlew.bat :app:assembleDebug` — Android app compiles and packages.
5. Manual smoke path documented in `docs/SETUP.md` (login → dashboard → attendance → undo → renew).

## 6. Environment variables

Nothing secret is committed. `backend/.env.example` lists every variable, `docs/SETUP.md` explains
each one. The Android app only knows `API_BASE_URL` (Gradle property) — no credentials.

Required before the app is usable end-to-end:

* `DATABASE_URL` (Neon Postgres, pooled connection string, `sslmode=require`)
* `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET`
* `SUPER_ADMIN_EMAIL`, `SUPER_ADMIN_PASSWORD`, `SUPER_ADMIN_NAME` (bootstrap)
* `GYM_NAME`, `GYM_TIMEZONE`
* Storage: `STORAGE_DRIVER=s3` + `S3_ENDPOINT`, `S3_REGION`, `S3_BUCKET`, `S3_ACCESS_KEY_ID`,
  `S3_SECRET_ACCESS_KEY` (or `STORAGE_DRIVER=local` for development)
* Optional: SMTP settings for invitation / password-reset emails
