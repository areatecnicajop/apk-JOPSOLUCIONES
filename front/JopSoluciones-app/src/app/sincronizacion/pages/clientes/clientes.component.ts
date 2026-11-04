import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { ClienteService } from '../../services/cliente.service';
import { CategoriaClienteService } from '../../services/categoria-cliente.service';
import { ProspectoService } from '../../services/prospecto.service';
import { PedidoService, Pedido, PedidoItem } from '../../services/pedido.service';
import { Cliente, STATUS_LABELS, STATUS_COLORS } from '../../interfaces/cliente.model';
import { CategoriaCliente } from '../../interfaces/categoria-cliente.model';
import { Prospecto } from '../../interfaces/prospecto.model';
import { LaudusProduct } from '../../interfaces/laudus-product.model';
import { PlacesAutocompleteDirective, PlaceResult } from '../../directives/places-autocomplete.directive';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-clientes',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule, PlacesAutocompleteDirective],
  templateUrl: './clientes.component.html',
  styleUrls: ['./clientes.component.css']
})
export class ClientesComponent implements OnInit {
  clientes: Cliente[] = [];
  categorias: CategoriaCliente[] = [];
  loading = true;
  saving = false;
  error: string | null = null;

  searchQuery = '';
  statusFilter: string | null = null;
  selectedCliente: Cliente | null = null;
  showForm = false;
  editingCliente: Cliente | null = null;
  formData: Partial<Cliente> = {};

  // ── Pedidos ──────────────────────────────────────────────────────────────
  pedidos: Pedido[] = [];
  loadingPedidos = false;
  showPedidoForm = false;
  savingPedido = false;

  pedidoNotes = '';
  carritoItems: { product: LaudusProduct; qty: number }[] = [];

  productos: LaudusProduct[] = [];
  loadingProductos = false;
  productoQuery = '';

  constructor(
    private clienteService: ClienteService,
    private categoriaService: CategoriaClienteService,
    private prospectoService: ProspectoService,
    private pedidoService: PedidoService,
    private http: HttpClient,
  ) {}

  ngOnInit(): void {
    this.loadAll();
  }

  loadAll(): void {
    this.loading = true;
    this.error = null;
    forkJoin({
      clientes:   this.clienteService.getAll(),
      prospectos: this.prospectoService.getAll(),
      categorias: this.categoriaService.getAll(),
    }).subscribe({
      next: ({ clientes, prospectos, categorias }) => {
        const leads = prospectos.map(p => this.prospectoToCliente(p));
        this.clientes   = [...clientes, ...leads];
        this.categorias = categorias;
        this.loading    = false;
      },
      error: () => { this.error = 'No se pudo conectar al backend'; this.loading = false; }
    });
  }

  private prospectoToCliente(p: Prospecto): Cliente {
    return {
      uuid:         `prospecto-${p.id}`,
      name:         p.nombre,
      phone:        p.telefono,
      email:        p.email,
      address:      p.direccion ? `${p.direccion}${p.comuna ? ', ' + p.comuna : ''}` : p.comuna,
      latitude:     p.latitud ?? 0,
      longitude:    p.longitud ?? 0,
      categoryName: p.rubro,
      status:       'lead',
      createdAt:    '',
      updatedAt:    '',
    };
  }

  get filteredClientes(): Cliente[] {
    return this.clientes.filter(c => {
      const q = this.searchQuery.toLowerCase();
      const matchSearch = !q ||
        c.name.toLowerCase().includes(q) ||
        (c.phone?.toLowerCase().includes(q) ?? false) ||
        (c.email?.toLowerCase().includes(q) ?? false) ||
        (c.address?.toLowerCase().includes(q) ?? false);
      const matchStatus = !this.statusFilter || c.status === this.statusFilter;
      return matchSearch && matchStatus;
    });
  }

