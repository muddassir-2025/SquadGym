# SquadGym API

Base URL: `{API_BASE_URL}/api/v1` · JSON only · UTF-8

```
Authorization: Bearer <accessToken>
Content-Type: application/json
```

---

## Conventions

### Success

```json
{ "success": true, "data": { … } }
```

Some endpoints add `meta` (pagination is embedded in the resource itself for list screens).

### Error

```json
{
  "success": false,
  "error": {
    "code": "UNDO_WINDOW_EXPIRED",
    "message": "The 5-minute undo window has passed. Use an attendance correction instead.",
    "details": { "field": "message" },
    "requestId": "b914e85c-…"
  }
}
```

`details` is present for validation errors (`{field: message}`) and occasionally for conflict
context. `requestId` matches the `X-Request-Id` response header and the server logs.

### Status codes

| Status | Meaning |
|---|---|
| 200 | OK |
| 201 | Created (mark present, renew, create member/admin) |
| 202 | Accepted (forgot password — never reveals whether the account exists) |
| 400 | Malformed JSON |
| 401 | Missing/expired token, bad credentials |
| 403 | Wrong role or missing permission, deactivated account |
| 404 | Unknown resource (also used for cross-gym access) |
| 409 | Business conflict (`ALREADY_MARKED`, `UNDO_WINDOW_EXPIRED`, `GYM_CLOSED`, …) |
| 415 | Unsupported upload type |
| 422 | Validation error |
| 429 | Rate limited |
| 5xx | Server/database failure |

### Error codes

`AUTH_REQUIRED`, `AUTH_INVALID_CREDENTIALS`, `AUTH_TOKEN_EXPIRED`, `AUTH_TOKEN_INVALID`,
`AUTH_ACCOUNT_INACTIVE`, `AUTH_ACCOUNT_INVITED`, `FORBIDDEN`, `VALIDATION_ERROR`, `NOT_FOUND`,
`CONFLICT`, `ALREADY_MARKED`, `ALREADY_UNDONE`, `UNDO_WINDOW_EXPIRED`, `UNDO_WINDOW_ACTIVE`,
`GYM_CLOSED`, `MEMBER_NOT_ELIGIBLE`, `MEMBER_ARCHIVED`, `RENEWAL_NOT_REVERSIBLE`, `RATE_LIMITED`,
`PAYLOAD_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`, `DEPENDENCY_UNAVAILABLE`, `INTERNAL_ERROR`.

### Pagination

Query: `page` (1-based, default 1) and `pageSize` (default 20, max 100). List resources return:

```json
"meta": { "page": 1, "pageSize": 20, "total": 42, "totalPages": 3, "hasNext": true, "hasPrevious": false }
```

### Roles & permissions

* `super_admin` — implicit access to everything.
* `admin` — needs an explicit permission (`dashboard.view`, `members.manage`, `attendance.correct`, …).
* `member` — only `/me/*`, `/leaderboard`, `/plans`, `/auth/*`.

Permission changes and deactivations take effect on the next request (the user row is re-read for
every authenticated call).

---

## Auth

### POST /auth/login

```json
{ "email": "admin@squadgym.app", "password": "Admin@12345" }
```

```json
{
  "success": true,
  "data": {
    "user": {
      "id": "…", "email": "admin@squadgym.app", "fullName": "Riya Admin",
      "role": "admin", "status": "active", "phone": "+91…",
      "photoUrl": null, "imageVersion": 0,
      "permissions": ["dashboard.view", "members.manage", "…"], "lastLoginAt": "…"
    },
    "gym": { "id": "…", "name": "Squad Gym", "timezone": "Asia/Kolkata",
             "expiringSoonDays": 7, "archiveAfterDays": 90,
             "inGymWindowMinutes": 60, "attendanceUndoSeconds": 300, "renewalUndoSeconds": 300 },
    "shifts": [{ "id": "…", "name": "Morning", "startTime": "06:00", "endTime": "12:00" }],
    "accessToken": "…", "refreshToken": "…", "accessTokenExpiresIn": 900, "tokenType": "Bearer"
  }
}
```

Errors: 401 `AUTH_INVALID_CREDENTIALS`, 403 `AUTH_ACCOUNT_INVITED` / `AUTH_ACCOUNT_INACTIVE`.
Rate limit: 25 requests / 15 min / IP.

### POST /auth/refresh

`{ "refreshToken": "…" }` → a new session object. Refresh tokens are **single-use**: the presented
token is revoked on success, so replay returns 401.

### POST /auth/logout

