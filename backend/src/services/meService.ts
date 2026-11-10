import {
  addDays,
  endOfMonth,
  isMonth,
  monthLabel,
  startOfMonth,
  startOfWeek,
  todayIn,
} from '../utils/datetime';
import { AppError } from '../utils/errors';
import type { AuthContext } from '../types/auth';
import { getMember } from '../repositories/userRepo';
import { getCurrentMembership } from '../repositories/membershipRepo';
import { getOperatingDays, requireGym } from '../repositories/gymRepo';
import { attendanceDates, memberAttendanceHistory } from '../repositories/attendanceRepo';
import { memberPeriodScore, memberRank } from '../repositories/leaderboardRepo';
import {
  buildAttendanceCalendar,
  computeCurrentStreak,
  computeLongestStreak,
  isOpenDay,
  summarisePeriod,
  type CalendarDay,
} from './scoring';
import { toMemberDto, toMembershipDto, type MemberDto, type MembershipDto } from './dto';
import { todayDashboard } from './dashboardService';

export interface PersonalPeriodDto {
  from: string;
  to: string;
  label: string;
  score: number;
  daysAttended: number;
  openDays: number;
  missedDays: number;
  attendancePercent: number;
}

export interface MemberDashboardDto {
  gymLocalDate: string;
  gymOpen: boolean;
  timezone: string;
  gymName: string;
  stats: Awaited<ReturnType<typeof todayDashboard>>['stats'];
  personal: {
    currentStreak: number;
    longestStreak: number;
    lifetimeScore: number;
    weekly: PersonalPeriodDto;
    monthly: PersonalPeriodDto;
    attendancePercent: number;
    daysAttended: number;
    missedDays: number;
    todayStatus: 'present' | 'absent' | 'closed';
    inGymNow: boolean;
  };
  membership: MembershipDto | null;
  ranking: { week: number; month: number; totalMembers: number };
  calendar: CalendarDay[];
  calendarMonth: string;
  calendarMonthLabel: string;
}

/**
 * Member home (spec §6). Gym-wide stats are shared with the admin dashboard;
 * personal analytics come from the same scoring rules as the leaderboard.
 */
export async function memberDashboard(auth: AuthContext, month?: string): Promise<MemberDashboardDto> {
  const gym = await requireGym(auth.gymId);
  const today = todayIn(gym.timezone);
  const calendarMonth = month && isMonth(month) ? month : today.slice(0, 7);

  const [dashboard, member, days, membership] = await Promise.all([
    todayDashboard(auth),
    getMember(gym.id, auth.userId, today),
    getOperatingDays(gym.id),
    getCurrentMembership(auth.userId),
  ]);
  if (!member) throw AppError.notFound('Member profile');

  const openWeekdays = new Set(days.filter((day) => day.is_open).map((day) => day.day_of_week));
  const historyFrom = addDays(today, -400);
  const dates = await attendanceDates(gym.id, auth.userId, historyFrom, today);
  const attended = new Set(dates);

  const membershipStart = membership?.start_date ?? member.joined_on;
  const membershipEnd = membership?.end_date ?? null;

  const weekStart = startOfWeek(today);
  const monthStart = startOfMonth(today);

  const weeklySummary = summarisePeriod({
    from: weekStart,
    to: today,
    attendedDates: dates,
    openWeekdays,
    membershipStart,
    membershipEnd,
    today,
  });
  const monthlySummary = summarisePeriod({
    from: monthStart,
    to: today,
    attendedDates: dates,
    openWeekdays,
    membershipStart,
    membershipEnd,
    today,
  });

  const [weekRank, monthRank, weekScore, monthScore, recent] = await Promise.all([
    memberRank({ gymId: gym.id, memberId: auth.userId, from: weekStart, to: addDays(weekStart, 6) }),
    memberRank({ gymId: gym.id, memberId: auth.userId, from: monthStart, to: endOfMonth(today) }),
    memberPeriodScore(gym.id, auth.userId, weekStart, today),
    memberPeriodScore(gym.id, auth.userId, monthStart, today),
    memberAttendanceHistory(gym.id, auth.userId, addDays(today, -1), today),
  ]);

  const presentToday = recent.some((row) => row.status === 'active' && row.attendance_date === today);
  const inGymNow = Boolean(
    recent.some(
      (row) =>
        row.status === 'active' &&
        row.attendance_date === today &&
        row.marked_at.getTime() > Date.now() - gym.in_gym_window_minutes * 60_000,
    ),
  );

  return {
    gymLocalDate: today,
    gymOpen: dashboard.gymOpen,
    timezone: gym.timezone,
    gymName: gym.name,
    stats: dashboard.stats,
    personal: {
      currentStreak: computeCurrentStreak({
        attendedDates: dates,
        openWeekdays,
        today,
        membershipStart,
      }),
      longestStreak: computeLongestStreak({ attendedDates: dates, openWeekdays, today }),
      lifetimeScore: dates.length,
      weekly: {
        from: weekStart,
        to: today,
        label: 'This week',
        score: weekScore,
        ...weeklySummary,
      },
      monthly: {
        from: monthStart,
        to: today,
        label: monthLabel(monthStart.slice(0, 7)),
        score: monthScore,
        ...monthlySummary,
      },
      attendancePercent: monthlySummary.attendancePercent,
      daysAttended: monthlySummary.daysAttended,
      missedDays: monthlySummary.missedDays,
      todayStatus: presentToday ? 'present' : dashboard.gymOpen ? 'absent' : 'closed',
      inGymNow,
    },
    membership: membership ? toMembershipDto(membership, gym, today) : null,
    ranking: { week: weekRank.rank, month: monthRank.rank, totalMembers: monthRank.total },
    calendar: buildAttendanceCalendar({
      month: calendarMonth,
      attendedDates: dates,
      openWeekdays,
      today,
      membershipStart,
      membershipEnd,
    }),
    calendarMonth,
    calendarMonthLabel: monthLabel(calendarMonth),
  };
}

