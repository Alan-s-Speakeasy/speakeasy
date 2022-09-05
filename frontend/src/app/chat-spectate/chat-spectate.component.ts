import {PaneLog} from "../new_data";
import {Router} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {AdminService} from "../../../openapi";
import {Component, Inject, OnInit} from '@angular/core';

@Component({selector: 'app-chat', templateUrl: './chat-spectate.component.html', styleUrls: ['./chat-spectate.component.css'],})

export class ChatSpectateComponent implements OnInit {
  options = {
    autoClose: true,
    keepAfterRouteChange: true
  };
  constructor(private router: Router,
              private titleService: Title,
              @Inject(AdminService) private adminService: AdminService) { }

  roomID!: string
  backUrl!: string

  paneLog!: PaneLog

  ngOnInit(): void {
    this.titleService.setTitle("Spectate Chat")

    this.backUrl = history.state.backUrl;
    this.roomID = history.state.roomID;
    this.paneLog = {
      roomID: history.state.roomID,
      ordinals: 0,
      messageLog: {},
      ratingOpen: false,
      ratings: {},
      myAlias: history.state.userAlias,
      otherAlias: history.state.partnerAlias,
      prompt: "spectating " + history.state.username,
      spectate: true
    }
  }

  // exit chat/rating and redirect to the panel page
  home(): void {
    this.router.navigateByUrl('/' + this.backUrl).then()
  }
}
