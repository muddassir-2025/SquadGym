-- 004_attendance.sql
-- Manual attendance, gym sessions and occupancy snapshots (spec §4.C, §8, §12).

-- One attendance point per member per gym-local day.
-- The partial unique index is the duplicate guard: concurrent taps or retries can
-- never create a second *active* record for the same member/day, while an undone
-- record stays in the table as audit history.
create table if not exists attendance (
  id uuid primary key default gen_random_uuid(),
  gym_id uuid not null references gyms (id) on delete cascade,
  member_id uuid not null references users (id) on delete cascade,
  attendance_date date not null,
  marked_by uuid references users (id) on delete set null,
  marked_at timestamptz not null default now(),
  undo_expires_at timestamptz not null,
  source text not null default 'manual' check (source in ('manual', 'correction')),
  status text not null default 'active' check (status in ('active', 'undone', 'corrected')),
  undone_at timestamptz,
  undone_by uuid references users (id) on delete set null,
  corrected_at timestamptz,
  corrected_by uuid references users (id) on delete set null,
  note text,
  created_at timestamptz not null default now()
);

create unique index if not exists ux_attendance_active_member_day
  on attendance (gym_id, member_id, attendance_date)
  where status = 'active';

create index if not exists ix_attendance_gym_day
  on attendance (gym_id, attendance_date, status);
create index if not exists ix_attendance_member_day
  on attendance (member_id, attendance_date desc);
create index if not exists ix_attendance_marked_at
  on attendance (gym_id, marked_at desc);

-- A member is "In Gym Now" while they have an active session that has not expired.
-- Sessions expire automatically after the configured window, so no cron is needed
-- for correctness — the job only tidies rows up.
create table if not exists gym_sessions (
  id uuid primary key default gen_random_uuid(),
  gym_id uuid not null references gyms (id) on delete cascade,
  member_id uuid not null references users (id) on delete cascade,
  attendance_id uuid not null unique references attendance (id) on delete cascade,
  checked_in_at timestamptz not null default now(),
  expires_at timestamptz not null,
  checked_out_at timestamptz,
  checked_out_by uuid references users (id) on delete set null,
  status text not null default 'active'
    check (status in ('active', 'expired', 'checked_out', 'voided')),
  created_at timestamptz not null default now(),
  check (expires_at > checked_in_at)
);

create index if not exists ix_gym_sessions_active
  on gym_sessions (gym_id, status, expires_at);
create index if not exists ix_gym_sessions_member
  on gym_sessions (member_id, checked_in_at desc);

-- Point-in-time occupancy. Used for the "same time yesterday" comparison so the
-- dashboard never invents historical data (spec §4.A).
create table if not exists occupancy_snapshots (
  id uuid primary key default gen_random_uuid(),
  gym_id uuid not null references gyms (id) on delete cascade,
  bucket_start timestamptz not null,
  captured_at timestamptz not null default now(),
  active_count integer not null check (active_count >= 0),
  attendance_count integer not null default 0 check (attendance_count >= 0),
  snapshot_date date not null,
  unique (gym_id, bucket_start)
);

create index if not exists ix_occupancy_snapshots_lookup
  on occupancy_snapshots (gym_id, snapshot_date, captured_at);
