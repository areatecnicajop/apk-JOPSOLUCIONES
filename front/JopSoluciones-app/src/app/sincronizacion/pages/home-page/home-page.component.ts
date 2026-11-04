import { Component, ChangeDetectorRef, OnInit, OnDestroy } from '@angular/core';
import { ApiService } from '../../services/api.service';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { Order } from '../../interfaces/order.model';
import { CommonModule, DecimalPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../auth/auth.service';
import { ClientesComponent } from '../clientes/clientes.component';
import { MapaComponent } from '../mapa/mapa.component';
import { CategoriasComponent } from '../categorias/categorias.component';
import { ProductosApkComponent } from '../productos-apk/productos-apk.component';
import { StockManualComponent } from '../stock-manual/stock-manual.component';
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
  imports: [CommonModule, HttpClientModule, FormsModule, ClientesComponent, MapaComponent, CategoriasComponent, ProductosApkComponent, StockManualComponent, CategoriasWebComponent, DecimalPipe, DatePipe, MatTableModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatChipsModule, MatTooltipModule],
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
  openSection: string = 'sync';
  activeView: string = 'sync';

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
  syncProductosColumns = ['estado', 'sku', 'name', 'price', 'stock', 'categoryName', 'acciones'];

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

  loadSyncProductos(): void {
    this.syncProductosLoading = true;
    this.syncProductosError = '';
    this.http.get<SyncProductoRow[]>(`${environment.baseUrl}sync/productos`).subscribe({
      next: rows => { this.syncProductos = rows; this.syncProductosLoading = false; this.cdr.detectChanges(); },
      error: () => { this.syncProductosError = 'No se pudo cargar el catálogo'; this.syncProductosLoading = false; this.cdr.detectChanges(); }
    });
  }

  get filteredSyncProductos(): SyncProductoRow[] {
    const q = this.syncProductosSearch.toLowerCase();
    if (!q) return this.syncProductos;
    return this.syncProductos.filter(p =>
      p.sku.toLowerCase().includes(q) ||
      p.name?.toLowerCase().includes(q) ||
      (p.categoryName?.toLowerCase().includes(q) ?? false));
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
