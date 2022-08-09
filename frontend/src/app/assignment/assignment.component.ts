import {Component, Inject, OnDestroy, OnInit} from '@angular/core';
import {Router} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {CommonService} from "../common.service";
import {
  AdminService, AssignmentGeneratorObject, AssignmentService,
} from "../../../openapi";
import {interval, Subscription} from "rxjs";
import { HttpClient } from '@angular/common/http';
import {FormControl} from "@angular/forms";
import {NgbModal} from "@ng-bootstrap/ng-bootstrap";
import {AlertService} from "../_alert";


@Component({
  selector: 'app-assignment',
  templateUrl: './assignment.component.html',
  styleUrls: ['./assignment.component.css']
})
export class AssignmentComponent implements OnInit, OnDestroy {
  options = {
    autoClose: true,
    keepAfterRouteChange: true
  };
  constructor(private router: Router, private titleService: Title,
              private httpClient: HttpClient,
              private commonService: CommonService,
              @Inject(AdminService) private adminService: AdminService,
              @Inject(AssignmentService) private assignmentService: AssignmentService,
              public alertService: AlertService,
              private modalService: NgbModal) { }

  private generatorSubscription!: Subscription;

  isActive = false

  isHumanSelected: Map<string, boolean> = new Map()
  isBotSelected: Map<string, boolean> = new Map()
  humans: string[] = []
  bots: string[] = []
  active: string[] = []

  promptForm = new FormControl("")
  prompts: string[] = []

  botsPerUser = 3
  duration = 10

  timeLeft = 0
  timeLeftFormatted = "--:--"
  roundTimer: any

  ngOnInit(): void {
    this.titleService.setTitle("User Details")

    this.fetchGenerator()
    this.generatorSubscription = interval(10000).subscribe((number) => {
      this.fetchGenerator()
    })
    this.roundTimer = setInterval(() => {this.countdown()}, 1000)
  }

  fetchGenerator() {
    this.assignmentService.getAssignmentGenerator().subscribe(response => {
      this.storeGeneratorResponse(response)
    })
  }

  newGenerator(): void {
    this.assignmentService.createNewAssignmentGenerator().subscribe(response => {
      this.storeGeneratorResponse(response)
    })
  }

  storeGeneratorResponse(response: AssignmentGeneratorObject): void {
    if (response.humans.length > 0) {
      this.isActive = true
      response.humans.forEach(human => {
        if (!this.isHumanSelected.get(human)) {
          this.isHumanSelected.set(human, false)
        }
      })
      response.bots.forEach(bot => {
        if (!this.isBotSelected.get(bot)) {
          this.isBotSelected.set(bot, false)
        }
      })
      this.humans = Array.from(this.isHumanSelected.keys())
      this.bots = Array.from(this.isBotSelected.keys())
      this.active = response.active
    }
  }

  removeGenerator(): void {
    this.assignmentService.deleteAssignmentGenerator().subscribe(response => {
      this.isActive = false
    })
  }

  switchHuman(human: string): void {
    let current = this.isHumanSelected.get(human)
    this.isHumanSelected.set(human, !current)
  }

  switchBot(bot: string): void {
    let current = this.isBotSelected.get(bot)
    this.isBotSelected.set(bot, !current)
  }

  setBotsPerUser(event: any): void {
    this.botsPerUser = event.value
  }

  setDuration(event: any): void {
    this.duration = event.value
  }

  next(): void {
    this.prompts = []
    let fieldContent: string = this.promptForm.value
    fieldContent.split("\n").forEach(prompt => {
      if (prompt != "") {
        this.prompts.push(prompt)
      }
    })

    this.assignmentService.startNewAssignmentRound({
      humans: Object.keys(this.humans).filter(h => this.isHumanSelected.get(h)),
      bots: Object.keys(this.bots).filter(h => this.isBotSelected.get(h)),
      prompts: this.prompts,
      botsPerHuman: this.botsPerUser,
      duration: this.duration
    }).subscribe(response => {
      this.timeLeft = Math.floor(response.remainingTime / 1000)
      console.log(this.timeLeft)
    })
  }

  countdown(): void {
    if (this.timeLeft > 0) {
      this.timeLeft -= 1
      const minutes = Math.floor(this.timeLeft / 60)
      const seconds = this.timeLeft % 60
      this.timeLeftFormatted = `${minutes < 10 ? '0' : ''}${minutes}:${seconds < 10 ? '0' : ''}${seconds}`
    }
  }

  home(): void {
    this.router.navigateByUrl('/panel').then()
  }

  ngOnDestroy() {
    this.generatorSubscription.unsubscribe()
    clearInterval(this.roundTimer)
  }

}
