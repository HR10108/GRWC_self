package no.nordicsemi.android.nrftoolbox.profile;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import androidx.annotation.StringRes;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import no.nordicsemi.android.log.ILogSession;
import no.nordicsemi.android.log.Logger;
import no.nordicsemi.android.nrftoolbox.error.GattError;
import no.nordicsemi.android.nrftoolbox.utility.DebugLogger;
import no.nordicsemi.android.nrftoolbox.utility.ParserUtils;

public abstract class BleManager<E extends BleManagerCallbacks> implements ILogger {
	private final static String TAG = "BleManager";

	private final static UUID CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

	private final static UUID BATTERY_SERVICE = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
	private final static UUID BATTERY_LEVEL_CHARACTERISTIC = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");

	private final static UUID GENERIC_ATTRIBUTE_SERVICE = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb");
	private final static UUID SERVICE_CHANGED_CHARACTERISTIC = UUID.fromString("00002A05-0000-1000-8000-00805f9b34fb");

	private final Object mLock = new Object();
	/**
	 * The log session or null if nRF Logger is not installed.
	 */
	protected ILogSession mLogSession;
	private final Context mContext;
	private final Handler mHandler;
	protected BluetoothDevice mBluetoothDevice;
	protected E mCallbacks;
	private BluetoothGatt mBluetoothGatt;
	private BleManagerGattCallback mGattCallback;
	/**
	 * This flag is set to false only when the {@link #shouldAutoConnect()} method returns true and the device got disconnected without calling {@link #disconnect()} method.
	 * If {@link #shouldAutoConnect()} returns false (default) this is always set to true.
	 */
	private boolean mUserDisconnected;
	/** Flag set to true when the device is connected. */
	private boolean mConnected;
	private int mConnectionState = BluetoothGatt.STATE_DISCONNECTED;
	/** Last received battery value or -1 if value wasn't received. */
	private int mBatteryValue = -1;

	//public static String EXTRAS_DATA = "No Data";

