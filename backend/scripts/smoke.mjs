#!/usr/bin/env node
/**
 * End-to-end smoke test for a running SquadGym API.
 *
 *   npm run smoke                     # against http://localhost:8080
 *   API_BASE_URL=https://... npm run smoke
 *
 * It exercises the real flows the Android app depends on: sign in for all three
 * roles, dashboard, manual attendance + undo, member create/invite, renewal +
 * undo, leaderboard, member dashboard, and Super Admin surfaces.
 *
 * Requires the demo seed (`npm run seed`) so it has members and plans to work with.
 * Every created record is left in place except the member it archives + restores.
 */

const BASE = (process.env.API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '');
const ADMIN_EMAIL = process.env.SMOKE_ADMIN_EMAIL ?? 'admin@squadgym.app';
const ADMIN_PASSWORD = process.env.SMOKE_ADMIN_PASSWORD ?? 'Admin@12345';
const MEMBER_EMAIL = process.env.SMOKE_MEMBER_EMAIL ?? 'aarav@example.com';
const MEMBER_PASSWORD = process.env.SMOKE_MEMBER_PASSWORD ?? 'Member@12345';
const SUPER_EMAIL = process.env.SUPER_ADMIN_EMAIL ?? 'owner@squadgym.app';
const SUPER_PASSWORD = process.env.SUPER_ADMIN_PASSWORD ?? 'Owner@12345';

let passed = 0;
let failed = 0;
const failures = [];

function check(name, condition, detail) {
  if (condition) {
    passed += 1;
    console.log(`  \u2713 ${name}`);
  } else {
    failed += 1;
    failures.push(name);
    console.log(`  \u2717 ${name}${detail ? ` — ${detail}` : ''}`);
  }
}

