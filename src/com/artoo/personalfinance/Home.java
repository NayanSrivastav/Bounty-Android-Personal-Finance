package com.artoo.personalfinance;

import java.util.List;
import com.artoo.personalfinance.broadcastReceiver.SMSReceiver;
import com.artoo.personalfinance.services.ArchiveSMSReaderService;
import com.artoo.personalfinance.services.SMSFilteringService;
import com.google.gson.Gson;

import persistantData.UtilitySharedpref;
import model.Transaction;
import widgets.DividerItemDecoration;
import adapter.NavigationDrawerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

public class Home extends AppCompatActivity implements FragmentPresenter,
		TransactionDetailsPresenter, HomeFragmentPresenter {

	private RecyclerView navRecyclerView;
	private static final String[] userPermissionText = {
			"You have allowed us to extract your transactions from your sms",
			"We are not extracting your transactions from your sms" };
	private static final String[] navItems = { "Home", "Comparative Reports",
			"Ignore List", "SMS Permission", "How it works" };

	private SMSTransactionReceiver receiver;
	private static final int[] navMenuDrawableIds = { R.drawable.home,
			R.drawable.ic_stats, R.drawable.ignore_item, R.drawable.sms_pref,
			R.drawable.how_it_works };

	private RecyclerView.LayoutManager navLayoutManager; // layout manager for
															// recyclerview to
															// specify its
															// appearance
	private NavigationDrawerAdapter navigationDrawerAdapter;

	private DrawerLayout mNavDrawer;

	private ActionBarDrawerToggle mDrawerToggle;

	private Toolbar toolBar;

	public static final int NEW_TRANSACTION_FRAGMENT_ID = 100,
			COMPARATIVE_REPORT = 1, FRAGMENT_IGNORE = 2, FRAGMENT_HOME = 0,
			FRAGMENT_HISTORY = 101, FRAGMENT_REPORT = 109,
			FRAGMENT_HOW_IT_WORKS = 4;

	private static final int SMS_PREF_OPTION = 3;

	private MenuItem newTrasactionItem;
	private int currentFragmentId = -1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_home);
		toolBar = (Toolbar) findViewById(R.id.app_bar);
		setSupportActionBar(toolBar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		navRecyclerView = (RecyclerView) findViewById(R.id.nav_recycler_view);
		mNavDrawer = (DrawerLayout) findViewById(R.id.nav_drawer);
		navRecyclerView.setHasFixedSize(true);
		navigationDrawerAdapter = new NavigationDrawerAdapter(this, navItems,
				this, navMenuDrawableIds);
		navLayoutManager = new LinearLayoutManager(Home.this,
				LinearLayoutManager.VERTICAL, false);
		navRecyclerView.setAdapter(navigationDrawerAdapter);
		navRecyclerView.setLayoutManager(navLayoutManager);
		navRecyclerView
				.addItemDecoration(new DividerItemDecoration(this, null));
		mDrawerToggle = new ActionBarDrawerToggle(this, mNavDrawer, toolBar,
				R.string.drawer_open, R.string.drawer_close) {
			/** Called when drawer is closed */
			public void onDrawerClosed(View view) {
				supportInvalidateOptionsMenu();
			}

			/** Called when a drawer is opened */
			public void onDrawerOpened(View drawerView) {
				supportInvalidateOptionsMenu();
			}
		};

		mNavDrawer.setDrawerListener(mDrawerToggle);
		mDrawerToggle.syncState();

		// checks if user has not set any preference about sms analysis
		// permission and if not show a dialog seeking user consent
		int userPermission = UtilitySharedpref.getSMSPermission(this);
		if (userPermission == SMSReceiver.SMS_DEFAULT_PERMISSION) {
			showUserConsentDialog();
		} else {
		}
		receiver = new SMSTransactionReceiver();
		// show home fragment by default on load of activity
		showFragment(FRAGMENT_HOME);
	}

	/**
	 * Show a dialog to ask user if user wants this app to read previous
	 * messages as well
	 */
	private void showArchiveMessageConsentSeekingDialog() {
		AlertDialog.Builder builder = new Builder(this);

		builder.setMessage("Do you want us to read your archive messages as well, this will only take 1 or two minutes.");

		builder.setNegativeButton("No", new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				UtilitySharedpref.setArchiveMessagesAsRead(Home.this);
			}
		});

		builder.setPositiveButton("Allow", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Intent smsIntent = new Intent(Home.this,
						ArchiveSMSReaderService.class);

				Home.this.startService(smsIntent);
				UtilitySharedpref.setArchiveMessagesAsRead(Home.this);
			}
		});

		builder.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				UtilitySharedpref.setArchiveMessagesAsRead(Home.this);
			}
		});
		builder.show();
	}

	/**
	 * shows up a dialog seeking user consent to analyze sms
	 */
	private void showUserConsentDialog() {
		AlertDialog.Builder builder = new Builder(this);

		builder.setMessage("Allow us to read your SMS and extract your transactions automatically.");

		builder.setNegativeButton("Not now", new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				UtilitySharedpref.setSMSPermission(Home.this,
						SMSReceiver.USER_DENIED_SMS_INTERCEPTION);
				SMSReceiver.disableBroadcastReceiver(getApplicationContext());

				showConsentPreferenceChangerDialog();
			}
		});

		builder.setPositiveButton("Allow", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				UtilitySharedpref.setSMSPermission(Home.this,
						SMSReceiver.USER_ALLOWED_SMS_INTERCEPTION);
				SMSReceiver.enableBroadcastReceiver(getApplicationContext());
				showConsentPreferenceChangerDialog();
			}
		});

		builder.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				int currentPermission = UtilitySharedpref
						.getSMSPermission(Home.this);
				UtilitySharedpref
						.setSMSPermission(Home.this, currentPermission);
				if (currentPermission != SMSReceiver.USER_ALLOWED_SMS_INTERCEPTION) {
					SMSReceiver
							.disableBroadcastReceiver(getApplicationContext());
				}
			}
		});
		builder.show();
	}

	@Override
	protected void onResume() {
		super.onResume();
		IntentFilter filter = new IntentFilter();
		filter.addAction(SMSFilteringService.BROADCAST_ACTION_KEY);
		registerReceiver(receiver, filter);
	}

	@Override
	protected void onStart() {
		super.onStart();

	}

	@Override
	protected void onPause() {

		super.onPause();
		unregisterReceiver(receiver);
	}

	/**
	 * to tell user how to change preferences this opens a dialog after user has
	 * allowed or denied from allowing this app to intercept messages
	 */
	private void showConsentPreferenceChangerDialog() {

		AlertDialog.Builder preferenceChangeDialog = new Builder(this);
		preferenceChangeDialog.setMessage("We have saved your preference!"
				+ " You can always change this from settings option.");
		preferenceChangeDialog.setPositiveButton("OK", new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				if (!UtilitySharedpref.getIsOlderMessageRead(Home.this))
					showArchiveMessageConsentSeekingDialog();
			}
		});
		preferenceChangeDialog
				.setOnCancelListener(new DialogInterface.OnCancelListener() {

					@Override
					public void onCancel(DialogInterface dialog) {
						if (!UtilitySharedpref.getIsOlderMessageRead(Home.this))
							showArchiveMessageConsentSeekingDialog();
					}
				});
		preferenceChangeDialog.show();
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean x = super.onPrepareOptionsMenu(menu);

		// hide the new transaction icon if current fragment is new
		// transaction fragment, show otherwise
		toggleTranIcon();
		return x;
	}

	/**
	 * hides the new transaction icon if new transaction screen is already
	 * opened, shows otherwise
	 */
	private void toggleTranIcon() {
		if (newTrasactionItem != null) {
			if (currentFragmentId == NEW_TRANSACTION_FRAGMENT_ID
					|| currentFragmentId == FRAGMENT_HISTORY) {
				newTrasactionItem.setVisible(false);
			} else {
				if (!newTrasactionItem.isVisible()) {
					newTrasactionItem.setVisible(true);
				}
			}
		}
	}

	/**
	 * close app when user is finally on home screen, if not bring home screen
	 * to the front
	 * 
	 */
	@Override
	public void onBackPressed() {

		if (currentFragmentId != FRAGMENT_HOME) {
			showFragment(FRAGMENT_HOME);
		} else {
			super.onBackPressed();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.home, menu);
		newTrasactionItem = menu.findItem(R.id.new_tran_menu_item);

		return true;
	}

	@Override
	public void showFragment(int position) {
		if (mNavDrawer.isShown())
			mNavDrawer.closeDrawers();

		// if user has selected sms preference option
		if (position == SMS_PREF_OPTION) {
			showUserConsentDialog();
			return;
		}

		// do not do anything if currently pressed position is same as what is
		// currently being
		// shown, otherwise show the item
		if (currentFragmentId != position) {
			currentFragmentId = position;
			FragmentTransaction fragmentTransaction = getSupportFragmentManager()
					.beginTransaction();

			switch (position) {

			case NEW_TRANSACTION_FRAGMENT_ID:
				ManualTransactionEntryFragment entryFragment = new ManualTransactionEntryFragment();
				fragmentTransaction.replace(R.id.content_frame, entryFragment);
				fragmentTransaction.commit();
				break;

			case COMPARATIVE_REPORT:
				ComparativeReport comparativeReport = new ComparativeReport();
				fragmentTransaction.replace(R.id.content_frame,
						comparativeReport);
				fragmentTransaction.commit();
				break;
			case FRAGMENT_HOME:
				FragmentHome fragmentHome = new FragmentHome(this);
				fragmentTransaction.replace(R.id.content_frame, fragmentHome);
				fragmentTransaction.commit();
				break;

			case FRAGMENT_IGNORE:
				IgnoreListCreateFragment ignoreListCreateFragment = new IgnoreListCreateFragment();
				fragmentTransaction.replace(R.id.content_frame,
						ignoreListCreateFragment);
				fragmentTransaction.commit();
				break;
			case FRAGMENT_HOW_IT_WORKS:
				FragmentHowItWorks fragmentHowItWorks = new FragmentHowItWorks(
						this);
				fragmentTransaction.replace(R.id.content_frame,
						fragmentHowItWorks);
				fragmentTransaction.commit();
			}

			// hide the new transaction icon if current fragment is new
			// transaction fragment, show otherwise
			toggleTranIcon();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.new_tran_menu_item) {
			showFragment(NEW_TRANSACTION_FRAGMENT_ID);
		} else if (id == R.id.sms_settings) {
			showUserConsentDialog();
		} else if (id == R.id.current_permission) {
			showUserSelectedPreference();
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * show a dialog to show user's current sms permission and an option to
	 * change them if user wants
	 */
	private void showUserSelectedPreference() {
		int userPref = UtilitySharedpref.getSMSPermission(this);
		String msg = userPermissionText[1];
		if (userPref == SMSReceiver.USER_ALLOWED_SMS_INTERCEPTION) {
			msg = userPermissionText[0];
		}
		AlertDialog.Builder prefBuilder = new Builder(this);
		prefBuilder.setTitle("SMS Preference");
		prefBuilder.setMessage(msg);
		prefBuilder.setPositiveButton("OK", new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {

			}
		});
		prefBuilder.setNegativeButton("Change", new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				showUserConsentDialog();
			}
		});
		prefBuilder.show();
	}

	@Override
	public void showHistoryFragment(Transaction transaction) {
		if (currentFragmentId != FRAGMENT_HISTORY) {
			currentFragmentId = FRAGMENT_HISTORY;
			FragmentTransaction fragmentTransaction = getSupportFragmentManager()
					.beginTransaction();
			TransactionHistoryFragment historyFragment = new TransactionHistoryFragment(
					transaction);
			fragmentTransaction.replace(R.id.content_frame, historyFragment);
			fragmentTransaction.commit();
			toggleTranIcon();
		}
	}

	private void notifyWhenBroadCast(Transaction transaction) {

		Toast.makeText(this,
				"New transaction of Rs." + transaction.getAmount() + " found",
				Toast.LENGTH_LONG).show();
	}

	// ---------------broadcast receiver if any new transaction from sms has
	// been added to database----------//

	private class SMSTransactionReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			Bundle databuBundle = intent.getExtras();
			if (databuBundle != null) {
				if (databuBundle
						.containsKey(SMSFilteringService.TRANSACTION_KEY)
						&& !databuBundle
								.containsKey(ArchiveSMSReaderService.IS_TRANSACTION)) {
					Transaction transaction = new Gson().fromJson(databuBundle
							.getString(SMSFilteringService.TRANSACTION_KEY),
							Transaction.class);
					notifyWhenBroadCast(transaction);
				}
			}
		}
	}

	@Override
	public void showReportFragment(List<Transaction> transactions) {
		if (currentFragmentId != FRAGMENT_REPORT) {
			currentFragmentId = FRAGMENT_REPORT;
			FragmentTransaction fragmentTransaction = getSupportFragmentManager()
					.beginTransaction();
			FragmentTransactionReport fragmentTransactionReport = new FragmentTransactionReport(
					transactions);
			fragmentTransaction.replace(R.id.content_frame,
					fragmentTransactionReport);
			fragmentTransaction.commit();
		}
	}

	@Override
	public void showHomeFragment() {
		showFragment(FRAGMENT_HOME);
	}
}