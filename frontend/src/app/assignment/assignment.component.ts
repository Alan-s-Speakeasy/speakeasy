import {Component, Inject, OnDestroy, OnInit} from '@angular/core';
import {Router} from "@angular/router";
import {Title} from "@angular/platform-browser";
import {CommonService} from "../common.service";
import {
  AdminService,
  AssignmentGeneratorObject,
  AssignmentService,
  ChatRoomAdminInfo, FeedbackRequest, FeedbackService,
  GeneratedAssignment,
} from "../../../openapi";
import {interval, Subscription} from "rxjs";
import {HttpClient} from '@angular/common/http';
import {UntypedFormControl} from "@angular/forms";
import {AlertService} from "../alert";
import {FeedbackForm, FrontendChatroomDetail} from "../new_data";


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
              @Inject(FeedbackService) private feedbackService: FeedbackService,
              @Inject(AssignmentService) private assignmentService: AssignmentService,
              public alertService: AlertService) {
  }

  private generatorSubscription!: Subscription;

  isActive = false
  evaluatorSelected = false
  assistantSelected = false

  isHumanSelected: Map<string, boolean> = new Map()
  isBotSelected: Map<string, boolean> = new Map()
  isAdminSelected: Map<string, boolean> = new Map()
  isEvaluatorSelected: Map<string, boolean> = new Map()
  isAssistantSelected: Map<string, boolean> = new Map()

  // Lists of users needed for assignements.
  // It is worth noting that thoses are set up in the storeGeneratorResponse method
  humans: string[] = []
  bots: string[] = []
  admins: string[] = []
  evaluator: string[] = []
  assistant: string[] = []

  active: string[] = []

  promptForm = new UntypedFormControl("")
  prompts: string[] = []

  formsMap: Map<string, FeedbackRequest[]> = new Map([
    ["", []]   // default value, no feedback form
  ])
  selectedFormName: string = ""

  botsPerUser = 3
  duration = 10

  // True if a change has been made after the generation
  changeAfterGenerate = false
  generated = false

  round = 0
  nextAssignment: GeneratedAssignment[] = []
  notOptimalAssignment = false

  remainingTime = 0
  roundTimer: any

  chatroomDetails: Map<string, FrontendChatroomDetail> = new Map()

  // Pagination stuff
  pageSizes: number[] = [5, 10, 15, 20];
  pageSizeHumanSelection = 10

  promptsError: string = "";
  botsValidationError: string = "";
  humansValidationError: string = "";
  botsPerUserConditionError : string = "";

  ngOnInit(): void {
    this.titleService.setTitle("Assignments")

    this.feedbackService.getApiFeedbackforms(undefined).subscribe((feedbackForms) => {
      feedbackForms.forms.forEach((form) => {
        this.formsMap.set(form.formName, form.requests)
      })
    })


    this.fetchGenerator(true)
    this.generatorSubscription = interval(10_000).subscribe(() => {
      this.fetchGenerator(false)
    })
    this.roundTimer = setInterval(() => {
      this.countdown()
    }, 1000)
  }

  /**
   * Fetches the generator from the backend. If it is the first time the generator is fetched, the response is used to store some data, such as
   * the selected users, the prompts, the bots per user, the duration, the selected form name, the active users,
   * the next assignment, the round, the chatroom details and the remaining time.
   * @param initial whether this is the first time the generator is fetched
   */
  fetchGenerator(initial: boolean) {
    this.assignmentService.getApiAssignment().subscribe(response => {
      this.storeGeneratorResponse(response, initial)
    })
  }

  newGenerator(): void {
    this.assignmentService.postApiAssignmentNew().subscribe(() => {
      this.fetchGenerator(true)
    })
  }

  /**
   * Store the response from the generator in the frontend
   * @param response the response from the generator
   * @param initial whether this is the first time the response is stored
   */
  storeGeneratorResponse(response: AssignmentGeneratorObject, initial: boolean): void {
    // Why is this check necessary?
    // Probably to avoid the case where there are no humans ?
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
      response.evaluator.forEach(evaluator => {
        if (!this.isEvaluatorSelected.get(evaluator)) {
          this.isEvaluatorSelected.set(evaluator, false)
        }
      })
      response.assistant.forEach(assistant => {
        if (!this.isAssistantSelected.get(assistant)) {
          // TODO Fix here
          this.isAssistantSelected.set(assistant, false)
        }
      })

      this.humans = Array.from(this.isHumanSelected.keys())
      this.bots = Array.from(this.isBotSelected.keys())
      this.admins = Array.from(this.isAdminSelected.keys())
      this.evaluator = Array.from(this.isEvaluatorSelected.keys())
      this.assistant = Array.from(this.isAssistantSelected.keys())
      this.active = response.active
      // Store the data only if it is the first time the generator is fetched
      if (initial) {
        this.prompts = response.prompts
        this.selectedFormName = response.formName
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
        response.evaluator.forEach(evaluator => {
          this.isEvaluatorSelected.set(evaluator, response.selected.evaluator.includes(evaluator))
        })
        response.assistant.forEach(assistant => {
          this.isAssistantSelected.set(assistant, response.selected.assistant.includes(assistant))
        })
      }
      this.nextAssignment = response.assignments
      this.round = response.round
      response.rooms.forEach(room => {
        if (!this.chatroomDetails.has(room.uid)) {
          this.pushChatRoomDetails(room)
        }
        let details = this.chatroomDetails.get(room.uid)
        if (details) {
          details.remainingTime = room.remainingTime
          this.chatroomDetails.set(room.uid, details)
        }
      })
      this.remainingTime = response.remainingTime
    }
  }

  removeGenerator(): void {
    this.assignmentService.deleteApiAssignment().subscribe(() => {
      this.isActive = false
      this.isHumanSelected = new Map()
      this.isBotSelected = new Map()
      this.isAdminSelected = new Map()
      this.isEvaluatorSelected = new Map()
      this.isAssistantSelected = new Map()
      this.active = []
      this.remainingTime = 0
      this.nextAssignment = []
      this.chatroomDetails.clear()
    })
  }

  areAllSelected(map: Map<string, boolean>): boolean {
    return !Array.from(map.values()).includes(false)
  }

  switchAll(type: string, event: any): void {
    if (type == "human") {
      this.isHumanSelected.forEach((v, k) => {
        let current = this.isHumanSelected.get(k)
        this.isHumanSelected.set(k, event.checked)

        // Prevent an admin being selected as both bot and human
        if (!current && this.admins.includes(k)) {
          this.isAdminSelected.set(k, false)
        }
      })
    } else if (type == "bot") {
      this.isBotSelected.forEach((v, k) => {
        this.isBotSelected.set(k, event.checked)
      })
    } else if (type == "admin") {
      this.isAdminSelected.forEach((v, k) => {
        let current = this.isAdminSelected.get(k)
        this.isAdminSelected.set(k, event.checked)

        // Prevent an admin being selected as both bot and human
        if (!current) {
          this.isHumanSelected.set(k, false)
        }
      })
    }
  }

  switchHuman(human: string): void {
    let current = this.isHumanSelected.get(human)
    this.isHumanSelected.set(human, !current)

    // Prevent an admin being selected as both bot and human
    if (!current && this.admins.includes(human)) {
      this.isAdminSelected.set(human, false)
    }
    this.changeAfterGenerate = true
  }

  switchBot(bot: string): void {
    let current = this.isBotSelected.get(bot)
    this.isBotSelected.set(bot, !current)
    this.changeAfterGenerate = true
  }

  switchEvaluator(): void {
    this.evaluatorSelected = !this.evaluatorSelected
  }

  switchAssistant(): void {
    this.assistantSelected = !this.assistantSelected
  }

  switchAdmin(admin: string): void {
    let current = this.isAdminSelected.get(admin)
    this.isAdminSelected.set(admin, !current)

    // Prevent an admin being selected as both bot and human
    if (!current) {
      this.isHumanSelected.set(admin, false)
    }
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
      if (prompt.trim()) {
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
    let humans = this.humans.filter(h => this.isHumanSelected.get(h))
    let bots = this.bots.filter(b => this.isBotSelected.get(b))
    let admins = this.admins.filter(a => this.isAdminSelected.get(a))
    let evaluator = this.evaluatorSelected
    let assistant = this.assistantSelected
    // Check each condition separately
    let humanConditionOk = (humans.length > 0 && !evaluator) || (!(humans.length > 0) && evaluator && !assistant);
    let botConditionOk = bots.length + admins.length > 0;
    let promptConditionOk = this.prompts.length > 0;
    let botsPerUserConditionOk = this.botsPerUser <= bots.length + admins.length;

    // Update validation error messages
    this.humansValidationError = humanConditionOk ? "" : "Please select at least one human or evaluator";
    this.botsValidationError = botConditionOk ? "" : "Please select at least one bot or admin";
    this.promptsError = promptConditionOk ? "" : "Please add at least one prompt";
    this.botsPerUserConditionError = botsPerUserConditionOk ? "" : "You can't have " + this.botsPerUser + " bots per human, please select more bots/admins";

    // Return whether all conditions are met
    return humanConditionOk && botConditionOk && promptConditionOk && botsPerUserConditionOk;
  }

  generateNextRound(): void {
    if (!this.canStartRound())
      return
    this.assignmentService.postApiAssignmentRound(this.evaluatorSelected.toString(), {
      humans: (this.humans.filter(h => this.isHumanSelected.get(h))),
      bots: this.bots.filter(b => this.isBotSelected.get(b)),
      admins: this.admins.filter(b => this.isAdminSelected.get(b)),
      prompts: this.prompts,
      botsPerHuman: this.botsPerUser,
      duration: this.duration,
      formName: this.selectedFormName
    }).subscribe(response => {
      let selectedHumans = this.humans.filter(h => this.isHumanSelected.get(h))
      this.nextAssignment = response
      this.notOptimalAssignment = this.nextAssignment.length / selectedHumans.length != this.botsPerUser
      this.generated = true
    }, error => {
      this.alertService.error("Next round could not be created.", this.options)
      this.nextAssignment = []
    })
    this.changeAfterGenerate = false
  }

  startNextRound(): void {
    this.assignmentService.patchApiAssignmentRound(this.assistantSelected.toString()).subscribe(() => {
      this.generated = false
      this.fetchGenerator(false)
    })
  }

  pushChatRoomDetails(chatRoom: ChatRoomAdminInfo) {
    this.chatroomDetails.set(chatRoom.uid, {
        assignment: chatRoom.assignment,
        formRef: chatRoom.formRef,
        prompt: chatRoom.prompt,
        roomID: chatRoom.uid,
        startTime: chatRoom.startTime!,
        remainingTime: chatRoom.remainingTime,
        userInfo: chatRoom.users,
        markAsNoFeedBack: chatRoom.markAsNoFeedback
      }
    )
  }

  getChatrooms(active: boolean): FrontendChatroomDetail[] {
    let arr = Array.from(this.chatroomDetails.values())
    return arr.filter(c => (c.remainingTime > 0) == active)
  }

  toggleFormName(value: string): void {
    this.selectedFormName = value
  }

  countdown(): void {
    if (this.remainingTime > 0) {
      this.remainingTime -= 1000
    } else {
      this.remainingTime = 0
    }
  }

  watch(chatroomDetail: FrontendChatroomDetail): void {
    let user1 = chatroomDetail.userInfo[0]
    let user2 = chatroomDetail.userInfo[1]
    this.router.navigateByUrl('/spectate', {
      state: {
        assignment: chatroomDetail.assignment,
        formRef: chatroomDetail.formRef,
        markAsNoFeedback: chatroomDetail.markAsNoFeedBack,
        roomID: chatroomDetail.roomID,
        username: user1.username,
        userAlias: user1.alias,
        partnerAlias: user2.username,
        backUrl: "assignment"
      }
    }).then()
  }

  home(): void {
    this.router.navigateByUrl('/panel').then()
  }

  ngOnDestroy() {
    this.generatorSubscription.unsubscribe()
    // todo: unsubscribe() more?
    clearInterval(this.roundTimer)
  }

}
