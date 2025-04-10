import { Component, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import {RouterOutlet} from "@angular/router";
import {SideNavbarComponent} from "./side-navbar/side-navbar.component";
import {AlertModule} from "./alert";

/**
 * MINIMAL LAYOUT
 * - Used for pages like Login
 * - Contains NO sidebar
 */
@Component({
  selector: 'app-minimal-layout',
  // Inline template to avoid a separate HTML file
  template: `
    <div class="position-fixed top-0 start-50 translate-middle-x" style="z-index: 1050; width: 100%; max-width: 600px;">
      <alert></alert>
    </div>
    <div class="d-flex justify-content-center align-items-center" style="min-height: 100vh;">
      <router-outlet></router-outlet>
    </div>
  `,
  // Inline styles (SCSS or CSS)
  styles: [`
  `],
  imports: [
    RouterOutlet,
    AlertModule
  ],
  standalone: true
})
export class MinimalLayoutComponent {}


/**
 * MAIN LAYOUT
 * - Used for all pages that DO need a sidebar
 */
@Component({
  selector: 'app-main-layout',
  template: `
    <div class="d-flex" style="min-height: 100vh;">
      <!-- Sidebar component -->
      <div class="">
        <app-side-navbar></app-side-navbar>
      </div>
      <!-- Main content area -->
      <div class="p-1" style="margin-left: 280px; width: calc(100% - 280px)">
        <div class="position-fixed fixed-top">
        </div>
        <router-outlet></router-outlet>
      </div>
    </div>
  `,
  styles: [`
  `],
  imports: [
    RouterOutlet,
    SideNavbarComponent,
    AlertModule
  ],
  standalone: true
})
export class MainLayoutComponent {}


