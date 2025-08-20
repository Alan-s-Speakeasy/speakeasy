import {Component, Inject, OnDestroy, OnInit} from '@angular/core';
import {Router} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {FrontendChatroomDetail} from "../new_data";
import {CommonService} from "../common.service";
import {FrontendDataService} from "../frontend-data.service";

import {AdminService, ChatRoomAdminInfo, UserDetails} from "../../../openapi";
import {Subscription, timer} from "rxjs";
import {exhaustMap, take} from "rxjs/operators";
import {NgbDate} from "@ng-bootstrap/ng-bootstrap";
import { HttpClient } from '@angular/common/http';
import {UserCondition} from "./users-involved-selected/users-involved-selected.component";

@Component({
  selector: 'app-chatroom-status',
  templateUrl: './chatroom-status.component.html',
  styleUrls: ['./chatroom-status.component.css'],
})
export class ChatroomStatusComponent implements OnInit, OnDestroy {

  constructor(private router: Router, private titleService: Title,
              private commonService: CommonService,
              @Inject(AdminService) private adminService: AdminService,
              private http : HttpClient,
              private frontendDataService: FrontendDataService) { }

  private activeRoomsSubscription!: Subscription;

  activateChatroomDetails: FrontendChatroomDetail[] = []
  allChatroomDetails: FrontendChatroomDetail[] = []

  ITEM_PER_PAGE = 10
  currentPageOfActivateRooms: number = 1
  pageArrayOfActivateRooms: number[] = [1]

  numOfAllRooms: number = 0
  currentPageOfAllRooms: number = 1
  pageArrayOfAllRooms: number[] = [1]

  dateRangeSelected: {from: NgbDate | null, to: NgbDate |null } = {from: null, to: null};

  // Users for the search box
  allUsers : UserDetails[] = [];
  selectedUsers : UserDetails[] | null = null;
  selectedUserConditions : UserCondition[] = [];

  // For exporting chatrooms
  selectedChatRoomsIdsForExport: Set<string> = new Set<string>();
  allChatRoomsAreSelectedForExport: boolean = false;

  // Search functionality
  searchTermActive: string = '';
  searchTermAll: string = '';
  searchPromptActive: string = '';
  searchPromptAll: string = '';
  filteredActiveChatroomDetails: FrontendChatroomDetail[] = [];

  ngOnInit(): void {
    this.titleService.setTitle("Chatroom Details")

    // Restore selected chatrooms from localStorage if they exist
    const savedSelectedChatrooms = this.frontendDataService.getItem('selectedChatrooms');
    if (savedSelectedChatrooms) {
      const selectedIds = JSON.parse(savedSelectedChatrooms);
      this.selectedChatRoomsIdsForExport = new Set(selectedIds);
    }

    this.activeRoomsSubscription = timer(2500, 10_000)
      .pipe(exhaustMap(_ => {return this.adminService.getApiRoomsActive()}))
      .subscribe((activechatrooms) => {
        this.activateChatroomDetails = []
        activechatrooms.rooms.forEach(room => {
          this.pushChatRoomDetails(this.activateChatroomDetails, room)
        })
        this.applyActiveSearch();
        // update page info
        const maxPage = Math.ceil(this.filteredActiveChatroomDetails.length / this.ITEM_PER_PAGE);
        if (this.currentPageOfActivateRooms > maxPage) { this.currentPageOfActivateRooms = maxPage }
        if (activechatrooms.rooms.length > 0 && this.currentPageOfActivateRooms == 0) {
          this.currentPageOfActivateRooms = 1
        }
        this.pageArrayOfActivateRooms = Array.from({ length: maxPage }, (_, i) => i + 1);
      })

    // fetch all rooms once with page=1
    this.setCurrentPage(1, false)

    // Get all users, for the select bar
    this.adminService.getApiUserList().subscribe((users) => {
      this.allUsers = users
    })

    // Initialize filtered arrays for active chatrooms
    this.filteredActiveChatroomDetails = [...this.activateChatroomDetails];
  }

  paginate<T>(list: T[], currentPage: number):  T[] {
    const startIdx = (currentPage - 1) * this.ITEM_PER_PAGE;
    const endIdx = currentPage * this.ITEM_PER_PAGE;
    return list.slice(startIdx, endIdx);
  }

  /**
   * Rests filters of allChat table
   */
  resetFiltersAllChat() {
    this.dateRangeSelected = {from: null, to: null};
    this.selectedUserConditions = [];
    this.selectedChatRoomsIdsForExport = new Set<string>();
    this.searchTermAll = '';
    this.searchPromptAll = '';
    // Clear selected chatrooms from localStorage
    this.frontendDataService.removeItem('selectedChatrooms');
    this.setCurrentPage(1, false);
  }


