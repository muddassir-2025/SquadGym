/**
 * Gym-timezone date utilities.
 *
 * All persisted timestamps are UTC instants. Every "day" in this product
 * (attendance day, leaderboard period, expiry, streaks) is a *gym-local calendar
 * day*. These helpers keep that distinction in one place so no repository has to
 * hand-roll timezone math.
 */

const MS_PER_DAY = 86_400_000;

export function pad2(value: number): string {
  return value < 10 ? `0${value}` : String(value);
}

export function isIsoDate(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const time = Date.parse(`${value}T00:00:00.000Z`);
  if (Number.isNaN(time)) return false;
  return new Date(time).toISOString().slice(0, 10) === value;
}

export function parseIsoDate(iso: string): Date {
  if (!isIsoDate(iso)) throw new Error(`Invalid ISO date: ${iso}`);
  return new Date(`${iso}T00:00:00.000Z`);
}

export function toIsoDate(date: Date): string {
  return `${date.getUTCFullYear()}-${pad2(date.getUTCMonth() + 1)}-${pad2(date.getUTCDate())}`;
}

export function addDays(iso: string, days: number): string {
  return toIsoDate(new Date(parseIsoDate(iso).getTime() + days * MS_PER_DAY));
}

export function addMonths(iso: string, months: number): string {
  const date = parseIsoDate(iso);
  const year = date.getUTCFullYear();
  const month = date.getUTCMonth() + months;
  const day = date.getUTCDate();
  const target = new Date(Date.UTC(year, month, 1));
  const daysInTarget = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  target.setUTCDate(Math.min(day, daysInTarget));
  return toIsoDate(target);
}

export function diffDays(fromIso: string, toIso: string): number {
  return Math.round((parseIsoDate(toIso).getTime() - parseIsoDate(fromIso).getTime()) / MS_PER_DAY);
}

/** ISO weekday: Monday = 1 … Sunday = 7. */
export function dayOfWeek(iso: string): number {
  const day = parseIsoDate(iso).getUTCDay();
  return day === 0 ? 7 : day;
}

export function startOfWeek(iso: string): string {
  return addDays(iso, 1 - dayOfWeek(iso));
}

export function endOfWeek(iso: string): string {
  return addDays(startOfWeek(iso), 6);
}

export function startOfMonth(iso: string): string {
  return `${iso.slice(0, 7)}-01`;
}

export function endOfMonth(iso: string): string {
  return addDays(addMonths(startOfMonth(iso), 1), -1);
}

/** All dates between `from` and `to`, inclusive. */
export function eachDate(fromIso: string, toIso: string): string[] {
  const dates: string[] = [];
  const total = diffDays(fromIso, toIso);
  for (let index = 0; index <= total; index += 1) {
    dates.push(addDays(fromIso, index));
  }
  return dates;
}

const formatterCache = new Map<string, Intl.DateTimeFormat>();

function zonedFormatter(timeZone: string): Intl.DateTimeFormat {
  const cached = formatterCache.get(timeZone);
  if (cached) return cached;
  const formatter = new Intl.DateTimeFormat('en-GB', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });
  formatterCache.set(timeZone, formatter);
  return formatter;
}

interface ZonedParts {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
  second: number;
}

export function zonedParts(instant: Date, timeZone: string): ZonedParts {
  const parts = zonedFormatter(timeZone).formatToParts(instant);
  const read = (type: Intl.DateTimeFormatPartTypes): number => {
    const found = parts.find((part) => part.type === type);
    return found ? Number(found.value) : 0;
  };
  return {
    year: read('year'),
    month: read('month'),
    day: read('day'),
    hour: read('hour') % 24,
    minute: read('minute'),
    second: read('second'),
  };
}

/** The gym-local calendar date (`YYYY-MM-DD`) for an instant. */
export function dateInZone(instant: Date, timeZone: string): string {
  const parts = zonedParts(instant, timeZone);
  return `${parts.year}-${pad2(parts.month)}-${pad2(parts.day)}`;
}

export function timeInZone(instant: Date, timeZone: string): string {
  const parts = zonedParts(instant, timeZone);
  return `${pad2(parts.hour)}:${pad2(parts.minute)}`;
}

export function todayIn(timeZone: string, now: Date = new Date()): string {
  return dateInZone(now, timeZone);
}

/** `HH:MM` → minutes since midnight. */
export function timeToMinutes(value: string): number {
  const [hours, minutes] = value.split(':');
  return Number(hours ?? 0) * 60 + Number(minutes ?? 0);
}

export function minutesToTime(minutes: number): string {
  const normalized = ((minutes % 1440) + 1440) % 1440;
  return `${pad2(Math.floor(normalized / 60))}:${pad2(normalized % 60)}`;
}

/** Whole seconds until `expiresAt`, floored at 0. */
export function secondsUntil(expiresAt: Date | string, now: Date = new Date()): number {
  const target = typeof expiresAt === 'string' ? new Date(expiresAt) : expiresAt;
  return Math.max(0, Math.floor((target.getTime() - now.getTime()) / 1000));
}

export function addSeconds(instant: Date, seconds: number): Date {
  return new Date(instant.getTime() + seconds * 1000);
}

export function addMinutes(instant: Date, minutes: number): Date {
  return new Date(instant.getTime() + minutes * 60_000);
}

export function addHours(instant: Date, hours: number): Date {
  return new Date(instant.getTime() + hours * 3_600_000);
}

/** Inclusive list of dates in a month (`YYYY-MM`). */
export function monthDays(month: string): string[] {
  const start = `${month}-01`;
  return eachDate(start, endOfMonth(start));
}

const SHORT_MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const SHORT_WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

export function formatShortDate(iso: string): string {
  const date = parseIsoDate(iso);
  return `${date.getUTCDate()} ${SHORT_MONTHS[date.getUTCMonth()]}`;
}

export function formatLongDate(iso: string): string {
  const date = parseIsoDate(iso);
  return `${SHORT_WEEKDAYS[dayOfWeek(iso) - 1]}, ${date.getUTCDate()} ${SHORT_MONTHS[date.getUTCMonth()]} ${date.getUTCFullYear()}`;
}

export function weekdayShort(iso: string): string {
  return SHORT_WEEKDAYS[dayOfWeek(iso) - 1] ?? '';
}

export function monthLabel(month: string): string {
  const [year, monthValue] = month.split('-');
  const index = Number(monthValue) - 1;
  return `${SHORT_MONTHS[index] ?? ''} ${year}`;
}

export function isMonth(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-(0[1-9]|1[0-2])$/.test(value);
}
