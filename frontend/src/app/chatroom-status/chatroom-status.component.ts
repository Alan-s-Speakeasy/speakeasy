import {Component, Inject, OnDestroy, OnInit} from '@angular/core';
import {Router} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {FrontendChatroomDetail, FrontendUserDetail} from "../new_data";
import {CommonService} from "../common.service";
import {AdminService, ChatRoomAdminInfo, UserSessionDetails} from "../../../openapi";
import {interval, Subscription} from "rxjs";

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

  activateChatroomDetails: FrontendChatroomDetail[] = []
  allChatroomDetails: FrontendChatroomDetail[] = []

  ngOnInit(): void {
    this.titleService.setTitle("Chatroom Details")

    this.allChatRoomsSubscription = interval(1000).subscribe(() => {

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

  pushChatRoomDetails(chatRoomDetails: FrontendChatroomDetail[], chatRoom: ChatRoomAdminInfo) {
    let users :string[] = []
    chatRoom.users.forEach(u => !users.includes(u.username) ? users.push(u.username) : null)

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
        sessions: sessions,
      }
    )
  }

  home(): void {
    this.router.navigateByUrl('/panel').then()
  }

  watch(chatroomDetail: FrontendChatroomDetail): void {
    this.router.navigateByUrl('/spectate', { state: {
      roomID: chatroomDetail.roomID,
      username: chatroomDetail.users[0],
      userAlias: chatroomDetail.aliases[0],
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
