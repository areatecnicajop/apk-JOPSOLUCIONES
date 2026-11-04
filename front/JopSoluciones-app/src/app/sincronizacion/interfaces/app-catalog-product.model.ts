export interface AppCatalogProduct {
  sku: string;
  name: string;
  unitPriceWithTaxes: number;
  stock: number;
  categoryName?: string;
  webDescription?: string;
  productCategoryId?: string;
}

export function formatPrice(price: number): string {
  if (!price || price === 0) return '$0';
  return '$' + Math.round(price).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}
