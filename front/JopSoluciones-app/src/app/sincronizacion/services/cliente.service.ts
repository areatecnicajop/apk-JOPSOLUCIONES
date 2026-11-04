import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Cliente } from '../interfaces/cliente.model';

@Injectable({ providedIn: 'root' })
export class ClienteService {
  private url = environment.baseUrl + 'api/clientes';

  constructor(private http: HttpClient) {}

  getAll(): Observable<Cliente[]> {
    return this.http.get<Cliente[]>(this.url);
  }

  create(c: Partial<Cliente>): Observable<Cliente> {
    return this.http.post<Cliente>(this.url, c);
  }

  update(uuid: string, c: Partial<Cliente>): Observable<Cliente> {
    return this.http.put<Cliente>(`${this.url}/${uuid}`, c);
  }

  delete(uuid: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${uuid}`);
  }
}
