import { addDays, eachDate, formatShortDate, todayIn, weekdayShort } from '../utils/datetime';
import { AppError } from '../utils/errors';
import { percentChange } from '../utils/pagination';
import type { AuthContext } from '../types/auth';
import {
  captureSnapshot,
  chartSeries,
  dashboardCounts,
  findSnapshotNear,
  occupancySnapshots,
  openDayMap,
  type ChartMetric,
} from '../repositories/dashboardRepo';
import { getOperatingDays, requireGym } from '../repositories/gymRepo';
import { isOpenDay } from './scoring';
import { dateLabel, type DashboardDto, type DayComparison, type StatCard } from './dto';

function comparison(current: number, previous: number): DayComparison {
  return {
    previousValue: previous,
    difference: current - previous,
    percentChange: percentChange(current, previous),
    direction: current > previous ? 'up' : current < previous ? 'down' : 'flat',
  };
}

export async function todayDashboard(auth: AuthContext): Promise<DashboardDto> {
  const gym = await requireGym(auth.gymId);
  const today = todayIn(gym.timezone);
  const yesterday = addDays(today, -1);
  const days = await getOperatingDays(gym.id);
  const openWeekdays = new Set(days.filter((day) => day.is_open).map((day) => day.day_of_week));

  const counts = await dashboardCounts(gym.id, today, yesterday, gym.timezone);

  // "In Gym Now" has no counter history, so it is compared against a *stored*
  // snapshot at the same time yesterday — never a fabricated number.
  const yesterdaySameTime = new Date(Date.now() - 86_400_000);
  const snapshot = await findSnapshotNear(gym.id, yesterdaySameTime, 15);

  const stats = {
    inGymNow: {
      value: counts.inGymNow,
      comparison: snapshot ? comparison(counts.inGymNow, snapshot.active_count) : null,
    } satisfies StatCard,
    totalMembers: {
      value: counts.totalMembers,
      comparison: comparison(counts.totalMembers, counts.yesterdayMembers),
    } satisfies StatCard,
    todayAttendance: {
      value: counts.todayAttendance,
      comparison: comparison(counts.todayAttendance, counts.yesterdayAttendance),
    } satisfies StatCard,
  };

  // Capture an occupancy snapshot for the current bucket (also done by the job).
  const bucketMs = 5 * 60_000;
  const bucket = new Date(Math.floor(Date.now() / bucketMs) * bucketMs);
  captureSnapshot({
    gymId: gym.id,
    activeCount: counts.inGymNow,
    attendanceCount: counts.todayAttendance,
    snapshotDate: today,
    bucketStart: bucket,
  }).catch(() => undefined);

  return {
    gymLocalDate: today,
    gymLocalDateLabel: dateLabel(today),
    gymOpen: isOpenDay(today, openWeekdays),
    timezone: gym.timezone,
    gymName: gym.name,
    stats,
    generatedAt: new Date().toISOString(),
  };
}

export type ChartRange = 'week' | 'month' | 'custom';

export interface ChartDto {
  metric: ChartMetric;
  range: ChartRange;
  from: string;
  to: string;
  rangeLabel: string;
  points: Array<{ date: string; label: string; weekday: string; value: number; isGymOpen: boolean }>;
  total: number;
  previousTotal: number | null;
  comparison: DayComparison | null;
}

const MAX_CHART_DAYS = 366;

export async function attendanceChart(
  auth: AuthContext,
  query: { metric: ChartMetric; range: ChartRange; from?: string; to?: string },
): Promise<ChartDto> {
  const gym = await requireGym(auth.gymId);
  const today = todayIn(gym.timezone);

  let from: string;
  let to: string;
  let rangeLabel: string;

  if (query.range === 'custom') {
    if (!query.from || !query.to) {
      throw AppError.validation({ from: 'Choose a start and end date.' });
    }
    if (query.from > query.to) {
      throw AppError.validation({ to: 'The end date must be after the start date.' });
    }
    const span = eachDate(query.from, query.to).length;
    if (span > MAX_CHART_DAYS) {
      throw AppError.validation({ to: `Choose a range of ${MAX_CHART_DAYS} days or less.` });
    }
    from = query.from;
    to = query.to;
    rangeLabel = `${formatShortDate(from)} – ${formatShortDate(to)}`;
  } else if (query.range === 'week') {
    to = today;
    from = addDays(today, -6);
    rangeLabel = 'Last 7 days';
  } else {
    to = today;
    from = addDays(today, -29);
    rangeLabel = 'Last 30 days';
  }

  const [series, days] = await Promise.all([
    chartSeries({ gymId: gym.id, metric: query.metric, from, to, timezone: gym.timezone }),
    getOperatingDays(gym.id),
  ]);
  const openMap = openDayMap(days, from, to);
  const valueByDate = new Map(series.map((point) => [point.date, point.value]));

  const points = eachDate(from, to).map((date) => ({
    date,
    label: formatShortDate(date),
    weekday: weekdayShort(date),
    value: valueByDate.get(date) ?? 0,
    isGymOpen: openMap.get(date) ?? false,
  }));

  const total = points.reduce((sum, point) => sum + point.value, 0);
  const spanDays = points.length;
  const previousFrom = addDays(from, -spanDays);
  const previousTo = addDays(to, -spanDays);
  const previousSeries = await chartSeries({
    gymId: gym.id,
    metric: query.metric,
    from: previousFrom,
    to: previousTo,
    timezone: gym.timezone,
  });
  const previousTotal = previousSeries.reduce((sum, point) => sum + point.value, 0);

  return {
    metric: query.metric,
    range: query.range,
    from,
    to,
    rangeLabel,
    points,
    total,
    previousTotal,
    comparison: comparison(total, previousTotal),
  };
}

export interface OccupancyPointDto {
  capturedAt: string;
  activeCount: number;
  attendanceCount: number;
}

export async function occupancyHistory(
  auth: AuthContext,
  date: string | undefined,
): Promise<{ date: string; dateLabel: string; points: OccupancyPointDto[] }> {
  const gym = await requireGym(auth.gymId);
  const target = date ?? todayIn(gym.timezone);
  const rows = await occupancySnapshots(gym.id, target);
  return {
    date: target,
    dateLabel: dateLabel(target),
    points: rows.map((row) => ({
      capturedAt: row.captured_at.toISOString(),
      activeCount: row.active_count,
      attendanceCount: row.attendance_count,
    })),
  };
}
