// This component contains code from https://www.bootdey.com/snippets/view/chat-app

import {FormControl} from "@angular/forms";
import {Subscription, interval} from "rxjs";
import {exhaustMap} from "rxjs/operators";
import {Message, PaneLog} from "../new_data";
import {ChatMessageReaction, ChatService, FeedbackResponseList, FeedbackService} from "../../../openapi";
import {Component, ElementRef, EventEmitter, Inject, Input, OnInit, Output, ViewChild} from '@angular/core';
import {AlertService} from "../alert";
import {CommonService} from "../common.service";

@Component({
  selector: 'app-chat-pane',
  templateUrl: './chat-pane.component.html',
  styleUrls: ['./chat-pane.component.css']
})
export class ChatPaneComponent implements OnInit {
  options = {
    autoClose: true,
    keepAfterRouteChange: true
  };

  private chatMessagesSubscription!: Subscription;
  @Input() paneLog!: PaneLog
  @Input() numQueries!: number

  @Output("removeRoom") removeRoom: EventEmitter<any> = new EventEmitter()

  num_messages: number = 0
  paneLogScroll: boolean = false
  num_to_ask!: number

  remainingTime!: number
  roundTimer: any

  constructor(
    @Inject(ChatService) private chatService: ChatService,
    @Inject(FeedbackService) private feedbackService: FeedbackService,
    @Inject(CommonService) private commonService: CommonService,
    public alertService: AlertService
    ) { }

  ngOnInit(): void {

    this.num_to_ask = this.numQueries
    this.num_messages = this.paneLog.ordinals
    this.remainingTime = this.commonService.getInitialRemainingTimeByRoomId(this.paneLog.roomID) // corrected remainingTime
    this.roundTimer = setInterval(() => {this.countdown()}, 1000)

    this.chatMessagesSubscription = this.commonService.getChatStatusByRoomId(this.paneLog.roomID)
      .subscribe((response) => {
        if (response == null) { return }

        // if (!this.paneLog.spectate) { // TODO: check prompt here later (for spectating and history).
        //   this.paneLog.prompt = response.info.prompt
        // }

        response.messages.forEach(sseMessage => {
          let message: Message;
          message = {
            myMessage: sseMessage.authorAlias == this.paneLog.myAlias,
            ordinal: sseMessage.ordinal,
            message: sseMessage.message,
            time: sseMessage.timeStamp,
            type: ""
          };
          this.paneLog.ordinals = message.ordinal + 1
          this.paneLog.messageLog[message.ordinal] = message
        })

        response.reactions.forEach(reaction => {
          this.paneLog.messageLog[reaction.messageOrdinal].type = reaction.type
        })

        if (this.num_messages != this.paneLog.ordinals) {
          this.paneLogScroll = true
        }

        if (this.paneLogScroll) {
          this.scrollToBottom()
          this.paneLogScroll = false
        }
      },
      (error) => {console.log("Messages are not retrieved properly for the chat room.", error);},
    );
  }

  countdown(): void {
    if (this.remainingTime > 0) {
      this.remainingTime -= 1000
    } else {
      this.remainingTime = 0
      this.chatMessagesSubscription.unsubscribe();
      if (this.paneLog.formRef !== '') {
        this.paneLog.ratingOpen = true
      }
      this.paneLog.active = false
      clearInterval(this.roundTimer)
    }
  }

// when the user wants to start rating
  rating(): void {
    let questionsAsked = 0
    for (let i = 0; i < this.paneLog.ordinals; i++) {
      if (this.paneLog.messageLog[i].myMessage) {
        questionsAsked++
      }
    }

    if (this.paneLog.active && questionsAsked < this.num_to_ask) {
     this.alertService.warn("Please ask at least " + this.numQueries + " questions before rating!", {autoClose: true})
    } else {
      this.paneLog.ratingOpen = !this.paneLog.ratingOpen
    }
  }
  close(): void {
    const responses: FeedbackResponseList = {responses: []};
    this.feedbackService.postApiFeedbackByRoomId(this.paneLog.roomID, undefined, responses).subscribe(
      (response) => {
        this.alertService.success("Closed Chat - " + this.paneLog.prompt + " (" + this.paneLog.otherAlias + ")", this.options)
        this.removeRoom.emit()
      },
      (error) => {
        if (error.status == 404) {
          this.alertService.error("Chat - " + this.paneLog.prompt + " (" + this.paneLog.otherAlias + ") not found, just closed it.", this.options)
          this.removeRoom.emit()
        } else if (error.status == 403) {
          this.alertService.error(error.message)
        } else {
          this.alertService.error("Something wrong when closing chat room", this.options)
        }
      }
    )
  }

  @ViewChild('scroll') scroll!: ElementRef;
  scrollToBottom(): void {
    try {this.scroll.nativeElement.scrollTop = this.scroll.nativeElement.scrollHeight;} catch(err) { }
  }

  // the input field of this pane
  query = new FormControl("")
  poseQuery(): void {
    this.doQuery(this.query.value)
  }

  // send a message to the chatroom
  doQuery(query: string): void {
    if (query !== "" && query !== null) {
      this.chatService.postApiRoomByRoomId(this.paneLog.roomID, undefined, query).subscribe(
        (response) => {
          //console.log("Messages is posted successfully to the room: ", this.paneLog.roomID);
        },
        (error) => {
          console.log("Messages is not posted successfully to the room: ", this.paneLog.roomID);
        }
      );
      this.query.reset()  // reset the input field
    }
  }

  // mark a message as THUMBS_UP/DOWN or STAR
  react(type: string, ordinal: number): void {
    this.chatService.postApiRoomByRoomIdReaction(this.paneLog.roomID, undefined, {messageOrdinal: ordinal, type: type} as ChatMessageReaction).subscribe(
      (response) => {
        //console.log("Messages is posted successfully to the room: ", this.paneLog.roomID);
      },
      (error) => {
        console.log("Messages is not posted successfully to the room: ", this.paneLog.roomID);
      }
    );
  }

  range(i: number) {
    return new Array(i);
  }

  ngOnDestroy(): void {
    // Unsubscribe from the chatMessagesSubscription before leaving chat page
    this.chatMessagesSubscription.unsubscribe();
  }
}
