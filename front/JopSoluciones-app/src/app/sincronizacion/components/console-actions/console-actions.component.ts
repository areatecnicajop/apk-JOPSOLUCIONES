import { Component, Input, ChangeDetectorRef, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'app-console-actions',
  standalone: true,
  templateUrl: './console-actions.component.html',
  styleUrls: ['./console-actions.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ConsoleActionsComponent {
  @Input() response: string = '';

  constructor(private cdr: ChangeDetectorRef) {}

  ngOnChanges() {
    this.cdr.detectChanges(); // Forzamos la actualización cuando `response` cambia
  }
}
