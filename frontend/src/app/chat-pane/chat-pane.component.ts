// This component contains code from https://www.bootdey.com/snippets/view/chat-app

import {FormControl} from "@angular/forms";
import {Subscription, interval} from "rxjs";
import {exhaustMap} from "rxjs/operators";
import {Message, PaneLog} from "../new_data";
import {ChatMessageReaction, ChatService, FeedbackResponseList, FeedbackService} from "../../../openapi";
import {Component, ElementRef, EventEmitter, Inject, Input, OnInit, Output, ViewChild} from '@angular/core';
import {AlertService} from "../_alert";

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
  remainingTime: string = ''
  num_to_ask!: number
  lastGetTime: number = 0
  constructor(
    @Inject(ChatService) private chatService: ChatService,
    @Inject(FeedbackService) private feedbackService: FeedbackService,
    public alertService: AlertService
    ) { }

  ngOnInit(): void {
    this.chatMessagesSubscription = interval(1000)
      .pipe(exhaustMap(_ => {
        return this.chatService.getApiRoomWithRoomidWithSince(this.paneLog.roomID, this.lastGetTime, undefined)
      })).subscribe((response) => {
        if (!this.paneLog.spectate) {
          this.paneLog.prompt = response.info.prompt
        }
        if (response.messages.length > 0) {
          // Set new since parameter to the timestamp of the last message (plus 1 to not get last message again)
          this.lastGetTime = response.messages.slice(-1)[0].timeStamp + 1
        }
        this.num_to_ask = this.numQueries
        this.num_messages = this.paneLog.ordinals

        response.messages.forEach(api_message => {
          let message: Message;
          message = {
            myMessage: api_message.authorAlias == this.paneLog.myAlias,
            ordinal: api_message.ordinal,
            message: api_message.message,
            time: api_message.timeStamp,
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

        if (response.info.remainingTime < 3600000) {
          const s = Math.floor(response.info.remainingTime / 1000);
          const minutes = Math.floor(s / 60);
          const seconds = s % 60;
          this.remainingTime = `${minutes < 10 ? '0' : ''}${minutes}:${seconds < 10 ? '0' : ''}${seconds}`;
        } else {
          this.remainingTime = 'no time limit';
        }

        if (response.info.remainingTime <= 0) { //chat session complete
          this.chatMessagesSubscription.unsubscribe();
          if (this.paneLog.formRef !== '') {
            this.paneLog.ratingOpen = true
          }
          this.paneLog.active = false
        }
        if (this.paneLogScroll) {
          this.scrollToBottom()
          this.paneLogScroll = false
        }
      },
      (error) => {console.log("Messages are not retrieved properly for the chat room.", error);},
    );
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
    this.feedbackService.postApiFeedbackWithRoomid(this.paneLog.roomID, undefined, responses).subscribe(
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
      this.chatService.postApiRoomWithRoomid(this.paneLog.roomID, undefined, query).subscribe(
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
    this.chatService.postApiRoomWithRoomidReaction(this.paneLog.roomID, undefined, {messageOrdinal: ordinal, type: type} as ChatMessageReaction).subscribe(
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
