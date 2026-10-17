import { ApplicationConfig, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { loadingInterceptorInterceptor } from './interceptors/loading-interceptor-interceptor';
import { provideMarkdown } from 'ngx-markdown';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideMarkdown(),
    provideHttpClient(withInterceptors([loadingInterceptorInterceptor]))
  ]
};
