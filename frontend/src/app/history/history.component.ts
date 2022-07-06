import {Component, Inject, OnInit} from '@angular/core';
import {Router} from "@angular/router";
import {FrontendDataService} from "../frontend-data.service";
import {Title} from "@angular/platform-browser";
import {PaneLog} from "../new_data";

import {
  UserService,
  FeedbackService,
  ChatService,
  ChatRoomInfo,
  FeedbackResponse, FeedbackResponseAverageItem, FeedbackRequest
} from "../../../openapi";
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
  userSessionAlias!: string

  ratingForm!: Array<FeedbackRequest>;
  averageFeedback!: FeedbackResponseAverageItem;

  paneLogs: PaneLog[] = [] // the list of PaneLog instances

  ngOnInit(): void {
    this.titleService.setTitle("History Page")
    this.authService.userSessionDetails.subscribe((response)=>{
      if(response != null){
        this.sessionId = response.sessionId
        this.userSessionAlias = response.userSessionAlias
      }
    });

    if (!this.sessionId) {
      this.alertService.error("You are not logged in!")
      this.router.navigateByUrl('/panel').then()
    }

    this.chatRoomsSubscription = this.commonService.alertOnNewChatRoom()

    this.feedbackService.getApiFeedback(undefined).subscribe((feedbackForm) => {
        this.ratingForm = feedbackForm.requests;
      },
      (error) => {
        console.log("Ratings form for this chat room is not retrieved properly.", error);
      }
    )
    this.feedbackService.getApiFeedbackAverage(true).subscribe((response) => {
      if (response) {
        this.averageFeedback = response.responses[0]
      }
    })

    this.paneLogsInit()
  }

  paneLogsInit(): void {
    this.chatService.getApiAssessedRooms().subscribe(
      (response)=>{
        for (let room of response.rooms) {

          let addRoom = false
          room.users.forEach(user => {
            user.sessions.forEach(session => {
              if (session == this.sessionId) {
                addRoom = true
              }
            })
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
      myAlias: this.userSessionAlias,
      otherAlias: "",
      prompt: "",
      spectate: false
    }

    room.users.forEach(user => {
      if (user.alias != this.userSessionAlias) {
        paneLog.otherAlias = user.alias
      }
    })

    this.paneLogs.push(paneLog)
  }

  idToText(response: FeedbackResponse): string {
    let text = response.value
    this.ratingForm.forEach(r => {
      if (r.id == response.id) {
        r.options.forEach(o => {
          if (o.value.toString() == response.value) {
            text = o.name
          }
        })
      }
    })
    return text
  }

  home(): void {
    this.router.navigateByUrl('/panel').then()
  }

  ngOnDestroy() {
    this.chatRoomsSubscription.unsubscribe()
  }

}
