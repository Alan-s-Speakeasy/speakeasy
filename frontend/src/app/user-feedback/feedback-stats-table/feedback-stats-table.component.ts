import {Component, EventEmitter, Input, Output, TemplateRef} from '@angular/core';
import {AdminService, FeedbackRequest, FeedbackResponse, FeedBackStatsOfRequest} from "../../../../openapi";
import {FrontendAverageFeedback, FrontendChatroomDetail, FrontendUserFeedback} from "../../new_data";
import {NgbPagination, NgbPopover, NgbTooltip} from "@ng-bootstrap/ng-bootstrap";
import {NgForOf, NgIf, NgStyle, SlicePipe} from "@angular/common";
import {MatCheckbox} from "@angular/material/checkbox";
import {FormBuilder} from "@angular/forms";
import {Router} from "@angular/router";

@Component({
  selector: 'app-feedback-stats-table',
  standalone: true,
  imports: [
    NgbPopover,
    NgbPagination,
    SlicePipe,
    NgForOf,
    NgIf,
    MatCheckbox,
    NgbTooltip,
    NgStyle
  ],
  templateUrl: './feedback-stats-table.component.html',
  styleUrl: './feedback-stats-table.component.css'
})
export class FeedbackStatsTableComponent {

  page: number = 1;
  pageSize: number = 10;
  toggleElement = -1;

  sortColumn: string = '';
  sortDirection: 'asc' | 'desc' | '' = '';


  @Input() authorPerspective: boolean | undefined;
  @Input() nonOptionQuestionIds: string[] | undefined;
  @Input() userFeedback: FrontendUserFeedback[] = []
  @Input() averageFeedback: FrontendAverageFeedback[] = [];
  @Input() statsOfAllRequests!: Array<FeedBackStatsOfRequest>;
  @Input() appliedSelectedUsernames: string[] = []

  @Input() openImpression!: (content: any, impression: FeedbackResponse) => void;
  @Input() readImpression: TemplateRef<any> | undefined;
  @Output() page_2Change = new EventEmitter<number>();
  @Input() ratingRequests!: Array<FeedbackRequest>;

  // Input lambda each checked username
  @Input() selectedUsernames: string[] = []
  // Output lambda each checked username
  @Output() onRowSelected  = new EventEmitter<string>();

  constructor(private fb: FormBuilder, private router: Router, private adminService: AdminService) {}

  getStatOfRequest(requestID: string): FeedBackStatsOfRequest {
    return this.statsOfAllRequests.find(s => s.requestID == requestID)!
  }

  mapValueToFeedbackRequestText(response: FeedBackStatsOfRequest): string {
    let text = response.average
    const roundedAverage = Math.round(parseFloat(response.average)).toString()
    this.ratingRequests.forEach(r => {
      if (r.id == response.requestID) {
        r.options.forEach(o => {
          if (o.value.toString() == roundedAverage) {
            text = o.name
          }
        })
      }
    })
    return text
  }

  getFeedbacks(username: string): FrontendUserFeedback[] {
    if (this.authorPerspective) {
      return this.userFeedback.filter(f => f.author == username)
    } else {
      return this.userFeedback.filter(f => f.recipient == username)
    }
  }


  getAverageFeedback(): FrontendAverageFeedback[] {
    if (this.appliedSelectedUsernames.length != 0) {
      return this.averageFeedback.filter(f => this.appliedSelectedUsernames.includes(f.username))
    } else {
      return this.averageFeedback
    }
  }


  idToText(response: FeedbackResponse): string {
    let text = response.value
    // Round up the rating to the nearest integer
    const roundedResponseValue = Math.round(parseFloat(response.value)).toString()
    this.ratingRequests.forEach(r => {
      if (r.id == response.id) {
        r.options.forEach(o => {
          if (o.value.toString() == roundedResponseValue) {
            text = o.name
          }
        })
      }
    })
    return text
  }


  sortTable(id: string) {
    if (this.sortColumn == id) {
      // if '' -> asc, if asc -> desc, if desc -> ''
      if (this.sortDirection == 'desc') {
        this.sortColumn = '';
        this.sortDirection = '';
        return
      }
      this.sortDirection = this.sortDirection == '' ? 'asc' : 'desc';
    } else {
      this.sortColumn = id;
      this.sortDirection = 'asc';
    }

    // sort the table
    this.averageFeedback.sort((a, b) => {
      const a_question_average = a.responses.find(r => r.requestID == id)!.average
      const b_question_average = b.responses.find(r => r.requestID == id)!.average
      if (this.sortDirection == 'asc') {
        return parseFloat(a_question_average) - parseFloat(b_question_average)
      } else {
        return parseFloat(b_question_average) - parseFloat(a_question_average)
      }
    })

  }

  watch(roomID:string): void {
    this.router.navigateByUrl('/spectate', { state: {
        roomID:roomID,
        backUrl: "feedback"
      } } ).then()
  }
}
