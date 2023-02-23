/*
 * 主程序
 * 完成人：周波，吕继兵
 */
package no.nordicsemi.android.nrftoolbox;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.GridView;
import android.widget.Toast;

import no.nordicsemi.android.nrftoolbox.adapter.AppAdapter;

public class MainActivity extends AppCompatActivity {

	static final int REQUEST_CODE_ACCESS_COARSE_LOCATION = 1;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(toolbar);
		toolbar.setOnMenuItemClickListener(onMenuItemClick);  // Menu item click 的监听事件一樣要設定在setSupportActionBar才有作用

		// ensure Bluetooth exists
		if (!ensureBLEExists())
			finish();

		final DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
		drawer.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

		// configure the app grid
		final GridView grid = (GridView) findViewById(R.id.grid);
		grid.setAdapter(new AppAdapter(this));
		grid.setEmptyView(findViewById(android.R.id.empty));

		//如果 API level 是大于等于 23(针对Android 6.0及以上系统) 时,需要判断是否具有权限
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
			{
				Toast.makeText(getApplicationContext(),"对于android 6.0及以上系统的手机，请务必允许该APP软件获取手机定位(位置)权限,打开定位权限后，退出并重新打开APP即可",Toast.LENGTH_SHORT).show();
				//请求权限
				ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CODE_ACCESS_COARSE_LOCATION);
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.help, menu);
		return true;
	}

	private Toolbar.OnMenuItemClickListener onMenuItemClick = new Toolbar.OnMenuItemClickListener() { //添加菜单监听事件
		@Override
		public boolean onMenuItemClick(MenuItem item) {
			switch (item.getItemId()) {
				case R.id.action_about:
					final AppHelpFragment fragment = AppHelpFragment.getInstance(R.string.about_text, true);
					fragment.show(getSupportFragmentManager(), null);
					break;
			}
			return true;
	    }
	};

	private boolean ensureBLEExists() {
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, "No BLE support!", Toast.LENGTH_SHORT).show();
			return false;
		}
		return true;
	}

}
