import {Component, Inject, OnDestroy, OnInit, ViewChild} from '@angular/core';
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
import {NgbModal} from "@ng-bootstrap/ng-bootstrap";
import {
  ApexAxisChartSeries,
  ApexChart,
  ChartComponent,
  ApexDataLabels,
  ApexPlotOptions,
  ApexYAxis,
  ApexTitleSubtitle,
  ApexXAxis
} from "ng-apexcharts";

export type ChartOptions = {
  series: ApexAxisChartSeries;
  chart: ApexChart;
  dataLabels: ApexDataLabels;
  plotOptions: ApexPlotOptions;
  yaxis: ApexYAxis;
  xaxis: ApexXAxis;
  title: ApexTitleSubtitle;
};

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
              public alertService: AlertService,
              private modalService: NgbModal) { }

  private feedbackSubscription!: Subscription;

  ratingForm!: Array<FeedbackRequest>;
  averageFeedback: FrontendAverageFeedback[] = []
  userFeedback: FrontendUserFeedback[] = []

  aggregated: Map<string, number>[] = []
  @ViewChild("chart") chart: ChartComponent | undefined;
  allChartOptions: Partial<ChartOptions>[] | any[] = [];

  toggleElement: number = -1
  toggleList: string = ""

  authorPerspective: boolean = true
  impressionToRead: string = ""

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
      this.generateChartBuckets()
      r.responses.forEach(response => {
        this.userFeedback.push(
          {
            author: response.author,
            recipient: response.recipient,
            roomId: response.room,
            responses: response.responses
          }
        )
        if (this.ratingForm) {
          response.responses.slice(0, -1).forEach(r => {
            let current = this.aggregated[parseInt(r.id) - 1].get(r.value) || 0
            this.aggregated[parseInt(r.id) - 1].set(r.value, current + 1)
          })
        }
        })
      if (this.allChartOptions.length == 0) {
        this.generateCharts()
      } else {
        this.updateCharts()
      }
    })
  }

  generateChartBuckets(): void {
    this.aggregated = [];
    this.ratingForm.slice(0, -1).forEach(f => {
      let x = new Map()
      f.options.forEach(o => x.set(o.value.toString(), 0))
      this.aggregated.push(x)
    })
  }

  generateCharts(): void {
    this.ratingForm.slice(0, -1).forEach(f => {
      this.allChartOptions?.push(
        {
          series: [
            {
              name: f.shortname,
              data: Array.from(this.aggregated[parseInt(f.id) - 1].values())
            },
          ],
          chart: {
            height: 300,
            type: "bar",
            animations: {
              enabled: false
            }
          },
          plotOptions: {
            bar: {
              dataLabels: {
                position: "bottom"
              }
            }
          },
          dataLabels: {
            enabled: true
          },
          xaxis: {
            categories: Array.from(f.options.map(o => o.name)),
            crosshairs: {
              fill: {
                type: "gradient",
                gradient: {
                  colorFrom: "#D8E3F0",
                  colorTo: "#BED1E6",
                  stops: [0, 100],
                  opacityFrom: 0.4,
                  opacityTo: 0.5
                }
              }
            }
          },
          title: {
            text: f.shortname,
            align: "center",
            style: {
              color: "#444"
            }
          }
        }
      )
    })
  }

  updateCharts(): void {
    this.ratingForm.slice(0, -1).forEach(f => {
      this.allChartOptions[parseInt(f.id) - 1].series = [{
        data: Array.from(this.aggregated[parseInt(f.id) - 1].values())
      }]
    })
  }

  getFeedbacks(username: string): FrontendUserFeedback[] {
    if (this.authorPerspective) {
      return this.userFeedback.filter(f => f.author == username)
    } else {
      return this.userFeedback.filter(f => f.recipient == username)
    }
  }

  openImpression(content: any, impression: FeedbackResponse): void {
    this.impressionToRead = impression.value
    this.modalService.open(content, { centered: true })
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
