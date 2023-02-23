//实现不同窗口间的数据共享
package no.nordicsemi.android.nrftoolbox.utility;

import android.app.Application;

public class DataConvey extends Application {
    public static String TX_DATA;
    public static boolean write_enable;
    public static String RX_DATA;
    public static float yaw;
    public static float pitch;
    public static float roll;
    public static byte calibration_label;
}
