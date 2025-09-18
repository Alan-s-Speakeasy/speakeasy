import {Component, Input, Output, EventEmitter} from '@angular/core';

@Component({
  selector: 'app-user-table',
  templateUrl: './user-table.component.html',
  styleUrls: ['./user-table.component.css']
})
export class UserTableComponent {
  @Input() users: string[] = [];
  @Input() active: string[] = [];
  @Input() isSelected: Map<string, boolean> = new Map();
  @Input() pageSizes: number[] = [5, 10, 15, 20];
  @Input() pageSize: number = 10;
  @Input() onlineOnly: boolean = false;

  @Output() userSwitched = new EventEmitter<string>();
  @Output() allSwitched = new EventEmitter<any>();
  @Output() onlineOnlySwitched = new EventEmitter<boolean>();

  searchTerm: string = '';
  sortColumn: string = '';
  sortDirection: 'asc' | 'desc' = 'asc';
  page: number = 1;

  get filteredUsers(): string[] {
    let filtered = this.onlineOnly ? this.users.filter(user => this.active.includes(user)) : [...this.users];

    if (this.searchTerm) {
      filtered = filtered.filter(user => user.toLowerCase().includes(this.searchTerm.toLowerCase()));
    }

    if (this.sortColumn) {
      filtered.sort((a, b) => {
        let comparison = 0;
        if (this.sortColumn === 'name') {
          comparison = a.localeCompare(b);
        } else if (this.sortColumn === 'selected') {
          comparison = (this.isSelected.get(a) === this.isSelected.get(b)) ? 0 : this.isSelected.get(a) ? -1 : 1;
        }
        return this.sortDirection === 'asc' ? comparison : -comparison;
      });
    }
    return filtered;
  }

  areAllSelected(): boolean {
    const filtered = this.filteredUsers;
    if (filtered.length === 0) return false;
    return filtered.every(user => this.isSelected.get(user));
  }

  switchUser(user: string): void {
    this.userSwitched.emit(user);
  }

  switchAll(event: any): void {
    this.allSwitched.emit({checked: event.checked, users: this.filteredUsers});
  }

  switchOnlineOnly(event: any): void {
    this.onlineOnlySwitched.emit(event.checked);
  }

  sortTable(column: string): void {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
  }
}
