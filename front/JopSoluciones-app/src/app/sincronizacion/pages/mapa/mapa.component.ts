import { Component, AfterViewInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClientModule, HttpClient } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { ClienteService }          from '../../services/cliente.service';
import { CategoriaClienteService } from '../../services/categoria-cliente.service';
import { ProspectoService }        from '../../services/prospecto.service';
import { AppSettingsService }      from '../../services/app-settings.service';
import { GoogleMapsService }       from '../../services/google-maps.service';
import { RutaService }             from '../../services/ruta.service';
import { PlacesAutocompleteDirective, PlaceResult } from '../../directives/places-autocomplete.directive';
import { Cliente, STATUS_COLORS, STATUS_LABELS } from '../../interfaces/cliente.model';
import { CategoriaCliente }        from '../../interfaces/categoria-cliente.model';
import { Prospecto }               from '../../interfaces/prospecto.model';
import { RutaGuardada, RutaParada, NuevaRuta } from '../../interfaces/ruta.model';

interface LatLng { lat: number; lng: number; }

interface Parada {
  prospecto:    Prospecto;
  distanciaAcum: number;
  orden:        number;
}

export interface RutaOpcion {
  id:          number;
  nombre:      string;
  descripcion: string;
  icono:       string;
  color:       string;
  paradas:     Parada[];
  distanciaKm: number | null;
  duracionMin: number | null;
  cargando:    boolean;
  error:       boolean;
  layer:       any;
}

@Component({
  selector: 'app-mapa',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule, PlacesAutocompleteDirective],
  templateUrl: './mapa.component.html',
  styleUrls: ['./mapa.component.css']
})
export class MapaComponent implements AfterViewInit, OnDestroy {

  // ── Datos ─────────────────────────────────────────────────
  clientes:   Cliente[]          = [];
  prospectos: Prospecto[]        = [];
  categorias: CategoriaCliente[] = [];
  loading = true;
  error: string | null = null;

  // ── Filtros mapa normal ───────────────────────────────────
  statusFilter:       string | null = null;
  mostrarClientes     = true;
  mostrarProspectos   = true;
  rubroDropdownOpen   = false;
  rubrosSeleccionados = new Set<string>();  // vacío = todos activos

  // ── Planificador de ruta: siempre visible, sin necesidad de activarlo ──
  puntoInicio: LatLng | null = null;
  puntoFin:    LatLng | null = null;
  labelInicio  = '';
  labelFin     = '';
  rutaRubroFiltro: string | null = null;
  maxParadas   = 15;
  calculando   = false;

  rutas: RutaOpcion[] = [];
  rutaSeleccionada: number | null = null;
  paradaMarkers: any[] = [];

  // ── Modo cálculo: automático (4 opciones) o manual (yo elijo los puntos) ──
  modoCalculo: 'auto' | 'manual' = 'auto';
  paradasManuales: Prospecto[] = [];

  // ── Guardar ruta ────────────────────────────────────────────
  guardandoRuta = false;
  mostrarFormGuardar = false;
  nombreNuevaRuta = '';
  rutaGuardadaMsg = '';

  // ── Rutas guardadas: siempre visible al lado del planificador ──
  cargandoRutasGuardadas = false;
  rutasGuardadas: RutaGuardada[] = [];
  rutaCargadaUuid: string | null = null;

  // ── Leaflet internals ─────────────────────────────────────
  private map:          any = null;
  private markers:      any[] = [];
  private markerInicio: any = null;
  private markerFin:    any = null;
  private clickHandler: any = null;

  get hasGmapsKey(): boolean { return !!this.appSettings.getGoogleMapsKey(); }

  constructor(
    private clienteService:   ClienteService,
    private categoriaService: CategoriaClienteService,
    private prospectoService: ProspectoService,
    private http:        HttpClient,
    private appSettings: AppSettingsService,
    private gmaps:       GoogleMapsService,
    private rutaService: RutaService,
  ) {}

  get rubrosDisponibles(): string[] {
    return [...new Set(this.prospectos.map(p => p.rubro).filter(r => !!r))].sort() as string[];
  }
  get totalMarcadores(): number { return this.markers.length; }
  get rutaActual(): RutaOpcion | null {
    return this.rutas.find(r => r.id === this.rutaSeleccionada) ?? null;
  }

