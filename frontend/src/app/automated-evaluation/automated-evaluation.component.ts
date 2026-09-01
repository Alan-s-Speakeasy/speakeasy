import {HttpClient, HttpErrorResponse} from '@angular/common/http';
import {ChangeDetectorRef, Component, Inject, OnDestroy, OnInit, QueryList, TemplateRef, ViewChild, ViewChildren} from '@angular/core';
import {UntypedFormControl} from '@angular/forms';
import {Title} from '@angular/platform-browser';
import {Router} from '@angular/router';
import {NgbModal, NgbPopover} from '@ng-bootstrap/ng-bootstrap';
import {filter, map, take, timeout} from 'rxjs/operators';
import {AdminService, ChatRequest, ChatRoomInfo, ChatService} from '../../../openapi';
import {AlertService} from '../alert';
import {AuthService} from '../authentication.service';
import {CommonService} from '../common.service';
import {PaneLog} from '../new_data';
import {ChatPaneComponent} from '../chat-pane/chat-pane.component';
import {AnswerVerdict, EvaluationQuestion, expectedAnswersLabel, parseQuestionsFile, scoreAnswer} from './questions-parser';
import {createSummaryExcelBlob} from './excel-summary';
import {
  copyGradingSettings,
  DEFAULT_GRADING_SETTINGS,
  formatBonusPoints,
  GradingSettings,
  sanitizeGradingSettings,
  speedBonusPoints,
  speedBonusRulesText
} from './grading-settings';
import {createZipBlob} from './zip-store';
import {EmailRecipientMap, parseEmailRecipients, recipientsFor} from './email-recipients';

interface EmailSendResult {
  username: string
  email: string
  sent: boolean
  error?: string | null
}

interface EmailSendResponse {
  sent: number
  failed: number
  results: EmailSendResult[]
}

interface EmailStatus {
  configured: boolean
  host?: string | null
  from?: string | null
}

interface BotCredential {
  username: string
  password: string
}

interface CredentialCheckResult {
  username: string
  ok: boolean
  reason?: string | null
}

interface EvalChat {
  username: string
  password: string
  paneLog: PaneLog
  lastVerdict: 'correct' | 'partial' | 'incorrect' | 'timeout' | null
  lastResponseMs: number | null
  correctOrdinals: number[]
}

interface EvaluationResult {
  username: string
  roomId: string
  questionIndex: number
  question: string
  expected: string
  answer: string
  correct: boolean
  partial: boolean
  timedOut: boolean
  responseTimeMs: number | null
}

interface ChatRanking {
  rank: number
  username: string
  roomId: string
  correct: number
  incorrect: number
  partial: number
  timeouts: number
  total: number
  accuracy: number
  grade: number
  avgResponseTimeMs: number | null
  totalResponseTimeMs: number
}

interface QuestionScore {
  verdict: 'correct' | 'partial' | 'incorrect' | 'timeout' | null
  timeMs: number | null
  fastBonusPoints: number
}

interface ChatScoreRow {
  username: string
  roomId: string
  answers: QuestionScore[]
  avgMs: number | null
  fastestMs: number | null
  fastestQuestion: number | null
  longestMs: number | null
  longestQuestion: number | null
  grade: number
}

interface EvaluationLogEntry {
  time: Date
  level: 'info' | 'success' | 'warning' | 'danger'
  message: string
  username?: string
  roomId?: string
}

@Component({
  selector: 'app-automated-evaluation',
  templateUrl: './automated-evaluation.component.html',
  styleUrls: ['./automated-evaluation.component.css']
})
export class AutomatedEvaluationComponent implements OnInit, OnDestroy {
  options = {
    autoClose: true,
    keepAfterRouteChange: true
  };

  @ViewChild('resultModal') resultModal!: TemplateRef<unknown>;
  @ViewChild('questionsModal') questionsModal!: TemplateRef<unknown>;
  @ViewChild('settingsModal') settingsModal!: TemplateRef<unknown>;
  @ViewChildren(ChatPaneComponent) chatPanes!: QueryList<ChatPaneComponent>;

  credentialsInput = new UntypedFormControl('');
  credentialSummary = '';
  credentialIssueGroups: {title: string, usernames: string[]}[] = [];
  chats: EvalChat[] = [];
  questions: EvaluationQuestion[] = [];
  questionsFileName = '';
  recipientsFileName = '';
  emailRecipients: EmailRecipientMap = {};
  emailConfigured: boolean | null = null;
  sendingMail = false;
  logsSentConfirm = false;
  grading: GradingSettings = copyGradingSettings(DEFAULT_GRADING_SETTINGS);
  gradingDraft: GradingSettings = copyGradingSettings(DEFAULT_GRADING_SETTINGS);
  results: EvaluationResult[] = [];
  logs: EvaluationLogEntry[] = [];

  startingChats = false;
  askingQuestions = false;
  downloadingChats = false;
  downloadingSummary = false;
  finishing = false;
  currentQuestionLabel = '';
  selectedRoomID = '';
  selectedLogChat = '';

  private stopAsking = false;
  private sessionId = 0;
  private readonly roomTimeoutMs = 15000;
  private recipientsHelpPopover: NgbPopover | null = null;
  private recipientsHelpCloseTimer: ReturnType<typeof setTimeout> | null = null;
  private recipientsHelpWindowEl: HTMLElement | null = null;
  private recipientsHelpEnter: (() => void) | null = null;
  private recipientsHelpLeave: (() => void) | null = null;
  private logsSentTimer: ReturnType<typeof setTimeout> | null = null;

  get answerTimeoutMs(): number {
    return Math.max(1, this.grading.timeoutSeconds) * 1000;
  }

  constructor(
    private router: Router,
    private titleService: Title,
    private http: HttpClient,
    private authService: AuthService,
    @Inject(CommonService) private commonService: CommonService,
    @Inject(ChatService) private chatService: ChatService,
    @Inject(AdminService) private adminService: AdminService,
    public alertService: AlertService,
    private modalService: NgbModal,
    private changeDetector: ChangeDetectorRef
  ) {
  }

  ngOnInit(): void {
    this.titleService.setTitle('Automated Evaluation');
    this.authService.userSessionDetails.subscribe((response) => {
      if (response == null) {
        this.router.navigateByUrl('/login').then(() => this.alertService.error('You are not logged in!'));
        return;
      }
      if (response.userDetails.role !== 'ADMIN') {
        this.router.navigateByUrl('/panel').then(() => this.alertService.error('Automated evaluation is only available to admins.'));
      }
    });

    this.commonService.openSseAndListenRooms(false);
    this.refreshEmailStatus();
  }

