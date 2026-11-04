import { Directive, ElementRef, EventEmitter, OnInit, Output } from '@angular/core';
import { GoogleMapsService } from '../services/google-maps.service';

export interface PlaceResult {
  address: string;
  lat: number;
  lng: number;
}

@Directive({ selector: '[placesAutocomplete]', standalone: true })
export class PlacesAutocompleteDirective implements OnInit {
  @Output() placeSelected = new EventEmitter<PlaceResult>();

  constructor(private el: ElementRef<HTMLInputElement>, private gmaps: GoogleMapsService) {}

  async ngOnInit(): Promise<void> {
    const ready = await this.gmaps.load();
    if (!ready) return;

    const g = (window as any)['google'];
    const autocomplete = new g.maps.places.Autocomplete(this.el.nativeElement, {
      types: ['address'],
      componentRestrictions: { country: 'cl' },
      fields: ['formatted_address', 'geometry'],
    });

    autocomplete.addListener('place_changed', () => {
      const place = autocomplete.getPlace();
      if (!place.geometry) return;
      this.placeSelected.emit({
        address: place.formatted_address ?? '',
        lat: place.geometry.location.lat(),
        lng: place.geometry.location.lng(),
      });
    });
  }
}