  statusColor(s: string): string { return STATUS_COLORS[s] ?? '#6b7280'; }
  statusLabel(s: string): string { return STATUS_LABELS[s] ?? s; }
  statusKeys(): string[] { return Object.keys(STATUS_LABELS); }
  avatarLetter(c: Cliente): string { return c.name.charAt(0).toUpperCase(); }

  openDetail(c: Cliente): void {
    this.selectedCliente = c;
    this.pedidos = [];
    if (!this.isProspecto(c)) {
      this.loadPedidos(c.uuid);
    }
  }
  closeDetail(): void { this.selectedCliente = null; }

  openNew(): void {
    this.editingCliente = null;
    this.formData = { status: 'prospect', latitude: 0, longitude: 0 };
    this.showForm = true;
    this.selectedCliente = null;
  }

  openEdit(c: Cliente): void {
    this.editingCliente = c;
    this.formData = { ...c };
    this.showForm = true;
    this.selectedCliente = null;
  }

  closeForm(): void {
    this.showForm = false;
    this.editingCliente = null;
    this.formData = {};
  }

  onPlaceSelected(place: PlaceResult): void {
    this.formData.address   = place.address;
    this.formData.latitude  = place.lat;
    this.formData.longitude = place.lng;
  }

  onCategoryChange(uuid: string): void {
    const cat = this.categorias.find(c => c.uuid === uuid);
    if (cat) {
      this.formData.categoryId   = cat.uuid;
      this.formData.categoryName = cat.name;
    }
  }

  isProspecto(c: Cliente | null): boolean {
    return !!c?.uuid?.startsWith('prospecto-');
  }

  promoverACliente(c: Cliente): void {
    if (!confirm(`¿Convertir "${c.name}" en cliente activo?`)) return;
    this.editingCliente = c;
    this.formData = { ...c, status: 'active' };
    this.showForm = true;
    this.selectedCliente = null;
  }

  saveCliente(): void {
    if (!this.formData.name) return;
    this.saving = true;
    const now = new Date().toISOString();
    const esProspecto = this.editingCliente && this.isProspecto(this.editingCliente);

    if (this.editingCliente && !esProspecto) {
      const payload = { ...this.formData, updatedAt: now };
      this.clienteService.update(this.editingCliente.uuid, payload).subscribe({
        next: updated => {
          const idx = this.clientes.findIndex(c => c.uuid === updated.uuid);
          if (idx !== -1) this.clientes[idx] = updated;
          this.saving = false;
          this.closeForm();
        },
        error: () => { this.saving = false; alert('Error al guardar'); }
      });
    } else {
      const oldUuid = this.editingCliente?.uuid;
      const payload: Partial<Cliente> = { ...this.formData, uuid: crypto.randomUUID(), createdAt: now, updatedAt: now };
      this.clienteService.create(payload).subscribe({
        next: created => {
          if (oldUuid) this.clientes = this.clientes.filter(c => c.uuid !== oldUuid);
          this.clientes = [created, ...this.clientes];
          this.saving = false;
          this.closeForm();
        },
        error: () => { this.saving = false; alert('Error al crear cliente'); }
      });
    }
  }

  deleteCliente(c: Cliente): void {
    if (!confirm(`¿Eliminar "${c.name}"?`)) return;
    this.clienteService.delete(c.uuid).subscribe({
      next: () => { this.clientes = this.clientes.filter(x => x.uuid !== c.uuid); this.closeDetail(); },
      error: () => alert('Error al eliminar')
    });
  }

  // ── Pedidos ─────────────────────────────────────────────────────────────

  loadPedidos(clienteUuid: string): void {
    this.loadingPedidos = true;
    this.pedidoService.getByCliente(clienteUuid).subscribe({
      next: p => { this.pedidos = p; this.loadingPedidos = false; },
      error: () => this.loadingPedidos = false,
    });
  }

  parsePedidoItems(p: Pedido): PedidoItem[] {
    return this.pedidoService.parseItems(p.items);
  }