  get scoringRulesText(): string {
    return `Correct ${this.grading.correctPoints}, partial ${this.grading.partialPoints}. Speed: ${speedBonusRulesText(this.grading)}. Timeout ${this.grading.timeoutSeconds}s.`;
  }

  get canEditSettings(): boolean {
    return !this.askingQuestions;
  }

  openSettings(): void {
    this.gradingDraft = copyGradingSettings(this.grading);
    this.modalService.open(this.settingsModal, {centered: true, size: 'lg'});
  }

  saveSettings(modal: {close: () => void}): void {
    this.grading = sanitizeGradingSettings(this.gradingDraft);
    this.log('info', `Grading settings saved. ${this.scoringRulesText}`);
    this.alertService.success('Grading settings saved.', this.options);
    modal.close();
  }

  resetSettingsDraft(): void {
    this.gradingDraft = copyGradingSettings(DEFAULT_GRADING_SETTINGS);
  }

  resetEvaluation(): void {
    this.sessionId += 1;
    this.stopAsking = true;
    this.modalService.dismissAll();
    this.chats.filter(chat => chat.paneLog.active).forEach(chat => {
      this.chatService.patchApiRoomByRoomId(chat.paneLog.roomID, undefined).toPromise().catch(() => undefined);
      this.markEvalChatClosed(chat);
    });
    this.credentialsInput.setValue('');
    this.credentialSummary = '';
    this.credentialIssueGroups = [];
    this.chats = [];
    this.questions = [];
    this.questionsFileName = '';
    this.recipientsFileName = '';
    this.emailRecipients = {};
    this.grading = copyGradingSettings(DEFAULT_GRADING_SETTINGS);
    this.gradingDraft = copyGradingSettings(DEFAULT_GRADING_SETTINGS);
    this.results = [];
    this.logs = [];
    this.startingChats = false;
    this.askingQuestions = false;
    this.downloadingChats = false;
    this.downloadingSummary = false;
    this.finishing = false;
    this.sendingMail = false;
    this.logsSentConfirm = false;
    this.clearLogsSentTimer();
    this.cancelRecipientsHelpClose();
    this.unbindRecipientsHelpWindow();
    this.currentQuestionLabel = '';
    this.selectedRoomID = '';
    this.selectedLogChat = '';
    this.alertService.success('Evaluation reset.', this.options);
    this.changeDetector.detectChanges();
  }

  get canStartChats(): boolean {
    return !this.startingChats && !this.askingQuestions && !!this.credentialsInput.value;
  }

  get canAskQuestions(): boolean {
    return !this.startingChats && !this.askingQuestions && this.chats.length > 0 && this.questions.length > 0 && this.hasActiveChats;
  }

  get canFinish(): boolean {
    return !this.startingChats && (this.chats.length > 0 || this.results.length > 0);
  }

  get canSeeQuestions(): boolean {
    return this.questions.length > 0;
  }

  get canDownloadChats(): boolean {
    return !this.downloadingChats && this.chats.length > 0;
  }

  get canDownloadSummary(): boolean {
    return !this.downloadingSummary && this.chatScoreRows.length > 0;
  }

  get recipientCount(): number {
    return Object.keys(this.emailRecipients).length;
  }

  get recipientRows(): {username: string, emails: string[]}[] {
    return Object.keys(this.emailRecipients)
      .sort((a, b) => a.localeCompare(b))
      .map(username => ({username, emails: this.emailRecipients[username]}));
  }

  get canSendLogs(): boolean {
    return !this.sendingMail && !this.logsSentConfirm && this.chats.length > 0 && this.recipientCount > 0;
  }

  openRecipientsHelp(popover: NgbPopover): void {
    this.recipientsHelpPopover = popover;
    this.cancelRecipientsHelpClose();
    if (!popover.isOpen()) {
      popover.open();
    }
  }

  scheduleCloseRecipientsHelp(): void {
    this.cancelRecipientsHelpClose();
    this.recipientsHelpCloseTimer = setTimeout(() => {
      this.recipientsHelpPopover?.close();
    }, 250);
  }

  bindRecipientsHelpWindow(): void {
    this.unbindRecipientsHelpWindow();
    const el = document.querySelector('ngb-popover-window.eval-hover-stay') as HTMLElement | null;
    if (!el) {
      return;
    }
    this.recipientsHelpWindowEl = el;
    this.recipientsHelpEnter = () => this.cancelRecipientsHelpClose();
    this.recipientsHelpLeave = () => this.scheduleCloseRecipientsHelp();
    el.addEventListener('mouseenter', this.recipientsHelpEnter);
    el.addEventListener('mouseleave', this.recipientsHelpLeave);
  }

  unbindRecipientsHelpWindow(): void {
    if (this.recipientsHelpWindowEl && this.recipientsHelpEnter && this.recipientsHelpLeave) {
      this.recipientsHelpWindowEl.removeEventListener('mouseenter', this.recipientsHelpEnter);
      this.recipientsHelpWindowEl.removeEventListener('mouseleave', this.recipientsHelpLeave);
    }
    this.recipientsHelpWindowEl = null;
    this.recipientsHelpEnter = null;
    this.recipientsHelpLeave = null;
  }

  get finishedChatCount(): number {
    return this.chats.filter(chat => this.isChatFinished(chat)).length;
  }

  get remainingChatCount(): number {
    return Math.max(0, this.chats.length - this.finishedChatCount);
  }

  get filteredLogs(): EvaluationLogEntry[] {
    const roomId = this.activeLogRoomId;
    if (!roomId) {
      return [];
    }
    const chat = this.chats.find(item => item.paneLog.roomID === roomId);
    return this.logs.filter(entry =>
      entry.roomId === roomId ||
      (!entry.roomId && !!chat && entry.username === chat.username)
    );
  }

  get activeLogRoomId(): string {
    if (this.chats.some(chat => chat.paneLog.roomID === this.selectedLogChat)) {
      return this.selectedLogChat;
    }
    return this.chats[0]?.paneLog.roomID || '';
  }

  selectLogChat(roomId: string): void {
    this.selectedLogChat = roomId;
  }

