import { Component, ChangeDetectorRef, OnInit, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import { ApiService } from '../../services/api.service';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { Order } from '../../interfaces/order.model';
import { CommonModule, DecimalPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription, firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../auth/auth.service';
import { ClientesComponent } from '../clientes/clientes.component';
import { MapaComponent } from '../mapa/mapa.component';
import { CategoriasComponent } from '../categorias/categorias.component';
import { ProductosApkComponent } from '../productos-apk/productos-apk.component';
import { CategoriasWebComponent } from '../categorias-web/categorias-web.component';
import { PedidoService, Pedido } from '../../services/pedido.service';
import { AppSettingsService } from '../../services/app-settings.service';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';

interface TestResult {
  status: 'success' | 'error' | 'loading';
  statusCode?: number;
  body: string;
  duration?: number;
}

interface FalabellaProductRow {
  sku: string;
  name: string;
  price: number;
  stock: number;
  categoryName: string | null;
  idWP: string | null;
  enFalabella: boolean;
}

interface FalabellaCategoria {
  id: string;
  path: string;
}

interface FalabellaAtributo {
  label: string;
  name: string;
  feedName: string;
  mandatory: boolean;
  description: string;
  attributeType: string; // 'value' | 'option'
  inputType: string;     // 'textfield' | 'numberfield' | 'dropdown' | ...
  options: { name: string }[];
}

interface FalabellaMarca {
  id: string;
  name: string;
}

interface FalabellaFeed {
  label: string;
  feedId: string;
  checking: boolean;
  result?: TestResult;
}

interface SyncProductoRow {
  sku: string;
  name: string;
  price: number;
  stock: number;
  categoryName: string | null;
  enWooCommerce: boolean;
  stockConocido: boolean;
  forceZeroStock: boolean;
  stockEfectivo: number;
  stockWebEnviado: number | null;
  stockCambio: boolean;          // cambió de stock en la última actualización desde Laudus
  stockAnterior: number | null;  // stock que tenía antes de esa actualización (null = no existía)
}

// Una foto dentro del editor: 'e' = ya está en la web, 'n' = nueva (aún sin subir).
interface FotoItem {
  key: string;
  tipo: 'e' | 'n';
  id?: number;       // solo 'e'
  url: string;       // URL de la web ('e') o vista previa local ('n')
  nombre: string;
  blob?: Blob;       // solo 'n': imagen ya normalizada (JPEG 1000×1000, fondo blanco)
  archivo?: Blob;    // solo 'n': foto original sin tocar, para poder volver a editarla
  ajuste?: FotoAjusteValores;  // solo 'n' editada: zoom/posición/giro con que se generó
}

interface FotoAjusteValores {
  zoom: number;   // 1 = la foto completa dentro del cuadrado
  ox: number;     // desplazamiento horizontal en px del lienzo de 1000×1000
  oy: number;     // desplazamiento vertical
  rot: number;    // giros de 90° (0-3)
}

interface FotoAjuste extends FotoAjusteValores {
  indice: number;
  origen: Blob | null;
  fuente: ImageBitmap | null;
  cargando: boolean;
  error: string;
}

interface FotoEditor {
  sku: string;
  nombre: string;
  cargando: boolean;
  guardando: boolean;
  items: FotoItem[];
  idsOriginales: number[];   // orden en que estaban en la web al abrir
  max: number;
  error: string;
  exito: string;
  avisos: string[];
}

interface LaudusCustomField {
  customFieldId: number;
  entity: string;
  name: string;
  label: string;
  type: string;
  length: number;
  precision: number;
  nullable: boolean;
  defaultValue: string;
  enumValues: string[] | null;
  required: boolean;
  tabIndex: number | null;
}

interface TestEndpoint {
  name: string;
  method: 'GET' | 'POST';
  url: string;
  description: string;
  body?: any;
  loading: boolean;
  result?: TestResult;
  warehouseId?: string;
  showSettings: boolean;
  storageKey?: string;
}

const STATUS_LABELS: Record<string, { label: string; color: string }> = {
  'pending':    { label: 'Pendiente',   color: '#f0ad4e' },
  'processing': { label: 'Procesando',  color: '#5bc0de' },
  'on-hold':    { label: 'En espera',   color: '#aaa' },
  'completed':  { label: 'Completado',  color: '#5cb85c' },
  'cancelled':  { label: 'Cancelado',   color: '#d9534f' },
  'refunded':   { label: 'Reembolsado', color: '#9b59b6' },
  'failed':     { label: 'Fallido',     color: '#e74c3c' },
};

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [CommonModule, HttpClientModule, FormsModule, ClientesComponent, MapaComponent, CategoriasComponent, ProductosApkComponent, CategoriasWebComponent, DecimalPipe, DatePipe, MatTableModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatChipsModule, MatTooltipModule],
  templateUrl: './home-page.component.html',
  styleUrls: ['./home-page.component.css'],
  providers: [ApiService]
})
export class HomePageComponent implements OnInit, OnDestroy {
  includeImages: boolean = false;
  forceUpdate: boolean = false;
  listStockUpdate: string[] = [];

  loading: boolean = false;
  loadingConsole: boolean = false;
  loadingButtons: boolean = false;
  response: string = '';

  // Imágenes
  imageFolders: { folder: string; images: { name: string; size: number; url: string }[]; count: number }[] = [];
  loadingImages: boolean = false;
  imageSearch: string = '';
  pendingFile: File | null = null;
  pendingFiles: File[] = [];
  uploadSku: string = '';
  previewImage: { name: string; size: number; url: string } | null = null;

  // Folder list state
  folderSort: 'name' | 'count' = 'name';
  folderSortAsc: boolean = true;
  folderPage: number = 0;
  readonly folderPageSize = 50;

  // Folder detail state
  selectedFolder: { folder: string; images: { name: string; size: number; url: string }[]; count: number } | null = null;
  selectedImages: Set<string> = new Set();
  detailSearch: string = '';

  // Folder management
  newFolderName: string = '';
  creatingFolder: boolean = false;
  suggestedFolders: string[] = [];
  loadingSuggested: boolean = false;
  suggestedSearch: string = '';
  showSuggested: boolean = false;

  orders: Order[] = [];
  loadingOrders: boolean = false;
  orderFilters: Record<string, boolean> = {
    pending:    true,
    processing: true,
    'on-hold':  true,
    completed:  true,
    cancelled:  true,
    refunded:   false,
    failed:     false,
  };
  expandedOrderId: number | null = null;

  private apiSubscription!: Subscription;
  private ws!: WebSocket;

  showImagesModal: boolean = false;
  // "Panel de Sync" oculto del menú (no eliminado): poner en true para volver a mostrarlo.
  // Al reactivarlo, restaurar activeView = 'sync' como vista inicial.
  showPanelSync: boolean = false;
  // Apartado antiguo "Imágenes → Galería" oculto (no borrado): las fotos ahora se editan desde Productos → 📷.
  // Para volver a mostrarlo basta poner esta bandera en true.
  showApartadoImagenes: boolean = false;
  openSection: string = 'sync';
  activeView: string = 'sync-productos';

  // Config
  gmapsKeyInput: string = '';
  gmapsSaved = false;

  testEndpoints: TestEndpoint[] = [
    {
      name: 'Stock desde Laudus',
      method: 'GET',
      url: `${environment.baseUrl}test/laudus/stock`,
      description: 'Lee el stock RAW directamente desde la API de Laudus. Solo lectura.',
      loading: false,
      showSettings: false,
      warehouseId: localStorage.getItem('test_bodega') ?? '8',
      storageKey: 'test_bodega'
    },
    {
      name: 'Categorías desde Laudus',
      method: 'GET',
      url: `${environment.baseUrl}test/laudus/categorias`,
      description: 'Lee las categorías RAW directamente desde la API de Laudus. Solo lectura.',
      loading: false,
      showSettings: false
    },
    {
      name: 'Pedidos de WooCommerce',
      method: 'GET',
      url: `${environment.baseUrl}allOrders`,
      description: 'Obtiene todos los pedidos actuales desde WooCommerce. Solo lectura.',
      loading: false,
      showSettings: false
    }
  ];

  falabellaEndpoints: TestEndpoint[] = [
    {
      name: 'Árbol de Categorías',
      method: 'GET',
      url: `${environment.baseUrl}test/falabella/categorias`,
      description: 'GetCategoryTree — trae el árbol de categorías de Falabella Seller Center. Solo lectura.',
      loading: false,
      showSettings: false
    },
    {
      name: 'Marcas',
      method: 'GET',
      url: `${environment.baseUrl}test/falabella/marcas`,
      description: 'GetBrands — trae el listado de marcas disponibles. Solo lectura.',
      loading: false,
      showSettings: false
    },
    {
      name: 'Productos',
      method: 'GET',
      url: `${environment.baseUrl}test/falabella/productos`,
      description: 'GetProducts — trae productos ya publicados en el marketplace. Solo lectura.',
      loading: false,
      showSettings: false
    },
    {
      name: 'Órdenes',
      method: 'GET',
      url: `${environment.baseUrl}test/falabella/ordenes`,
      description: 'GetOrders — trae las órdenes más recientes del marketplace. Solo lectura.',
      loading: false,
      showSettings: false
    }
  ];

  // ── Falabella: Productos ────────────────────────────────────────
  falabellaProductos: FalabellaProductRow[] = [];
  loadingFalabellaProductos = false;
  falabellaProductosError = '';
  falabellaSearch = '';
  falabellaColumns = ['estado', 'sku', 'name', 'price', 'stock', 'categoryName', 'acciones'];

  falabellaSelected: FalabellaProductRow | null = null;

  falabellaCategoriasFlat: FalabellaCategoria[] = [];
  falabellaCategoriasLoading = false;
  falabellaCategoriaSearch = '';
  falabellaCategoriaSeleccionada: FalabellaCategoria | null = null;

  falabellaAtributos: FalabellaAtributo[] = [];
  falabellaAtributosLoading = false;
  falabellaExtraAttrs: Record<string, string> = {};

  // FeedNames que ya se llenan con campos dedicados (nombre, marca, descripción,
  // categoría, SKU) o que se autocompletan (Variation) — no se piden de nuevo.
  private readonly falabellaAttrsExcluidos = new Set([
    'Name', 'Brand', 'Description', 'PrimaryCategory', 'SellerSku', 'Variation',
    'ConditionType', 'PackageWidth', 'PackageLength', 'PackageHeight', 'PackageWeight'
  ]);

  falabellaMarcas: FalabellaMarca[] = [];
  falabellaMarcasLoading = false;
  falabellaMarcaSearch = '';
  falabellaMarcaSeleccionada: FalabellaMarca | null = null;

  falabellaForm = {
    conditionType: 'Nuevo',
    packageHeight: null as number | null,
    packageWidth: null as number | null,
    packageLength: null as number | null,
    packageWeight: null as number | null,
    ancho: '',
    largo: '',
    price: null as number | null,
    specialPrice: null as number | null,
    description: '',
    name: ''
  };
  falabellaPrecioLaudus: number | null = null;
  falabellaPrecioLoading = false;
  falabellaDescripcionLoading = false;

  falabellaFeeds: FalabellaFeed[] = [];
  falabellaUploading = false;
  falabellaUploadingStage = '';
  falabellaUploadError = '';

  falabellaImagenesWoo: string[] = [];
  falabellaImagenesLoading = false;
  falabellaImagenesUploading = false;
  falabellaImagenesError = '';

  // ── Config Laudus: Custom Fields ─────────────────────────────────
  laudusCustomFields: LaudusCustomField[] = [];
  laudusLoading = false;
  laudusError = '';
  laudusSearch = '';
  laudusColumns = ['customFieldId', 'entity', 'name', 'type', 'length', 'precision', 'required', 'nullable', 'acciones'];

  laudusFormMode: 'create' | 'edit' | null = null;
  laudusForm: Partial<LaudusCustomField> & { enumValuesText?: string } = {};
  laudusSaving = false;
  laudusSaveError = '';
  laudusDeletingId: number | null = null;

