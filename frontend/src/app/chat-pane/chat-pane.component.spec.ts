import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ChatPaneComponent } from './chat-pane.component';

describe('ChatPaneComponent', () => {
  let component: ChatPaneComponent;
  let fixture: ComponentFixture<ChatPaneComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ChatPaneComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ChatPaneComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
