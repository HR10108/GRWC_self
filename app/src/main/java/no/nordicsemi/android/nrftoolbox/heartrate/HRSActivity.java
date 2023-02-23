package no.nordicsemi.android.nrftoolbox.heartrate;

import android.app.Dialog;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import no.nordicsemi.android.nrftoolbox.R;

public class HRSActivity extends AppCompatActivity {
    Dialog alertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hrs);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);  //设置返回键操作
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        final String text = new String("该页显示二维心率曲线");
        alertDialog = new AlertDialog.Builder(this).setTitle(R.string.about_title).setMessage(text).setPositiveButton(R.string.ok, null).create();
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
                break;
        }
        return super.onOptionsItemSelected(item);
    }

}
