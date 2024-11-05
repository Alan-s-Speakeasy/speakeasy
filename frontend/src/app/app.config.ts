import {Injectable} from "@angular/core";

@Injectable()
export class AppConfig {

  //needs to be blank for regular deployment
  // private overwriteBasePath: string = ''; //'http://127.0.0.1:8080'
   private overwriteBasePath: string = ''; //'http://127.0.0.1:8080'
  // private overwriteBasePath: string = 'http://127.0.0.1:8080';

  get basePath(): string {
    if (this.overwriteBasePath.length != 0) {
      return this.overwriteBasePath;
    } else {
      const url = new URL(window.location.href);
      const protocol = url.protocol === 'https:' ? 'https:' : 'http:';
      const port = url.port == '' ? '' : `:${url.port}`;
      const path = `${protocol}//${url.hostname}${port}`;
      console.log(path);
      return path

    }
  }

}
