import {Component, Inject, OnInit} from '@angular/core';
import {Router} from "@angular/router";
import {FrontendDataService} from "../frontend-data.service";
import {Title} from "@angular/platform-browser";
import {PaneLog} from "../new_data";

import {UserService, FeedbackService, ChatService, ChatRoomList, ChatRoomInfo} from "../../../openapi";
import {AuthService} from "../authentication.service";
import {AlertService} from "../_alert";
import {Subscription} from "rxjs";
import {CommonService} from "../common.service";

@Component({
  selector: 'app-history',
  templateUrl: './history.component.html',
  styleUrls: ['./history.component.css']
})
export class HistoryComponent implements OnInit {

  private chatRoomsSubscription!: Subscription;
  constructor(private router: Router,
              private frontendDataService: FrontendDataService,
              private titleService: Title,
              private authService: AuthService,
              @Inject(CommonService) private commonService: CommonService,
              @Inject(UserService) private userService: UserService,
              @Inject(FeedbackService) private feedbackService: FeedbackService,
              @Inject(ChatService) private chatService: ChatService,
              public alertService: AlertService) { }

  sessionId!: string

  paneLogs: PaneLog[] = [] // the list of PaneLog instances

  ngOnInit(): void {
    this.titleService.setTitle("History Page")
    this.authService.userSessionDetails.subscribe((response)=>{
      if(response != null){
        this.sessionId = response.sessionId
      }
    });

    if (!this.sessionId) {
      this.alertService.error("You are not logged in!")
      this.router.navigateByUrl('/panel').then()
    }

    this.chatRoomsSubscription = this.commonService.alertOnNewChatRoom()

    this.paneLogsInit()
  }

  paneLogsInit(): void {
    this.chatService.getApiAssessedRooms().subscribe(
      (response)=>{
        for (let room of response.rooms) {

          let addRoom = false
          room.sessions.forEach(session => {
            if (session == this.sessionId) {
              addRoom = true
            }
          })

          if (addRoom) {
            this.addChatRoom(room)
          }

        }
      },
      (error) => {console.log("Chat rooms are not retrieved properly.", error);},
    )
  }

  // add a chatroom to the UI
  addChatRoom(room: ChatRoomInfo): void {
    let paneLog: PaneLog = {
      roomID: room.uid,
      session: this.sessionId,
      ordinals: [],
      messageLog: {},
      ratingOpen: true,
      ratings: {},
      myAlias: "",
      otherAlias: "",
      prompt: "",
      spectate: false
    }

    // get the aliases of the current user and the other user in the chat room
    this.chatService.getApiAliasWithRoomid(paneLog.roomID).subscribe(
      (response)=>{
        response.list.forEach((alias) => {
          if (alias.session != paneLog.session) {
            paneLog.otherAlias = alias.alias
          } else {
            paneLog.myAlias = alias.alias
          }
        })
      },
      (error) => {console.log("Aliases are not retrieved properly for the chat room.", error);}
    )

    this.paneLogs.push(paneLog)
  }

  home(): void {
    this.router.navigateByUrl('/panel').then()
  }

  ngOnDestroy() {
    this.chatRoomsSubscription.unsubscribe()
  }

}