  // ── Sincronizador: Productos (estado + preview WooCommerce) ──────
  syncProductos: SyncProductoRow[] = [];
  syncProductosLoading = false;
  syncProductosError = '';
  syncProductosSearch = '';
  syncProductosColumns = ['sel', 'estado', 'sku', 'name', 'price', 'stock', 'forzar', 'efectivo', 'enviado', 'diferencia', 'fotos', 'acciones'];

  // Stock: selección y envío a WooCommerce (solo stock, ver SyncProductosController.enviarStock)
  // Filtros tipo Excel por columna: col → valores permitidos (sin entrada = sin filtro).
  syncColFiltros: Record<string, Set<string>> = {};
  // Menú de filtro abierto desde el título de una columna (posición fija en pantalla para que la tabla no lo recorte).
  syncMenu: { col: string; top: number; left: number; maxHeight: number; busca: string; valores: { valor: string; n: number }[] } | null = null;
  // SKU → cantidad de fotos en la web (null mientras carga o si no se pudo consultar)
  syncFotosConteo: Record<string, number> | null = null;
  syncFotosCargando = false;
  syncFotosError = '';
  fotoEditor: FotoEditor | null = null;
  fotoDragIndex: number | null = null;
  // Visor de ajuste de una foto (zoom, mover, girar)
  fotoAjuste: FotoAjuste | null = null;
  @ViewChild('ajusteCanvas') ajusteCanvas?: ElementRef<HTMLCanvasElement>;
  private ajusteArrastre: { x: number; y: number; ox: number; oy: number } | null = null;
  syncSeleccion = new Set<string>();
  syncActualizandoStock = false;
  syncEnviandoStock = false;
  syncEnvioProgreso = '';
  syncEnvioTotal = 0;
  syncEnvioHechos = 0;
  syncEnvioOk = 0;
  syncEnvioErr = 0;
  syncEnvioActual = '';
  syncStockMsg = '';
  syncStockMsgError = false;

  syncSelected: SyncProductoRow | null = null;
  syncDatosWoo: any = null;
  syncDatosWooLoading = false;
  syncPreviewLaudus: any = null;
  syncPreviewLaudusLoading = false;
  syncEnviando = false;
  syncEnviarError = '';
  syncEnviarOk = '';

  syncTitulo = '';
  syncDescripcionCorta = '';
  syncDescripcionLarga = '';
  syncGuardandoDescripcion = false;
  syncDescripcionError = '';
  syncDescripcionOk = '';

  // Pedidos internos
  pedidosInternos: Pedido[] = [];
  loadingPedidos = false;
  pedidoStatusFilter = '';
  selectedPedido: Pedido | null = null;

  // Auto-sync
  syncAutoStatus: any = null;
  syncAutoInterval = 60;

  constructor(
    private apiService: ApiService,
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
    public auth: AuthService,
    private pedidoService: PedidoService,
    private appSettings: AppSettingsService
  ) {}

  ngOnInit(): void {
    this.connectWebSocket();
    this.loadSyncAutoStatus();
    if (this.activeView === 'sync-productos') this.loadSyncProductos();
    this.gmapsKeyInput = this.appSettings.getGoogleMapsKey();
    this.gmapsSaved    = !!this.gmapsKeyInput;
  }

  ngOnDestroy(): void {
    if (this.apiSubscription) this.apiSubscription.unsubscribe();
    if (this.ws) this.ws.close();
  }

