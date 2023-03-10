package no.nordicsemi.android.nrftoolbox.utility;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

public class ParserUtils {
	final private static char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

	public static String parse(final BluetoothGattCharacteristic characteristic) {
		return parse(characteristic.getValue());
	}

	public static String parse(final BluetoothGattDescriptor descriptor) {
		return parse(descriptor.getValue());
	}

	public static String parse(final byte[] data) {
		if (data == null || data.length == 0)
			return "";

		final char[] out = new char[data.length * 3 - 1];
		for (int j = 0; j < data.length; j++) {
			int v = data[j] & 0xFF;
			out[j * 3] = HEX_ARRAY[v >>> 4];
			out[j * 3 + 1] = HEX_ARRAY[v & 0x0F];
			if (j != data.length - 1)
				out[j * 3 + 2] = '-';
		}
		return new String(out);
	}

	public static String parse_zb(final byte[] data) {
		if (data == null || data.length == 0)
			return "";
		final char[] out = new char[data.length * 4];
		for (int j = 0; j < data.length; j++) {
			int v = data[j] & 0xFF;
			out[j * 4] = '0';
			out[j * 4 + 1] = 'x';
			out[j * 4 + 2] = HEX_ARRAY[v >>> 4];
			out[j * 4 + 3] = HEX_ARRAY[v & 0x0F];
		}
		return new String(out);
	}

}
