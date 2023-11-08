/** Frontend variables and mock data */
import {
  ChatRoomUserAdminInfo,
  FeedbackRequest,
  FeedbackResponse,
  ChatRoomList,
  ChatMessageReactionType
} from "../../openapi";

export function convertFromJSON<T>(json: string): T {
  const data = JSON.parse(json);

  if (Array.isArray(data)) {
    return data as unknown as T;
  } else {
    return Object.assign({}, data) as T;
  }
}

export enum ChatEventType {
  ROOMS = "ROOMS",
  MESSAGES = "MESSAGES",
  REACTIONS = "REACTIONS"
}

export interface SseChatMessage {
  roomId: string;
  timeStamp: number;
  authorAlias: string;
  ordinal: number;
  message: string;
}

export interface SseChatReaction {
  roomId: string;
  messageOrdinal: number;
  type: ChatMessageReactionType;
}

export interface SseRoomState {
  messages: SseChatMessage[],
  reactions: SseChatReaction[]
}

export interface Message {
  myMessage: boolean,  // true: the message will be shown as my message (green bubble, on the right side);
  ordinal: number, // the ordinal of the current message;
  message: string,  // the content of this message;
  time: number,  // the time stamp of this message;
  type: string,  // the type of this message, ["THUMBS_UP", "THUMBS_DOWN", "STAR", ""];
}

export interface MessageLog {
  [key: number]: Message  // key: message ordinal
}

export interface PaneLog {
  assignment: boolean,
  formRef: string,
  markAsNoFeedback: boolean,
  roomID: string,  // id of the chatroom;
  ordinals: number,  // number of messages
  messageLog: MessageLog,  // all messages of the chatroom;
  active: boolean,
  ratingOpen: boolean,  // true: open for rating;
  ratings: Ratings, // ratings of this chatroom
  prompt: string,
  myAlias: string,
  otherAlias: string,
  spectate: boolean,
  history?: boolean,
}

export interface Ratings {
  [key: string]: string
}

export interface FeedbackForm {
  formName: string,
  requests: FeedbackRequest[],
}

export interface FrontendUser {
  userID: string,
  role: string,
  username: string,
}

export interface FrontendUserDetail {
  userID: string,
  role: string,
  username: string,
  startTime: number[]
  sessionId: string[],
  sessionToken: string[],
}

export interface FrontendGroup {
  groupID: string,
  groupName: string,
  users: FrontendUserInGroup[],
}

export interface FrontendUserInGroup {
  username: string,
  role: string,
}

export interface FrontendChatroomDetail {
  assignment: boolean,
  formRef: string,
  prompt: string,
  roomID: string,
  startTime: number,
  remainingTime: number,
  userInfo: ChatRoomUserAdminInfo[],
  markAsNoFeedBack: boolean
}

export interface FrontendUserFeedback {
  author: string,
  recipient: string,
  roomId: string,
  responses: FeedbackResponse[]
}

export interface FrontendAverageFeedback {
  username: string,
  responses: FeedbackResponse[]
}
