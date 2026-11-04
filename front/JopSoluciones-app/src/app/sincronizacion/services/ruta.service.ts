import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { RutaGuardada, NuevaRuta } from '../interfaces/ruta.model';

@Injectable({ providedIn: 'root' })
export class RutaService {
  private url = environment.baseUrl + 'api/rutas';

  constructor(private http: HttpClient) {}

  getAll(): Observable<RutaGuardada[]> {
    return this.http.get<RutaGuardada[]>(this.url);
  }

  create(r: NuevaRuta): Observable<RutaGuardada> {
    return this.http.post<RutaGuardada>(this.url, r);
  }

  delete(uuid: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${uuid}`);
  }
}
