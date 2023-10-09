import {Component, Inject, OnInit} from '@angular/core';
import {Router} from "@angular/router";
import {FrontendDataService} from "../frontend-data.service";
import {Title} from "@angular/platform-browser";
import {FeedbackForm, PaneLog} from "../new_data";

import {
  ChatRoomInfo,
  ChatService,
  FeedbackResponse,
  FeedbackResponseAverageItem,
  FeedbackService,
  UserService
} from "../../../openapi";
import {AuthService} from "../authentication.service";
import {AlertService} from "../alert";
import {CommonService} from "../common.service";

@Component({
  selector: 'app-history',
  templateUrl: './history.component.html',
  styleUrls: ['./history.component.css']
})
export class HistoryComponent implements OnInit {

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

  ratingFormsMap: Map<string, FeedbackForm> = new Map();
  nonOptionIdsMap: Map<string, string[]> = new Map(); // formName -> ids of text questions
  averageFeedbackRequestedMap: Map<string, FeedbackResponseAverageItem> = new Map();
  averageFeedbackAssignedMap: Map<string, FeedbackResponseAverageItem> = new Map();

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

    this.commonService.addNewRoomsAlertEventListener()

    // get all forms
    this.feedbackService.getApiFeedbackforms(undefined).subscribe((feedbackForms) => {
        feedbackForms.forms.forEach( (form) => {
          this.ratingFormsMap.set(form.formName, form)

          // for each form, we get the FeedbackResponseAverageItem
          this.feedbackService.getApiFeedbackaverageByFormName(form.formName,true)
            .subscribe((response) => {
              if (response.assigned.length > 0) {
                this.averageFeedbackAssignedMap.set(form.formName, response.assigned[0])
              }
              if (response.requested.length > 0) {
                this.averageFeedbackRequestedMap.set(form.formName, response.requested[0])
              }
            })

          this.addNonOptionIds(form)
          }
        )
      },
      (error) => {
        console.log("Ratings forms for this chat room is not retrieved properly.", error);
      }
    )

    this.paneLogsInit()
  }

  paneLogsInit(): void {
    this.chatService.getApiRoomsAssessed().subscribe(
      (response)=>{
        for (let room of response.rooms) {

          let addRoom = true
          this.paneLogs.forEach((paneLog) => {
            if (paneLog.roomID == room.uid) {
              addRoom = false
            }
          })

          if (addRoom) {
            this.addChatRoom(room)
          }

        }
        this.paneLogs.reverse()
      },
      (error) => {console.log("Chat rooms are not retrieved properly.", error);},
    )
  }

  // add a chatroom to the UI
  addChatRoom(room: ChatRoomInfo): void {
    let paneLog: PaneLog = {
      assignment: room.assignment,
      formRef: room.formRef,
      markAsNoFeedback: room.markAsNoFeedback,
      roomID: room.uid,
      ordinals: 0,
      messageLog: {},
      ratingOpen: true,
      active: false,
      ratings: {},
      myAlias: room.userAliases.find(a => a == room.alias) || "",
      otherAlias: room.userAliases.find(a => a != room.alias) || "",
      prompt: "",
      spectate: false,
      history: true
    }

    this.paneLogs.push(paneLog)
  }

  idToText(response: FeedbackResponse, formName: string): string { // todo: fix infinite calls!
    let text = response.value
    this.ratingFormsMap.get(formName)!.requests.forEach(r => {
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

  addNonOptionIds(form: FeedbackForm){
    form.requests.forEach( (question) => {
      let nonOptionIds: string[] = []
      if (question.options.length === 0) {
        nonOptionIds.push(question.id)
      }
      this.nonOptionIdsMap.set(form.formName, nonOptionIds)
    } )
  }

  filterOptionQuestions(formName: string) {
    return this.ratingFormsMap.get(formName)!.requests.filter(question =>
      !this.nonOptionIdsMap.get(formName)!.includes(question.id)
    )
  }

  filterOptionResponses(formName: string, item: FeedbackResponseAverageItem) {
    return item.responses.filter(response =>
      !this.nonOptionIdsMap.get(formName)!.includes(response.id)
    )
  }

  home(): void {
    this.router.navigateByUrl('/panel').then()
  }

  ngOnDestroy() {
    this.commonService.removeNewRoomsAlertEventListener()
  }

}