`{ "refreshToken": "…" }` → `{ "signedOut": true }`. Revokes that refresh token and writes an audit entry.

### GET /auth/me

Returns `{ user, gym, shifts }` for the current session. Use it to validate a cached session on app
start and to refresh permissions.

### POST /auth/forgot-password

`{ "email": "…" }` → `202 { "accepted": true, "message": "…", "devToken": "ABCD1234" }`.
`devToken` appears only when `EXPOSE_DEV_TOKENS=true` and SMTP is not configured. Always 202.

### POST /auth/reset-password

`{ "token": "ABCD1234", "newPassword": "…" }` → `{ "reset": true }`.
Revokes every refresh token for the account.

### POST /invitations/accept

`{ "token": "…", "password": "…" }` → a session object (same shape as login). Activates an invited
member/admin account.

### GET /invitations/{token}

Public preview: `{ "email", "fullName", "role", "gymName", "expiresAt" }`. 409 when used/revoked/expired.

---

## Dashboard (staff)

### GET /dashboard/today

```json
{
  "gymLocalDate": "2026-11-10",
  "gymLocalDateLabel": "Tue, 10 Nov 2026",
  "gymOpen": true,
  "timezone": "Asia/Kolkata",
  "gymName": "Squad Gym",
  "stats": {
    "inGymNow": { "value": 3,
      "comparison": { "previousValue": 7, "difference": -4, "percentChange": -57.1, "direction": "down" } },
    "totalMembers": { "value": 11, "comparison": { … } },
    "todayAttendance": { "value": 6, "comparison": { … } }
  },
  "generatedAt": "2026-11-10T08:14:33.093Z"
}
```

* `inGymNow` — active gym sessions that have not expired (60 min by default).
* `comparison` for `inGymNow` comes from a **stored occupancy snapshot** at the same time yesterday;
  it is `null` when no snapshot exists (the dashboard must never invent history).
* `totalMembers` excludes archived members and compares against yesterday's count.
* `todayAttendance` counts distinct members who attended today and compares with yesterday.

A snapshot for the current 5-minute bucket is captured on read.

### GET /dashboard/chart

Query: `metric=attendance|new_members|total_members`, `range=week|month|custom`, `from`, `to`
(`YYYY-MM-DD`, required for `custom`, max 366 days). Defaults: `attendance`, `week`.

```json
{
  "metric": "attendance", "range": "week",
  "from": "2026-11-04", "to": "2026-11-10", "rangeLabel": "Last 7 days",
  "points": [{ "date": "2026-11-04", "label": "4 Nov", "weekday": "Wed", "value": 8, "isGymOpen": true }],
  "total": 52, "previousTotal": 47,
  "comparison": { "previousValue": 47, "difference": 5, "percentChange": 10.6, "direction": "up" }
}
```

### GET /dashboard/occupancy?date=YYYY-MM-DD

Snapshots for one gym-local day: `{ date, dateLabel, points: [{ capturedAt, activeCount, attendanceCount }] }`.

### GET /leaderboard?period=week|month&anchor=YYYY-MM-DD&page=1

```json
{
  "period": "week", "periodStart": "2026-11-09", "periodEnd": "2026-11-15",
  "periodLabel": "9 Nov – 15 Nov", "timezone": "Asia/Kolkata",
  "entries": [{ "rank": 1, "memberId": "…", "fullName": "Aarav Sharma",
                "photoUrl": "…", "imageVersion": 0, "score": 6, "currentStreak": 6, "isMe": false }],
  "me": { "rank": 4, "score": 3, "currentStreak": 2, "totalMembers": 11, … },
  "meta": { … }
}
```

One point per distinct attendance day. Ties break by `fullName`, then `id` (deterministic).
Periods are computed in the gym's timezone.

### GET /plans

`{ "plans": [{ "id", "name", "durationDays", "priceCents", "currency", "description", "isActive", "sortOrder" }] }`

---

## Members

### GET /members

Query: `tab=all|expiring|expired|archived`, `search`, `gender=male|female|other`, `planId`, `shiftId`,
`status=active|expiring|expired|none`, `minScore`, `maxScore`, `expiresFrom`, `expiresTo`,
`joinedFrom`, `joinedTo`, `sort=name_asc|score_desc|score_asc|newest|oldest|expiry_asc`,
`page`, `pageSize`.

