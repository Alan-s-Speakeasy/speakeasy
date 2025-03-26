import {Component, EventEmitter, Input, Output, TemplateRef} from '@angular/core';
import {
  AdminService,
  FeedbackRequest,
  FeedbackResponse,
  FeedBackStatsOfRequest
} from "../../../../openapi";
import {FrontendAverageFeedback, FrontendChatroomDetail, FrontendUserFeedback} from "../../new_data";
import {NgbModal, NgbPagination, NgbPopover, NgbTooltip} from "@ng-bootstrap/ng-bootstrap";
import {NgClass, NgForOf, NgIf, NgStyle, SlicePipe} from "@angular/common";
import {MatCheckbox} from "@angular/material/checkbox";
import {FormBuilder, FormsModule} from "@angular/forms";
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
    NgStyle,
    FormsModule,
    NgClass
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
  usernameFilter: string = '';

  impressionToRead: string = '';


  @Input() authorPerspective: boolean | undefined;
  @Input() nonOptionQuestionIds: string[] | undefined;
  @Input() userFeedback: FrontendUserFeedback[] = []
  // For each username, contains the statistics of each request per username
  @Input() averageFeedback: FrontendAverageFeedback[] = [];
  // Stats of all requests merged
  @Input() statsOfAllRequests!: Array<FeedBackStatsOfRequest>;
  @Input() appliedSelectedUsernames: string[] = []

  @Input() readImpression: TemplateRef<any> | undefined;
  @Output() page_2Change = new EventEmitter<number>();
  @Input() ratingRequests!: Array<FeedbackRequest>;

  // Input lambda each checked username
  @Input() selectedUsernames: string[] = []
  // Output lambda each checked username
  @Output() onRowSelected  = new EventEmitter<string>();
  // Output lambda all checked usernames
  @Output() onAllSelected  = new EventEmitter<string>();

  constructor(private fb: FormBuilder,
              private router: Router,
              private adminService: AdminService,
              private modalService: NgbModal
  ) {
  }

  getStatOfRequest(requestID: string): FeedBackStatsOfRequest {
    return this.statsOfAllRequests.find(s => s.requestID == requestID)!
  }

  mapValueToFeedbackRequestText(response: FeedBackStatsOfRequest): string {
    let text = response.average
    const roundedAverage = Math.round(parseFloat(response.average))
    this.ratingRequests.forEach(r => {
      if (r.id == response.requestID) {
        r.options.forEach(o => {
          if (o.value === roundedAverage) {
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
    let averageFeedbackProcessed = this.averageFeedback.filter(f => f.username.includes(this.usernameFilter))
    // sort the table
    if (this.sortColumn != '') {
       averageFeedbackProcessed.sort((a, b) => {
        const a_question_average = a.responses.find(r => r.requestID == this.sortColumn)!.average
        const b_question_average = b.responses.find(r => r.requestID == this.sortColumn)!.average
        if (this.sortDirection == 'asc') {
          return parseFloat(a_question_average) - parseFloat(b_question_average)
        } else {
          return parseFloat(b_question_average) - parseFloat(a_question_average)
        }
      })
    }

    if (this.appliedSelectedUsernames.length != 0) {
      return averageFeedbackProcessed .filter(f => this.appliedSelectedUsernames.includes(f.username))
    } else {
      return averageFeedbackProcessed
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


  setSorting(columnID: string) {
    if (this.sortColumn == columnID) {
      // if '' -> asc, if asc -> desc, if desc -> ''
      if (this.sortDirection == 'desc') {
        this.sortColumn = '';
        this.sortDirection = '';
        return
      }
      this.sortDirection = this.sortDirection == '' ? 'asc' : 'desc';
    } else {
      this.sortColumn = columnID;
      this.sortDirection = 'asc';
    }
  }

  watch(roomID:string): void {
    // Pass some infos to the spectate component
    this.router.navigateByUrl('/spectate', { state: {
        roomID: roomID,
        isViewedAsHistory:true,
        backUrl: "feedback"
      } } ).then()
  }

  isControversial(response_i: FeedBackStatsOfRequest, requestStats : FeedBackStatsOfRequest): boolean {
    // NOTE : There are better, more statistically robust ways of comparing variances of a subpopulation (like Levene's test ?)
    // As I don't have any data to test things on it, and I'm not sure about the sample size, I'm using a simple method here of
    // just comparing the variances.

    // To take into account the number of responses the ratio is weighted by the number of responses.
    // This prevents the situation where a few responses with a large variance can skew the result.
    if (response_i.count < 3) {
      return false
    }
    const ALPHA = 5
    const var_i = response_i.variance * response_i.count / (response_i.count + ALPHA)
    const var_global = requestStats.variance

/*
    // Follows a weird rule of thumb, reference : https://library.virginia.edu/data/articles/a-rule-thumb-unequal-variances
    // This can definitely change if needed/required.
    const TRESHOLD_VARIANCE_RATIO = 3
    const s_max = Math.max(var_i, var_global)
    const s_min = Math.min(var_i, var_global)
    return (s_max / s_min) > TRESHOLD_VARIANCE_RATIO
*/
    // This should be a parameter of the UI
    return var_i > 3
  }

  openImpression(content: any, impression: FeedbackResponse): void {
    this.impressionToRead = impression.value;
    this.modalService.open(content, {centered: true});
  }

  protected readonly isNaN = isNaN;
  protected readonly parseFloat = parseFloat;
}
