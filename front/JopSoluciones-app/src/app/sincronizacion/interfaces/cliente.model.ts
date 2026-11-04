export interface Cliente {
  uuid: string;
  name: string;
  phone?: string;
  email?: string;
  address?: string;
  latitude: number;
  longitude: number;
  categoryId?: string;
  categoryName?: string;
  status: 'active' | 'inactive' | 'prospect' | 'lead';
  notes?: string;
  extraFields?: Record<string, string>;
  createdAt: string;
  updatedAt: string;
}

export interface Categoria {
  uuid: string;
  name: string;
  colorHex: string;
  iconName?: string;
  description?: string;
}

export const STATUS_LABELS: Record<string, string> = {
  active: 'Activo',
  inactive: 'Inactivo',
  prospect: 'Prospecto',
  lead: 'Lead',
};

export const STATUS_COLORS: Record<string, string> = {
  active: '#22c55e',
  inactive: '#6b7280',
  prospect: '#f97316',
  lead: '#3b82f6',
};

export const CLIENTES_DEMO: Cliente[] = [
  { uuid: '1', name: 'Ferretería Pérez', phone: '+56912345678', email: 'perez@ferreteria.cl', address: 'Av. Providencia 1234', latitude: -33.432, longitude: -70.610, categoryName: 'Ferretería', status: 'active', createdAt: '2024-01-15T10:00:00', updatedAt: '2024-03-20T15:30:00' },
  { uuid: '2', name: 'Taller López', phone: '+56998765432', address: 'San Martín 456', latitude: -33.451, longitude: -70.652, categoryName: 'Mecánico', status: 'prospect', createdAt: '2024-02-01T09:00:00', updatedAt: '2024-02-01T09:00:00' },
  { uuid: '3', name: 'Constructora Sur', phone: '+56911112222', email: 'info@constructorasur.cl', address: 'Gran Avenida 789', latitude: -33.498, longitude: -70.653, categoryName: 'Construcción', status: 'active', notes: 'Cliente VIP, prioridad alta', createdAt: '2024-01-05T08:00:00', updatedAt: '2024-04-10T11:00:00' },
  { uuid: '4', name: 'Eléctrica Norte', phone: '+56933334444', address: 'Vicuña Mackenna 321', latitude: -33.521, longitude: -70.601, categoryName: 'Eléctrico', status: 'lead', createdAt: '2024-03-15T14:00:00', updatedAt: '2024-03-15T14:00:00' },
  { uuid: '5', name: 'Pinturerías del Valle', phone: '+56955556666', email: 'ventas@pintureria.cl', address: 'Américo Vespucio 500', latitude: -33.512, longitude: -70.766, status: 'active', createdAt: '2023-11-20T10:00:00', updatedAt: '2024-04-01T09:00:00' },
];

export const CATEGORIAS_DEMO: Categoria[] = [
  { uuid: 'cat1', name: 'Ferretería', colorHex: 'E91E63', description: 'Ferreterías y afines' },
  { uuid: 'cat2', name: 'Mecánico', colorHex: '2196F3', description: 'Talleres mecánicos' },
  { uuid: 'cat3', name: 'Construcción', colorHex: '4CAF50', description: 'Empresas constructoras' },
  { uuid: 'cat4', name: 'Eléctrico', colorHex: '00BCD4', description: 'Instalaciones eléctricas' },
  { uuid: 'cat5', name: 'Barraca', colorHex: 'FF9800', description: 'Barracas de madera' },
];
