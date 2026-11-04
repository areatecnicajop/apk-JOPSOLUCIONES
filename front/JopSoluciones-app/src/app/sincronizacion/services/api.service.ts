import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Order } from '../interfaces/order.model';
import { environment } from '../../../environments/environment';



@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private baseUrl = environment.baseUrl;; // Asegurar que termine con "/"

  constructor(private http: HttpClient) {}

  startSynchro(skus: string[] = [], forceUpdate: boolean = false, includeImages: boolean = false): Observable<string> {
    this.http.get<string[]>(`${this.baseUrl}productLaudusStock/get`);
    let url = `${this.baseUrl}start`;

    const params = [];
    if (forceUpdate) params.push("forceUpdate=true");
    if (includeImages) params.push("includeImages=true");

    if (params.length > 0) {
        url += "?" + params.join("&");
    }

    return this.http.post(url, skus, { responseType: 'text' }); // 🔥 Evita error de parsing
  }

  syncroCategories(): Observable<string> {
    return this.http.post(`${this.baseUrl}syncroCategorias`, {}, { responseType: 'text' }); // 🔥 Evita error de parsing
  }
  getOrders(): Observable<Order[]> {
    return this.http.get<Order[]>(`${this.baseUrl}allOrders`); // ✅ No tocar
  }

  updateStock(): Observable<string[]> {
    // Sin parámetro: usa el default del backend (bodega "008", la real).
    // Antes mandaba localStorage['test_bodega'] ?? '8' — "8" no existe como
    // bodega en Laudus, y ese valor de localStorage es del panel de Testing,
    // no debería controlar el botón real de actualizar stock.
    return this.http.get<string[]>(`${this.baseUrl}productLaudusStock/get`);
  }
}
