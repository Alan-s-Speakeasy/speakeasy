// This component contains code from https://www.bootdey.com/snippets/view/chat-app

import {Component, EventEmitter, Inject, Input, OnInit, Output} from '@angular/core';
import {PaneLog, Ratings} from "../new_data";
import {FeedbackRequest, FeedbackResponse, FeedbackResponseList, FeedbackService} from "../../../openapi";
import {AlertService} from "../alert";

@Component({
  selector: 'app-rating-pane',
  templateUrl: './rating-pane.component.html',
  styleUrls: ['./rating-pane.component.css']
})
export class RatingPaneComponent implements OnInit {
  options = {
    autoClose: true,
    keepAfterRouteChange: true
  };
  constructor(@Inject(FeedbackService) private feedbackService: FeedbackService,
              public alertService: AlertService) { }

  @Input() paneLog!: PaneLog;

  @Output("removeRoom") removeRoom: EventEmitter<any> = new EventEmitter()

  ratingForm!: Array<FeedbackRequest>;

  ngOnInit(): void {
    this.fetchRatingForm()
    this.retrieveFeedbackHistory()
  }

  // fetch the rating form
  fetchRatingForm(): void {
    this.feedbackService.getApiFeedbackformByFormName(this.paneLog.formRef, undefined).subscribe(
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

  canSubmit(): boolean {
    return this.ratingForm && Object.keys(this.paneLog.ratings).length == this.ratingForm.length
  }

  close(): void {
    this.paneLog.ratingOpen = false
  }

  // submit ratings
  submit(): void {
    if (Object.keys(this.paneLog.ratings).length == this.ratingForm.length) {
      this.feedbackService.postApiFeedbackByRoomId(this.paneLog.roomID, undefined, this.ratings2Responses(this.paneLog.ratings)).subscribe(
        (response) => {
          this.alertService.success("Ratings for Chat - " + this.paneLog.prompt + " (" + this.paneLog.otherAlias + ") successfully submitted!", this.options)
          this.removeRoom.emit()
          //console.log("submitted ratings:", this.ratings2Responses(this.paneLog.ratings))
        },
        (error) => {
          console.log("Feedback is not successfully submitted for the room: ", this.paneLog.roomID);
          if (error.status == 409) {
            this.alertService.error("Ratings for Chat - " + this.paneLog.prompt + " (" + this.paneLog.otherAlias + ") already submitted fron this user!", this.options)
            this.removeRoom.emit()
          }
        }
      );
    } else {
      this.alertService.warn("Please complete the rating form before submitting!",this.options)
    }
  }

  closeWithoutRating(): void {
    const responses: FeedbackResponseList = {responses: []};
    this.feedbackService.postApiFeedbackByRoomId(this.paneLog.roomID, undefined, responses).subscribe(
      (response) => {
        this.alertService.success("Closed Chat - " + this.paneLog.prompt + " (" + this.paneLog.otherAlias + ") without rating successfully.", this.options)
        this.removeRoom.emit()
      },
      (error) => {
        if (error.status == 409) {
          this.alertService.error("Chat - " + this.paneLog.prompt + " (" + this.paneLog.otherAlias + ") already submitted from this user!", this.options)
          this.removeRoom.emit()
        } else if (error.status == 404) {
          this.alertService.error("Chat - " + this.paneLog.prompt + " (" + this.paneLog.otherAlias + ") not found, just closed it.", this.options)
          this.removeRoom.emit()
        } else if (error.status == 403) {
          this.alertService.error(error.message)
        } else {
          this.alertService.error("Something wrong when closing feedback form", this.options)
        }
      }
    )
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
