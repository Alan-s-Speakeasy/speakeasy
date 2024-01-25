import {Component, Inject, OnDestroy, OnInit} from '@angular/core';
import {Router} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {FrontendChatroomDetail} from "../new_data";
import {CommonService} from "../common.service";

import {AdminService, ChatRoomAdminInfo} from "../../../openapi";
import {interval, Subscription, timer} from "rxjs";
import {exhaustMap, take} from "rxjs/operators";

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
  // private allRoomsSubscription!: Subscription;

  activateChatroomDetails: FrontendChatroomDetail[] = []
  allChatroomDetails: FrontendChatroomDetail[] = []

  ITEM_PER_PAGE = 10
  currentPageOfActivateRooms: number = 1
  pageArrayOfActivateRooms: number[] = [1]

  numOfAllRooms: number = 0
  currentPageOfAllRooms: number = 1
  pageArrayOfAllRooms: number[] = [1]

  ngOnInit(): void {
    this.titleService.setTitle("Chatroom Details")

    this.activeRoomsSubscription = timer(2500, 10_000)
      .pipe(exhaustMap(_ => {return this.adminService.getApiRoomsActive()}))
      .subscribe((activechatrooms) => {
        this.activateChatroomDetails = []
        activechatrooms.rooms.forEach(room => {
          this.pushChatRoomDetails(this.activateChatroomDetails, room)
        })
        // update page info
        const maxPage = Math.ceil(this.activateChatroomDetails.length / this.ITEM_PER_PAGE);
        if (this.currentPageOfActivateRooms > maxPage) { this.currentPageOfActivateRooms = maxPage }
        if (activechatrooms.rooms.length > 0 && this.currentPageOfActivateRooms == 0) {
          this.currentPageOfActivateRooms = 1
        }
        this.pageArrayOfActivateRooms = Array.from({ length: maxPage }, (_, i) => i + 1);
      })

    // fetch all rooms once with page=1
    this.setCurrentPage(1, false)
  }

  paginate<T>(list: T[], currentPage: number):  T[] {
    const startIdx = (currentPage - 1) * this.ITEM_PER_PAGE;
    const endIdx = currentPage * this.ITEM_PER_PAGE;
    return list.slice(startIdx, endIdx);
  }

  setCurrentPage(page: number, activateRooms: boolean = false) {
    if (activateRooms) {
      this.currentPageOfActivateRooms = page
    } else {
      this.currentPageOfAllRooms = page
      // this.adminService.getApiRoomsAll(this.currentPageOfAllRooms, this.ITEM_PER_PAGE)
      this.adminService.getApiRoomsAll(this.currentPageOfAllRooms, this.ITEM_PER_PAGE)
        .pipe(take(1))
        .subscribe((paginatedRooms) => {
          this.allChatroomDetails = [];
          paginatedRooms.rooms.forEach(room => {
            this.pushChatRoomDetails(this.allChatroomDetails, room);
          });
          this.numOfAllRooms = paginatedRooms.numOfAllRooms;

          // Update page info
          const maxPage = Math.ceil(paginatedRooms.numOfAllRooms / this.ITEM_PER_PAGE);
          if (this.currentPageOfAllRooms > maxPage) { this.currentPageOfAllRooms = maxPage; }
          this.pageArrayOfAllRooms = Array.from({ length: maxPage }, (_, i) => i + 1);
        });
    }
  }

  pushChatRoomDetails(chatRoomDetails: FrontendChatroomDetail[], chatRoom: ChatRoomAdminInfo) {

    chatRoomDetails.push(
      {
        assignment: chatRoom.assignment,
        formRef: chatRoom.formRef,
        prompt: chatRoom.prompt,
        roomID: chatRoom.uid,
        startTime: chatRoom.startTime,
        remainingTime: chatRoom.remainingTime,
        userInfo: chatRoom.users,
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
      formRef: chatroomDetail.formRef,
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
    // this.allRoomsSubscription.unsubscribe()
  }
}
