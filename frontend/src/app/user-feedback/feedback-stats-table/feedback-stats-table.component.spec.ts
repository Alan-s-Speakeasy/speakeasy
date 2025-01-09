import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FeedbackStatsTableComponent } from './feedback-stats-table.component';

describe('FeedbackStatsTableComponent', () => {
  let component: FeedbackStatsTableComponent;
  let fixture: ComponentFixture<FeedbackStatsTableComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FeedbackStatsTableComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(FeedbackStatsTableComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