  /**
   * Sets the current page of the chatrooms table, both active rooms and all rooms.
   *
   * @param page
   * @param activateRooms
   */
  setCurrentPage(page: number, activateRooms: boolean = false) {
    if (activateRooms) {
      this.currentPageOfActivateRooms = page
    } else {
      let timeRange_str = undefined;
      if (this.dateRangeSelected.to && this.dateRangeSelected.from) {
        timeRange_str = [this.dateRangeSelected.from, this.dateRangeSelected.to].map(date => {
          return new Date(date.year, date.month - 1, date.day).getTime();
        }).join(',');
      }

      // Perfoms a cartesian product of the selected users to get all possible tuples from the user conditions.
      let selectedUsersTuples_str = this.selectedUserConditions.flatMap(condition => condition.cartesianProductToIdStr());
      this.currentPageOfAllRooms = page
      this.adminService.getApiRoomsAll(
        this.currentPageOfAllRooms,
        this.ITEM_PER_PAGE,
        timeRange_str,
        selectedUsersTuples_str,
        this.searchTermAll.trim(),
        this.searchPromptAll.trim(),
        )
        .pipe(take(1))
        .subscribe((paginatedRooms) => {
          this.allChatroomDetails = [];
          paginatedRooms.rooms.forEach(room => {
            this.pushChatRoomDetails(this.allChatroomDetails, room);
          });
          this.numOfAllRooms = paginatedRooms.numOfAllRooms;

          // Update page info
          const maxPage = Math.ceil(paginatedRooms.numOfAllRooms / this.ITEM_PER_PAGE);
          if (this.currentPageOfAllRooms > maxPage) {
            this.currentPageOfAllRooms = maxPage;
          }
          this.pageArrayOfAllRooms = Array.from({length: maxPage}, (_, i) => i + 1);
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

  /**
   * Called when a chatroom is selected to be later exported.
   * Will add the chatroom to the set of selected chatrooms if it is not already in the set.
   *
   * @param roomID The room ID of the chatroom to be exported.
   */
  toggleSelectedChatRoomForExport(roomID: string) {
    if (this.selectedChatRoomsIdsForExport.has(roomID)) {
      this.selectedChatRoomsIdsForExport.delete(roomID);
    }
    else {
      this.selectedChatRoomsIdsForExport.add(roomID);
    }

    // Save selected chatrooms to localStorage
    this.frontendDataService.setItem('selectedChatrooms', JSON.stringify(Array.from(this.selectedChatRoomsIdsForExport)));
  }

  /**
   * Export the selected chatrooms in the selected format.
   * @param format The selected format to export the chatrooms in.
   */
  exportChatrooms(format : "json" | "csv") {
    if (this.selectedChatRoomsIdsForExport.size === 0) {
      return;
    }
    const roomsID_str = Array.from(this.selectedChatRoomsIdsForExport).join(',');
    this.adminService.getApiRoomsExport(roomsID_str, format).subscribe((response) => {
      // If CSV is selected, the CSV files are zipped, so the type is zip
      const blob = new Blob([response], { type: 'application/' + (format == "csv" ? "zip" : format) });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `chatrooms.${(format == "csv" ? "zip" : format)}`;
      a.click();
      window.URL.revokeObjectURL(url);
    })

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

  /**
   * Apply search filter to active chatrooms based on room ID and prompt (client-side)
   */
  applyActiveSearch(): void {
    let filtered = [...this.activateChatroomDetails];

    // Filter by room ID if search term is provided
    if (this.searchTermActive.trim()) {
      const searchTerm = this.searchTermActive.toLowerCase().trim();
      filtered = filtered.filter(room =>
        room.roomID.toLowerCase().includes(searchTerm)
      );
    }

    // Filter by prompt if search term is provided
    if (this.searchPromptActive.trim()) {
      const searchPrompt = this.searchPromptActive.toLowerCase().trim();
      filtered = filtered.filter(room =>
        room.prompt.toLowerCase().includes(searchPrompt)
      );
    }

    this.filteredActiveChatroomDetails = filtered;
    this.updateActivePagination();
  }

  /**
   * Update pagination info for active rooms after filtering
   */
  private updateActivePagination(): void {
    const maxPage = Math.ceil(this.filteredActiveChatroomDetails.length / this.ITEM_PER_PAGE);
    if (this.currentPageOfActivateRooms > maxPage && maxPage > 0) {
      this.currentPageOfActivateRooms = maxPage;
    }
    if (this.filteredActiveChatroomDetails.length > 0 && this.currentPageOfActivateRooms == 0) {
      this.currentPageOfActivateRooms = 1;
    }
    this.pageArrayOfActivateRooms = Array.from({ length: maxPage }, (_, i) => i + 1);
  }

  /**
   * Handle search input changes for active chatrooms
   */
  onActiveSearchChange(): void {
    this.currentPageOfActivateRooms = 1;
    this.applyActiveSearch();
  }

  /**
   * Handle search input changes for all chatrooms (server-side filtering)
   */
  onAllSearchChange(): void {
    this.setCurrentPage(1, false);
  }

  /**
   * Handle prompt search input changes for active chatrooms
   */
  onActivePromptSearchChange(): void {
    this.currentPageOfActivateRooms = 1;
    this.applyActiveSearch();
  }

  /**
   * Handle prompt search input changes for all chatrooms (server-side filtering)
   */
  onAllPromptSearchChange(): void {
    this.setCurrentPage(1, false);
  }

  /**
   * Clear active search and reset to show all active chatrooms
   */
  clearActiveSearch(): void {
    this.searchTermActive = '';
    this.applyActiveSearch();
  }

  /**
   * Clear all search and reset to show all chatrooms (server-side)
   */
  clearAllSearch(): void {
    this.searchTermAll = '';
    this.setCurrentPage(1, false);
  }

  /**
   * Clear active prompt search and reset to show all active chatrooms
   */
  clearActivePromptSearch(): void {
    this.searchPromptActive = '';
    this.applyActiveSearch();
  }

  /**
   * Clear all prompt search and reset to show all chatrooms (server-side)
   */
  clearAllPromptSearch(): void {
    this.searchPromptAll = '';
    this.setCurrentPage(1, false);
  }

  ngOnDestroy() {
    this.activeRoomsSubscription.unsubscribe()
  }
}
