import {Component, Inject, OnDestroy, OnInit} from '@angular/core';
import {Router} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {CommonService} from "../common.service";
import {
  AdminService, AssignmentGeneratorObject, AssignmentService, ChatRoomInfo, GeneratedAssignment,
} from "../../../openapi";
import {interval, Subscription} from "rxjs";
import { HttpClient } from '@angular/common/http';
import {FormControl} from "@angular/forms";
import {AlertService} from "../_alert";
import {FrontendChatroomDetail} from "../new_data";


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
              public alertService: AlertService) { }

  private generatorSubscription!: Subscription;

  isActive = false

  isHumanSelected: Map<string, boolean> = new Map()
  isBotSelected: Map<string, boolean> = new Map()
  isAdminSelected: Map<string, boolean> = new Map()

  humans: string[] = []
  bots: string[] = []
  admins: string[] = []

  active: string[] = []

  promptForm = new FormControl("")
  prompts: string[] = []

  botsPerUser = 3
  duration = 10

  changeAfterGenerate = false

  round = 0
  nextAssignment: GeneratedAssignment[] = []
  notOptimalAssignment = false

  remainingTime = 0
  timeLeftFormatted = "--:--"
  roundTimer: any

  activeChatroomDetails: FrontendChatroomDetail[] = []
  activeRound: any

  ngOnInit(): void {
    this.titleService.setTitle("User Details")

    this.fetchGenerator(true)
    this.generatorSubscription = interval(10000).subscribe(() => {
      this.fetchGenerator(false)
    })
    this.roundTimer = setInterval(() => {this.countdown()}, 1000)
  }

  fetchGenerator(initial: boolean) {
    this.assignmentService.getAssignmentGenerator().subscribe(response => {
      this.storeGeneratorResponse(response, initial)
    })
  }

  newGenerator(): void {
    this.assignmentService.createNewAssignmentGenerator().subscribe(() => {
      this.fetchGenerator(true)
    })
  }

  storeGeneratorResponse(response: AssignmentGeneratorObject, initial: boolean): void {
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
      response.admins.forEach(admin => {
        if (!this.isAdminSelected.get(admin)) {
          this.isAdminSelected.set(admin, false)
        }
      })

      this.humans = Array.from(this.isHumanSelected.keys())
      this.bots = Array.from(this.isBotSelected.keys())
      this.admins = Array.from(this.isAdminSelected.keys())
      this.active = response.active
      if (initial) {
        this.prompts = response.prompts
        this.botsPerUser = response.botsPerHuman
        this.duration = response.duration
        response.humans.forEach(human => {
          this.isHumanSelected.set(human, response.selected.humans.includes(human))
        })
        response.bots.forEach(bot => {
          this.isBotSelected.set(bot, response.selected.bots.includes(bot))
        })
        response.admins.forEach(admin => {
          this.isAdminSelected.set(admin, response.selected.admins.includes(admin))
        })
      }
      this.nextAssignment = response.assignments
      this.round = response.round
      this.remainingTime = Math.floor(response.remainingTime / 1000)
    }
  }

  removeGenerator(): void {
    this.assignmentService.deleteAssignmentGenerator().subscribe(() => {
      this.isActive = false
      this.isHumanSelected = new Map()
      this.isBotSelected = new Map()
      this.isAdminSelected = new Map()
      this.active = []
      this.remainingTime = 0
      this.nextAssignment = []
    })
  }

  areAllSelected(map: Map<string, boolean>): boolean {
    return !Array.from(map.values()).includes(false)
  }

  switchAll(type: string, event: any): void {
    if (type == "human") {
      this.isHumanSelected.forEach((v, k) => {
        this.isHumanSelected.set(k, event.checked)
        return
      })
    } else if (type == "bot") {
      this.isBotSelected.forEach((v, k) => {
        this.isBotSelected.set(k, event.checked)
        return
      })
    } else if (type == "admin") {
      this.isAdminSelected.forEach((v, k) => {
        this.isAdminSelected.set(k, event.checked)
      })
    }
  }

  switchHuman(human: string): void {
    let current = this.isHumanSelected.get(human)
    this.isHumanSelected.set(human, !current)
    this.changeAfterGenerate = true
  }

  switchBot(bot: string): void {
    let current = this.isBotSelected.get(bot)
    this.isBotSelected.set(bot, !current)
    this.changeAfterGenerate = true
  }

  switchAdmin(admin: string): void {
    let current = this.isAdminSelected.get(admin)
    this.isAdminSelected.set(admin, !current)
    this.changeAfterGenerate = true
  }

  setBotsPerUser(event: any): void {
    this.botsPerUser = event.value
    this.changeAfterGenerate = true
  }

  setDuration(event: any): void {
    this.duration = event.value
    this.changeAfterGenerate = true
  }

  addPrompts(): void {
    let fieldContent: string = this.promptForm.value
    fieldContent.split("\n").forEach(prompt => {
      if (prompt != "") {
        this.prompts.push(prompt)
      }
    })
    this.promptForm.setValue("")
    this.changeAfterGenerate = true
  }

  removePrompt(index: number): void {
    this.prompts.splice(index, 1)
    this.changeAfterGenerate = true
  }

  canStartRound(): boolean {
    return this.humans.filter(h => this.isHumanSelected.get(h)).length > 0 &&
      (this.bots.filter(b => this.isBotSelected.get(b)).length > 0 ||
        this.admins.filter(a => this.isAdminSelected.get(a)).length > 0) &&
      this.prompts.length > 0
  }

  generateNextRound(): void {
    this.assignmentService.generateAssignmentRound({
      humans: this.humans.filter(h => this.isHumanSelected.get(h)),
      bots: this.bots.filter(b => this.isBotSelected.get(b)),
      admins: this.admins.filter(b => this.isAdminSelected.get(b)),
      prompts: this.prompts,
      botsPerHuman: this.botsPerUser,
      duration: this.duration
    }).subscribe(response => {
      let selectedHumans = this.humans.filter(h => this.isHumanSelected.get(h))
      this.nextAssignment = response
      this.notOptimalAssignment = this.nextAssignment.length / selectedHumans.length != this.botsPerUser
    }, error => {
      this.alertService.error("Next round could not be created.", this.options)
      this.nextAssignment = []
    })
    this.changeAfterGenerate = false
  }

  startNextRound(): void {
    this.assignmentService.startAssignmentRound().subscribe(response => {
      this.remainingTime = Math.floor(response.remainingTime / 1000)
      this.roundTimer = setInterval(() => {this.countdown()}, 1000)
    })
  }

  fetchActiveRound(): void {
    this.adminService.getApiRoomsActive().subscribe((activechatrooms)=>{
      this.activeChatroomDetails = []
      activechatrooms.rooms.forEach(room => {
        this.pushChatRoomDetails(this.activeChatroomDetails, room)
      })
    })
  }

  countdown(): void {
    if (this.remainingTime > 0) {
      this.remainingTime -= 1
      const minutes = Math.floor(this.remainingTime / 60)
      const seconds = this.remainingTime % 60
      this.timeLeftFormatted = `${minutes < 10 ? '0' : ''}${minutes}:${seconds < 10 ? '0' : ''}${seconds}`
      this.fetchActiveRound()
    } else {
      clearInterval(this.roundTimer)
      clearInterval(this.activeRound)
    }
  }

  pushChatRoomDetails(chatRoomDetails: FrontendChatroomDetail[], chatRoom: ChatRoomInfo) {
    let users :string[] = []
    chatRoom.users.forEach(u => users.push(u.username))

    let aliases :string[] = []
    chatRoom.users.forEach(u => aliases.push(u.alias))

    let sessions: string[] = []
    chatRoom.users.forEach(u => {u.sessions.forEach(s => sessions.push(s))})

    chatRoomDetails.push(
      {
        prompt: chatRoom.prompt,
        roomID: chatRoom.uid,
        startTime: chatRoom.startTime!,
        remainingTime: chatRoom.remainingTime,
        users: users,
        aliases: aliases,
        sessions: sessions,
      }
    )
  }

  watch(chatroomDetail: FrontendChatroomDetail): void {
    this.router.navigateByUrl('/spectate', { state: {
        roomID: chatroomDetail.roomID,
        username: chatroomDetail.users[0],
        userAlias: chatroomDetail.aliases[0],
        partnerAlias: chatroomDetail.users[1],
        userSession: chatroomDetail.sessions[0],
        users: chatroomDetail.users,
        backUrl: "assignment"
      } } ).then()
  }

  home(): void {
    this.router.navigateByUrl('/panel').then()
  }

  ngOnDestroy() {
    this.generatorSubscription.unsubscribe()
    clearInterval(this.roundTimer)
    clearInterval(this.activeRound)
  }

}