```json
{
  "members": [{
    "id": "…", "fullName": "Kabir Khan", "email": "kabir@example.com", "phone": "+91…",
    "gender": "male", "address": null, "joinedOn": "2026-08-01", "archivedAt": null,
    "emergencyContactName": "…", "emergencyContactPhone": "…",
    "photoUrl": "…/media/…?avatar=v0", "imageVersion": 0, "accountStatus": "active",
    "shift": { "id": "…", "name": "Evening" },
    "membership": { "id": "…", "planId": "…", "planName": "Monthly",
                    "startDate": "2026-10-14", "endDate": "2026-11-13",
                    "status": "expiring", "daysRemaining": 3 },
    "membershipStatus": "expiring", "score": 12
  }],
  "context": { "gymLocalDate": "2026-11-10", "expiringSoonDays": 7,
               "scorePeriodStart": "2026-11-01", "scorePeriodEnd": "2026-11-10" },
  "meta": { "page": 1, "pageSize": 20, "total": 11, "totalPages": 1, "hasNext": false, "hasPrevious": false }
}
```

`score` is the member's points **in the current month** (filters/sorting use it). `membershipStatus` is
derived from the gym-local date and `expiringSoonDays`; archived members are excluded from `all`.

### GET /members/{id}

```json
{
  "member": { …MemberDto… },
  "extras": { "lifetimeScore": 84, "monthlyScore": 9, "weeklyScore": 3,
              "lastAttendanceAt": "…", "totalAttendanceDays": 84,
              "inGymNowUntil": "…", "currentStreak": 0 },
  "membershipHistory": [ …MembershipDto… ],
  "attendance": [{ "id", "date": "2026-11-10", "dateLabel": "10 Nov", "status": "active",
                   "markedAt": "…", "markedByName": "Riya Admin" }],
  "renewals": [{ "id", "performedAt", "newStartDate", "newEndDate", "status", "undoExpiresAt" }]
}
```

### POST /members

```json
{
  "fullName": "Smoke Test Member",
  "email": "member@example.com",
  "phone": "+91 90000 12345",
  "gender": "male",
  "address": "12 Street",
  "planId": "…",
  "startDate": "2026-11-10",
  "shiftId": "…",
  "emergencyContactName": "…",
  "emergencyContactPhone": "…",
  "dateOfBirth": "1998-04-02",
  "notes": "…",
  "initialPassword": "optional-8-chars"
}
```

Without `initialPassword` the account is created **invited** and an invitation is issued.

```json
{ "memberId": "…", "invitation": { "sent": false, "token": "…" }, "accountStatus": "invited" }
```

`invitation.token` is only returned in development (`EXPOSE_DEV_TOKENS=true`); in production it is emailed.

### PATCH /members/{id}

Any subset of: `fullName`, `email`, `phone`, `gender`, `address`, `shiftId`,
`emergencyContactName`, `emergencyContactPhone`, `dateOfBirth`, `notes`. Returns the updated member.

### POST /members/{id}/archive · POST /members/{id}/restore

Archive: `{ "reason": "optional" }` → `{ "archived": true, "memberId": "…" }`.
Archiving voids any active gym session; attendance and membership history are preserved.

### POST /members/{id}/photo

`multipart/form-data`, field **photo** (JPEG/PNG/WebP, ≤5 MB). Returns
`{ "photoUrl": "…?avatar=v2", "imageVersion": 2 }`. The URL includes the version, so clients can cache
aggressively and bust by URL.

### GET /members/{id}/memberships

`{ "items": [{ "id", "planName", "startDate", "endDate", "status", "days", "renewalId" }] }`

### GET /members/{id}/renewal-preview?planId=…

```json
{ "memberId": "…", "planId": "…", "planName": "Monthly", "durationDays": 30,
  "current": { …MembershipDto… },
  "newStartDate": "2026-11-14", "newEndDate": "2026-12-13",
  "extendedFromExisting": true, "priceCents": 0, "currency": "INR" }
```

### POST /members/{id}/renewals

`{ "planId": "…" }` → 201

```json
{
  "memberId": "…",
  "membership": { "id", "planId", "planName", "startDate": "2026-11-14", "endDate": "2026-12-13",
                  "status": "active", "rowStatus": "active", "daysRemaining": 33, "priceCents": 0 },
  "previousMembership": { … },
  "renewal": { "id": "…", "performedAt": "…", "undoExpiresAt": "…",
               "canUndo": true, "secondsRemaining": 300, "extendedFromExisting": true }
}
```

Date rules: renewed **before** expiry → starts the day after the current end date (no lost days);
renewed **after** expiry → starts today. Both preserve history.

---

## Attendance

### GET /attendance

Query: `date=YYYY-MM-DD` (defaults to the gym-local today), `tab=all|present|absent`, `search`,
`shiftId`, `gender`, `planId`, `page`, `pageSize`.

