import {Component, EventEmitter, Input, Output} from '@angular/core';
import {FormArray, FormBuilder, FormGroup, ReactiveFormsModule, FormsModule} from '@angular/forms';
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
export class UsersInvolvedSelectedComponent {
  @Input() allUsers: UserDetails[] = [];
  @Output() onUsersSelected = new EventEmitter<UserCondition[]>();

  conditions: Array<UserCondition> = [new UserCondition()];

  // Add a new condition
  addCondition() {
    this.conditions.push(new UserCondition());
  }

  onConditionChanged(conditionIndex : number, event: UserDetails[], isA: boolean) {
    this.conditions[conditionIndex].usersA = isA ? event : this.conditions[conditionIndex].usersA;
    this.conditions[conditionIndex].usersB = isA ? this.conditions[conditionIndex].usersB : event;
    this.onUsersSelected.emit(this.conditions);
  }


  // Remove a condition by index
  removeCondition(index: number) {
    this.conditions.splice(index, 1);
  }

  // Submit the data
  submitConditions() {
    this.onUsersSelected.emit(this.conditions);
    // Process the conditions array as needed
  }
}
