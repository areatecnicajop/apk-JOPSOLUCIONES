import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { ButtonActionComponent } from './components/button-action/button-action.component';
import { ConsoleActionsComponent } from './components/console-actions/console-actions.component';
import { ApiService } from './services/api.service'; // IMPORTANTE: Importar el servicio

@NgModule({
  imports: [
    CommonModule, 
    HttpClientModule,
    ButtonActionComponent,
    ConsoleActionsComponent,
  ],
  providers: [ApiService], // Registrar ApiService en providers
  exports: [ButtonActionComponent, ConsoleActionsComponent] // Exportamos para su uso en otros módulos
})
export class SincronizacionModule {}
