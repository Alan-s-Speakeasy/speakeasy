import {Inject, Injectable} from '@angular/core';
import {BehaviorSubject, Subscription, observable, Observable, throwError, of, pipe, interval} from "rxjs";
import {takeUntil,take} from "rxjs/operators";
import {map, catchError, tap} from 'rxjs/operators';
import { HttpClient, HttpHeaders, HttpErrorResponse } from '@angular/common/http';
import {AuthService} from "./authentication.service";
import {
  UserSessionDetails,
  UserService,
  AdminService,
  FeedbackService,
  ChatService,
  LoginRequest,
  SuccessStatus,
  PasswordChangeRequest,
  UserDetails, ChatRoomList, ChatRoomState, ChatMessageReaction, FeedbackRequestList, FeedbackResponseList
} from "../../openapi";
import {AlertService} from "./_alert";


/**
 * Service class for common tasks.
 */
@Injectable({
  providedIn: 'root'
})
export class CommonService {
  options = {
    autoClose: true,
    keepAfterRouteChange: true
  };
  //userSession!: UserSessionDetails;
  chatRooms!: ChatRoomList;
  private _Rooms = new BehaviorSubject<ChatRoomList|null>(null);
  public Rooms = this._Rooms.asObservable();
  /**
   * Constructor
   */
  constructor(@Inject(ChatService) private chatService: ChatService,
              @Inject(FeedbackService) private feedbackService: FeedbackService,
              @Inject(AuthService) private AuthService: AuthService,
              public alertService: AlertService) {
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

  get currentRoomList(): Observable<ChatRoomList|null>{
    return this._Rooms.pipe(
      map(u => u),
      catchError(e => of(null))
    );
  }


  /**
   *  Get a chat room status
   */
  public getChatRoomStatus(roomID: string) : Observable<boolean> {
    return this.chatService.getApiRoomWithRoomidWithSince(roomID, 0, undefined).pipe(
      map(response =>
        response.info.remainingTime > 0
      ),
      catchError(e => of(false)));
  }

  /**
   *  Check chatroom feedback message availability
   */
  public getChatRoomFeedbackStatus(roomID: string) : Observable<boolean> {
    return this.feedbackService.getApiFeedbackhistoryWithRoomid(roomID).pipe(
      map(response =>
        response.responses != null
      ),
      catchError(e => of(false)));
  }

  public alertOnNewChatRoom() {
    return interval(2000).subscribe((number) => {
      let newRooms!: ChatRoomList;
      let oldRooms: String[];
      let currentRooms: String[];
      let addedRooms: String[];
      this.Rooms.pipe(take(1)).subscribe(
        (value) => {
          if (value) {
            oldRooms = value?.rooms.map(({uid}) => uid);
          }
        });
      this.getChatRooms().pipe(take(1)).subscribe((value) => {
          newRooms = value;
          if (value.rooms) {
            currentRooms = value.rooms.map(({uid}) => uid);
          }
        },
        error => {
        },
        () => {
          addedRooms = currentRooms.filter(item => oldRooms && oldRooms.indexOf(item) == -1);
          if (addedRooms.length > 0) {
            if (addedRooms.length == 1) {
              this.alertService.success("A new chat room has become available!", this.options)
            } else {
              this.alertService.success(addedRooms.length + " new chat rooms has become available!", this.options)
            }
          }
        });
    });
  }

}


