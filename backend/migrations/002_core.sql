-- 002_core.sql
-- Gym, schedule, staff/member identities and membership plan catalogue.

create or replace function set_updated_at() returns trigger
language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create table if not exists gyms (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  slug text not null unique,
  timezone text not null default 'Asia/Kolkata',
  address text,
  phone text,
  email text,
  logo_key text,
  -- Business configuration (Super Admin → Gym settings)
  expiring_soon_days integer not null default 7 check (expiring_soon_days between 1 and 120),
  archive_after_days integer not null default 90 check (archive_after_days between 1 and 3650),
  in_gym_window_minutes integer not null default 60 check (in_gym_window_minutes between 5 and 480),
  attendance_undo_seconds integer not null default 300 check (attendance_undo_seconds between 0 and 86400),
  renewal_undo_seconds integer not null default 300 check (renewal_undo_seconds between 0 and 86400),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

drop trigger if exists trg_gyms_updated_at on gyms;
create trigger trg_gyms_updated_at before update on gyms
  for each row execute function set_updated_at();

-- Which weekdays the gym operates. 1 = Monday … 7 = Sunday (ISO-8601).
create table if not exists gym_operating_days (
  gym_id uuid not null references gyms (id) on delete cascade,
  day_of_week smallint not null check (day_of_week between 1 and 7),
  is_open boolean not null default true,
  opens_at time not null default '05:00',
  closes_at time not null default '23:00',
  primary key (gym_id, day_of_week),
  check (closes_at > opens_at)
);

-- Gym-configurable shifts (Morning / Evening / Night …). Members reference these.
create table if not exists gym_shifts (
  id uuid primary key default gen_random_uuid(),
  gym_id uuid not null references gyms (id) on delete cascade,
  name text not null check (length(btrim(name)) between 1 and 40),
  start_time time not null default '06:00',
  end_time time not null default '12:00',
  sort_order integer not null default 0,
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);
create unique index if not exists ux_gym_shifts_name
  on gym_shifts (gym_id, lower(name));

-- One table for every authenticated identity: Super Admin, Admin and Member.
create table if not exists users (
  id uuid primary key default gen_random_uuid(),
  gym_id uuid not null references gyms (id) on delete cascade,
  email citext not null,
  password_hash text,
  role text not null check (role in ('super_admin', 'admin', 'member')),
  status text not null default 'invited' check (status in ('active', 'inactive', 'invited')),
  full_name text not null check (length(btrim(full_name)) between 1 and 120),
  phone text,
  avatar_object_key text,
  avatar_version integer not null default 0,
  permissions text[] not null default '{}',
  last_login_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Email is the login identifier, so it is unique across the deployment.
create unique index if not exists ux_users_email on users (email);
create index if not exists ix_users_gym_role on users (gym_id, role);
create index if not exists ix_users_status on users (gym_id, status);

drop trigger if exists trg_users_updated_at on users;
create trigger trg_users_updated_at before update on users
  for each row execute function set_updated_at();

-- Member-specific attributes. Admins/Super Admins have no row here.
create table if not exists member_profiles (
  user_id uuid primary key references users (id) on delete cascade,
  gym_id uuid not null references gyms (id) on delete cascade,
  gender text check (gender in ('male', 'female', 'other')),
  address text,
  date_of_birth date,
  shift_id uuid references gym_shifts (id) on delete set null,
  emergency_contact_name text,
  emergency_contact_phone text,
  joined_on date not null default current_date,
  archived_at timestamptz,
  archived_by uuid references users (id) on delete set null,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists ix_member_profiles_gym on member_profiles (gym_id);
create index if not exists ix_member_profiles_shift on member_profiles (gym_id, shift_id);
create index if not exists ix_member_profiles_joined on member_profiles (gym_id, joined_on);
create index if not exists ix_member_profiles_archived on member_profiles (gym_id, archived_at);

drop trigger if exists trg_member_profiles_updated_at on member_profiles;
create trigger trg_member_profiles_updated_at before update on member_profiles
  for each row execute function set_updated_at();

-- Configurable membership plans (no hard-coded durations).
create table if not exists membership_plans (
  id uuid primary key default gen_random_uuid(),
  gym_id uuid not null references gyms (id) on delete cascade,
  name text not null check (length(btrim(name)) between 1 and 60),
  duration_days integer not null check (duration_days between 1 and 3650),
  price_cents integer not null default 0 check (price_cents >= 0),
  currency text not null default 'INR',
  description text,
  is_active boolean not null default true,
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists ux_membership_plans_name
  on membership_plans (gym_id, lower(name));
create index if not exists ix_membership_plans_active
  on membership_plans (gym_id, is_active, sort_order);

drop trigger if exists trg_membership_plans_updated_at on membership_plans;
create trigger trg_membership_plans_updated_at before update on membership_plans
  for each row execute function set_updated_at();
