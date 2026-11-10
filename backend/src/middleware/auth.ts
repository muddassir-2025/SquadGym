import jwt from 'jsonwebtoken';
import type { NextFunction, Request, Response } from 'express';
import { env } from '../config/env';
import type { Permission, Role } from '../config/constants';
import { AppError } from '../utils/errors';
import { findUserById } from '../repositories/userRepo';

export interface AccessTokenPayload {
  sub: string;
  gymId: string;
  role: Role;
  email: string;
}

export function signAccessToken(payload: AccessTokenPayload): { token: string; expiresIn: number } {
  const token = jwt.sign({ gymId: payload.gymId, role: payload.role, email: payload.email }, env.JWT_ACCESS_SECRET, {
    subject: payload.sub,
    expiresIn: env.ACCESS_TOKEN_TTL_SECONDS,
    issuer: 'squadgym',
    audience: 'squadgym-android',
  });
  return { token, expiresIn: env.ACCESS_TOKEN_TTL_SECONDS };
}

export function verifyAccessToken(token: string): AccessTokenPayload {
  try {
    const decoded = jwt.verify(token, env.JWT_ACCESS_SECRET, {
      issuer: 'squadgym',
      audience: 'squadgym-android',
    }) as jwt.JwtPayload;
    if (!decoded.sub) throw new AppError(401, 'AUTH_TOKEN_INVALID', 'The session token is invalid.');
    return {
      sub: decoded.sub,
      gymId: String(decoded.gymId ?? ''),
      role: decoded.role as Role,
      email: String(decoded.email ?? ''),
    };
  } catch (error) {
    if (error instanceof AppError) throw error;
    if (error instanceof jwt.TokenExpiredError) {
      throw new AppError(401, 'AUTH_TOKEN_EXPIRED', 'Your session has expired. Please sign in again.');
    }
    throw new AppError(401, 'AUTH_TOKEN_INVALID', 'The session token is invalid.');
  }
}

/**
 * Authenticates a request and loads the *current* user row, so a deactivated
 * account or a changed permission takes effect immediately rather than after the
 * access token expires.
 */
export async function requireAuth(req: Request, _res: Response, next: NextFunction): Promise<void> {
  const header = req.headers.authorization;
  if (!header || !header.startsWith('Bearer ')) {
    throw AppError.unauthorized('A bearer token is required.');
  }
  const payload = verifyAccessToken(header.slice('Bearer '.length).trim());
  const user = await findUserById(payload.sub);
  if (!user || user.gym_id !== payload.gymId) {
    throw new AppError(401, 'AUTH_TOKEN_INVALID', 'The session is no longer valid.');
  }
  if (user.status === 'inactive') {
    throw new AppError(403, 'AUTH_ACCOUNT_INACTIVE', 'This account has been deactivated.');
  }
  if (user.status === 'invited') {
    throw new AppError(403, 'AUTH_ACCOUNT_INVITED', 'This account has not been activated yet.');
  }

  req.auth = {
    userId: user.id,
    gymId: user.gym_id,
    email: user.email,
    fullName: user.full_name,
    role: user.role,
    permissions: user.permissions,
    status: user.status,
  };
  req.actor = { userId: user.id, fullName: user.full_name, role: user.role };
  next();
}

export function effectivePermissions(role: Role, permissions: Permission[]): Set<Permission> {
  if (role === 'super_admin') {
    return new Set<Permission>([
      'dashboard.view',
      'members.view',
      'members.manage',
      'attendance.view',
      'attendance.manage',
      'attendance.correct',
      'memberships.view',
      'memberships.manage',
      'leaderboard.view',
      'plans.manage',
      'admins.manage',
      'audit.view',
      'settings.manage',
    ]);
  }
  return new Set(permissions);
}

export function requireRole(...roles: Role[]) {
  return (req: Request, _res: Response, next: NextFunction): void => {
    if (!req.auth) throw AppError.unauthorized();
    if (!roles.includes(req.auth.role)) {
      throw AppError.forbidden(`This action requires one of these roles: ${roles.join(', ')}.`);
    }
    next();
  };
}

export function requirePermission(...permissions: Permission[]) {
  return (req: Request, _res: Response, next: NextFunction): void => {
    if (!req.auth) throw AppError.unauthorized();
    const granted = effectivePermissions(req.auth.role, req.auth.permissions);
    const missing = permissions.filter((permission) => !granted.has(permission));
    if (missing.length > 0) {
      throw AppError.forbidden(`Missing permission: ${missing.join(', ')}.`);
    }
    next();
  };
}

/** Admins and Super Admins only. */
export function requireStaff() {
  return requireRole('admin', 'super_admin');
}

export function requireMember() {
  return requireRole('member');
}
