import {Inject, Injectable} from '@angular/core';
import {BehaviorSubject, Subscription, observable, Observable, throwError, of, pipe, interval} from "rxjs";
import {takeUntil,take} from "rxjs/operators";
import {map, catchError, tap} from 'rxjs/operators';
import {AuthService} from "./authentication.service";
import { AppConfig } from './app.config';

import {
  FeedbackService,
  ChatService,
  ChatRoomList
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

  // TODO: To get the configured basePath for SSE, I instantiate AppConfig here.
  //  I'm not sure if it's a good practice. Is there any way to improve it?
  private appConfig: AppConfig = new AppConfig()

  // SSE EventSource object and eventListener which 1) alters when new rooms come 2) updates _Rooms
  private eventSource: EventSource | null = null
  private readonly roomsAlertEventListener: EventListener
  private readonly chatMessageEventListener: EventListener
  private readonly chatReactionEventListener: EventListener
  private readonly SseChatEventUrl: string = `${this.appConfig.basePath}/api/sse`


  /**
   * Constructor
   */
  constructor(@Inject(ChatService) private chatService: ChatService,
              @Inject(FeedbackService) private feedbackService: FeedbackService,
              @Inject(AuthService) private AuthService: AuthService,
              public alertService: AlertService) {

    this.roomsAlertEventListener =  (ev) => {
      // console.log("Here is one time call of roomsAlertEventListener")
      let oldRooms: String[];
      let currentRooms: String[];
      let addedRooms: String[];
      // this.Rooms are not updated yet here
      this.Rooms.pipe(take(1)).subscribe( // Automatic Unsubscription in Nesting TODO: check other nested subscribe
        (value) => {
          if (value) {
            oldRooms = value?.rooms.map(({uid}) => uid); // [roomid, roomid ...]
          }
        });
      // convert data from sse to ChatRoomList
      const response = convertFromJSON<ChatRoomList>((ev as MessageEvent).data);
      // update this._Rooms as well as this.Rooms
      this._Rooms.next(response);

      // check if there is any new room received
      if (response.rooms) {
        currentRooms = response.rooms.map(({uid}) => uid);
        addedRooms = currentRooms.filter(item => oldRooms && oldRooms.indexOf(item) == -1);
        // init _roomsStateMap[addedRoomId] with an empty state
        addedRooms.forEach( roomId =>
          this._roomsStateMap.set(
            roomId as string,
            new BehaviorSubject<SseRoomState>({ messages: [], reactions: [] }))
        )

        if (this.roomsWithAlerts) {
          if (addedRooms.length > 0) {
            if (addedRooms.length == 1) {
              this.alertService.success("A new chat room has become available!", this.options)
            } else {
              this.alertService.success(addedRooms.length + " new chat rooms has become available!", this.options)
            }
          }
        }
      }
    }

    this.chatMessageEventListener =  (ev) => {
      const sseChatMessage = convertFromJSON<SseChatMessage>((ev as MessageEvent).data)
      const roomId = sseChatMessage.roomId
      // TODO: also update remaining time here
      if (!this._roomsStateMap.has(roomId)) {
        this._roomsStateMap.set(roomId, new BehaviorSubject<SseRoomState>({ messages: [], reactions: [] }));
        console.warn('This should not happen: No roomId key in _roomsStateMap when adding a message')
      }
      const stateSubject = this._roomsStateMap.get(roomId)!
      stateSubject.next({
        ...stateSubject.getValue(),
        messages: [...stateSubject.getValue().messages, sseChatMessage]
      })
    }

    this.chatReactionEventListener = (ev) => {
      const sseChatReaction = convertFromJSON<SseChatReaction>((ev as MessageEvent).data)
      const roomId = sseChatReaction.roomId
      if (!this._roomsStateMap.has(roomId)) {
        this._roomsStateMap.set(roomId, new BehaviorSubject<SseRoomState>({ messages: [], reactions: [] }));
        console.warn('This should not happen: No roomId key in _roomsStateMap when adding a reaction')
      }
      const stateSubject = this._roomsStateMap.get(roomId)!
      stateSubject.next({
        ...stateSubject.getValue(),
        reactions: [...stateSubject.getValue().reactions, sseChatReaction]
      })
    }
  }

  public openSseAndListenRooms(withAlerts: boolean = true) {
    this.roomsWithAlerts = withAlerts
    if (this.eventSource == null) {
      this.eventSource = new EventSource( this.SseChatEventUrl,
        {withCredentials: true}) // Each new EventSource() creates a new SseClient in backend
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

  public getStateByRoomId(roomId: string): Observable<SseRoomState|null> {
    if (!this._roomsStateMap.has(roomId)) {
      console.warn(`Can't find such roomId: ${roomId}`)
      return of(null)
    }
    return this._roomsStateMap.get(roomId)!.asObservable();
  }


  /**
   *  Get a chat room status
   */
  public getChatRoomStatus(roomID: string) : Observable<boolean> {
    return this.chatService.getApiRoomByRoomIdBySince(roomID, 0, undefined).pipe(
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


