import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {NgForOf} from "@angular/common";
import {UserDetails} from "../../../../openapi";
import {NgSelectComponent} from "@ng-select/ng-select";

// Create a data scruture to hold the conditions
export class UserCondition {
  usersA: UserDetails[] = [];
  usersB: UserDetails[] = [];

  constructor() {
    this.usersA = [];
    this.usersB = [];
  }

  /**
   * Computes cartesian product of the usersA and usersB arrays and returns a nested list of strings
   * Returns a list of strings, where each string is a comma separated list of user ids
   * as requested by the API route.
   */
  cartesianProductToIdStr(): string[] {
    let result: string[] = [];
    // Special case : if userB is empty
    if (this.usersB.length == 0) {
      for (let userA of this.usersA) {
        result.push(userA.id + ",");
      }
      return result;
    }
    for (let userA of this.usersA) {
      for (let userB of this.usersB) {
        result.push(userA.id + "," + userB.id);
      }
    }
    return result;
  }
}


@Component({
  selector: 'app-users-involved-selected',
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule, NgForOf, NgSelectComponent],
  templateUrl: './users-involved-selected.component.html',
  styleUrls: ['./users-involved-selected.component.css']
})
export class UsersInvolvedSelectedComponent implements OnInit{
  @Input() allUsers: UserDetails[] = [];
  // NOTE : this needs to be an input because everytime the popover is closed the component gets destroyed.
  // By adding an input here, we can keep the state of the date range selector.
  userConditions: UserCondition[] = [];
  @Input() initUserConditions: UserCondition[] = [];
  @Output() onUsersSelected = new EventEmitter<UserCondition[]>();

  // Add a new condition
  addCondition() {
    this.userConditions.push(new UserCondition());
  }

  onConditionChanged(conditionIndex : number, event: UserDetails[], isA: boolean) {
    this.userConditions[conditionIndex].usersA = isA ? event : this.userConditions[conditionIndex].usersA;
    this.userConditions[conditionIndex].usersB = isA ? this.userConditions[conditionIndex].usersB : event;
    this.onUsersSelected.emit(this.userConditions);
  }


  // Remove a condition by index
  removeCondition(index: number) {
    this.userConditions.splice(index, 1);
    this.onUsersSelected.emit(this.userConditions);
  }

  // Submit the data
  submitConditions() {
    this.onUsersSelected.emit(this.userConditions);
    // Process the conditions array as needed
  }

  ngOnInit(): void {
    this.userConditions = this.initUserConditions;
  }
}
