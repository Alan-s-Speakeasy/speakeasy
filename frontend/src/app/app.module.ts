import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { AppComponent } from './app.component';
import { LoginComponent } from './login/login.component';

import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import { AppRoutingModule } from './app-routing.module';
import { PanelComponent } from './panel/panel.component';
import { PasswordComponent } from './password/password.component';
import { HistoryComponent } from './history/history.component';
import { HttpClientModule } from '@angular/common/http';
import { ChatComponent } from './chat/chat.component';
import { RatingPaneComponent } from './rating-pane/rating-pane.component';
import { ChatPaneComponent } from './chat-pane/chat-pane.component';
import { UserStatusComponent } from './user-status/user-status.component';
import { ChatroomStatusComponent } from './chatroom-status/chatroom-status.component';
import {FrontendDataService} from "./frontend-data.service";
import {ApiModule, Configuration} from "../../openapi";
import {AppConfig} from "./app.config";
import {ChatCommandsPipe} from "./chatcommands.pipe";
import {AlertModule} from './alert';
import {ChatSpectateComponent} from "./chat-spectate/chat-spectate.component";
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";
import {UserFeedbackComponent} from "./user-feedback/user-feedback.component";
import {MatButtonToggleModule} from "@angular/material/button-toggle";
import {NgApexchartsModule} from "ng-apexcharts";
import {BrowserAnimationsModule} from "@angular/platform-browser/animations";
import {MatLegacyFormFieldModule as MatFormFieldModule} from "@angular/material/legacy-form-field";
import {MatLegacySelectModule as MatSelectModule} from "@angular/material/legacy-select";
import {MatLegacyOptionModule as MatOptionModule} from "@angular/material/legacy-core";
import {AssignmentComponent} from "./assignment/assignment.component";
import {MatLegacyCheckboxModule as MatCheckboxModule} from "@angular/material/legacy-checkbox";
import {MatLegacySliderModule as MatSliderModule} from "@angular/material/legacy-slider";
import {MatLegacyTooltipModule as MatTooltipModule} from "@angular/material/legacy-tooltip";
import {RatingPaneHistoryComponent} from "./rating-pane-history/rating-pane-history.component";
import {MatLegacyInputModule as MatInputModule} from "@angular/material/legacy-input";

export function initializeApiConfig() {
  const appConfig = new AppConfig();
  return new Configuration({basePath: appConfig.basePath, withCredentials: true});
}

@NgModule({
  declarations: [
    AppComponent,
    LoginComponent,
    PanelComponent,
    PasswordComponent,
    HistoryComponent,
    ChatComponent,
    RatingPaneComponent,
    RatingPaneHistoryComponent,
    ChatPaneComponent,
    UserStatusComponent,
    ChatroomStatusComponent,
    ChatCommandsPipe,
    ChatSpectateComponent,
    UserFeedbackComponent,
    AssignmentComponent
  ],
  imports: [
    BrowserModule,
    ReactiveFormsModule,
    AppRoutingModule,
    FormsModule,
    HttpClientModule,
    ApiModule,
    AlertModule,
    NgbModule,
    MatButtonToggleModule,
    NgApexchartsModule,
    BrowserAnimationsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatOptionModule,
    MatCheckboxModule,
    MatSliderModule,
    MatTooltipModule,
    MatInputModule
  ],
  providers: [
    FrontendDataService,
    { provide: Configuration, useFactory: initializeApiConfig }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
