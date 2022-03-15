import { NgModule } from '@angular/core';
import {RouterModule, Routes} from "@angular/router";
import {LoginComponent} from "./login/login.component";
import {PanelComponent} from "./panel/panel.component";
import {HistoryComponent} from "./history/history.component";
import {PasswordComponent} from "./password/password.component";
import {ChatComponent} from "./chat/chat.component";
import {UserStatusComponent} from "./user-status/user-status.component";
import {ChatroomStatusComponent} from "./chatroom-status/chatroom-status.component";

const routes: Routes = [
  {path: '', redirectTo: 'login', pathMatch: 'full'},
  {path: 'login', component: LoginComponent},
  {path: 'panel', component: PanelComponent},
  {path: 'history', component: HistoryComponent},
  {path: 'password', component: PasswordComponent},
  {path: 'chat', component: ChatComponent},
  {path: 'userStatus', component: UserStatusComponent},
  {path: 'chatroomStatus', component: ChatroomStatusComponent},
]

@NgModule({
  declarations: [],
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
