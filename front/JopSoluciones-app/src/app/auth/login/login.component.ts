import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  username = '';
  password = '';
  error = '';
  loading = false;
  showPassword = false;

  constructor(private auth: AuthService, private router: Router) {}

  submit(): void {
    this.error = '';
    if (!this.username || !this.password) {
      this.error = 'Ingresa usuario y contraseña.';
      return;
    }
    this.loading = true;
    setTimeout(() => {
      const ok = this.auth.login(this.username.trim(), this.password);
      if (ok) {
        this.router.navigate(['/']);
      } else {
        this.error = 'Usuario o contraseña incorrectos.';
        this.loading = false;
      }
    }, 400);
  }
}
