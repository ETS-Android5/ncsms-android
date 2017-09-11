package fr.unix_experience.owncloud_sms.engine;

/*
 *  Copyright (c) 2014-2016, Loic Blot <loic.blot@unix-experience.fr>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import fr.unix_experience.owncloud_sms.R;
import fr.unix_experience.owncloud_sms.enums.OCSMSNotificationType;
import fr.unix_experience.owncloud_sms.exceptions.OCSyncException;
import fr.unix_experience.owncloud_sms.jni.SmsBuffer;
import fr.unix_experience.owncloud_sms.notifications.OCSMSNotificationUI;
import fr.unix_experience.owncloud_sms.prefs.OCSMSSharedPrefs;

public interface ASyncSMSSync {
	class SyncTask extends AsyncTask<Void, Void, Void> {
		public SyncTask(Context context) {
			_context = context;
			_smsBuffer = null;
		}

		public SyncTask(Context context, SmsBuffer buffer) {
			_context = context;
			_smsBuffer = buffer;
		}

		@Override
		protected Void doInBackground(Void... params) {
            Log.i(ASyncSMSSync.TAG, "Starting background sync");

			// If no smsBuffer given it's a full sync
			if (_smsBuffer == null) {
				doFullSync();
			}
			else {
				performSync(_smsBuffer);
			}

            Log.i(ASyncSMSSync.TAG, "Stopping background sync");
			return null;
		}

		private void doFullSync() {
			OCSMSSharedPrefs prefs = new OCSMSSharedPrefs(_context);
			long syncStartupDate = prefs.getLastMessageDate();

			Log.i(ASyncSMSSync.TAG, "Current message date is " + syncStartupDate);
			boolean shouldSync = true;
			AndroidSmsFetcher fetcher = new AndroidSmsFetcher(_context);
			while (shouldSync) {
				SmsBuffer smsBuffer = new SmsBuffer();
				fetcher.bufferMessagesSinceDate(smsBuffer, syncStartupDate);
				if (smsBuffer.empty()) {
					Toast.makeText(_context, _context.getString(R.string.nothing_to_sync), Toast.LENGTH_SHORT).show();
					Log.i(ASyncSMSSync.TAG, "Finish syncAllMessages(): no more sms");
					smsBuffer.clear();
					shouldSync = false;
					continue;
				}

				if (prefs.showSyncNotifications()) {
					OCSMSNotificationUI.notify(_context, _context.getString(R.string.sync_title),
							_context.getString(R.string.sync_inprogress), OCSMSNotificationType.SYNC.ordinal());
				}

				syncStartupDate = smsBuffer.getLastMessageDate();
				performSync(smsBuffer);
			}
		}

		private void performSync(SmsBuffer smsBuffer) {
			// Get ownCloud SMS account list
			AccountManager _accountMgr = AccountManager.get(_context);
			Account[] myAccountList = _accountMgr.getAccountsByType(_context.getString(R.string.account_type));

			// Notify that we are syncing SMS
			for (Account element : myAccountList) {
				try {
					OCSMSOwnCloudClient _client = new OCSMSOwnCloudClient(_context, element);
					_client.doPushRequest(smsBuffer);
					OCSMSNotificationUI.cancel(_context);
				} catch (IllegalStateException e) { // Fail to read account data
					OCSMSNotificationUI.notify(_context, _context.getString(R.string.fatal_error),
							e.getMessage(), OCSMSNotificationType.SYNC_FAILED.ordinal());
				} catch (OCSyncException e) {
					Log.e(ASyncSMSSync.TAG, _context.getString(e.getErrorId()));
					OCSMSNotificationUI.notify(_context, _context.getString(R.string.fatal_error),
							e.getMessage(), OCSMSNotificationType.SYNC_FAILED.ordinal());
				}
			}
			OCSMSNotificationUI.cancel(_context);
			smsBuffer.clear();
		}

		private final SmsBuffer _smsBuffer;
		private final Context _context;
	}

	String TAG = ASyncSMSSync.class.getSimpleName();
}
