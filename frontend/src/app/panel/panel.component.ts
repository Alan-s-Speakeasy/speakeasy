import {Component, Inject, OnInit} from '@angular/core';
import {Router} from "@angular/router";
import {Subscription, interval} from "rxjs";
import {AuthService} from "../authentication.service";
import {Title} from "@angular/platform-browser";
import {UserService, ChatService, ChatRoomList, UserSessionDetails} from "../../../openapi";
import {FrontendDataService} from "../frontend-data.service";
import {CommonService} from "../common.service";
import {takeUntil,take} from "rxjs/operators";
import { AlertService } from '../_alert';

@Component({
  selector: 'app-panel',
  templateUrl: './panel.component.html',
  styleUrls: ['./panel.component.css']
})
export class PanelComponent implements OnInit {
  options = {
    autoClose: true,
    keepAfterRouteChange: true
  };
  private chatRoomsSubscription!: Subscription;
  private chatRoomListSubscription!: Subscription;
  constructor(private router: Router, private frontendDataService: FrontendDataService,
              private titleService: Title,
              @Inject(UserService) private userService: UserService,
              @Inject(AuthService) private authService: AuthService,
              @Inject(CommonService) private commonService: CommonService,
              @Inject(ChatService) private chatService: ChatService,
              public alertService: AlertService) { }

  userName!: string
  role!: string
  session!: UserSessionDetails

  ngOnInit(): void {
    this.titleService.setTitle("Panel Page")

    this.authService.userSessionDetails.subscribe((response)=>{

        if(response != null){
          this.userName = response.userDetails.username
          this.role = response.userDetails.role
          this.session = response
          this.startChatSubscriptions();
        }
      },
      error => {},
      ()=>{
        if (!this.role || !this.userName) {
          this.alertService.success("You are not logged in!", this.options)
          this.router.navigateByUrl('/login').then()
        }
      });

  }

  startChatSubscriptions(): void {
      this.chatRoomsSubscription = interval(2000).subscribe((number) => {
        let newRooms!: ChatRoomList;
        let oldRooms: String[];
        let currentRooms: String[];
        let addedRooms: String[];
        this.commonService.Rooms.pipe(take(1)).subscribe(
          (value) => {
            if (value) {
              oldRooms = value?.rooms.map(({uid}) => uid);
            }
          });
        this.commonService.getChatRooms().pipe(take(1)).subscribe((value) => {
            newRooms = value;
            if (value.rooms) {
              currentRooms = value.rooms.map(({uid}) => uid);
            }
          },
          error => {
          },
          () => {
            addedRooms = currentRooms.filter(item => oldRooms.indexOf(item) == -1);
            if (addedRooms.length > 0) {
              if (addedRooms.length == 1) {
                this.alertService.success("A new chat room has become available!", this.options)
              } else {
                this.alertService.success(addedRooms.length + " new chat rooms has become available!", this.options)
              }
            }
          });
      })

      this.chatRoomListSubscription = this.commonService.Rooms.subscribe((roomList) => {
      })

  }

  userLogout(): void {
    if(confirm("Are you sure you want to log out from your current session?")) {
      this.authService.userLogout().subscribe(
        (response)=> {
          if (response) {
            this.chatRoomsSubscription.unsubscribe();
            this.chatRoomListSubscription.unsubscribe();
            localStorage.clear()
            confirm("You are logged out. Please close all the Speakeasy tabs.")
            this.router.navigateByUrl('/login').then();
          }
          else {
            this.alertService.error("Logout failed. Please try again.")
          }
        },
        error => {
          this.alertService.error("Logout failed. Please try again.")
        }
      );
    }
  }

  chat(): void {
    this.router.navigateByUrl('/chat').then()
  }

  checkHistory(): void {
    this.router.navigateByUrl('/history').then()
  }

  changePassword(): void {
    this.router.navigateByUrl('/password').then()
  }

  userStatus() {
    this.router.navigateByUrl('/userStatus').then()
  }

  chatroomStatus() {
    this.router.navigateByUrl('/chatroomStatus').then()
  }

}
