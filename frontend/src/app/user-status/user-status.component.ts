import {Component, Inject, OnDestroy, OnInit} from '@angular/core';
import {FrontendUserDetail, FrontendUser, FrontendChatroomDetail} from "../new_data";
import {Router} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {CommonService} from "../common.service";
import {
  AddUserRequest,
  AdminService,
  ChatRoomAdminInfo,
  ChatRoomAdminInfoUsers,
  UserDetails,
  UserSessionDetails
} from "../../../openapi";
import {interval, Subscription} from "rxjs";
import {exhaustMap} from "rxjs/operators";
import { HttpClient } from '@angular/common/http';
import {FormControl} from "@angular/forms";
import {NgbModal} from "@ng-bootstrap/ng-bootstrap";
import {AlertService} from "../_alert";


@Component({
  selector: 'app-user-status',
  templateUrl: './user-status.component.html',
  styleUrls: ['./user-status.component.css']
})
export class UserStatusComponent implements OnInit, OnDestroy {
  options = {
    autoClose: true,
    keepAfterRouteChange: true
  };
  constructor(private router: Router, private titleService: Title,
              private httpClient: HttpClient,
              private commonService: CommonService,
              @Inject(AdminService) private adminService: AdminService,
              public alertService: AlertService,
              private modalService: NgbModal) { }

  private allRoomsSubscription!: Subscription;
  private userSessionSubscription!: Subscription;
  private userListSubscription!: Subscription;

  humanDetails: FrontendUserDetail[] = []
  adminDetails: FrontendUserDetail[] = []
  botDetails: FrontendUserDetail[] = []

  humanList: FrontendUser[] = []
  adminList: FrontendUser[] = []
  botList: FrontendUser[] = []

  toggleElement: number = -1
  toggleList: string = ""

  usernameToAdd = new FormControl("")
  passwordToAdd = new FormControl("")
  roleToAdd: string = ""
  usernameToRemove: string = ""
  forceRemove: boolean = false

  allChatroomDetails: FrontendChatroomDetail[] = []

  ngOnInit(): void {
    this.titleService.setTitle("User Details")

    this.allRoomsSubscription = interval(1000)
      .pipe(exhaustMap(_ => {return this.adminService.getApiRoomsAll()}))
      .subscribe((allchatrooms) => {
        allchatrooms.rooms.forEach(room => {
          let update = true
          this.allChatroomDetails.forEach(currentRoom => {
            if (currentRoom.roomID == room.uid) {
              update = false
              currentRoom.remainingTime = room.remainingTime
            }
          })
          if (update) {
            this.pushChatRoomDetails(this.allChatroomDetails, room)
          }
        })
      })

    this.userSessionSubscription = interval(1000)
      .pipe(exhaustMap(_ => {return this.adminService.getApiUserSessions()}))
      .subscribe((usersessions) => {
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
      })

    this.userListSubscription = interval(1000)
      .pipe(exhaustMap(_ => {return this.adminService.getApiUserList()}))
      .subscribe((userlist) => {
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
      })
  }

  pushChatRoomDetails(chatRoomDetails: FrontendChatroomDetail[], chatRoom: ChatRoomAdminInfo) {
    let userInfo: ChatRoomAdminInfoUsers[] = []
    chatRoom.users.forEach(u => userInfo.push({username: u.username, alias: u.alias}))

    chatRoomDetails.push(
      {
        prompt: chatRoom.prompt,
        roomID: chatRoom.uid,
        startTime: chatRoom.startTime!,
        remainingTime: chatRoom.remainingTime,
        userInfo: userInfo
      }
    )
  }

  pushDetail(details: FrontendUserDetail[], usersession: UserSessionDetails): void {
    let userExistsIndex = details.findIndex(u => u.userID == usersession.userDetails.id)

    if (userExistsIndex < 0) {
      let detail = {
        userID: usersession.userDetails.id,
        username: usersession.userDetails.username,
        role: usersession.userDetails.role,
        startTime: [usersession.startTime],
        sessionId: [usersession.sessionId],
        sessionToken: [usersession.sessionToken],
      }
      details.push(detail)
    } else {
      details[userExistsIndex].sessionId.push(usersession.sessionId)
      details[userExistsIndex].sessionToken.push(usersession.sessionToken)
      details[userExistsIndex].startTime.push(usersession.startTime)
    }
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

  getPartners(userInfo: ChatRoomAdminInfoUsers[], exclude: string): string[] {
    let res = new Set<string>()
    userInfo.forEach(u => {
      if (!exclude.includes(u.username)) {
        res.add(u.username)
      }
    })
    return Array.from(res)
  }

  sortByTime(chatrooms: FrontendChatroomDetail[]): FrontendChatroomDetail[] {
    return chatrooms.sort((c1, c2) => c2.remainingTime - c1.remainingTime)
  }

  isRelevantRoom(chatroomDetail: FrontendChatroomDetail, username: string): boolean {
    return chatroomDetail.userInfo.find(u => u.username == username) != null
  }

  home(): void {
    this.router.navigateByUrl('/panel').then()
  }

  watch(frontendUserDetail: FrontendUserDetail, chatroomDetail: FrontendChatroomDetail): void {

    let user = chatroomDetail.userInfo.find(u => u.username == frontendUserDetail.username)
    let partner = chatroomDetail.userInfo.find(u => u.username != frontendUserDetail.username)

    if (user && partner) {
      this.router.navigateByUrl('/spectate', {
        state: {
          roomID: chatroomDetail.roomID,
          username: user.username,
          userAlias: user.alias,
          partnerAlias: partner.username,
          backUrl: "userStatus"
        }
      }).then()
    }
  }

  openUserAddModal(content: any, role: string) {
    this.roleToAdd = role == "Humans" ? "HUMAN" : role == "Bots" ? "BOT" : role == "Admins" ? "ADMIN" : ""
    console.log(content)
    this.modalService.open(content, { centered: true })
  }

  openRemoveUserModal(content: any, username: string) {
    this.usernameToRemove = username
    this.modalService.open(content, { centered: true })
  }

  addUser(): void {
    // username, password, role
    this.adminService.addApiUser({"username": this.usernameToAdd.value, "role": this.roleToAdd, "password": this.passwordToAdd.value} as AddUserRequest).subscribe(
      () => {
        this.alertService.success("User successfully created.", this.options)
      },
      (error) => {
        this.alertService.error("User could not be created.", this.options)
      }
    )

    this.usernameToAdd.reset("")
    this.passwordToAdd.reset("")
  }

  removeUser(): void {
    this.adminService.removeApiUser(this.forceRemove, this.usernameToRemove).subscribe(() => {
        this.alertService.success("User successfully removed.", this.options)
      },
      (error) => {
        this.alertService.error("User could not be removed.", this.options)
      }
    )
    this.forceRemove = false
  }

  readableTime(remainingTime: number): string {
    const s = Math.floor(remainingTime / 1000);
    const minutes = Math.floor(s / 60);
    const seconds = s % 60;
    return `${minutes < 10 ? '0' : ''}${minutes}:${seconds < 10 ? '0' : ''}${seconds}`;
  }

  ngOnDestroy() {
    this.userListSubscription.unsubscribe();
  }

}
