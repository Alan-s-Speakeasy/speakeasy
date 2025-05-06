import { NgModule } from '@angular/core';
import {RouterModule, Routes} from "@angular/router";
import {LoginComponent} from "./login/login.component";
import {PanelComponent} from "./panel/panel.component";
import {HistoryComponent} from "./history/history.component";
import {PasswordComponent} from "./password/password.component";
import {ChatComponent} from "./chat/chat.component";
import {UserStatusComponent} from "./user-status/user-status.component";
import {ChatroomStatusComponent} from "./chatroom-status/chatroom-status.component";
import {ChatSpectateComponent} from "./chat-spectate/chat-spectate.component";
import {UserFeedbackComponent} from "./user-feedback/user-feedback.component";
import {AssignmentComponent} from "./assignment/assignment.component";
import {FormDefinitionsComponent} from "./form-definitions/form-definitions.component";
import {MainLayoutComponent, MinimalLayoutComponent} from "./layouts";

const routes: Routes = [
  // MinimalLayout (NO sidebar)
  {
    path: '',
    component: MinimalLayoutComponent,
    children: [
      { path: '', redirectTo: 'login', pathMatch: 'full' },
      { path: 'login', component: LoginComponent },
    ],
  },

  // MainLayout (WITH sidebar)
  {
    path: '',
    component: MainLayoutComponent,
    children: [
      { path: 'panel', component: PanelComponent },
      { path: 'history', component: HistoryComponent },
      { path: 'password', component: PasswordComponent },
      { path: 'chat', component: ChatComponent },
      { path: 'userStatus', component: UserStatusComponent },
      { path: 'chatroomStatus', component: ChatroomStatusComponent },
      { path: 'spectate', component: ChatSpectateComponent },
      { path: 'feedback', component: UserFeedbackComponent },
      { path: 'assignment', component: AssignmentComponent },
      { path: 'forms', component: FormDefinitionsComponent },
    ],
  },
];

@NgModule({
  declarations: [],
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
