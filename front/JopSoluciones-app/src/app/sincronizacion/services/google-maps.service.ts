import { Injectable } from '@angular/core';
import { AppSettingsService } from './app-settings.service';

@Injectable({ providedIn: 'root' })
export class GoogleMapsService {
  private loaded = false;
  private loading = false;
  private callbacks: (() => void)[] = [];

  constructor(private settings: AppSettingsService) {}

  load(): Promise<boolean> {
    const key = this.settings.getGoogleMapsKey();
    if (!key) return Promise.resolve(false);

    if (this.loaded) return Promise.resolve(true);

    if (this.loading) {
      return new Promise(resolve => {
        this.callbacks.push(() => resolve(true));
      });
    }

    this.loading = true;
    return new Promise(resolve => {
      const callbackName = '__gmapsCallback__';
      (window as any)[callbackName] = () => {
        this.loaded = true;
        this.loading = false;
        this.callbacks.forEach(cb => cb());
        this.callbacks = [];
        resolve(true);
      };

      const script = document.createElement('script');
      script.src = `https://maps.googleapis.com/maps/api/js?key=${key}&libraries=places&callback=${callbackName}&loading=async`;
      script.async = true;
      script.defer = true;
      script.onerror = () => { this.loading = false; resolve(false); };
      document.head.appendChild(script);
    });
  }

  isLoaded(): boolean {
    return this.loaded || !!(window as any)['google']?.maps?.places;
  }
}
