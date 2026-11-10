import {
  addDays,
  dayOfWeek,
  diffDays,
  eachDate,
  endOfMonth,
  isIsoDate,
  startOfMonth,
  todayIn,
} from '../utils/datetime';

/**
 * Pure attendance scoring / streak / calendar rules.
 *
 * Keeping this file dependency-free (no DB, no express) makes the business rules
 * unit-testable and keeps them identical everywhere they are used (leaderboard,
 * member dashboard, calendar, corrections).
 */

export type CalendarStatus =
  | 'attended'
  | 'missed'
  | 'closed'
  | 'future'
  | 'before_membership'
  | 'after_membership';

/** Monday-first set of open weekdays (1..7). */
export function openWeekdaysFrom(rows: Array<{ day_of_week: number; is_open: boolean }>): Set<number> {
  return new Set(rows.filter((row) => row.is_open).map((row) => row.day_of_week));
}

export function isOpenDay(date: string, openWeekdays: Set<number>): boolean {
  return openWeekdays.has(dayOfWeek(date));
}

export function countOpenDays(from: string, to: string, openWeekdays: Set<number>): number {
  if (from > to) return 0;
  let count = 0;
  for (const date of eachDate(from, to)) {
    if (isOpenDay(date, openWeekdays)) count += 1;
  }
  return count;
}

interface StreakInput {
  attendedDates: Iterable<string>;
  openWeekdays: Set<number>;
  today: string;
  membershipStart?: string | null;
  /** Look-back cap; 400 days is more than enough for a "longest streak". */
  lookbackDays?: number;
}

/**
 * Current streak in *open gym days*.
 *
 * Today is treated as pending: if the gym is open today and the member has not
 * checked in yet, the streak is not broken until the day ends. Closed days never
 * break a streak.
 */
export function computeCurrentStreak(input: StreakInput): number {
  const attended = new Set(input.attendedDates);
  const lookback = input.lookbackDays ?? 400;
  let cursor = input.today;
  let streak = 0;
  let pendingToday = true;

  for (let step = 0; step < lookback; step += 1) {
    if (input.membershipStart && cursor < input.membershipStart) break;
    if (!isOpenDay(cursor, input.openWeekdays)) {
      cursor = addDays(cursor, -1);
      continue;
    }
    if (attended.has(cursor)) {
      streak += 1;
      pendingToday = false;
      cursor = addDays(cursor, -1);
      continue;
    }
    if (pendingToday) {
      // The gym is open today but the member has not arrived yet: not a break.
      pendingToday = false;
      cursor = addDays(cursor, -1);
      continue;
    }
    break;
  }
  return streak;
}

/** Longest run of consecutive attended open days. */
export function computeLongestStreak(input: StreakInput): number {
  const attended = new Set(input.attendedDates);
  if (attended.size === 0) return 0;
  const lookback = input.lookbackDays ?? 400;
  const start = addDays(input.today, -lookback);

  let longest = 0;
  let current = 0;
  let pendingToday = input.today;

  for (const date of eachDate(start, input.today)) {
    if (!isOpenDay(date, input.openWeekdays)) continue;
    if (attended.has(date)) {
      current += 1;
      longest = Math.max(longest, current);
    } else if (date === pendingToday) {
      // Treat today as pending, do not reset.
      continue;
    } else {
      current = 0;
    }
  }
  return longest;
}

export interface CalendarDay {
  date: string;
  status: CalendarStatus;
  isOpen: boolean;
}

export interface CalendarInput {
  month: string;
  attendedDates: Iterable<string>;
  openWeekdays: Set<number>;
  today: string;
  membershipStart?: string | null;
  membershipEnd?: string | null;
}

/**
 * Attendance calendar for one month (spec §6.B): green = attended, muted = missed
 * an open day, grey = gym closed, future dates are not counted.
 */
export function buildAttendanceCalendar(input: CalendarInput): CalendarDay[] {
  const attended = new Set(input.attendedDates);
  const start = startOfMonth(`${input.month}-01`);
  const end = endOfMonth(start);
  const days: CalendarDay[] = [];

  for (const date of eachDate(start, end)) {
    const isOpen = isOpenDay(date, input.openWeekdays);
    let status: CalendarStatus;

    if (date > input.today) status = 'future';
    else if (input.membershipStart && date < input.membershipStart) status = 'before_membership';
    else if (input.membershipEnd && date > input.membershipEnd) status = 'after_membership';
    else if (attended.has(date)) status = 'attended';
    else if (!isOpen) status = 'closed';
    else status = 'missed';

    days.push({ date, status, isOpen });
  }
  return days;
}

