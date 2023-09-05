/** Frontend variables and mock data */
import {ChatRoomAdminInfoUsers, FeedbackResponse} from "../../openapi";

export interface Message {
  myMessage: boolean,  // true: the message will be shown as my message (green bubble, on the right side);
  ordinal: number, // the ordinal of the current message;
  message: string,  // the content of this message;
  time: number,  // the time stamp of this message;
  type: string,  // the type of this message, ["THUMBS_UP", "THUMBS_DOWN", "STAR", ""];
  recipients: Array<string>,
  authorAlias: string,// true: the message is displayed in the chatroom;
}

export interface MessageLog {
  [key: number]: Message  // key: message ordinal
}

export interface PaneLog {
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
  isDevelopment?: boolean,
  developerAlias?: string
}

export interface Ratings {
  [key: string]: string
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
  prompt: string,
  roomID: string,
  startTime: number,
  remainingTime: number,
  userInfo: ChatRoomAdminInfoUsers[]
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
