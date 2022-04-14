import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ChatSpectateComponent } from './chat-spectate.component';

describe('ChatComponent', () => {
  let component: ChatSpectateComponent;
  let fixture: ComponentFixture<ChatSpectateComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ChatSpectateComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ChatSpectateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
