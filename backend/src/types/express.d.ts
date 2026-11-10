import type { AuthContext } from './auth';

declare global {
  namespace Express {
    interface Request {
      /** Correlates a request with its log lines and error payloads. */
      requestId: string;
      /** Present whenever `requireAuth` allowed the request through. */
      auth?: AuthContext;
      /** Minimal actor snapshot for the audit log. */
      actor?: { userId: string; fullName: string; role: AuthContext['role'] };
      /** Values parsed by the `validate` middleware (query is read-only in Express 5). */
      validated: {
        body?: unknown;
        query?: unknown;
        params?: unknown;
      };
    }
  }
}

export {};