```json
{
  "date": "2026-11-10", "dateLabel": "Tue, 10 Nov 2026", "timezone": "Asia/Kolkata",
  "gymOpen": true, "inGymNow": 3,
  "summary": { "total": 11, "present": 6, "absent": 5, "filtered": true },
  "gymTotals": { "total": 11, "present": 6, "absent": 5 },
  "members": [{
    "memberId": "…", "fullName": "Aarav Sharma", "email": "aarav@example.com", "phone": "…",
    "photoUrl": "…", "imageVersion": 0, "gender": "male",
    "shift": { "id": "…", "name": "Morning" }, "plan": { "id": "…", "name": "Quarterly" },
    "membershipEndDate": "2026-12-21", "membershipStatus": "active",
    "status": "present", "attendanceId": "…", "markedAt": "2026-11-10T01:00:00.000Z",
    "canUndo": true, "undoSecondsRemaining": 271,
    "session": { "id": "…", "checkedInAt": "…", "expiresAt": "…", "secondsRemaining": 2671,
                 "isActive": true, "checkedOutAt": null, "status": "active" },
    "score": 4
  }],
  "meta": { … }
}
```

Eligibility: gym open **and** the member is not archived **and** a membership term covers `date`.
Members without an active membership are never counted as absent.

`summary` reflects the current filters; `gymTotals` is always unfiltered (`filtered` tells you which
is which).

### POST /attendance/mark

`{ "memberId": "…", "date": "2026-11-10" }` (date optional, defaults to gym today) → 201

```json
{
  "attendance": { "id", "memberId", "attendanceDate", "markedAt", "status": "active", "source": "manual" },
  "member": { "id", "fullName", "photoUrl", "imageVersion" },
  "alreadyMarked": false,
  "undo": { "canUndo": true, "secondsRemaining": 300, "expiresAt": "…" },
  "session": { "id", "checkedInAt", "expiresAt", "secondsRemaining" }
}
```

Idempotent: a repeated tap returns **200/201** with `alreadyMarked: true` and the existing record.
Conflicts: `GYM_CLOSED` (409), `MEMBER_NOT_ELIGIBLE` (409), future date (422).
Marking the gym-local today also starts the 60-minute occupancy session.

### POST /attendance/{attendanceId}/undo

Server-enforced window (default 300 s). Returns
`{ "attendanceId", "memberId", "attendanceDate", "status": "undone", "undoneAt" }`.

Conflicts: `ALREADY_UNDONE` (409), `UNDO_WINDOW_EXPIRED` (409). Undo also voids the gym session;
the original record stays in the audit trail.

### POST /attendance/corrections

```json
{ "memberId": "…", "date": "2026-11-10", "action": "mark_present", "reason": "missed at the desk" }
```

`action=mark_present` adds an audited attendance point (no undo window). `action=remove` reverses an
existing record **after** its undo window; while the window is open the API returns
`UNDO_WINDOW_ACTIVE` (409) so admins use Undo instead. Requires `attendance.correct`.

### GET /attendance/sessions/active

`{ "items": [{ "id", "memberId", "fullName", "email", "photoUrl", "imageVersion", "shiftName",
"checkedInAt", "expiresAt", "secondsRemaining" }], "total": 3 }`

### POST /attendance/sessions/{id}/checkout

Ends occupancy early without touching the attendance record or leaderboard points:
`{ "sessionId", "memberId", "checkedOutAt" }`.

---

## Memberships

### POST /memberships/renewals/{renewalId}/undo

Reverses the **latest** renewal within its window:

```json
{ "memberId": "…", "membership": { …restored term… },
  "renewalId": "…", "reversedAt": "…" }
```

Conflicts: `RENEWAL_NOT_REVERSIBLE` (already reversed, or a newer membership change exists),
`UNDO_WINDOW_EXPIRED`.

---

## Member self-service (`/me`, role `member` only)

### GET /me/dashboard?month=YYYY-MM

```json
{
  "gymLocalDate": "2026-11-10", "gymOpen": true, "timezone": "Asia/Kolkata", "gymName": "Squad Gym",
  "stats": { "inGymNow": {…}, "totalMembers": {…}, "todayAttendance": {…} },
  "personal": {
    "currentStreak": 4, "longestStreak": 11, "lifetimeScore": 62,
    "weekly": { "from": "2026-11-09", "to": "2026-11-10", "label": "This week", "score": 2,
                "daysAttended": 2, "openDays": 2, "missedDays": 0, "attendancePercent": 100.0 },
    "monthly": { … },
    "attendancePercent": 71.4, "daysAttended": 10, "missedDays": 4,
    "todayStatus": "present", "inGymNow": true
  },
  "membership": { "id", "planName", "startDate", "endDate", "status", "daysRemaining" },
  "ranking": { "week": 2, "month": 3, "totalMembers": 11 },
  "calendar": [{ "date": "2026-11-09", "status": "attended", "isOpen": true }],
  "calendarMonth": "2026-11", "calendarMonthLabel": "November 2026"
}
```

