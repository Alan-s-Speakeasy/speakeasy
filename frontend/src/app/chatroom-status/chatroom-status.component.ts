import {Component, Inject, OnDestroy, OnInit} from '@angular/core';
import {Router} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {FrontendChatroomDetail} from "../new_data";
import {CommonService} from "../common.service";
import {AdminService, ChatRoomAdminInfo, ChatRoomAdminInfoUsers} from "../../../openapi";
import {interval, Subscription} from "rxjs";
import {exhaustMap} from "rxjs/operators";

@Component({
  selector: 'app-chatroom-status',
  templateUrl: './chatroom-status.component.html',
  styleUrls: ['./chatroom-status.component.css']
})
export class ChatroomStatusComponent implements OnInit, OnDestroy {

  constructor(private router: Router, private titleService: Title,
              private commonService: CommonService,
              @Inject(AdminService) private adminService: AdminService) { }

  private activeRoomsSubscription!: Subscription;
  private allRoomsSubscription!: Subscription;

  activateChatroomDetails: FrontendChatroomDetail[] = []
  allChatroomDetails: FrontendChatroomDetail[] = []

  ngOnInit(): void {
    this.titleService.setTitle("Chatroom Details")

    this.activeRoomsSubscription = interval(1000)
      .pipe(exhaustMap(_ => {return this.adminService.getApiRoomsActive()}))
      .subscribe((activechatrooms) => {
        this.activateChatroomDetails = []
        activechatrooms.rooms.forEach(room => {
          this.pushChatRoomDetails(this.activateChatroomDetails, room)
        })
      })

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
  }

  pushChatRoomDetails(chatRoomDetails: FrontendChatroomDetail[], chatRoom: ChatRoomAdminInfo) {
    let userInfo: ChatRoomAdminInfoUsers[] = []
    chatRoom.users.forEach(u => userInfo.push({username: u.username, alias: u.alias}))

    chatRoomDetails.push(
      {
        assignment: chatRoom.assignment,
        prompt: chatRoom.prompt,
        roomID: chatRoom.uid,
        startTime: chatRoom.startTime,
        remainingTime: chatRoom.remainingTime,
        userInfo: userInfo,
        markAsNoFeedBack: chatRoom.markAsNoFeedback
      }
    )
  }

  home(): void {
    this.router.navigateByUrl('/panel').then()
  }

  watch(chatroomDetail: FrontendChatroomDetail): void {
    let user1 = chatroomDetail.userInfo[0]
    let user2 = chatroomDetail.userInfo[1]
    this.router.navigateByUrl('/spectate', { state: {
      assignment: chatroomDetail.assignment,
      markAsNoFeedback: chatroomDetail.markAsNoFeedBack,
      roomID: chatroomDetail.roomID,
      username: user1.username,
      userAlias: user1.alias,
      partnerAlias: user2.username,
      backUrl: "chatroomStatus"
    } } ).then()
  }

  ngOnDestroy() {
    this.activeRoomsSubscription.unsubscribe()
    this.allRoomsSubscription.unsubscribe()
  }
}
