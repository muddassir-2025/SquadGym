/* Minimal structured logger — keeps Render logs readable and greppable. */

type Level = 'debug' | 'info' | 'warn' | 'error';

const LEVEL_ORDER: Record<Level, number> = { debug: 10, info: 20, warn: 30, error: 40 };
const MIN_LEVEL: Level = (process.env.LOG_LEVEL as Level | undefined) ?? 'info';

function shouldLog(level: Level): boolean {
  return LEVEL_ORDER[level] >= LEVEL_ORDER[MIN_LEVEL];
}

function emit(level: Level, message: string, meta?: unknown): void {
  if (!shouldLog(level)) return;
  const line = {
    time: new Date().toISOString(),
    level,
    message,
    ...(meta && typeof meta === 'object' ? { meta } : meta !== undefined ? { meta } : {}),
  };
  const serialized = JSON.stringify(line);
  if (level === 'error') console.error(serialized);
  else if (level === 'warn') console.warn(serialized);
  else console.log(serialized);
}

export const logger = {
  debug: (message: string, meta?: unknown) => emit('debug', message, meta),
  info: (message: string, meta?: unknown) => emit('info', message, meta),
  warn: (message: string, meta?: unknown) => emit('warn', message, meta),
  error: (message: string, meta?: unknown) => emit('error', message, meta),
};

/** Never log secrets: redact obvious keys before writing. */
export function redact(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(redact);
  if (value && typeof value === 'object') {
    const clone: Record<string, unknown> = {};
    for (const [key, entry] of Object.entries(value as Record<string, unknown>)) {
      if (/pass|secret|token|authorization|apikey|api_key/i.test(key)) clone[key] = '[redacted]';
      else clone[key] = redact(entry);
    }
    return clone;
  }
  return value;
}
