import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UsersInvolvedSelectedComponent } from './users-involved-selected.component';

describe('UsersInvolvedSelectedComponent', () => {
  let component: UsersInvolvedSelectedComponent;
  let fixture: ComponentFixture<UsersInvolvedSelectedComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UsersInvolvedSelectedComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(UsersInvolvedSelectedComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
