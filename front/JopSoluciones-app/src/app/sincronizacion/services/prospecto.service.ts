import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Prospecto } from '../interfaces/prospecto.model';

@Injectable({ providedIn: 'root' })
export class ProspectoService {
  private url = environment.baseUrl + 'api/prospectos';

  constructor(private http: HttpClient) {}

  getAll(rubro?: string): Observable<Prospecto[]> {
    let params = new HttpParams();
    if (rubro) params = params.set('rubro', rubro);
    return this.http.get<Prospecto[]>(this.url, { params });
  }

  getConUbicacion(): Observable<Prospecto[]> {
    return this.http.get<Prospecto[]>(`${this.url}?conUbicacion=true`);
  }
}
