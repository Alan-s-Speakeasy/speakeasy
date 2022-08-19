import {Component, Inject, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {
  FrontendAverageFeedback,
  FrontendUserFeedback
} from "../new_data";
import {Router} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {CommonService} from "../common.service";
import {
  AdminService, FeedbackRequest, FeedbackResponse,
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
import {animate, style, transition, trigger} from "@angular/animations";

export type ChartOptions = {
  series: ApexAxisChartSeries;
  chart: ApexChart;
  colors: any[];
  dataLabels: ApexDataLabels;
  plotOptions: ApexPlotOptions;
  yaxis: ApexYAxis;
  xaxis: ApexXAxis;
  title: ApexTitleSubtitle;
};

@Component({
  selector: 'app-user-feedback',
  templateUrl: './user-feedback.component.html',
  styleUrls: ['./user-feedback.component.css'],
  animations: [
    trigger('inOutAnimation', [
      transition(':enter', [
        style({ opacity: 0 }),
        animate('200ms', style({ opacity: 1 })),
      ]),
      transition(':leave', [
        animate('200ms', style({ opacity: 0 }))
      ])
    ])
  ]
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

  chartDataPerUsername: Map<string, Map<string, number>[]> = new Map
  @ViewChild("chart") chart: ChartComponent | undefined;
  allChartOptions: Partial<ChartOptions>[] | any[] = [];

  toggleElement: number = -1
  authorPerspective: boolean = true
  impressionToRead: string = ""

  usernames: string[] = []
  selectedUsername: string | null = null
  usernameChartData: Map<string, number>[] = []
  remainderChartData: Map<string, number>[] = []

  ngOnInit(): void {
    this.titleService.setTitle("Evaluation Feedback")

    this.feedbackService.getApiFeedback(undefined).subscribe((feedbackForm) => {
        this.ratingForm = feedbackForm.requests;
      },
      (error) => {
        console.log("Ratings form for this chat room is not retrieved properly.", error);
      }
    )

    // Fetch initially and then periodically refetch
    this.fetchFeedback()
    this.feedbackSubscription = interval(10000).subscribe(() => {
      this.fetchFeedback()
    })
  }

  fetchFeedback(): void {
    this.adminService.getApiFeedbackAverage(this.authorPerspective).subscribe((r) => {
      this.averageFeedback = []
      this.usernames = []
      r.responses.forEach(average => {
        this.averageFeedback.push(
          {
            username: average.username,
            responses: average.responses
          }
        )
        this.usernames.push(average.username)
      })
    })

    this.adminService.getApiFeedbackHistory().subscribe((r) => {
      this.userFeedback = []
      this.chartDataPerUsername = this.generateEmptyChartBucketsPerUsername()
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
          let username = this.authorPerspective ? response.author : response.recipient
          response.responses.slice(0, -1).forEach(r => {
            let current = this.chartDataPerUsername.get(username)![parseInt(r.id) - 1].get(r.value) || 0
            this.chartDataPerUsername.get(username)![parseInt(r.id) - 1].set(r.value, current + 1)
          })
        }
        })
      this.updateUsernameAndCharts()
    })
  }

  generateEmptyChartBuckets(): Map<string, number>[] {
    let res: Map<string, number>[] = []
    this.ratingForm.slice(0, -1).forEach(f => {
      let x = new Map()
      f.options.forEach(o => x.set(o.value.toString(), 0))
      res.push(x)
    })
    return res
  }

  generateEmptyChartBucketsPerUsername(): Map<string, Map<string, number>[]> {
    let res: Map<string, Map<string, number>[]> = new Map
    this.usernames.forEach(u => {
      res.set(u, [])
      this.ratingForm.slice(0, -1).forEach(f => {
        let x = new Map()
        f.options.forEach(o => x.set(o.value.toString(), 0))
        res.get(u)!.push(x)
      })
    })
    return res
  }

  getChartData(username: string | null, id: string) : number[] {
    if (username != null) {
      return Array.from(this.usernameChartData[parseInt(id) - 1].values())
    } else {
      return Array.from(this.remainderChartData[parseInt(id) - 1].values())
    }
  }

  updateUsernameAndCharts() : void {
    this.usernameChartData = this.generateEmptyChartBuckets()
    this.remainderChartData = this.generateEmptyChartBuckets()
    this.chartDataPerUsername.forEach((v, username) => {
      if (this.selectedUsername == username) {
        for (let category = 0; category < this.usernameChartData.length; category++) {
          this.usernameChartData[category].forEach((value, name) => {
            let newValue = value + v[category]!.get(name)!
            this.usernameChartData[category].set(name, newValue)
          })
        }
      } else {
        for (let category = 0; category < this.remainderChartData.length; category++) {
          this.remainderChartData[category].forEach((value, name) => {
            let newValue = value + v[category]!.get(name)!
            this.remainderChartData[category].set(name, newValue)
          })
        }
      }
    })
    if (this.allChartOptions.length == 0) {
      this.generateCharts()
    } else {
      this.updateCharts()
    }
  }

  generateCharts(): void {
    this.ratingForm.slice(0, -1).forEach(f => {
      let series = [{
        name: "All other users",
        data: this.getChartData(null, f.id)
      }]
      if (this.selectedUsername != null) {
        series.push({
          name: this.selectedUsername,
          data: this.getChartData(this.selectedUsername, f.id)
        })
      }
      this.allChartOptions?.push(
        {
          series: series,
          colors: ['#0066ff', '#ff9933'],
          chart: {
            height: 300,
            type: "bar",
            animations: {
              enabled: false
            },
            stacked: true
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
      let series = [{
        name: "All other users",
        data: this.getChartData(null, f.id)
      }]
      if (this.selectedUsername != null) {
        series.push({
          name: this.selectedUsername,
          data: this.getChartData(this.selectedUsername, f.id)
        })
      }
      this.allChartOptions[parseInt(f.id) - 1].series = series
    })
  }

  getFeedbacks(username: string): FrontendUserFeedback[] {
    if (this.authorPerspective) {
      return this.userFeedback.filter(f => f.author == username)
    } else {
      return this.userFeedback.filter(f => f.recipient == username)
    }
  }

  getAverageFeedback(): FrontendAverageFeedback[] {
    if (this.selectedUsername != null) {
      return this.averageFeedback.filter(f => f.username == this.selectedUsername)
    } else {
      return this.averageFeedback
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
