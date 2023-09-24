# Frontend

This project was generated with [Angular CLI](https://github.com/angular/angular-cli) version 12.1.4.

## Some Comments

- To login as a human: `username: human1, password: human1`; To login as an admin: `username: admin1, password: admin1`

- Each page is implemented as a component in [`./src/app/`](https://gitlab.ifi.uzh.ch/ddis/Lectures/atai/speakeasy/-/tree/main/frontend/src/app);

- Page navigation is handled by [`app-routing`](https://gitlab.ifi.uzh.ch/ddis/Lectures/atai/speakeasy/-/blob/main/frontend/src/app/app-routing.module.ts); 

- Communication between components is handled by [`CommonService`](https://gitlab.ifi.uzh.ch/ddis/Lectures/atai/speakeasy/-/blob/main/frontend/src/app/common.service.ts);

- Mock data is defined in [`data.ts`](https://gitlab.ifi.uzh.ch/ddis/Lectures/atai/speakeasy/-/blob/main/frontend/src/app/data.ts); 
  
## Frontend Development

To develop the frontend, please ensure you are now under the path `speakeasy/frontend`.

Run `ng serve` for a dev server. Navigate to `http://127.0.0.1:4200/` (Do NOT use `http://localhost:4200/`, which cannot store the session cookie for you). The app will automatically reload if you change any of the source files.

## Code scaffolding

Run `ng generate component component-name` to generate a new component. You can also use `ng generate directive|pipe|service|class|guard|interface|enum|module`.

## Build

After your development on port `4200` (`ng serve` mode), please do the production build so that your changes can be updated on port `8080` (when you are running the whole Speakeasy project via the backend `main()` function):

Run `npm run pbuild` to update your frontend changes.

## Running unit tests

Run `ng test` to execute the unit tests via [Karma](https://karma-runner.github.io).

## Running end-to-end tests

Run `ng e2e` to execute the end-to-end tests via a platform of your choice. To use this command, you need to first add a package that implements end-to-end testing capabilities.

## Further help

To get more help on the Angular CLI use `ng help` or go check out the [Angular CLI Overview and Command Reference](https://angular.io/cli) page.

## Page Previews

- Login Page  
  <img src="https://gitlab.ifi.uzh.ch/ddis/Lectures/atai/speakeasy/-/raw/main/frontend/previews/login_page.png" alt="login page" width="70%">

- Panel Page  
  <img src="https://gitlab.ifi.uzh.ch/ddis/Lectures/atai/speakeasy/-/raw/main/frontend/previews/panel_page.png" alt="panel page" width="70%">

- Password Reset  
  <img src="https://gitlab.ifi.uzh.ch/ddis/Lectures/atai/speakeasy/-/raw/main/frontend/previews/password_reset.png" alt="password reset" width="70%">
  
- Chatting Page   
  <img src="https://gitlab.ifi.uzh.ch/ddis/Lectures/atai/speakeasy/-/raw/main/frontend/previews/chatting_page.png" alt="chatting page" width="70%">

- Rating Page   
  <img src="https://gitlab.ifi.uzh.ch/ddis/Lectures/atai/speakeasy/-/raw/main/frontend/previews/rating_page.png" alt="rating page" width="70%">

- History Page   
  <img src="https://gitlab.ifi.uzh.ch/ddis/Lectures/atai/speakeasy/-/raw/main/frontend/previews/history_page.png" alt="history page" width="70%">
  
- Admin Panel Page  
  <img src="https://gitlab.ifi.uzh.ch/ddis/Lectures/atai/speakeasy/-/raw/main/frontend/previews/admin_panel.png" alt="admin panel page" width="70%">
  
- Admin Chat Page  
  <img src="https://gitlab.ifi.uzh.ch/ddis/Lectures/atai/speakeasy/-/raw/main/frontend/previews/chatting_page.png" alt="admin chat page" width="70%">
  
- User Details Page  
  <img src="https://gitlab.ifi.uzh.ch/ddis/Lectures/atai/speakeasy/-/raw/main/frontend/previews/user_details.png" alt="user details page" width="70%">

- Chatroom Details Page  
  <img src="https://gitlab.ifi.uzh.ch/ddis/Lectures/atai/speakeasy/-/raw/main/frontend/previews/chatroom_details.png" alt="chatroom details page" width="70%">
