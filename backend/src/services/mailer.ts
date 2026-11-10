import nodemailer, { type Transporter } from 'nodemailer';
import { env } from '../config/env';
import { logger } from '../utils/logger';

/**
 * Transactional email.
 *
 * When SMTP is not configured (local development) nothing is sent: the message is
 * logged and the API returns the token when `EXPOSE_DEV_TOKENS=true`, so the full
 * invitation / reset flow can be exercised without a mail server.
 */

let transporter: Transporter | null = null;
let transporterReady = false;

function getTransporter(): Transporter | null {
  if (transporterReady) return transporter;
  transporterReady = true;
  if (!env.SMTP_HOST) return null;
  transporter = nodemailer.createTransport({
    host: env.SMTP_HOST,
    port: env.SMTP_PORT,
    secure: env.SMTP_SECURE,
    ...(env.SMTP_USER && env.SMTP_PASS
      ? { auth: { user: env.SMTP_USER, pass: env.SMTP_PASS } }
      : {}),
  });
  return transporter;
}

export interface MailResult {
  delivered: boolean;
  /** Only populated in development when EXPOSE_DEV_TOKENS=true. */
  devToken?: string;
}

async function send(to: string, subject: string, text: string, html: string): Promise<boolean> {
  const client = getTransporter();
  if (!client) {
    logger.info('Email not sent (SMTP not configured)', { to, subject, preview: text.slice(0, 240) });
    return false;
  }
  try {
    await client.sendMail({ from: env.MAIL_FROM, to, subject, text, html });
    return true;
  } catch (error) {
    logger.error('Email delivery failed', { to, subject, message: (error as Error).message });
    return false;
  }
}

export async function sendInvitationEmail(input: {
  to: string;
  fullName: string;
  gymName: string;
  role: 'admin' | 'member';
  token: string;
}): Promise<MailResult> {
  const link = `${env.API_BASE_URL.replace(/\/$/, '')}/invite/${input.token}`;
  const subject = `You're invited to ${input.gymName}`;
  const text = [
    `Hi ${input.fullName},`,
    '',
    `${input.gymName} has created a ${input.role} account for you.`,
    `Set your password and activate the account: ${link}`,
    '',
    'If you were not expecting this email, you can ignore it.',
  ].join('\n');
  const html = `
    <div style="font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;line-height:1.5;color:#111">
      <h2 style="margin:0 0 8px">You're invited to ${escapeHtml(input.gymName)}</h2>
      <p>Hi ${escapeHtml(input.fullName)},</p>
      <p>${escapeHtml(input.gymName)} has created a ${input.role} account for you.
         Set your password to activate it.</p>
      <p><a href="${link}" style="background:#c6f24e;color:#0b0d0c;padding:12px 20px;border-radius:12px;text-decoration:none;font-weight:700">Activate account</a></p>
      <p style="color:#666;font-size:13px">Or copy this link: ${link}</p>
    </div>`;
  const delivered = await send(input.to, subject, text, html);
  return { delivered, ...(env.EXPOSE_DEV_TOKENS ? { devToken: input.token } : {}) };
}

export async function sendPasswordResetEmail(input: {
  to: string;
  fullName: string;
  token: string;
}): Promise<MailResult> {
  const subject = 'Reset your SquadGym password';
  const text = [
    `Hi ${input.fullName},`,
    '',
    'Use the code below to reset your password. It expires in 30 minutes.',
    '',
    input.token,
    '',
    'If you did not request this, ignore this email.',
  ].join('\n');
  const html = `
    <div style="font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;line-height:1.5;color:#111">
      <h2 style="margin:0 0 8px">Reset your password</h2>
      <p>Hi ${escapeHtml(input.fullName)},</p>
      <p>Enter this code in the app. It expires in 30 minutes.</p>
      <p style="font-family:ui-monospace,Menlo,monospace;font-size:22px;letter-spacing:4px;background:#f2f4ef;padding:12px 16px;border-radius:10px">${escapeHtml(input.token)}</p>
      <p style="color:#666;font-size:13px">If you did not request this, you can ignore this email.</p>
    </div>`;
  const delivered = await send(input.to, subject, text, html);
  return { delivered, ...(env.EXPOSE_DEV_TOKENS ? { devToken: input.token } : {}) };
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}
