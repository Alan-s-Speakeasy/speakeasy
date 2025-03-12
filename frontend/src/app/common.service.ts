import {Inject, Injectable} from '@angular/core';
import {BehaviorSubject, Observable, of} from "rxjs";
import {take, filter} from "rxjs/operators";
import {map, catchError, tap} from 'rxjs/operators';
import {AuthService} from "./authentication.service";
import { AppConfig } from './app.config';

import {
  FeedbackService,
  ChatService,
  ChatRoomList, ChatRoomInfo
} from "../../openapi";
import {AlertService} from "./alert";
import {ChatEventType, convertFromJSON, SseChatMessage, SseChatReaction, SseRoomState} from "./new_data";


/**
 * Service class for common tasks.
 */
@Injectable({
  providedIn: 'root'
})
export class CommonService {
  options = {
    autoClose: true,
    keepAfterRouteChange: true,
    timeout: 30000
  };
  //userSession!: UserSessionDetails;
  chatRooms!: ChatRoomList;
  private _Rooms = new BehaviorSubject<ChatRoomList|null>(null);
  public Rooms = this._Rooms.asObservable(); // only for Sse Rooms now
  private roomsWithAlerts = true

  private _roomsStateMap: Map<string, BehaviorSubject<SseRoomState>> = new Map();

  // Recording the timestamp of receiving a new sse/api message, witch helps to correct the remainingTime of each
  // Chatroom without having to launch a new http or sse request
  private lastReceivedTime: number = 0;

  private appConfig: AppConfig = new AppConfig()

  // SSE EventSource object and eventListener which 1) alters when new rooms come 2) updates _Rooms
  private eventSource: EventSource | null = null
  private readonly roomsAlertEventListener: EventListener
  private readonly chatMessageEventListener: EventListener
  private readonly chatReactionEventListener: EventListener
  private readonly SseRoomEventUrl: string = `${this.appConfig.basePath}/sse/rooms`


  /**
   * Constructor
   */
  constructor(@Inject(ChatService) private chatService: ChatService,
              @Inject(FeedbackService) private feedbackService: FeedbackService,
              @Inject(AuthService) private authService: AuthService,
              public alertService: AlertService) {

    const initChatRoomsByApi = () => {
       chatService.getApiRooms().pipe(take(1)).subscribe(
        (response) => {
          this._Rooms.next(response)
          // reset lastReceivedTime so that the remainingTime can be corrected
          this.lastReceivedTime = Date.now()
          response.rooms.forEach( (roomInfo) => {
            chatService.getApiRoomByRoomId(roomInfo.uid, 0).pipe(take(1)).subscribe(
              (state) => {
                const initMessages = state.messages.map( (restMsg) => {
                  return {
                    roomId: roomInfo.uid,
                    timeStamp: restMsg.timeStamp,
                    authorAlias: restMsg.authorAlias,
                    ordinal: restMsg.ordinal,
                    message: restMsg.message,
                    recipients: restMsg.recipients
                  } as SseChatMessage
                })
                const initReactions = state.reactions.map( (restRec) => {
                  return {
                    roomId: roomInfo.uid,
                    messageOrdinal: restRec.messageOrdinal,
                    type: restRec.type
                  } as SseChatReaction
                })
                this._roomsStateMap.set(roomInfo.uid,
                  new BehaviorSubject<SseRoomState>({ messages: initMessages, reactions: initReactions }));
              }
            )
          } )
        },
        (e) => {
          console.warn('Failed to init chat rooms :', e)
        }
      )
    }
    this.authService.isValidSession.pipe(
      // execute `initChatRoomsByApi()` only when refreshing and login (i.e., _userSession change to not null)
      filter(valid => valid),
    ).subscribe((res) => {
      initChatRoomsByApi();
    });

    this.roomsAlertEventListener =  (ev) => {
      // according to the backend, what we received via SSE must be new.
      let oldRooms: ChatRoomInfo[] = [];
      // this.Rooms are not updated yet here
      this.Rooms.pipe(take(1)).subscribe(
        (value) => {
          if (value) {
            oldRooms = value.rooms.map(oldRoom => ({
              ...oldRoom,
              // update the old remainingTime
              remainingTime: Math.max(oldRoom.remainingTime - (Date.now() - this.lastReceivedTime), 0)
            }));
          }
        });
      // reset lastReceivedTime so that the remainingTime can be corrected
      this.lastReceivedTime = Date.now()

      // convert data from sse to ChatRoomList
      const addedRooms = convertFromJSON<ChatRoomList>((ev as MessageEvent).data).rooms;
      // update this._Rooms as well as this.Rooms
      this._Rooms.next( {rooms: oldRooms.concat(addedRooms)} as ChatRoomList);
      // init _roomsStateMap with an empty state for each new room
      addedRooms.forEach( room => {
        if (!this._roomsStateMap.has(room.uid)) {
          this._roomsStateMap.set(
            room.uid,
            new BehaviorSubject<SseRoomState>({ messages: [], reactions: [] }))
        }
      })

      // alert if needed
      if (addedRooms && this.roomsWithAlerts) {
        if (addedRooms.length > 0) {
          if (addedRooms.length == 1) {
            this.alertService.success("A new chat room has become available!", this.options)
          } else {
            this.alertService.success(addedRooms.length + " new chat rooms has become available!", this.options)
          }
        }
      }
    }

    this.chatMessageEventListener =  (ev) => {
      const sseChatMessage = convertFromJSON<SseChatMessage>((ev as MessageEvent).data)
      if (!this._roomsStateMap.has(sseChatMessage.roomId)) {
        // This could happen because there's a delay mechanism in backend for sending rooms.
        this._roomsStateMap.set(sseChatMessage.roomId,
          new BehaviorSubject<SseRoomState>({ messages: [], reactions: [] }));
      }
      const stateSubject = this._roomsStateMap.get(sseChatMessage.roomId)!
      stateSubject.next({
        ...stateSubject.getValue(),
        messages: [...stateSubject.getValue().messages, sseChatMessage]
      })
    }

    this.chatReactionEventListener = (ev) => {
      const sseChatReaction = convertFromJSON<SseChatReaction>((ev as MessageEvent).data)
      if (!this._roomsStateMap.has(sseChatReaction.roomId)) {
        // This could happen because there's a delay mechanism in backend for sending rooms.
        this._roomsStateMap.set(sseChatReaction.roomId,
          new BehaviorSubject<SseRoomState>({ messages: [], reactions: [] }));
      }
      const stateSubject = this._roomsStateMap.get(sseChatReaction.roomId)!
      stateSubject.next({
        ...stateSubject.getValue(),
        reactions: [...stateSubject.getValue().reactions, sseChatReaction]
      })
    }
  }

