import { TestBed } from '@angular/core/testing';

import { FrontendDataService } from './frontend-data.service';

describe('FrontendDataService', () => {
  let service: FrontendDataService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(FrontendDataService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
