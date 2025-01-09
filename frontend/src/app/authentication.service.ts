import {Inject, Injectable} from '@angular/core';
import { BehaviorSubject, Observable, of } from "rxjs";
import {catchError, filter, map, take, tap} from 'rxjs/operators';
import { CommonService } from './common.service';
import {
  SuccessStatus,
  UserSessionDetails,
  UserService,
  AdminService,
  FeedbackService,
  ChatService, UserDetails, LoginRequest
} from "../../openapi";

/**
 * Service class for authentication tasks. It uses the UserService API.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  /** UserDetails created during login. */
  private _userSession = new BehaviorSubject<UserSessionDetails|null>(null);

  /**
   * Constructor
   */
  constructor(@Inject(UserService) private userService: UserService,
              @Inject(AdminService) private adminService: AdminService) {
  }

  /**
   *  Login the user then tell all the subscribers about the new status
   */
  public userLogin(uname: string, pwd: string) : Observable<UserSessionDetails> {
    return this.userService.postApiLogin({username: uname, password: pwd} as LoginRequest).pipe(
      tap((response) => {
        this._userSession.next(response);
      }),
    );
  }

  /**
   * Returns the current session as Observable by calling User Service.
   */
  public sessionDetails(): Observable<UserSessionDetails|null>{
    return this.userService.getApiUserCurrent().pipe(
      tap((u) => {
        this._userSession.next(u);
      }),
      catchError(e => of(null))
    );
  }

  /**
   * Returns the current login state as Observable.
   */
  get isValidSession(): Observable<boolean> {
    return this._userSession.pipe(
      map(u => u != null),
      catchError((e) => of(false))
    );
  }

  /**
   * Returns the current session as Observable.
   */
  get userSessionDetails(): Observable<UserSessionDetails|null>{
    if(this._userSession.getValue() == null) {
      return this.sessionDetails();
    }
    return this._userSession;
  }

  /**
   * Log out the current user then tell all the subscribers about the new status of user session
   *
   * @return Observable
   */
  public userLogout(): Observable<SuccessStatus|null> {
    return this.userService.getApiLogout(undefined, 'body', true).pipe(
      catchError(e => of(null)),
      tap(() => {
        this._userSession.next(null);
      }),
    );
  }



}
