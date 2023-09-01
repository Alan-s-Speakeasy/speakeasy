import {Component, Inject, OnInit} from '@angular/core';
import {Router} from "@angular/router";
import {FormControl, FormGroup} from "@angular/forms";
import {Title} from "@angular/platform-browser";
import {CommonService} from "../common.service";
import {PasswordChangeRequest, UserService, UserSessionDetails} from "../../../openapi";
import {AlertService} from "../alert";
import {Subscription} from "rxjs";

@Component({
  selector: 'app-password',
  templateUrl: './password.component.html',
  styleUrls: ['./password.component.css']
})
export class PasswordComponent implements OnInit {

  private chatRoomsSubscription!: Subscription;
  constructor(private router: Router, private commonService: CommonService,
              private titleService: Title,
              @Inject(UserService) private userService: UserService,
              public alertService: AlertService) { }

  passwordForm = new FormGroup({
    currentPassword: new FormControl(''),
    newPassword: new FormControl(''),
    confirmPassword: new FormControl(''),
  })


  ngOnInit(): void {
    this.titleService.setTitle("Password Reset")
    this.chatRoomsSubscription = this.commonService.alertOnNewChatRoom()
  }

  changePassword(): void {
      if (this.passwordForm.value.newPassword == this.passwordForm.value.confirmPassword) {
        if(this.passwordForm.value.newPassword != this.passwordForm.value.currentPassword){
          this.userService.patchApiUserPassword(undefined, {
            currentPassword: this.passwordForm.value.currentPassword,
            newPassword: this.passwordForm.value.newPassword
          } as PasswordChangeRequest).subscribe(() => {
              this.userLogout()
            },
            error => {
              this.alertService.error("An error occurred. Please try again.")
            }
          );
        }
        else{
          this.alertService.error("New password cannot be same as your old password.")
        }
      } else {
        this.alertService.error("The first input of your new password does not match the second input.")
      }
  }

  userLogout(): void {
    this.alertService.success("Password has been successfully changed. Please re-login with your new password!")
    this.userService.getApiLogout(undefined,'body',true).subscribe((response)=> {
        if (response) {
          this.router.navigateByUrl('/login').then();
        }
        else {
          this.alertService.error("Logout failed. Please try again.")
        }
      },
      error => {
        this.alertService.error("Logout failed. Please try again.");
      }
    );
  }

  home(): void {
    this.router.navigateByUrl('/panel').then()
  }

  ngOnDestroy() {
    this.chatRoomsSubscription.unsubscribe()
  }
}
