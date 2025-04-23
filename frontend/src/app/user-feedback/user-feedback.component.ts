import {Component, Inject, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {FrontendAverageFeedback, FrontendUserFeedback} from "../new_data";
import {Router} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {CommonService} from "../common.service";
import {
  AdminService,
  FeedbackRequest,
  FeedbackResponse,
  FeedbackService,
  FeedBackStatsOfRequest
} from "../../../openapi";
import {interval, Subscription} from "rxjs";
import {HttpClient} from '@angular/common/http';
import {AlertService} from "../alert";
import {NgbModal} from "@ng-bootstrap/ng-bootstrap";
import {
  ApexAxisChartSeries,
  ApexChart,
  ApexDataLabels,
  ApexFill,
  ApexLegend,
  ApexMarkers,
  ApexNonAxisChartSeries,
  ApexPlotOptions,
  ApexResponsive,
  ApexStroke,
  ApexTitleSubtitle,
  ApexXAxis,
  ApexYAxis,
  ChartComponent
} from "ng-apexcharts";
import {FrontendDataService} from "../frontend-data.service";

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

// Add this type definition with your other type definitions
export type RadarChartOptions = {
  series: ApexNonAxisChartSeries;
  chart: ApexChart;
  labels: string[];
  title: ApexTitleSubtitle;
  colors: any[];
  stroke: ApexStroke;
  markers: ApexMarkers;
  fill: ApexFill;
  responsive: ApexResponsive[];
  yaxis: ApexYAxis;
};

const COLORS_BARS = ['#ff4081', '#7b1fa2', '#00796b', '#ffc107', '#ff5722'];


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
              private modalService: NgbModal,
              private frontendDataService: FrontendDataService) {
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
  radarChartOptions: Partial<RadarChartOptions> | any = {};

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
  isRadarCollapsed: boolean = true;
  isIndividualChartsCollapsed : boolean = true;

  ngOnInit(): void {
    this.titleService.setTitle("Evaluation Feedback");

    // Restore selected users from localStorage if they exist
    const savedSelectedUsers = this.frontendDataService.getItem('selectedFeedbackUsers');
    if (savedSelectedUsers) {
      this.selectedUsernames = JSON.parse(savedSelectedUsers);
    }

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
                    const q_id = parseInt(resp.id);
                    // NOTE : Sometimes the question id is out of bounds (like it does not correspond to the question id in the form)
                    // this can happen when for example the question id start from 1 to N, when the table is from 0 to N-1.
                    // This is by design of the backend: it always return the raw CSV line containing the question id and the answer
                    // AS generateEmptyChartBucketsPerUsername() return a list, we map each indices to the question id
                    // But the problem arrises when the question id does not start from 0, is it therefore not an index.
                    // TODO : Change this, I think the whole file should be rewritten at this point to use more comprehensive data structures
                    if (q_id >= 0 && q_id < this.chartDataPerUsername.get(username)!.length) {
                      let current = this.chartDataPerUsername.get(username)![q_id].get(resp.value) || 0;
                      this.chartDataPerUsername.get(username)![q_id].set(resp.value, current + 1);
                    }
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
    this.feedbackService.getApiFeedbackaverageexportByFormName(this.selectedFormName, usernames_str, this.chooseAssignments,this.authorPerspective)
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

  getIndividualChartData(usernames: string[], id: string): number[] {
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
    // Save selected users to localStorage
    this.frontendDataService.setItem('selectedFeedbackUsers', JSON.stringify(this.selectedUsernames));
    this.applyFilter();
  }

  switchAll(): void {
    this.selectedUsernames = this.selectedUsernames.length == this.usernames.length ? [] : [...this.usernames];
    // Save selected users to localStorage
    this.frontendDataService.setItem('selectedFeedbackUsers', JSON.stringify(this.selectedUsernames));
    this.applyFilter();
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
    // Clear selected users from localStorage
    this.frontendDataService.removeItem('selectedFeedbackUsers');
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
      this.generateIndividualCharts();
      this.generateRadarChart()
    } else {
      this.updateCharts();
      this.updateRadarChart()
    }
  }

  generateIndividualCharts(): void {
    this.ratingRequests.forEach(f => {
      if (!this.nonOptionQuestionIds.includes(f.id)) {
        let series: ApexAxisChartSeries = [{
          name: "All users",
          data: this.getIndividualChartData([], f.id),
        }];
        if (this.plottedUsernames.length > 0) {
          this.plottedUsernames.forEach(username => {
            series.push({
              name: username,
              data: this.getIndividualChartData([username], f.id)
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
          data: this.getIndividualChartData([], f.id),
          color: "#3954ea",
        }];

        for (let index = 0; index < this.plottedUsernames.length; index++) {
          let username = this.plottedUsernames[index];
          chartOptions.series.push({
            name: username,
            data: this.getIndividualChartData([username], f.id),
            color: COLORS_BARS[index % COLORS_BARS.length]
          });
        }
      }
    });
  }

  generateRadarChart(): void {
    // Extract questions shortnames for radar chart labels
    const labels: string[] = this.ratingRequests
      .filter(f => !this.nonOptionQuestionIds.includes(f.id))
      .map(f => f.shortname);

    // Calculate average score for each question across all users
    const radarData: number[] = [];

    // This could typically use the backend to avoid recomputing this
    this.ratingRequests.forEach((request, idx) => {
      if (!this.nonOptionQuestionIds.includes(request.id)) {
        // Calculate weighted average for this question
        let totalWeight = 0;
        let weightedSum = 0;

        // Get all user data for this question
        const questionIndex = parseInt(request.id);
        this.allChartData[questionIndex].forEach((count, option) => {
          // Convert option (which is a string) to a numeric value
          const numericValue = parseFloat(option);
          weightedSum += numericValue * count;
          totalWeight += count;
        });

        // Calculate average and push to radar data
        const average = totalWeight > 0 ? weightedSum / totalWeight : 0;
        radarData.push(parseFloat(average.toFixed(2)));
      }
    });

    this.radarChartOptions = {
      series: [
        {
          name: "Global Average",
          data: radarData,
          color: "#f60a0a",
          stroke: {
            curve: "smooth"
          }
        }
      ],
      chart: {
        height: 600,
        type: "radar",
        toolbar: {
          show: true,
        }
      },
      stroke: {
        width: 2,
        dashArray: [4, 0]  // first series dashed (pattern length of 4), subsequent series solid
      },
      fill: {
        opacity: [0, 0.1,0.1, 0.1, 0.1, 0.1]
      },
      markers: {
        size: 2,
        hover: {
          size: 4
        }
      },
      labels: labels,
      yaxis: {
        min: -2,
        max: 2,
        tickAmount: 4
      }
    };
  }

  updateRadarChart(): void {
    if (!this.radarChartOptions) {
      return;
    }
    const radarData = this.plottedUsernames.map(username => this.averageFeedback.find(f => f.username === username)).filter(f => f !== undefined);
    this.radarChartOptions.series = [
      this.radarChartOptions.series[0],  // Keep the first item (Global Average)
      ...radarData.map((f, index) => ({
        name: f?.username,
        data: f?.responses.filter(resp => !this.nonOptionQuestionIds.includes(resp.requestID)).map(resp => parseFloat(resp.average)),
        color: COLORS_BARS[index % COLORS_BARS.length]
      }))
    ];
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
    if (this.feedbackSubscription) {
      this.feedbackSubscription.unsubscribe();
    }
  }
}