Calendar statuses: `attended`, `missed`, `closed`, `future`, `before_membership`, `after_membership`.
Missed days count only when the gym was open **and** the membership was active.

### GET /me/calendar?month=YYYY-MM

`{ "month", "monthLabel", "days": [ … ], "summary": { …PeriodDto… } }`

### GET /me/profile · GET /me/membership

`/me/profile` → MemberDto · `/me/membership` → `{ "membership": MembershipDto | null }`

### GET /me/attendance?from=YYYY-MM-DD&to=YYYY-MM-DD

`{ "from", "to", "items": [{ "id", "date", "status", "markedAt", "markedByName" }] }` (defaults: last 30 days)

---

## Super Admin (`/superadmin`, role `super_admin` only)

### GET /superadmin/admins

`{ "admins": [{ "id", "email", "fullName", "phone", "role", "status", "permissions",
"photoUrl", "imageVersion", "lastLoginAt", "createdAt" }] }`

### POST /superadmin/admins

`{ "fullName", "email", "phone?", "permissions?": ["members.manage"], "initialPassword?" }`
→ 201 `{ "admin": { … }, "invitation": { "sent": false, "token": "…" } | null }`

### PATCH /superadmin/admins/{id}

`{ "fullName?", "phone?", "permissions?" }` → updated admin. Super Admin permissions are immutable.

### POST /superadmin/admins/{id}/status

`{ "status": "active" | "inactive" }`. Deactivating yourself (409) or the last Super Admin (409)
is rejected. Deactivation takes effect immediately — existing tokens stop working.

### GET /superadmin/audit-logs

Query: `page`, `pageSize`, `action`, `actorId`, `entityType`, `from`, `to`, `search`.

```json
{ "items": [{ "id": "128", "actorUserId": "…", "actorName": "Riya Admin", "actorRole": "admin",
              "action": "attendance.marked", "entityType": "attendance", "entityId": "…",
              "metadata": { "memberName": "Aarav Sharma", "attendanceDate": "2026-11-10" },
              "createdAt": "…" }],
  "meta": { … } }
```

Actions: `auth.login`, `auth.logout`, `auth.password_reset`, `invitation.accepted`, `member.created`,
`member.updated`, `member.archived`, `member.restored`, `member.photo_updated`, `attendance.marked`,
`attendance.undone`, `attendance.corrected`, `attendance.session_checked_out`, `membership.renewed`,
`membership.renewal_reversed`, `plan.created`, `plan.updated`, `admin.created`, `admin.updated`,
`admin.status_changed`, `admin.permissions_changed`, `settings.updated`.

### GET /superadmin/gym

`{ "gym": GymDto, "operatingDays": [{ "dayOfWeek", "dayLabel", "isOpen", "opensAt", "closesAt" }],
"shifts": [...], "plans": [...], "permissionsCatalog": [...] }`

### PATCH /superadmin/gym/settings

Any subset of: `name`, `timezone` (validated IANA), `address`, `phone`, `email`, `expiringSoonDays`,
`archiveAfterDays`, `inGymWindowMinutes`, `attendanceUndoSeconds`, `renewalUndoSeconds` → GymDto.

### PUT /superadmin/gym/operating-days

```json
{ "days": [{ "dayOfWeek": 1, "isOpen": true, "opensAt": "05:00", "closesAt": "23:00" }] }
```

### POST /superadmin/gym/shifts · PATCH /superadmin/gym/shifts/{id}

`{ "name", "startTime": "06:00", "endTime": "12:00", "sortOrder?" }` · patch with
`{ "name?", "startTime?", "endTime?", "sortOrder?", "isActive?" }`.

### POST /superadmin/plans · PATCH /superadmin/plans/{id}

`{ "name", "durationDays", "priceCents?", "currency?", "description?", "sortOrder?" }` ·
patch with `{ …, "isActive?" }`.

---

## Media

### GET /media/{token}

Authenticated binary response (the token is the base64url-encoded object key embedded in `photoUrl`).
`Cache-Control: private, max-age=31536000, immutable` — the URL changes when the image version
changes. The bucket never needs to be public.
