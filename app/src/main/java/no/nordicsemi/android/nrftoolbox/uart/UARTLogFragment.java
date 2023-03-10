package no.nordicsemi.android.nrftoolbox.uart;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import androidx.fragment.app.ListFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;

import no.nordicsemi.android.log.ILogSession;
import no.nordicsemi.android.log.LogContract;
import no.nordicsemi.android.nrftoolbox.R;
import no.nordicsemi.android.nrftoolbox.profile.BleProfileService;
import no.nordicsemi.android.nrftoolbox.utility.DataConvey;

public class UARTLogFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor> {
	private static final String SIS_LOG_SCROLL_POSITION = "sis_scroll_position";
	private static final int LOG_SCROLL_NULL = -1;
	private static final int LOG_SCROLLED_TO_BOTTOM = -2;

	private static final int LOG_REQUEST_ID = 1;
	private static final String[] LOG_PROJECTION = { LogContract.Log._ID, LogContract.Log.TIME, LogContract.Log.LEVEL, LogContract.Log.DATA };

	/** The service UART interface that may be used to send data to the target. */
	private UARTInterface mUARTInterface;
	/** The adapter used to populate the list with log entries. */
	private CursorAdapter mLogAdapter;
	/** The log session created to log events related with the target device. */
	private ILogSession mLogSession;

	/** The last list view position. */
	private int mLogScrollPosition;

	/**
	 * The receiver that listens for {@link BleProfileService#BROADCAST_CONNECTION_STATE} action.
	 */
	private final BroadcastReceiver mCommonBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			// This receiver listens only for the BleProfileService.BROADCAST_CONNECTION_STATE action, no need to check it.
			final int state = intent.getIntExtra(BleProfileService.EXTRA_CONNECTION_STATE, BleProfileService.STATE_DISCONNECTED);

			if(DataConvey.connected){
				//TODO
			}

			switch (state) {
				case BleProfileService.STATE_CONNECTED: {
					break;
				}
				case BleProfileService.STATE_DISCONNECTED: {
					break;
				}
				case BleProfileService.STATE_CONNECTING:
				case BleProfileService.STATE_DISCONNECTING:
					// current implementation does nothing in this states
				default:
					// there should be no other actions
					break;
			}
		}
	};


	private ServiceConnection mServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(final ComponentName name, final IBinder service) {
			final UARTService.UARTBinder bleService = (UARTService.UARTBinder) service;
			mUARTInterface = bleService;
			mLogSession = bleService.getLogSession();

			// Start the loader
			if (mLogSession != null) {
				getLoaderManager().restartLoader(LOG_REQUEST_ID, null, UARTLogFragment.this);
			}
		}

		@Override
		public void onServiceDisconnected(final ComponentName name) {
			mUARTInterface = null;
		}

		@Override
		public void onNullBinding(ComponentName name) {
			ServiceConnection.super.onNullBinding(name);
		}
	};

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mCommonBroadcastReceiver, makeIntentFilter());

		// Load the last log list view scroll position
		if (savedInstanceState != null) {
			mLogScrollPosition = savedInstanceState.getInt(SIS_LOG_SCROLL_POSITION);
		}
	}

	@Override
	public void onStart() {
		super.onStart();

		/*
		 * If the service has not been started before the following lines will not start it. However, if it's running, the Activity will be binded to it
		 * and notified via mServiceConnection.
		 */
		Log.e("hello_Log","onStart");
		final Intent service = new Intent(getActivity(), UARTService.class);
		getActivity().bindService(service, mServiceConnection, 0); // we pass 0 as a flag so the service will not be created if not exists
	}

	@Override
	public void onStop() {
		super.onStop();

		try {
			getActivity().unbindService(mServiceConnection);
			mUARTInterface = null;
		} catch (final IllegalArgumentException e) {
			// do nothing, we were not connected to the sensor
		}
	}

	@Override
	public void onSaveInstanceState(final Bundle outState) {
		super.onSaveInstanceState(outState);

		// Save the last log list view scroll position
		final ListView list = getListView();
		final boolean scrolledToBottom = list.getCount() > 0 && list.getLastVisiblePosition() == list.getCount() - 1;
		outState.putInt(SIS_LOG_SCROLL_POSITION, scrolledToBottom ? LOG_SCROLLED_TO_BOTTOM : list.getFirstVisiblePosition());
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mCommonBroadcastReceiver);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.fragment_feature_uart_log, container, false);

		return view;
	}

	@Override
	public void onViewCreated(final View view, final Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		// Create the log adapter, initially with null cursor
		mLogAdapter = new UARTLogAdapter(getActivity());
		setListAdapter(mLogAdapter);
	}

	@Override
	public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
		switch (id) {
		case LOG_REQUEST_ID: {
			return new CursorLoader(getActivity(), mLogSession.getSessionEntriesUri(), LOG_PROJECTION, null, null, LogContract.Log.TIME);
		}
		}
		return null;
	}

	@Override
	public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
		// Here we have to restore the old saved scroll position, or scroll to the bottom if before adding new events it was scrolled to the bottom.  
		final ListView list = getListView();
		final int position = mLogScrollPosition;
		final boolean scrolledToBottom = position == LOG_SCROLLED_TO_BOTTOM || (list.getCount() > 0 && list.getLastVisiblePosition() == list.getCount() - 1);

		mLogAdapter.swapCursor(data);

		if (position > LOG_SCROLL_NULL) {
			list.setSelectionFromTop(position, 0);
		} else {
			if (scrolledToBottom)
				list.setSelection(list.getCount() - 1);
		}
		mLogScrollPosition = LOG_SCROLL_NULL;
	}

	@Override
	public void onLoaderReset(final Loader<Cursor> loader) {
		mLogAdapter.swapCursor(null);
	}

	/**
	 * Method called when user selected a device on the scanner dialog after the service has been started.
	 * Here we may bind this fragment to it.
	 */
	public void onServiceStarted() {
		// The service has been started, bind to it
		Log.e("hello_Log","ServiceStart");
		final Intent service = new Intent(getActivity(), UARTService.class);
		getActivity().bindService(service, mServiceConnection, 0);
	}

	private static IntentFilter makeIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BleProfileService.BROADCAST_CONNECTION_STATE);
		return intentFilter;
	}
}
