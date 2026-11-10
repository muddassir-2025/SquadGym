import { env } from '../config/env';
import { query } from '../database/pool';
import { countAttendanceOn, countInGymNow, expireStaleSessions } from '../repositories/attendanceRepo';
import { captureSnapshot } from '../repositories/dashboardRepo';
import { purgeExpiredTokens } from '../repositories/tokenRepo';
import { todayIn } from '../utils/datetime';
import { logger } from '../utils/logger';

/**
 * Background maintenance.
 *
 * Correctness never depends on these jobs (expiry is evaluated at read time), but
 * they keep occupancy history dense enough for the "same time yesterday"
 * comparison and keep stale rows tidy.
 */

interface GymTick {
  id: string;
  timezone: string;
}

async function gyms(): Promise<GymTick[]> {
  return query<GymTick>('select id, timezone from gyms');
}

async function snapshotAllGyms(): Promise<void> {
  const all = await gyms();
  const bucketMs = env.OCCUPANCY_SNAPSHOT_INTERVAL_MINUTES * 60_000;
  const bucket = new Date(Math.floor(Date.now() / bucketMs) * bucketMs);

  for (const gym of all) {
    try {
      const today = todayIn(gym.timezone);
      const [activeCount, attendanceCount] = await Promise.all([
        countInGymNow(gym.id),
        countAttendanceOn(gym.id, today),
      ]);
      await captureSnapshot({
        gymId: gym.id,
        activeCount,
        attendanceCount,
        snapshotDate: today,
        bucketStart: bucket,
      });
      await expireStaleSessions(gym.id);
    } catch (error) {
      logger.warn('Occupancy snapshot failed', { gymId: gym.id, message: (error as Error).message });
    }
  }
}

export interface SchedulerHandle {
  stop: () => void;
}

export function startScheduler(): SchedulerHandle {
  const snapshotInterval = setInterval(() => {
    void snapshotAllGyms();
  }, env.OCCUPANCY_SNAPSHOT_INTERVAL_MINUTES * 60_000);

  const purgeInterval = setInterval(
    () => {
      void purgeExpiredTokens().catch((error: Error) =>
        logger.warn('Token purge failed', { message: error.message }),
      );
    },
    60 * 60 * 1000,
  );

  void snapshotAllGyms();

  logger.info('Scheduler started', {
    occupancyIntervalMinutes: env.OCCUPANCY_SNAPSHOT_INTERVAL_MINUTES,
  });

  return {
    stop: () => {
      clearInterval(snapshotInterval);
      clearInterval(purgeInterval);
    },
  };
}
