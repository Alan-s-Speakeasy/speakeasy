export class Alert {
  id!: string;
  type!: AlertType;
  message!: string;
  autoClose!: boolean;
  keepAfterRouteChange?: boolean;
  fade!: boolean;
  timeout?: number;

  constructor(init?:Partial<Alert>) {
    Object.assign(this, init);
  }
}

export enum AlertType {
  Success,
  Error,
  Info,
  Warning
}