export interface PeriodSummary {
  daysAttended: number;
  openDays: number;
  missedDays: number;
  attendancePercent: number;
}

/**
 * Missed days are only counted when the gym was open AND the member had an active
 * membership on that date (spec §6.B).
 */
export function summarisePeriod(input: {
  from: string;
  to: string;
  attendedDates: Iterable<string>;
  openWeekdays: Set<number>;
  membershipStart?: string | null;
  membershipEnd?: string | null;
  today: string;
}): PeriodSummary {
  const attended = new Set(input.attendedDates);
  const upperBound = input.to > input.today ? input.today : input.to;
  const effectiveStart =
    input.membershipStart && input.membershipStart > input.from ? input.membershipStart : input.from;
  const effectiveEnd =
    input.membershipEnd && input.membershipEnd < upperBound ? input.membershipEnd : upperBound;

  if (effectiveStart > effectiveEnd) {
    return { daysAttended: 0, openDays: 0, missedDays: 0, attendancePercent: 0 };
  }

  const openDays = countOpenDays(effectiveStart, effectiveEnd, input.openWeekdays);
  let daysAttended = 0;
  for (const date of attended) {
    if (date >= effectiveStart && date <= effectiveEnd) daysAttended += 1;
  }
  const missedDays = Math.max(0, openDays - daysAttended);
  const attendancePercent = openDays === 0 ? 0 : Math.round((daysAttended / openDays) * 1000) / 10;

  return { daysAttended, openDays, missedDays, attendancePercent };
}

/** One point per unique attendance day (spec §4.G). */
export function scoreForDates(dates: Iterable<string>, from: string, to: string): number {
  const unique = new Set<string>();
  for (const date of dates) {
    if (date >= from && date <= to) unique.add(date);
  }
  return unique.size;
}

export interface RenewalDates {
  startDate: string;
  endDate: string;
  extendedFromExisting: boolean;
}

/**
 * Renewal date math (spec §5):
 *  * still-valid membership → new term starts the day after the current end date;
 *  * expired (or missing) membership → new term starts on the renewal date.
 */
export function computeRenewalDates(input: {
  durationDays: number;
  today: string;
  currentEndDate?: string | null;
}): RenewalDates {
  const { durationDays, today, currentEndDate } = input;
  if (!Number.isInteger(durationDays) || durationDays < 1) {
    throw new Error('durationDays must be a positive integer');
  }
  const extended = Boolean(currentEndDate && currentEndDate >= today);
  const startDate = extended ? addDays(currentEndDate as string, 1) : today;
  const endDate = addDays(startDate, durationDays - 1);
  return { startDate, endDate, extendedFromExisting: extended };
}

export interface UndoState {
  canUndo: boolean;
  secondsRemaining: number;
}

/** Undo windows are absolute server-side timestamps, never client timers. */
export function undoState(expiresAt: Date | string | null | undefined, now: Date = new Date()): UndoState {
  if (!expiresAt) return { canUndo: false, secondsRemaining: 0 };
  const target = typeof expiresAt === 'string' ? new Date(expiresAt) : expiresAt;
  const remaining = Math.floor((target.getTime() - now.getTime()) / 1000);
  return { canUndo: remaining > 0, secondsRemaining: Math.max(0, remaining) };
}

/** Membership status label shared by every member-facing surface. */
export function membershipStatusFor(input: {
  endDate?: string | null;
  startDate?: string | null;
  today: string;
  expiringSoonDays: number;
}): 'active' | 'expiring' | 'expired' | 'upcoming' | 'none' {
  const { endDate, startDate, today, expiringSoonDays } = input;
  if (!endDate) return 'none';
  if (endDate < today) return 'expired';
  if (startDate && startDate > today) return 'upcoming';
  const remaining = diffDays(today, endDate);
  if (remaining <= expiringSoonDays) return 'expiring';
  return 'active';
}

/** Gym-local "today" re-exported for convenience in services. */
export { todayIn, isIsoDate };
