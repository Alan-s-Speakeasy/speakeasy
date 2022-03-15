import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ChatroomStatusComponent } from './chatroom-status.component';

describe('ChatroomStatusComponent', () => {
  let component: ChatroomStatusComponent;
  let fixture: ComponentFixture<ChatroomStatusComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ChatroomStatusComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ChatroomStatusComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
