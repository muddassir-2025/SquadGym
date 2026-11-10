import type { Request } from 'express';
import { AppError } from '../utils/errors';
import { parsePagination, type PageRequest } from '../utils/pagination';
import type { AuthContext } from '../types/auth';
import type { RequestMeta } from '../services/authService';

/** Typed accessors for values parsed by the `validate` middleware. */

export function body<T>(req: Request): T {
  return (req.validated?.body ?? {}) as T;
}

export function query<T>(req: Request): T {
  return (req.validated?.query ?? {}) as T;
}

export function params<T>(req: Request): T {
  return (req.validated?.params ?? {}) as T;
}

export function auth(req: Request): AuthContext {
  if (!req.auth) throw AppError.unauthorized();
  return req.auth;
}

export function meta(req: Request): RequestMeta {
  return { ip: req.ip ?? null, userAgent: req.header('user-agent') ?? null };
}

export function page(req: Request, defaultPageSize = 20): PageRequest {
  return parsePagination(query<Record<string, unknown>>(req), defaultPageSize);
}