  statusColor(s: string): string { return STATUS_COLORS[s] ?? '#6b7280'; }
  statusLabel(s: string): string { return STATUS_LABELS[s] ?? s; }
  statusKeys(): string[]          { return Object.keys(STATUS_LABELS); }

  private categoriaColor(categoriaId?: string, categoriaName?: string): string {
    const cat = this.categorias.find(c => c.uuid === categoriaId)
             ?? this.categorias.find(c => c.name === categoriaName);
    return cat?.colorHex ? `#${cat.colorHex.replace('#', '')}` : '#6b7280';
  }
  rubroColor(rubro: string): string {
    const cat = this.categorias.find(c => c.name === rubro);
    return cat?.colorHex ? `#${cat.colorHex.replace('#', '')}` : '#9E9E9E';
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.initMap(), 100);
    this.loadData();
  }
  ngOnDestroy(): void {
    if (this.map) { this.map.remove(); this.map = null; }
  }

  loadData(): void {
    this.loading = true;
    this.error   = null;
    forkJoin({
      categorias: this.categoriaService.getAll(),
      clientes:   this.clienteService.getAll(),
      prospectos: this.prospectoService.getAll(),
    }).subscribe({
      next: ({ categorias, clientes, prospectos }) => {
        this.categorias  = categorias;
        this.clientes    = clientes;
        this.prospectos  = prospectos;
        this.loading     = false;
        this.renderMarkers();
      },
      error: () => { this.error = 'No se pudo conectar al backend'; this.loading = false; }
    });
  }

  private initMap(): void {
    const L = (window as any).L;
    if (!L) return;
    this.map = L.map('clientes-map').setView([-33.45, -70.65], 11);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors'
    }).addTo(this.map);
    this.renderMarkers();

    // El planificador de ruta está siempre activo: carga Google Maps en
    // background para el autocomplete, y deja el click del mapa listo para
    // marcar inicio/fin.
    this.gmaps.load();
    this.clickHandler = (e: any) => {
      const latlng: LatLng = { lat: e.latlng.lat, lng: e.latlng.lng };
      const coordLabel = `${latlng.lat.toFixed(5)}, ${latlng.lng.toFixed(5)}`;
      if (!this.puntoInicio) {
        this.puntoInicio = latlng;
        this.labelInicio = coordLabel;
        this.colocarMarkerEspecial(latlng, '🟢', 'Punto de inicio');
      } else if (!this.puntoFin) {
        this.puntoFin = latlng;
        this.labelFin = coordLabel;
        this.colocarMarkerEspecial(latlng, '🔴', 'Punto de llegada');
      }
    };
    this.map.on('click', this.clickHandler);

    this.cargarRutasGuardadas();
  }

  renderMarkers(): void {
    const L = (window as any).L;
    if (!L || !this.map) return;
    this.markers.forEach(m => m.remove());
    this.markers = [];

    if (this.mostrarClientes) {
      this.clientes
        .filter(c => c.latitude && c.longitude && (!this.statusFilter || c.status === this.statusFilter))
        .forEach(c => {
          const color = this.categoriaColor(c.categoryId, c.categoryName);
          const m = L.circleMarker([c.latitude, c.longitude], {
            radius: 10, fillColor: color, color: '#fff', weight: 2, fillOpacity: 0.95
          }).bindTooltip(`${c.name}${c.categoryName ? ' · ' + c.categoryName : ''}`, {
            direction: 'top', offset: [0, -8], opacity: 0.95
          }).bindPopup(`<div style="min-width:170px">
            <strong>${c.name}</strong><br>
            <span style="color:${color};font-size:0.78rem;font-weight:700">${STATUS_LABELS[c.status ?? 'active'] ?? c.status}</span>
            ${c.categoryName ? `<span style="font-size:0.78rem;color:#555"> · ${c.categoryName}</span>` : ''}
            ${c.phone ? `<br>📞 ${c.phone}` : ''}
          </div>`);
          m.addTo(this.map); this.markers.push(m);
        });
    }

    if (this.mostrarProspectos) {
      this.prospectos
        .filter(p => p.latitud && p.longitud && this.isRubroActivo(p.rubro ?? ''))
        .forEach(p => {
          const seleccionandoManual = this.modoCalculo === 'manual';
          const enManual = this.esParadaManual(p);
          const color = this.rubroColor(p.rubro ?? '');
          const m = L.circleMarker([p.latitud, p.longitud], {
            radius: enManual ? 10 : 7,
            fillColor: enManual ? '#6366f1' : color,
            color: enManual ? '#fff' : '#fff',
            weight: enManual ? 3 : 1.5,
            fillOpacity: enManual ? 0.95 : 0.75
          }).bindTooltip(`${p.nombre} · ${p.rubro ?? ''}`, {
            direction: 'top', offset: [0, -6], opacity: 0.95
          }).bindPopup(`<div style="min-width:170px">
            <strong>${p.nombre}</strong><br>
            <span style="color:${color};font-size:0.78rem;font-weight:700">${p.rubro ?? ''}</span>
            <span style="font-size:0.75rem;color:#888"> · Prospecto</span>
            ${p.comuna ? `<br>📌 ${p.comuna}` : ''}
            ${p.telefono ? `<br>📞 ${p.telefono}` : ''}
            ${seleccionandoManual ? `<br><em style="font-size:0.72rem;color:#6366f1">${enManual ? 'Click para quitar de la ruta' : 'Click para agregar a la ruta'}</em>` : ''}
          </div>`);
          if (seleccionandoManual) {
            m.on('click', () => this.toggleParadaManual(p));
          }
          m.addTo(this.map); this.markers.push(m);
        });
    }
  }

  // ══════════════════════════════════════════════════════════
  // MODO MANUAL — el usuario elige los prospectos uno a uno
  // ══════════════════════════════════════════════════════════

  setModoCalculo(modo: 'auto' | 'manual'): void {
    this.modoCalculo = modo;
    this._resetRutas();
    this.renderMarkers();
  }

  esParadaManual(p: Prospecto): boolean {
    return this.paradasManuales.some(x => x.id === p.id);
  }

  toggleParadaManual(p: Prospecto): void {
    const idx = this.paradasManuales.findIndex(x => x.id === p.id);
    if (idx >= 0) this.paradasManuales.splice(idx, 1);
    else this.paradasManuales.push(p);
    this.renderMarkers();
  }

  quitarParadaManual(p: Prospecto): void {
    this.paradasManuales = this.paradasManuales.filter(x => x.id !== p.id);
    this.renderMarkers();
  }

  moverParadaManual(index: number, delta: number): void {
    const nuevo = index + delta;
    if (nuevo < 0 || nuevo >= this.paradasManuales.length) return;
    const arr = [...this.paradasManuales];
    [arr[index], arr[nuevo]] = [arr[nuevo], arr[index]];
    this.paradasManuales = arr;
  }

  async calcularRutaManual(): Promise<void> {
    if (!this.puntoInicio || !this.puntoFin || this.paradasManuales.length === 0) return;
    this.calculando = true;
    this.limpiarCapasRuta();
    this.rutaSeleccionada = null;

    const paradas: Parada[] = this.paradasManuales.map((p, i) => ({
      prospecto: p, distanciaAcum: 0, orden: i + 1
    }));

    const ruta: RutaOpcion = {
      id: 1, nombre: 'Ruta Manual', descripcion: 'Paradas elegidas por ti, en el orden seleccionado',
      icono: '✍️', color: '#6366f1',
      paradas, distanciaKm: null, duracionMin: null, cargando: true, error: false, layer: null
    };
    this.rutas = [ruta];

    await this.calcularOSRM(ruta, this.puntoInicio, this.puntoFin, paradas);
    this.calculando = false;
    this.seleccionarRuta(ruta.id);
  }

  applyStatusFilter(s: string | null): void { this.statusFilter = s; this.renderMarkers(); }
  toggleClientes():   void { this.mostrarClientes   = !this.mostrarClientes;   this.renderMarkers(); }
  toggleProspectos(): void { this.mostrarProspectos = !this.mostrarProspectos; this.renderMarkers(); }

  toggleRubroDropdown(): void { this.rubroDropdownOpen = !this.rubroDropdownOpen; }

  toggleRubro(rubro: string): void {
    if (this.rubrosSeleccionados.has(rubro)) {
      this.rubrosSeleccionados.delete(rubro);
    } else {
      this.rubrosSeleccionados.add(rubro);
    }
    this.rubrosSeleccionados = new Set(this.rubrosSeleccionados); // trigger CD
    this.renderMarkers();
  }

  isRubroActivo(rubro: string): boolean {
    return this.rubrosSeleccionados.size === 0 || this.rubrosSeleccionados.has(rubro);
  }

  get rubrosActivosLabel(): string {
    const n = this.rubrosSeleccionados.size;
    return n === 0 ? 'Todos los rubros' : `${n} rubro${n > 1 ? 's' : ''} seleccionado${n > 1 ? 's' : ''}`;
  }

  seleccionarTodosRubros(): void { this.rubrosSeleccionados.clear(); this.rubrosSeleccionados = new Set(); this.renderMarkers(); }
  deseleccionarTodosRubros(): void { this.rubrosSeleccionados = new Set(this.rubrosDisponibles); this.renderMarkers(); }

  // ══════════════════════════════════════════════════════════
  // PLANIFICADOR DE RUTA
  // ══════════════════════════════════════════════════════════

  onInicioSelected(place: PlaceResult): void {
    this.puntoInicio = { lat: place.lat, lng: place.lng };
    this.labelInicio = place.address;
    this.colocarMarkerEspecial(this.puntoInicio, '🟢', 'Punto de inicio');
    this.map?.setView([place.lat, place.lng], 14);
  }

  onFinSelected(place: PlaceResult): void {
    this.puntoFin  = { lat: place.lat, lng: place.lng };
    this.labelFin  = place.address;
    this.colocarMarkerEspecial(this.puntoFin, '🔴', 'Punto de llegada');
    this.map?.setView([place.lat, place.lng], 14);
  }

  onInicioClear(): void {
    this.puntoInicio = null;
    this.labelInicio = '';
    if (this.markerInicio) { this.markerInicio.remove(); this.markerInicio = null; }
    this._resetRutas();
  }

  onFinClear(): void {
    this.puntoFin = null;
    this.labelFin = '';
    if (this.markerFin) { this.markerFin.remove(); this.markerFin = null; }
    this._resetRutas();
  }

  nuevaBusqueda(): void {
    this._resetRutas();
    this.paradasManuales = [];
    this.renderMarkers();
  }

  private _resetRutas(): void {
    this.mostrarFormGuardar = false;
    this.rutaGuardadaMsg    = '';
    this.rutaCargadaUuid    = null;
    if (this.rutas.length === 0) return;
    this.limpiarCapasRuta();
    this.rutas            = [];
    this.rutaSeleccionada = null;
  }

  private colocarMarkerEspecial(latlng: LatLng, emoji: string, label: string): void {
    const L = (window as any).L;
    const icon = L.divIcon({
      html: `<div style="font-size:28px;line-height:1;filter:drop-shadow(0 2px 4px rgba(0,0,0,0.5))">${emoji}</div>`,
      iconSize: [32, 32], iconAnchor: [16, 28], className: ''
    });
    const m = L.marker([latlng.lat, latlng.lng], { icon }).addTo(this.map).bindPopup(label).openPopup();
    if (emoji === '🟢') { if (this.markerInicio) this.markerInicio.remove(); this.markerInicio = m; }
    else                { if (this.markerFin)    this.markerFin.remove();    this.markerFin    = m; }
  }

  // ── Calcular las 4 rutas ──────────────────────────────────
  async calcularRutas(): Promise<void> {
    if (!this.puntoInicio || !this.puntoFin) return;
    this.calculando = true;
    this.limpiarCapasRuta();
    this.rutaSeleccionada = null;

    const candidatos = this.prospectos.filter(p =>
      p.latitud && p.longitud &&
      (!this.rutaRubroFiltro || p.rubro === this.rutaRubroFiltro)
    );

    const distDirecta = this.haversine(this.puntoInicio, this.puntoFin);

    // Definición de las 4 estrategias
    const estrategias = [
      {
        id: 1, nombre: 'Directa', icono: '⚡', color: '#6366f1',
        descripcion: 'Ruta más rápida, corredor estrecho',
        candidatos: this.filtrarPorCorredor(candidatos, this.puntoInicio, this.puntoFin, Math.max(distDirecta * 0.25, 3)),
        maxP: Math.min(this.maxParadas, 10),
        lado: null as 'norte' | 'sur' | null
      },
      {
        id: 2, nombre: 'Máx. Visitas', icono: '🎯', color: '#10b981',
        descripcion: 'Más paradas, corredor amplio',
        candidatos: this.filtrarPorCorredor(candidatos, this.puntoInicio, this.puntoFin, Math.max(distDirecta * 0.6, 7)),
        maxP: this.maxParadas,
        lado: null as 'norte' | 'sur' | null
      },
      {
        id: 3, nombre: 'Ruta Norte', icono: '⬆️', color: '#f59e0b',
        descripcion: 'Prioriza prospectos al norte',
        candidatos: this.filtrarPorCorredor(candidatos, this.puntoInicio, this.puntoFin, Math.max(distDirecta * 0.5, 6)),
        maxP: this.maxParadas,
        lado: 'norte' as 'norte' | 'sur' | null
      },
      {
        id: 4, nombre: 'Ruta Sur', icono: '⬇️', color: '#ef4444',
        descripcion: 'Prioriza prospectos al sur',
        candidatos: this.filtrarPorCorredor(candidatos, this.puntoInicio, this.puntoFin, Math.max(distDirecta * 0.5, 6)),
        maxP: this.maxParadas,
        lado: 'sur' as 'norte' | 'sur' | null
      },
    ];

    // Inicializar rutas vacías con estado cargando
    this.rutas = estrategias.map(e => ({
      id: e.id, nombre: e.nombre, descripcion: e.descripcion,
      icono: e.icono, color: e.color,
      paradas: [], distanciaKm: null, duracionMin: null,
      cargando: true, error: false, layer: null
    }));

    // Calcular cada ruta
    for (const est of estrategias) {
      const candidatosFiltrados = est.lado
        ? est.candidatos.filter(p => {
            const lado = this.ladoDeLaLinea(
              { lat: p.latitud!, lng: p.longitud! },
              this.puntoInicio!, this.puntoFin!
            );
            return est.lado === 'norte' ? lado >= 0 : lado <= 0;
          })
        : est.candidatos;

      const paradas = this.nearestNeighbor(candidatosFiltrados, this.puntoInicio!, this.puntoFin!, est.maxP);
      const ruta = this.rutas.find(r => r.id === est.id)!;
      ruta.paradas = paradas;

      // Llamar OSRM
      await this.calcularOSRM(ruta, this.puntoInicio!, this.puntoFin!, paradas);
    }

    this.calculando = false;
    // Seleccionar la ruta con más paradas por defecto
    const mejorRuta = [...this.rutas].sort((a, b) => b.paradas.length - a.paradas.length)[0];
    this.seleccionarRuta(mejorRuta.id);
  }

  private calcularOSRM(ruta: RutaOpcion, inicio: LatLng, fin: LatLng, paradas: Parada[]): Promise<void> {
    return new Promise(resolve => {
      const waypoints: LatLng[] = [
        inicio,
        ...paradas.map(p => ({ lat: p.prospecto.latitud!, lng: p.prospecto.longitud! })),
        fin
      ];
      const coords = waypoints.map(w => `${w.lng},${w.lat}`).join(';');
      const url = `https://router.project-osrm.org/route/v1/driving/${coords}?overview=full&geometries=geojson&steps=false`;

      this.http.get<any>(url).subscribe({
        next: (data) => {
          const route = data.routes?.[0];
          if (route) {
            ruta.distanciaKm = +(route.distance / 1000).toFixed(1);
            ruta.duracionMin = Math.round(route.duration / 60);
            const L = (window as any).L;
            ruta.layer = L.geoJSON(route.geometry, {
              style: { color: ruta.color, weight: 4, opacity: 0.85 }
            });
          }
          ruta.cargando = false;
          resolve();
        },
        error: () => {
          // Fallback: línea recta
          const L = (window as any).L;
          const coords2 = waypoints.map(w => [w.lat, w.lng]);
          ruta.layer = (window as any).L.polyline(coords2, { color: ruta.color, weight: 3, dashArray: '8 4' });
          ruta.cargando = false;
          ruta.error    = true;
          resolve();
        }
      });
    });
  }

  seleccionarRuta(id: number): void {
    // Click sobre la ruta ya activa → deseleccionar en vez de repintar
    if (this.rutaSeleccionada === id) {
      this.deseleccionarRuta();
      return;
    }

    // Quitar layer anterior
    if (this.rutaSeleccionada !== null) {
      const anterior = this.rutas.find(r => r.id === this.rutaSeleccionada);
      if (anterior?.layer) anterior.layer.remove();
    }
    this.limpiarParadaMarkers();

    this.rutaSeleccionada = id;
    const ruta = this.rutas.find(r => r.id === id);
    if (!ruta) return;

    // Mostrar layer de esta ruta
    if (ruta.layer && this.map) {
      ruta.layer.addTo(this.map);
      try { this.map.fitBounds(ruta.layer.getBounds(), { padding: [40, 40] }); } catch {}
    }

    // Pintar paradas numeradas
    this.pintarParadas(ruta.paradas, ruta.color);
  }

  // Quita la ruta del mapa (línea + paradas numeradas) sin borrar las
  // opciones calculadas — se puede volver a elegir cualquiera después.
  deseleccionarRuta(): void {
    if (this.rutaSeleccionada !== null) {
      const actual = this.rutas.find(r => r.id === this.rutaSeleccionada);
      if (actual?.layer) actual.layer.remove();
    }
    this.limpiarParadaMarkers();
    this.rutaSeleccionada = null;
  }

  private pintarParadas(paradas: Parada[], colorRuta: string): void {
    const L = (window as any).L;
    if (!L || !this.map) return;
    paradas.forEach(p => {
      const color = this.rubroColor(p.prospecto.rubro ?? '') || colorRuta;
      const icon = L.divIcon({
        html: `<div style="background:${color};border:2px solid #fff;border-radius:50%;
          width:26px;height:26px;display:flex;align-items:center;justify-content:center;
          color:#fff;font-weight:800;font-size:11px;box-shadow:0 2px 6px rgba(0,0,0,0.45)">${p.orden}</div>`,
        iconSize: [26, 26], iconAnchor: [13, 13], className: ''
      });
      const m = L.marker([p.prospecto.latitud, p.prospecto.longitud], { icon })
        .addTo(this.map)
        .bindTooltip(`Parada ${p.orden} · ${p.prospecto.nombre}`, {
          direction: 'top', offset: [0, -10], opacity: 0.95
        })
        .bindPopup(`<div style="min-width:170px">
          <div style="font-size:0.68rem;color:#888;margin-bottom:2px">Parada ${p.orden}</div>
          <strong>${p.prospecto.nombre}</strong><br>
          <span style="color:${color};font-size:0.78rem;font-weight:700">${p.prospecto.rubro ?? ''}</span>
          ${p.prospecto.comuna ? `<br>📌 ${p.prospecto.comuna}` : ''}
          ${p.prospecto.telefono ? `<br>📞 ${p.prospecto.telefono}` : ''}
          ${p.prospecto.direccion ? `<br>🏠 ${p.prospecto.direccion}` : ''}
        </div>`);
      this.paradaMarkers.push(m);
    });
  }

  abrirEnGoogleMaps(): void {
    if (!this.puntoInicio || !this.puntoFin || !this.rutaActual) return;
    const origin = `${this.puntoInicio.lat},${this.puntoInicio.lng}`;
    const dest   = `${this.puntoFin.lat},${this.puntoFin.lng}`;
    const waypts = this.rutaActual.paradas
      .map(p => `${p.prospecto.latitud},${p.prospecto.longitud}`).join('|');
    const url = `https://www.google.com/maps/dir/?api=1&origin=${origin}&destination=${dest}${waypts ? '&waypoints=' + waypts : ''}`;
    window.open(url, '_blank');
  }

  // ══════════════════════════════════════════════════════════
  // GUARDAR / CARGAR RUTAS
  // ══════════════════════════════════════════════════════════

  abrirFormGuardar(): void {
    this.mostrarFormGuardar = true;
    this.rutaGuardadaMsg    = '';
    this.nombreNuevaRuta    = this.rutaActual
      ? `${this.rutaActual.nombre} · ${new Date().toLocaleDateString('es-CL')}`
      : '';
  }

  cancelarGuardar(): void {
    this.mostrarFormGuardar = false;
  }

  guardarRutaActual(): void {
    if (!this.puntoInicio || !this.puntoFin || !this.rutaActual || !this.nombreNuevaRuta.trim()) return;
    this.guardandoRuta = true;

    const paradas: RutaParada[] = this.rutaActual.paradas.map(p => ({
      prospectoUuid: String(p.prospecto.id),
      nombre: p.prospecto.nombre,
      rubro: p.prospecto.rubro,
      lat: p.prospecto.latitud!,
      lng: p.prospecto.longitud!,
      orden: p.orden
    }));

    const nueva: NuevaRuta = {
      uuid: crypto.randomUUID(),
      nombre: this.nombreNuevaRuta.trim(),
      tipo: this.modoCalculo,
      inicioLat: this.puntoInicio.lat, inicioLng: this.puntoInicio.lng, inicioLabel: this.labelInicio,
      finLat: this.puntoFin.lat, finLng: this.puntoFin.lng, finLabel: this.labelFin,
      paradas: JSON.stringify(paradas),
      distanciaKm: this.rutaActual.distanciaKm,
      duracionMin: this.rutaActual.duracionMin,
    };

    this.rutaService.create(nueva).subscribe({
      next: (creada) => {
        this.guardandoRuta      = false;
        this.mostrarFormGuardar = false;
        this.rutaGuardadaMsg    = `✅ Ruta "${creada.nombre}" guardada`;
        this.rutaCargadaUuid    = creada.uuid;
        this.cargarRutasGuardadas();
      },
      error: () => {
        this.guardandoRuta   = false;
        this.rutaGuardadaMsg = '❌ Error al guardar la ruta';
      }
    });
  }

  cargarRutasGuardadas(): void {
    this.cargandoRutasGuardadas = true;
    this.rutaService.getAll().subscribe({
      next: (rutas) => { this.rutasGuardadas = rutas; this.cargandoRutasGuardadas = false; },
      error: () => { this.cargandoRutasGuardadas = false; }
    });
  }

  async cargarRutaGuardada(r: RutaGuardada): Promise<void> {
    this._resetRutas();
    if (this.markerInicio) { this.markerInicio.remove(); this.markerInicio = null; }
    if (this.markerFin)    { this.markerFin.remove();    this.markerFin    = null; }

    this.puntoInicio = { lat: r.inicioLat, lng: r.inicioLng };
    this.puntoFin    = { lat: r.finLat, lng: r.finLng };
    this.labelInicio = r.inicioLabel;
    this.labelFin    = r.finLabel;
    this.colocarMarkerEspecial(this.puntoInicio, '🟢', 'Punto de inicio');
    this.colocarMarkerEspecial(this.puntoFin, '🔴', 'Punto de llegada');

    let paradasGuardadas: RutaParada[] = [];
    try { paradasGuardadas = JSON.parse(r.paradas); } catch {}

    const paradas: Parada[] = paradasGuardadas.map(pg => {
      const prospectoReal = this.prospectos.find(p => String(p.id) === pg.prospectoUuid);
      const prospecto: Prospecto = prospectoReal ?? {
        id: -1, nombre: pg.nombre, rubro: pg.rubro ?? '', latitud: pg.lat, longitud: pg.lng
      };
      return { prospecto, distanciaAcum: 0, orden: pg.orden };
    });

    this.modoCalculo = r.tipo;
    const ruta: RutaOpcion = {
      id: 1, nombre: r.nombre, descripcion: `Ruta guardada el ${new Date(r.createdAt).toLocaleDateString('es-CL')}`,
      icono: r.tipo === 'manual' ? '✍️' : '⚡', color: '#6366f1',
      paradas, distanciaKm: r.distanciaKm ?? null, duracionMin: r.duracionMin ?? null,
      cargando: true, error: false, layer: null
    };
    this.rutas = [ruta];
    this.rutaCargadaUuid = r.uuid;

    await this.calcularOSRM(ruta, this.puntoInicio, this.puntoFin, paradas);
    this.seleccionarRuta(ruta.id);
  }

  eliminarRutaGuardada(r: RutaGuardada, event: Event): void {
    event.stopPropagation();
    if (!confirm(`¿Eliminar la ruta "${r.nombre}"?`)) return;
    this.rutaService.delete(r.uuid).subscribe({
      next: () => { this.rutasGuardadas = this.rutasGuardadas.filter(x => x.uuid !== r.uuid); }
    });
  }

  // ── Algoritmos ────────────────────────────────────────────

  private filtrarPorCorredor(candidatos: Prospecto[], inicio: LatLng, fin: LatLng, radio: number): Prospecto[] {
    return candidatos.filter(p => {
      const dist = this.distanciaASegmento({ lat: p.latitud!, lng: p.longitud! }, inicio, fin);
      return dist <= radio;
    });
  }

  // Determina si un punto está al norte (+) o al sur (-) de la línea inicio→fin
  private ladoDeLaLinea(p: LatLng, a: LatLng, b: LatLng): number {
    return (b.lng - a.lng) * (p.lat - a.lat) - (b.lat - a.lat) * (p.lng - a.lng);
  }

  private nearestNeighbor(candidatos: Prospecto[], inicio: LatLng, fin: LatLng, maxP: number): Parada[] {
    const resultado: Parada[] = [];
    const pendientes = [...candidatos];
    let actual: LatLng = inicio;
    let distAcum = 0;

    while (resultado.length < maxP && pendientes.length > 0) {
      let minDist = Infinity, minIdx = -1;
      pendientes.forEach((p, i) => {
        const d = this.haversine(actual, { lat: p.latitud!, lng: p.longitud! });
        if (d < minDist) { minDist = d; minIdx = i; }
      });
      if (minIdx === -1) break;

      const elegido = pendientes.splice(minIdx, 1)[0];
      distAcum += minDist;

      const distAlFin = this.haversine({ lat: elegido.latitud!, lng: elegido.longitud! }, fin);
      const distDirectaRestante = this.haversine(actual, fin);
      if (distAlFin > distDirectaRestante * 2.5) { pendientes.unshift(elegido); break; }

      resultado.push({ prospecto: elegido, distanciaAcum: distAcum, orden: resultado.length + 1 });
      actual = { lat: elegido.latitud!, lng: elegido.longitud! };
    }
    return resultado;
  }

  private haversine(a: LatLng, b: LatLng): number {
    const R = 6371;
    const dLat = (b.lat - a.lat) * Math.PI / 180;
    const dLng = (b.lng - a.lng) * Math.PI / 180;
    const h = Math.sin(dLat/2)**2 + Math.cos(a.lat*Math.PI/180)*Math.cos(b.lat*Math.PI/180)*Math.sin(dLng/2)**2;
    return R * 2 * Math.asin(Math.sqrt(h));
  }

  private distanciaASegmento(p: LatLng, a: LatLng, b: LatLng): number {
    const dx = b.lng - a.lng, dy = b.lat - a.lat;
    const len2 = dx*dx + dy*dy;
    if (len2 === 0) return this.haversine(p, a);
    let t = ((p.lng - a.lng)*dx + (p.lat - a.lat)*dy) / len2;
    t = Math.max(0, Math.min(1, t));
    return this.haversine(p, { lat: a.lat + t*dy, lng: a.lng + t*dx });
  }

  private limpiarCapasRuta(): void {
    this.rutas.forEach(r => { if (r.layer) { r.layer.remove(); r.layer = null; } });
    this.limpiarParadaMarkers();
  }

  private limpiarParadaMarkers(): void {
    this.paradaMarkers.forEach(m => m.remove());
    this.paradaMarkers = [];
  }
}
