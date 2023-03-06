package no.nordicsemi.android.nrftoolbox.compass;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import no.nordicsemi.android.nrftoolbox.R;
import no.nordicsemi.android.nrftoolbox.uart.UARTService;
import no.nordicsemi.android.nrftoolbox.utility.DataConvey;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class CompassActivity extends AppCompatActivity {
    private CompassView compassView;
    private TextView textView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compass);
        compassView = findViewById(R.id.compass_view);
        compassView.start();
        textView = findViewById(R.id.direction_text);
        Log.e("hello_compass","onCreate");
    }

    @Override
    protected void onResume() {
        Log.e("hello_compass","onResume");
        super.onResume();
        compassView.start();
    }

    @Override
    protected void onPause() {
        Log.e("hello_compass","onPause");
        super.onPause();
        compassView.stop();
    }

    //以下部分真的应该写个基类的QAQ
    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UARTService.BROADCAST_UART_RX);
        return intentFilter;
    }

    protected void onInitialize(final Bundle savedInstanceState) {
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, makeIntentFilter());
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            if (UARTService.BROADCAST_UART_RX.equals(action)) {
                //开始帧数据的处理
                String[] RXdata = DataConvey.RX_DATA.split("\\-");  //将字符串组劈开
                short[] buffer = new short[RXdata.length];
                for(int i = 0; i < buffer.length; i++) {  //将字符串转换成short数组
                    buffer[i] = Short.parseShort(RXdata[i],16);
                }
                if ((buffer[0] == 0xa5) && (buffer[1] == 0x5a))  //判断帧头
                {
                    int len = buffer[2];  //取帧长
                    if (buffer.length == len + 2)   //判断帧是否完整
                    {
                        int checksum = 0;
                        for (byte i = 2 ; i < len; i++) {
                            checksum += buffer[i];
                        }
                        if (checksum % 256 == buffer[len]) { //判断校验位
                            if (buffer[3] == 0xA1)  //
                            {
                                Log.e("hello_compass_activity", String.valueOf(DataConvey.yaw));
                            }
                        }
                    }
                }

            }
        }
    };

}