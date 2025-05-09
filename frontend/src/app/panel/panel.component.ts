import {Component, Inject, OnInit} from '@angular/core';
import {Router} from "@angular/router";
import {interval, Subscription, timer} from "rxjs";
import {AuthService} from "../authentication.service";
import {Title} from "@angular/platform-browser";
import {ChatService, UserService, UserSessionDetails, ChatRoomList, CountUsersResponse} from "../../../openapi";
import {FrontendDataService} from "../frontend-data.service";
import {CommonService} from "../common.service";
import {AlertService} from '../alert';
import {NgbModal} from "@ng-bootstrap/ng-bootstrap";
import {exhaustMap} from "rxjs/operators";

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
  private userDetailsSubscription!: Subscription;
  private userCountSubscription!: Subscription;

  constructor(private router: Router, private frontendDataService: FrontendDataService,
              private titleService: Title,
              @Inject(UserService) private userService: UserService,
              @Inject(AuthService) private authService: AuthService,
              @Inject(CommonService) private commonService: CommonService,
              @Inject(ChatService) private chatService: ChatService,
              public alertService: AlertService,
              private modalService: NgbModal) {
  }

  userName!: string
  role!: string
  session!: UserSessionDetails

  countUsers: CountUsersResponse = { countUsers: 0, countBots: 0, countHumans: 0, countBotsOnline: 0, countHumansOnline: 0 }


  ngOnInit(): void {
    this.titleService.setTitle("Panel Page")

    this.userDetailsSubscription = this.authService.userSessionDetails.subscribe((response) => {
        if (response != null) {
          this.userName = response.userDetails.username
          this.role = response.userDetails.role
          this.session = response
        }
      },
      error => {
      },
      () => {
        if (!this.role || !this.userName) {
          this.router.navigateByUrl('/login').then( () =>
            this.alertService.error("You are not logged in!", this.options) )
        }
      });

    this.userCountSubscription = timer(0, 5000).pipe(
      exhaustMap(() => this.userService.getApiUserCount())
    ).subscribe(
      response => {
        if (response) {
          this.countUsers = response;
        }
      },
      error => {
        this.alertService.error("Failed to get the user count. Please try again.", this.options);
      }
    );

    this.commonService.openSseAndListenRooms()
  }


  openModal(content: any) {
    this.modalService.open(content, { centered: true })
  }

  userLogout(): void {
    this.authService.userLogout().subscribe(
      (response) => {
        if (response) {
          localStorage.clear()
          this.router.navigateByUrl('/login').then();
        } else {
          this.alertService.error("Logout failed. Please try again.")
        }
      },
      error => {
        this.alertService.error("Logout failed. Please try again.")
      }
    );
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

  feedbackStatus() {
    this.router.navigateByUrl('/feedback').then()
  }

  assignmentStatus() {
    this.router.navigateByUrl('/assignment').then()
  }

  ngOnDestroy() {
    this.userDetailsSubscription.unsubscribe();
    this.userCountSubscription.unsubscribe();
  }
}
