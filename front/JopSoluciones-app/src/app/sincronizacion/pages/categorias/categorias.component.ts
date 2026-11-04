import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { CategoriaClienteService } from '../../services/categoria-cliente.service';
import { CategoriaCliente, CATEGORIA_COLORES } from '../../interfaces/categoria-cliente.model';

@Component({
  selector: 'app-categorias',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  templateUrl: './categorias.component.html',
  styleUrls: ['./categorias.component.css']
})
export class CategoriasComponent implements OnInit {
  categorias: CategoriaCliente[] = [];
  loading = true;
  saving = false;
  error: string | null = null;
  showForm = false;
  editingCat: CategoriaCliente | null = null;
  formData: Partial<CategoriaCliente> = {};
  readonly colores = CATEGORIA_COLORES;

  constructor(private service: CategoriaClienteService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = null;
    this.service.getAll().subscribe({
      next: data => { this.categorias = data; this.loading = false; },
      error: () => { this.error = 'No se pudo conectar al backend'; this.loading = false; }
    });
  }

  openNew(): void {
    this.editingCat = null;
    this.formData = { colorHex: '#3b82f6', iconName: 'location_on' };
    this.showForm = true;
  }

  openEdit(c: CategoriaCliente): void {
    this.editingCat = c;
    this.formData = { ...c };
    this.showForm = true;
  }

  closeForm(): void {
    this.showForm = false;
    this.editingCat = null;
    this.formData = {};
  }

  save(): void {
    if (!this.formData.name) return;
    this.saving = true;
    const now = new Date().toISOString();

    if (this.editingCat) {
      this.service.update(this.editingCat.uuid, this.formData).subscribe({
        next: updated => {
          const idx = this.categorias.findIndex(c => c.uuid === updated.uuid);
          if (idx !== -1) this.categorias[idx] = updated;
          this.saving = false;
          this.closeForm();
        },
        error: () => { this.saving = false; alert('Error al guardar'); }
      });
    } else {
      const payload = { ...this.formData, uuid: crypto.randomUUID(), createdAt: now };
      this.service.create(payload).subscribe({
        next: created => {
          this.categorias = [...this.categorias, created];
          this.saving = false;
          this.closeForm();
        },
        error: () => { this.saving = false; alert('Error al crear'); }
      });
    }
  }

  delete(c: CategoriaCliente): void {
    if (!confirm(`¿Eliminar categoría "${c.name}"?`)) return;
    this.service.delete(c.uuid).subscribe({
      next: () => this.categorias = this.categorias.filter(x => x.uuid !== c.uuid),
      error: () => alert('Error al eliminar')
    });
  }
}
