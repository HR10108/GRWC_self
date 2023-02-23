package no.nordicsemi.android.nrftoolbox.calibration;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import no.nordicsemi.android.nrftoolbox.R;
import no.nordicsemi.android.nrftoolbox.uart.UARTActivity;
import no.nordicsemi.android.nrftoolbox.uart.UARTService;
import no.nordicsemi.android.nrftoolbox.utility.DataConvey;
import no.nordicsemi.android.nrftoolbox.utility.ParserUtils;

import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class CalibrationActivity extends AppCompatActivity {
    Dialog alertDialog,alertDialog1;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);  //设置返回键操作
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        //getSupportActionBar().setDisplayShowHomeEnabled(true);

        final String text = new String("参考下述三步进行地磁传感器校正：\n\n" +
                "1.将手环水平放置，手动使其绕Z轴旋转几圈；这一步是采集磁力计X轴和Y轴的最大最小值" +
                "\n\n2.将手环垂直放置，手动使其绕X轴或Y轴旋转几圈；这一步是采集磁力计Z轴的最大最小值" +
                "\n\n3.将手环以任意角度旋转多次，直到该页的6项数据不再更新，然后点击“保存标定”按键");
        alertDialog = new AlertDialog.Builder(this).setTitle(R.string.about_title).setMessage(text).setPositiveButton(R.string.ok, null).create();

        onInitialize(savedInstanceState);

        myButton1 = (Button) findViewById(R.id.calibration_button1);
        myButton1.setOnClickListener(onButtonClick1);
        myButton1.setText("开始标定");
        Button myButton2 = (Button) findViewById(R.id.calibration_button2);
        myButton2.setOnClickListener(onButtonClick2);
        myButton3 = (Button) findViewById(R.id.calibration_button3);
        myButton3.setVisibility(View.INVISIBLE);
        myButton3.setOnClickListener(onButtonClick3);

        textView1 = (TextView) findViewById(R.id.textview1);
        textView2 = (TextView) findViewById(R.id.textview2);
        textView3 = (TextView) findViewById(R.id.textview3);
        textView4 = (TextView) findViewById(R.id.textview4);
        textView5 = (TextView) findViewById(R.id.textview5);
        textView6 = (TextView) findViewById(R.id.textview6);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {  //加载菜单
        getMenuInflater().inflate(R.menu.help, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                alertDialog.show();
                break;
            case android.R.id.home:
                onBackPressed();
                myButton3.setVisibility(View.INVISIBLE);
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

    private TextView textView1,textView2,textView3,textView4,textView5,textView6;     //显示手势
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
                int hmaxx = 0,hminx = 0,hmaxy = 0,hminy = 0,hmaxz = 0,hminz = 0;
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
                            if (buffer[3] == 0xA4)  //帧识别码，针对磁力计校正
                            {
                                int tempminx, tempmaxx, tempminy, tempmaxy, tempminz, tempmaxz;
                                tempmaxx = buffer[4] * 256 + buffer[5];                 //对磁力计上传的数据进行处理
                                tempmaxy = buffer[6] * 256 + buffer[7];
                                tempmaxz = buffer[8] * 256 + buffer[9];
                                tempminx = buffer[10] * 256 + buffer[11];
                                tempminy = buffer[12] * 256 + buffer[13];
                                tempminz = buffer[14] * 256 + buffer[15];
                                hmaxx = tempmaxx > 32767 ? (32767 - tempmaxx) : tempmaxx;
                                hminx = tempminx > 32767 ? (32767 - tempminx) : tempminx;
                                hmaxy = tempmaxy > 32767 ? (32767 - tempmaxy) : tempmaxy;
                                hminy = tempminy > 32767 ? (32767 - tempminy) : tempminy;
                                hmaxz = tempmaxz > 32767 ? (32767 - tempmaxz) : tempmaxz;
                                hminz = tempminz > 32767 ? (32767 - tempminz) : tempminz;
                            }
                        }
                    }
                }
                textView1.setText(String.valueOf(hmaxx));     //显示磁力计最大最小值
                textView2.setText(String.valueOf(hminx));
                textView3.setText(String.valueOf(hmaxy));
                textView4.setText(String.valueOf(hminy));
                textView5.setText(String.valueOf(hmaxz));
                textView6.setText(String.valueOf(hminz));
            }
        }
    };
    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UARTService.BROADCAST_UART_RX);
        return intentFilter;
    }
    //数据同步结束

    Button myButton1;
    private View.OnClickListener onButtonClick1 = new View.OnClickListener() {  //单击操作
        @Override
        public void onClick (View v) {
            byte[] buffer;
            Intent intent = new Intent(CalibrationActivity.this, UARTActivity.class);
            if(myButton1.getText().toString() == "开始标定") {
                buffer = new byte[]{(byte) 0xa5, 0x5a, 0x04, (byte) 0xe3, (byte) 0xe7, (byte) 0xaa};   //磁力计开始标定的数据格式
                DataConvey.TX_DATA = ParserUtils.parse(buffer);
                DataConvey.calibration_label = 0;
                startActivity(intent);
                myButton1.setText("保存标定");
                myButton3.setVisibility(View.INVISIBLE);
            }
            else {
                if (DataConvey.calibration_label == 1) {
                    buffer = new byte[]{(byte) 0xa5, 0x5a, 0x04, (byte) 0xe1, (byte) 0xe5, (byte) 0xaa};   //磁力计保存标定的数据格式
                    DataConvey.TX_DATA = ParserUtils.parse(buffer);
                    DataConvey.calibration_label = 0;
                    startActivity(intent);
                    myButton3.setVisibility(View.VISIBLE);
                }
                else {
                    myButton3.setVisibility(View.INVISIBLE);
                }
                myButton1.setText("开始标定");
            }
        }
    };

    private View.OnClickListener onButtonClick2 = new View.OnClickListener() {  //单击操作
        @Override
        public void onClick (View v) {
            onBackPressed();
            myButton3.setVisibility(View.INVISIBLE);
        }
    };

    Button myButton3;
    private View.OnClickListener onButtonClick3 = new View.OnClickListener() {  //单击操作
        @Override
        public void onClick (View v) {
            if (DataConvey.calibration_label == 2) {
                Toast.makeText(getApplicationContext(),"磁力计标定成功！请退出",Toast.LENGTH_SHORT).show();
            }
            else {
                Toast.makeText(getApplicationContext(),"磁力计标定失败，请重新标定！",Toast.LENGTH_SHORT).show();
            }
        }
    };

}
