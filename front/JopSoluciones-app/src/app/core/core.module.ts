import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from './services/api.service';

@NgModule({
  imports: [CommonModule],
  providers: [ApiService] // Agrega los servicios globales aquí
})
export class CoreModule {}
