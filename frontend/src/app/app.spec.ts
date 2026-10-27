import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

describe('Base de navegación', () => {
  it('carga la ruta lazy inicial y recupera una ruta desconocida', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes)] });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Booklane');
    await harness.navigateByUrl('/ruta-desconocida');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Booklane');
  });
});
