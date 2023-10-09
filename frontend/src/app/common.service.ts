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
import {convertFromJSON} from "./new_data";


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
  public Rooms = this._Rooms.asObservable(); // only for roomsAlertEventListener now

  // TODO: To get the configured basePath for SSE, I instantiate AppConfig here.
  //  I'm not sure if it's a good practice. Is there any way to improve it?
  private appConfig: AppConfig = new AppConfig()

  // SSE EventSource object and eventListener which 1) alters when new rooms come 2) updates _Rooms
  private readonly eventSource: EventSource
  private readonly roomsAlertEventListener: EventListener


  /**
   * Constructor
   */
  constructor(@Inject(ChatService) private chatService: ChatService,
              @Inject(FeedbackService) private feedbackService: FeedbackService,
              @Inject(AuthService) private AuthService: AuthService,
              public alertService: AlertService) {

    this.eventSource = new EventSource(
      `${this.appConfig.basePath}/api/sse`, //  TODO: Is there any way to improve it?
      {withCredentials: true}) // Each new EventSource create a client in backend

    this.roomsAlertEventListener = (ev) => {
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

  public addNewRoomsAlertEventListener() {
    this.eventSource.addEventListener('SseRooms', this.roomsAlertEventListener);
  }
  public removeNewRoomsAlertEventListener() {
    this.eventSource.removeEventListener('SseRooms', this.roomsAlertEventListener);
  }

  get currentRooms(): Observable<ChatRoomList|null>{
    return this._Rooms.pipe(
      map(u => u),
      catchError(e => of(null))
    );
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
  public getChatRooms(): Observable<ChatRoomList>{ // TODO: change sse rooms here
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


