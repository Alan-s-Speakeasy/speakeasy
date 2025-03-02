import {Component, inject, Inject, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {
  FrontendAverageFeedback,
  FrontendUserFeedback
} from "../new_data";
import {Router} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {CommonService} from "../common.service";
import {
  AdminService, FeedbackRequest, FeedbackResponse,
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
  averageFeedback: FrontendAverageFeedback[] = []
  userFeedback: FrontendUserFeedback[] = []
  statsOfAllRequest: Array<FeedBackStatsOfRequest> = []
  chooseAssignments: boolean = true

  selectedFormName!: string

  formNameOptions: string[] = []

  chartDataPerUsername: Map<string, Map<string, number>[]> = new Map<string, Map<string, number>[]>()
  @ViewChild("chart") chart: ChartComponent | undefined;
  allChartOptions: Partial<ChartOptions>[] | any[] = [];

  toggleElement: number = -1
  authorPerspective: boolean = true
  impressionToRead: string = ""

  usernames: string[] = []
  selectedUsernames: string[] = []
  // The usernames plotted. Sometimes, we want to plot only a subset of the selected usernames as the chart can get too crowded
  plottedUsernames: string[] = []
  selectedChartData: Map<string, number>[] = []
  selectedUsernamesChartData: Map<string, Map<string, Array<number>>> = new Map<string, Map<string, Array<number>>>()
  allChartData: Map<string, number>[] = []

  nonOptionQuestionIds: string[] = []

  page = 1;
  pageSize = 10;

  ngOnInit(): void {
    this.titleService.setTitle("Evaluation Feedback");

    this.feedbackService.getApiFeedbackforms(undefined).subscribe((feedbackForms) => {
      this.formNameOptions = feedbackForms.forms.map(form => form.formName);
      this.selectedFormName = this.formNameOptions[0]; // use the first one as default form

      // Fetch initially and then periodically refetch
      this.fetchFeedback();
      this.feedbackSubscription = interval(10_000).subscribe(() => {
        this.fetchFeedback();
      });
    });
  }

    fetchFeedback(): void {
      this.feedbackService.getApiFeedbackformByFormName(this.selectedFormName, undefined).subscribe((feedbackForm) => {
          this.ratingRequests = feedbackForm.requests;
          feedbackForm.requests.forEach((request) => {
            if (request.options.length == 0 && !this.nonOptionQuestionIds.includes(request.id)) {
              this.nonOptionQuestionIds.push(request.id);
            }
          });

          this.adminService.getApiFeedbackaverageByFormName(this.selectedFormName, this.authorPerspective).subscribe((r) => {
            this.averageFeedback = [];
            this.usernames = [];
            let responses = this.chooseAssignments ? r.assigned : r.requested;
            responses.forEach(average => {
              this.averageFeedback.push({
                username: average.username,
                responses: average.statsOfResponsePerRequest
              });
              this.usernames.push(average.username);
            });
            this.usernames.sort((a, b) => a.localeCompare(b));
            this.averageFeedback.sort((a, b) => a.username.localeCompare(b.username));
            this.statsOfAllRequest = r.statsOfAllRequest;

            this.adminService.getApiFeedbackhistoryFormByFormName(this.selectedFormName).subscribe((r) => {
              this.userFeedback = [];
              this.chartDataPerUsername = this.generateEmptyChartBucketsPerUsername();
              let responses = this.chooseAssignments ? r.assigned : r.requested;
              responses.forEach(response => {
                this.userFeedback.push({
                  author: response.author,
                  recipient: response.recipient,
                  roomId: response.room,
                  responses: response.responses
                });
                if (this.ratingRequests) {
                  let username = this.authorPerspective ? response.author : response.recipient;
                  response.responses.forEach(resp => {
                    if (!this.nonOptionQuestionIds.includes(resp.id)) {
                      let current = this.chartDataPerUsername.get(username)![parseInt(resp.id)].get(resp.value) || 0;
                      this.chartDataPerUsername.get(username)![parseInt(resp.id)].set(resp.value, current + 1);
                    }
                  });
                }
              });
              this.updateUsernameAndCharts(); // Update charts AFTER all data is fetched
            });
          });
        },
        (error) => {
          console.log("Ratings form for this chat room is not retrieved properly.", error);
        }
      );
  }

  /**
   * Export the selected lines (Selected usernames) as a CSV format by calling the backend API
   */
  exportFeedback(): void {
    if (this.selectedUsernames.length == 0) {
      this.alertService.error("Please select at least one user to export.", this.options);
      return;
    }
    const usernames_str = this.selectedUsernames.join(",");
    this.feedbackService.getApiFeedbackaverageexportByFormName(this.selectedFormName, usernames_str, this.authorPerspective)
      .subscribe((response) => {
      const blob = new Blob([response], {type: 'text/csv'});
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'feedback.csv';
      a.click();
      window.URL.revokeObjectURL(url);
    });
  }

  generateEmptyChartBuckets(): Map<string, number>[] {
    let res: Map<string, number>[] = [];
    this.ratingRequests.forEach(f => {
      if (!this.nonOptionQuestionIds.includes(f.id)) {
        let x = new Map();
        f.options.forEach(o => x.set(o.value.toString(), 0));
        res.push(x);
      }
    });
    return res;
  }

  generateEmptyChartBucketsPerUsername(): Map<string, Map<string, number>[]> {
    let res: Map<string, Map<string, number>[]> = new Map();
    this.usernames.forEach(u => {
      res.set(u, []);
      this.ratingRequests.forEach(f => {
        if (!this.nonOptionQuestionIds.includes(f.id)) {
          let x = new Map();
          f.options.forEach(o => x.set(o.value.toString(), 0));
          res.get(u)!.push(x);
        }
      });
    });
    return res;
  }

  getChartData(usernames: string[], id: string): number[] {
    const questionIndex = parseInt(id);
    const allUsersData = Array.from(this.allChartData[questionIndex].values());

    if (usernames.length > 0) {
      const aggregatedUserData: Map<string, number> = new Map();
      this.ratingRequests[questionIndex].options.forEach(o => aggregatedUserData.set(o.value.toString(), 0));

      for (const username of usernames) {
        const userDataForQuestion = this.chartDataPerUsername.get(username)?.[questionIndex];
        if (userDataForQuestion) {
          userDataForQuestion.forEach((count, option) => {
            aggregatedUserData.set(option, (aggregatedUserData.get(option) || 0) + count);
          });
        }
      }
      // Total count of all responses for the selected users
      const totalForUsers = Array.from(aggregatedUserData.values()).reduce((sum, count) => sum + count, 0);
      // COmpute the average
      return Array.from(aggregatedUserData.values()).map(count => totalForUsers > 0 ? count / totalForUsers : 0);
    } else {
      return allUsersData;
    }
  }

  isSelected(username: string): boolean {
    return this.selectedUsernames.includes(username);
  }

  switch(username: string): void {
    const idx = this.selectedUsernames.findIndex(u => u == username);
    if (idx >= 0) {
      this.selectedUsernames.splice(idx, 1);
    } else {
      this.selectedUsernames.push(username);
    }
    this.applyFilter();
  }

  switchAll(): void {
    this.selectedUsernames = this.selectedUsernames.length == this.usernames.length ? [] : [...this.usernames];
    this.applyFilter()
  }

  applyFilter(): void {
    this.plottedUsernames = [...this.selectedUsernames]; // Create a copy
    // Limit the number of selected usernames to 4
    if (this.plottedUsernames.length > 4) {
      this.plottedUsernames = this.plottedUsernames.slice(0, 4);
    }
    this.updateUsernameAndCharts();
  }

  resetSelected(): void {
    this.selectedUsernames = [];
    this.applyFilter();
  }

  updateUsernameAndCharts(): void {
    this.allChartData = this.generateEmptyChartBuckets();

    let totalAll: number[] = new Array(this.allChartData.length).fill(0);

    this.chartDataPerUsername.forEach((responses) => {
      for (let request = 0; request < this.allChartData.length; request++) {
        this.allChartData[request].forEach((value, name) => {
          const count = responses[request]?.get(name) || 0;
          this.allChartData[request].set(name, value + count);
          totalAll[request] += count;
        });
      }
    });

    for (let category = 0; category < this.allChartData.length; category++) {
      this.allChartData[category].forEach((value, name) => {
        this.allChartData[category].set(name, totalAll[category] > 0 ? value / totalAll[category] : 0);
      });
    }

    if (this.allChartOptions.length === 0) {
      this.generateCharts();
    } else {
      this.updateCharts();
    }
  }

  generateCharts(): void {
    this.ratingRequests.forEach(f => {
      if (!this.nonOptionQuestionIds.includes(f.id)) {
        let series: ApexAxisChartSeries = [{
          name: "All users",
          data: this.getChartData([], f.id),
        }];
        if (this.plottedUsernames.length > 0) {
          this.plottedUsernames.forEach(username => {
            series.push({
              name: username,
              data: this.getChartData([username], f.id)
            });
          });
        }
        this.allChartOptions?.push(
          {
            series: series,
            legend: {
              showForSingleSeries: true
            },
            colors: ['#0066ff', ...COLORS_BARS],
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
                  return val?.toFixed(1);
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
        );
      }
    });
  }

  updateCharts(): void {
    this.ratingRequests.forEach(f => {
      if (!this.nonOptionQuestionIds.includes(f.id)) {
        const chartOptions = this.allChartOptions[parseInt(f.id)];
        chartOptions.series = [{
          name: "All users",
          data: this.getChartData([], f.id),
          color: "#3954ea",
        }];

        for (let index = 0; index < this.plottedUsernames.length; index++) {
          let username = this.plottedUsernames[index];
          chartOptions.series.push({
            name: username,
            data: this.getChartData([username], f.id),
            color: COLORS_BARS[index % COLORS_BARS.length]
          });
        }
      }
    });
  }

  openImpression(content: any, impression: FeedbackResponse): void {
    this.impressionToRead = impression.value;
    this.modalService.open(content, {centered: true});
  }

  toggleDirection(value: string): void {
    // Unselect all usernames when changing the direction
    this.selectedUsernames = [];
    this.authorPerspective = value == "author";
    this.averageFeedback = [];
    this.fetchFeedback();
  }

  toggleAssignments(value: string): void {
    this.selectedUsernames = [];
    this.chooseAssignments = value == "assigned";
    this.averageFeedback = [];
    this.fetchFeedback();
  }

  toggleFormName(value: string): void {
    this.nonOptionQuestionIds = [];
    this.allChartOptions = [];
    this.selectedFormName = value;
    this.fetchFeedback();
  }

  home(): void {
    this.router.navigateByUrl('/panel').then();
  }

  ngOnDestroy() {
    this.feedbackSubscription.unsubscribe();
  }
}
