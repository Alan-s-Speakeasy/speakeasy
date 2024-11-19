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

  @Output() userSwitched = new EventEmitter<string>();
  @Output() allSwitched = new EventEmitter<any>();

  searchTerm: string = '';
  sortColumn: string = '';
  sortDirection: 'asc' | 'desc' = 'asc';
  page: number = 1;

  get filteredUsers(): string[] {
    let filtered = this.users.filter(user => user.toLowerCase().includes(this.searchTerm.toLowerCase()));
    if (this.sortColumn) {
      filtered.sort((a, b) => {
        let comparison = 0;
        if (this.sortColumn === 'name') {
          comparison = a.localeCompare(b);
        } else if (this.sortColumn === 'selected') {
          comparison = (this.isSelected.get(a) === this.isSelected.get(b)) ? 0 : this.isSelected.get(a) ? -1 : 1;
        } else if (this.sortColumn === 'online') {
          comparison = (this.active.includes(a) === this.active.includes(b)) ? 0 : this.active.includes(a) ? -1 : 1;
        }
        return this.sortDirection === 'asc' ? comparison : -comparison;
      });
    }
    return filtered;
  }

  areAllSelected(): boolean {
    return !Array.from(this.isSelected.values()).includes(false);
  }

  switchUser(user: string): void {
    this.userSwitched.emit(user);
  }

  switchAll(event: any): void {
    this.allSwitched.emit(event);
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
