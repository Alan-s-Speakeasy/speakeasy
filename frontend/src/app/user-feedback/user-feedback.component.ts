import {Component, Inject, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {
  FrontendAverageFeedback,
  FrontendUserFeedback
} from "../new_data";
import {Router} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {CommonService} from "../common.service";
import {
  AdminService, FeedbackRequest, FeedbackResponse, FeedbackResponseStatsItem,
  FeedbackService, FeedBackStatsOfRequest
} from "../../../openapi";
import {interval, Subscription} from "rxjs";
import {HttpClient} from '@angular/common/http';
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

const COLORS_BARS = ['#ff9933', '#0066ff', '#33cc33', '#cc33ff', '#ff3333'];

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
              private modalService: NgbModal) {
  }

  private feedbackSubscription!: Subscription;

  ratingRequests!: Array<FeedbackRequest>;
  // This should be a map, but it is essentially username -> average feedback
  averageFeedback: FrontendAverageFeedback[] = []
  userFeedback: FrontendUserFeedback[] = []
  chooseAssignments: boolean = true

  selectedFormName!: string

  formNameOptions: string[] = []

  // TODO: TypeError: Cannot read properties of undefined (reading '0')
  // Username -> (requestID -> answer?)[].
  chartDataPerUsername: Map<string, Map<string, number>[]> = new Map<string, Map<string, number>[]>()
  @ViewChild("chart") chart: ChartComponent | undefined;
  allChartOptions: Partial<ChartOptions>[] | any[] = [];

  toggleElement: number = -1
  authorPerspective: boolean = true
  impressionToRead: string = ""

  usernames: string[] = []
  selectedUsernames: string[] = []
  appliedSelectedUsernames: string[] = []
  // The data for the selected feedback id (as a string, for some ""historical"" reasons...)
  selectedChartData: Map<string, number>[] = []
  // Contains the data for the selected users
  // Format : questionId (as string for ""historical"" reasons) -> username -> [values for each question]
  selectedUsernamesChartData: Map<string, Map<string, Array<number>>> = new Map<string, Map<string, Array<number>>>()
  // The data for the users. Can for example display the average.
  allChartData: Map<string, number>[] = []

  // Contains the ids of the questions that are not option questions (eg, text questions)
  nonOptionQuestionIds: string[] = []

  page = 1;
  pageSize = 10;

  ngOnInit(): void {
    this.titleService.setTitle("Evaluation Feedback")

    this.feedbackService.getApiFeedbackforms(undefined).subscribe((feedbackForms) => {
      this.formNameOptions = feedbackForms.forms.map(form => form.formName)
      this.selectedFormName = this.formNameOptions[0] // use the first one as default form

      // Fetch initially and then periodically refetch
      this.fetchFeedback()
      this.feedbackSubscription = interval(10_000).subscribe(() => {
        this.fetchFeedback()
      })
    })

  }

  fetchFeedback(): void {
    this.feedbackService.getApiFeedbackformByFormName(this.selectedFormName, undefined).subscribe((feedbackForm) => {
        this.ratingRequests = feedbackForm.requests;
        feedbackForm.requests.forEach((request) => {
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
            responses: average.statsOfResponsePerRequest
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
            if (!this.nonOptionQuestionIds.includes(r.id)) {
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
      if (!this.nonOptionQuestionIds.includes(f.id)) {
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

  /**
   * Given a list of usernames and an id of a question, return this.selectedCHartData[id] or allChartData if the list is not empty,
   * @param usernames
   * @param id
   */
  getChartData(usernames: string[], id: string): number[] {
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

  updateUsernameAndCharts(): void {
    this.selectedChartData = this.generateEmptyChartBuckets()
    this.allChartData = this.generateEmptyChartBuckets()

    let totalSelected: number[] = new Array(this.selectedChartData.length).fill(0)
    let totalAll: number[] = new Array(this.allChartData.length).fill(0)

    // THis thing is black magic
    this.chartDataPerUsername.forEach((responses, username) => {
      if (this.isSelected(username)) {
        for (let request = 0; request < this.selectedChartData.length; request++) {
          this.selectedChartData[request].forEach((value, name) => {
            let newValue = value + responses[request]!.get(name)!
            this.selectedChartData[request].set(name, newValue)
            totalSelected[request] += responses[request]!.get(name)!
          })
        }
      }
      for (let category = 0; category < this.allChartData.length; category++) {
        this.allChartData[category].forEach((value, name) => {
          let newValue = value + responses[category]!.get(name)!
          this.allChartData[category].set(name, newValue)
          totalAll[category] += responses[category]!.get(name)!
        })
      }
    })

    // Category are requests, or questions
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
        if (this.appliedSelectedUsernames.length > 0) {
          for (let username of this.appliedSelectedUsernames) {
            series.push({
              name: username,
              data: this.getChartData([username], f.id)
            })
          }
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
          data: this.getChartData([], f.id),
          color: "#3954ea",
        }]
        for (let index = 0; index < this.appliedSelectedUsernames.length; index++) {
          let username = this.appliedSelectedUsernames[index];
          series.push({
            name: username,
            data: this.getChartData([username], f.id),
            // Increment color for each user
            color: COLORS_BARS[index % COLORS_BARS.length]
          });
        }
        this.allChartOptions[parseInt(f.id) - 1].series = series;
      }
    })
  }


  openImpression(content: any, impression: FeedbackResponse): void {
    this.impressionToRead = impression.value
    this.modalService.open(content, {centered: true})
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
