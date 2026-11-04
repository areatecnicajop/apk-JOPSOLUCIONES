import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-button-action',
  standalone: true,
  templateUrl: './button-action.component.html',
  styleUrls: ['./button-action.component.css']
})
export class ButtonActionComponent {
  @Input() label: string = '';
  @Input() action!: () => void;
  @Input() disabled: boolean = false; // 🔥 Nueva propiedad para deshabilitar el botón

  handleClick() {
    if (this.action && !this.disabled) { // 🔥 Evita que el botón se ejecute si está deshabilitado
      this.action();
    }
  }
}
