import { describe, expect, it } from 'vitest';
import {
  computeCurrentStreak,
  computeLongestStreak,
  computeRenewalDates,
  countOpenDays,
  buildAttendanceCalendar,
  membershipStatusFor,
  openWeekdaysFrom,
  scoreForDates,
  summarisePeriod,
  undoState,
} from '../src/services/scoring';

// Mon..Sat open, Sunday closed (1..6).
const OPEN = new Set([1, 2, 3, 4, 5, 6]);

describe('open day helpers', () => {
  it('derives open weekdays from gym configuration', () => {
    const set = openWeekdaysFrom([
      { day_of_week: 1, is_open: true },
      { day_of_week: 2, is_open: false },
      { day_of_week: 3, is_open: true },
    ]);
    expect([...set]).toEqual([1, 3]);
  });

  it('counts only open days inside a range', () => {
    // 2026-11-09 (Mon) .. 2026-11-15 (Sun) → 6 open days
    expect(countOpenDays('2026-11-09', '2026-11-15', OPEN)).toBe(6);
    expect(countOpenDays('2026-11-09', '2026-11-09', OPEN)).toBe(1);
  });
});

describe('current streak', () => {
  it('counts consecutive open days and skips closed days', () => {
    // Fri 13, Sat 14 attended, Sun 15 closed, today Mon 16 attended.
    const streak = computeCurrentStreak({
      attendedDates: ['2026-11-13', '2026-11-14', '2026-11-16'],
      openWeekdays: OPEN,
      today: '2026-11-16',
    });
    expect(streak).toBe(3);
  });

  it('treats today as pending instead of breaking the streak', () => {
    const streak = computeCurrentStreak({
      attendedDates: ['2026-11-12', '2026-11-13'],
      openWeekdays: OPEN,
      today: '2026-11-16', // Monday, not attended yet; Sat 14 attended? no → 13 is Fri
    });
    // 16 pending, 15 closed, 14 Sat missed → streak is 0 because Saturday was missed.
    expect(streak).toBe(0);
  });

  it('keeps the streak alive when only today is missing', () => {
    const streak = computeCurrentStreak({
      attendedDates: ['2026-11-14'], // Sat
      openWeekdays: OPEN,
      today: '2026-11-16', // Mon, not attended yet
    });
    expect(streak).toBe(1);
  });

  it('stops at the membership start date', () => {
    const streak = computeCurrentStreak({
      attendedDates: ['2026-11-10', '2026-11-11', '2026-11-12'],
      openWeekdays: OPEN,
      today: '2026-11-12',
      membershipStart: '2026-11-10',
    });
    expect(streak).toBe(3);
  });

  it('returns 0 with no attendance', () => {
    expect(computeCurrentStreak({ attendedDates: [], openWeekdays: OPEN, today: '2026-11-16' })).toBe(0);
  });
});

describe('longest streak', () => {
  it('finds the longest historical run', () => {
    const longest = computeLongestStreak({
      attendedDates: ['2026-11-02', '2026-11-03', '2026-11-04', '2026-11-06'],
      openWeekdays: OPEN,
      today: '2026-11-06',
    });
    expect(longest).toBe(3);
  });
});

describe('period summary', () => {
  it('counts attended, open and missed days inside the membership window', () => {
    const summary = summarisePeriod({
      from: '2026-11-09',
      to: '2026-11-15',
      attendedDates: ['2026-11-09', '2026-11-10', '2026-11-11'],
      openWeekdays: OPEN,
      membershipStart: '2026-11-10',
      membershipEnd: '2026-11-14',
      today: '2026-11-30',
    });
    // Window shrinks to 10..14 (Tue..Sat) = 5 open days, 2 attended inside it.
    expect(summary.openDays).toBe(5);
    expect(summary.daysAttended).toBe(2);
    expect(summary.missedDays).toBe(3);
    expect(summary.attendancePercent).toBe(40);
  });

  it('never counts future days', () => {
    const summary = summarisePeriod({
      from: '2026-11-09',
      to: '2026-11-15',
      attendedDates: [],
      openWeekdays: OPEN,
      today: '2026-11-10',
    });
    expect(summary.openDays).toBe(2);
    expect(summary.missedDays).toBe(2);
  });
});