	private final BroadcastReceiver mBluetoothStateBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
			final int previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.STATE_OFF);

			final String stateString = "[Broadcast] Action received: " + BluetoothAdapter.ACTION_STATE_CHANGED + ", state changed to " + state2String(state);
			Logger.d(mLogSession, stateString);

			switch (state) {
				case BluetoothAdapter.STATE_TURNING_OFF:
				case BluetoothAdapter.STATE_OFF:
					if (mConnected && previousState != BluetoothAdapter.STATE_TURNING_OFF && previousState != BluetoothAdapter.STATE_OFF) {
						// The connection is killed by the system, no need to gently disconnect
						mGattCallback.notifyDeviceDisconnected(mBluetoothDevice);
						close();
					}
					break;
			}
		}

		private String state2String(final int state) {
			switch (state) {
				case BluetoothAdapter.STATE_TURNING_ON:
					return "TURNING ON";
				case BluetoothAdapter.STATE_ON:
					return "ON";
				case BluetoothAdapter.STATE_TURNING_OFF:
					return "TURNING OFF";
				case BluetoothAdapter.STATE_OFF:
					return "OFF";
				default:
					return "UNKNOWN (" + state + ")";
			}
		}
	};

	private BroadcastReceiver mBondingBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
			final int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);

			// Skip other devices
			if (mBluetoothGatt == null || !device.getAddress().equals(mBluetoothGatt.getDevice().getAddress()))
				return;

			Logger.d(mLogSession, "[Broadcast] Action received: " + BluetoothDevice.ACTION_BOND_STATE_CHANGED + ", bond state changed to: " + bondStateToString(bondState) + " (" + bondState + ")");
			DebugLogger.i(TAG, "Bond state changed for: " + device.getName() + " new state: " + bondState + " previous: " + previousBondState);

			switch (bondState) {
				case BluetoothDevice.BOND_BONDING:
					mCallbacks.onBondingRequired(device);
					break;
				case BluetoothDevice.BOND_BONDED:
					Logger.i(mLogSession, "Device bonded");
					mCallbacks.onBonded(device);

					// Start initializing again.
					// In fact, bonding forces additional, internal service discovery (at least on Nexus devices), so this method may safely be used to start this process again.
					Logger.v(mLogSession, "Discovering Services...");
					Logger.d(mLogSession, "gatt.discoverServices()");
					mBluetoothGatt.discoverServices();
					break;
			}
		}
	};

	private final BroadcastReceiver mPairingRequestBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

			// Skip other devices
			if (mBluetoothGatt == null || !device.getAddress().equals(mBluetoothGatt.getDevice().getAddress()))
				return;

			// String values are used as the constants are not available for Android 4.3.
			final int variant = intent.getIntExtra("android.bluetooth.device.extra.PAIRING_VARIANT"/*BluetoothDevice.EXTRA_PAIRING_VARIANT*/, 0);
			Logger.d(mLogSession, "[Broadcast] Action received: android.bluetooth.device.action.PAIRING_REQUEST"/*BluetoothDevice.ACTION_PAIRING_REQUEST*/ +
					", pairing variant: " + pairingVariantToString(variant) + " (" + variant + ")");

			// The API below is available for Android 4.4 or newer.

			// An app may set the PIN here or set pairing confirmation (depending on the variant) using:
			// device.setPin(new byte[] { '1', '2', '3', '4', '5', '6' });
			// device.setPairingConfirmation(true);
		}
	};

	public BleManager(final Context context) {
		mContext = context;
		mHandler = new Handler();
	}

	/**
	 * Returns the context that the manager was created with.
	 *
	 * @return the context
	 */
	protected Context getContext() {
		return mContext;
	}

	/**
	 * This method must return the gatt callback used by the manager.
	 * This method must not create a new gatt callback each time it is being invoked, but rather return a single object.
	 *
	 * @return the gatt callback object
	 */
	protected abstract BleManagerGattCallback getGattCallback();

	/**
	 * Returns whether to connect to the remote device just once (false) or to add the address to white list of devices
	 * that will be automatically connect as soon as they become available (true). In the latter case, if
	 * Bluetooth adapter is enabled, Android scans periodically for devices from the white list and if a advertising packet
	 * is received from such, it tries to connect to it. When the connection is lost, the system will keep trying to reconnect
	 * to it in. If true is returned, and the connection to the device is lost the {@link BleManagerCallbacks#onLinklossOccur(BluetoothDevice)}
	 * callback is called instead of {@link BleManagerCallbacks#onDeviceDisconnected(BluetoothDevice)}.
	 * <p>This feature works much better on newer Android phone models and many not work on older phones.</p>
	 * <p>This method should only be used with bonded devices, as otherwise the device may change it's address.
	 * It will however work also with non-bonded devices with private static address. A connection attempt to
	 * a device with private resolvable address will fail.</p>
	 *
	 * @return autoConnect flag value
	 */
	protected boolean shouldAutoConnect() {
		return false;
	}

	/**
	 * Connects to the Bluetooth Smart device.
	 *
	 * @param device a device to connect to
	 */
	public void connect(final BluetoothDevice device) {
		if (mConnected)
			return;

		synchronized (mLock) {
			if (mBluetoothGatt != null) {
				Logger.d(mLogSession, "gatt.close()");
				mBluetoothGatt.close();
				mBluetoothGatt = null;
			} else {
				// Register bonding broadcast receiver
				mContext.registerReceiver(mBluetoothStateBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
				mContext.registerReceiver(mBondingBroadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
				mContext.registerReceiver(mPairingRequestBroadcastReceiver, new IntentFilter("android.bluetooth.device.action.PAIRING_REQUEST"/*BluetoothDevice.ACTION_PAIRING_REQUEST*/));
			}
		}

		final boolean autoConnect = shouldAutoConnect();
		mUserDisconnected = !autoConnect; // We will receive Linkloss events only when the device is connected with autoConnect=true
		mBluetoothDevice = device;
		Logger.v(mLogSession, "Connecting...");
		mConnectionState = BluetoothGatt.STATE_CONNECTING;
		mCallbacks.onDeviceConnecting(device);
		Logger.d(mLogSession, "gatt = device.connectGatt(autoConnect = " + autoConnect + ")");
		mBluetoothGatt = device.connectGatt(mContext, autoConnect, mGattCallback = getGattCallback());
	}

	/**
	 * Disconnects from the device. Does nothing if not connected.
	 * @return true if device is to be disconnected. False if it was already disconnected.
	 */
	public boolean disconnect() {
		mUserDisconnected = true;

		if (mConnected && mBluetoothGatt != null) {
			Logger.v(mLogSession, "Disconnecting...");
			mConnectionState = BluetoothGatt.STATE_DISCONNECTING;
			mCallbacks.onDeviceDisconnecting(mBluetoothGatt.getDevice());
			Logger.d(mLogSession, "gatt.disconnect()");
			mBluetoothGatt.disconnect();
			return true;
		}
		return false;
	}

	/**
	 * This method returns true if the device is connected. Services could have not been discovered yet.
	 */
	public boolean isConnected() {
		return mConnected;
	}

	/**
	 * Method returns the connection state:
	 * {@link BluetoothGatt#STATE_CONNECTING STATE_CONNECTING},
	 * {@link BluetoothGatt#STATE_CONNECTED STATE_CONNECTED},
	 * {@link BluetoothGatt#STATE_DISCONNECTING STATE_DISCONNECTING},
	 * {@link BluetoothGatt#STATE_DISCONNECTED STATE_DISCONNECTED}
	 * @return the connection state
	 */
	public int getConnectionState() {
		return mConnectionState;
	}

	/**
	 * Returns the last received value of Battery Level characteristic, or -1 if such does not exist, hasn't been read or notification wasn't received yet.
	 * @return the last battery level value in percent
	 */
	public int getBatteryValue() {
		return mBatteryValue;
	}

	/**
	 * Closes and releases resources. May be also used to unregister broadcast listeners.
	 */
	public void close() {
		try {
			mContext.unregisterReceiver(mBluetoothStateBroadcastReceiver);
			mContext.unregisterReceiver(mBondingBroadcastReceiver);
			mContext.unregisterReceiver(mPairingRequestBroadcastReceiver);
		} catch (Exception e) {
			// the receiver must have been not registered or unregistered before
		}
		synchronized (mLock) {
			if (mBluetoothGatt != null) {
				Logger.d(mLogSession, "gatt.close()");
				mBluetoothGatt.close();
				mBluetoothGatt = null;
			}
			mConnected = false;
			mConnectionState = BluetoothGatt.STATE_DISCONNECTED;
			mGattCallback = null;
			mBluetoothDevice = null;
		}
	}

	/**
	 * Sets the optional log session. This session will be used to log Bluetooth events.
	 * The logs may be viewed using the nRF Logger application: https://play.google.com/store/apps/details?id=no.nordicsemi.android.log
	 * Since nRF Logger Library v2.0 an app may define it's own log provider. Use {@link BleProfileServiceReadyActivity#getLocalAuthorityLogger()} to define local log URI.
	 * NOTE: nRF Logger must be installed prior to nRF Toolbox as it defines the required permission which is used by nRF Toolbox.
	 *
	 * @param session the session, or null if nRF Logger is not installed.
	 */
	public void setLogger(final ILogSession session) {
		mLogSession = session;
	}

	@Override
	public void log(final int level, final String message) {
		Logger.log(mLogSession, level, message);
	}

	@Override
	public void log(final int level, @StringRes final int messageRes, final Object... params) {
		Logger.log(mLogSession, level, messageRes, params);
	}

	/**
	 * Sets the manager callback listener
	 *
	 * @param callbacks the callback listener
	 */
	public void setGattCallbacks(E callbacks) {
		mCallbacks = callbacks;
	}

	/**
	 * When the device is bonded and has the Generic Attribute service and the Service Changed characteristic this method enables indications on this characteristic.
	 * In case one of the requirements is not fulfilled this method returns <code>false</code>.
	 *
	 * @return <code>true</code> when the request has been sent, <code>false</code> when the device is not bonded, does not have the Generic Attribute service, the GA service does not have
	 * the Service Changed characteristic or this characteristic does not have the CCCD.
	 */
	private boolean ensureServiceChangedEnabled() {
		final BluetoothGatt gatt = mBluetoothGatt;
		if (gatt == null)
			return false;

		// The Service Changed indications have sense only on bonded devices
		final BluetoothDevice device = gatt.getDevice();
		if (device.getBondState() != BluetoothDevice.BOND_BONDED)
			return false;

		final BluetoothGattService gaService = gatt.getService(GENERIC_ATTRIBUTE_SERVICE);
		if (gaService == null)
			return false;

		final BluetoothGattCharacteristic scCharacteristic = gaService.getCharacteristic(SERVICE_CHANGED_CHARACTERISTIC);
		if (scCharacteristic == null)
			return false;

		Logger.i(mLogSession, "Service Changed characteristic found on a bonded device");
		return enableIndications(scCharacteristic);
	}

	/**
	 * Enables notifications on given characteristic.
	 *
	 * @return true is the request has been enqueued
	 */
	protected final boolean enableNotifications(final BluetoothGattCharacteristic characteristic) {
		return enqueue(Request.newEnableNotificationsRequest(characteristic));
	}

	private boolean internalEnableNotifications(final BluetoothGattCharacteristic characteristic) {
		final BluetoothGatt gatt = mBluetoothGatt;
		if (gatt == null || characteristic == null)
			return false;

		// Check characteristic property
		final int properties = characteristic.getProperties();
		if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0)
			return false;

		Logger.d(mLogSession, "gatt.setCharacteristicNotification(" + characteristic.getUuid() + ", true)");
		gatt.setCharacteristicNotification(characteristic, true);
		final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
		if (descriptor != null) {
			descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			Logger.v(mLogSession, "Enabling notifications for " + characteristic.getUuid());
			Logger.d(mLogSession, "gatt.writeDescriptor(" + CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID + ", value=0x01-00)");
			return gatt.writeDescriptor(descriptor);
		}
		return false;
	}

	/**
	 * Enables indications on given characteristic.
	 *
	 * @return true is the request has been enqueued
	 */
	protected final boolean enableIndications(final BluetoothGattCharacteristic characteristic) {
		return enqueue(Request.newEnableIndicationsRequest(characteristic));
	}

	private boolean internalEnableIndications(final BluetoothGattCharacteristic characteristic) {
		final BluetoothGatt gatt = mBluetoothGatt;
		if (gatt == null || characteristic == null)
			return false;

		// Check characteristic property
		final int properties = characteristic.getProperties();
		if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) == 0)
			return false;

		Logger.d(mLogSession, "gatt.setCharacteristicNotification(" + characteristic.getUuid() + ", true)");
		gatt.setCharacteristicNotification(characteristic, true);
		final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
		if (descriptor != null) {
			descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
			Logger.v(mLogSession, "Enabling indications for " + characteristic.getUuid());
			Logger.d(mLogSession, "gatt.writeDescriptor(" + CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID + ", value=0x02-00)");
			return gatt.writeDescriptor(descriptor);
		}
		return false;
	}

	/**
	 * Sends the read request to the given characteristic.
	 *
	 * @param characteristic the characteristic to read
	 * @return true if request has been enqueued
	 */
	protected final boolean readCharacteristic(final BluetoothGattCharacteristic characteristic) {
		return enqueue(Request.newReadRequest(characteristic));
	}

	private boolean internalReadCharacteristic(final BluetoothGattCharacteristic characteristic) {
		final BluetoothGatt gatt = mBluetoothGatt;
		if (gatt == null || characteristic == null)
			return false;

		// Check characteristic property
		final int properties = characteristic.getProperties();
		if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) == 0)
			return false;

		Logger.v(mLogSession, "Reading characteristic " + characteristic.getUuid());
		Logger.d(mLogSession, "gatt.readCharacteristic(" + characteristic.getUuid() + ")");
		return gatt.readCharacteristic(characteristic);
	}

	/**
	 * Writes the characteristic value to the given characteristic.
	 *
	 * @param characteristic the characteristic to write to
	 * @return true if request has been enqueued
	 */
	protected final boolean writeCharacteristic(final BluetoothGattCharacteristic characteristic) {
		return enqueue(Request.newWriteRequest(characteristic, characteristic.getValue()));
	}

	private boolean internalWriteCharacteristic(final BluetoothGattCharacteristic characteristic) {
		final BluetoothGatt gatt = mBluetoothGatt;
		if (gatt == null || characteristic == null)
			return false;

		// Check characteristic property
		final int properties = characteristic.getProperties();
		if ((properties & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0)
			return false;

		Logger.v(mLogSession, "Writing characteristic " + characteristic.getUuid() + " (" + getWriteType(characteristic.getWriteType()) + ")");
		Logger.d(mLogSession, "gatt.writeCharacteristic(" + characteristic.getUuid() + ")");
		return gatt.writeCharacteristic(characteristic);
	}

	/**
	 * Sends the read request to the given descriptor.
	 *
	 * @param descriptor the descriptor to read
	 * @return true if request has been enqueued
	 */
	protected final boolean readDescriptor(final BluetoothGattDescriptor descriptor) {
		return enqueue(Request.newReadRequest(descriptor));
	}

	private boolean internalReadDescriptor(final BluetoothGattDescriptor descriptor) {
		final BluetoothGatt gatt = mBluetoothGatt;
		if (gatt == null || descriptor == null)
			return false;

		Logger.v(mLogSession, "Reading descriptor " + descriptor.getUuid());
		Logger.d(mLogSession, "gatt.readDescriptor(" + descriptor.getUuid() + ")");
		return gatt.readDescriptor(descriptor);
	}

	/**
	 * Writes the descriptor value to the given descriptor.
	 *
	 * @param descriptor the descriptor to write to
	 * @return true if request has been enqueued
	 */
	protected final boolean writeDescriptor(final BluetoothGattDescriptor descriptor) {
		return enqueue(Request.newWriteRequest(descriptor, descriptor.getValue()));
	}

	private boolean internalWriteDescriptor(final BluetoothGattDescriptor descriptor) {
		final BluetoothGatt gatt = mBluetoothGatt;
		if (gatt == null || descriptor == null)
			return false;

		Logger.v(mLogSession, "Writing descriptor " + descriptor.getUuid());
		Logger.d(mLogSession, "gatt.writeDescriptor(" + descriptor.getUuid() + ")");
		// There was a bug in Android up to 6.0 where the descriptor was written using parent characteristic write type, instead of always Write With Response,
		// as the spec says.
		final BluetoothGattCharacteristic parentCharacteristic = descriptor.getCharacteristic();
		final int originalWriteType = parentCharacteristic.getWriteType();
		parentCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
		final boolean result = gatt.writeDescriptor(descriptor);
		parentCharacteristic.setWriteType(originalWriteType);
		return result;
	}

	/**
	 * Reads the battery level from the device.
	 *
	 * @return true if request has been enqueued
	 */
	public final boolean readBatteryLevel() {
		return enqueue(Request.newReadBatteryLevelRequest());
	}

	private boolean internalReadBatteryLevel() {
		final BluetoothGatt gatt = mBluetoothGatt;
		if (gatt == null)
			return false;

		final BluetoothGattService batteryService = gatt.getService(BATTERY_SERVICE);
		if (batteryService == null)
			return false;

		final BluetoothGattCharacteristic batteryLevelCharacteristic = batteryService.getCharacteristic(BATTERY_LEVEL_CHARACTERISTIC);
		if (batteryLevelCharacteristic == null)
			return false;

		// Check characteristic property
		final int properties = batteryLevelCharacteristic.getProperties();
		if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) == 0)
			return false;

		Logger.a(mLogSession, "Reading battery level...");
		return internalReadCharacteristic(batteryLevelCharacteristic);
	}

	/**
	 * This method tries to enable notifications on the Battery Level characteristic.
	 *
	 * @param enable <code>true</code> to enable battery notifications, false to disable
	 * @return true if request has been enqueued
	 */
	public final boolean setBatteryNotifications(final boolean enable) {
		if (enable)
			return enqueue(Request.newEnableBatteryLevelNotificationsRequest());
		else
			return enqueue(Request.newDisableBatteryLevelNotificationsRequest());
	}

	private boolean internalSetBatteryNotifications(final boolean enable) {
		final BluetoothGatt gatt = mBluetoothGatt;
		if (gatt == null) {
			return false;
		}

		final BluetoothGattService batteryService = gatt.getService(BATTERY_SERVICE);
		if (batteryService == null)
			return false;

		final BluetoothGattCharacteristic batteryLevelCharacteristic = batteryService.getCharacteristic(BATTERY_LEVEL_CHARACTERISTIC);
		if (batteryLevelCharacteristic == null)
			return false;

		// Check characteristic property
		final int properties = batteryLevelCharacteristic.getProperties();
		if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0)
			return false;

		gatt.setCharacteristicNotification(batteryLevelCharacteristic, enable);
		final BluetoothGattDescriptor descriptor = batteryLevelCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
		if (descriptor != null) {
			if (enable) {
				descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
				Logger.a(mLogSession, "Enabling battery level notifications...");
				Logger.v(mLogSession, "Enabling notifications for " + BATTERY_LEVEL_CHARACTERISTIC);
				Logger.d(mLogSession, "gatt.writeDescriptor(" + CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID + ", value=0x0100)");
			} else {
				descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
				Logger.a(mLogSession, "Disabling battery level notifications...");
				Logger.v(mLogSession, "Disabling notifications for " + BATTERY_LEVEL_CHARACTERISTIC);
				Logger.d(mLogSession, "gatt.writeDescriptor(" + CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID + ", value=0x0000)");
			}
			return gatt.writeDescriptor(descriptor);
		}
		return false;
	}

	/**
	 * Enqueues a new request. The request will be handled immediately if there is no operation in progress,
	 * or automatically after the last enqueued one will finish.
	 * <p>This method should be used to read and write data from the target device as it ensures that the last operation has finished
	 * before a new one will be called.</p>
	 * @param request new request to be performed
	 * @return true if request has been enqueued, false if the device is not connected
	 */
	public boolean enqueue(final Request request) {
		if (mGattCallback != null) {
			// Add the new task to the end of the queue
			mGattCallback.mTaskQueue.add(request);
			mGattCallback.nextRequest();
			return true;
		}
		return false;
	}

	/**
	 * On Android, when multiple BLE operations needs to be done, it is required to wait for a proper
	 * {@link android.bluetooth.BluetoothGattCallback BluetoothGattCallback} callback before calling
	 * another operation. In order to make BLE operations easier the BleManager allows to enqueue a request
	 * containing all data necessary for a given operation. Requests are performed one after another until the
	 * queue is empty. Use static methods from below to instantiate a request and then enqueue them using {@link #enqueue(Request)}.
	 */
	protected static final class Request {
		private enum Type {
			WRITE,
			READ,
			WRITE_DESCRIPTOR,
			READ_DESCRIPTOR,
			ENABLE_NOTIFICATIONS,
			ENABLE_INDICATIONS,
			READ_BATTERY_LEVEL,
			ENABLE_BATTERY_LEVEL_NOTIFICATIONS,
			DISABLE_BATTERY_LEVEL_NOTIFICATIONS,
			ENABLE_SERVICE_CHANGED_INDICATIONS,
		}

		private final Type type;
		private final BluetoothGattCharacteristic characteristic;
		private final BluetoothGattDescriptor descriptor;
		private final byte[] value;
		private final int writeType;

		private Request(final Type type) {
			this.type = type;
			this.characteristic = null;
			this.descriptor = null;
			this.value = null;
			this.writeType = 0;
		}

		private Request(final Type type, final BluetoothGattCharacteristic characteristic) {
			this.type = type;
			this.characteristic = characteristic;
			this.descriptor = null;
			this.value = null;
			this.writeType = 0;
		}

		private Request(final Type type, final BluetoothGattCharacteristic characteristic, final int writeType, final byte[] value, final int offset, final int length) {
			this.type = type;
			this.characteristic = characteristic;
			this.descriptor = null;
			this.value = copy(value, offset, length);
			this.writeType = writeType;
		}

		private Request(final Type type, final BluetoothGattDescriptor descriptor) {
			this.type = type;
			this.characteristic = null;
			this.descriptor = descriptor;
			this.value = null;
			this.writeType = 0;
		}

		private Request(final Type type, final BluetoothGattDescriptor descriptor, final byte[] value, final int offset, final int length) {
			this.type = type;
			this.characteristic = null;
			this.descriptor = descriptor;
			this.value = copy(value, offset, length);
			this.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
		}

		private static byte[] copy(final byte[] value, final int offset, final int length) {
			if (value == null || offset > value.length)
				return null;
			final int maxLength = Math.min(value.length - offset, length);
			final byte[] copy = new byte[maxLength];
			System.arraycopy(value, offset, copy, 0, maxLength);
			return copy;
		}

		/**
		 * Creates new Read Characteristic request. The request will not be executed if given characteristic
		 * is null or does not have READ property. After the operation is complete a proper callback will be invoked.
		 * @param characteristic characteristic to be read
		 * @return the new request that can be enqueued using {@link #enqueue(Request)} method.
		 */
		public static Request newReadRequest(final BluetoothGattCharacteristic characteristic) {
			return new Request(Type.READ, characteristic);
		}

		/**
		 * Creates new Write Characteristic request. The request will not be executed if given characteristic
		 * is null or does not have WRITE property. After the operation is complete a proper callback will be invoked.
		 * @param characteristic characteristic to be written
		 * @param value value to be written. The array is copied into another buffer so it's safe to reuse the array again.
		 * @return the new request that can be enqueued using {@link #enqueue(Request)} method.
		 */
		public static Request newWriteRequest(final BluetoothGattCharacteristic characteristic, final byte[] value) {
			return new Request(Type.WRITE, characteristic, characteristic.getWriteType(), value, 0, value != null ? value.length : 0);
		}

		/**
		 * Creates new Write Characteristic request. The request will not be executed if given characteristic
		 * is null or does not have WRITE property. After the operation is complete a proper callback will be invoked.
		 * @param characteristic characteristic to be written
		 * @param value value to be written. The array is copied into another buffer so it's safe to reuse the array again.
		 * @param writeType write type to be used, one of {@link BluetoothGattCharacteristic#WRITE_TYPE_DEFAULT}, {@link BluetoothGattCharacteristic#WRITE_TYPE_NO_RESPONSE}.
		 * @return the new request that can be enqueued using {@link #enqueue(Request)} method.
		 */
		public static Request newWriteRequest(final BluetoothGattCharacteristic characteristic, final byte[] value, final int writeType) {
			return new Request(Type.WRITE, characteristic, writeType, value, 0, value != null ? value.length : 0);
		}

		/**
		 * Creates new Write Characteristic request. The request will not be executed if given characteristic
		 * is null or does not have WRITE property. After the operation is complete a proper callback will be invoked.
		 * @param characteristic characteristic to be written
		 * @param value value to be written. The array is copied into another buffer so it's safe to reuse the array again.
		 * @param offset the offset from which value has to be copied
		 * @param length number of bytes to be copied from the value buffer
		 * @return the new request that can be enqueued using {@link #enqueue(Request)} method.
		 */
		public static Request newWriteRequest(final BluetoothGattCharacteristic characteristic, final byte[] value, final int offset, final int length) {
			return new Request(Type.WRITE, characteristic, characteristic.getWriteType(), value, offset, length);
		}

		/**
		 * Creates new Write Characteristic request. The request will not be executed if given characteristic
		 * is null or does not have WRITE property. After the operation is complete a proper callback will be invoked.
		 * @param characteristic characteristic to be written
		 * @param value value to be written. The array is copied into another buffer so it's safe to reuse the array again.
		 * @param offset the offset from which value has to be copied
		 * @param length number of bytes to be copied from the value buffer
		 * @param writeType write type to be used, one of {@link BluetoothGattCharacteristic#WRITE_TYPE_DEFAULT}, {@link BluetoothGattCharacteristic#WRITE_TYPE_NO_RESPONSE}.
		 * @return the new request that can be enqueued using {@link #enqueue(Request)} method.
		 */
		public static Request newWriteRequest(final BluetoothGattCharacteristic characteristic, final byte[] value, final int offset, final int length, final int writeType) {
			return new Request(Type.WRITE, characteristic, writeType, value, offset, length);
		}

		/**
		 * Creates new Read Descriptor request. The request will not be executed if given descriptor
		 * is null. After the operation is complete a proper callback will be invoked.
		 * @param descriptor descriptor to be read
		 * @return the new request that can be enqueued using {@link #enqueue(Request)} method.
		 */
		public static Request newReadRequest(final BluetoothGattDescriptor descriptor) {
			return new Request(Type.READ_DESCRIPTOR, descriptor);
		}

		/**
		 * Creates new Write Descriptor request. The request will not be executed if given descriptor
		 * is null. After the operation is complete a proper callback will be invoked.
		 * @param descriptor descriptor to be written
		 * @param value value to be written. The array is copied into another buffer so it's safe to reuse the array again.
		 * @return the new request that can be enqueued using {@link #enqueue(Request)} method.
		 */
		public static Request newWriteRequest(final BluetoothGattDescriptor descriptor, final byte[] value) {
			return new Request(Type.WRITE_DESCRIPTOR, descriptor, value, 0, value != null ? value.length : 0);
		}

		/**
		 * Creates new Write Descriptor request. The request will not be executed if given descriptor
		 * is null. After the operation is complete a proper callback will be invoked.
		 * @param descriptor descriptor to be written
		 * @param value value to be written. The array is copied into another buffer so it's safe to reuse the array again.
		 * @param offset the offset from which value has to be copied
		 * @param length number of bytes to be copied from the value buffer
		 * @return the new request that can be enqueued using {@link #enqueue(Request)} method.
		 */
		public static Request newWriteRequest(final BluetoothGattDescriptor descriptor, final byte[] value, final int offset, final int length) {
			return new Request(Type.WRITE_DESCRIPTOR, descriptor, value, offset, length);
		}

		/**
		 * Creates new Enable Notification request. The request will not be executed if given characteristic
		 * is null, does not have NOTIFY property or the CCCD. After the operation is complete a proper callback will be invoked.
		 * @param characteristic characteristic to have notifications enabled
		 * @return the new request that can be enqueued using {@link #enqueue(Request)} method.
		 */
		public static Request newEnableNotificationsRequest(final BluetoothGattCharacteristic characteristic) {
			return new Request(Type.ENABLE_NOTIFICATIONS, characteristic);
		}

		/**
		 * Creates new Enable Indications request. The request will not be executed if given characteristic
		 * is null, does not have INDICATE property or the CCCD. After the operation is complete a proper callback will be invoked.
		 * @param characteristic characteristic to have indications enabled
		 * @return the new request that can be enqueued using {@link #enqueue(Request)} method.
		 */
		public static Request newEnableIndicationsRequest(final BluetoothGattCharacteristic characteristic) {
			return new Request(Type.ENABLE_INDICATIONS, characteristic);
		}

		/**
		 * Reads the first found Battery Level characteristic value from the first found Battery Service.
		 * If any of them is not found, or the characteristic does not have the READ property this operation will not execute.
		 * @return the new request that can be enqueued using {@link #enqueue(Request)} method.
		 */
		public static Request newReadBatteryLevelRequest() {
			return new Request(Type.READ_BATTERY_LEVEL); // the first Battery Level char from the first Battery Service is used
		}

		/**
		 * Enables notifications on the first found Battery Level characteristic from the first found Battery Service.
		 * If any of them is not found, or the characteristic does not have the NOTIFY property this operation will not execute.
		 * @return the new request that can be enqueued using {@link #enqueue(Request)} method.
		 */
		public static Request newEnableBatteryLevelNotificationsRequest() {
			return new Request(Type.ENABLE_BATTERY_LEVEL_NOTIFICATIONS); // the first Battery Level char from the first Battery Service is used
		}

		/**
		 * Disables notifications on the first found Battery Level characteristic from the first found Battery Service.
		 * If any of them is not found, or the characteristic does not have the NOTIFY property this operation will not execute.
		 * @return the new request that can be enqueued using {@link #enqueue(Request)} method.
		 */
		public static Request newDisableBatteryLevelNotificationsRequest() {
			return new Request(Type.DISABLE_BATTERY_LEVEL_NOTIFICATIONS); // the first Battery Level char from the first Battery Service is used
		}

		/**
		 * Enables indications on Service Changed characteristic if such exists in the Generic Attribute service.
		 * It is required to enable those notifications on bonded devices on older Android versions to be
		 * informed about attributes changes. Android 7+ (or 6+) handles this automatically and no action is required.
		 * @return the new request that can be enqueued using {@link #enqueue(Request)} method.
		 */
		private static Request newEnableServiceChangedIndicationsRequest() {
			return new Request(Type.ENABLE_SERVICE_CHANGED_INDICATIONS); // the only Service Changed char is used (if such exists)
		}
	}

	protected abstract class BleManagerGattCallback extends BluetoothGattCallback {
		private final static String ERROR_CONNECTION_STATE_CHANGE = "Error on connection state change";
		private final static String ERROR_DISCOVERY_SERVICE = "Error on discovering services";
		private final static String ERROR_AUTH_ERROR_WHILE_BONDED = "Phone has lost bonding information";
		private final static String ERROR_READ_CHARACTERISTIC = "Error on reading characteristic";
		private final static String ERROR_WRITE_CHARACTERISTIC = "Error on writing characteristic";
		private final static String ERROR_READ_DESCRIPTOR = "Error on reading descriptor";
		private final static String ERROR_WRITE_DESCRIPTOR = "Error on writing descriptor";

		private final Queue<Request> mTaskQueue = new LinkedList<>();
		private Deque<Request> mInitQueue;
		private boolean mInitInProgress;
		private boolean mOperationInProgress;

		/**
		 * This method should return <code>true</code> when the gatt device supports the required services.
		 *
		 * @param gatt the gatt device with services discovered
		 * @return <code>true</code> when the device has teh required service
		 */
		protected abstract boolean isRequiredServiceSupported(final BluetoothGatt gatt);

		/**
		 * This method should return <code>true</code> when the gatt device supports the optional services.
		 * The default implementation returns <code>false</code>.
		 *
		 * @param gatt the gatt device with services discovered
		 * @return <code>true</code> when the device has teh optional service
		 */
		protected boolean isOptionalServiceSupported(final BluetoothGatt gatt) {
			return false;
		}

		/**
		 * This method should return a list of requests needed to initialize the profile.
		 * Enabling Service Change indications for bonded devices and reading the Battery Level value and enabling Battery Level notifications
		 * is handled before executing this queue. The queue should not have requests that are not available, e.g. should not
		 * read an optional service when it is not supported by the connected device.
		 * <p>This method is called when the services has been discovered and the device is supported (has required service).</p>
		 *
		 * @param gatt the gatt device with services discovered
		 * @return the queue of requests
		 */
		protected abstract Deque<Request> initGatt(final BluetoothGatt gatt);

		/**
		 * Called then the initialization queue is complete.
		 */
		protected void onDeviceReady() {
			mCallbacks.onDeviceReady(mBluetoothGatt.getDevice());
		}

		/**
		 * This method should nullify all services and characteristics of the device.
		 * It's called when the device is no longer connected, either due to user action
		 * or a link loss.
		 */
		protected abstract void onDeviceDisconnected();

		private void notifyDeviceDisconnected(final BluetoothDevice device) {
			mConnected = false;
			mConnectionState = BluetoothGatt.STATE_DISCONNECTED;
			if (mUserDisconnected) {
				Logger.i(mLogSession, "Disconnected");
				mCallbacks.onDeviceDisconnected(device);
				close();
			} else {
				Logger.w(mLogSession, "Connection lost");
				mCallbacks.onLinklossOccur(device);
				// We are not closing the connection here as the device should try to reconnect automatically.
				// This may be only called when the shouldAutoConnect() method returned true.
			}
			onDeviceDisconnected();
		}

		/**
		 * Callback reporting the result of a characteristic read operation.
		 *
		 * @param gatt GATT client
		 * @param characteristic Characteristic that was read from the associated remote device.
		 */
		protected void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			// do nothing
		}

		/**
		 * Callback indicating the result of a characteristic write operation.
		 * <p>If this callback is invoked while a reliable write transaction is
		 * in progress, the value of the characteristic represents the value
		 * reported by the remote device. An application should compare this
		 * value to the desired value to be written. If the values don't match,
		 * the application must abort the reliable write transaction.
		 *
		 * @param gatt GATT client
		 * @param characteristic Characteristic that was written to the associated remote device.
		 */
		protected void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			// do nothing
		}

		/**
		 * Callback reporting the result of a descriptor read operation.
		 *
		 * @param gatt GATT client
		 * @param descriptor Descriptor that was read from the associated remote device.
		 */
		protected void onDescriptorRead(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor) {
			// do nothing
		}

		/**
		 * Callback indicating the result of a descriptor write operation.
		 * <p>If this callback is invoked while a reliable write transaction is in progress,
		 * the value of the characteristic represents the value reported by the remote device.
		 * An application should compare this value to the desired value to be written.
		 * If the values don't match, the application must abort the reliable write transaction.
		 *
		 * @param gatt GATT client
		 * @param descriptor Descriptor that was written to the associated remote device.
		 */
		protected void onDescriptorWrite(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor) {
			// do nothing
		}

		/**
		 * Callback reporting the value of Battery Level characteristic which could have
		 * been received by Read or Notify operations.
		 *
		 * @param gatt GATT client
		 * @param value the battery value in percent
		 */
		protected void onBatteryValueReceived(final BluetoothGatt gatt, final int value) {
			// do nothing
		}

		/**
		 * Callback indicating a notification has been received.
		 * @param gatt GATT client
		 * @param characteristic Characteristic from which the notification came.
		 */
		protected void onCharacteristicNotified(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			// do nothing
		}

		/**
		 * Callback indicating an indication has been received.
		 * @param gatt GATT client
		 * @param characteristic Characteristic from which the indication came.
		 */
		protected void onCharacteristicIndicated(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			// do nothing
		}

		private void onError(final BluetoothDevice device, final String message, final int errorCode) {
			Logger.e(mLogSession, "Error (0x" + Integer.toHexString(errorCode) + "): " + GattError.parse(errorCode));
			mCallbacks.onError(device, message, errorCode);
		}

		@Override
		public final void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
			Logger.d(mLogSession, "[Callback] Connection state changed with status: " + status + " and new state: " + newState + " (" + stateToString(newState) + ")");

			if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
				// Notify the parent activity/service
				Logger.i(mLogSession, "Connected to " + gatt.getDevice().getAddress());
				mConnected = true;
				mConnectionState = BluetoothGatt.STATE_CONNECTED;
				mCallbacks.onDeviceConnected(gatt.getDevice());
				/*
				 * The onConnectionStateChange event is triggered just after the Android connects to a device.
				 * In case of bonded devices, the encryption is reestablished AFTER this callback is called.
				 * Moreover, when the device has Service Changed indication enabled, and the list of services has changed (e.g. using the DFU),
				 * the indication is received few milliseconds later, depending on the connection interval.
				 * When received, Android will start performing a service discovery operation itself, internally.
				 *
				 * If the mBluetoothGatt.discoverServices() method would be invoked here, if would returned cached services,
				 * as the SC indication wouldn't be received yet.
				 * Therefore we have to postpone the service discovery operation until we are (almost, as there is no such callback) sure, that it had to be handled.
				 * Our tests has shown that 600 ms is enough. It is important to call it AFTER receiving the SC indication, but not necessarily
				 * after Android finishes the internal service discovery.
				 *
				 * NOTE: This applies only for bonded devices with Service Changed characteristic, but to be sure we will postpone
				 * service discovery for all devices.
				 */
				mHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
						// Some proximity tags (e.g. nRF PROXIMITY) initialize bonding automatically when connected.
						if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_BONDING) {
							Logger.v(mLogSession, "Discovering Services...");
							Logger.d(mLogSession, "gatt.discoverServices()");
							gatt.discoverServices();
						}
					}
				}, 600);
			} else {
				if (newState == BluetoothProfile.STATE_DISCONNECTED) {
					if (status != BluetoothGatt.GATT_SUCCESS)
						Logger.w(mLogSession, "Error: (0x" + Integer.toHexString(status) + "): " + GattError.parseConnectionError(status));

					mOperationInProgress = true; // no more calls are possible
					notifyDeviceDisconnected(gatt.getDevice());
					return;
				}

				// TODO Should the disconnect method be called or the connection is still valid? Does this ever happen?
				Logger.e(mLogSession, "Error (0x" + Integer.toHexString(status) + "): " + GattError.parseConnectionError(status));
				mCallbacks.onError(gatt.getDevice(), ERROR_CONNECTION_STATE_CHANGE, status);
			}
		}

		@Override
		public final void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Logger.i(mLogSession, "Services Discovered");
				if (isRequiredServiceSupported(gatt)) {
					Logger.v(mLogSession, "Primary service found");
					final boolean optionalServicesFound = isOptionalServiceSupported(gatt);
					if (optionalServicesFound)
						Logger.v(mLogSession, "Secondary service found");

					// Notify the parent activity
					mCallbacks.onServicesDiscovered(gatt.getDevice(), optionalServicesFound);

					// Obtain the queue of initialization requests
					mInitInProgress = true;
					mInitQueue = initGatt(gatt);

					// Before we start executing the initialization queue some other tasks need to be done.
					if (mInitQueue == null)
						mInitQueue = new LinkedList<>();

					// Note, that operations are added in reverse order to the front of the queue.

					// 3. Enable Battery Level notifications if required (if this char. does not exist, this operation will be skipped)
					if (mCallbacks.shouldEnableBatteryLevelNotifications(gatt.getDevice()))
						mInitQueue.addFirst(Request.newEnableBatteryLevelNotificationsRequest());
					// 2. Read Battery Level characteristic (if such does not exist, this will be skipped)
					mInitQueue.addFirst(Request.newReadBatteryLevelRequest());
					// 1. On devices running Android 4.3-6.0 the Service Changed characteristic needs to be enabled by the app (for bonded devices)
					if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
						mInitQueue.addFirst(Request.newEnableServiceChangedIndicationsRequest());

					mOperationInProgress = false;
					nextRequest();
				} else {
					Logger.w(mLogSession, "Device is not supported");
					mCallbacks.onDeviceNotSupported(gatt.getDevice());
					disconnect();
				}
			} else {
				DebugLogger.e(TAG, "onServicesDiscovered error " + status);
				onError(gatt.getDevice(), ERROR_DISCOVERY_SERVICE, status);
			}
		}

		@Override
		public final void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Logger.i(mLogSession, "Read Response received from " + characteristic.getUuid() + ", value: " + ParserUtils.parse(characteristic));

				if (isBatteryLevelCharacteristic(characteristic)) {
					final int batteryValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
					Logger.a(mLogSession, "Battery level received: " + batteryValue + "%");
					mBatteryValue = batteryValue;
					onBatteryValueReceived(gatt, batteryValue);
					mCallbacks.onBatteryValueReceived(gatt.getDevice(), batteryValue);
				} else {
					// The value has been read. Notify the manager and proceed with the initialization queue.
					onCharacteristicRead(gatt, characteristic);
				}
				mOperationInProgress = false;
				nextRequest();
			} else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
				if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_NONE) {
					// This should never happen but it used to: http://stackoverflow.com/a/20093695/2115352
					DebugLogger.w(TAG, ERROR_AUTH_ERROR_WHILE_BONDED);
					mCallbacks.onError(gatt.getDevice(), ERROR_AUTH_ERROR_WHILE_BONDED, status);
				}
			} else {
				DebugLogger.e(TAG, "onCharacteristicRead error " + status);
				onError(gatt.getDevice(), ERROR_READ_CHARACTERISTIC, status);
			}
		}

		@Override
		public final void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Logger.i(mLogSession, "Data written to " + characteristic.getUuid() + ", value: " + ParserUtils.parse(characteristic));
				// The value has been written. Notify the manager and proceed with the initialization queue.
				onCharacteristicWrite(gatt, characteristic);
				mOperationInProgress = false;
				nextRequest();
			} else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
				if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_NONE) {
					// This should never happen but it used to: http://stackoverflow.com/a/20093695/2115352
					DebugLogger.w(TAG, ERROR_AUTH_ERROR_WHILE_BONDED);
					mCallbacks.onError(gatt.getDevice(), ERROR_AUTH_ERROR_WHILE_BONDED, status);
				}
			} else {
				DebugLogger.e(TAG, "onCharacteristicWrite error " + status);
				onError(gatt.getDevice(), ERROR_WRITE_CHARACTERISTIC, status);
			}
		}

		@Override
		public void onDescriptorRead(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Logger.i(mLogSession, "Read Response received from descr. " + descriptor.getUuid() + ", value: " + ParserUtils.parse(descriptor));

				// The value has been read. Notify the manager and proceed with the initialization queue.
				onDescriptorRead(gatt, descriptor);
				mOperationInProgress = false;
				nextRequest();
			} else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
				if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_NONE) {
					// This should never happen but it used to: http://stackoverflow.com/a/20093695/2115352
					DebugLogger.w(TAG, ERROR_AUTH_ERROR_WHILE_BONDED);
					mCallbacks.onError(gatt.getDevice(), ERROR_AUTH_ERROR_WHILE_BONDED, status);
				}
			} else {
				DebugLogger.e(TAG, "onDescriptorRead error " + status);
				onError(gatt.getDevice(), ERROR_READ_DESCRIPTOR, status);
			}
		}

		@Override
		public final void onDescriptorWrite(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Logger.i(mLogSession, "Data written to descr. " + descriptor.getUuid() + ", value: " + ParserUtils.parse(descriptor));

				if (isServiceChangedCCCD(descriptor)) {
					Logger.a(mLogSession, "Service Changed notifications enabled");
				} else if (isBatteryLevelCCCD(descriptor)) {
					final byte[] value = descriptor.getValue();
					if (value != null && value.length == 2 && value[1] == 0x00) {
						if (value[0] == 0x01) {
							Logger.a(mLogSession, "Battery Level notifications enabled");
						} else {
							Logger.a(mLogSession, "Battery Level notifications disabled");
						}
					} else {
						onDescriptorWrite(gatt, descriptor);
					}
				} else if (isCCCD(descriptor)) {
					final byte[] value = descriptor.getValue();
					if (value != null && value.length == 2 && value[1] == 0x00) {
						switch (value[0]) {
							case 0x00:
								Logger.a(mLogSession, "Notifications and indications disabled");
								break;
							case 0x01:
								Logger.a(mLogSession, "Notifications enabled");
								break;
							case 0x02:
								Logger.a(mLogSession, "Indications enabled");
								break;
						}
					} else {
						onDescriptorWrite(gatt, descriptor);
					}
				} else {
					onDescriptorWrite(gatt, descriptor);
				}
				mOperationInProgress = false;
				nextRequest();
			} else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
				if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_NONE) {
					// This should never happen but it used to: http://stackoverflow.com/a/20093695/2115352
					DebugLogger.w(TAG, ERROR_AUTH_ERROR_WHILE_BONDED);
					mCallbacks.onError(gatt.getDevice(), ERROR_AUTH_ERROR_WHILE_BONDED, status);
				}
			} else {
				DebugLogger.e(TAG, "onDescriptorWrite error " + status);
				onError(gatt.getDevice(), ERROR_WRITE_DESCRIPTOR, status);
			}
		}

		@Override
		public final void onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			final String data = ParserUtils.parse(characteristic);

			if (isBatteryLevelCharacteristic(characteristic)) {
				Logger.i(mLogSession, "Notification received from " + characteristic.getUuid() + ", value: " + data);
				final int batteryValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
				Logger.a(mLogSession, "Battery level received: " + batteryValue + "%");
				mBatteryValue = batteryValue;
				onBatteryValueReceived(gatt, batteryValue);
				mCallbacks.onBatteryValueReceived(gatt.getDevice(), batteryValue);
			} else {
				final BluetoothGattDescriptor cccd = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
				final boolean notifications = cccd == null || cccd.getValue() == null || cccd.getValue().length != 2 || cccd.getValue()[0] == 0x01;
				if (notifications) {
					//Logger.i(mLogSession, "Notification received from " + characteristic.getUuid() + ", value: " + data);
					Logger.i(mLogSession, "Notification received from " + characteristic.getUuid());
					onCharacteristicNotified(gatt, characteristic);
				} else { // indications
					Logger.i(mLogSession, "Indication received from " + characteristic.getUuid() + ", value: " + data);
					onCharacteristicIndicated(gatt, characteristic);
				}
			}
		}

		/**
		 * Executes the next request. If the last element from the initialization queue has been executed
		 * the {@link #onDeviceReady()} callback is called.
		 */
		private void nextRequest() {
			if (mOperationInProgress)
				return;

			// Get the first request from the init queue
			Request request = mInitQueue != null ? mInitQueue.poll() : null;

			// Are we done with initializing?
			if (request == null) {
				if (mInitInProgress) {
					mInitQueue = null; // release the queue
					mInitInProgress = false;
					onDeviceReady();
				}
				// If so, we can continue with the task queue
				request = mTaskQueue.poll();
				if (request == null) {
					// Nothing to be done for now
					return;
				}
			}

			mOperationInProgress = true;
			boolean result = false;
			switch (request.type) {
				case READ: {
					result = internalReadCharacteristic(request.characteristic);
					break;
				}
				case WRITE: {
					final BluetoothGattCharacteristic characteristic = request.characteristic;
					characteristic.setValue(request.value);
					characteristic.setWriteType(request.writeType);
					result = internalWriteCharacteristic(characteristic);
					break;
				}
				case READ_DESCRIPTOR: {
					result = internalReadDescriptor(request.descriptor);
					break;
				}
				case WRITE_DESCRIPTOR: {
					final BluetoothGattDescriptor descriptor = request.descriptor;
					descriptor.setValue(request.value);
					result = internalWriteDescriptor(descriptor);
					break;
				}
				case ENABLE_NOTIFICATIONS: {
					result = internalEnableNotifications(request.characteristic);
					break;
				}
				case ENABLE_INDICATIONS: {
					result = internalEnableIndications(request.characteristic);
					break;
				}
				case READ_BATTERY_LEVEL: {
					result = internalReadBatteryLevel();
					break;
				}
				case ENABLE_BATTERY_LEVEL_NOTIFICATIONS: {
					result = internalSetBatteryNotifications(true);
					break;
				}
				case DISABLE_BATTERY_LEVEL_NOTIFICATIONS: {
					result = internalSetBatteryNotifications(false);
					break;
				}
				case ENABLE_SERVICE_CHANGED_INDICATIONS: {
					result = ensureServiceChangedEnabled();
					break;
				}
			}
			// The result may be false if given characteristic or descriptor were not found on the device.
			// In that case, proceed with next operation and ignore the one that failed.
			if (!result) {
				mOperationInProgress = false;
				nextRequest();
			}
		}

		/**
		 * Returns true if this descriptor is from the Service Changed characteristic.
		 *
		 * @param descriptor the descriptor to be checked
		 * @return true if the descriptor belongs to the Service Changed characteristic
		 */
		private boolean isServiceChangedCCCD(final BluetoothGattDescriptor descriptor) {
			if (descriptor == null)
				return false;

			return SERVICE_CHANGED_CHARACTERISTIC.equals(descriptor.getCharacteristic().getUuid());
		}

		/**
		 * Returns true if the characteristic is the Battery Level characteristic.
		 *
		 * @param characteristic the characteristic to be checked
		 * @return true if the characteristic is the Battery Level characteristic.
		 */
		private boolean isBatteryLevelCharacteristic(final BluetoothGattCharacteristic characteristic) {
			if (characteristic == null)
				return false;

			return BATTERY_LEVEL_CHARACTERISTIC.equals(characteristic.getUuid());
		}

		/**
		 * Returns true if this descriptor is from the Battery Level characteristic.
		 *
		 * @param descriptor the descriptor to be checked
		 * @return true if the descriptor belongs to the Battery Level characteristic
		 */
		private boolean isBatteryLevelCCCD(final BluetoothGattDescriptor descriptor) {
			if (descriptor == null)
				return false;

			return BATTERY_LEVEL_CHARACTERISTIC.equals(descriptor.getCharacteristic().getUuid());
		}

		/**
		 * Returns true if this descriptor is a Client Characteristic Configuration descriptor (CCCD).
		 *
		 * @param descriptor the descriptor to be checked
		 * @return true if the descriptor is a CCCD
		 */
		private boolean isCCCD(final BluetoothGattDescriptor descriptor) {
			if (descriptor == null)
				return false;

			return CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID.equals(descriptor.getUuid());
		}
	}

	private static final int PAIRING_VARIANT_PIN = 0;
	private static final int PAIRING_VARIANT_PASSKEY = 1;
	private static final int PAIRING_VARIANT_PASSKEY_CONFIRMATION = 2;
	private static final int PAIRING_VARIANT_CONSENT = 3;
	private static final int PAIRING_VARIANT_DISPLAY_PASSKEY = 4;
	private static final int PAIRING_VARIANT_DISPLAY_PIN = 5;
	private static final int PAIRING_VARIANT_OOB_CONSENT = 6;

	protected String pairingVariantToString(final int variant) {
		switch (variant) {
			case PAIRING_VARIANT_PIN:
				return "PAIRING_VARIANT_PIN";
			case PAIRING_VARIANT_PASSKEY:
				return "PAIRING_VARIANT_PASSKEY";
			case PAIRING_VARIANT_PASSKEY_CONFIRMATION:
				return "PAIRING_VARIANT_PASSKEY_CONFIRMATION";
			case PAIRING_VARIANT_CONSENT:
				return "PAIRING_VARIANT_CONSENT";
			case PAIRING_VARIANT_DISPLAY_PASSKEY:
				return "PAIRING_VARIANT_DISPLAY_PASSKEY";
			case PAIRING_VARIANT_DISPLAY_PIN:
				return "PAIRING_VARIANT_DISPLAY_PIN";
			case PAIRING_VARIANT_OOB_CONSENT:
				return "PAIRING_VARIANT_OOB_CONSENT";
			default:
				return "UNKNOWN";
		}
	}

	protected String bondStateToString(final int state) {
		switch (state) {
			case BluetoothDevice.BOND_NONE:
				return "BOND_NONE";
			case BluetoothDevice.BOND_BONDING:
				return "BOND_BONDING";
			case BluetoothDevice.BOND_BONDED:
				return "BOND_BONDED";
			default:
				return "UNKNOWN";
		}
	}

	protected String getWriteType(final int type) {
		switch (type) {
			case BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT:
				return "WRITE REQUEST";
			case BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE:
				return "WRITE COMMAND";
			case BluetoothGattCharacteristic.WRITE_TYPE_SIGNED:
				return "WRITE SIGNED";
			default:
				return "UNKNOWN: " + type;
		}
	}

	/**
	 * Converts the connection state to String value
	 * @param state the connection state
	 * @return state as String
	 */
	protected String stateToString(final int state) {
		switch (state) {
			case BluetoothProfile.STATE_CONNECTED:
				return "CONNECTED";
			case BluetoothProfile.STATE_CONNECTING:
				return "CONNECTING";
			case BluetoothProfile.STATE_DISCONNECTING:
				return "DISCONNECTING";
			default:
				return "DISCONNECTED";
		}
	}
}