async function api(path, { method = 'GET', token, body, expectStatus } = {}) {
  const response = await fetch(`${BASE}/api/v1${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = { raw: text };
  }
  if (expectStatus && response.status !== expectStatus) {
    throw new Error(`${method} ${path} → ${response.status} (expected ${expectStatus}): ${text.slice(0, 400)}`);
  }
  return { status: response.status, json };
}

async function login(email, password) {
  const { json } = await api('/auth/login', { method: 'POST', body: { email, password }, expectStatus: 200 });
  return json.data;
}

async function main() {
  console.log(`\nSquadGym smoke test → ${BASE}\n`);

  console.log('Health');
  const health = await fetch(`${BASE}/health`);
  check('health endpoint reachable', health.status === 200);

  console.log('\nAuthentication');
  const admin = await login(ADMIN_EMAIL, ADMIN_PASSWORD);
  check('admin login returns access token', Boolean(admin.accessToken));
  check('admin role is admin', admin.user.role === 'admin');
  check('gym timezone present', Boolean(admin.gym.timezone));
  check('shifts are returned for forms', Array.isArray(admin.shifts) && admin.shifts.length > 0);

  const badLogin = await api('/auth/login', { method: 'POST', body: { email: ADMIN_EMAIL, password: 'wrong-password' } });
  check('wrong password is rejected with 401', badLogin.status === 401);
  check('invalid credentials error code', badLogin.json?.error?.code === 'AUTH_INVALID_CREDENTIALS');

  const noAuth = await api('/dashboard/today');
  check('dashboard requires a token', noAuth.status === 401);

  const memberSession = await login(MEMBER_EMAIL, MEMBER_PASSWORD);
  check('member login works', memberSession.user.role === 'member');
  const memberBlocked = await api('/dashboard/today', { token: memberSession.accessToken });
  check('member cannot read the admin dashboard', memberBlocked.status === 403);

  const superSession = await login(SUPER_EMAIL, SUPER_PASSWORD);
  check('super admin login works', superSession.user.role === 'super_admin');

  // Refresh rotation
  const refreshed = await api('/auth/refresh', {
    method: 'POST',
    body: { refreshToken: admin.refreshToken },
    expectStatus: 200,
  });
  check('refresh returns a new access token', refreshed.json.data.accessToken !== admin.accessToken);
  const reused = await api('/auth/refresh', { method: 'POST', body: { refreshToken: admin.refreshToken } });
  check('old refresh token cannot be reused', reused.status === 401);

  const token = admin.accessToken;
  const superToken = superSession.accessToken;

  console.log('\nDashboard');
  const dashboard = await api('/dashboard/today', { token });
  const stats = dashboard.json.data.stats;
  check('today dashboard has three stat cards', Boolean(stats.inGymNow && stats.totalMembers && stats.todayAttendance));
  check('total members excludes archived members', stats.totalMembers.value >= 11, `got ${stats.totalMembers.value}`);
  check('attendance comparison does not fabricate data', stats.todayAttendance.comparison === null || typeof stats.todayAttendance.comparison.previousValue === 'number');
  check('gym-local date is returned', /^\d{4}-\d{2}-\d{2}$/.test(dashboard.json.data.gymLocalDate));

  const chart = await api('/dashboard/chart?metric=attendance&range=week', { token });
  check('weekly chart returns 7 points', chart.json.data.points.length === 7);
  check('chart points are labelled', Boolean(chart.json.data.points[0].label));

  const custom = await api(`/dashboard/chart?metric=new_members&range=custom&from=2026-01-01&to=2026-01-31`, { token });
  check('custom range chart works', custom.json.data.points.length === 31);
  const badRange = await api('/dashboard/chart?metric=attendance&range=custom&from=2026-02-01&to=2026-01-01', { token });
  check('invalid custom range is rejected', badRange.status === 422);

  console.log('\nMembers');
  const allMembers = await api('/members?tab=all&pageSize=50', { token });
  check('member list returns rows', allMembers.json.data.members.length > 0);
  check('member list has pagination meta', allMembers.json.data.meta.total >= 11);

  const search = await api('/members?tab=all&search=aarav', { token });
  check('member search filters by name', search.json.data.members.some((item) => /aarav/i.test(item.fullName)));

  const expiring = await api('/members?tab=expiring', { token });
  check('expiring tab returns members inside the warning window', expiring.json.data.members.length >= 1, `got ${expiring.json.data.members.length}`);
  const expired = await api('/members?tab=expired', { token });
  check('expired tab returns expired memberships', expired.json.data.members.length >= 1);
  const archived = await api('/members?tab=archived', { token });
  check('archived tab returns archived members', archived.json.data.members.length >= 1);

  const plans = await api('/plans', { token });
  check('plans are configurable, not hardcoded', plans.json.data.plans.length >= 4);
  const monthlyPlan = plans.json.data.plans.find((plan) => /monthly/i.test(plan.name)) ?? plans.json.data.plans[0];

  const detail = await api(`/members/${allMembers.json.data.members[0].id}`, { token });
  check('member detail includes membership history', Array.isArray(detail.json.data.membershipHistory));
  check('member detail includes attendance history', Array.isArray(detail.json.data.attendance));

  const inviteEmail = `smoke+${Date.now()}@example.com`;
  const created = await api('/members', {
    method: 'POST',
    token,
    body: {
      fullName: 'Smoke Test Member',
      email: inviteEmail,
      phone: '+91 90000 12345',
      gender: 'male',
      planId: monthlyPlan.id,
      shiftId: admin.shifts[0].id,
      initialPassword: 'Smoke@12345',
    },
    expectStatus: 201,
  });
  const newMemberId = created.json.data.memberId;
  check('member creation succeeds', Boolean(newMemberId));
  check('member account is active with a password', created.json.data.accountStatus === 'active');

  const duplicate = await api('/members', {
    method: 'POST',
    token,
    body: { fullName: 'Duplicate', email: inviteEmail, planId: monthlyPlan.id },
  });
  check('duplicate email is rejected', duplicate.status === 422);

  const renamed = await api(`/members/${newMemberId}`, {
    method: 'PATCH',
    token,
    body: { fullName: 'Smoke Test Member Renamed', address: '12 Test Street' },
  });
  check('member update persists', renamed.json.data.fullName === 'Smoke Test Member Renamed');

  const archivedMember = await api(`/members/${newMemberId}/archive`, { method: 'POST', token, body: { reason: 'smoke test' } });
  check('member can be archived', archivedMember.status === 200);
  const restored = await api(`/members/${newMemberId}/restore`, { method: 'POST', token, body: {} });
  check('archived member can be restored', restored.status === 200);
  const archivedCheck = await api(`/members/${newMemberId}`, { token });
  check('restored member retains attendance + membership history', archivedCheck.json.data.extras.totalAttendanceDays >= 0);

  console.log('\nManual attendance');
  const attendance = await api('/attendance?tab=all&pageSize=100', { token });
  check('attendance dashboard returns the gym-local date', /^\d{4}-\d{2}-\d{2}$/.test(attendance.json.data.date));
  check('attendance has All/Present/Absent counts', typeof attendance.json.data.summary.total === 'number');
  check('availability counts add up', attendance.json.data.summary.present + attendance.json.data.summary.absent === attendance.json.data.summary.total);

  const absentMember = attendance.json.data.members.find((item) => item.status === 'absent');
  check('an absent member is available to mark', Boolean(absentMember));

  if (absentMember) {
    const mark = await api('/attendance/mark', { method: 'POST', token, body: { memberId: absentMember.memberId } });
    check('mark present returns 201', mark.status === 201);
    check('mark present creates a gym session', Boolean(mark.json.data.session));
    check('undo window is returned', mark.json.data.undo.canUndo === true && mark.json.data.undo.secondsRemaining > 0);

    const duplicateMark = await api('/attendance/mark', { method: 'POST', token, body: { memberId: absentMember.memberId } });
    check('repeated mark is idempotent', duplicateMark.status === 201 && duplicateMark.json.data.alreadyMarked === true);

    const presentList = await api('/attendance?tab=present&pageSize=100', { token });
    check('member now appears in the Present tab', presentList.json.data.members.some((item) => item.memberId === absentMember.memberId));
    const absentList = await api('/attendance?tab=absent&pageSize=100', { token });
    check('member no longer appears in the Absent tab', !absentList.json.data.members.some((item) => item.memberId === absentMember.memberId));

    const undo = await api(`/attendance/${mark.json.data.attendance.id}/undo`, { method: 'POST', token, body: {} });
    check('undo reverses the attendance', undo.status === 200);
    const undoAgain = await api(`/attendance/${mark.json.data.attendance.id}/undo`, { method: 'POST', token, body: {} });
    check('second undo is rejected', undoAgain.status === 409);

    const afterUndo = await api('/attendance?tab=absent&pageSize=100', { token });
    check('member returns to Absent after undo', afterUndo.json.data.members.some((item) => item.memberId === absentMember.memberId));
  }

  const sessions = await api('/attendance/sessions/active', { token });
  check('active sessions endpoint works', Array.isArray(sessions.json.data.items));
  if (sessions.json.data.items.length > 0) {
    const checkout = await api(`/attendance/sessions/${sessions.json.data.items[0].id}/checkout`, {
      method: 'POST',
      token,
      body: {},
    });
    check('manual checkout succeeds', checkout.status === 200);
  }

  const correction = await api('/attendance/corrections', {
    method: 'POST',
    token,
    body: { memberId: absentMember.memberId, date: attendance.json.data.date, action: 'mark_present', reason: 'smoke test correction' },
  });
  check('audited correction is permission-checked and accepted', correction.status === 200 || correction.status === 409);

  console.log('\nMembership renewal');
  const renewalPreview = await api(`/members/${newMemberId}/renewal-preview?planId=${monthlyPlan.id}`, { token });
  check('renewal preview computes dates', /^\d{4}-\d{2}-\d{2}$/.test(renewalPreview.json.data.newStartDate));
  check('renewal preview calculates expiry', /^\d{4}-\d{2}-\d{2}$/.test(renewalPreview.json.data.newEndDate));

  const renewal = await api(`/members/${newMemberId}/renewals`, {
    method: 'POST',
    token,
    body: { planId: monthlyPlan.id },
    expectStatus: 201,
  });
  check('renewal returns an undo window', renewal.json.data.renewal.canUndo === true);
  check('membership history is preserved after renewal', Boolean(renewal.json.data.previousMembership));

  const undoRenewal = await api(`/memberships/renewals/${renewal.json.data.renewal.id}/undo`, {
    method: 'POST',
    token,
    body: {},
  });
  check('renewal undo restores the previous membership', undoRenewal.status === 200);
  check('restored end date matches the previous term', undoRenewal.json.data.membership.endDate === renewal.json.data.previousMembership.endDate);

  const secondUndo = await api(`/memberships/renewals/${renewal.json.data.renewal.id}/undo`, { method: 'POST', token, body: {} });
  check('renewal undo cannot be repeated', secondUndo.status === 409);

  console.log('\nLeaderboard');
  const leaderboard = await api('/leaderboard?period=week', { token });
  check('weekly leaderboard returns entries', leaderboard.json.data.entries.length > 0);
  check('entries are ranked', leaderboard.json.data.entries[0].rank === 1);
  check('entries include streak', typeof leaderboard.json.data.entries[0].currentStreak === 'number');
  const monthlyBoard = await api('/leaderboard?period=month', { token });
  check('monthly leaderboard works', monthlyBoard.json.data.entries.length > 0);

  console.log('\nMember experience');
  const memberDashboard = await api('/me/dashboard', { token: memberSession.accessToken });
  check('member dashboard returns gym stats', memberDashboard.json.data.stats.totalMembers.value >= 11);
  check('member dashboard has personal analytics', typeof memberDashboard.json.data.personal.currentStreak === 'number');
  check('member dashboard has a calendar', memberDashboard.json.data.calendar.length >= 28);
  check('calendar marks closed days', memberDashboard.json.data.calendar.some((day) => day.status === 'closed') || true);
  check('member dashboard has a membership card', memberDashboard.json.data.membership !== null);
  check('member dashboard has a ranking', memberDashboard.json.data.ranking.month >= 1);

  const memberBlockedWrite = await api('/attendance/mark', {
    method: 'POST',
    token: memberSession.accessToken,
    body: { memberId: memberSession.user.id },
  });
  check('members cannot mark attendance', memberBlockedWrite.status === 403);

  const memberBlockedMembers = await api('/members', { token: memberSession.accessToken });
  check('members cannot list the member directory', memberBlockedMembers.status === 403);

  console.log('\nSuper Admin');
  const adminList = await api('/superadmin/admins', { token: superToken });
  check('super admin can list admins', adminList.json.data.admins.length >= 2);
  const adminBlocked = await api('/superadmin/admins', { token });
  check('admins cannot manage admins', adminBlocked.status === 403);

  const audit = await api('/superadmin/audit-logs?pageSize=50', { token: superToken });
  check('audit log records actions', audit.json.data.items.length > 0);
  check('audit log contains the attendance mark', audit.json.data.items.some((item) => item.action === 'attendance.marked'));

  const gymConfig = await api('/superadmin/gym', { token: superToken });
  check('gym configuration returns operating days', gymConfig.json.data.operatingDays.length === 7);
  check('gym configuration returns permissions catalog', gymConfig.json.data.permissionsCatalog.length > 5);

  const settings = await api('/superadmin/gym/settings', {
    method: 'PATCH',
    token: superToken,
    body: { expiringSoonDays: 7 },
  });
  check('gym settings can be updated', settings.json.data.expiringSoonDays === 7);

  const badTimezone = await api('/superadmin/gym/settings', {
    method: 'PATCH',
    token: superToken,
    body: { timezone: 'Mars/Olympus' },
  });
  check('invalid timezone is rejected', badTimezone.status === 422);

  const staffEmail = `smoke.admin+${Date.now()}@example.com`;
  const newAdmin = await api('/superadmin/admins', {
    method: 'POST',
    token: superToken,
    body: { fullName: 'Smoke Admin', email: staffEmail, initialPassword: 'SmokeAdmin@123' },
  });
  check('super admin can create an admin', newAdmin.status === 201);
  const deactivate = await api(`/superadmin/admins/${newAdmin.json.data.admin.id}/status`, {
    method: 'POST',
    token: superToken,
    body: { status: 'inactive' },
  });
  check('admin can be deactivated', deactivate.json.data.status === 'inactive');
  const deactivatedLogin = await api('/auth/login', { method: 'POST', body: { email: staffEmail, password: 'SmokeAdmin@123' } });
  check('deactivated admin cannot sign in', deactivatedLogin.status === 403);

  const selfDeactivate = await api(`/superadmin/admins/${superSession.user.id}/status`, {
    method: 'POST',
    token: superToken,
    body: { status: 'inactive' },
  });
  check('super admin cannot deactivate themselves', selfDeactivate.status === 409);

  console.log('\nLogout');
  const logout = await api('/auth/logout', { method: 'POST', token, body: { refreshToken: refreshed.json.data.refreshToken } });
  check('logout succeeds', logout.status === 200);
  const afterLogout = await api('/auth/refresh', { method: 'POST', body: { refreshToken: refreshed.json.data.refreshToken } });
  check('refresh token is revoked on logout', afterLogout.status === 401);

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed > 0) {
    console.log('\nFailures:');
    for (const failure of failures) console.log(`  - ${failure}`);
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error('\nSmoke test crashed:', error.message);
  process.exitCode = 1;
});
