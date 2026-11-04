// app.module.ts
import { NgModule } from '@angular/core';
import { SincronizacionModule } from './sincronizacion/sincronizacion.module';
import { AppComponent } from './app.component';

@NgModule({
  declarations: [],
  imports: [ 
    SincronizacionModule
  ],
  bootstrap: []
})
export class AppModule {}