describe('attendance calendar', () => {
  it('classifies each day of a month', () => {
    const days = buildAttendanceCalendar({
      month: '2026-11',
      attendedDates: ['2026-11-09'],
      openWeekdays: OPEN,
      today: '2026-11-11',
      membershipStart: '2026-11-02',
      membershipEnd: '2026-11-30',
    });
    const byDate = new Map(days.map((day) => [day.date, day.status]));
    expect(days).toHaveLength(30);
    expect(byDate.get('2026-11-01')).toBe('before_membership');
    expect(byDate.get('2026-11-08')).toBe('closed'); // Sunday
    expect(byDate.get('2026-11-09')).toBe('attended');
    expect(byDate.get('2026-11-10')).toBe('missed');
    expect(byDate.get('2026-11-12')).toBe('future');
  });
});

describe('scoring', () => {
  it('awards one point per unique attendance day', () => {
    expect(scoreForDates(['2026-11-10', '2026-11-10', '2026-11-11'], '2026-11-01', '2026-11-30')).toBe(2);
    expect(scoreForDates(['2026-10-31', '2026-11-01'], '2026-11-01', '2026-11-30')).toBe(1);
  });
});

describe('renewal date math', () => {
  it('extends from the current end date when still valid', () => {
    const dates = computeRenewalDates({ durationDays: 30, today: '2026-11-10', currentEndDate: '2026-11-20' });
    expect(dates).toEqual({ startDate: '2026-11-21', endDate: '2026-12-20', extendedFromExisting: true });
  });

  it('starts today when the membership already expired', () => {
    const dates = computeRenewalDates({ durationDays: 30, today: '2026-11-10', currentEndDate: '2026-11-01' });
    expect(dates).toEqual({ startDate: '2026-11-10', endDate: '2026-12-09', extendedFromExisting: false });
  });

  it('starts today when there is no membership', () => {
    const dates = computeRenewalDates({ durationDays: 90, today: '2026-11-10', currentEndDate: null });
    expect(dates.startDate).toBe('2026-11-10');
    expect(dates.endDate).toBe('2027-02-07');
  });

  it('extends when the membership ends today', () => {
    const dates = computeRenewalDates({ durationDays: 7, today: '2026-11-10', currentEndDate: '2026-11-10' });
    expect(dates).toEqual({ startDate: '2026-11-11', endDate: '2026-11-17', extendedFromExisting: true });
  });

  it('rejects invalid durations', () => {
    expect(() => computeRenewalDates({ durationDays: 0, today: '2026-11-10' })).toThrow();
  });
});

describe('undo window', () => {
  const expires = new Date('2026-11-10T10:05:00.000Z');

  it('allows undo inside the window', () => {
    const state = undoState(expires, new Date('2026-11-10T10:04:30.000Z'));
    expect(state.canUndo).toBe(true);
    expect(state.secondsRemaining).toBe(30);
  });

  it('rejects undo after the window', () => {
    const state = undoState(expires, new Date('2026-11-10T10:05:01.000Z'));
    expect(state.canUndo).toBe(false);
    expect(state.secondsRemaining).toBe(0);
  });

  it('is disabled for corrections that have no window', () => {
    expect(undoState(new Date()).canUndo).toBe(false);
    expect(undoState(null).canUndo).toBe(false);
  });
});

describe('membership status', () => {
  const today = '2026-11-10';
  it('labels active, expiring, expired and upcoming memberships', () => {
    expect(membershipStatusFor({ endDate: '2026-12-31', startDate: '2026-10-01', today, expiringSoonDays: 7 })).toBe('active');
    expect(membershipStatusFor({ endDate: '2026-11-14', startDate: '2026-10-01', today, expiringSoonDays: 7 })).toBe('expiring');
    expect(membershipStatusFor({ endDate: '2026-11-09', startDate: '2026-10-01', today, expiringSoonDays: 7 })).toBe('expired');
    expect(membershipStatusFor({ endDate: '2026-12-01', startDate: '2026-11-20', today, expiringSoonDays: 7 })).toBe('upcoming');
    expect(membershipStatusFor({ endDate: null, startDate: null, today, expiringSoonDays: 7 })).toBe('none');
  });
});
