import { describe, expect, it } from 'vitest';
import {
  addDays,
  addMonths,
  dateInZone,
  dayOfWeek,
  diffDays,
  eachDate,
  endOfMonth,
  endOfWeek,
  formatLongDate,
  isIsoDate,
  isMonth,
  monthDays,
  pad2,
  startOfMonth,
  startOfWeek,
  timeInZone,
  timeToMinutes,
  toIsoDate,
  todayIn,
} from '../src/utils/datetime';

describe('ISO date helpers', () => {
  it('accepts only real calendar dates', () => {
    expect(isIsoDate('2026-11-10')).toBe(true);
    expect(isIsoDate('2026-02-30')).toBe(false);
    expect(isIsoDate('2026-13-01')).toBe(false);
    expect(isIsoDate('10-11-2026')).toBe(false);
  });

  it('adds and subtracts days across month and year boundaries', () => {
    expect(addDays('2026-01-31', 1)).toBe('2026-02-01');
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
    expect(addDays('2024-03-01', -1)).toBe('2024-02-29');
  });

  it('adds months clamping to the end of shorter months', () => {
    expect(addMonths('2026-01-31', 1)).toBe('2026-02-28');
    expect(addMonths('2026-11-10', 3)).toBe('2027-02-10');
    expect(addMonths('2026-01-15', -1)).toBe('2025-12-15');
  });

  it('computes ISO weekdays (Monday = 1, Sunday = 7)', () => {
    expect(dayOfWeek('2026-11-09')).toBe(1);
    expect(dayOfWeek('2026-11-10')).toBe(2);
    expect(dayOfWeek('2026-11-15')).toBe(7);
  });

  it('computes week and month bounds', () => {
    expect(startOfWeek('2026-11-10')).toBe('2026-11-09');
    expect(endOfWeek('2026-11-10')).toBe('2026-11-15');
    expect(startOfMonth('2026-11-10')).toBe('2026-11-01');
    expect(endOfMonth('2026-11-10')).toBe('2026-11-30');
    expect(endOfMonth('2026-02-10')).toBe('2026-02-28');
  });

  it('builds inclusive date ranges', () => {
    expect(eachDate('2026-11-08', '2026-11-10')).toEqual(['2026-11-08', '2026-11-09', '2026-11-10']);
    expect(eachDate('2026-11-10', '2026-11-10')).toEqual(['2026-11-10']);
    expect(monthDays('2026-02')).toHaveLength(28);
  });

  it('counts day differences', () => {
    expect(diffDays('2026-11-01', '2026-11-10')).toBe(9);
    expect(diffDays('2026-11-10', '2026-11-01')).toBe(-9);
  });

  it('formats labels without timezone drift', () => {
    expect(formatLongDate('2026-11-10')).toBe('Tue, 10 Nov 2026');
    expect(toIsoDate(new Date('2026-11-10T23:30:00.000Z'))).toBe('2026-11-10');
    expect(pad2(7)).toBe('07');
  });

  it('parses HH:MM into minutes', () => {
    expect(timeToMinutes('06:30')).toBe(390);
    expect(timeToMinutes('23:59')).toBe(1439);
  });

  it('validates YYYY-MM', () => {
    expect(isMonth('2026-11')).toBe(true);
    expect(isMonth('2026-13')).toBe(false);
    expect(isMonth('2026-1')).toBe(false);
  });
});

describe('gym timezone handling', () => {
  it('maps late-UTC instants to the next gym-local day in Asia/Kolkata', () => {
    const instant = new Date('2026-11-10T19:00:00.000Z'); // 00:30 IST on the 11th
    expect(dateInZone(instant, 'Asia/Kolkata')).toBe('2026-11-11');
    expect(timeInZone(instant, 'Asia/Kolkata')).toBe('00:30');
    expect(todayIn('Asia/Kolkata', instant)).toBe('2026-11-11');
    // Same instant is still the 10th in UTC.
    expect(dateInZone(instant, 'UTC')).toBe('2026-11-10');
  });

  it('handles a US timezone behind UTC', () => {
    const instant = new Date('2026-11-11T02:00:00.000Z'); // 21:00 EST on the 10th
    expect(dateInZone(instant, 'America/New_York')).toBe('2026-11-10');
  });

  it('handles IST offset used by the default gym timezone', () => {
    const instant = new Date('2026-11-10T13:18:41Z');
    expect(todayIn('Asia/Kolkata', instant)).toBe('2026-11-10');
  });
});
