import {
  addDays,
  endOfMonth,
  endOfWeek,
  formatShortDate,
  monthLabel,
  startOfMonth,
  startOfWeek,
  todayIn,
} from '../utils/datetime';
import type { AuthContext } from '../types/auth';
import {
  attendanceDatesByMembers,
  memberPeriodScore,
  memberRank,
  rankedMembers,
} from '../repositories/leaderboardRepo';
import { getOperatingDays, requireGym } from '../repositories/gymRepo';
import { findUserById } from '../repositories/userRepo';
import { computeCurrentStreak } from './scoring';
import { mediaUrl } from './dto';

export type LeaderboardPeriod = 'week' | 'month';

export interface LeaderboardEntryDto {
  rank: number;
  memberId: string;
  fullName: string;
  photoUrl: string | null;
  imageVersion: number;
  score: number;
  currentStreak: number;
  isMe: boolean;
}

export interface LeaderboardDto {
  period: LeaderboardPeriod;
  periodStart: string;
  periodEnd: string;
  periodLabel: string;
  timezone: string;
  entries: LeaderboardEntryDto[];
  me: (LeaderboardEntryDto & { totalMembers: number }) | null;
  meta: { page: number; pageSize: number; total: number; totalPages: number; hasNext: boolean; hasPrevious: boolean };
}

export function periodBounds(period: LeaderboardPeriod, anchor: string): { start: string; end: string } {
  if (period === 'week') return { start: startOfWeek(anchor), end: endOfWeek(anchor) };
  return { start: startOfMonth(anchor), end: endOfMonth(anchor) };
}

export async function leaderboard(
  auth: AuthContext,
  query: { period: LeaderboardPeriod; anchor?: string },
  page: { page: number; pageSize: number; offset: number },
): Promise<LeaderboardDto> {
  const gym = await requireGym(auth.gymId);
  const today = todayIn(gym.timezone);
  const anchor = query.anchor ?? today;
  const { start, end } = periodBounds(query.period, anchor);

  const [{ items, total }, days] = await Promise.all([
    rankedMembers({ gymId: gym.id, from: start, to: end, limit: page.pageSize, offset: page.offset }),
    getOperatingDays(gym.id),
  ]);

  const openWeekdays = new Set(days.filter((day) => day.is_open).map((day) => day.day_of_week));
  const memberIds = items.map((item) => item.id);
  const historyFrom = addDays(today, -400);
  const history = await attendanceDatesByMembers(gym.id, memberIds, historyFrom, today);

  const entries = items.map((item) => ({
    rank: item.rank,
    memberId: item.id,
    fullName: item.full_name,
    photoUrl: mediaUrl(item.avatar_object_key, item.avatar_version),
    imageVersion: item.avatar_version,
    score: item.score,
    currentStreak: computeCurrentStreak({
      attendedDates: history.get(item.id) ?? [],
      openWeekdays,
      today,
      membershipStart: item.joined_on,
    }),
    isMe: item.id === auth.userId,
  }));

  let me: (LeaderboardEntryDto & { totalMembers: number }) | null = null;
  if (auth.role === 'member') {
    const rank = await memberRank({ gymId: gym.id, memberId: auth.userId, from: start, to: end });
    const [mine, myHistory, user] = await Promise.all([
      memberPeriodScore(gym.id, auth.userId, start, end),
      attendanceDatesByMembers(gym.id, [auth.userId], historyFrom, today),
      findUserById(auth.userId),
    ]);
    me = {
      rank: rank.rank,
      memberId: auth.userId,
      fullName: auth.fullName,
      photoUrl: user ? mediaUrl(user.avatar_object_key, user.avatar_version) : null,
      imageVersion: user?.avatar_version ?? 0,
      score: mine,
      currentStreak: computeCurrentStreak({
        attendedDates: myHistory.get(auth.userId) ?? [],
        openWeekdays,
        today,
      }),
      isMe: true,
      totalMembers: rank.total,
    };
  }

  return {
    period: query.period,
    periodStart: start,
    periodEnd: end,
    periodLabel:
      query.period === 'week'
        ? `${formatShortDate(start)} – ${formatShortDate(end)}`
        : monthLabel(start.slice(0, 7)),
    timezone: gym.timezone,
    entries,
    me,
    meta: {
      page: page.page,
      pageSize: page.pageSize,
      total,
      totalPages: Math.max(1, Math.ceil(total / page.pageSize)),
      hasNext: page.page * page.pageSize < total,
      hasPrevious: page.page > 1,
    },
  };
}
