// This component contains code from https://www.bootdey.com/snippets/view/chat-app

import {Component, EventEmitter, Inject, Input, OnInit, Output} from '@angular/core';
import {PaneLog, Ratings} from "../new_data";
import {FeedbackRequest, FeedbackResponse, FeedbackResponseList, FeedbackService, FormService} from "../../../openapi";
import {AlertService} from "../alert";

@Component({
  selector: 'app-rating-pane-history',
  templateUrl: './rating-pane-history.component.html',
  styleUrls: ['./rating-pane-history.component.css']
})
export class RatingPaneHistoryComponent implements OnInit {
  options = {
    autoClose: true,
    keepAfterRouteChange: true
  };
  constructor(@Inject(FeedbackService) private feedbackService: FeedbackService,
              @Inject(FormService) private formService: FormService,
              public alertService: AlertService) { }

  @Input() paneLog!: PaneLog;

  @Output("removeRoom") removeRoom: EventEmitter<any> = new EventEmitter()

  ratingForm!: Array<FeedbackRequest>;

  ngOnInit(): void {
    if (!this.paneLog.markAsNoFeedback && this.paneLog.formRef != ""){
      this.fetchRatingForm()
      this.retrieveFeedbackHistory()
    }
  }

  // fetch the rating form
  fetchRatingForm(): void {
    this.formService.getApiFeedbackformsByFormName(this.paneLog.formRef,undefined).subscribe(
      (feedbackForm) => {
        this.ratingForm = feedbackForm.requests;
      },
      (error) => {
        console.log("Ratings form for this chat room is not retrieved properly.", error);
      })
  }

  // try to fetch submitted ratings
  retrieveFeedbackHistory(): void {
    this.feedbackService.getApiFeedbackhistoryRoomByRoomId(this.paneLog.roomID, undefined).subscribe(
      (feedback) => {
        if (feedback.responses.length > 0) {
          for (let each of feedback.responses) {
            this.paneLog.ratings[each.id] = each.value;
          }
        } else {
          console.log("failed to find submitted feedback")
        }
      },
      (error) => {
        console.log("Feedback responses are not retrieved properly for the chat room.", error);
      }
    );
  }

  ratings2Responses(ratings: Ratings): FeedbackResponseList {
    let responses: FeedbackResponseList = {responses: []}
    for (let key in ratings) {
      let response: FeedbackResponse = {
        id: key, value: ratings[key]
      };
      responses.responses.push(
        response
      )
    }
    return responses
  }


}
