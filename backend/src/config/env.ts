import 'dotenv/config';
import { z } from 'zod';

/**
 * Environment loading + validation.
 *
 * Everything that touches secrets is read here exactly once. The rest of the
 * codebase imports `env` so a missing/misconfigured variable fails fast at boot
 * instead of at the first request.
 */

const booleanish = z.preprocess((value) => {
  if (typeof value === 'string') {
    return ['1', 'true', 'yes', 'on'].includes(value.trim().toLowerCase());
  }
  return value;
}, z.boolean());

const optionalString = z
  .string()
  .optional()
  .transform((value) => (value && value.trim().length > 0 ? value.trim() : undefined));

const DEV_ACCESS_SECRET = 'dev-only-access-secret-change-me-0123456789abcdef';
const DEV_REFRESH_SECRET = 'dev-only-refresh-secret-change-me-0123456789abcdef';

const schema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  PORT: z.coerce.number().int().positive().default(8080),
  API_BASE_URL: z.string().url().default('http://localhost:8080'),
  CORS_ORIGINS: z.string().default('*'),

  DATABASE_URL: z
    .string()
    .min(1)
    .default('postgresql://postgres:postgres@localhost:5432/squadgym'),

  JWT_ACCESS_SECRET: z.string().min(32).default(DEV_ACCESS_SECRET),
  JWT_REFRESH_SECRET: z.string().min(32).default(DEV_REFRESH_SECRET),
  ACCESS_TOKEN_TTL_SECONDS: z.coerce.number().int().positive().default(900),
  REFRESH_TOKEN_TTL_SECONDS: z.coerce.number().int().positive().default(2_592_000),
  BCRYPT_ROUNDS: z.coerce.number().int().min(8).max(15).default(12),

  SUPER_ADMIN_EMAIL: z.string().email().default('owner@squadgym.app'),
  SUPER_ADMIN_PASSWORD: z.string().min(8).default('ChangeMe-Strong-Passw0rd!'),
  SUPER_ADMIN_NAME: z.string().default('Gym Owner'),
  GYM_NAME: z.string().default('Squad Gym'),
  GYM_TIMEZONE: z.string().default('Asia/Kolkata'),

  STORAGE_DRIVER: z.enum(['local', 's3']).default('local'),
  LOCAL_UPLOAD_DIR: z.string().default('var/uploads'),
  S3_ENDPOINT: optionalString,
  S3_REGION: z.string().default('us-east-1'),
  S3_BUCKET: optionalString,
  S3_ACCESS_KEY_ID: optionalString,
  S3_SECRET_ACCESS_KEY: optionalString,
  S3_FORCE_PATH_STYLE: booleanish.default(true),
  MEDIA_PUBLIC_BASE_URL: optionalString,
  MEDIA_URL_TTL_SECONDS: z.coerce.number().int().positive().default(900),
  MAX_UPLOAD_BYTES: z.coerce.number().int().positive().default(5_242_880),

  SMTP_HOST: optionalString,
  SMTP_PORT: z.coerce.number().int().positive().default(587),
  SMTP_SECURE: booleanish.default(false),
  SMTP_USER: optionalString,
  SMTP_PASS: optionalString,
  MAIL_FROM: z.string().default('Squad Gym <no-reply@squadgym.app>'),

  DEFAULT_IN_GYM_WINDOW_MINUTES: z.coerce.number().int().min(5).max(480).default(60),
  DEFAULT_EXPIRING_SOON_DAYS: z.coerce.number().int().min(1).max(120).default(7),
  DEFAULT_ARCHIVE_AFTER_DAYS: z.coerce.number().int().min(1).max(3650).default(90),
  DEFAULT_ATTENDANCE_UNDO_SECONDS: z.coerce.number().int().min(0).max(86_400).default(300),
  DEFAULT_RENEWAL_UNDO_SECONDS: z.coerce.number().int().min(0).max(86_400).default(300),
  OCCUPANCY_SNAPSHOT_INTERVAL_MINUTES: z.coerce.number().int().min(1).max(60).default(5),

  EXPOSE_DEV_TOKENS: booleanish.default(false),
});

export type Env = z.infer<typeof schema> & {
  isProduction: boolean;
  isTest: boolean;
  corsOrigins: string[];
};

function validateTimeZone(timeZone: string): string {
  try {
    new Intl.DateTimeFormat('en-US', { timeZone }).format(new Date());
    return timeZone;
  } catch {
    throw new Error(`GYM_TIMEZONE "${timeZone}" is not a valid IANA timezone`);
  }
}

function build(): Env {
  const parsed = schema.safeParse(process.env);
  if (!parsed.success) {
    const issues = parsed.error.issues
      .map((issue) => `${issue.path.join('.') || '(root)'}: ${issue.message}`)
      .join('\n  - ');
    throw new Error(`Invalid environment configuration:\n  - ${issues}`);
  }

  const value = parsed.data;
  const isProduction = value.NODE_ENV === 'production';

  if (isProduction) {
    const problems: string[] = [];
    if (value.JWT_ACCESS_SECRET === DEV_ACCESS_SECRET) problems.push('JWT_ACCESS_SECRET must be set');
    if (value.JWT_REFRESH_SECRET === DEV_REFRESH_SECRET) problems.push('JWT_REFRESH_SECRET must be set');
    if (!/^postgres(ql)?:\/\//.test(value.DATABASE_URL)) problems.push('DATABASE_URL must be a Postgres URL');
    if (value.SUPER_ADMIN_EMAIL === 'owner@squadgym.app') {
      problems.push('SUPER_ADMIN_EMAIL must be a real address');
    }
    if (value.SUPER_ADMIN_PASSWORD === 'ChangeMe-Strong-Passw0rd!') {
      problems.push('SUPER_ADMIN_PASSWORD must be changed');
    }
    if (value.EXPOSE_DEV_TOKENS) problems.push('EXPOSE_DEV_TOKENS must be false in production');
    if (value.STORAGE_DRIVER === 's3' && !value.S3_BUCKET) problems.push('S3_BUCKET is required for s3 storage');
    if (problems.length > 0) {
      throw new Error(`Refusing to start in production:\n  - ${problems.join('\n  - ')}`);
    }
  }

  return {
    ...value,
    isProduction,
    isTest: value.NODE_ENV === 'test',
    corsOrigins: value.CORS_ORIGINS.split(',')
      .map((origin) => origin.trim())
      .filter(Boolean),
  };
}

export const env: Env = build();
export const gymTimezone = validateTimeZone(env.GYM_TIMEZONE);
