import type { NextFunction, Request, Response } from 'express';
import type { ZodTypeAny } from 'zod';
import { AppError } from '../utils/errors';

/** Flattens a ZodError into `{ field: message }` so the app can highlight inputs. */
export function formatIssues(error: unknown): Record<string, string> {
  const issues: Record<string, string> = {};
  if (error && typeof error === 'object' && 'issues' in error) {
    const list = (error as { issues: Array<{ path: Array<string | number>; message: string }> }).issues;
    for (const issue of list) {
      const key = issue.path.length > 0 ? issue.path.join('.') : 'body';
      if (!issues[key]) issues[key] = issue.message;
    }
  }
  return issues;
}

export function parseOrThrow<T extends ZodTypeAny>(schema: T, value: unknown): ReturnType<T['parse']> {
  const result = schema.safeParse(value);
  if (!result.success) {
    throw AppError.validation(formatIssues(result.error));
  }
  return result.data;
}

/**
 * Validates and stores the parsed value on `req.validated[source]`.
 * We never mutate `req.query` (Express 5 exposes it as a getter).
 */
export function validate<T extends ZodTypeAny>(schema: T, source: 'body' | 'query' | 'params') {
  return (req: Request, _res: Response, next: NextFunction): void => {
    const result = schema.safeParse(req[source]);
    if (!result.success) {
      throw AppError.validation(formatIssues(result.error));
    }
    req.validated ??= {};
    req.validated[source] = result.data as unknown;
    next();
  };
}
