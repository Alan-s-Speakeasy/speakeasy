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
import {AlertService} from "../alert";
import {NgbModal} from "@ng-bootstrap/ng-bootstrap";
import {
  ApexAxisChartSeries,
  ApexChart,
  ChartComponent,
  ApexDataLabels,
  ApexPlotOptions,
  ApexYAxis,
  ApexTitleSubtitle,
  ApexXAxis,
  ApexLegend
} from "ng-apexcharts";

export type ChartOptions = {
  series: ApexAxisChartSeries;
  legend: ApexLegend;
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

  ratingRequests!: Array<FeedbackRequest>;
  averageFeedback: FrontendAverageFeedback[] = []
  userFeedback: FrontendUserFeedback[] = []
  chooseAssignments: boolean = true

  selectedFormName!: string

  formNameOptions: string[] = []

  // TODO: TypeError: Cannot read properties of undefined (reading '0')
  chartDataPerUsername: Map<string, Map<string, number>[]> = new Map<string, Map<string, number>[]>()
  @ViewChild("chart") chart: ChartComponent | undefined;
  allChartOptions: Partial<ChartOptions>[] | any[] = [];

  toggleElement: number = -1
  authorPerspective: boolean = true
  impressionToRead: string = ""

  usernames: string[] = []
  selectedUsernames: string[] = []
  appliedSelectedUsernames: string[] = []
  selectedChartData: Map<string, number>[] = []
  allChartData: Map<string, number>[] = []

  nonOptionQuestionIds: string[] = []

  ngOnInit(): void {
    this.titleService.setTitle("Evaluation Feedback")

    this.feedbackService.getApiFeedbackforms(undefined).subscribe((feedbackForms) => {
      this.formNameOptions = feedbackForms.forms.map( form => form.formName )
      this.selectedFormName = this.formNameOptions[0] // use the first one as default form

      // Fetch initially and then periodically refetch
      this.fetchFeedback()
      this.feedbackSubscription = interval(10000).subscribe(() => {
        this.fetchFeedback()
      })
    })

  }

  fetchFeedback(): void {
    this.feedbackService.getApiFeedbackformByFormName(this.selectedFormName,undefined).subscribe((feedbackForm) => {
        this.ratingRequests = feedbackForm.requests;
        feedbackForm.requests.forEach( (request) => {
          // record text questions
          if (request.options.length == 0 && !this.nonOptionQuestionIds.includes(request.id)) {
            this.nonOptionQuestionIds.push(request.id)
          }
        })
      },
      (error) => {
        console.log("Ratings form for this chat room is not retrieved properly.", error);
      }
    )


    this.adminService.getApiFeedbackaverageByFormName(this.selectedFormName, this.authorPerspective).subscribe((r) => {
      this.averageFeedback = []
      this.usernames = []
      let responses = this.chooseAssignments ? r.assigned : r.requested
      responses.forEach(average => {
        this.averageFeedback.push(
          {
            username: average.username,
            responses: average.responses
          }
        )
        this.usernames.push(average.username)
      })
      this.usernames.sort((a, b) => a.localeCompare(b))
      this.averageFeedback.sort((a, b) => a.username.localeCompare(b.username))
    })

    this.adminService.getApiFeedbackhistoryFormByFormName(this.selectedFormName).subscribe((r) => {
      this.userFeedback = []
      this.chartDataPerUsername = this.generateEmptyChartBucketsPerUsername()
      let responses = this.chooseAssignments ? r.assigned : r.requested
      responses.forEach(response => {
        this.userFeedback.push(
          {
            author: response.author,
            recipient: response.recipient,
            roomId: response.room,
            responses: response.responses
          }
        )
        if (this.ratingRequests) {
          let username = this.authorPerspective ? response.author : response.recipient
          response.responses.forEach(r => {
            if (!this.nonOptionQuestionIds.includes(r.id)){
              let current = this.chartDataPerUsername.get(username)![parseInt(r.id) - 1].get(r.value) || 0
              this.chartDataPerUsername.get(username)![parseInt(r.id) - 1].set(r.value, current + 1)
            }
          })
        }
      })
      this.updateUsernameAndCharts()
    })
  }

  generateEmptyChartBuckets(): Map<string, number>[] {
    let res: Map<string, number>[] = []
    this.ratingRequests.forEach(f => {
      if (!this.nonOptionQuestionIds.includes(f.id)){
        let x = new Map()
        f.options.forEach(o => x.set(o.value.toString(), 0))
        res.push(x)
      }
    })
    return res
  }

  generateEmptyChartBucketsPerUsername(): Map<string, Map<string, number>[]> {
    let res: Map<string, Map<string, number>[]> = new Map
    this.usernames.forEach(u => {
      res.set(u, [])
      this.ratingRequests.forEach(f => {
        if (!this.nonOptionQuestionIds.includes(f.id)) {
          let x = new Map()
          f.options.forEach(o => x.set(o.value.toString(), 0))
          res.get(u)!.push(x)
        }
      })
    })
    return res
  }

  getChartData(usernames: string[], id: string) : number[] {
    if (usernames.length > 0) {
      return Array.from(this.selectedChartData[parseInt(id) - 1].values())
    } else {
      return Array.from(this.allChartData[parseInt(id) - 1].values())
    }
  }

  isSelected(username: string): boolean {
    return this.selectedUsernames.includes(username)
  }

  switch(username: string): void {
    const idx = this.selectedUsernames.findIndex(u => u == username)
    if (idx >= 0) {
      this.selectedUsernames.splice(idx, 1)
    } else {
      this.selectedUsernames.push(username)
    }
  }

  applyFilter(): void {
    this.appliedSelectedUsernames = this.selectedUsernames
    this.updateUsernameAndCharts()
  }

  resetSelected(): void {
    this.selectedUsernames = []
    this.applyFilter()
  }

  updateUsernameAndCharts() : void {
    this.selectedChartData = this.generateEmptyChartBuckets()
    this.allChartData = this.generateEmptyChartBuckets()

    let totalSelected: number[] = new Array(this.selectedChartData.length).fill(0)
    let totalAll: number[] = new Array(this.allChartData.length).fill(0)

    this.chartDataPerUsername.forEach((v, username) => {
      if (this.appliedSelectedUsernames.includes(username)) {
        for (let category = 0; category < this.selectedChartData.length; category++) {
          this.selectedChartData[category].forEach((value, name) => {
            let newValue = value + v[category]!.get(name)!
            this.selectedChartData[category].set(name, newValue)
            totalSelected[category] += v[category]!.get(name)!
          })
        }
      }
      for (let category = 0; category < this.allChartData.length; category++) {
        this.allChartData[category].forEach((value, name) => {
          let newValue = value + v[category]!.get(name)!
          this.allChartData[category].set(name, newValue)
          totalAll[category] += v[category]!.get(name)!
        })
      }
    })

    for (let category = 0; category < this.selectedChartData.length; category++) {
      this.selectedChartData[category].forEach((value, name) => {
        this.selectedChartData[category].set(name, value / totalSelected[category])
      })
    }
    for (let category = 0; category < this.allChartData.length; category++) {
      this.allChartData[category].forEach((value, name) => {
        this.allChartData[category].set(name, value / totalAll[category])
      })
    }

    if (this.allChartOptions.length == 0) {
      this.generateCharts()
    } else {
      this.updateCharts()
    }
  }

  generateCharts(): void {
    this.ratingRequests.forEach(f => {
      if (!this.nonOptionQuestionIds.includes(f.id)) {
        let series = [{
          name: "All users",
          data: this.getChartData([], f.id),
        }]
        if (this.appliedSelectedUsernames.length != 0) {
          series.push({
            name: "Selected users",
            data: this.getChartData(this.appliedSelectedUsernames, f.id)
          })
        }
        this.allChartOptions?.push(
          {
            series: series,
            legend: {
              showForSingleSeries: true
            },
            colors: ['#0066ff', '#ff9933'],
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
              enabled: false
            },
            yaxis: {
              min: 0,
              max: 1,
              tickAmount: 5,
              labels: {
                formatter: function (val) {
                  return val.toFixed(1);
                }
              },
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
      }
    })
  }

  updateCharts(): void {
    this.ratingRequests.forEach(f => {
      if (!this.nonOptionQuestionIds.includes(f.id)) {
        let series = [{
          name: "All users",
          data: this.getChartData([], f.id)
        }]
        if (this.appliedSelectedUsernames.length != 0) {
          series.push({
            name: "Selected users",
            data: this.getChartData(this.appliedSelectedUsernames, f.id)
          })
        }
        this.allChartOptions[parseInt(f.id) - 1].series = series
      }
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
    if (this.appliedSelectedUsernames.length != 0) {
      return this.averageFeedback.filter(f => this.appliedSelectedUsernames.includes(f.username))
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
    this.ratingRequests.forEach(r => {
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

  toggleAssignments(value: string): void {
    this.chooseAssignments = value == "assigned"
    this.fetchFeedback()
  }

  toggleFormName(value: string): void {
    this.nonOptionQuestionIds = []
    this.allChartOptions = []
    this.selectedFormName = value
    this.fetchFeedback()
  }


  home(): void {
    this.router.navigateByUrl('/panel').then()
  }

  ngOnDestroy() {
    this.feedbackSubscription.unsubscribe()
  }
}