  public openSseAndListenRooms(withAlerts: boolean = true) {
    this.roomsWithAlerts = withAlerts
    if (this.eventSource == null) {
      // Each new EventSource() creates a new SseClient in backend, so we limit this connection to 1
      this.eventSource = new EventSource( this.SseRoomEventUrl,
        {withCredentials: true})
      this.eventSource.addEventListener(ChatEventType.ROOMS, this.roomsAlertEventListener);
      this.eventSource.addEventListener(ChatEventType.MESSAGE, this.chatMessageEventListener);
      this.eventSource.addEventListener(ChatEventType.REACTION, this.chatReactionEventListener);
    }
  }

  public closeSeeRooms() {
    if (this.eventSource != null) {
      this.eventSource.close()
      this.eventSource = null
    }
  }

  get currentRooms(): Observable<ChatRoomList|null>{
    return this._Rooms.pipe(
      map(u => u),
      catchError(e => of(null))
    );
  }

  public removeCachedRoom(roomId: string) {
    this.Rooms.pipe(take(1)).subscribe((oldRooms) => {
      if (oldRooms === null) {
        this._Rooms.next(null);
      } else {
        const newRooms = { rooms: oldRooms.rooms.filter((r) => r.uid !== roomId) };
        this._Rooms.next(newRooms);
      }
    });
  }

  public getChatStatusByRoomId(roomId: string): Observable<SseRoomState|null> {
    const roomState = this._roomsStateMap.get(roomId);
    if (!roomState) {
      console.warn(`Can't find such roomId in cached room state: ${roomId}`);
      return of(null);
    }
    return roomState.asObservable();
  }

  public getInitialRemainingTimeByRoomId(roomId: string): number {
    let correctedRemainingTime: number = 0
    this._Rooms.pipe(take(1)).subscribe(response => {
      const room = response?.rooms.find( (r) => r.uid === roomId )
      if (room !== undefined) {
        // corrected = the old remainingTime - how old it is
        correctedRemainingTime = room.remainingTime - (Date.now() - this.lastReceivedTime)
      }
    })
    return correctedRemainingTime > 0 ? correctedRemainingTime : 0;
  }


  /**
   *  Get a chat room status
   */
  public getChatRoomStatus(roomID: string) : Observable<boolean> {
    return this.chatService.getApiRoomByRoomId(roomID, 0, undefined).pipe(
      map(response =>
        response.info.remainingTime > 0
      ),
      catchError(e => of(false)));
  }

  /**
   *  Check chatroom feedback message availability
   */
  public getChatRoomFeedbackStatus(roomID: string) : Observable<boolean> {
    return this.feedbackService.getApiFeedbackhistoryRoomByRoomId(roomID).pipe(
      map(response =>
        response.responses != null
      ),
      catchError(e => of(false)));
  }

  /**
   *  Check chat rooms every few seconds
   */
  public getChatRooms(): Observable<ChatRoomList>{
    return this.chatService.getApiRooms().pipe(
      tap((response) => {
        this._Rooms.next(response);
      }),
    );
  }

  /**
   * Returns the chat rooms as Observable.
   */
  get isChatRoomsAvailable(): Observable<boolean> {
    return this._Rooms.pipe(
      map(u => u != null),
      catchError(e => of(false))
    );
  }

}


