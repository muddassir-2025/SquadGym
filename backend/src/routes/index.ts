import { Router } from 'express';
import { authRouter, invitationRouter } from './auth';
import { membersRouter } from './members';
import { attendanceRouter } from './attendance';
import { membershipsRouter } from './memberships';
import { dashboardRouter, leaderboardRouter, plansRouter } from './dashboard';
import { meRouter } from './me';
import { superAdminRouter } from './superadmin';
import { mediaRouter } from './media';

/**
 * API surface, versioned under `/api/v1`.
 *
 *   /auth            sign in / refresh / logout / password reset
 *   /invitations     invitation preview + acceptance (unauthenticated)
 *   /me              member-only personal dashboard, calendar, attendance
 *   /dashboard       gym statistics, chart, occupancy
 *   /leaderboard     weekly / monthly rankings
 *   /members         member CRUD, photos, renewals, membership history
 *   /attendance      manual attendance dashboard, mark, undo, corrections, sessions
 *   /memberships     renewal undo
 *   /plans           membership plans
 *   /superadmin      admins, audit log, gym configuration
 *   /media           authenticated private media
 */
export const apiRouter = Router();

apiRouter.use('/auth', authRouter);
apiRouter.use('/invitations', invitationRouter);
apiRouter.use('/me', meRouter);
apiRouter.use('/dashboard', dashboardRouter);
apiRouter.use('/leaderboard', leaderboardRouter);
apiRouter.use('/members', membersRouter);
apiRouter.use('/attendance', attendanceRouter);
apiRouter.use('/memberships', membershipsRouter);
apiRouter.use('/plans', plansRouter);
apiRouter.use('/superadmin', superAdminRouter);
apiRouter.use('/media', mediaRouter);
