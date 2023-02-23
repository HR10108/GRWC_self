/*
 * 处理蓝牙通信的主程序
 */
package no.nordicsemi.android.nrftoolbox.uart;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.google.android.gms.common.api.GoogleApiClient;
import java.util.UUID;

import no.nordicsemi.android.nrftoolbox.R;
import no.nordicsemi.android.nrftoolbox.profile.BleProfileService;
import no.nordicsemi.android.nrftoolbox.profile.BleProfileServiceReadyActivity;
import no.nordicsemi.android.nrftoolbox.utility.DataConvey;

public class UARTActivity extends BleProfileServiceReadyActivity<UARTService.UARTBinder> implements UARTInterface, GoogleApiClient.ConnectionCallbacks {

	/** This preference is set to true when initial data synchronization for wearables has been completed. */
	private final static String PREFS_WEAR_SYNCED = "prefs_uart_synced";

	private SharedPreferences mPreferences;
	private UARTService.UARTBinder mServiceBinder;

	@Override
	protected Class<? extends BleProfileService> getServiceClass() {
		return UARTService.class;
	}

	@Override
	protected int getLoggerProfileTitle() {
		return R.string.uart_feature_title;
	}

	@Override
	protected Uri getLocalAuthorityLogger() {
		return UARTLocalLogContentProvider.AUTHORITY_URI;
	}

	@Override
	protected void setDefaultUI() {
		// empty
	}

	@Override
	protected void onServiceBinded(final UARTService.UARTBinder binder) {
		mServiceBinder = binder;
	}

	@Override
	protected void onServiceUnbinded() {
		mServiceBinder = null;
	}

	@Override
	protected void onInitialize(final Bundle savedInstanceState) {
		mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, makeIntentFilter());
	}

	/**
	 * Method called when Google API Client connects to Wearable.API.
	 */
	@Override
	public void onConnected(final Bundle bundle) {
		if (!mPreferences.getBoolean(PREFS_WEAR_SYNCED, false)) {
			new Thread(new Runnable() {
				@Override
				public void run() {
				}
			}).start();
		}
	}

	/**
	 * Method called then Google API client connection was suspended.
	 * @param cause the cause of suspension
	 */
	@Override
	public void onConnectionSuspended(final int cause) {
		// dp nothing
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
	}

	private TextView textView;
	private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
		    final String action = intent.getAction();
			if (UARTService.BROADCAST_UART_RX.equals(action)) {
				final String zbdata = intent.getStringExtra(UARTService.EXTRA_DATA);
				textView.setText(zbdata);
			}
		}
	};
	private static IntentFilter makeIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(UARTService.BROADCAST_UART_RX);
		return intentFilter;
	}

	private TextView editText;
	private void onSendClicked() {
		final String text = editText.getText().toString();
		if(!(text.equals(""))) {
			DataConvey.write_enable = true;
			send(text);
			DataConvey.write_enable = false;
		}
		editText.setText("");
		DataConvey.TX_DATA = "";
		onBackPressed();
	}

	@Override
	protected void onCreateView(final Bundle savedInstanceState) {
		setContentView(R.layout.activity_feature_uart);

		textView = (TextView)findViewById(R.id.RXData);
		editText = (TextView) findViewById(R.id.TXData);
		editText.setText(DataConvey.TX_DATA);
		DataConvey.TX_DATA = "";
		final String text = editText.getText().toString();
		Button sendButton = (Button) findViewById(R.id.sendButton);
		if(!(text.equals(""))) {
			sendButton.setEnabled(true);
		}
		else {
			sendButton.setEnabled(false);
		}
		sendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				onSendClicked();
			}
		});
	}

	@Override
	protected void onViewCreated(final Bundle savedInstanceState) {
		getSupportActionBar().setDisplayShowTitleEnabled(false);
	}

	@Override
	protected void onRestoreInstanceState(final @NonNull Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
	}

	@Override
	public void onSaveInstanceState(final Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onServicesDiscovered(final BluetoothDevice device, final boolean optionalServicesFound) {
		// do nothing
	}

	@Override
	public void onDeviceSelected(final BluetoothDevice device, final String name) {
		// The super method starts the service
		super.onDeviceSelected(device, name);

		// Notify the log fragment about it
		final UARTLogFragment logFragment = (UARTLogFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_log);
		logFragment.onServiceStarted();
	}

	@Override
	protected int getDefaultDeviceName() {
		return R.string.uart_default_name;
	}

	@Override
	protected int getAboutTextId() {
		return R.string.uart_about_text;
	}

	@Override
	protected UUID getFilterUUID() {
		return null; // not used
	}

	@Override
	public void send(final String text) {
		if (mServiceBinder != null)
			mServiceBinder.send(text);
	}

}
