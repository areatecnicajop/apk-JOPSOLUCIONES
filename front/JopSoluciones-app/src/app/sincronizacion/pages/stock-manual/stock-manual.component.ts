import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { ProductoWeb } from '../../interfaces/producto-web.model';
import { environment } from '../../../../environments/environment';

interface PreviewWooCommerce {
  sku: string;
  name: string;
  price: number;
  regular_price: string;
  stock_quantity: number | null;
  status: string;
  manage_stock: boolean;
  short_description: string | null;
  categories: { id: number }[];
}

// Productos que van a la web (webStatus=true) con su código y stock, y el
// override manual para forzar stock 0 — afecta directo lo que se manda a
// WooCommerce (TransactionBDtoWPService.convertLaudusToLocal).
@Component({
  selector: 'app-stock-manual',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  templateUrl: './stock-manual.component.html',
  styleUrls: ['./stock-manual.component.css']
})
export class StockManualComponent implements OnInit {
  productos: ProductoWeb[] = [];
  loading = true;
  error = '';
  searchQuery = '';
  guardandoSku: string | null = null;

  skuExpandido: string | null = null;
  cargandoPreview = false;
  previewCache: Record<string, PreviewWooCommerce> = {};
  previewError = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.loading = true;
    this.error = '';
    this.http.get<ProductoWeb[]>(environment.baseUrl + 'api/productos-web').subscribe({
      next: (data) => { this.productos = data ?? []; this.loading = false; },
      error: (err) => { this.error = err.message ?? 'Error al cargar productos'; this.loading = false; }
    });
  }

  get filtrados(): ProductoWeb[] {
    const q = this.searchQuery.toLowerCase();
    if (!q) return this.productos;
    return this.productos.filter(p =>
      p.sku.toLowerCase().includes(q) || p.description.toLowerCase().includes(q));
  }

  toggleForceZero(p: ProductoWeb, value: boolean): void {
    this.guardandoSku = p.sku;
    this.http.put<{ sku: string; forceZeroStock: boolean }>(
      `${environment.baseUrl}api/productos-web/${p.sku}/force-zero-stock`,
      { value }
    ).subscribe({
      next: () => {
        p.forceZeroStock = value;
        p.stockEfectivo = value ? 0 : p.stockReal;
        delete this.previewCache[p.sku]; // el preview cambia con el override, forzar recarga
        this.guardandoSku = null;
      },
      error: () => { this.guardandoSku = null; }
    });
  }

  verPreview(p: ProductoWeb): void {
    if (this.skuExpandido === p.sku) {
      this.skuExpandido = null;
      return;
    }
    this.skuExpandido = p.sku;
    this.previewError = '';
    if (this.previewCache[p.sku]) return; // ya la tenemos, no la vuelve a pedir

    this.cargandoPreview = true;
    this.http.get<PreviewWooCommerce>(`${environment.baseUrl}api/productos-web/${p.sku}/preview-woocommerce`).subscribe({
      next: (data) => {
        this.previewCache[p.sku] = data;
        this.cargandoPreview = false;
      },
      error: () => {
        this.previewError = 'No se pudo cargar el detalle de este producto';
        this.cargandoPreview = false;
      }
    });
  }
}
