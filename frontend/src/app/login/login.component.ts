import {Component, Inject, OnInit} from '@angular/core';
import {FormControl, FormGroup} from "@angular/forms";
import {AuthService} from "../authentication.service";
import {Title} from "@angular/platform-browser";
import {Router} from "@angular/router";
import {HttpErrorResponse} from '@angular/common/http';
import {FrontendDataService} from "../frontend-data.service";
import {interval} from "rxjs";
import {ChatRoomList} from "../../../openapi";
import {take} from "rxjs/operators";
import {AlertService} from "../_alert";

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {

  constructor(private frontendDataService: FrontendDataService,
              private authService: AuthService,
              private titleService: Title,
              private router: Router,
              public alertService: AlertService) { };

  loginForm = new FormGroup({
    username: new FormControl(''),
    password: new FormControl(''),
  })


  ngOnInit(): void {
    this.titleService.setTitle("Login Page")

    this.authService.userSessionDetails.subscribe((response)=>{
        if(response != null){
          if(response.userDetails.role == "HUMAN" || response.userDetails.role == "ADMIN") {
            this.router.navigateByUrl('/panel', {}).then();
          }
          else {
            this.alertService.error("You seem to be a bot trying to login. However, only humans are allowed to login!")
          }
        }
      },
      error => {
        this.alertService.error("ERROR, Try again!")
      });
  }

  userLogin(): void {
    this.authService.userLogin(this.loginForm.value.username, this.loginForm.value.password).subscribe(
      (response)=> {
        if (response) {
          if(response.userDetails.role == "HUMAN" || response.userDetails.role == "ADMIN") {
            this.router.navigateByUrl('/panel', {}).then();
          }
          else {
            this.alertService.error("You seem to be a bot trying to login. However, only humans are allowed to login!")
          }
        }
        else {this.alertService.error("Authentication failed! Please try again with the correct username and password.");}
      },
      (error: HttpErrorResponse) =>{
        if(error.status == 401){
          this.alertService.error("Authentication failed! Please try again with the correct username and password.");
        }
        else{
          this.alertService.error(error.statusText);
        }
      }
    );
  }

}
