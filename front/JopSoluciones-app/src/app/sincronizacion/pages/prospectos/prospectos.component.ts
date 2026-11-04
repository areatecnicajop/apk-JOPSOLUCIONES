import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { Prospecto, colorRubro, RUBROS_COLORES } from '../../interfaces/prospecto.model';
import { ProspectoService } from '../../services/prospecto.service';

@Component({
  selector: 'app-prospectos',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  templateUrl: './prospectos.component.html',
  styleUrls: ['./prospectos.component.css']
})
export class ProspectosComponent implements OnInit, OnDestroy {
  todos: Prospecto[] = [];
  filtrados: Prospecto[] = [];
  rubros: string[] = [];
  rubroSeleccionado: string | null = null;
  busqueda = '';
  activeTab: 'lista' | 'mapa' = 'lista';
  detalle: Prospecto | null = null;
  loading = true;
  error: string | null = null;

  private map: any = null;
  private markers: any[] = [];

  constructor(private prospectoService: ProspectoService) {}

  ngOnInit(): void {
    this.prospectoService.getAll().subscribe({
      next: data => {
        this.todos = data;
        this.rubros = [...new Set(data.map(p => p.rubro).filter(r => !!r))].sort() as string[];
        this.aplicarFiltro();
        this.loading = false;
      },
      error: () => {
        this.error = 'No se pudo conectar al servidor';
        this.loading = false;
      }
    });
  }

  aplicarFiltro(): void {
    this.filtrados = this.todos.filter(p => {
      const matchRubro = !this.rubroSeleccionado || p.rubro === this.rubroSeleccionado;
      const q = this.busqueda.toLowerCase();
      const matchBusq = !q ||
        p.nombre.toLowerCase().includes(q) ||
        (p.comuna?.toLowerCase().includes(q) ?? false) ||
        (p.rubro?.toLowerCase().includes(q) ?? false);
      return matchRubro && matchBusq;
    });
    if (this.activeTab === 'mapa') this.renderMarkers();
  }

  get conUbicacion(): Prospecto[] {
    return this.filtrados.filter(p => p.latitud != null && p.longitud != null);
  }

  colorRubro(rubro: string): string {
    return colorRubro(rubro);
  }

  limpiarBusqueda(): void {
    this.busqueda = '';
    this.aplicarFiltro();
  }

  selectRubro(r: string | null): void {
    this.rubroSeleccionado = r;
    this.aplicarFiltro();
  }

  abrirDetalle(p: Prospecto): void {
    this.detalle = p;
    if (this.activeTab === 'mapa') {
      setTimeout(() => this.map?.invalidateSize(), 50);
    }
  }

  cerrarDetalle(): void {
    this.detalle = null;
    if (this.activeTab === 'mapa') {
      setTimeout(() => this.map?.invalidateSize(), 50);
    }
  }

  mapsUrl(p: Prospecto): string {
    if (p.latitud != null && p.longitud != null)
      return `https://www.google.com/maps/search/?api=1&query=${p.latitud},${p.longitud}`;
    return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(p.nombre + ' ' + (p.comuna ?? ''))}`;
  }

  waUrl(p: Prospecto): string {
    const num = (p.telefono ?? '').replace(/[^\d]/g, '');
    return `https://wa.me/${num}`;
  }

  direccionCompleta(p: Prospecto): string {
    return [p.direccion, p.comuna].filter(x => !!x).join(', ');
  }

  rubrosKeys(): string[] {
    return this.rubros;
  }

  setTab(tab: 'lista' | 'mapa'): void {
    this.activeTab = tab;
    if (tab === 'mapa') {
      setTimeout(() => this.initMap(), 100);
    }
  }

  initMap(): void {
    const L = (window as any).L;
    if (!L) return;

    if (this.map) {
      this.map.remove();
      this.map = null;
    }

    this.map = L.map('prospectos-map').setView([-33.45, -70.65], 11);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors'
    }).addTo(this.map);

    this.renderMarkers();
  }

  private renderMarkers(): void {
    const L = (window as any).L;
    if (!L || !this.map) return;

    this.markers.forEach(m => m.remove());
    this.markers = [];

    this.conUbicacion.forEach(p => {
      const color = this.colorRubro(p.rubro ?? '');
      const marker = L.circleMarker([p.latitud, p.longitud], {
        radius: 8,
        fillColor: color,
        color: '#fff',
        weight: 2,
        opacity: 1,
        fillOpacity: 0.85
      });
      marker.bindPopup(`
        <div style="min-width:160px">
          <strong>${p.nombre}</strong><br>
          <span style="color:${color};font-size:0.82rem;font-weight:600">${p.rubro ?? ''}</span>
          ${p.comuna ? `<br><span style="font-size:0.8rem">📌 ${p.comuna}</span>` : ''}
          ${p.telefono ? `<br><span style="font-size:0.8rem">📞 ${p.telefono}</span>` : ''}
        </div>
      `);
      marker.on('click', () => this.abrirDetalle(p));
      marker.addTo(this.map);
      this.markers.push(marker);
    });
  }

  ngOnDestroy(): void {
    if (this.map) {
      this.map.remove();
      this.map = null;
    }
  }
}
