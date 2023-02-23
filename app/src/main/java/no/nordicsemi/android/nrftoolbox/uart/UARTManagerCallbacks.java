package no.nordicsemi.android.nrftoolbox.uart;

import android.bluetooth.BluetoothDevice;
import no.nordicsemi.android.nrftoolbox.profile.BleManagerCallbacks;

public interface UARTManagerCallbacks extends BleManagerCallbacks {
	void onDataReceived(final BluetoothDevice device, final String data);
	void onDataSent(final BluetoothDevice device, final String data);
}
