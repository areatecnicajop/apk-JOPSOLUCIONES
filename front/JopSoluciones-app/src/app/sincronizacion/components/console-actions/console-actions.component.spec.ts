import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConsoleActionsComponent } from './console-actions.component';

describe('ConsoleActionsComponent', () => {
  let component: ConsoleActionsComponent;
  let fixture: ComponentFixture<ConsoleActionsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConsoleActionsComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ConsoleActionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
