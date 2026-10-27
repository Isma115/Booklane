import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Booklane',
    loadComponent: () => import('./shell/app-shell.component').then((m) => m.AppShellComponent),
  },
  { path: '**', redirectTo: '' },
];
