import {PaneLog} from "../new_data";
import {Router} from "@angular/router";
import {UntypedFormControl} from "@angular/forms";
import { DOCUMENT } from '@angular/common';
import {Title} from "@angular/platform-browser";
import {FrontendDataService} from "../frontend-data.service";
import {ChatPaneComponent} from "../chat-pane/chat-pane.component";
import {ChatService, FeedbackService, ChatRequest, ChatRoomInfo} from "../../../openapi";
import {Component, Inject, OnDestroy, OnInit, QueryList, ViewChildren} from '@angular/core';
import {AuthService} from "../authentication.service";
import {interval, Subscription, timer} from "rxjs";
import {exhaustMap} from "rxjs/operators";
import {AlertService} from "../alert";
import {NgbModal} from "@ng-bootstrap/ng-bootstrap";
import {CommonService} from "../common.service";

@Component({selector: 'app-chat', templateUrl: './chat.component.html', styleUrls: ['./chat.component.css'],})

export class ChatComponent implements OnInit, OnDestroy {
  options = {
    autoClose: true,
    keepAfterRouteChange: true
  };
  constructor(private router: Router,
              private frontendDataService: FrontendDataService,
              private titleService: Title,
              private authService: AuthService,
              @Inject(CommonService) private commonService: CommonService,
              @Inject(FeedbackService) private feedbackService: FeedbackService,
              @Inject(ChatService) private chatService: ChatService,
              @Inject(DOCUMENT) private document: Document,
              public alertService: AlertService,
              private modalService: NgbModal) { }

  private chatroomSubscription!: Subscription;
  sessionId!: string

  paneLogs: PaneLog[] = [] // the list of PaneLog instances
  numQueries = 5  // the number of queries recommended to ask
  selectedRoomID = "" // the room ID of the selected chat room

  ngOnInit(): void {
    this.titleService.setTitle("Chat Page")
    this.authService.userSessionDetails.subscribe((response)=>{
      if(response != null){
        this.sessionId = response.sessionId
      }
      // Note: This block should not be placed outside the subscribe{} (async) block.
      // Otherwise, it will always redirect the user to /login and then to /panel.
      if (!this.sessionId) {
        this.router.navigateByUrl('/login').then( () => this.alertService.error("You are not logged in!") )
      }
    });

    // rooms EventListener will listen to sse and update _Rooms (as well as Rooms and currentRooms)
    this.commonService.openSseAndListenRooms(false)

    this.chatroomSubscription = this.commonService.Rooms
      .subscribe(
        (response) => {
          if (response) {
            if (response.rooms.length > 0 && this.selectedRoomID == "") {
              this.selectedRoomID = response.rooms[response.rooms.length - 1].uid
            }
            for (let room of response.rooms) {
              let addRoom = true
              this.paneLogs.forEach((paneLog) => {
                if (paneLog.roomID == room.uid) { addRoom = false }
              })
              if (addRoom) {
                this.addChatRoom(room)
              }
            }
          }
        },
        (error) => {console.log("Chat rooms are not retrieved properly.", error);},
      )
  }

  // add a chatroom to the UI
  addChatRoom(room: ChatRoomInfo): void {
    if (this.selectedRoomID == "") {
      this.selectedRoomID = room.uid
    }
    let paneLog: PaneLog = {
      assignment: room.assignment,
      formRef: room.formRef,
      markAsNoFeedback: room.markAsNoFeedback,
      roomID: room.uid,
      ordinals: 0,
      messageLog: {},
      ratingOpen: false,
      active: true,
      ratings: {},
      myAlias: room.userAliases.find(a => a == room.alias) || "",
      otherAlias: room.userAliases.find(a => a != room.alias) || "",
      prompt: room.prompt,
      spectate: false,
      testerBotAlias: room.testerBotAlias
    }

    this.paneLogs.unshift(paneLog)
  }

  // request chatroom with a specified user
  uname = new UntypedFormControl("")
  requestChatRoom() {
    this.chatService.postApiRoomsRequest({username: this.uname.value} as ChatRequest).subscribe(
      (response) => {
        this.alertService.success("Your request is successful. A new chatroom has been created with "+this.uname.value,this.options)
      },
      (error) => {
        this.alertService.error("Your request to create a new chatroom is unsuccessful. Try again later.",this.options)
        },
      ()=>{
        this.uname.reset()
      });
  }

  // Check if there are inactive chatrooms
  hasActiveRooms(): boolean {
    return this.paneLogs.some(paneLog => paneLog.active)
  }

  // post messages to all chatrooms
  @ViewChildren(ChatPaneComponent)
  private paneComponents!: QueryList<ChatPaneComponent>;
  queryAll = new UntypedFormControl("")
  askALl() {
    // call the doQuery method of each pane component
    this.paneComponents.toArray().forEach((component) => component.doQuery(this.queryAll.value))
    // reset the bottom input field
    this.queryAll.reset()
  }

  // navigate to the selected chat/rating pane
  scrollTo(id: string) {
    let pane = document.querySelector('#chat' + id)
    if (pane) {
      pane.scrollIntoView({ block: 'nearest', inline: 'center', behavior: 'smooth' })
    } else {
      console.log("failed to find ", id)
    }
  }

  // remove an assessed chat room
  removeRoom(roomID: string): void {
    this.paneLogs.forEach( (paneLog, index) => {
      if (paneLog.roomID == roomID) {
        this.paneLogs.splice(index, 1)
      }
    })
    // When a room is removed, the backend will not seed a message. We need to process the cached Rooms on the frontend.
    this.commonService.removeCachedRoom(roomID)
  }

  openModal(content: any) {
    if (this.paneLogs.length > 0) {
      this.modalService.open(content, { centered: true })
    }
    else {
      this.home()
    }
  }

  // exit chat/rating and redirect to the panel page
  home(): void {
    this.router.navigateByUrl('/panel').then()
  }

  ngOnDestroy(): void {
    // Unsubscribe from the Subscription before leaving chat page
    this.chatroomSubscription.unsubscribe()
  }

}
