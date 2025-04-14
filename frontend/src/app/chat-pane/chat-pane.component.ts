// This component contains code from https://www.bootdey.com/snippets/view/chat-app

import {UntypedFormControl} from "@angular/forms";
import {Subscription, interval, timer} from "rxjs";
import {exhaustMap} from "rxjs/operators";
import {Message, PaneLog, SseRoomState} from "../new_data";
import {
  ChatMessageReaction,
  ChatRoomState,
  ChatService,
  FeedbackResponseList,
  FeedbackService,
  UserService
} from "../../../openapi";
import {
  Component,
  ElementRef,
  EventEmitter,
  Inject,
  Input,
  OnInit,
  Output,
  ViewChild
} from '@angular/core';
import {AlertService} from "../alert";
import {CommonService} from "../common.service";
import {CdkCopyToClipboard} from "@angular/cdk/clipboard";
import {NgStyle} from "@angular/common";
import {NgbTooltip} from "@ng-bootstrap/ng-bootstrap";

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
  private usersOnlineStatusSubscription!: Subscription;

  @Input() paneLog!: PaneLog
  @Input() numQueries!: number

  @Output("removeRoom") removeRoom: EventEmitter<any> = new EventEmitter()

  num_messages: number = 0
  paneLogScroll: boolean = false
  num_to_ask!: number
  lastGetTime: number = 0

  remainingTime!: number
  lastUpdateRemainingTime!: number
  chatTimer: any

  constructor(
    @Inject(ChatService) private chatService: ChatService,
    @Inject(FeedbackService) private feedbackService: FeedbackService,
    @Inject(CommonService) private commonService: CommonService,
    public alertService: AlertService
    ) { }

  ngOnInit(): void {
    if (this.paneLog.spectate || this.paneLog.history) {
      // Handling administrator spectate of a chatroom that is not in the cache,
      // we are still utilizing a polling mechanism to address this administrator functionality.
      // If it is viewed as a history, the subscription will only execute once.
      this.chatMessagesSubscription = timer(0, 2000)
        .pipe(exhaustMap(_ => {
          return this.chatService.getApiRoomByRoomId(this.paneLog.roomID, this.lastGetTime)
        })).subscribe((response) => {
            // update the remainingTime in real-time from the backend
            this.remainingTime = response.info.remainingTime
            // The aliases can be undefined in the panelLog given as input, so we retrieve them here
            if (this.paneLog.myAlias === undefined) {
              this.paneLog.myAlias = response.info.userAliases[0]
            }
            if (this.paneLog.otherAlias === undefined) {
              this.paneLog.otherAlias = response.info.userAliases[1]
            }
            this.handleChatSubscription(response, false)
          },
          (error) => {
            console.log("Messages are not retrieved properly for the chat room.", error);
            if (error.status === 429) {
              this.alertService.error("Too many requests. Please try again later.", this.options);
            }
          },
        );
    } else {
      // For regular chat activities, we employ the SSE mechanism and subscribe to the cache within the commonService.
      this.num_to_ask = this.numQueries
      // Due to the SSE mechanism, we are unable to update the remainingTime in real-time from the backend.
      // So we need to update the remainingTime on the frontend.
      this.remainingTime = this.commonService.getInitialRemainingTimeByRoomId(this.paneLog.roomID) // corrected remainingTime
      this.lastUpdateRemainingTime = Date.now()
      this.chatTimer = setInterval(() => {
        this.countdown()
      }, 1000)
      this.chatMessagesSubscription = this.commonService.getChatStatusByRoomId(this.paneLog.roomID)
        .subscribe((response) => {
            if (response == null) {
              return
            }
            this.handleChatSubscription(response, true)
          },
          (error) => {
            console.log("Messages are not retrieved properly for the chat room.", error);
          },
        );
    }
    this.usersOnlineStatusSubscription = timer(0, 5000).pipe(
      exhaustMap(_ => {
        return this.chatService.getApiRoomByRoomIdUsersStatus(this.paneLog.roomID)
      })
    ).subscribe({
      next: (response) => {
        // The API returns a map of user aliases to their online status
        // We want to know if the other user is online
        if (response && this.paneLog.otherAlias) {
          this.paneLog.isOtherOnline = !!response[this.paneLog.otherAlias];
        }
      },
      error: (error) => {
        console.log("Error retrieving users online status:", error);
      }
    });

  }

  private handleChatSubscription(response: SseRoomState | ChatRoomState, isSse: boolean) {
    if (!isSse) {
      if (this.remainingTime <= 0 || this.paneLog.history) {
        this.chatMessagesSubscription.unsubscribe()
      }
      if (response.messages.length > 0) {
        // Set new since parameter to the timestamp of the last message (plus 1 to not get last message again)
        this.lastGetTime = response.messages.slice(-1)[0].timeStamp + 1
      }
    }
    response.messages.forEach(api_message => {
      let message: Message;
      message = {
        myMessage: api_message.authorAlias == this.paneLog.myAlias,
        ordinal: api_message.ordinal,
        message: api_message.message,
        time: api_message.timeStamp,
        type: "",
        recipients: api_message.recipients,
        authorAlias: api_message.authorAlias
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
      // Little hack : we need to wait for the DOM to be updated before scrolling.
      // Sometimes (often) two messages are received too quickly, and the DOM is not updated yet.
      setTimeout(() => this.scrollToBottom(), 40);
      this.paneLogScroll = false
    }
    this.num_messages = this.paneLog.ordinals
  }

  private countdown(): void {
    if (this.remainingTime >= 1000) {
      // When the page or screen loses focus, the browser suspends or slows down some operations, including the
      // execution of timers. We need to subtract the actual elapsed time instead of using a fixed interval of 1000 ms.
      const currentTime = Date.now()
      this.remainingTime -= currentTime - this.lastUpdateRemainingTime
      this.lastUpdateRemainingTime = currentTime
    } else {
      this.remainingTime = 0
      this.chatMessagesSubscription.unsubscribe();
      if (this.paneLog.formRef !== '') {
        this.paneLog.ratingOpen = true
      }
      this.paneLog.active = false
      clearInterval(this.chatTimer)
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
    this.closeRoom()
  }

  @ViewChild('scroll') scroll!: ElementRef;

  scrollToBottom(): void {
    try {
      this.scroll.nativeElement.scrollTop = this.scroll.nativeElement.scrollHeight;
    } catch (err) {
      console.error(err)
    }
  }

  // the input field of this pane
  query = new UntypedFormControl("")

  poseQuery(): void {
    this.doQuery(this.query.value)
  }

  // send a message to the chatroom
  doQuery(query: string): void {
    if (query !== "" && query !== null) {
      this.chatService.postApiRoomByRoomId(this.paneLog.roomID, undefined, "", query).subscribe(
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
    this.chatService.postApiRoomByRoomIdReaction(this.paneLog.roomID, undefined, {
      messageOrdinal: ordinal,
      type: type
    } as ChatMessageReaction).subscribe(
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

  closeRoom(): void {
    this.chatService.patchApiRoomByRoomId(this.paneLog.roomID, undefined).subscribe(
      (response) => {
        //console.log("Messages is posted successfully to the room: ", this.paneLog.roomID);
      },
      (error) => {
        console.log("Room was not successfully closed. ", this.paneLog.roomID);
      }
    );
  }

  ngOnDestroy(): void {
    // Unsubscribe from the chatMessagesSubscription before leaving chat page
    this.chatMessagesSubscription.unsubscribe();
  }

  /**
   * Handles the key down event for the input field, so we can send the message when the user presses Enter, and
   * use Shift+Enter to add a new line.
   * @param event
   */
  handleKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault(); // Prevents adding a new line
      this.poseQuery();       // Calls the function to send the message
    }
  }
}

/**
 * Very simple component that copies the text to the clipboard when clicked.
 */
@Component({
  selector: 'app-copy-button',
  standalone: true,
  imports: [
    CdkCopyToClipboard,
    NgStyle,
    NgbTooltip
  ],
  template: `
    <span
      [cdkCopyToClipboard]="textToCopy"
      class="label label-default position-relative"
      style="padding-left: 10px;"
      [ngStyle]="{
        'cursor': isActive ? 'pointer' : 'auto',
      }"
      (click)="handleCopy()"
      ngbTooltip="{{ isCopied ? 'Copied!' : 'Copy' }}"
    >
      <i [class.fa-copy]="!isCopied" [class.fa-check]="isCopied" class="fa"></i>
    </span>
  `
})
export class CopyButtonComponent {
  @Input() textToCopy: string = ''; // The text to be copied
  @Input() isActive: boolean = true; // Determines if the button is active
  @Input() resetTimeout: number = 2000; // Time in ms to reset the icon after copying

  isCopied: boolean = false;

  handleCopy(): void {
    if (!this.isActive || this.isCopied) return;

    this.isCopied = true;
    setTimeout(() => (this.isCopied = false), this.resetTimeout);
  }
}
