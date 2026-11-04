import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { CategoriaWeb } from '../../interfaces/categoria-web.model';
import { environment } from '../../../../environments/environment';

interface PreviewCategoriaWoo {
  name: string;
  slug: string;
  parent: number;
}

// Categorías locales (tabla "categories") y preview de lo que se sube a
// WooCommerce por cada una. A diferencia de productos, no hay flag tipo
// webStatus — todo lo que está acá se sincroniza; el filtro real ocurre
// antes, al traer las categorías desde Laudus.
@Component({
  selector: 'app-categorias-web',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  templateUrl: './categorias-web.component.html',
  styleUrls: ['./categorias-web.component.css']
})
export class CategoriasWebComponent implements OnInit {
  categorias: CategoriaWeb[] = [];
  loading = true;
  error = '';
  searchQuery = '';

  laudusIdExpandido: string | null = null;
  cargandoPreview = false;
  previewCache: Record<string, PreviewCategoriaWoo> = {};
  previewError = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.loading = true;
    this.error = '';
    this.http.get<CategoriaWeb[]>(environment.baseUrl + 'api/categorias-web').subscribe({
      next: (data) => { this.categorias = data ?? []; this.loading = false; },
      error: (err) => { this.error = err.message ?? 'Error al cargar categorías'; this.loading = false; }
    });
  }

  get filtradas(): CategoriaWeb[] {
    const q = this.searchQuery.toLowerCase();
    if (!q) return this.categorias;
    return this.categorias.filter(c =>
      c.laudusName.toLowerCase().includes(q) || c.laudusPath.toLowerCase().includes(q));
  }

  verPreview(c: CategoriaWeb): void {
    if (this.laudusIdExpandido === c.laudusId) {
      this.laudusIdExpandido = null;
      return;
    }
    this.laudusIdExpandido = c.laudusId;
    this.previewError = '';
    if (this.previewCache[c.laudusId]) return;

    this.cargandoPreview = true;
    this.http.get<PreviewCategoriaWoo>(`${environment.baseUrl}api/categorias-web/${c.laudusId}/preview-woocommerce`).subscribe({
      next: (data) => {
        this.previewCache[c.laudusId] = data;
        this.cargandoPreview = false;
      },
      error: () => {
        this.previewError = 'No se pudo cargar el detalle de esta categoría';
        this.cargandoPreview = false;
      }
    });
  }
}
