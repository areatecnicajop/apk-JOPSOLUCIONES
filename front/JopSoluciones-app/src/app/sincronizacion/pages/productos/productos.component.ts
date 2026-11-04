import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { LaudusProduct, formatPrice } from '../../interfaces/laudus-product.model';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-productos',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  templateUrl: './productos.component.html',
  styleUrls: ['./productos.component.css']
})
export class ProductosComponent implements OnInit {
  productos: LaudusProduct[] = [];
  loading = true;
  error = '';
  searchQuery = '';
  categoriaFilter: string | null = null;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadProductos();
  }

  loadProductos(): void {
    this.loading = true;
    this.error = '';
    this.http.get<{ products: LaudusProduct[]; total: number }>(
      environment.baseUrl + 'api/products'
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

  get categorias(): string[] {
    const set = new Set<string>();
    this.productos.forEach(p => { if (p.categoryName) set.add(p.categoryName); });
    return Array.from(set).sort();
  }

  get filteredProductos(): LaudusProduct[] {
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

  formatPrice(p: LaudusProduct): string {
    return formatPrice(p.unitPriceWithTaxes);
  }
}
