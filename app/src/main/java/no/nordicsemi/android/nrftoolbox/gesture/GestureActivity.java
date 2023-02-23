package no.nordicsemi.android.nrftoolbox.gesture;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashMap;

import no.nordicsemi.android.nrftoolbox.R;
import no.nordicsemi.android.nrftoolbox.uart.UARTService;
import no.nordicsemi.android.nrftoolbox.utility.DataConvey;

public class GestureActivity extends AppCompatActivity {

    Dialog alertDialog;
    private SoundPool soundPool;                   //定义的音频 变量
    private HashMap<Integer,Integer> soundmap = new HashMap<Integer,Integer>();

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gesture);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);  //设置返回键操作
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        final String text = new String("该页以文字和语音形式，展示手势识别结果；点击扬声器图标，可开启/关闭语音");
        alertDialog = new AlertDialog.Builder(this).setTitle(R.string.about_title).setMessage(text).setPositiveButton(R.string.ok, null).create();

        textView = (TextView)findViewById(R.id.gestureData);
        onInitialize(savedInstanceState);

        soundPool = new SoundPool(11, AudioManager.STREAM_SYSTEM,0);   //创建一个SoundPool对象，容纳11个音频
        //将要播放的音频保存到HashMap对象中
        soundmap.put(1,soundPool.load(this,R.raw.ges0,1));
        soundmap.put(2,soundPool.load(this,R.raw.ges1,1));
        soundmap.put(3,soundPool.load(this,R.raw.ges2,1));
        soundmap.put(4,soundPool.load(this,R.raw.ges3,1));
        soundmap.put(5,soundPool.load(this,R.raw.ges4,1));
        soundmap.put(6,soundPool.load(this,R.raw.ges5,1));
        soundmap.put(7,soundPool.load(this,R.raw.ges6,1));
        soundmap.put(8,soundPool.load(this,R.raw.ges7,1));
        soundmap.put(9,soundPool.load(this,R.raw.ges8,1));
        soundmap.put(10,soundPool.load(this,R.raw.ges9,1));
        soundmap.put(11,soundPool.load(this,R.raw.ges254,1));

        gestureButton =(ImageView)findViewById(R.id.gestureButton);   //图像按钮的处理
        gestureButton.setOnClickListener(onImageClick);
        gestureButton.setBackgroundResource(R.mipmap.image2);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {  //加载菜单
        getMenuInflater().inflate(R.menu.help, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {  //菜单点击操作
        switch (item.getItemId()) {
            case R.id.action_about:
                alertDialog.show();
                break;
            case android.R.id.home:
                onBackPressed();
                break;
        }
        return super.onOptionsItemSelected(item);
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

    private TextView textView;
    ImageView gestureButton;
    boolean isPlaying = false;
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
                String gestureLabel = "等待中...";
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
                            if (buffer[3] == 0xA0)  //帧识别码，针对手势识别
                            {
                                switch (buffer[4]) {
                                    case 0 : gestureLabel = "待机"; break;
                                    case 1 : gestureLabel = "右旋"; break;
                                    case 2 : gestureLabel = "上抬"; break;
                                    case 3 : gestureLabel = "下拉"; break;
                                    case 4 : gestureLabel = "左移右旋"; break;
                                    case 5 : gestureLabel = "右移右旋"; break;
                                    case 6 : gestureLabel = "上抬右旋"; break;
                                    case 7 : gestureLabel = "下拉右旋"; break;
                                    case 8 : gestureLabel = "左旋"; break;
                                    case 9 : gestureLabel = "下拉左旋"; break;
                                    case 254 : gestureLabel = "无法识别"; break;
                                    default : break;
                                }
                                if(isPlaying) {
                                    switch (buffer[4]) {   //播放指定的音频
                                        case 0 : soundPool.play(soundmap.get(1),1,1,0,0,1); break;
                                        case 1 : soundPool.play(soundmap.get(2),1,1,0,0,1); break;
                                        case 2 : soundPool.play(soundmap.get(3),1,1,0,0,1); break;
                                        case 3 : soundPool.play(soundmap.get(4),1,1,0,0,1); break;
                                        case 4 : soundPool.play(soundmap.get(5),1,1,0,0,1); break;
                                        case 5 : soundPool.play(soundmap.get(6),1,1,0,0,1); break;
                                        case 6 : soundPool.play(soundmap.get(7),1,1,0,0,1); break;
                                        case 7 : soundPool.play(soundmap.get(8),1,1,0,0,1); break;
                                        case 8 : soundPool.play(soundmap.get(9),1,1,0,0,1); break;
                                        case 9 : soundPool.play(soundmap.get(10),1,1,0,0,1); break;
                                        case 254 : soundPool.play(soundmap.get(11),1,1,0,0,1); break;
                                        default : break;
                                    }
                                }
                            }
                        }
                    }
                }
                textView.setText(gestureLabel);     //显示手势
            }
        }
    };
    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UARTService.BROADCAST_UART_RX);
        return intentFilter;
    }
    //数据同步结束

    private View.OnClickListener onImageClick = new View.OnClickListener() {  //图像单击操作
        @Override
        public void onClick (View v) {
            if(!isPlaying) {
                gestureButton.setBackgroundResource(R.mipmap.image1);
                isPlaying = true;
            }
            else {
                gestureButton.setBackgroundResource(R.mipmap.image2);
                isPlaying = false;
            }
        }
    };

}