  private connectWebSocket(): void {
    const wsUrl = environment.baseUrl.replace(/^http/, 'ws') + 'ws/console';
    this.ws = new WebSocket(wsUrl);

    this.ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        const msg: string = data.message ?? event.data;
        this.response = this.response ? msg + '\n' + this.response : msg;
        this.cdr.detectChanges();
      } catch {
        this.response = event.data + '\n' + this.response;
        this.cdr.detectChanges();
      }
    };

    this.ws.onclose = () => {
      // Reconectar tras 3s si se cae
      setTimeout(() => this.connectWebSocket(), 3000);
    };
  }

  toggleSection(section: string): void {
    this.openSection = this.openSection === section ? '' : section;
  }

  saveGmapsKey(): void {
    this.appSettings.setGoogleMapsKey(this.gmapsKeyInput);
    this.gmapsSaved = true;
    setTimeout(() => this.gmapsSaved = false, 3000);
  }

  setView(view: string): void {
    this.activeView = view;
    if (view === 'orders') this.loadOrders();
    if (view === 'pedidos-internos') this.loadPedidosInternos();
    if (view === 'images' && this.imageFolders.length === 0) this.loadImages();
  }

  clearConsole(): void {
    this.response = '';
  }

  get consoleLines(): string[] {
    return this.response.split('\n').filter(l => l.trim().length > 0).reverse();
  }

  lineClass(line: string): string {
    if (line.startsWith('❌') || line.startsWith('🚫') || line.includes('FALLO')) return 'line-error';
    if (line.startsWith('✅')) return 'line-success';
    if (line.startsWith('⚠️ STOCK CERO')) return 'line-zero';
    if (line.startsWith('⚠️') || line.startsWith('🚫 NO EN WEB') || line.includes('SIN STOCK')) return 'line-warn';
    if (line.startsWith('📦')) return 'line-stock';
    if (line.startsWith('🔍')) return 'line-sync';
    if (line.startsWith('📢')) return 'line-info';
    return 'line-default';
  }

  // ── Helpers pedidos ────────────────────────────────────────────

  get filteredOrders(): Order[] {
    return this.orders.filter(o => this.orderFilters[o.status] !== false);
  }

  get activeFilterCount(): number {
    return Object.values(this.orderFilters).filter(v => v).length;
  }

  statusInfo(status: string) {
    return STATUS_LABELS[status] ?? { label: status, color: '#888' };
  }

  toggleOrder(id: number): void {
    this.expandedOrderId = this.expandedOrderId === id ? null : id;
  }

  orderStatusKeys(): string[] {
    return Object.keys(STATUS_LABELS);
  }

  countByStatus(status: string): number {
    return this.orders.filter(o => o.status === status).length;
  }

  // ── Sincronizador ──────────────────────────────────────────────

  updateStock = () => {
    this.clearConsole();
    this.loadingConsole = true;
    this.loadingButtons = true;

    this.apiSubscription = this.apiService.updateStock().subscribe(
      (updatedSkus: string[]) => {
        if (updatedSkus.length === 0) {
          this.response = `📢 No hay cambios en el stock.\n${this.response}`;
          this.loadingConsole = false;
          this.loadingButtons = false;
          this.cdr.detectChanges();
          return;
        }
        this.response = `📦 Stock actualizado para ${updatedSkus.length} productos.\n${this.response}`;
        this.listStockUpdate = updatedSkus;
        this.loadingConsole = false;
        this.forceUpdate = true;
        this.cdr.detectChanges();
        this.startSynchro(this.listStockUpdate);
      },
      (error) => {
        this.response = `❌ Error al actualizar stock: ${error.message}\n${this.response}`;
        this.loadingConsole = false;
        this.cdr.detectChanges();
      }
    );
  };

  askImages(): void {
    this.showImagesModal = true;
  }

  confirmSync(withImages: boolean): void {
    this.showImagesModal = false;
    this.includeImages = withImages;
    this.startSynchro();
  }

  startSynchro = (skus: string[] = []) => {
    this.loadingConsole = true;
    this.loadingButtons = true;

    this.apiSubscription = this.apiService
      .startSynchro(skus, this.forceUpdate, this.includeImages)
      .subscribe({
        next: () => {
          this.response = `✅ Sincronización de productos completada.\n${this.response}`;
          this.loadingConsole = false;
          this.forceUpdate = false;
          this.checkIfCanEnableButtons();
          this.cdr.detectChanges();
        },
        error: (err) => {
          this.response = `❌ Error en API: ${err.message}\n${this.response}`;
          this.loadingConsole = false;
          this.forceUpdate = false;
          this.checkIfCanEnableButtons();
          this.cdr.detectChanges();
        }
      });
  };

  startSynchroCategories = () => {
    this.clearConsole();
    this.loadingConsole = true;
    this.loadingButtons = true;

    this.apiSubscription = this.apiService.syncroCategories().subscribe({
      next: (data) => {
        this.response = `Respuesta de API: ${data}\n${this.response}`;
        this.loadingConsole = false;
        this.checkIfCanEnableButtons();
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.response = `❌ Error en API: ${err.message}\n${this.response}`;
        this.loadingConsole = false;
        this.checkIfCanEnableButtons();
        this.cdr.detectChanges();
      }
    });
  };

  // ── Imágenes ───────────────────────────────────────────────────

  totalImages(): number {
    return this.imageFolders.reduce((sum, f) => sum + (f.images?.length ?? 0), 0);
  }

  private sortedFilteredFolders() {
    const q = this.imageSearch.toLowerCase().trim();
    let list = this.imageFolders.map(f => ({ ...f, images: f.images ?? [] }));
    if (q) list = list.filter(f => f.folder.toLowerCase().includes(q));
    list.sort((a, b) => {
      const cmp = this.folderSort === 'name'
        ? a.folder.localeCompare(b.folder)
        : (a.images.length - b.images.length);
      return this.folderSortAsc ? cmp : -cmp;
    });
    return list;
  }

  pagedFolders() {
    const all = this.sortedFilteredFolders();
    const start = this.folderPage * this.folderPageSize;
    return all.slice(start, start + this.folderPageSize);
  }

  totalFolderPages(): number {
    return Math.ceil(this.sortedFilteredFolders().length / this.folderPageSize);
  }

  setSortFolder(by: 'name' | 'count'): void {
    if (this.folderSort === by) this.folderSortAsc = !this.folderSortAsc;
    else { this.folderSort = by; this.folderSortAsc = by === 'name'; }
    this.folderPage = 0;
  }

  folderBarPct(f: any): number {
    const max = Math.max(...this.imageFolders.map(x => x.images?.length ?? 0), 1);
    return Math.round(((f.images?.length ?? 0) / max) * 100);
  }

  openFolder(f: any): void {
    this.selectedFolder = { ...f, images: f.images ?? [] };
    this.selectedImages = new Set();
    this.detailSearch = '';
  }

  closeFolder(): void {
    this.selectedFolder = null;
    this.selectedImages = new Set();
    this.detailSearch = '';
  }

  filteredDetailImages() {
    const q = this.detailSearch.toLowerCase().trim();
    const imgs = this.selectedFolder?.images ?? [];
    return q ? imgs.filter(i => i.name.toLowerCase().includes(q)) : imgs;
  }

  toggleImgSelect(img: { url: string }): void {
    if (this.selectedImages.has(img.url)) this.selectedImages.delete(img.url);
    else this.selectedImages.add(img.url);
  }

  toggleSelectAll(): void {
    const imgs = this.selectedFolder?.images ?? [];
    if (this.selectedImages.size === imgs.length) this.selectedImages = new Set();
    else this.selectedImages = new Set(imgs.map(i => i.url));
  }

  imgUrl(path: string): string {
    return environment.baseUrl.replace(/\/$/, '') + path;
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  loadImages(): void {
    this.loadingImages = true;
    this.http.get<any[]>(`${environment.baseUrl}images`).subscribe({
      next: (data) => {
        // Backend puede devolver lista plana [{name, size, url}] o agrupada [{folder, images, count}]
        if (data.length > 0 && data[0]['folder'] !== undefined) {
          // Ya viene agrupado
          this.imageFolders = data.map(f => ({ ...f, images: f.images ?? [] }));
        } else {
          // Lista plana — agrupar por carpeta según el nombre "carpeta/archivo.ext"
          const map = new Map<string, { name: string; size: number; url: string }[]>();
          for (const item of data) {
            const parts = (item.name as string).split('/');
            const folder = parts.length > 1 ? parts.slice(0, -1).join('/') : '/';
            const fileName = parts[parts.length - 1];
            if (!map.has(folder)) map.set(folder, []);
            map.get(folder)!.push({ name: fileName, size: item.size, url: item.url });
          }
          this.imageFolders = Array.from(map.entries()).map(([folder, images]) => ({
            folder, images, count: images.length
          }));
        }
        if (this.selectedFolder) {
          const updated = this.imageFolders.find(f => f.folder === this.selectedFolder!.folder);
          this.selectedFolder = updated ? { ...updated } : null;
        }
        this.loadingImages = false;
        this.cdr.detectChanges();
      },
      error: () => { this.loadingImages = false; this.cdr.detectChanges(); }
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) this.pendingFile = input.files[0];
  }

  onFileSelectedDetail(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) this.pendingFiles = Array.from(input.files);
    input.value = '';
  }

  uploadFilesToFolder(): void {
    if (!this.pendingFiles.length || !this.selectedFolder) return;
    const folder = this.selectedFolder.folder;
    const uploads = this.pendingFiles.map(file => {
      const form = new FormData();
      form.append('file', file);
      form.append('sku', folder);
      return this.http.post<any>(`${environment.baseUrl}images/upload`, form).toPromise();
    });
    Promise.all(uploads)
      .then(() => { this.pendingFiles = []; this.loadImages(); })
      .catch(e => alert('Error al subir: ' + e.message));
  }

  uploadImage(): void {
    if (!this.pendingFile || !this.uploadSku) return;
    const form = new FormData();
    form.append('file', this.pendingFile);
    form.append('sku', this.uploadSku.trim());
    this.http.post<any>(`${environment.baseUrl}images/upload`, form).subscribe({
      next: () => { this.pendingFile = null; this.uploadSku = ''; this.loadImages(); },
      error: (e) => alert('Error al subir: ' + e.message)
    });
  }

  deleteImage(img: { name: string; size: number; url: string }): void {
    if (!confirm(`¿Eliminar "${img.name}"?`)) return;
    const relPath = img.url.replace('/images/file/', '');
    this.http.delete(`${environment.baseUrl}images/file/${relPath}`).subscribe({
      next: () => {
        this.imageFolders = this.imageFolders
          .map(f => ({ ...f, images: (f.images ?? []).filter(i => i.url !== img.url) }));
        if (this.selectedFolder) {
          this.selectedFolder = { ...this.selectedFolder, images: this.selectedFolder.images.filter(i => i.url !== img.url) };
        }
        this.selectedImages.delete(img.url);
        this.cdr.detectChanges();
      },
      error: (e) => alert('Error al eliminar: ' + e.message)
    });
  }

  bulkDeleteImages(): void {
    const count = this.selectedImages.size;
    if (!confirm(`¿Eliminar ${count} imagen${count !== 1 ? 'es' : ''}?`)) return;
    const urls = Array.from(this.selectedImages);
    const deletes = urls.map(url => {
      const relPath = url.replace('/images/file/', '');
      return this.http.delete(`${environment.baseUrl}images/file/${relPath}`).toPromise();
    });
    Promise.all(deletes)
      .then(() => {
        this.imageFolders = this.imageFolders
          .map(f => ({ ...f, images: (f.images ?? []).filter(i => !urls.includes(i.url)) }));
        if (this.selectedFolder) {
          this.selectedFolder = { ...this.selectedFolder, images: this.selectedFolder.images.filter(i => !urls.includes(i.url)) };
        }
        this.selectedImages = new Set();
        this.cdr.detectChanges();
      })
      .catch(e => alert('Error al eliminar: ' + e.message));
  }

  createFolder(): void {
    const name = this.newFolderName.trim().toUpperCase();
    if (!name) return;
    this.creatingFolder = true;
    this.http.post<any>(`${environment.baseUrl}images/folder`, { name }).subscribe({
      next: () => {
        this.newFolderName = '';
        this.creatingFolder = false;
        this.loadImages();
        this.cdr.detectChanges();
      },
      error: (e) => {
        this.creatingFolder = false;
        alert('Error al crear carpeta: ' + (e.error?.error ?? e.message));
        this.cdr.detectChanges();
      }
    });
  }

  deleteFolder(f: any, event?: Event): void {
    event?.stopPropagation();
    if (!confirm(`¿Eliminar carpeta "${f.folder}" y todas sus imágenes (${f.images?.length ?? 0})?`)) return;
    this.http.delete(`${environment.baseUrl}images/folder/${encodeURIComponent(f.folder)}`).subscribe({
      next: () => {
        this.imageFolders = this.imageFolders.filter(x => x.folder !== f.folder);
        if (this.selectedFolder?.folder === f.folder) this.selectedFolder = null;
        this.cdr.detectChanges();
      },
      error: (e) => alert('Error al eliminar carpeta: ' + (e.error?.error ?? e.message))
    });
  }

  loadSuggestedFolders(): void {
    this.loadingSuggested = true;
    this.showSuggested = true;
    this.http.get<string[]>(`${environment.baseUrl}images/suggested-folders`).subscribe({
      next: data => { this.suggestedFolders = data; this.loadingSuggested = false; this.cdr.detectChanges(); },
      error: () => { this.loadingSuggested = false; this.cdr.detectChanges(); }
    });
  }

  filteredSuggested(): string[] {
    const q = this.suggestedSearch.toLowerCase().trim();
    return q ? this.suggestedFolders.filter(s => s.toLowerCase().includes(q)) : this.suggestedFolders;
  }

  createFolderFromSuggestion(sku: string): void {
    this.http.post<any>(`${environment.baseUrl}images/folder`, { name: sku }).subscribe({
      next: () => {
        this.suggestedFolders = this.suggestedFolders.filter(s => s !== sku);
        this.imageFolders = [...this.imageFolders, { folder: sku, images: [], count: 0 }];
        this.cdr.detectChanges();
      },
      error: (e) => alert('Error: ' + (e.error?.error ?? e.message))
    });
  }

  createAllSuggested(): void {
    const list = this.filteredSuggested();
    if (!list.length) return;
    if (!confirm(`¿Crear ${list.length} carpetas?`)) return;
    const calls = list.map(sku =>
      this.http.post<any>(`${environment.baseUrl}images/folder`, { name: sku }).toPromise()
    );
    Promise.all(calls).then(() => {
      this.suggestedFolders = this.suggestedFolders.filter(s => !list.includes(s));
      const newFolders = list.map(sku => ({ folder: sku, images: [], count: 0 }));
      this.imageFolders = [...this.imageFolders, ...newFolders];
      this.cdr.detectChanges();
    }).catch(e => alert('Error al crear algunas carpetas'));
  }

  loadOrders = () => {
    this.loadingOrders = true;
    this.apiSubscription = this.apiService.getOrders().subscribe({
      next: (data: Order[]) => {
        this.orders = data;
        this.loadingOrders = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.loadingOrders = false;
        this.cdr.detectChanges();
      }
    });
  };

  // ── Testing ────────────────────────────────────────────────────

  saveParam(test: TestEndpoint): void {
    if (test.storageKey && test.warehouseId !== undefined) {
      localStorage.setItem(test.storageKey, test.warehouseId);
    }
    test.showSettings = false;
  }

  runTest(test: TestEndpoint): void {
    test.loading = true;
    test.result = { status: 'loading', body: '' };
    const start = Date.now();

    const url = test.warehouseId !== undefined
      ? `${test.url}?warehouseId=${test.warehouseId}`
      : test.url;

    const request$ = test.method === 'GET'
      ? this.http.get(url, { observe: 'response', responseType: 'text' })
      : this.http.post(url, test.body ?? {}, { observe: 'response', responseType: 'text' });

    request$.subscribe({
      next: (res: any) => {
        const duration = Date.now() - start;
        let body = res.body ?? '';
        try { body = JSON.stringify(JSON.parse(body), null, 2); } catch {}
        test.result = { status: 'success', statusCode: res.status, body, duration };
        test.loading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        const duration = Date.now() - start;
        test.result = {
          status: 'error',
          statusCode: err.status,
          body: err.error ?? err.message ?? 'Error desconocido',
          duration
        };
        test.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  private checkIfCanEnableButtons(): void {
    if (!this.loadingConsole) {
      this.loadingButtons = false;
      this.cdr.detectChanges();
    }
  }

  // ── Falabella: Productos ────────────────────────────────────────

  private asArray(x: any): any[] {
    return Array.isArray(x) ? x : (x ? [x] : []);
  }

  loadFalabellaProductos(): void {
    this.loadingFalabellaProductos = true;
    this.falabellaProductosError = '';
    this.http.get<FalabellaProductRow[]>(`${environment.baseUrl}falabella/productos`).subscribe({
      next: rows => { this.falabellaProductos = rows; this.loadingFalabellaProductos = false; this.cdr.detectChanges(); },
      error: () => { this.falabellaProductosError = 'No se pudo cargar el catálogo'; this.loadingFalabellaProductos = false; this.cdr.detectChanges(); }
    });
  }

  get filteredFalabellaProductos(): FalabellaProductRow[] {
    const q = this.falabellaSearch.toLowerCase();
    if (!q) return this.falabellaProductos;
    return this.falabellaProductos.filter(p =>
      p.sku.toLowerCase().includes(q) ||
      p.name?.toLowerCase().includes(q) ||
      (p.categoryName?.toLowerCase().includes(q) ?? false));
  }

  private static readonly FALABELLA_DEFAULTS_KEY = 'falabella_defaults';

  saveFalabellaDefaults(): void {
    // selectFalabellaCategoria/selectFalabellaMarca llaman esto justo después
    // de resetear falabellaExtraAttrs a {} (antes de que carguen los atributos
    // de la nueva categoría) — guardar ese {} tal cual pisaba para siempre los
    // valores ya guardados. Por eso acá se hace merge contra lo ya persistido
    // en vez de sobreescribir: un objeto vacío nunca borra nada, solo lo que
    // realmente tenga claves (un input editado de verdad) pisa lo anterior.
    const anterior = this.loadFalabellaDefaults();
    const defaults = {
      categoria: this.falabellaCategoriaSeleccionada,
      marca: this.falabellaMarcaSeleccionada,
      conditionType: this.falabellaForm.conditionType,
      packageHeight: this.falabellaForm.packageHeight,
      packageWidth: this.falabellaForm.packageWidth,
      packageLength: this.falabellaForm.packageLength,
      packageWeight: this.falabellaForm.packageWeight,
      extraAttrs: { ...(anterior?.extraAttrs ?? {}), ...this.falabellaExtraAttrs }
    };
    localStorage.setItem(HomePageComponent.FALABELLA_DEFAULTS_KEY, JSON.stringify(defaults));
  }

  // Default de fábrica — la mayoría del catálogo es repuestos de compresor
  // GENERICO, así que se precarga esto de una hasta que el usuario guarde
  // algo distinto (lo guardado en localStorage siempre tiene prioridad).
  private static readonly FALABELLA_HARDCODED_DEFAULTS = {
    categoria: {
      id: '2105',
      path: 'Construcción y ferretería > Herramientas|equipos eléctricos > Accesorios|repuestos para herramientas eléctricas > Accesorios de maquinarias compresoras'
    },
    marca: { id: '11011', name: 'GENERICO' },
    conditionType: 'Nuevo',
    extraAttrs: {
      PlazoDeDisponibilidadDeRepuestos: 'Inmediata',
      PlazoDeDisponibilidadDeServicioTecnico: 'Inmediata',
      DuracionEnCondicionesPrevisiblesDeUso: '2 años'
    }
  };

  private loadFalabellaDefaults(): any {
    const raw = localStorage.getItem(HomePageComponent.FALABELLA_DEFAULTS_KEY);
    if (!raw) return HomePageComponent.FALABELLA_HARDCODED_DEFAULTS;
    try { return JSON.parse(raw); } catch { return HomePageComponent.FALABELLA_HARDCODED_DEFAULTS; }
  }

  openFalabellaProduct(p: FalabellaProductRow): void {
    this.falabellaSelected = p;
    this.falabellaCategoriaSeleccionada = null;
    this.falabellaAtributos = [];
    this.falabellaMarcaSeleccionada = null;
    this.falabellaCategoriaSearch = '';
    this.falabellaMarcaSearch = '';
    this.falabellaForm = { conditionType: 'Nuevo', packageHeight: null, packageWidth: null, packageLength: null, packageWeight: null, ancho: '', largo: '', price: null, specialPrice: null, description: '', name: p.name ?? '' };
    this.falabellaExtraAttrs = {};
    this.falabellaFeeds = [];
    this.falabellaUploadError = '';
    this.falabellaImagenesWoo = [];
    this.falabellaImagenesError = '';
    if (!this.falabellaCategoriasFlat.length) this.loadFalabellaCategorias();
    if (!this.falabellaMarcas.length) this.loadFalabellaMarcas();
    this.cargarImagenesWoo();

    if (p.enFalabella) {
      // Ya existe allá — se precarga con lo que hay DE VERDAD en Falabella,
      // no con la sugerencia basada en Laudus (que ya quedó desactualizada).
      this.cargarDatosFalabella(p.sku);
      return;
    }

    this.cargarPrecioSugerido();
    this.cargarDescripcionActual();

    // Precarga lo que quedó guardado la última vez que se subió un producto —
    // así no hay que volver a elegir categoría/marca/atributos administrativos
    // cada vez. Solo se pisan si el usuario cambia algo a mano.
    const defaults = this.loadFalabellaDefaults();
    if (defaults) {
      if (defaults.marca) this.selectFalabellaMarca(defaults.marca);
      if (defaults.conditionType) this.falabellaForm.conditionType = defaults.conditionType;
      if (defaults.packageHeight != null) this.falabellaForm.packageHeight = defaults.packageHeight;
      if (defaults.packageWidth != null) this.falabellaForm.packageWidth = defaults.packageWidth;
      if (defaults.packageLength != null) this.falabellaForm.packageLength = defaults.packageLength;
      if (defaults.packageWeight != null) this.falabellaForm.packageWeight = defaults.packageWeight;
      if (defaults.categoria) {
        this.selectFalabellaCategoria(defaults.categoria);
        // selectFalabellaCategoria ya limpia falabellaExtraAttrs y dispara la
        // carga de atributos — una vez lleguen, se rellenan con lo guardado.
        // Se mezcla con el default de fábrica como piso: si lo guardado quedó
        // corrupto o incompleto (bug de guardado ya arreglado), esto lo autorepara.
        const guardados = { ...HomePageComponent.FALABELLA_HARDCODED_DEFAULTS.extraAttrs, ...defaults.extraAttrs };
        const esperar = () => {
          if (this.falabellaAtributosLoading) { setTimeout(esperar, 200); return; }
          this.falabellaExtraAttrs = { ...guardados };
          this.cdr.detectChanges();
        };
        esperar();
      }
    }
  }

  // Precarga el formulario con los datos REALES que ya están en Falabella
  // (categoría, marca, precio, oferta, condición, dimensiones, Diámetro/Ancho/
  // Largo y los atributos administrativos) — para cuando el producto ya existe
  // y lo que se va a hacer es un ProductUpdate, no un ProductCreate a ciegas.
  cargarDatosFalabella(sku: string): void {
    this.falabellaPrecioLoading = true;
    this.falabellaDescripcionLoading = true;
    this.http.get<any>(`${environment.baseUrl}falabella/productos/${sku}/datos-falabella`).subscribe({
      next: datos => {
        this.falabellaPrecioLoading = false;
        this.falabellaDescripcionLoading = false;
        if (!datos.existe) { this.cdr.detectChanges(); return; }

        this.falabellaForm.price = datos.price ? Math.round(parseFloat(datos.price)) : null;
        this.falabellaForm.specialPrice = datos.specialPrice ? Math.round(parseFloat(datos.specialPrice)) : null;
        this.falabellaForm.description = datos.description ?? '';
        this.falabellaForm.name = datos.name ?? this.falabellaForm.name;

        const pd = datos.productData ?? {};
        this.falabellaForm.conditionType = pd.ConditionType || 'Nuevo';
        this.falabellaForm.packageHeight = pd.PackageHeight ? Math.round(parseFloat(pd.PackageHeight)) : null;
        this.falabellaForm.packageWidth = pd.PackageWidth ? Math.round(parseFloat(pd.PackageWidth)) : null;
        this.falabellaForm.packageLength = pd.PackageLength ? Math.round(parseFloat(pd.PackageLength)) : null;
        this.falabellaForm.packageWeight = pd.PackageWeight ? parseFloat(pd.PackageWeight) : null;
        this.falabellaForm.ancho = pd.Ancho ?? '';
        this.falabellaForm.largo = pd.Largo ?? '';
        // Solo estas 4 — el resto de ProductData (Model, NameCn, garantías, etc.)
        // no tiene campo propio en el formulario; al no reenviarlo en el Update
        // queda intacto (Falabella no borra lo que no se manda).
        this.falabellaExtraAttrs = {
          Diametro: pd.Diametro ?? '',
          PlazoDeDisponibilidadDeRepuestos: pd.PlazoDeDisponibilidadDeRepuestos ?? '',
          PlazoDeDisponibilidadDeServicioTecnico: pd.PlazoDeDisponibilidadDeServicioTecnico ?? '',
          DuracionEnCondicionesPrevisiblesDeUso: pd.DuracionEnCondicionesPrevisiblesDeUso ?? ''
        };

        const esperarCategoria = () => {
          if (this.falabellaCategoriasLoading) { setTimeout(esperarCategoria, 200); return; }
          const encontrada = this.falabellaCategoriasFlat.find(c => c.id === datos.primaryCategoryId);
          this.falabellaCategoriaSeleccionada = encontrada
            ?? (datos.primaryCategoryId ? { id: datos.primaryCategoryId, path: `Categoría ID ${datos.primaryCategoryId}` } : null);
          if (this.falabellaCategoriaSeleccionada) this.loadFalabellaAtributos(this.falabellaCategoriaSeleccionada.id);
          this.cdr.detectChanges();
        };
        esperarCategoria();

        const esperarMarca = () => {
          if (this.falabellaMarcasLoading) { setTimeout(esperarMarca, 200); return; }
          const encontrada = this.falabellaMarcas.find(m => m.name.toUpperCase() === (datos.brand ?? '').toUpperCase());
          this.falabellaMarcaSeleccionada = encontrada ?? (datos.brand ? { id: '', name: datos.brand } : null);
          this.cdr.detectChanges();
        };
        esperarMarca();

        this.cdr.detectChanges();
      },
      error: () => { this.falabellaPrecioLoading = false; this.falabellaDescripcionLoading = false; this.cdr.detectChanges(); }
    });
  }


  closeFalabellaProduct(): void {
    this.falabellaSelected = null;
  }

  loadFalabellaCategorias(): void {
    this.falabellaCategoriasLoading = true;
    this.http.get<any>(`${environment.baseUrl}falabella/categorias`).subscribe({
      next: resp => {
        const roots = this.asArray(resp?.SuccessResponse?.Body?.Categories?.Category);
        this.falabellaCategoriasFlat = this.flattenCategorias(roots, '');
        this.falabellaCategoriasLoading = false;
        this.cdr.detectChanges();
      },
      error: () => { this.falabellaCategoriasLoading = false; this.cdr.detectChanges(); }
    });
  }

  private flattenCategorias(nodes: any[], prefix: string): FalabellaCategoria[] {
    let out: FalabellaCategoria[] = [];
    for (const node of nodes) {
      const path = prefix ? `${prefix} > ${node.Name}` : node.Name;
      out.push({ id: String(node.CategoryId), path });
      const children = this.asArray(node.Children?.Category);
      if (children.length) out = out.concat(this.flattenCategorias(children, path));
    }
    return out;
  }

  get filteredCategorias(): FalabellaCategoria[] {
    const q = this.falabellaCategoriaSearch.toLowerCase();
    if (!q) return [];
    return this.falabellaCategoriasFlat.filter(c => c.path.toLowerCase().includes(q)).slice(0, 40);
  }

  selectFalabellaCategoria(cat: FalabellaCategoria): void {
    this.falabellaCategoriaSeleccionada = cat;
    this.falabellaCategoriaSearch = cat.path;
    this.falabellaExtraAttrs = {};
    this.loadFalabellaAtributos(cat.id);
    this.saveFalabellaDefaults();
  }

  loadFalabellaAtributos(categoryId: string): void {
    this.falabellaAtributosLoading = true;
    this.http.get<any>(`${environment.baseUrl}falabella/categorias/atributos`, { params: { primaryCategory: categoryId } }).subscribe({
      next: resp => {
        const attrs = this.asArray(resp?.SuccessResponse?.Body?.Attribute);
        this.falabellaAtributos = attrs
          .filter((a: any) => a.isMandatory === '1' && !this.falabellaAttrsExcluidos.has(a.FeedName))
          .map((a: any) => ({
            label: a.Label,
            name: a.Name,
            feedName: a.FeedName,
            mandatory: true,
            description: a.Description,
            attributeType: a.AttributeType,
            inputType: a.InputType,
            options: this.asArray(a.Options?.Option).map((o: any) => ({ name: o.Name }))
          }));
        this.falabellaAtributosLoading = false;
        this.cdr.detectChanges();
      },
      error: () => { this.falabellaAtributosLoading = false; this.cdr.detectChanges(); }
    });
  }

  loadFalabellaMarcas(): void {
    this.falabellaMarcasLoading = true;
    this.http.get<any>(`${environment.baseUrl}falabella/marcas`).subscribe({
      next: resp => {
        const brands = this.asArray(resp?.SuccessResponse?.Body?.Brands);
        this.falabellaMarcas = brands.map((b: any) => ({ id: b.Brand.BrandId, name: b.Brand.Name }));
        this.falabellaMarcasLoading = false;
        this.cdr.detectChanges();
      },
      error: () => { this.falabellaMarcasLoading = false; this.cdr.detectChanges(); }
    });
  }

  get filteredMarcas(): FalabellaMarca[] {
    const q = this.falabellaMarcaSearch.toLowerCase();
    if (!q) return [];
    return this.falabellaMarcas.filter(m => m.name.toLowerCase().includes(q)).slice(0, 40);
  }

  selectFalabellaMarca(m: FalabellaMarca): void {
    this.falabellaMarcaSeleccionada = m;
    this.falabellaMarcaSearch = m.name;
    this.saveFalabellaDefaults();
  }

  // Falabella exige el título entre 20-100 caracteres y la descripción con al
  // menos 50 (visto en su propio panel de "Puntaje de contenido") — se valida
  // acá para no enterarse recién después de subir.
  private stripHtml(html: string): string {
    return (html ?? '').replace(/<[^>]*>/g, '').trim();
  }

  get falabellaTituloLength(): number {
    return (this.falabellaForm.name ?? '').trim().length;
  }

  get falabellaDescripcionLength(): number {
    return this.stripHtml(this.falabellaForm.description).length;
  }

  get falabellaCamposFaltantes(): string[] {
    const faltan: string[] = [];
    if (!this.falabellaCategoriaSeleccionada) faltan.push('Categoría');
    if (!this.falabellaMarcaSeleccionada) faltan.push('Marca');
    const f = this.falabellaForm;
    if (f.packageHeight == null) faltan.push('Alto');
    if (f.packageWidth == null) faltan.push('Ancho');
    if (f.packageLength == null) faltan.push('Largo');
    if (f.packageWeight == null) faltan.push('Peso');
    const tituloLen = this.falabellaTituloLength;
    if (tituloLen < 20 || tituloLen > 100) faltan.push(`Título (tiene ${tituloLen}, debe tener 20-100)`);
    const descLen = this.falabellaDescripcionLength;
    if (descLen < 50) faltan.push(`Descripción (tiene ${descLen}, mínimo 50)`);
    for (const a of this.falabellaAtributos) {
      if (!this.falabellaExtraAttrs[a.feedName]?.trim()) faltan.push(a.label);
    }
    return faltan;
  }

  get falabellaFormValid(): boolean {
    return this.falabellaCamposFaltantes.length === 0;
  }

  private pushFalabellaFeed(label: string, resp: any): void {
    const requestId = resp?.SuccessResponse?.Head?.RequestId || resp?.ErrorResponse?.Head?.RequestId || '';
    if (requestId) {
      this.falabellaFeeds.unshift({ label, feedId: requestId, checking: false });
    }
  }

  subirFalabellaProducto(): void {
    if (!this.falabellaSelected || !this.falabellaFormValid) return;
    this.falabellaUploading = true;
    this.falabellaUploadError = '';
    this.saveFalabellaDefaults();
    const body = {
      primaryCategory: this.falabellaCategoriaSeleccionada!.id,
      brand: this.falabellaMarcaSeleccionada!.name,
      conditionType: this.falabellaForm.conditionType,
      packageHeight: this.falabellaForm.packageHeight,
      packageWidth: this.falabellaForm.packageWidth,
      packageLength: this.falabellaForm.packageLength,
      packageWeight: this.falabellaForm.packageWeight,
      ancho: this.falabellaForm.ancho,
      largo: this.falabellaForm.largo,
      price: this.falabellaForm.price,
      specialPrice: this.falabellaForm.specialPrice,
      description: this.falabellaForm.description,
      name: this.falabellaForm.name,
      extraAttributes: this.falabellaExtraAttrs
    };
    // Un solo llamado bloqueante: crea el producto, espera a que ese feed
    // termine, y recién ahí sube las imágenes (con su propio reintento) —
    // puede tardar hasta ~1 minuto, por eso el mensaje de carga cambia.
    this.falabellaUploadingStage = 'Creando producto y esperando confirmación...';
    this.http.post<any>(`${environment.baseUrl}falabella/productos/${this.falabellaSelected.sku}/subir-completo`, body).subscribe({
      next: resp => {
        this.pushFalabellaFeed(`Producto (${resp.producto.action ?? 'ProductCreate'})`, JSON.parse(resp.producto.body));
        // El feed no terminó en error/cancelado ⇒ el producto quedó creado o
        // actualizado allá — se refleja solo, sin depender del clic manual de
        // "Marcar como confirmado" (misma referencia que la fila de la tabla).
        if (resp.producto.feedStatus !== 'Error' && resp.producto.feedStatus !== 'Canceled') {
          this.falabellaSelected!.enFalabella = true;
        }
        if (resp.imagenes?.feed) {
          const estado = resp.imagenes.asociadas ? `asociadas en ${resp.imagenes.intentos} intento(s)` : `sin asociar tras ${resp.imagenes.intentos} intento(s)`;
          this.pushFalabellaFeed(`Imágenes (Image) — ${estado}`, JSON.parse(resp.imagenes.feed));
        }
        if (resp.imagenes && !resp.imagenes.asociadas) {
          this.falabellaImagenesError = resp.imagenes.error ?? 'Las imágenes no quedaron asociadas.';
        }
        this.falabellaUploading = false;
        this.cdr.detectChanges();
      },
      error: err => {
        this.falabellaUploadError = JSON.stringify(err.error ?? err.message, null, 2);
        this.falabellaUploading = false;
        this.cdr.detectChanges();
      }
    });
  }

  consultarFalabellaFeed(feed: FalabellaFeed): void {
    feed.checking = true;
    const start = Date.now();
    this.http.get(`${environment.baseUrl}falabella/feeds/${feed.feedId}`, { observe: 'response', responseType: 'text' }).subscribe({
      next: res => {
        let body = res.body ?? '';
        try { body = JSON.stringify(JSON.parse(body), null, 2); } catch {}
        feed.result = { status: 'success', statusCode: res.status, body, duration: Date.now() - start };
        feed.checking = false;
        this.cdr.detectChanges();
      },
      error: err => {
        feed.result = { status: 'error', statusCode: err.status, body: err.error ?? err.message, duration: Date.now() - start };
        feed.checking = false;
        this.cdr.detectChanges();
      }
    });
  }

  confirmarFalabellaSubida(): void {
    if (!this.falabellaSelected) return;
    this.http.post<any>(`${environment.baseUrl}falabella/productos/${this.falabellaSelected.sku}/confirmar`, {}).subscribe({
      next: () => {
        this.falabellaSelected!.enFalabella = true;
        this.cdr.detectChanges();
      }
    });
  }

  cargarImagenesWoo(): void {
    if (!this.falabellaSelected) return;
    this.falabellaImagenesLoading = true;
    this.http.get<string[]>(`${environment.baseUrl}falabella/productos/${this.falabellaSelected.sku}/imagenes-woocommerce`).subscribe({
      next: urls => { this.falabellaImagenesWoo = urls; this.falabellaImagenesLoading = false; this.cdr.detectChanges(); },
      error: () => { this.falabellaImagenesLoading = false; this.cdr.detectChanges(); }
    });
  }

  // Precio de Laudus + 30% mínimo redondeado hacia arriba al centenar, para
  // cubrir la comisión de Falabella — se precarga pero el usuario lo puede editar.
  cargarPrecioSugerido(): void {
    if (!this.falabellaSelected) return;
    this.falabellaPrecioLoading = true;
    this.http.get<{ precioLaudus: number; precioSugerido: number }>(`${environment.baseUrl}falabella/productos/${this.falabellaSelected.sku}/precio-sugerido`).subscribe({
      next: res => {
        this.falabellaPrecioLaudus = res.precioLaudus;
        this.falabellaForm.price = res.precioSugerido;
        this.falabellaPrecioLoading = false;
        this.cdr.detectChanges();
      },
      error: () => { this.falabellaPrecioLoading = false; this.cdr.detectChanges(); }
    });
  }

  // Descripción que se va a mandar como <Description> — sale de Laudus
  // (webDescription o description), pero queda editable antes de subir.
  cargarDescripcionActual(): void {
    if (!this.falabellaSelected) return;
    this.falabellaDescripcionLoading = true;
    this.http.get<{ descripcion: string }>(`${environment.baseUrl}falabella/productos/${this.falabellaSelected.sku}/descripcion`).subscribe({
      next: res => {
        this.falabellaForm.description = res.descripcion ?? '';
        this.falabellaDescripcionLoading = false;
        this.cdr.detectChanges();
      },
      error: () => { this.falabellaDescripcionLoading = false; this.cdr.detectChanges(); }
    });
  }

  subirImagenesFalabella(): void {
    if (!this.falabellaSelected) return;
    this.falabellaImagenesUploading = true;
    this.falabellaImagenesError = '';
    // El backend ahora espera a que el feed termine, verifica con GetProducts
    // que las imágenes de verdad quedaron asociadas, y reintenta solo si hace
    // falta — puede tardar unos segundos (hasta ~45s en el peor caso).
    this.http.post<any>(`${environment.baseUrl}falabella/productos/${this.falabellaSelected.sku}/imagenes`, {}).subscribe({
      next: resp => {
        if (resp.feed) this.pushFalabellaFeed(`Imágenes (Image) — asociadas en ${resp.intentos} intento(s)`, JSON.parse(resp.feed));
        this.falabellaImagenesUploading = false;
        this.cdr.detectChanges();
      },
      error: err => {
        const body = err.error ?? {};
        this.falabellaImagenesError = typeof body === 'string' ? body : JSON.stringify(body, null, 2);
        if (body.feed) this.pushFalabellaFeed(`Imágenes (Image) — sin asociar tras ${body.intentos} intento(s)`, JSON.parse(body.feed));
        this.falabellaImagenesUploading = false;
        this.cdr.detectChanges();
      }
    });
  }

  // ── Config Laudus: Custom Fields ─────────────────────────────────

  loadLaudusCustomFields(): void {
    this.laudusLoading = true;
    this.laudusError = '';
    this.http.get<LaudusCustomField[]>(`${environment.baseUrl}laudus/custom-fields`).subscribe({
      next: fields => { this.laudusCustomFields = fields; this.laudusLoading = false; this.cdr.detectChanges(); },
      error: err => { this.laudusError = err.error?.error ?? err.message; this.laudusLoading = false; this.cdr.detectChanges(); }
    });
  }

  get filteredLaudusCustomFields(): LaudusCustomField[] {
    const q = this.laudusSearch.toLowerCase();
    if (!q) return this.laudusCustomFields;
    return this.laudusCustomFields.filter(f =>
      f.name.toLowerCase().includes(q) || f.entity.toLowerCase().includes(q) || (f.label ?? '').toLowerCase().includes(q));
  }

  openLaudusCreate(): void {
    this.laudusFormMode = 'create';
    this.laudusForm = { entity: 'products', type: 'C', length: 20, precision: 0, nullable: true, required: false, defaultValue: '' };
    this.laudusSaveError = '';
  }

  openLaudusEdit(f: LaudusCustomField): void {
    this.laudusFormMode = 'edit';
    this.laudusForm = { ...f, enumValuesText: f.enumValues?.join(', ') ?? '' };
    this.laudusSaveError = '';
  }

  closeLaudusForm(): void {
    this.laudusFormMode = null;
  }

  saveLaudusCustomField(): void {
    if (!this.laudusForm.entity || !this.laudusForm.name || !this.laudusForm.type) return;
    this.laudusSaving = true;
    this.laudusSaveError = '';

    const enumValues = this.laudusForm.enumValuesText?.trim()
      ? this.laudusForm.enumValuesText.split(',').map(v => v.trim()).filter(v => v)
      : null;

    const body = {
      entity: this.laudusForm.entity,
      name: this.laudusForm.name,
      label: this.laudusForm.label ?? '',
      type: this.laudusForm.type,
      length: this.laudusForm.length ?? 0,
      precision: this.laudusForm.precision ?? 0,
      nullable: this.laudusForm.nullable ?? true,
      defaultValue: this.laudusForm.defaultValue ?? '',
      enumValues,
      required: this.laudusForm.required ?? false,
      tabIndex: this.laudusForm.tabIndex ?? null
    };

    const req$ = this.laudusFormMode === 'edit' && this.laudusForm.customFieldId
      ? this.http.put(`${environment.baseUrl}laudus/custom-fields/${this.laudusForm.customFieldId}`, body)
      : this.http.post(`${environment.baseUrl}laudus/custom-fields`, body);

    req$.subscribe({
      next: () => {
        this.laudusSaving = false;
        this.closeLaudusForm();
        this.loadLaudusCustomFields();
      },
      error: err => {
        this.laudusSaveError = JSON.stringify(err.error ?? err.message, null, 2);
        this.laudusSaving = false;
        this.cdr.detectChanges();
      }
    });
  }

  deleteLaudusCustomField(f: LaudusCustomField): void {
    const ok = confirm(
      `¿Borrar el custom field "${f.name}" (${f.entity})?\n\nEsto es IRREVERSIBLE del lado de Laudus y puede afectar datos ya cargados en productos que usen este campo.`
    );
    if (!ok) return;
    this.laudusDeletingId = f.customFieldId;
    this.http.delete(`${environment.baseUrl}laudus/custom-fields/${f.customFieldId}`).subscribe({
      next: () => {
        this.laudusDeletingId = null;
        this.loadLaudusCustomFields();
      },
      error: err => {
        this.laudusDeletingId = null;
        alert('No se pudo borrar: ' + (err.error?.error ?? err.message));
        this.cdr.detectChanges();
      }
    });
  }

  // ── Sincronizador: Productos ─────────────────────────────────────

  loadSyncProductos(alCargar?: () => void): void {
    this.syncProductosLoading = true;
    this.syncProductosError = '';
    this.http.get<SyncProductoRow[]>(`${environment.baseUrl}sync/productos`).subscribe({
      next: rows => {
        this.syncProductos = rows;
        this.syncProductosLoading = false;
        alCargar?.();
        this.cdr.detectChanges();
        this.cargarResumenFotos();
      },
      error: () => { this.syncProductosError = 'No se pudo cargar el catálogo'; this.syncProductosLoading = false; this.cdr.detectChanges(); }
    });
  }

  get filteredSyncProductos(): SyncProductoRow[] {
    const q = this.syncProductosSearch.toLowerCase();
    const cols = Object.keys(this.syncColFiltros);
    return this.syncProductos.filter(p =>
      cols.every(c => this.syncValoresDe(p, c).some(v => this.syncColFiltros[c].has(v))) &&
      (!q ||
        p.sku.toLowerCase().includes(q) ||
        p.name?.toLowerCase().includes(q) ||
        (p.categoryName?.toLowerCase().includes(q) ?? false)));
  }

  // ── Filtros tipo Excel desde el título de cada columna ───────────
  // Cada columna ofrece la lista de sus valores (con cuántos productos tienen cada uno), con casillas.
  // Sin filtro = todos los valores marcados. Los filtros de varias columnas se combinan (Y).

  readonly syncTitulosFiltro: Record<string, string> = {
    estado: 'Web', sku: 'SKU', name: 'Nombre', price: 'Precio', stock: 'Stock interno',
    forzar: 'Forzar 0', efectivo: 'A enviar', enviado: 'En web (último envío)', diferencia: 'Diferencia', fotos: 'Fotos'
  };
  private readonly SYNC_CAMBIO = '★ Cambió en la última actualización';

  // Valor(es) que tiene un producto en una columna (un producto puede tener más de uno, p. ej. stock + «Cambió»).
  syncValoresDe(p: SyncProductoRow, col: string): string[] {
    switch (col) {
      case 'estado': return [p.enWooCommerce ? 'En la web' : 'No está en la web'];
      case 'sku': return [p.sku];
      case 'name': return [p.name || '(sin nombre)'];
      case 'price': return [String(p.price)];
      case 'stock': return [...(p.stockCambio ? [this.SYNC_CAMBIO] : []), p.stockConocido ? String(p.stock) : '—'];
      case 'forzar': return [p.forceZeroStock ? 'Sí' : 'No'];
      case 'efectivo': return [p.stockConocido ? String(p.stockEfectivo) : '—'];
      case 'enviado': return [p.stockWebEnviado === null ? 'Nunca enviado' : String(p.stockWebEnviado)];
      case 'diferencia': {
        if (!this.syncPuedeSeleccionar(p)) return ['—'];
        const d = this.syncDiferencia(p);
        return [d === null ? 'sin enviar' : d === 0 ? '✓ igual' : d > 0 ? '▲ sube' : '▼ baja'];
      }
      case 'fotos': {
        if (!p.enWooCommerce) return ['— (no está en la web)'];
        const n = this.fotosDe(p);
        return [n === null ? '… (sin dato)' : n === 0 ? '0 · sin fotos' : n === 1 ? '1 foto' : `${n} fotos`];
      }
      default: return [];
    }
  }

  syncFiltroActivo(col: string): boolean {
    return !!this.syncColFiltros[col];
  }

  syncAbrirMenu(col: string, ev: MouseEvent): void {
    ev.stopPropagation();
    if (this.syncMenu?.col === col) { this.syncMenu = null; return; }
    const cuenta = new Map<string, number>();
    for (const p of this.syncProductos) {
      for (const v of this.syncValoresDe(p, col)) cuenta.set(v, (cuenta.get(v) ?? 0) + 1);
    }
    const valores = Array.from(cuenta, ([valor, n]) => ({ valor, n })).sort((a, b) => {
      const sa = a.valor.startsWith('★') ? 0 : 1, sb = b.valor.startsWith('★') ? 0 : 1;
      return sa - sb || a.valor.localeCompare(b.valor, 'es', { numeric: true });
    });
    const r = (ev.currentTarget as HTMLElement).getBoundingClientRect();
    const top = r.bottom + 4;
    this.syncMenu = {
      col, busca: '', valores,
      top, left: Math.max(8, Math.min(r.left, window.innerWidth - 300)),
      maxHeight: Math.max(220, window.innerHeight - top - 12)
    };
  }

  syncMenuVisibles(): { valor: string; n: number }[] {
    const m = this.syncMenu;
    if (!m) return [];
    const q = m.busca.trim().toLowerCase();
    return q ? m.valores.filter(v => v.valor.toLowerCase().includes(q)) : m.valores;
  }

  syncMenuSel(valor: string): boolean {
    const f = this.syncMenu ? this.syncColFiltros[this.syncMenu.col] : undefined;
    return !f || f.has(valor);
  }

  get syncMenuTodosMarcado(): boolean {
    const vis = this.syncMenuVisibles();
    return vis.length > 0 && vis.every(v => this.syncMenuSel(v.valor));
  }

  get syncMenuMarcados(): number {
    return this.syncMenu ? this.syncMenu.valores.filter(v => this.syncMenuSel(v.valor)).length : 0;
  }

  // Guarda el conjunto elegido; si quedan marcados todos los valores, equivale a «sin filtro».
  private syncGuardarFiltro(col: string, set: Set<string>, todos: string[]): void {
    if (todos.every(v => set.has(v))) delete this.syncColFiltros[col];
    else this.syncColFiltros[col] = set;
  }

  syncMenuToggle(valor: string): void {
    const m = this.syncMenu;
    if (!m) return;
    const todos = m.valores.map(v => v.valor);
    const set = new Set(this.syncColFiltros[m.col] ?? todos);
    if (set.has(valor)) set.delete(valor); else set.add(valor);
    this.syncGuardarFiltro(m.col, set, todos);
  }

  // «Seleccionar todo» actúa sobre lo que se ve en la lista (respeta lo escrito en la búsqueda del menú).
  syncMenuToggleTodos(): void {
    const m = this.syncMenu;
    if (!m) return;
    const todos = m.valores.map(v => v.valor);
    const marcado = this.syncMenuTodosMarcado;
    const set = new Set(this.syncColFiltros[m.col] ?? todos);
    this.syncMenuVisibles().forEach(v => { if (marcado) set.delete(v.valor); else set.add(v.valor); });
    this.syncGuardarFiltro(m.col, set, todos);
  }

  syncLimpiarFiltroCol(col: string): void {
    delete this.syncColFiltros[col];
  }

  syncLimpiarFiltros(): void {
    this.syncColFiltros = {};
    this.syncMenu = null;
  }

  // Filtros activos, para mostrarlos como «chips» arriba de la tabla.
  get syncFiltrosActivos(): { col: string; titulo: string; etiqueta: string }[] {
    return Object.keys(this.syncColFiltros).map(c => {
      const vals = Array.from(this.syncColFiltros[c]);
      return {
        col: c,
        titulo: this.syncTitulosFiltro[c],
        etiqueta: vals.length === 0 ? '(ninguno)' : vals.length <= 3 ? vals.join(', ') : `${vals.length} valores`
      };
    });
  }

  // ── Fotos: resumen por producto y editor ─────────────────────────

  // Cuántas fotos tiene el producto en la web (null = no se sabe: cargando, error o producto fuera de la web).
  fotosDe(p: SyncProductoRow): number | null {
    if (!p.enWooCommerce || !this.syncFotosConteo) return null;
    const n = this.syncFotosConteo[p.sku];
    return n === undefined ? null : n;
  }

  get syncSinFotosCount(): number {
    return this.syncProductos.filter(p => this.fotosDe(p) === 0).length;
  }

  cargarResumenFotos(): void {
    this.syncFotosCargando = true;
    this.syncFotosError = '';
    this.http.get<{ fotos: Record<string, number> }>(`${environment.baseUrl}sync/productos/fotos-resumen`).subscribe({
      next: res => { this.syncFotosConteo = res.fotos; this.syncFotosCargando = false; this.cdr.detectChanges(); },
      error: () => {
        this.syncFotosConteo = null;
        this.syncFotosCargando = false;
        this.syncFotosError = 'No se pudo consultar cuántas fotos tiene cada producto en la web.';
        this.cdr.detectChanges();
      }
    });
  }

  abrirFotos(p: SyncProductoRow): void {
    if (!p.enWooCommerce) return;
    this.fotoEditor = {
      sku: p.sku, nombre: p.name, cargando: true, guardando: false,
      items: [], idsOriginales: [], max: 10, error: '', exito: '', avisos: []
    };
    this.http.get<{ fotos: { id: number; url: string; nombre: string }[]; max: number }>(
      `${environment.baseUrl}sync/productos/${encodeURIComponent(p.sku)}/fotos`).subscribe({
      next: res => {
        if (!this.fotoEditor || this.fotoEditor.sku !== p.sku) return;
        this.fotoEditor.items = res.fotos.map(f => ({ key: 'e' + f.id, tipo: 'e' as const, id: f.id, url: f.url, nombre: f.nombre ?? '' }));
        this.fotoEditor.idsOriginales = res.fotos.map(f => f.id);
        this.fotoEditor.max = res.max;
        this.fotoEditor.cargando = false;
        this.cdr.detectChanges();
      },
      error: err => {
        if (!this.fotoEditor) return;
        this.fotoEditor.cargando = false;
        this.fotoEditor.error = err.error?.error ?? 'No se pudieron leer las fotos del producto en la web.';
        this.cdr.detectChanges();
      }
    });
  }

  private liberarPreviews(items: FotoItem[]): void {
    items.filter(i => i.tipo === 'n').forEach(i => URL.revokeObjectURL(i.url));
  }

  cerrarFotos(): void {
    const ed = this.fotoEditor;
    if (!ed || ed.guardando) return;
    if (this.fotosHayCambios && !confirm('Tienes cambios sin guardar en las fotos. ¿Cerrar y descartarlos?')) return;
    this.liberarPreviews(ed.items);
    this.fotoEditor = null;
  }

  // Hay cambios si hay fotos nuevas, se quitó alguna o cambió el orden.
  get fotosHayCambios(): boolean {
    const ed = this.fotoEditor;
    if (!ed) return false;
    if (ed.items.some(i => i.tipo === 'n')) return true;
    const ids = ed.items.map(i => i.id);
    return ids.length !== ed.idsOriginales.length || ids.some((id, k) => id !== ed.idsOriginales[k]);
  }

  get fotosQuitadasCount(): number {
    const ed = this.fotoEditor;
    if (!ed) return 0;
    const quedan = new Set(ed.items.filter(i => i.tipo === 'e').map(i => i.id));
    return ed.idsOriginales.filter(id => !quedan.has(id)).length;
  }

  // Deja la imagen en JPEG 1000×1000 con fondo blanco. El navegador ya respeta la orientación EXIF
  // (fotos de celular) y entiende WebP, que el servidor no lee.
  private async normalizarImagen(file: File): Promise<Blob> {
    const LADO = 1000;
    const bmp = await createImageBitmap(file, { imageOrientation: 'from-image' });
    const ratio = Math.min(LADO / bmp.width, LADO / bmp.height);
    const nw = Math.max(1, Math.round(bmp.width * ratio));
    const nh = Math.max(1, Math.round(bmp.height * ratio));
    const canvas = document.createElement('canvas');
    canvas.width = LADO; canvas.height = LADO;
    const ctx = canvas.getContext('2d')!;
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, LADO, LADO);
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(bmp, (LADO - nw) / 2, (LADO - nh) / 2, nw, nh);
    bmp.close();
    return await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob(b => b ? resolve(b) : reject(new Error('toBlob')), 'image/jpeg', 0.88));
  }

  async fotosAgregar(files: FileList | File[] | null): Promise<void> {
    const ed = this.fotoEditor;
    if (!ed || !files || ed.guardando) return;
    ed.error = ''; ed.exito = '';
    const lista = Array.from(files);
    const rechazadas: string[] = [];
    for (const f of lista) {
      if (ed.items.length >= ed.max) { rechazadas.push(`«${f.name}»: máximo ${ed.max} fotos por producto`); continue; }
      if (!/^image\/(jpeg|png|webp)$/.test(f.type)) { rechazadas.push(`«${f.name}»: solo JPG, PNG o WebP`); continue; }
      if (f.size > 10 * 1024 * 1024) { rechazadas.push(`«${f.name}»: pesa más de 10 MB`); continue; }
      try {
        const blob = await this.normalizarImagen(f);
        ed.items.push({ key: 'n' + Date.now() + Math.random(), tipo: 'n', url: URL.createObjectURL(blob), nombre: f.name, blob, archivo: f });
      } catch {
        rechazadas.push(`«${f.name}»: no se pudo leer la imagen`);
      }
    }
    if (rechazadas.length) ed.error = 'No se agregaron: ' + rechazadas.join('; ') + '.';
    this.cdr.detectChanges();
  }

  fotosSeleccionadas(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    void this.fotosAgregar(input.files);
    input.value = '';
  }

  fotosSoltadas(ev: DragEvent): void {
    ev.preventDefault();
    if (ev.dataTransfer?.files?.length) void this.fotosAgregar(ev.dataTransfer.files);
  }

  // ── Visor de ajuste: zoom, mover y girar una foto ────────────────
  // Siempre trabaja desde la foto ORIGINAL (no desde la ya reducida), así el zoom conserva calidad.
  // Al aplicar, la foto pasa a ser una «nueva» en la misma posición; si era una de la web,
  // la anterior se quita al guardar (queda copia en la papelera del servidor).

  async fotoEditar(i: number): Promise<void> {
    const ed = this.fotoEditor;
    if (!ed || ed.guardando) return;
    const it = ed.items[i];
    this.fotoAjuste = {
      indice: i, origen: null, fuente: null, cargando: true, error: '',
      zoom: it.ajuste?.zoom ?? 1, ox: it.ajuste?.ox ?? 0, oy: it.ajuste?.oy ?? 0, rot: it.ajuste?.rot ?? 0
    };
    this.cdr.detectChanges();
    const a = this.fotoAjuste;
    try {
      const origen: Blob = it.tipo === 'n'
        ? (it.archivo ?? it.blob!)
        : await firstValueFrom(this.http.get(
            `${environment.baseUrl}sync/productos/${encodeURIComponent(ed.sku)}/fotos/${it.id}/original`, { responseType: 'blob' }));
      const fuente = await createImageBitmap(origen, { imageOrientation: 'from-image' });
      if (this.fotoAjuste !== a) { fuente.close(); return; } // se cerró mientras cargaba
      a.origen = origen; a.fuente = fuente; a.cargando = false;
      this.cdr.detectChanges();
      this.ajusteRender();
    } catch {
      if (this.fotoAjuste !== a) return;
      a.cargando = false;
      a.error = 'No se pudo abrir la foto para editarla.';
      this.cdr.detectChanges();
    }
  }

  // Escala con que se dibuja la foto en el cuadrado (1 = tamaño real de los pixeles de la original).
  get ajusteEscala(): number {
    const a = this.fotoAjuste;
    if (!a?.fuente) return 1;
    const impar = a.rot % 2 === 1;
    const fw = impar ? a.fuente.height : a.fuente.width;
    const fh = impar ? a.fuente.width : a.fuente.height;
    return Math.min(1000 / fw, 1000 / fh) * a.zoom;
  }

  // Dibuja en el lienzo de 1000×1000 (fondo blanco) exactamente lo que se publicaría.
  ajusteRender(): void {
    const a = this.fotoAjuste;
    const cv = this.ajusteCanvas?.nativeElement;
    if (!a?.fuente || !cv) return;
    const L = 1000;
    if (cv.width !== L) { cv.width = L; cv.height = L; }
    const ctx = cv.getContext('2d')!;
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, L, L);
    ctx.imageSmoothingQuality = 'high';
    ctx.save();
    ctx.translate(L / 2 + a.ox, L / 2 + a.oy);
    ctx.rotate(a.rot * Math.PI / 2);
    const s = this.ajusteEscala;
    ctx.scale(s, s);
    ctx.drawImage(a.fuente, -a.fuente.width / 2, -a.fuente.height / 2);
    ctx.restore();
  }

  ajusteZoomPor(factor: number): void {
    const a = this.fotoAjuste;
    if (!a) return;
    a.zoom = Math.min(6, Math.max(0.5, +(a.zoom * factor).toFixed(3)));
    this.ajusteRender();
  }

  ajusteRueda(ev: WheelEvent): void {
    ev.preventDefault();
    this.ajusteZoomPor(ev.deltaY < 0 ? 1.1 : 1 / 1.1);
  }

  ajusteGirar(delta: number): void {
    const a = this.fotoAjuste;
    if (!a) return;
    a.rot = (a.rot + delta + 4) % 4;
    this.ajusteRender();
  }

  ajusteRestablecer(): void {
    const a = this.fotoAjuste;
    if (!a) return;
    a.zoom = 1; a.ox = 0; a.oy = 0; a.rot = 0;
    this.ajusteRender();
  }

  ajusteBajar(ev: PointerEvent): void {
    const a = this.fotoAjuste;
    if (!a?.fuente) return;
    (ev.currentTarget as HTMLElement).setPointerCapture(ev.pointerId);
    this.ajusteArrastre = { x: ev.clientX, y: ev.clientY, ox: a.ox, oy: a.oy };
  }

  ajusteMover(ev: PointerEvent): void {
    const a = this.fotoAjuste;
    const d = this.ajusteArrastre;
    if (!a || !d) return;
    const k = 1000 / (ev.currentTarget as HTMLElement).clientWidth; // px de pantalla → px del lienzo
    a.ox = Math.max(-3000, Math.min(3000, d.ox + (ev.clientX - d.x) * k));
    a.oy = Math.max(-3000, Math.min(3000, d.oy + (ev.clientY - d.y) * k));
    this.ajusteRender();
  }

  ajusteSoltar(): void {
    this.ajusteArrastre = null;
  }

  ajusteCancelar(): void {
    this.fotoAjuste?.fuente?.close();
    this.fotoAjuste = null;
    this.ajusteArrastre = null;
  }

  ajusteAplicar(): void {
    const a = this.fotoAjuste;
    const ed = this.fotoEditor;
    const cv = this.ajusteCanvas?.nativeElement;
    if (!a?.fuente || !a.origen || !ed || !cv) return;
    this.ajusteRender();
    cv.toBlob(blob => {
      if (!blob || !this.fotoEditor) return;
      const it = ed.items[a.indice];
      if (it.tipo === 'n') URL.revokeObjectURL(it.url);
      ed.items[a.indice] = {
        key: 'n' + Date.now() + Math.random(), tipo: 'n', url: URL.createObjectURL(blob), nombre: it.nombre,
        blob, archivo: a.origen!, ajuste: { zoom: a.zoom, ox: a.ox, oy: a.oy, rot: a.rot }
      };
      ed.exito = '';
      this.ajusteCancelar();
      this.cdr.detectChanges();
    }, 'image/jpeg', 0.9);
  }

  fotoQuitar(i: number): void {
    const ed = this.fotoEditor;
    if (!ed || ed.guardando) return;
    const [q] = ed.items.splice(i, 1);
    if (q?.tipo === 'n') URL.revokeObjectURL(q.url);
    ed.exito = '';
  }

  fotoMover(i: number, delta: number): void {
    const ed = this.fotoEditor;
    if (!ed || ed.guardando) return;
    const j = i + delta;
    if (j < 0 || j >= ed.items.length) return;
    [ed.items[i], ed.items[j]] = [ed.items[j], ed.items[i]];
    ed.exito = '';
  }

  fotoDragStart(i: number): void { this.fotoDragIndex = i; }

  fotoDropEn(i: number, ev: DragEvent): void {
    ev.preventDefault();
    ev.stopPropagation();
    const ed = this.fotoEditor;
    const from = this.fotoDragIndex;
    this.fotoDragIndex = null;
    if (!ed || ed.guardando || from === null || from === i) return;
    const [it] = ed.items.splice(from, 1);
    ed.items.splice(i, 0, it);
    ed.exito = '';
  }

  fotosGuardar(): void {
    const ed = this.fotoEditor;
    if (!ed || ed.guardando || !this.fotosHayCambios) return;
    const quitadas = this.fotosQuitadasCount;
    const nuevas = ed.items.filter(i => i.tipo === 'n');
    const detalle = [
      nuevas.length ? `${nuevas.length} nueva(s)` : '',
      quitadas ? `${quitadas} a borrar (también de la biblioteca de medios)` : ''
    ].filter(Boolean).join(', ');
    if (!confirm(`¿Guardar y publicar las fotos de ${ed.sku} en la web?${detalle ? '\n' + detalle + '.' : ''}`)) return;

    const orden: string[] = [];
    const form = new FormData();
    let n = 0;
    for (const it of ed.items) {
      if (it.tipo === 'e') orden.push('e:' + it.id);
      else { orden.push('n:' + n++); form.append('nuevas', it.blob!, `${ed.sku}-${n}.jpg`); }
    }
    form.append('plan', JSON.stringify({ orden }));

    ed.guardando = true; ed.error = ''; ed.exito = ''; ed.avisos = [];
    this.http.post<{ ok: boolean; fotos: { id: number; url: string; nombre: string }[]; subidas: number; borradas: number; advertencias: string[] }>(
      `${environment.baseUrl}sync/productos/${encodeURIComponent(ed.sku)}/fotos`, form).subscribe({
      next: res => {
        this.liberarPreviews(ed.items);
        ed.items = res.fotos.map(f => ({ key: 'e' + f.id, tipo: 'e' as const, id: f.id, url: f.url, nombre: f.nombre ?? '' }));
        ed.idsOriginales = res.fotos.map(f => f.id);
        ed.guardando = false;
        ed.avisos = res.advertencias ?? [];
        ed.exito = `Guardado y verificado en la web: ${res.fotos.length} foto(s) · ${res.subidas} subida(s) · ${res.borradas} borrada(s).`;
        if (this.syncFotosConteo) this.syncFotosConteo[ed.sku] = res.fotos.length;
        this.cdr.detectChanges();
      },
      error: err => {
        ed.guardando = false;
        ed.error = err.error?.error ?? 'No se pudieron guardar las fotos. Intenta de nuevo.';
        this.cdr.detectChanges();
      }
    });
  }

  // ── Stock: diferencia, selección y envío ─────────────────────────

  // Solo se puede enviar stock a productos que ya existen en WooCommerce y tienen stock conocido.
  syncPuedeSeleccionar(p: SyncProductoRow): boolean {
    return p.enWooCommerce && p.stockConocido;
  }

  // Hay que enviar si nunca se envió desde acá (sin referencia) o si el stock efectivo cambió.
  syncNecesitaEnvio(p: SyncProductoRow): boolean {
    return this.syncPuedeSeleccionar(p) && (p.stockWebEnviado === null || p.stockWebEnviado !== p.stockEfectivo);
  }

  // null = sin referencia (nunca enviado) o no aplica; 0 = sin diferencia.
  syncDiferencia(p: SyncProductoRow): number | null {
    if (!this.syncPuedeSeleccionar(p) || p.stockWebEnviado === null) return null;
    return p.stockEfectivo - p.stockWebEnviado;
  }

  get syncSeleccionables(): SyncProductoRow[] {
    return this.filteredSyncProductos.filter(p => this.syncPuedeSeleccionar(p));
  }

  get syncTodosSeleccionados(): boolean {
    const sel = this.syncSeleccionables;
    return sel.length > 0 && sel.every(p => this.syncSeleccion.has(p.sku));
  }

  get syncEnWebCount(): number {
    return this.syncProductos.filter(p => p.enWooCommerce).length;
  }

  get syncNoWebCount(): number {
    return this.syncProductos.filter(p => !p.enWooCommerce).length;
  }

  get syncCambiaronCount(): number {
    return this.syncProductos.filter(p => p.stockCambio).length;
  }

  // Variación del stock interno respecto de antes de la última actualización (null si no cambió o no había dato previo).
  syncVariacion(p: SyncProductoRow): number | null {
    if (!p.stockCambio || p.stockAnterior === null) return null;
    return p.stock - p.stockAnterior;
  }

  get syncConDiferenciaCount(): number {
    return this.syncProductos.filter(p => this.syncNecesitaEnvio(p)).length;
  }

  syncToggleSel(p: SyncProductoRow): void {
    if (this.syncSeleccion.has(p.sku)) this.syncSeleccion.delete(p.sku);
    else this.syncSeleccion.add(p.sku);
  }

  // Seleccionar todo / deseleccionar todo, solo sobre lo que se ve con el filtro y la búsqueda actuales.
  syncToggleTodos(): void {
    if (this.syncTodosSeleccionados) {
      this.syncSeleccionables.forEach(p => this.syncSeleccion.delete(p.sku));
    } else {
      this.syncSeleccionables.forEach(p => this.syncSeleccion.add(p.sku));
    }
  }

  syncLimpiarSeleccion(): void {
    this.syncSeleccion.clear();
  }

  // Trae el stock de Laudus (bodega 008) a la base interna. No toca WooCommerce.
  actualizarStockInterno(): void {
    this.syncActualizandoStock = true;
    this.syncStockMsg = '';
    this.syncStockMsgError = false;
    this.http.post<{ status: string; updated: number }>(`${environment.baseUrl}sync/stock`, {}).subscribe({
      next: () => {
        this.syncActualizandoStock = false;
        // El resumen se arma con la tabla recargada: ahí vienen marcados los que cambiaron.
        this.loadSyncProductos(() => {
          const cambiaron = this.syncProductos.filter(p => p.stockCambio);
          const sinWeb = cambiaron.filter(p => !p.enWooCommerce).length;
          this.syncStockMsg = cambiaron.length === 0
            ? 'Stock interno al día: ningún producto cambió desde la actualización anterior.'
            : `Stock interno actualizado: ${cambiaron.length} producto(s) cambiaron` +
              (sinWeb > 0 ? `, ${sinWeb} de ellos NO están en la web.` : ', todos están en la web.');
        });
      },
      error: err => {
        this.syncActualizandoStock = false;
        this.syncStockMsgError = true;
        this.syncStockMsg = err.error?.error ?? 'No se pudo actualizar el stock interno desde Laudus.';
        this.cdr.detectChanges();
      }
    });
  }

  toggleForceZeroSync(p: SyncProductoRow, value: boolean): void {
    this.http.put(`${environment.baseUrl}api/productos-web/${p.sku}/force-zero-stock`, { value }).subscribe({
      next: () => {
        p.forceZeroStock = value;
        p.stockEfectivo = value ? 0 : p.stock;
        this.cdr.detectChanges();
      },
      error: () => {
        this.syncStockMsgError = true;
        this.syncStockMsg = `No se pudo cambiar "forzar stock 0" de ${p.sku}.`;
        this.cdr.detectChanges();
      }
    });
  }

  // Envía solo el stock de los seleccionados, de a 25 para poder mostrar avance.
  async enviarStockSeleccionados(): Promise<void> {
    const skus = Array.from(this.syncSeleccion);
    if (skus.length === 0 || this.syncEnviandoStock) return;
    if (!confirm(`¿Enviar el stock de ${skus.length} producto(s) a la web? Solo se modifica el stock en WooCommerce.`)) return;

    this.syncEnviandoStock = true;
    this.syncStockMsg = '';
    this.syncStockMsgError = false;
    let ok = 0, errores = 0, omitidos = 0;
    const CHUNK = 5; // lotes chicos para que la barra avance con frecuencia
    this.syncEnvioTotal = skus.length;
    this.syncEnvioHechos = 0;
    this.syncEnvioOk = 0;
    this.syncEnvioErr = 0;

    for (let i = 0; i < skus.length; i += CHUNK) {
      const lote = skus.slice(i, i + CHUNK);
      this.syncEnvioActual = lote.join(', ');
      this.syncEnvioProgreso = `Enviando ${Math.min(i + CHUNK, skus.length)} de ${skus.length}...`;
      this.cdr.detectChanges();
      try {
        const res: any = await firstValueFrom(
          this.http.post(`${environment.baseUrl}sync/productos/enviar-stock`, { skus: lote }));
        ok += res.enviados; errores += res.errores; omitidos += res.omitidos;
        for (const r of res.resultados ?? []) {
          if (r.estado === 'ok') {
            const row = this.syncProductos.find(p => p.sku === r.sku);
            if (row) {
              row.stockWebEnviado = r.stockEnviado;
              // Ya está en la web: el stock anterior pasa a ser el que se subió, así que deja de marcarse como cambiado.
              row.stockCambio = false;
              row.stockAnterior = null;
            }
            this.syncSeleccion.delete(r.sku);
          }
        }
      } catch {
        errores += lote.length;
      }
      this.syncEnvioHechos = Math.min(i + CHUNK, skus.length);
      this.syncEnvioOk = ok;
      this.syncEnvioErr = errores;
      this.cdr.detectChanges();
    }

    this.syncEnviandoStock = false;
    this.syncEnvioProgreso = '';
    this.syncEnvioActual = '';
    this.syncStockMsgError = errores > 0;
    this.syncStockMsg = `Envío terminado: ${ok} enviado(s)` +
      (errores ? `, ${errores} con error` : '') + (omitidos ? `, ${omitidos} omitido(s)` : '') + '.';
    this.cdr.detectChanges();
  }

  openSyncProducto(p: SyncProductoRow): void {
    this.syncSelected = p;
    this.syncDatosWoo = null;
    this.syncPreviewLaudus = null;
    this.syncEnviarError = '';
    this.syncEnviarOk = '';
    this.syncTitulo = p.name ?? '';
    this.syncDescripcionCorta = '';
    this.syncDescripcionLarga = '';
    this.syncDescripcionError = '';
    this.syncDescripcionOk = '';

    if (p.enWooCommerce) {
      this.syncDatosWooLoading = true;
      this.http.get<any>(`${environment.baseUrl}sync/productos/${p.sku}/datos-woocommerce`).subscribe({
        next: datos => {
          this.syncDatosWoo = datos;
          // Editable: se precarga con lo que REALMENTE hay en WooCommerce,
          // no con lo que Laudus enviaría.
          if (datos.existe) {
            this.syncTitulo = datos.name ?? this.syncTitulo;
            this.syncDescripcionCorta = datos.shortDescription ?? '';
            this.syncDescripcionLarga = datos.description ?? '';
          }
          this.syncDatosWooLoading = false;
          this.cdr.detectChanges();
        },
        error: () => { this.syncDatosWooLoading = false; this.cdr.detectChanges(); }
      });
    }

    // Lo que Laudus mandaría si se sincroniza ahora — mismo cálculo que usa
    // el push real (TransactionBDtoWPService.convertLaudusToLocal), no una
    // reinvención aparte.
    this.syncPreviewLaudusLoading = true;
    this.http.get(`${environment.baseUrl}api/productos-web/${p.sku}/preview-woocommerce`, { responseType: 'text' }).subscribe({
      next: raw => {
        try { this.syncPreviewLaudus = JSON.parse(raw); } catch { this.syncPreviewLaudus = raw; }
        // Si el producto todavía no está en WooCommerce, no hay descripción
        // real que precargar — se usa la corta que Laudus mandaría como punto
        // de partida (la larga no existe en el flujo de Laudus, queda vacía).
        if (!p.enWooCommerce && this.syncPreviewLaudus?.short_description) {
          this.syncDescripcionCorta = this.syncPreviewLaudus.short_description;
        }
        this.syncPreviewLaudusLoading = false;
        this.cdr.detectChanges();
      },
      error: () => { this.syncPreviewLaudusLoading = false; this.cdr.detectChanges(); }
    });
  }

  closeSyncProducto(): void {
    this.syncSelected = null;
  }

  guardarDescripcionWoo(): void {
    if (!this.syncSelected) return;
    this.syncGuardandoDescripcion = true;
    this.syncDescripcionError = '';
    this.syncDescripcionOk = '';
    this.http.put<any>(`${environment.baseUrl}sync/productos/${this.syncSelected.sku}/editar`, {
      name: this.syncTitulo,
      short_description: this.syncDescripcionCorta,
      description: this.syncDescripcionLarga
    }).subscribe({
      next: () => {
        this.syncGuardandoDescripcion = false;
        this.syncDescripcionOk = 'Guardado en WooCommerce.';
        this.cdr.detectChanges();
      },
      error: err => {
        this.syncGuardandoDescripcion = false;
        this.syncDescripcionError = err.error?.error ?? err.message ?? 'Error desconocido';
        this.cdr.detectChanges();
      }
    });
  }

  sincronizarSyncProducto(): void {
    if (!this.syncSelected) return;
    this.syncEnviando = true;
    this.syncEnviarError = '';
    this.syncEnviarOk = '';
    this.apiService.startSynchro([this.syncSelected.sku], true, false).subscribe({
      next: () => {
        this.syncEnviando = false;
        this.syncEnviarOk = 'Sincronizado — revisa el estado abajo.';
        this.syncSelected!.enWooCommerce = true;
        this.openSyncProducto(this.syncSelected!); // recarga el preview/datos reales
      },
      error: err => {
        this.syncEnviando = false;
        this.syncEnviarError = err.error ?? err.message ?? 'Error desconocido';
        this.cdr.detectChanges();
      }
    });
  }

  // ── Pedidos Internos ───────────────────────────────────────────

  loadPedidosInternos(): void {
    this.loadingPedidos = true;
    this.pedidoService.getAll().subscribe({
      next: data => { this.pedidosInternos = data; this.loadingPedidos = false; this.cdr.detectChanges(); },
      error: () => { this.loadingPedidos = false; this.cdr.detectChanges(); }
    });
  }

  filteredPedidosInternos(): Pedido[] {
    return this.pedidoStatusFilter
      ? this.pedidosInternos.filter(p => p.status === this.pedidoStatusFilter)
      : this.pedidosInternos;
  }

  parsePedidoItems(itemsJson: string): any[] {
    return this.pedidoService.parseItems(itemsJson);
  }

  changePedidoStatus(p: Pedido, status: string): void {
    this.pedidoService.updateStatus(p.uuid, status).subscribe({
      next: updated => {
        const idx = this.pedidosInternos.findIndex(x => x.uuid === updated.uuid);
        if (idx >= 0) this.pedidosInternos[idx] = updated;
        if (this.selectedPedido?.uuid === updated.uuid) this.selectedPedido = updated;
        this.cdr.detectChanges();
      }
    });
  }

  deletePedido(p: Pedido): void {
    if (!confirm(`¿Eliminar pedido de "${p.clienteName}"?`)) return;
    this.pedidoService.delete(p.uuid).subscribe({
      next: () => {
        this.pedidosInternos = this.pedidosInternos.filter(x => x.uuid !== p.uuid);
        if (this.selectedPedido?.uuid === p.uuid) this.selectedPedido = null;
        this.cdr.detectChanges();
      }
    });
  }

  pedidoStatusLabel(s: string): string {
    return s === 'pending' ? 'Pendiente' : s === 'confirmed' ? 'Confirmado' : s === 'cancelled' ? 'Cancelado' : s;
  }

  pedidoStatusColor(s: string): string {
    return s === 'confirmed' ? '#5ecf8a' : s === 'cancelled' ? '#e05050' : '#f0ad4e';
  }

  // ── Auto-sync Stock ────────────────────────────────────────────

  loadSyncAutoStatus(): void {
    this.http.get<any>(`${environment.baseUrl}sync/stock/auto`).subscribe({
      next: data => { this.syncAutoStatus = data; this.syncAutoInterval = data.intervalMinutes ?? 60; this.cdr.detectChanges(); },
      error: () => {}
    });
  }

  toggleAutoSync(enabled: boolean): void {
    const params = enabled
      ? `enabled=true&intervalMinutes=${this.syncAutoInterval}`
      : `enabled=false`;
    this.http.post<any>(`${environment.baseUrl}sync/stock/auto?${params}`, {}).subscribe({
      next: data => { this.syncAutoStatus = data; this.cdr.detectChanges(); },
      error: () => {}
    });
  }
}
