import {Component, Inject, OnDestroy, OnInit} from '@angular/core';
import {
  FrontendAverageFeedback,
  FrontendUserFeedback
} from "../new_data";
import {Router} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {CommonService} from "../common.service";
import {
  AdminService, ChatService, FeedbackRequest, FeedbackResponse,
  FeedbackService
} from "../../../openapi";
import {interval, Subscription} from "rxjs";
import { HttpClient } from '@angular/common/http';
import {AlertService} from "../_alert";


@Component({
  selector: 'app-user-feedback',
  templateUrl: './user-feedback.component.html',
  styleUrls: ['./user-feedback.component.css']
})
export class UserFeedbackComponent implements OnInit, OnDestroy {
  options = {
    autoClose: true,
    keepAfterRouteChange: true
  };
  constructor(private router: Router, private titleService: Title,
              private httpClient: HttpClient,
              private commonService: CommonService,
              @Inject(FeedbackService) private feedbackService: FeedbackService,
              @Inject(AdminService) private adminService: AdminService,
              public alertService: AlertService) { }

  private feedbackSubscription!: Subscription;

  ratingForm!: Array<FeedbackRequest>;
  averageFeedback: FrontendAverageFeedback[] = []
  userFeedback: FrontendUserFeedback[] = []

  toggleElement: number = -1
  toggleList: string = ""

  authorPerspective: boolean = true

  ngOnInit(): void {
    this.titleService.setTitle("Evaluation Feedback")

    this.feedbackService.getApiFeedback(undefined).subscribe(
      (feedbackForm) => {
        this.ratingForm = feedbackForm.requests;
      },
      (error) => {
        console.log("Ratings form for this chat room is not retrieved properly.", error);
      }
    )

    // Fetch initially and then periodically refetch
    this.fetchFeedback()
    this.feedbackSubscription = interval(10000).subscribe((number) => {
      this.fetchFeedback()
    })
  }

  fetchFeedback(): void {
    this.adminService.getApiFeedbackAverage(this.authorPerspective).subscribe((r) => {
      this.averageFeedback = []
      r.responses.forEach(average => {
        this.averageFeedback.push(
          {
            username: average.username,
            responses: average.responses
          }
        )
      })
    })

    this.adminService.getApiFeedbackHistory(this.authorPerspective).subscribe((r) => {
      this.userFeedback = []
      r.responses.forEach(response => {
        this.userFeedback.push(
          {
            author: response.author,
            recipient: response.recipient,
            roomId: response.room,
            responses: response.responses
          }
        )
      })
    })
  }

  getFeedbacks(username: string): FrontendUserFeedback[] {
    if (this.authorPerspective) {
      return this.userFeedback.filter(f => f.author == username)
    } else {
      return this.userFeedback.filter(f => f.recipient == username)
    }
  }

  idToText(response: FeedbackResponse): string {
    let text = response.value
    this.ratingForm.forEach(r => {
      if (r.id == response.id) {
        r.options.forEach(o => {
          if (o.value.toString() == response.value) {
            text = o.name
          }
        })
      }
    })
    return text
  }

  toggleDirection(value: string): void {
    this.authorPerspective = value == "author"
    this.fetchFeedback()
  }

  home(): void {
    this.router.navigateByUrl('/panel').then()
  }

  ngOnDestroy() {
    this.feedbackSubscription.unsubscribe()
  }
}
