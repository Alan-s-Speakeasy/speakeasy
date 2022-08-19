import {Component, Inject, OnDestroy, OnInit} from '@angular/core';
import {FrontendUserDetail, FrontendUser, FrontendChatroomDetail} from "../new_data";
import {Router} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {CommonService} from "../common.service";
import {AddUserRequest, AdminService, ChatRoomInfo, UserDetails, UserSessionDetails} from "../../../openapi";
import {interval, Subscription} from "rxjs";
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

  private userListSubscription!: Subscription;
  humanDetails: FrontendUserDetail[] = []
  adminDetails: FrontendUserDetail[] = []
  botDetails: FrontendUserDetail[] = []

  sessionToUserMap = new Map<string, FrontendUserDetail>()

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

    this.userListSubscription = interval(1000).subscribe(response=> {

      this.adminService.getApiRoomsAll().subscribe((allchatrooms)=>{
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
      });

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

  pushChatRoomDetails(chatRoomDetails: FrontendChatroomDetail[], chatRoom: ChatRoomInfo) {
    let users: string[] = []
    chatRoom.users.forEach(u => users.push(u.username))

    let aliases :string[] = []
    chatRoom.users.forEach(u => aliases.push(u.alias))

    let sessions: string[] = []
    chatRoom.users.forEach(u => {u.sessions.forEach(s => sessions.push(s))})

    chatRoomDetails.push(
      {
        prompt: chatRoom.prompt,
        roomID: chatRoom.uid,
        startTime: chatRoom.startTime!,
        remainingTime: chatRoom.remainingTime,
        users: users,
        aliases: aliases,
        sessions: sessions
      }
    )
  }

  pushDetail(details: FrontendUserDetail[], usersession: UserSessionDetails): void {
    let userExists = details.find(u => u.userID == usersession.userDetails.id)

    if (userExists) {
      userExists.sessionId.push(usersession.sessionId);
      userExists.startTime.push(usersession.startTime);
      userExists.sessionToken.push(usersession.sessionToken);
      this.sessionToUserMap.set(usersession.sessionId, userExists)
    }
    else {
      let detail = {
        userID: usersession.userDetails.id,
        username: usersession.userDetails.username,
        role: usersession.userDetails.role,
        startTime: [usersession.startTime],
        userSessionAlias: usersession.userSessionAlias,
        sessionId: [usersession.sessionId],
        sessionToken: [usersession.sessionToken],
      }
      details.push(detail)
      this.sessionToUserMap.set(usersession.sessionId, detail)
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

  getPartners(sessions: string[], exclude: string[]): string[] {
    let res = new Set<string>()
    sessions.forEach(s => {
      if (!exclude.includes(s)) {
        let userDetails = this.sessionToUserMap.get(s)
        if (userDetails) {
          res.add(userDetails.username + " (" + userDetails.userSessionAlias + ", " + userDetails.role + ")")
        }
      }
    })
    return Array.from(res)
  }

  sortByTime(chatrooms: FrontendChatroomDetail[]): FrontendChatroomDetail[] {
    return chatrooms.sort((c1, c2) => c2.remainingTime - c1.remainingTime)
  }

  home(): void {
    this.router.navigateByUrl('/panel').then()
  }

  watch(frontendUserDetail: FrontendUserDetail, chatroomDetail: FrontendChatroomDetail): void {

    let partnerUsername = chatroomDetail.users.find(username => username != frontendUserDetail.username)

    this.router.navigateByUrl('/spectate', { state: {
      roomID: chatroomDetail.roomID,
      username: frontendUserDetail.username,
      userAlias: frontendUserDetail.userSessionAlias,
      partnerAlias: partnerUsername,
      userSession: frontendUserDetail.sessionId,
      backUrl: "userStatus"
    } } ).then()
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
      (response) => {
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
    this.adminService.removeApiUser(this.forceRemove, this.usernameToRemove).subscribe(
      (response) => {
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
