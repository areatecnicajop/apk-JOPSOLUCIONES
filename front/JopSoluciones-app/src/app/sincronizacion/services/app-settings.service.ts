import { Injectable } from '@angular/core';

const KEYS = {
  googleMaps: 'jop_gmaps_key',
} as const;

@Injectable({ providedIn: 'root' })
export class AppSettingsService {

  getGoogleMapsKey(): string {
    return localStorage.getItem(KEYS.googleMaps) ?? '';
  }

  setGoogleMapsKey(key: string): void {
    localStorage.setItem(KEYS.googleMaps, key.trim());
  }
}
