import {Inject, Injectable} from '@angular/core';
import {PaneLog} from "./new_data";
import requests from "./feedbackrequests.json"
import {FeedbackService, UserSessionDetails} from "../../openapi";

@Injectable({
  providedIn: 'root'
})
export class FrontendDataService {

  constructor(@Inject(FeedbackService) private feedbackService: FeedbackService) { }

  userSession!: UserSessionDetails;

  paneLogs!: PaneLog[]

  ratingForm = requests.requests

  fetchRatingForm (): any[] {
    return this.ratingForm
  }

  public setItem(key: string, value: string) {
    localStorage.setItem(key, value);
  }
  public getItem(key: string){
    return localStorage.getItem(key)
  }
  public removeItem(key:string) {
    localStorage.removeItem(key);
  }
  public clear(){
    localStorage.clear();
  }

}
