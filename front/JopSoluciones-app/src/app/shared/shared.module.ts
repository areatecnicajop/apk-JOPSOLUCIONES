import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonComponent } from './components/button/button.component';

@NgModule({
  declarations: [ButtonComponent],
  imports: [CommonModule],
  exports: [ButtonComponent] // Exportamos para que otros módulos lo usen
})
export class SharedModule {}
import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonComponent } from './components/button/button.component';

@NgModule({
  declarations: [ButtonComponent],
  imports: [CommonModule],
  exports: [ButtonComponent] // Exportamos para que otros módulos lo usen
})
export class SharedModule {}
