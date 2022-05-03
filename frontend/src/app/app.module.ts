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
import {AlertModule} from './_alert';
import {ChatSpectateComponent} from "./chat-spectate/chat-spectate.component";
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";

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
    ChatPaneComponent,
    UserStatusComponent,
    ChatroomStatusComponent,
    ChatCommandsPipe,
    ChatSpectateComponent
  ],
  imports: [
    BrowserModule,
    ReactiveFormsModule,
    AppRoutingModule,
    FormsModule,
    HttpClientModule,
    ApiModule,
    AlertModule,
    NgbModule
  ],
  providers: [
    FrontendDataService,
    { provide: Configuration, useFactory: initializeApiConfig }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
