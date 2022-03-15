import {Component, Inject, OnDestroy, OnInit} from '@angular/core';
import {FrontendUserDetail, FrontendUser} from "../new_data";
import {Router} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {CommonService} from "../common.service";
import {AdminService, UserDetails, UserSessionDetails} from "../../../openapi";
import {interval, Subscription} from "rxjs";
import { HttpClient } from '@angular/common/http';


@Component({
  selector: 'app-user-status',
  templateUrl: './user-status.component.html',
  styleUrls: ['./user-status.component.css']
})
export class UserStatusComponent implements OnInit, OnDestroy {

  constructor(private router: Router, private titleService: Title,
              private httpClient: HttpClient,
              private commonService: CommonService,
              @Inject(AdminService) private adminService: AdminService) { }

  private userListSubscription!: Subscription;
  humanDetails: FrontendUserDetail[] = []
  adminDetails: FrontendUserDetail[] = []
  botDetails: FrontendUserDetail[] = []

  humanList: FrontendUser[] = []
  adminList: FrontendUser[] = []
  botList: FrontendUser[] = []

  ngOnInit(): void {
    this.titleService.setTitle("User Details")

    this.userListSubscription = interval(1000).subscribe(response=> {

      this.adminService.getApiUserSessions().subscribe((usersessions) => {
        while (this.humanDetails.length > 0) {
          this.humanDetails.pop()
        }
        while (this.adminDetails.length > 0) {
          this.adminDetails.pop()
        }
        while (this.botDetails.length > 0) {
          this.botDetails.pop()
        }
        usersessions.forEach(usersession => {
          if (usersession.userDetails.role == "HUMAN") {
            this.pushDetail(this.humanDetails, usersession)
          }
          if (usersession.userDetails.role == "ADMIN") {
            this.pushDetail(this.adminDetails, usersession)
          }
          if (usersession.userDetails.role == "BOT") {
            this.pushDetail(this.botDetails, usersession)
          }
        })
      });

      this.adminService.getApiUserList().subscribe((userlist) => {
        while (this.humanList.length > 0) {
          this.humanList.pop()
        }
        while (this.adminList.length > 0) {
          this.adminList.pop()
        }
        while (this.botList.length > 0) {
          this.botList.pop()
        }
        userlist.forEach(user => {
          if (user.role == "HUMAN") {
            this.pushItem(this.humanList, user)
          }
          if (user.role == "ADMIN") {
            this.pushItem(this.adminList, user)
          }
          if (user.role == "BOT") {
            this.pushItem(this.botList, user)
          }
        })
      });
    })
  }

  pushDetail(details: FrontendUserDetail[], usersession: UserSessionDetails): void {
    details.push(
      {
        userID: usersession.userDetails.id,
        username: usersession.userDetails.username,
        role: usersession.userDetails.role,
        startTime: usersession.startTime,
        userSessionAlias: usersession.userSessionAlias,
        sessionId: usersession.sessionId,
        sessionToken: usersession.sessionToken,
      }
    )
  }

  pushItem(list: FrontendUser[], userDetail: UserDetails): void {
    list.push(
      {
        userID: userDetail.id,
        role: userDetail.role,
        username: userDetail.username,
      }
    )
  }

  allUserDetails = [
    {name: "Humans", table: "success", details: this.humanDetails},
    {name: "Bots", table: "warning", details: this.botDetails},
    {name: "Admins", table: "info", details: this.adminDetails},
  ]

  allUserLists = [
    {name: "Humans", table: "success", list: this.humanList},
    {name: "Bots", table: "warning", list: this.botList},
    {name: "Admins", table: "info", list: this.adminList},
  ]

  home(): void {
    this.router.navigateByUrl('/panel').then()
  }

  ngOnDestroy() {
    this.userListSubscription.unsubscribe();
  }

}
