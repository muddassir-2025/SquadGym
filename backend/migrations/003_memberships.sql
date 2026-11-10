-- 003_memberships.sql
-- Immutable membership terms + renewal history (spec §5, §8).
--
-- Invariants:
--  * One row per membership term. Terms are never edited in place after a renewal.
--  * Renewing before expiry creates a new term starting the day after the current
--    term ends, so no remaining days are lost.
--  * Renewing after expiry creates a new term starting on the renewal date.
--  * Undo of a renewal marks the new term `reversed` and returns the previous term
--    to `active` — history is preserved, never deleted.

create table if not exists memberships (
  id uuid primary key default gen_random_uuid(),
  gym_id uuid not null references gyms (id) on delete cascade,
  member_id uuid not null references users (id) on delete cascade,
  plan_id uuid not null references membership_plans (id),
  start_date date not null,
  end_date date not null,
  status text not null default 'active'
    check (status in ('active', 'superseded', 'reversed', 'cancelled')),
  created_by uuid references users (id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (end_date >= start_date)
);

create index if not exists ix_memberships_member
  on memberships (member_id, end_date desc);
create index if not exists ix_memberships_gym_dates
  on memberships (gym_id, start_date, end_date);
create index if not exists ix_memberships_plan on memberships (plan_id);

-- At most one currently-running term per member.
create unique index if not exists ux_memberships_active_per_member
  on memberships (member_id)
  where status = 'active';

drop trigger if exists trg_memberships_updated_at on memberships;
create trigger trg_memberships_updated_at before update on memberships
  for each row execute function set_updated_at();

-- The newest non-reversed, non-cancelled term for every member.
create or replace view v_current_membership as
select distinct on (m.member_id)
  m.id,
  m.gym_id,
  m.member_id,
  m.plan_id,
  m.start_date,
  m.end_date,
  m.status as row_status,
  m.created_at
from memberships m
where m.status <> 'reversed'
  and m.status <> 'cancelled'
order by m.member_id, m.end_date desc, m.created_at desc;

create table if not exists membership_renewals (
  id uuid primary key default gen_random_uuid(),
  gym_id uuid not null references gyms (id) on delete cascade,
  member_id uuid not null references users (id) on delete cascade,
  previous_membership_id uuid not null references memberships (id),
  new_membership_id uuid not null references memberships (id) on delete cascade,
  previous_plan_id uuid references membership_plans (id),
  new_plan_id uuid references membership_plans (id),
  previous_start_date date not null,
  previous_end_date date not null,
  new_start_date date not null,
  new_end_date date not null,
  status text not null default 'active' check (status in ('active', 'reversed')),
  performed_by uuid references users (id) on delete set null,
  performed_at timestamptz not null default now(),
  undo_expires_at timestamptz not null,
  reversed_at timestamptz,
  reversed_by uuid references users (id) on delete set null,
  reason text,
  metadata jsonb not null default '{}'::jsonb
);

create index if not exists ix_membership_renewals_member
  on membership_renewals (member_id, performed_at desc);
create index if not exists ix_membership_renewals_gym
  on membership_renewals (gym_id, performed_at desc);
create index if not exists ix_membership_renewals_undo
  on membership_renewals (gym_id, undo_expires_at)
  where status = 'active';
