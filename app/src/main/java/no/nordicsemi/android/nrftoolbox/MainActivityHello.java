package no.nordicsemi.android.nrftoolbox;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivityHello extends AppCompatActivity {
    Button buttonBLE = null;
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
    }

    private void connectBLE(){
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

}