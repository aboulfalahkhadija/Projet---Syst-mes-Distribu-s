import { Component, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AsyncPipe } from '@angular/common';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs';
import { Account, AccountListState, RequestStatus } from '../model/account.model';

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [AsyncPipe],
  templateUrl: './accounts.html',
  styleUrl: './accounts.css',
})
export class Accounts {
  private http = inject(HttpClient);

  // Permet au template HTML d'utiliser RequestStatus.SUCCESS et RequestStatus.ERROR
  protected readonly RequestStatus = RequestStatus;

  accounts$: Observable<AccountListState> = this.http
    .get<Account[]>('http://localhost:9999/EBANK-SERVICE/accounts')
    .pipe(
      map(resp => {
        return { accounts: resp, status: RequestStatus.SUCCESS };
      }),
      catchError(err => {
        return of({ status: RequestStatus.ERROR, errorMessage: err.statusText || err.message });
      })
    ); // <-- Notez la fermeture de la parenthèse du pipe ici
}
