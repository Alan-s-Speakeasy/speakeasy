import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RatingPaneComponent } from './rating-pane.component';

describe('RatingPaneComponent', () => {
  let component: RatingPaneComponent;
  let fixture: ComponentFixture<RatingPaneComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ RatingPaneComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(RatingPaneComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
