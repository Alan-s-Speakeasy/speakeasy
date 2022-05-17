import {Component, Inject, OnDestroy, OnInit} from '@angular/core';
import {Router} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {FrontendChatroomDetail, FrontendUserDetail} from "../new_data";
import {CommonService} from "../common.service";
import {AdminService, ChatRoomInfo, UserSessionDetails} from "../../../openapi";
import {interval, Subscription} from "rxjs";
import {state} from "@angular/animations";

@Component({
  selector: 'app-chatroom-status',
  templateUrl: './chatroom-status.component.html',
  styleUrls: ['./chatroom-status.component.css']
})
export class ChatroomStatusComponent implements OnInit, OnDestroy {

  constructor(private router: Router, private titleService: Title,
              private commonService: CommonService,
              @Inject(AdminService) private adminService: AdminService) { }

  private allChatRoomsSubscription!: Subscription;
  allUserDetails: FrontendUserDetail[] = []

  sessionToUserMap = new Map<string, FrontendUserDetail>()

  activateChatroomDetails: FrontendChatroomDetail[] = []
  allChatroomDetails: FrontendChatroomDetail[] = []

  ngOnInit(): void {
    this.titleService.setTitle("Chatroom Details")

    this.allChatRoomsSubscription = interval(1000).subscribe(response=>{
      this.adminService.getApiUserSessions().subscribe((usersessions) => {
        this.allUserDetails = []
        usersessions.forEach(usersession => {
          this.pushDetail(this.allUserDetails, usersession)
        })
      });

      this.adminService.getApiRoomsActive().subscribe((activechatrooms)=>{
        this.activateChatroomDetails = []
        activechatrooms.rooms.forEach(room => {
          this.pushChatRoomDetails(this.activateChatroomDetails, room)
        })
      });

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
    });
  }

  pushChatRoomDetails(chatRoomDetails: FrontendChatroomDetail[], chatRoom: ChatRoomInfo) {
    let users :string[] = []
    // chatRoom.users.forEach(user => {
    //   user.sessions.forEach(sessionId => {
    //     let found = false
    //     this.allUserDetails.forEach(user => {
    //       if (user.sessionId[0] == sessionId) {
    //         users.push(user.sessionId + " (" + user.username + ", " + user.role + ")")
    //         found = true
    //       }
    //     })
    //     if (!found) {
    //       users.push(sessionId + " (user offline)")
    //     }
    //   })
    // })
    chatRoom.users.forEach(u => users.push(u.alias))

    let sessions: string[] = []
    chatRoom.users.forEach(u => {u.sessions.forEach(s => sessions.push(s))})

    chatRoomDetails.push(
      {
        prompt: chatRoom.prompt,
        roomID: chatRoom.uid,
        startTime: chatRoom.startTime!,
        remainingTime: chatRoom.remainingTime,
        users: users,
        sessions: sessions,
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

  getUsers(sessions: string[]): string[] {
    let res = new Set<string>()
    sessions.forEach(s => {
      let userDetails = this.sessionToUserMap.get(s)
      if (userDetails) {
        res.add(userDetails.username + " (" + userDetails.userSessionAlias + ", " + userDetails.role + ")")
      }
    })
    return Array.from(res)
  }

  home(): void {
    this.router.navigateByUrl('/panel').then()
  }

  watch(chatroomDetail: FrontendChatroomDetail): void {
    this.router.navigateByUrl('/spectate', { state: {
      roomID: chatroomDetail.roomID,
      username: chatroomDetail.users[0],
      userAlias: chatroomDetail.users[0],
      partnerAlias: chatroomDetail.users[1],
      userSession: chatroomDetail.sessions[0],
      users: chatroomDetail.users,
      backUrl: "chatroomStatus"
    } } ).then()
  }

  ngOnDestroy() {
    this.allChatRoomsSubscription.unsubscribe();
  }
}
