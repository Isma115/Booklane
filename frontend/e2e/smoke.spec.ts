import { expect, test } from '@playwright/test';

test('carga la base sin errores ni operaciones de negocio simuladas', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.goto('/');
  await expect(page).toHaveTitle('Booklane');
  await expect(page.getByRole('main').getByRole('heading', { level: 1 })).toHaveText('Booklane');
  await expect(page.getByRole('button')).toHaveCount(0);
  await page.goto('/ruta-desconocida');
  await expect(page).toHaveURL('/');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Booklane');
  expect(errors).toEqual([]);
});
