import type { Permission, Role } from '../config/constants';

/** Populated by the auth middleware for every authenticated request. */
export interface AuthContext {
  userId: string;
  gymId: string;
  email: string;
  fullName: string;
  role: Role;
  permissions: Permission[];
  status: 'active' | 'inactive' | 'invited';
}

export interface AuditActor {
  userId: string;
  fullName: string;
  role: Role;
}
