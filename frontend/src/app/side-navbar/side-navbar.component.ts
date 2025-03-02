import {Component, Inject, OnInit} from '@angular/core';
import {AuthService} from "../authentication.service";
import {UserSessionDetails} from "../../../openapi";
import {Subscription} from "rxjs";
import {Router, RouterLink, RouterLinkActive} from "@angular/router";
import {NgClass, NgForOf, NgIf, NgOptimizedImage, NgStyle} from "@angular/common";
import {NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle} from "@ng-bootstrap/ng-bootstrap";
import {AvatarModule} from "primeng/avatar";
import {AlertService} from "../alert";

@Component({
  selector: 'app-side-navbar',
  standalone: true,
  imports: [
    RouterLink,
    NgForOf,
    RouterLinkActive,
    NgOptimizedImage,
    NgbDropdown,
    NgbDropdownMenu,
    NgbDropdownItem,
    NgbDropdownToggle,
    AvatarModule,
    NgStyle,
    NgClass,
    NgIf
  ],
  templateUrl: './side-navbar.component.html',
  styleUrl: './side-navbar.component.css'
})
export class SideNavbarComponent implements OnInit{
  options = {
    autoClose: true,
    keepAfterRouteChange: true
  };
  private userDetailsSubscription!: Subscription;

  constructor(@Inject(AuthService) private authService: AuthService,
              @Inject(Router) protected router: Router,
              @Inject(AlertService) private alertService: AlertService,
  ) {
  }

  userName!: string
  role!: string
  session!: UserSessionDetails
  // Links of the navar
  links_admin = [
    {name: 'Home', url: '/panel', icon: 'pi pi-home'},
    {name: 'Chat', url: '/chat', icon: 'pi pi-comments'},
    {name: 'Assignments', url: '/assignment', icon: 'pi pi-check-square'},
    {name: 'Feedbacks', url: '/feedback', icon: 'pi pi-comment'},
    {name: 'Chatrooms', url: '/chatroomStatus', icon: 'pi pi-list'},
    {name: 'Users', url: '/userStatus', icon: 'pi pi-users'},
  ]
    links_user = [
    {name: 'Home', url: '/panel', icon: 'pi pi-home'},
    {name: 'Chat', url: '/chat', icon: 'pi pi-comments'},
    {name: 'History', url: '/history', icon: 'pi pi-clock'},
  ]
  links: any = []


  ngOnInit(): void {

    this.userDetailsSubscription = this.authService.userSessionDetails.subscribe((response) => {
        if (response != null) {
          this.userName = response.userDetails.username
          this.role = response.userDetails.role
          this.session = response
        }
        if (!this.role || !this.userName) {
          this.links = []
        }
        else if (this.role == "ADMIN") {
          this.links = this.links_admin
        }
        else {
          this.links = this.links_user
        }
      },
      error => {
      },
      () => {

      });
  }


  userLogout(): void {
    this.authService.userLogout().subscribe(
      (response) => {
        if (response) {
          localStorage.clear()
          this.router.navigateByUrl('/login').then();
        } else {
          this.alertService.error("Logout failed. Please try again.")
        }
      },
      error => {
        this.alertService.error("Logout failed. Please try again.")
      }
    );
  }

}
