import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CategoriaCliente } from '../interfaces/categoria-cliente.model';

@Injectable({ providedIn: 'root' })
export class CategoriaClienteService {
  private url = environment.baseUrl + 'api/categorias-cliente';

  constructor(private http: HttpClient) {}

  getAll(): Observable<CategoriaCliente[]> {
    return this.http.get<CategoriaCliente[]>(this.url);
  }

  create(c: Partial<CategoriaCliente>): Observable<CategoriaCliente> {
    return this.http.post<CategoriaCliente>(this.url, c);
  }

  update(uuid: string, c: Partial<CategoriaCliente>): Observable<CategoriaCliente> {
    return this.http.put<CategoriaCliente>(`${this.url}/${uuid}`, c);
  }

  delete(uuid: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${uuid}`);
  }
}
