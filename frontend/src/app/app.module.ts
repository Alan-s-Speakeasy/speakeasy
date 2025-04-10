import {NgModule} from '@angular/core';
import {BrowserModule} from '@angular/platform-browser';

import {AppComponent} from './app.component';
import {LoginComponent} from './login/login.component';

import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import {AppRoutingModule} from './app-routing.module';
import {PanelComponent} from './panel/panel.component';
import {PasswordComponent} from './password/password.component';
import {HistoryComponent} from './history/history.component';
import {provideHttpClient, withInterceptorsFromDi} from '@angular/common/http';
import {ChatComponent} from './chat/chat.component';
import {RatingPaneComponent} from './rating-pane/rating-pane.component';
import {ChatPaneComponent, CopyButtonComponent} from './chat-pane/chat-pane.component';
import {UserStatusComponent} from './user-status/user-status.component';
import {ChatroomStatusComponent} from './chatroom-status/chatroom-status.component';
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
import {MatFormFieldModule} from "@angular/material/form-field";
import {MatSelectModule} from "@angular/material/select";
import {MatOptionModule} from "@angular/material/core";
import {AssignmentComponent} from "./assignment/assignment.component";
import {MatCheckboxModule} from "@angular/material/checkbox";
import {MatSliderModule} from "@angular/material/slider";
import {MatTooltipModule} from "@angular/material/tooltip";
import {RatingPaneHistoryComponent} from "./rating-pane-history/rating-pane-history.component";
import {MatInputModule} from "@angular/material/input";
import {NgbdDatepickerRangePopup} from "./chatroom-status/date-range-selector/date-range-selector.component";
import {NgOptionComponent, NgSelectComponent} from "@ng-select/ng-select";
import {UserTableComponent} from "./assignment/user-table/user-table.component";
import {AvatarModule} from "primeng/avatar";
import {Button} from "primeng/button";
import {PanelModule} from "primeng/panel";
import {ScrollPanelModule} from "primeng/scrollpanel";
import {OverlayPanelModule} from "primeng/overlaypanel";
import {InputTextModule} from "primeng/inputtext";
import {CdkCopyToClipboard} from "@angular/cdk/clipboard";
import {TooltipModule} from "primeng/tooltip";
import {
  UsersInvolvedSelectedComponent
} from "./chatroom-status/users-involved-selected/users-involved-selected.component";
import {FeedbackStatsTableComponent} from "./user-feedback/feedback-stats-table/feedback-stats-table.component";
import {NgOptimizedImage} from "@angular/common";
import {SideNavbarComponent} from "./side-navbar/side-navbar.component";
import {AlertComponent} from "./alert/alert.component";

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
    AssignmentComponent,
    UserTableComponent,
  ],
  bootstrap: [AppComponent],
  imports: [BrowserModule,
    ReactiveFormsModule,
    AppRoutingModule,
    FormsModule,
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
    MatInputModule, NgbdDatepickerRangePopup,
    NgSelectComponent, NgOptionComponent, AvatarModule, Button, PanelModule, ScrollPanelModule, OverlayPanelModule, InputTextModule, CdkCopyToClipboard, TooltipModule, CopyButtonComponent, UsersInvolvedSelectedComponent, UsersInvolvedSelectedComponent, NgOptimizedImage, FeedbackStatsTableComponent, SideNavbarComponent],
  providers: [
    FrontendDataService,
    {provide: Configuration, useFactory: initializeApiConfig},
    provideHttpClient(withInterceptorsFromDi())
  ]
})
export class AppModule {
}