export interface MemberCalendarDto {
  month: string;
  monthLabel: string;
  days: CalendarDay[];
  summary: PersonalPeriodDto;
}

export async function memberCalendar(auth: AuthContext, month: string): Promise<MemberCalendarDto> {
  const gym = await requireGym(auth.gymId);
  const today = todayIn(gym.timezone);
  const target = isMonth(month) ? month : today.slice(0, 7);
  const [days, history] = await Promise.all([getOperatingDays(gym.id), getCurrentMembership(auth.userId)]);
  const openWeekdays = new Set(days.filter((day) => day.is_open).map((day) => day.day_of_week));
  const from = addDays(startOfMonth(target), -1);
  const to = endOfMonth(target);
  const dates = await attendanceDates(gym.id, auth.userId, from, to);
  const summary = summarisePeriod({
    from: startOfMonth(target),
    to,
    attendedDates: dates,
    openWeekdays,
    membershipStart: history?.start_date ?? null,
    membershipEnd: history?.end_date ?? null,
    today,
  });

  return {
    month: target,
    monthLabel: monthLabel(target),
    days: buildAttendanceCalendar({
      month: target,
      attendedDates: dates,
      openWeekdays,
      today,
      membershipStart: history?.start_date ?? null,
      membershipEnd: history?.end_date ?? null,
    }),
    summary: {
      from: startOfMonth(target),
      to,
      label: monthLabel(target),
      score: summary.daysAttended,
      ...summary,
    },
  };
}

export interface MemberAttendanceHistoryDto {
  from: string;
  to: string;
  items: Array<{ id: string; date: string; status: string; markedAt: string; markedByName: string | null }>;
}

export async function myAttendanceHistory(
  auth: AuthContext,
  from: string,
  to: string,
): Promise<MemberAttendanceHistoryDto> {
  const gym = await requireGym(auth.gymId);
  const rows = await memberAttendanceHistory(gym.id, auth.userId, from, to);
  return {
    from,
    to,
    items: rows
      .filter((row) => row.status === 'active')
      .map((row) => ({
        id: row.id,
        date: row.attendance_date,
        status: row.status,
        markedAt: row.marked_at.toISOString(),
        markedByName: row.marked_by_name,
      })),
  };
}

export async function myProfile(auth: AuthContext): Promise<MemberDto> {
  const gym = await requireGym(auth.gymId);
  const today = todayIn(gym.timezone);
  const member = await getMember(gym.id, auth.userId, today);
  if (!member) throw AppError.notFound('Member profile');
  return toMemberDto(member, gym, today);
}

export async function myMembership(auth: AuthContext): Promise<MembershipDto | null> {
  const gym = await requireGym(auth.gymId);
  const membership = await getCurrentMembership(auth.userId);
  return membership ? toMembershipDto(membership, gym, todayIn(gym.timezone)) : null;
}

export { isOpenDay };
