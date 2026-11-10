-- 001_extensions.sql
-- Required extensions on Neon (both are allow-listed on Neon Postgres).
create extension if not exists "pgcrypto";  -- gen_random_uuid()
create extension if not exists "citext";    -- case-insensitive emails
