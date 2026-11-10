-- 005_security_audit.sql
-- Sessions, invitations, password resets and the administrative audit trail.

-- Refresh tokens are opaque, rotated on every use, and stored hashed.
create table if not exists refresh_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users (id) on delete cascade,
  token_hash text not null unique,
  expires_at timestamptz not null,
  revoked_at timestamptz,
  replaced_by_id uuid references refresh_tokens (id) on delete set null,
  user_agent text,
  ip text,
  created_at timestamptz not null default now()
);

create index if not exists ix_refresh_tokens_user on refresh_tokens (user_id, expires_at desc);
create index if not exists ix_refresh_tokens_expiry on refresh_tokens (expires_at);

create table if not exists password_reset_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users (id) on delete cascade,
  token_hash text not null unique,
  expires_at timestamptz not null,
  used_at timestamptz,
  created_at timestamptz not null default now()
);

create index if not exists ix_password_reset_tokens_user on password_reset_tokens (user_id, expires_at desc);

-- Members and Admins are invited; there is no public registration and no public
-- path to a Super Admin account (spec §2, §3).
create table if not exists invitations (
  id uuid primary key default gen_random_uuid(),
  gym_id uuid not null references gyms (id) on delete cascade,
  email citext not null,
  full_name text not null,
  role text not null check (role in ('admin', 'member')),
  token_hash text not null unique,
  invited_by uuid references users (id) on delete set null,
  member_id uuid references users (id) on delete cascade,
  expires_at timestamptz not null,
  accepted_at timestamptz,
  revoked_at timestamptz,
  created_at timestamptz not null default now()
);

create unique index if not exists ux_invitations_pending_email
  on invitations (gym_id, email)
  where accepted_at is null and revoked_at is null;
create index if not exists ix_invitations_token on invitations (token_hash);
create index if not exists ix_invitations_gym on invitations (gym_id, created_at desc);

-- Append-only audit trail. Nothing in the application deletes rows here.
create table if not exists audit_logs (
  id bigserial primary key,
  gym_id uuid not null references gyms (id) on delete cascade,
  actor_user_id uuid references users (id) on delete set null,
  actor_name text,
  actor_role text,
  action text not null,
  entity_type text,
  entity_id text,
  metadata jsonb not null default '{}'::jsonb,
  ip text,
  user_agent text,
  created_at timestamptz not null default now()
);

create index if not exists ix_audit_logs_gym_time on audit_logs (gym_id, created_at desc);
create index if not exists ix_audit_logs_action on audit_logs (gym_id, action, created_at desc);
create index if not exists ix_audit_logs_entity on audit_logs (entity_type, entity_id);
create index if not exists ix_audit_logs_actor on audit_logs (actor_user_id, created_at desc);
