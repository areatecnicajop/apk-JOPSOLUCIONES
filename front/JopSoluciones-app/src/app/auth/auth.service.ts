import { Injectable } from '@angular/core';
import { Router } from '@angular/router';

export interface AuthUser {
  username: string;
  role: 'admin' | 'user';
}

const USERS: { username: string; password: string; role: 'admin' | 'user' }[] = [
  { username: 'admin', password: 'admin4492', role: 'admin' },
  { username: 'jop',   password: '4492',      role: 'user'  },
];

const SESSION_KEY = 'jop_session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  constructor(private router: Router) {}

  login(username: string, password: string): boolean {
    const match = USERS.find(u => u.username === username && u.password === password);
    if (!match) return false;
    const user: AuthUser = { username: match.username, role: match.role };
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(user));
    return true;
  }

  logout(): void {
    sessionStorage.removeItem(SESSION_KEY);
    this.router.navigate(['/login']);
  }

  getUser(): AuthUser | null {
    const raw = sessionStorage.getItem(SESSION_KEY);
    return raw ? JSON.parse(raw) : null;
  }

  isLoggedIn(): boolean {
    return !!this.getUser();
  }

  isAdmin(): boolean {
    return this.getUser()?.role === 'admin';
  }
}
