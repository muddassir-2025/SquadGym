import type { NextFunction, Request, Response } from 'express';
import { MulterError } from 'multer';
import { ZodError } from 'zod';
import { env } from '../config/env';
import { AppError, type ErrorBody } from '../utils/errors';
import { logger } from '../utils/logger';
import { formatIssues } from './validate';

type PgError = { code?: string; constraint?: string; detail?: string };

function isJsonSyntaxError(error: unknown): boolean {
  return Boolean(
    error && typeof error === 'object' && 'type' in error && (error as { type: string }).type === 'entity.parse.failed',
  );
}

export function notFoundHandler(req: Request, _res: Response, next: NextFunction): void {
  next(new AppError(404, 'NOT_FOUND', `No route matches ${req.method} ${req.originalUrl}.`));
}

export function errorHandler(
  error: unknown,
  req: Request,
  res: Response,
  _next: NextFunction,
): void {
  const requestId = req.requestId;

  if (error instanceof AppError) {
    if (error.status >= 500) {
      logger.error('Request failed', { requestId, code: error.code, message: error.message });
    }
    const body: ErrorBody = {
      success: false,
      error: { code: error.code, message: error.message, requestId, ...(error.details ? { details: error.details } : {}) },
    };
    res.status(error.status).json(body);
    return;
  }

  if (error instanceof ZodError) {
    const body: ErrorBody = {
      success: false,
      error: {
        code: 'VALIDATION_ERROR',
        message: 'Some fields are invalid.',
        details: formatIssues(error),
        requestId,
      },
    };
    res.status(422).json(body);
    return;
  }

  if (error instanceof MulterError) {
    const tooLarge = error.code === 'LIMIT_FILE_SIZE';
    const body: ErrorBody = {
      success: false,
      error: {
        code: tooLarge ? 'PAYLOAD_TOO_LARGE' : 'VALIDATION_ERROR',
        message: tooLarge
          ? 'That image is too large. Choose a smaller file.'
          : `Upload failed: ${error.message}`,
        requestId,
      },
    };
    res.status(tooLarge ? 413 : 422).json(body);
    return;
  }

  if (isJsonSyntaxError(error)) {
    res.status(400).json({
      success: false,
      error: { code: 'VALIDATION_ERROR', message: 'The request body is not valid JSON.', requestId },
    } satisfies ErrorBody);
    return;
  }

  const pgError = error as PgError;
  if (pgError?.code === '23505') {
    res.status(409).json({
      success: false,
      error: {
        code: 'CONFLICT',
        message: 'That value already exists. Use a different one.',
        details: env.isProduction ? undefined : { constraint: pgError.constraint, detail: pgError.detail },
        requestId,
      },
    } satisfies ErrorBody);
    return;
  }

  if (pgError?.code === '23503') {
    res.status(409).json({
      success: false,
      error: {
        code: 'CONFLICT',
        message: 'This record is still referenced by other data.',
        requestId,
      },
    } satisfies ErrorBody);
    return;
  }

  logger.error('Unhandled error', {
    requestId,
    message: error instanceof Error ? error.message : String(error),
    stack: env.isProduction ? undefined : (error as Error)?.stack,
  });

  res.status(500).json({
    success: false,
    error: {
      code: 'INTERNAL_ERROR',
      message: 'Something went wrong. Please try again.',
      requestId,
    },
  } satisfies ErrorBody);
}
