import express, { type Express } from 'express';
import helmet from 'helmet';
import cors from 'cors';
import compression from 'compression';
import { env } from './config/env';
import { requestContext } from './middleware/requestContext';
import { errorHandler, notFoundHandler } from './middleware/error';
import { apiRouter } from './routes';
import { ok } from './utils/errors';
import { pool } from './database/pool';

export function createApp(): Express {
  const app = express();

  // Render terminates TLS in front of the app; trust one proxy hop so
  // rate limiting and req.ip see the real client address.
  app.set('trust proxy', 1);
  app.disable('x-powered-by');

  app.use(
    helmet({
      crossOriginResourcePolicy: { policy: 'cross-origin' },
      contentSecurityPolicy: false,
    }),
  );
  app.use(
    cors({
      origin: env.corsOrigins.includes('*') ? true : env.corsOrigins,
      methods: ['GET', 'POST', 'PATCH', 'PUT', 'DELETE', 'OPTIONS'],
      allowedHeaders: ['Content-Type', 'Authorization', 'X-Request-Id'],
      exposedHeaders: ['X-Request-Id'],
      maxAge: 600,
    }),
  );
  app.use(compression());
  app.use(express.json({ limit: '1mb' }));
  app.use(express.urlencoded({ extended: false, limit: '1mb' }));
  app.use(requestContext);

  app.get('/', (_req, res) => {
    res.json(
      ok({
        service: 'squadgym-api',
        version: '1.0.0',
        docs: '/api/v1',
        time: new Date().toISOString(),
      }),
    );
  });

  app.get('/health', (_req, res) => {
    res.json(ok({ status: 'ok', uptimeSeconds: Math.round(process.uptime()) }));
  });

  app.get('/ready', async (_req, res) => {
    try {
      await pool.query('select 1');
      res.json(ok({ status: 'ready', database: 'up' }));
    } catch {
      res.status(503).json({ success: false, error: { code: 'DEPENDENCY_UNAVAILABLE', message: 'Database unavailable' } });
    }
  });

  app.use('/api/v1', apiRouter);

  app.use(notFoundHandler);
  app.use(errorHandler);

  return app;
}
