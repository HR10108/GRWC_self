package no.nordicsemi.android.nrftoolbox.threedimension;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import no.nordicsemi.android.nrftoolbox.uart.UARTService;
import no.nordicsemi.android.nrftoolbox.utility.DataConvey;

public class ThreeDimensionActivity extends Activity {
    private GLSurfaceView mGLView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mGLView = new GLSurfaceView(this);                     //创建一个GLSurfaceView对象
        mGLView.setRenderer(new CubeRenderer());                //为GLSurfaceView指定使用的Renderer对象
        setContentView(mGLView);                                //设置activity显示的内容为GLSurfaceView对象

        LinearLayout myLinearLayout = new LinearLayout(this);   //在3D视图中添加按键
        myLinearLayout.setOrientation(LinearLayout.VERTICAL);
        myLinearLayout.setGravity(Gravity.LEFT);
        Button myButton = new Button(this);
        myButton.setText("<--返回");
        myButton.setTextColor(Color.BLACK);
        myButton.setBackgroundColor(Color.WHITE);
        myButton.setOnClickListener(onMyClick);
        myButton.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
        myLinearLayout.addView(myButton);
        this.addContentView(myLinearLayout, new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));

        onInitialize(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGLView.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
        mGLView.onPause();
    }

    //将UART得到的数据同步过来
    protected void onInitialize(final Bundle savedInstanceState) {
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, makeIntentFilter());
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
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
                            if (buffer[3] == 0xA1)  //帧识别码，针对姿态角
                            {
                                int tempYaw,tempPitch,tempRoll;
                                tempYaw = buffer[4]*256+buffer[5];
                                tempPitch = buffer[6]*256+buffer[7];
                                tempRoll = buffer[8]*256+buffer[9];
                                DataConvey.yaw = 0.1f*(float)(tempYaw > 32767 ? (32767 - tempYaw) : tempYaw);
                                DataConvey.pitch = 0.1f*(float)(tempPitch > 32767 ? (32767 - tempPitch) : tempPitch);
                                DataConvey.roll = 0.1f*(float)(tempRoll > 32767 ? (32767 - tempRoll) : tempRoll);
                            }
                        }
                    }
                }
            }
        }
    };
    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UARTService.BROADCAST_UART_RX);
        return intentFilter;
    }
    //数据同步结束

    private View.OnClickListener onMyClick = new View.OnClickListener() {  //返回主页面
        @Override
        public void onClick (View v) {
            onBackPressed();
        }
    };

}
