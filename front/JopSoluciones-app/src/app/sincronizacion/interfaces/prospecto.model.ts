export interface Prospecto {
  id: number;
  nombre: string;
  rubro: string;
  telefono?: string;
  email?: string;
  direccion?: string;
  comuna?: string;
  region?: string;
  latitud?: number;
  longitud?: number;
  url?: string;
}

export const RUBROS_COLORES: Record<string, string> = {
  'Mecánico':            '#2196F3',
  'Funeraria':           '#9C27B0',
  'Mueblería':           '#4CAF50',
  'Barraca':             '#FF9800',
  'Pintura Automotriz':  '#FF5722',
  'Desabolladura':       '#F44336',
  'Taller Automotriz':   '#03A9F4',
  'Taller de Pintura':   '#FF7043',
  'Carpintería':         '#8BC34A',
  'Ebanistería':         '#33691E',
  'Vulcanización':       '#FFC107',
  'Tapicería Automotriz':'#00BCD4',
  'Hojalatería':         '#78909C',
};

export function colorRubro(rubro: string): string {
  const entry = Object.entries(RUBROS_COLORES).find(
    ([k]) => rubro.includes(k) || k.includes(rubro)
  );
  return entry ? entry[1] : '#888';
}

export const PROSPECTOS_DEMO: Prospecto[] = [
  { id: 1, nombre: 'Ferretería El Maestro', rubro: 'Ferretería', telefono: '+56912345678', email: 'ferreteria@elmaestro.cl', direccion: 'Av. Providencia 1234', comuna: 'Providencia', region: 'Metropolitana', latitud: -33.432, longitud: -70.610 },
  { id: 2, nombre: 'Taller Mecánico Rodríguez', rubro: 'Mecánico', telefono: '+56998765432', direccion: 'San Martín 456', comuna: 'Santiago', region: 'Metropolitana', latitud: -33.451, longitud: -70.652 },
  { id: 3, nombre: 'Mueblería Don Carlos', rubro: 'Mueblería', telefono: '+56911112222', email: 'ventas@mueblesdancarlos.cl', direccion: 'Gran Avenida 789', comuna: 'San Miguel', region: 'Metropolitana', latitud: -33.498, longitud: -70.653 },
  { id: 4, nombre: 'Barraca Los Robles', rubro: 'Barraca', telefono: '+56933334444', direccion: 'Vicuña Mackenna 321', comuna: 'La Florida', region: 'Metropolitana', latitud: -33.521, longitud: -70.601 },
  { id: 5, nombre: 'Pinturas Automotrices García', rubro: 'Pintura Automotriz', telefono: '+56955556666', email: 'garcia.pinturas@gmail.com', direccion: 'Américo Vespucio 500', comuna: 'Maipú', region: 'Metropolitana', latitud: -33.512, longitud: -70.766 },
  { id: 6, nombre: 'Ferretería Central Ñuñoa', rubro: 'Ferretería', telefono: '+56977778888', direccion: 'Irarrázaval 1200', comuna: 'Ñuñoa', region: 'Metropolitana', latitud: -33.456, longitud: -70.618 },
  { id: 7, nombre: 'Instalaciones Eléctricas Pérez', rubro: 'Eléctrico', telefono: '+56999990000', email: 'perez.electrico@outlook.com', direccion: 'Los Leones 850', comuna: 'Providencia', region: 'Metropolitana' },
  { id: 8, nombre: 'Pompas Fúnebres del Sur', rubro: 'Funeraria', telefono: '+56944443333', direccion: 'Av. Matta 600', comuna: 'Santiago', region: 'Metropolitana', latitud: -33.463, longitud: -70.641 },
  { id: 9, nombre: 'Plomería Rápida Expres', rubro: 'Plomería', telefono: '+56922221111', direccion: 'Tobalaba 980', comuna: 'Las Condes', region: 'Metropolitana', latitud: -33.418, longitud: -70.573 },
  { id: 10, nombre: 'Muebles & Diseño Moderna', rubro: 'Mueblería', email: 'info@mueblasmoderna.cl', direccion: 'Av. Kennedy 5413', comuna: 'Las Condes', region: 'Metropolitana', latitud: -33.402, longitud: -70.577 },
];
