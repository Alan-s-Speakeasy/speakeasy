import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RatingPaneHistoryComponent } from './rating-pane-history.component';

describe('RatingPaneHistoryComponent', () => {
  let component: RatingPaneHistoryComponent;
  let fixture: ComponentFixture<RatingPaneHistoryComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ RatingPaneHistoryComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(RatingPaneHistoryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
