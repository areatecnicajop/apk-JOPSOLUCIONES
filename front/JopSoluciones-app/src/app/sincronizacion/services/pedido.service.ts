import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface PedidoItem {
  sku: string;
  name: string;
  qty: number;
  unitPrice: number;
  total: number;
}

export interface Pedido {
  uuid: string;
  clienteUuid: string;
  clienteName: string;
  items: string; // JSON string
  subtotal: number;
  tax: number;
  total: number;
  status: 'pending' | 'confirmed' | 'cancelled';
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class PedidoService {
  private url = environment.baseUrl + 'api/pedidos';

  constructor(private http: HttpClient) {}

  getAll(): Observable<Pedido[]> {
    return this.http.get<Pedido[]>(this.url);
  }

  getByCliente(clienteUuid: string): Observable<Pedido[]> {
    return this.http.get<Pedido[]>(`${this.url}/cliente/${clienteUuid}`);
  }

  updateStatus(uuid: string, status: string): Observable<Pedido> {
    return this.http.patch<Pedido>(`${this.url}/${uuid}/status`, { status });
  }

  create(pedido: Partial<Pedido>): Observable<Pedido> {
    return this.http.post<Pedido>(this.url, pedido);
  }

  delete(uuid: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${uuid}`);
  }

  parseItems(itemsJson: string): PedidoItem[] {
    try { return JSON.parse(itemsJson) as PedidoItem[]; } catch { return []; }
  }
}
