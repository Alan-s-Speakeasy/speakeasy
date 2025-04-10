import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { AlertComponent } from './alert.component';

@NgModule({
  imports: [CommonModule, AlertComponent],
  declarations: [],
  exports: [AlertComponent]
})
export class AlertModule { }
