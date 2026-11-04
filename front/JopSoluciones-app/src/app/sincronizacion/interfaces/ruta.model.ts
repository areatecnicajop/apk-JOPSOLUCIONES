export interface RutaParada {
  prospectoUuid?: string;
  nombre: string;
  rubro?: string;
  lat: number;
  lng: number;
  orden: number;
}

export interface RutaGuardada {
  uuid: string;
  nombre: string;
  tipo: 'auto' | 'manual';
  inicioLat: number;
  inicioLng: number;
  inicioLabel: string;
  finLat: number;
  finLng: number;
  finLabel: string;
  paradas: string; // JSON string, parsear con JSON.parse a RutaParada[]
  distanciaKm?: number | null;
  duracionMin?: number | null;
  createdAt: string;
}

export interface NuevaRuta {
  uuid: string;
  nombre: string;
  tipo: 'auto' | 'manual';
  inicioLat: number;
  inicioLng: number;
  inicioLabel: string;
  finLat: number;
  finLng: number;
  finLabel: string;
  paradas: string;
  distanciaKm?: number | null;
  duracionMin?: number | null;
}
