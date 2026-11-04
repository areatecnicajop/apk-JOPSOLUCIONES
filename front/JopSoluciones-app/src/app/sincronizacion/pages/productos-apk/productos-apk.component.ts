import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { AppCatalogProduct, formatPrice } from '../../interfaces/app-catalog-product.model';
import { environment } from '../../../../environments/environment';

// Espejo del catálogo local que usa la APK (misma tabla, mismos endpoints:
// /api/app-catalog/products y /api/app-catalog/refresh). No tiene relación
// con /api/products ni con la pestaña "Panel de Sync" — eso es otro proceso.
@Component({
  selector: 'app-productos-apk',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  templateUrl: './productos-apk.component.html',
  styleUrls: ['./productos-apk.component.css']
})
export class ProductosApkComponent implements OnInit {
  productos: AppCatalogProduct[] = [];
  loading = true;
  syncing = false;
  error = '';
  syncMsg = '';
  syncError = false;
  searchQuery = '';
  categoriaFilter: string | null = null;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadProductos();
  }

  loadProductos(): void {
    this.loading = true;
    this.error = '';
    this.http.get<{ products: AppCatalogProduct[]; total: number }>(
      environment.baseUrl + 'api/app-catalog/products'
    ).subscribe({
      next: (res) => {
        this.productos = res.products ?? [];
        this.loading = false;
      },
      error: (err) => {
        this.error = err.message ?? 'Error al cargar productos';
        this.loading = false;
      }
    });
  }

  actualizarCatalogo(): void {
    this.syncing = true;
    this.syncMsg = '';
    this.syncError = false;
    this.http.post<{ status: string; productos: number; stockActualizado: number; error?: string }>(
      environment.baseUrl + 'api/app-catalog/refresh', {}
    ).subscribe({
      next: (res) => {
        this.syncing = false;
        if (res.status === 'ok') {
          this.syncMsg = `✅ ${res.productos} productos · stock actualizado en ${res.stockActualizado}`;
          this.syncError = false;
          this.loadProductos();
        } else {
          this.syncMsg = `❌ ${res.error ?? 'Error desconocido'}`;
          this.syncError = true;
        }
      },
      error: (err) => {
        this.syncing = false;
        this.syncMsg = `❌ Error: ${err.error?.error ?? err.message}`;
        this.syncError = true;
      }
    });
  }

  get categorias(): string[] {
    const set = new Set<string>();
    this.productos.forEach(p => { if (p.categoryName) set.add(p.categoryName); });
    return Array.from(set).sort();
  }

  get filteredProductos(): AppCatalogProduct[] {
    const q = this.searchQuery.toLowerCase();
    return this.productos.filter(p => {
      const matchSearch = !q ||
        p.name.toLowerCase().includes(q) ||
        p.sku.toLowerCase().includes(q) ||
        (p.categoryName?.toLowerCase().includes(q) ?? false);
      const matchCat = !this.categoriaFilter || p.categoryName === this.categoriaFilter;
      return matchSearch && matchCat;
    });
  }

  formatPrice(p: AppCatalogProduct): string {
    return formatPrice(p.unitPriceWithTaxes);
  }
}
