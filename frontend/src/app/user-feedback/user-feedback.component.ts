import {Component, Inject, OnDestroy, OnInit} from '@angular/core';
import {FrontendUserDetail, FrontendUser, FrontendChatroomDetail, FrontendAverageFeedback} from "../new_data";
import {Router} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {CommonService} from "../common.service";
import {
  AddUserRequest,
  AdminService,
  ChatRoomInfo, FeedbackRequest,
  FeedbackService,
  UserDetails,
  UserSessionDetails
} from "../../../openapi";
import {interval, Subscription} from "rxjs";
import { HttpClient } from '@angular/common/http';
import {FormControl} from "@angular/forms";
import {NgbModal} from "@ng-bootstrap/ng-bootstrap";
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

  private feedbackListSubscription!: Subscription;

  ratingForm!: Array<FeedbackRequest>;
  averageFeedback: FrontendAverageFeedback[] = []

  toggleElement: number = -1
  toggleList: string = ""

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

    this.feedbackListSubscription = interval(1000).subscribe(response=> {
      this.adminService.getApiFeedbackAverage(true).subscribe((r)=>{
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
    })
  }

  home(): void {
    this.router.navigateByUrl('/panel').then()
  }

  ngOnDestroy() {
    this.feedbackListSubscription.unsubscribe();
  }

}
