package no.nordicsemi.android.nrftoolbox;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class MainActivityHello extends AppCompatActivity {
    Button buttonBLE = null;
    static final int REQUEST_CODE_ACCESS_COARSE_LOCATION = 1;
    private List<String> mPermissionList = new ArrayList<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_hello);
        buttonBLE = (Button) findViewById(R.id.buttonBLE);
        buttonBLE.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectBLE();
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mPermissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
            mPermissionList.add(Manifest.permission.BLUETOOTH_SCAN);
            mPermissionList.add(Manifest.permission.BLUETOOTH_CONNECT);
            mPermissionList.add(Manifest.permission.BLUETOOTH);
            mPermissionList.add(Manifest.permission.BLUETOOTH_PRIVILEGED);
            mPermissionList.add(Manifest.permission.BLUETOOTH_ADMIN);
        }
        mPermissionList.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        ActivityCompat.requestPermissions(this,mPermissionList.toArray(new String[0]), REQUEST_CODE_ACCESS_COARSE_LOCATION);
    }

    private void connectBLE(){
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

}