  get chatScoreRows(): ChatScoreRow[] {
    return this.chats.map(chat => {
      const chatResults = this.results.filter(r => r.roomId === chat.paneLog.roomID);
      const byQuestion = new Map(chatResults.map(result => [result.questionIndex, result]));
      const timed = chatResults.filter(result => !result.timedOut && result.responseTimeMs != null);
      const fastest = timed.reduce<EvaluationResult | null>((best, result) => {
        if (!best || (result.responseTimeMs as number) < (best.responseTimeMs as number)) {
          return result;
        }
        return best;
      }, null);
      const longest = timed.reduce<EvaluationResult | null>((best, result) => {
        if (!best || (result.responseTimeMs as number) > (best.responseTimeMs as number)) {
          return result;
        }
        return best;
      }, null);

      return {
        username: chat.username,
        roomId: chat.paneLog.roomID,
        answers: this.questions.map(question => {
          const result = byQuestion.get(question.index);
          if (!result) {
            return {verdict: null, timeMs: null, fastBonusPoints: 0};
          }
          let verdict: QuestionScore['verdict'] = 'incorrect';
          if (result.timedOut) {
            verdict = 'timeout';
          } else if (result.correct) {
            verdict = 'correct';
          } else if (result.partial) {
            verdict = 'partial';
          }
          return {
            verdict,
            timeMs: result.responseTimeMs,
            fastBonusPoints: this.bonusForResult(result)
          };
        }),
        avgMs: this.averageTime(chatResults),
        fastestMs: fastest?.responseTimeMs ?? null,
        fastestQuestion: fastest ? fastest.questionIndex + 1 : null,
        longestMs: longest?.responseTimeMs ?? null,
        longestQuestion: longest ? longest.questionIndex + 1 : null,
        grade: this.gradeForResults(chatResults)
      };
    });
  }

  get hasActiveChats(): boolean {
    return this.chats.some(chat => chat.paneLog.active);
  }

  get totalAsked(): number {
    return this.results.length;
  }

  get totalCorrect(): number {
    return this.results.filter(r => r.correct).length;
  }

  get totalIncorrect(): number {
    return this.results.filter(r => !r.correct && !r.partial && !r.timedOut).length;
  }

  get totalPartial(): number {
    return this.results.filter(r => r.partial).length;
  }

  get totalTimeouts(): number {
    return this.results.filter(r => r.timedOut).length;
  }

  get accuracyPercent(): number {
    if (this.totalAsked === 0) {
      return 0;
    }
    return Math.round((this.totalCorrect / this.totalAsked) * 100);
  }

  get overallAvgResponseTimeMs(): number | null {
    return this.averageTime(this.results);
  }

  get rankedChats(): ChatRanking[] {
    const rows: ChatRanking[] = this.chats.map(chat => {
      const chatResults = this.results.filter(r => r.roomId === chat.paneLog.roomID);
      const answeredTimes = chatResults
        .filter(r => !r.timedOut && r.responseTimeMs != null)
        .map(r => r.responseTimeMs as number);
      const correct = chatResults.filter(r => r.correct).length;
      const partial = chatResults.filter(r => r.partial).length;
      const timeouts = chatResults.filter(r => r.timedOut).length;
      const total = chatResults.length;
      return {
        rank: 0,
        username: chat.username,
        roomId: chat.paneLog.roomID,
        correct,
        partial,
        incorrect: chatResults.filter(r => !r.correct && !r.partial && !r.timedOut).length,
        timeouts,
        total,
        accuracy: total === 0 ? 0 : Math.round((correct / total) * 100),
        grade: this.gradeForResults(chatResults),
        avgResponseTimeMs: answeredTimes.length === 0
          ? null
          : answeredTimes.reduce((sum, time) => sum + time, 0) / answeredTimes.length,
        totalResponseTimeMs: answeredTimes.reduce((sum, time) => sum + time, 0)
      };
    });

    rows.sort((a, b) => {
      if (b.grade !== a.grade) {
        return b.grade - a.grade;
      }
      if (b.correct !== a.correct) {
        return b.correct - a.correct;
      }
      if (a.timeouts !== b.timeouts) {
        return a.timeouts - b.timeouts;
      }
      const aTime = a.avgResponseTimeMs ?? Number.POSITIVE_INFINITY;
      const bTime = b.avgResponseTimeMs ?? Number.POSITIVE_INFINITY;
      if (aTime !== bTime) {
        return aTime - bTime;
      }
      return a.username.localeCompare(b.username);
    });

    let previousKey = '';
    let previousRank = 0;
    rows.forEach((row, index) => {
      const key = `${row.grade}|${row.correct}|${row.timeouts}|${row.avgResponseTimeMs ?? 'na'}`;
      if (key === previousKey) {
        row.rank = previousRank;
      } else {
        row.rank = index + 1;
        previousRank = row.rank;
        previousKey = key;
      }
    });
    return rows;
  }

  formatResponseTime(ms: number | null | undefined): string {
    if (ms == null) {
      return '—';
    }
    return `${(ms / 1000).toFixed(3)}s`;
  }

  formatGrade(grade: number | null | undefined): string {
    if (grade == null) {
      return '—';
    }
    return grade.toFixed(1);
  }

  gradeFor(chat: EvalChat): number {
    return this.gradeForResults(this.results.filter(result => result.roomId === chat.paneLog.roomID));
  }

  rankFor(chat: EvalChat): number | null {
    const row = this.rankedChats.find(stat => stat.roomId === chat.paneLog.roomID);
    return row && row.total > 0 ? row.rank : null;
  }

  isChatFinished(chat: EvalChat): boolean {
    if (this.questions.length === 0) {
      return false;
    }
    const lastIndex = this.questions[this.questions.length - 1].index;
    return this.results.some(result => result.roomId === chat.paneLog.roomID && result.questionIndex === lastIndex);
  }

  scoreRatioFor(chat: EvalChat): string | null {
    const chatResults = this.results.filter(result => result.roomId === chat.paneLog.roomID);
    if (chatResults.length === 0) {
      return null;
    }
    const correct = chatResults.filter(result => result.correct).length;
    return `${correct}/${chatResults.length}`;
  }

  importQuestionsFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files && input.files[0];
    input.value = '';
    if (!file) {
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const parsed = parseQuestionsFile(String(reader.result || ''));
        this.questions = parsed;
        this.questionsFileName = file.name;
        this.log('success', `Loaded ${parsed.length} question${parsed.length === 1 ? '' : 's'} from ${file.name}.`);
        this.alertService.success(`Loaded ${parsed.length} question${parsed.length === 1 ? '' : 's'} from ${file.name}.`, this.options);
        this.changeDetector.detectChanges();
      } catch (error: unknown) {
        this.alertService.error(this.errorMessage(error, 'Could not parse the questions file.'), this.options);
      }
    };
    reader.onerror = () => {
      this.alertService.error('Could not read the questions file.', this.options);
    };
    reader.readAsText(file);
  }

  clearQuestions(): void {
    if (this.askingQuestions) {
      return;
    }
    const name = this.questionsFileName;
    this.questions = [];
    this.questionsFileName = '';
    this.log('info', name ? `Removed questions file ${name}.` : 'Removed loaded questions.');
    this.changeDetector.detectChanges();
  }

  importRecipientsFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files && input.files[0];
    input.value = '';
    if (!file) {
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const parsed = parseEmailRecipients(String(reader.result || ''));
        this.emailRecipients = parsed.mapping;
        this.recipientsFileName = file.name;
        const chats = this.recipientCount;
        const addresses = Object.values(parsed.mapping).reduce((sum, emails) => sum + emails.length, 0);
        this.log('success', `Loaded recipients for ${chats} chat${chats === 1 ? '' : 's'} (${addresses} address${addresses === 1 ? '' : 'es'}) from ${file.name}.`);
        if (parsed.invalidEmails.length > 0) {
          this.alertService.warn(`Loaded ${chats} chat${chats === 1 ? '' : 's'}. Skipped invalid address${parsed.invalidEmails.length === 1 ? '' : 'es'}: ${parsed.invalidEmails.join(', ')}.`, {
            ...this.options,
            timeout: 12000
          });
        } else {
          this.alertService.success(`Loaded recipients for ${chats} chat${chats === 1 ? '' : 's'}.`, this.options);
        }
        this.changeDetector.detectChanges();
      } catch (error: unknown) {
        this.alertService.error(this.errorMessage(error, 'Could not parse the recipients file.'), this.options);
      }
    };
    reader.onerror = () => {
      this.alertService.error('Could not read the recipients file.', this.options);
    };
    reader.readAsText(file);
  }

  clearRecipients(): void {
    const name = this.recipientsFileName;
    this.emailRecipients = {};
    this.recipientsFileName = '';
    this.log('info', name ? `Removed recipients file ${name}.` : 'Removed loaded recipients.');
    this.changeDetector.detectChanges();
  }

  sendLogs(): void {
    if (!this.canSendLogs) {
      return;
    }

    const transcripts = this.chats
      .map(chat => ({
        username: chat.username,
        content: this.buildChatExport(chat),
        recipients: recipientsFor(this.emailRecipients, chat.username)
      }))
      .filter(item => item.recipients.length > 0);

    const missingChats = this.chats
      .filter(chat => recipientsFor(this.emailRecipients, chat.username).length === 0)
      .map(chat => chat.username);
    const unusedRecipients = Object.keys(this.emailRecipients)
      .filter(username => !this.chats.some(chat => chat.username.toLowerCase() === username.toLowerCase()));

    if (transcripts.length === 0) {
      this.alertService.warn(`No chats match the recipients file. Missing: ${missingChats.join(', ') || 'none'}.`, {
        ...this.options,
        timeout: 12000
      });
      return;
    }

    this.sendingMail = true;
    this.http.post<EmailSendResponse>(
      '/api/automated-evaluation/emails',
      {transcripts},
      {withCredentials: true}
    ).subscribe({
      next: response => {
        this.sendingMail = false;
        this.emailConfigured = true;
        const extras = [
          missingChats.length > 0 ? `${missingChats.length} chat${missingChats.length === 1 ? '' : 's'} had no email` : '',
          unusedRecipients.length > 0 ? `${unusedRecipients.length} recipient name${unusedRecipients.length === 1 ? '' : 's'} had no chat` : ''
        ].filter(part => part.length > 0);
        if (response.failed === 0) {
          const summary = `Sent ${response.sent} email${response.sent === 1 ? '' : 's'}.${extras.length ? ' ' + extras.join('. ') + '.' : ''}`;
          this.log('success', summary);
          this.alertService.success(summary, {...this.options, timeout: 12000});
          this.showLogsSentConfirm();
        } else {
          const firstError = response.results.find(result => !result.sent)?.error;
          const summary = `Sent ${response.sent}, failed ${response.failed}.${firstError ? ' ' + firstError : ''}`;
          this.log('warning', summary);
          this.alertService.warn(summary, {...this.options, timeout: 15000});
        }
        this.changeDetector.detectChanges();
      },
      error: (error: HttpErrorResponse) => {
        this.sendingMail = false;
        const message = this.httpErrorDescription(error, 'Could not send chat logs.');
        this.log('danger', message);
        this.alertService.error(message, this.options);
        if (error.status === 503) {
          this.emailConfigured = false;
        }
        this.changeDetector.detectChanges();
      }
    });
  }

  private cancelRecipientsHelpClose(): void {
    if (this.recipientsHelpCloseTimer) {
      clearTimeout(this.recipientsHelpCloseTimer);
      this.recipientsHelpCloseTimer = null;
    }
  }

  private showLogsSentConfirm(): void {
    this.logsSentConfirm = true;
    this.clearLogsSentTimer();
    this.logsSentTimer = setTimeout(() => {
      this.logsSentConfirm = false;
      this.logsSentTimer = null;
      this.changeDetector.detectChanges();
    }, 3000);
  }

  private clearLogsSentTimer(): void {
    if (this.logsSentTimer) {
      clearTimeout(this.logsSentTimer);
      this.logsSentTimer = null;
    }
  }

  private refreshEmailStatus(): void {
    this.http.get<EmailStatus>('/api/automated-evaluation/email/status', {withCredentials: true}).subscribe({
      next: status => {
        this.emailConfigured = !!status?.configured;
        this.changeDetector.detectChanges();
      },
      error: () => {
        this.emailConfigured = null;
      }
    });
  }

  startChats(): void {
    const credentials = this.parseCredentials(this.credentialsInput.value || '');
    if (credentials.length === 0) {
      this.alertService.error('Enter credentials as bot1 pass1, bot2 pass2.', this.options);
      return;
    }

    this.startingChats = true;
    this.stopAsking = false;
    this.checkCredentials(credentials).then(results => {
      const valid = this.keepValidCredentials(credentials, results);
      this.showCredentialSummary(results, valid.length > 0);
      if (valid.length === 0) {
        this.alertService.error(`None of the ${credentials.length} bots could be started. ${this.skippedReasonText(results)}`, {
          ...this.options,
          timeout: 12000
        });
        return;
      }
      this.log('info', `Starting ${valid.length} of ${credentials.length} chat${credentials.length === 1 ? '' : 's'}...`);
      return this.createChatsSequentially(valid);
    }).finally(() => {
      this.startingChats = false;
    });
  }

  importCredentialsFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files && input.files[0];
    input.value = '';
    if (!file) {
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const credentials = this.parseImportedCredentials(String(reader.result || ''));
      if (credentials.length === 0) {
        this.credentialSummary = '';
        this.credentialIssueGroups = [];
        this.alertService.error('No botname-password pairs found in the file.', this.options);
        return;
      }
      this.checkCredentials(credentials).then(results => {
        const valid = this.keepValidCredentials(credentials, results);
        this.showCredentialSummary(results, false);
        if (valid.length === credentials.length) {
          this.log('success', `Imported ${valid.length} bot${valid.length === 1 ? '' : 's'} from ${file.name}.`);
          this.alertService.success(`Imported ${valid.length} bot credential${valid.length === 1 ? '' : 's'}.`, this.options);
          return;
        }
        const summary = `Kept ${valid.length} of ${credentials.length}. ${this.skippedReasonText(results)}`;
        this.log('warning', summary);
        this.alertService.warn(summary, {...this.options, timeout: 12000});
      });
    };
    reader.onerror = () => {
      this.alertService.error('Could not read the text file.', this.options);
    };
    reader.readAsText(file);
  }

  askQuestions(): void {
    if (this.startingChats || this.askingQuestions || this.chats.length === 0 || !this.hasActiveChats) {
      return;
    }
    if (this.questions.length === 0) {
      this.alertService.error('Add a questions file first.', this.options);
      return;
    }
    this.askingQuestions = true;
    this.stopAsking = false;
    this.log('info', `Asking ${this.questions.length} questions in order...`);
    this.askNextQuestion(0).then(() => {
      if (!this.askingQuestions) {
        return;
      }
      this.askingQuestions = false;
      this.currentQuestionLabel = '';
      if (this.stopAsking) {
        this.chats.forEach(chat => this.log('warning', 'Question round was stopped.', chat.username, chat.paneLog.roomID));
        return;
      }
      this.chats.forEach(chat => this.log('success', 'All questions were sent and scored.', chat.username, chat.paneLog.roomID));
      this.alertService.success('Question round finished. You can now finish the evaluation.', this.options);
    }).catch((error) => {
      this.askingQuestions = false;
      this.currentQuestionLabel = '';
      this.log('danger', `Question round failed: ${error?.message || error}`);
      this.alertService.error('Could not complete the question round.', this.options);
    });
  }

  seeQuestions(): void {
    if (this.questions.length === 0) {
      this.alertService.error('Add a questions file first.', this.options);
      return;
    }
    this.modalService.open(this.questionsModal, {size: 'lg', centered: true, scrollable: true});
  }

  onPaneClosed(chat: EvalChat): void {
    this.markEvalChatClosed(chat);
    this.chats = this.chats.filter(item => item.paneLog.roomID !== chat.paneLog.roomID);
    if (this.selectedRoomID === chat.paneLog.roomID) {
      this.selectedRoomID = this.chats[0]?.paneLog.roomID || '';
    }
    if (this.selectedLogChat === chat.paneLog.roomID) {
      this.selectedLogChat = this.chats[0]?.paneLog.roomID || '';
    }
    this.log('info', `Closed chat with ${chat.username}.`, chat.username, chat.paneLog.roomID);
    this.changeDetector.detectChanges();
  }

  private markEvalChatClosed(chat: EvalChat): void {
    chat.paneLog.active = false;
    const panes = this.chatPanes ? this.chatPanes.toArray() : [];
    const pane = panes.find(item => item.paneLog.roomID === chat.paneLog.roomID)
      || panes.find(item => item.paneLog === chat.paneLog);
    if (pane) {
      pane.markClosed();
    } else {
      panes.filter(item => item.paneLog.roomID === chat.paneLog.roomID).forEach(item => item.markClosed());
    }
  }

  finishEvaluation(): void {
    this.stopAsking = true;
    this.finishing = true;
    this.selectedLogChat = this.chats[0]?.paneLog.roomID || '';
    this.modalService.open(this.resultModal, {
      size: 'xl',
      centered: true,
      scrollable: true,
      windowClass: 'eval-result-modal'
    });
    this.finishing = false;
  }

  downloadChats(): void {
    if (!this.canDownloadChats) {
      return;
    }
    this.downloadingChats = true;
    try {
      const usedNames = new Set<string>();
      const files = this.chats.map(chat => {
        const base = this.safeFileName(chat.username);
        let name = `${base}.txt`;
        let suffix = 2;
        while (usedNames.has(name)) {
          name = `${base}-${suffix}.txt`;
          suffix += 1;
        }
        usedNames.add(name);
        return {name, content: this.buildChatExport(chat)};
      });
      const blob = createZipBlob(files);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-');
      link.href = url;
      link.download = `automated-evaluation-chats-${stamp}.zip`;
      link.click();
      window.URL.revokeObjectURL(url);
      this.log('success', 'Chat transcripts downloaded.');
    } catch (error) {
      this.alertService.error('Could not download chats.', this.options);
    } finally {
      this.downloadingChats = false;
    }
  }

  downloadSummary(): void {
    if (!this.canDownloadSummary) {
      return;
    }
    this.downloadingSummary = true;
    try {
      const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-');
      this.triggerDownload(`automated-evaluation-summary-${stamp}.xlsx`, this.buildSummaryExcel());
    } catch (error) {
      this.alertService.error('Could not download the summary.', this.options);
    } finally {
      this.downloadingSummary = false;
    }
  }

  scrollTo(id: string): void {
    const pane = document.querySelector('#evalchat' + id);
    if (pane) {
      pane.scrollIntoView({block: 'nearest', inline: 'center', behavior: 'smooth'});
    }
  }

  ngOnDestroy(): void {
    this.stopAsking = true;
    this.clearLogsSentTimer();
    this.cancelRecipientsHelpClose();
    this.unbindRecipientsHelpWindow();
  }

  expectedAnswersLabel(item: EvaluationQuestion): string {
    return expectedAnswersLabel(item);
  }

  private parseCredentials(raw: string): BotCredential[] {
    return raw
      .split(',')
      .map(part => part.trim())
      .filter(part => part.length > 0)
      .map(part => {
        const separator = part.search(/\s+/);
        if (separator <= 0) {
          return {username: '', password: ''};
        }
        return {
          username: part.slice(0, separator).trim(),
          password: part.slice(separator).trim()
        };
      })
      .filter(cred => cred.username.length > 0 && cred.password.length > 0);
  }

  private parseImportedCredentials(raw: string): BotCredential[] {
    const normalized = raw
      .replace(/^\uFEFF/, '')
      .replace(/\r\n/g, '\n')
      .replace(/\r/g, '\n')
      .split('\n')
      .map(line => line.trim())
      .filter(line => line.length > 0 && !line.startsWith('#'))
      .join(',');
    const spaced = this.parseCredentials(normalized);
    if (spaced.length > 0) {
      return spaced;
    }
    return normalized
      .split(',')
      .map(part => part.trim())
      .filter(part => part.length > 0)
      .map(part => {
        const separator = part.indexOf('-');
        if (separator <= 0 || separator === part.length - 1) {
          return {username: '', password: ''};
        }
        return {
          username: part.slice(0, separator).trim(),
          password: part.slice(separator + 1).trim()
        };
      })
      .filter(cred => cred.username.length > 0 && cred.password.length > 0);
  }

  private checkCredentials(credentials: BotCredential[]): Promise<CredentialCheckResult[]> {
    return this.http.post<CredentialCheckResult[]>(
      '/api/automated-evaluation/credentials/check',
      {credentials},
      {withCredentials: true}
    ).toPromise()
      .then(results => {
        if (!results || results.length !== credentials.length) {
          throw new Error('Credential check returned an unexpected result.');
        }
        return results;
      })
      .catch(() => this.checkCredentialsFallback(credentials));
  }

  private checkCredentialsFallback(credentials: BotCredential[]): Promise<CredentialCheckResult[]> {
    return Promise.all([
      this.adminService.getApiUserList().toPromise(),
      this.adminService.getApiUserSessions().toPromise()
    ]).then(([users, sessions]) => {
      const known = new Set((users || []).map(user => user.username));
      const online = new Set((sessions || []).map(session => session.userDetails.username));
      return credentials.map(credential => {
        if (!known.has(credential.username)) {
          return {username: credential.username, ok: false, reason: 'does_not_exist'};
        }
        if (!online.has(credential.username)) {
          return {username: credential.username, ok: false, reason: 'not_logged_in'};
        }
        return {username: credential.username, ok: true};
      });
    }).catch(() => credentials.map(credential => ({username: credential.username, ok: true})));
  }

  private keepValidCredentials(credentials: BotCredential[], results: CredentialCheckResult[]): BotCredential[] {
    const valid = credentials.filter((_, index) => results[index]?.ok);
    this.credentialsInput.setValue(valid.map(cred => `${cred.username} ${cred.password}`).join(', '));
    return valid;
  }

  private showCredentialSummary(results: CredentialCheckResult[], toast = true): void {
    const failed = results.filter(result => !result.ok);
    const kept = results.length - failed.length;
    this.credentialIssueGroups = this.buildCredentialIssueGroups(results);
    this.credentialSummary = failed.length === 0
      ? ''
      : `Kept ${kept} of ${results.length}. ${this.skippedReasonText(results)}`;
    failed.forEach(result => this.log('warning', this.credentialIssueMessage(result), result.username));
    if (toast && this.credentialSummary) {
      this.alertService.warn(this.credentialSummary, {...this.options, timeout: 12000});
    }
    this.changeDetector.detectChanges();
  }

  private buildCredentialIssueGroups(results: CredentialCheckResult[]): {title: string, usernames: string[]}[] {
    return [
      {reason: 'not_logged_in', title: 'Inactive'},
      {reason: 'does_not_exist', title: 'Does not exist'},
      {reason: 'wrong_password', title: 'Wrong password'}
    ]
      .map(group => ({
        title: group.title,
        usernames: [...new Set(results
          .filter(result => !result.ok && (result.reason || 'other') === group.reason)
          .map(result => result.username))]
      }))
      .filter(group => group.usernames.length > 0);
  }

  private skippedReasonText(results: CredentialCheckResult[]): string {
    const counts = new Map<string, number>();
    results.filter(result => !result.ok).forEach(result => {
      const key = result.reason || 'other';
      counts.set(key, (counts.get(key) || 0) + 1);
    });
    const parts = [
      this.countLabel(counts.get('not_logged_in') || 0, 'not logged in', 'not logged in'),
      this.countLabel(counts.get('does_not_exist') || 0, 'does not exist', 'do not exist'),
      this.countLabel(counts.get('wrong_password') || 0, 'wrong password', 'wrong password'),
      this.countLabel(counts.get('other') || 0, 'cannot be used', 'cannot be used')
    ].filter(part => part.length > 0);
    return parts.length > 0 ? `Skipped: ${parts.join(', ')}.` : '';
  }

  private countLabel(count: number, singular: string, plural: string): string {
    if (count <= 0) {
      return '';
    }
    return `${count} ${count === 1 ? singular : plural}`;
  }

  private credentialIssueMessage(result: CredentialCheckResult): string {
    if (result.reason === 'does_not_exist') {
      return `User ${result.username} does not exist.`;
    }
    if (result.reason === 'wrong_password') {
      return `User ${result.username} has the wrong password.`;
    }
    if (result.reason === 'not_logged_in') {
      return `User ${result.username} is not logged in.`;
    }
    return `User ${result.username} cannot be used.`;
  }

  private async createChatsSequentially(credentials: BotCredential[]): Promise<void> {
    const session = this.sessionId;
    for (const credential of credentials) {
      if (session !== this.sessionId) {
        return;
      }
      try {
        const knownIds = await this.snapshotRoomIds();
        await this.chatService.postApiRoomsRequest({
          username: credential.username,
          formName: '',
          automatedEvaluation: true
        } as ChatRequest & {automatedEvaluation: boolean}).toPromise();
        const room = await this.waitForNewRoom(knownIds);
        if (session !== this.sessionId) {
          this.chatService.patchApiRoomByRoomId(room.uid, undefined).toPromise().catch(() => undefined);
          return;
        }
        this.addChat(room, credential);
      } catch (error: unknown) {
        if (session !== this.sessionId) {
          return;
        }
        const status = this.httpStatus(error);
        if (status === 404) {
          this.log('danger', `User ${credential.username} does not exist.`, credential.username);
          this.alertService.error(`User ${credential.username} does not exist.`, this.options);
        } else if (status === 403) {
          this.log('danger', 'Cannot start a chat while an assignment round is running.');
          this.alertService.error('Cannot start a chat while an assignment round is running.', this.options);
        } else if (this.errorName(error) === 'TimeoutError') {
          this.log('danger', `Timed out waiting for a room with ${credential.username}.`, credential.username);
        } else {
          this.log('danger', `Could not start a chat with ${credential.username}.`, credential.username);
          this.alertService.error(`Could not start a chat with ${credential.username}.`, this.options);
        }
      }
    }
    if (session !== this.sessionId) {
      return;
    }
    this.log('success', `Started ${this.chats.length} chat${this.chats.length === 1 ? '' : 's'}.`);
    if (this.chats.length > 0) {
      this.alertService.success('Chats started. You can scroll through them below.', this.options);
    }
  }

  private snapshotRoomIds(): Promise<Set<string>> {
    return this.commonService.Rooms.pipe(take(1)).toPromise().then(list => {
      const ids = new Set(this.chats.map(chat => chat.paneLog.roomID));
      list?.rooms.forEach(room => ids.add(room.uid));
      return ids;
    });
  }

  private waitForNewRoom(knownIds: Set<string>): Promise<ChatRoomInfo> {
    return this.commonService.Rooms.pipe(
      map(list => list?.rooms.find(room => !knownIds.has(room.uid))),
      filter((room): room is ChatRoomInfo => !!room),
      take(1),
      timeout(this.roomTimeoutMs)
    ).toPromise() as Promise<ChatRoomInfo>;
  }

  private addChat(room: ChatRoomInfo, credential: BotCredential): void {
    if (this.chats.some(chat => chat.paneLog.roomID === room.uid)) {
      return;
    }
    if (this.selectedRoomID === '') {
      this.selectedRoomID = room.uid;
    }
    if (this.selectedLogChat === '') {
      this.selectedLogChat = room.uid;
    }
    const paneLog: PaneLog = {
      assignment: room.assignment,
      formRef: room.formRef,
      markAsNoFeedback: room.markAsNoFeedback,
      roomID: room.uid,
      ordinals: 0,
      messageLog: {},
      ratingOpen: false,
      active: true,
      ratings: {},
      myAlias: room.userAliases.find(a => a == room.alias) || '',
      otherAlias: room.userAliases.find(a => a != room.alias) || '',
      prompt: room.prompt,
      spectate: false,
      testerBotAlias: room.testerBotAlias
    };
    this.chats.unshift({
      username: credential.username,
      password: credential.password,
      paneLog,
      lastVerdict: null,
      lastResponseMs: null,
      correctOrdinals: []
    });
    this.log('success', `Chat started with ${credential.username} (${paneLog.otherAlias}).`, credential.username, room.uid);
  }

  private async askNextQuestion(questionIndex: number): Promise<void> {
    if (this.stopAsking || questionIndex >= this.questions.length) {
      return;
    }
    const item = this.questions[questionIndex];
    this.currentQuestionLabel = `Q${item.index + 1}: ${this.shorten(item.question)}`;
    this.chats.forEach(chat => {
      this.log('info', `Sending question ${item.index + 1}: ${this.shorten(item.question)}`, chat.username, chat.paneLog.roomID);
    });

    await Promise.all(this.chats.map(chat => this.askChatQuestion(chat, item)));
    await this.askNextQuestion(questionIndex + 1);
  }

  private async askChatQuestion(chat: EvalChat, item: EvaluationQuestion): Promise<void> {
    if (this.stopAsking) {
      return;
    }
    if (!chat.paneLog.active) {
      this.recordResult(chat, item, '', 'timeout', null);
      this.log('warning', `${chat.username}: room is no longer active, skipped question ${item.index + 1}.`, chat.username, chat.paneLog.roomID);
      return;
    }

    const beforeOrdinal = this.lastOtherOrdinal(chat);
    try {
      await this.chatService.postApiRoomByRoomId(chat.paneLog.roomID, undefined, '', item.question).toPromise();
    } catch (error) {
      this.recordResult(chat, item, '', 'timeout', null);
      this.log('danger', `${chat.username}: failed to send question ${item.index + 1}.`, chat.username, chat.paneLog.roomID);
      return;
    }

    const startedAt = Date.now();
    try {
      const reply = await this.waitForReply(chat, beforeOrdinal);
      const responseTimeMs = Date.now() - startedAt;
      const verdict = scoreAnswer(item.expectedAnswers, reply.message);
      this.recordResult(chat, item, reply.message, verdict, responseTimeMs);
      chat.lastVerdict = verdict;
      chat.lastResponseMs = responseTimeMs;
      if (verdict === 'correct') {
        chat.correctOrdinals = chat.correctOrdinals.concat(reply.ordinal);
      }
      this.log(verdict === 'incorrect' ? 'warning' : 'success',
        `${chat.username} Q${item.index + 1}: ${verdict} in ${this.formatResponseTime(responseTimeMs)} — "${this.shorten(reply.message)}"`,
        chat.username, chat.paneLog.roomID);
    } catch (error) {
      const responseTimeMs = Date.now() - startedAt;
      this.recordResult(chat, item, '', 'timeout', responseTimeMs);
      chat.lastVerdict = 'timeout';
      chat.lastResponseMs = responseTimeMs;
      this.log('warning', `${chat.username} Q${item.index + 1}: no answer within ${this.formatResponseTime(responseTimeMs)}.`, chat.username, chat.paneLog.roomID);
    }
  }

  private waitForReply(chat: EvalChat, afterOrdinal: number): Promise<{message: string, ordinal: number}> {
    return this.commonService.getChatStatusByRoomId(chat.paneLog.roomID).pipe(
      filter(state => {
        if (!state) {
          return false;
        }
        return state.messages.some(message =>
          message.authorAlias !== chat.paneLog.myAlias && message.ordinal > afterOrdinal);
      }),
      map(state => {
        const replies = state!.messages.filter(message =>
          message.authorAlias !== chat.paneLog.myAlias && message.ordinal > afterOrdinal);
        const last = replies[replies.length - 1];
        return {message: last.message, ordinal: last.ordinal};
      }),
      take(1),
      timeout(this.answerTimeoutMs)
    ).toPromise() as Promise<{message: string, ordinal: number}>;
  }

  private lastOtherOrdinal(chat: EvalChat): number {
    let last = -1;
    for (let i = 0; i < chat.paneLog.ordinals; i++) {
      const message = chat.paneLog.messageLog[i];
      if (message && !message.myMessage) {
        last = Math.max(last, message.ordinal);
      }
    }
    return last;
  }

  private recordResult(
    chat: EvalChat,
    item: EvaluationQuestion,
    answer: string,
    verdict: AnswerVerdict | 'timeout',
    responseTimeMs: number | null
  ): void {
    this.results.push({
      username: chat.username,
      roomId: chat.paneLog.roomID,
      questionIndex: item.index,
      question: item.question,
      expected: expectedAnswersLabel(item),
      answer,
      correct: verdict === 'correct',
      partial: verdict === 'partial',
      timedOut: verdict === 'timeout',
      responseTimeMs
    });
  }

  private averageTime(results: EvaluationResult[]): number | null {
    const times = results.flatMap(result =>
      !result.timedOut && result.responseTimeMs != null ? [result.responseTimeMs] : []
    );
    if (times.length === 0) {
      return null;
    }
    return times.reduce((sum, time) => sum + time, 0) / times.length;
  }

  private gradeForResults(results: EvaluationResult[]): number {
    return results.reduce((sum, result) => sum + this.pointsForResult(result), 0);
  }

  private pointsForResult(result: EvaluationResult): number {
    if (result.timedOut) {
      return 0;
    }
    let points = 0;
    if (result.correct) {
      points += this.grading.correctPoints;
    } else if (result.partial) {
      points += this.grading.partialPoints;
    }
    return points + this.bonusForResult(result);
  }

  private bonusForResult(result: EvaluationResult): number {
    const eligible = !result.timedOut && (!this.grading.speedBonusCorrectOnly || result.correct);
    return speedBonusPoints(result.responseTimeMs, this.grading, eligible);
  }

  formatBonus(points: number | null | undefined): string {
    return formatBonusPoints(points || 0);
  }

  private log(level: EvaluationLogEntry['level'], message: string, username?: string, roomId?: string): void {
    this.logs.unshift({time: new Date(), level, message, username, roomId});
  }

  private triggerDownload(filename: string, blob: Blob): void {
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    window.URL.revokeObjectURL(url);
  }

  private buildSummaryExcel(): Blob {
    return createSummaryExcelBlob(this.questions.length, this.chatScoreRows, this.grading);
  }

  private shorten(text: string): string {
    const compact = text.replace(/\s+/g, ' ').trim();
    return compact.length > 80 ? compact.slice(0, 77) + '...' : compact;
  }

  private safeFileName(value: string): string {
    const cleaned = value.replace(/[^a-zA-Z0-9._-]+/g, '_').replace(/^_+|_+$/g, '');
    return cleaned || 'chat';
  }

  private buildChatExport(chat: EvalChat): string {
    const lines: string[] = [
      `Chat: ${chat.username}`,
      `Room ID: ${chat.paneLog.roomID}`,
      '',
      '=== Conversation ==='
    ];

    for (let i = 0; i < chat.paneLog.ordinals; i++) {
      const message = chat.paneLog.messageLog[i];
      if (!message) {
        continue;
      }
      const speaker = message.myMessage ? 'admin' : chat.username;
      lines.push(`[${this.formatClock(message.time)}] ${speaker}: ${message.message}`);
    }

    if (chat.paneLog.ordinals === 0) {
      lines.push('(no messages)');
    }

    const chatResults = this.results
      .filter(result => result.roomId === chat.paneLog.roomID)
      .sort((a, b) => a.questionIndex - b.questionIndex);

    lines.push('', '=== Evaluation ===');
    if (chatResults.length === 0) {
      lines.push('(no scored questions)');
    } else {
      chatResults.forEach(result => {
        const verdict = result.timedOut ? 'TIMEOUT' : (result.correct ? 'CORRECT' : (result.partial ? 'PARTIAL' : 'INCORRECT'));
        const entry = [
          `Q${result.questionIndex + 1}: ${result.question}`,
          `Expected: ${result.expected}`,
          `Answer: ${result.timedOut ? '(no answer)' : result.answer}`,
          `Result: ${verdict}`,
          `Response time: ${this.formatResponseTime(result.responseTimeMs)}`
        ];
        if (this.grading.showGradesInLogs) {
          entry.push(`Points: ${this.formatGrade(this.pointsForResult(result))}`);
        }
        entry.push('');
        lines.push(...entry);
      });
    }

    const row = this.chatScoreRows.find(score => score.roomId === chat.paneLog.roomID);
    lines.push('=== Timing summary ===');
    lines.push(`Average: ${this.formatResponseTime(row?.avgMs ?? null)}`);
    lines.push(`Fastest: ${this.formatTimedQuestion(row?.fastestMs ?? null, row?.fastestQuestion ?? null)}`);
    lines.push(`Longest: ${this.formatTimedQuestion(row?.longestMs ?? null, row?.longestQuestion ?? null)}`);
    if (this.grading.showGradesInLogs) {
      lines.push(`Grade: ${this.formatGrade(row?.grade ?? 0)} (${this.scoringRulesText})`);
    }
    return lines.join('\n');
  }

  formatTimedQuestion(ms: number | null | undefined, questionNumber: number | null | undefined): string {
    if (ms == null) {
      return '—';
    }
    const label = this.formatResponseTime(ms);
    return questionNumber != null ? `${label} (Q${questionNumber})` : label;
  }

  private formatClock(ms: number): string {
    const date = new Date(ms);
    const pad = (value: number) => value.toString().padStart(2, '0');
    return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
  }

  private errorMessage(error: unknown, fallback: string): string {
    return error instanceof Error && error.message ? error.message : fallback;
  }

  private errorName(error: unknown): string {
    return error instanceof Error ? error.name : '';
  }

  private httpStatus(error: unknown): number | undefined {
    return error instanceof HttpErrorResponse ? error.status : undefined;
  }

  private httpErrorDescription(error: HttpErrorResponse, fallback: string): string {
    const body = error.error;
    if (body && typeof body === 'object' && typeof (body as {description?: unknown}).description === 'string') {
      return (body as {description: string}).description;
    }
    return fallback;
  }
}
