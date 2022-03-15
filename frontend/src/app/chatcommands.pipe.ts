import {Pipe, PipeTransform} from "@angular/core";

@Pipe({name: 'chatCommands'})
export class ChatCommandsPipe implements PipeTransform {

  private basePath = 'https://files.ifi.uzh.ch/ddis/teaching/2021/ATAI/dataset/movienet/';

  transform(message: string): string {
    return message
      .replace("</", "&lt;&#47;")
      .replace("<", "&lt;")
      .replace(/wd:([qQ][0-9]+)/g, "<a target='_blank' href='https://www.wikidata.org/wiki/$1'>$1</a>")
      .replace(/wdt:([pP][0-9]+)/g, "<a target='_blank' href='https://www.wikidata.org/wiki/Property:$1'>$1</a>")
      .replace(/imdb:(tt[0-9]+)/g, "<a target='_blank' href='https://www.imdb.com/title/$1'>$1</a>")
      .replace(/imdb:(nm[0-9]+)/g, "<a target='_blank' href='https://www.imdb.com/name/$1'>$1</a>")
      .replace(/frame:(tt[0-9]{7}\/shot_[0-9]{4}_img_[0-2])/g, "<br /><img style='max-width: 200px' src='" + this.basePath + "frames/$1.jpg' /><br />")
      .replace(/image:([0-9]{4}\/rm[0-9]{8,})/g, "<br /><img style='max-width: 200px' src='" + this.basePath + "images/$1.jpg' /><br />")
      .replace(/commons:([^\s:]{5,})/g, "<br /><img style='max-width: 200px' src='https://commons.wikimedia.org/wiki/File:$1' /><br />")
      //.replace(/yt:([A-Za-z0-9_\-]{11})/g, "<br /><iframe type='text/html' width='200' height='112.5' src='https://www.youtube.com/embed/$1' frameborder='0'><br />") //iframes are apparently not allowed...
  }

}
