import { Routes } from '@angular/router';
import { Accounts } from './accounts/accounts';
import { BotUi } from './bot-ui/bot-ui';
export const routes: Routes = [
  { path: 'accounts', component: Accounts },
  { path: 'bot', component: BotUi }
];
