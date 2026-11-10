/** Typed application errors + the single error shape every endpoint returns. */

export type ErrorCode =
  | 'AUTH_REQUIRED'
  | 'AUTH_INVALID_CREDENTIALS'
  | 'AUTH_TOKEN_EXPIRED'
  | 'AUTH_TOKEN_INVALID'
  | 'AUTH_ACCOUNT_INACTIVE'
  | 'AUTH_ACCOUNT_INVITED'
  | 'FORBIDDEN'
  | 'VALIDATION_ERROR'
  | 'NOT_FOUND'
  | 'CONFLICT'
  | 'ALREADY_MARKED'
  | 'ALREADY_UNDONE'
  | 'UNDO_WINDOW_EXPIRED'
  | 'UNDO_WINDOW_ACTIVE'
  | 'GYM_CLOSED'
  | 'MEMBER_NOT_ELIGIBLE'
  | 'MEMBER_ARCHIVED'
  | 'RENEWAL_NOT_REVERSIBLE'
  | 'RATE_LIMITED'
  | 'PAYLOAD_TOO_LARGE'
  | 'UNSUPPORTED_MEDIA_TYPE'
  | 'DEPENDENCY_UNAVAILABLE'
  | 'INTERNAL_ERROR';

export interface ErrorBody {
  success: false;
  error: {
    code: ErrorCode;
    message: string;
    details?: unknown;
    requestId?: string;
  };
}

export class AppError extends Error {
  readonly status: number;
  readonly code: ErrorCode;
  readonly details?: unknown;
  readonly expose: boolean;

  constructor(status: number, code: ErrorCode, message: string, details?: unknown, expose = true) {
    super(message);
    this.name = 'AppError';
    this.status = status;
    this.code = code;
    this.details = details;
    this.expose = expose;
  }

  static unauthorized(message = 'Authentication is required.'): AppError {
    return new AppError(401, 'AUTH_REQUIRED', message);
  }

  static invalidCredentials(message = 'Email or password is incorrect.'): AppError {
    return new AppError(401, 'AUTH_INVALID_CREDENTIALS', message);
  }

  static forbidden(message = 'You do not have permission to perform this action.'): AppError {
    return new AppError(403, 'FORBIDDEN', message);
  }

  static notFound(entity = 'Resource'): AppError {
    return new AppError(404, 'NOT_FOUND', `${entity} was not found.`);
  }

  static validation(details: unknown, message = 'Some fields are invalid.'): AppError {
    return new AppError(422, 'VALIDATION_ERROR', message, details);
  }

  static conflict(code: ErrorCode = 'CONFLICT', message = 'The request conflicts with the current state.'): AppError {
    return new AppError(409, code, message);
  }

  static undoWindowExpired(message = 'The undo window for this action has expired.'): AppError {
    return new AppError(409, 'UNDO_WINDOW_EXPIRED', message);
  }

  static internal(message = 'Something went wrong. Please try again.'): AppError {
    return new AppError(500, 'INTERNAL_ERROR', message, undefined, false);
  }
}

/** Conventional success envelope. */
export interface SuccessBody<T> {
  success: true;
  data: T;
  meta?: Record<string, unknown>;
}

export function ok<T>(data: T, meta?: Record<string, unknown>): SuccessBody<T> {
  return meta ? { success: true, data, meta } : { success: true, data };
}
