export interface CategoriaCliente {
  uuid: string;
  name: string;
  colorHex: string;
  iconName?: string;
  description?: string;
  createdAt?: string;
}

export const CATEGORIA_COLORES = [
  '#ef4444', '#f97316', '#eab308', '#22c55e',
  '#3b82f6', '#8b5cf6', '#ec4899', '#14b8a6',
  '#f59e0b', '#64748b'
];