  pedidoStatusLabel(s: string): string {
    return s === 'confirmed' ? 'Confirmado' : s === 'cancelled' ? 'Cancelado' : 'Pendiente';
  }
  pedidoStatusColor(s: string): string {
    return s === 'confirmed' ? '#16a34a' : s === 'cancelled' ? '#dc2626' : '#d97706';
  }

  formatPrice(v: number): string {
    if (!v) return '$0';
    return '$' + Math.round(v).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  }

  get pedidoTotal(): number {
    return this.carritoItems.reduce((s, i) => s + i.product.unitPriceWithTaxes * i.qty, 0);
  }

  openPedidoForm(): void {
    this.carritoItems   = [];
    this.pedidoNotes    = '';
    this.productoQuery  = '';
    this.productos      = [];
    this.showPedidoForm = true;
    this.loadProductos();
  }

  closePedidoForm(): void { this.showPedidoForm = false; }

  loadProductos(): void {
    this.loadingProductos = true;
    this.http.get<{ products: LaudusProduct[]; total: number }>(
      environment.baseUrl + 'api/products'
    ).subscribe({
      next: r => { this.productos = r.products ?? []; this.loadingProductos = false; },
      error: () => this.loadingProductos = false,
    });
  }

  get productosFiltrados(): LaudusProduct[] {
    const q = this.productoQuery.toLowerCase().trim();
    if (!q) return this.productos.slice(0, 50);
    return this.productos.filter(p =>
      p.name.toLowerCase().includes(q) || p.sku.toLowerCase().includes(q)
    ).slice(0, 50);
  }

  agregarAlCarrito(p: LaudusProduct): void {
    const existing = this.carritoItems.find(i => i.product.sku === p.sku);
    if (existing) { existing.qty++; }
    else { this.carritoItems.push({ product: p, qty: 1 }); }
  }

  quitarDelCarrito(sku: string): void {
    this.carritoItems = this.carritoItems.filter(i => i.product.sku !== sku);
  }

  cambiarCantidad(sku: string, delta: number): void {
    const item = this.carritoItems.find(i => i.product.sku === sku);
    if (!item) return;
    item.qty = Math.max(1, item.qty + delta);
  }

  savePedido(): void {
    if (!this.selectedCliente || this.carritoItems.length === 0) return;
    this.savingPedido = true;
    const now = new Date().toISOString();
    const items: PedidoItem[] = this.carritoItems.map(i => ({
      sku:       i.product.sku,
      name:      i.product.name,
      qty:       i.qty,
      unitPrice: i.product.unitPriceWithTaxes,
      total:     i.product.unitPriceWithTaxes * i.qty,
    }));
    const pedido: Partial<Pedido> = {
      uuid:        crypto.randomUUID(),
      clienteUuid: this.selectedCliente.uuid,
      clienteName: this.selectedCliente.name,
      items:       JSON.stringify(items),
      subtotal:    this.pedidoTotal,
      tax:         0,
      total:       this.pedidoTotal,
      status:      'pending',
      notes:       this.pedidoNotes || undefined,
      createdAt:   now,
      updatedAt:   now,
    };
    this.pedidoService.create(pedido).subscribe({
      next: created => {
        this.pedidos = [created, ...this.pedidos];
        this.savingPedido = false;
        this.closePedidoForm();
      },
      error: () => { this.savingPedido = false; alert('Error al guardar pedido'); }
    });
  }

  cambiarEstadoPedido(p: Pedido, status: string): void {
    this.pedidoService.updateStatus(p.uuid, status).subscribe({
      next: updated => {
        const idx = this.pedidos.findIndex(x => x.uuid === p.uuid);
        if (idx !== -1) this.pedidos[idx] = updated;
      },
      error: () => alert('Error al cambiar estado'),
    });
  }

  deletePedido(p: Pedido): void {
    if (!confirm('¿Eliminar este pedido?')) return;
    this.pedidoService.delete(p.uuid).subscribe({
      next: () => this.pedidos = this.pedidos.filter(x => x.uuid !== p.uuid),
      error: () => alert('Error al eliminar pedido'),
    });
  }
}
