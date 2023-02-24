/*
 * 主程序
 * 完成人：周波，吕继兵
 */
package no.nordicsemi.android.nrftoolbox;

import android.Manifest;
import android.app.Activity;
import android.app.LocalActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.GridView;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.things.update.UpdateManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import no.nordicsemi.android.nrftoolbox.adapter.AppAdapter;
import no.nordicsemi.android.nrftoolbox.calibration.CalibrationActivity;
import no.nordicsemi.android.nrftoolbox.gesture.GestureActivity;
import no.nordicsemi.android.nrftoolbox.heartrate.HRSActivity;
import no.nordicsemi.android.nrftoolbox.threedimension.ThreeDimensionActivity;
import no.nordicsemi.android.nrftoolbox.uart.UARTActivity;


public class MainActivity extends AppCompatActivity {

	static final int REQUEST_CODE_ACCESS_COARSE_LOCATION = 1;
	BottomNavigationView bottomNav = null;
	List<View> listViews;
	Context context = null;
	LocalActivityManager manager = null;
	private ViewPager pager = null;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main_new);

//        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
//        setSupportActionBar(toolbar);
//		toolbar.setOnMenuItemClickListener(onMenuItemClick);  // Menu item click 的监听事件一樣要設定在setSupportActionBar才有作用
//
//		final DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
//		drawer.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
//
//		// configure the app grid
//		final GridView grid = (GridView) findViewById(R.id.grid);
//		grid.setAdapter(new AppAdapter(this));
//		grid.setEmptyView(findViewById(android.R.id.empty));

		bottomNav = (BottomNavigationView) findViewById(R.id.bottomNav);
		bottomNav.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
		context = MainActivity.this;
		pager = (ViewPager) findViewById(R.id.viewpager);
		listViews = new ArrayList<View>();
		manager = new LocalActivityManager(this,true);
		manager.dispatchCreate(savedInstanceState);



		Intent uart = new Intent(context, UARTActivity.class);
		listViews.add(getView("Uart",uart));
		Intent threeD = new Intent(context, ThreeDimensionActivity.class);
		listViews.add(getView("3D",threeD));
		Intent calibration = new Intent(context, CalibrationActivity.class);
		listViews.add(getView("Calibration",calibration));
		Intent gesture = new Intent(context, GestureActivity.class);
		listViews.add(getView("Gesture",gesture));
		Intent hrs = new Intent(context, HRSActivity.class);
		listViews.add(getView("HRS",hrs));
		pager.setAdapter(new MyPageAdapter(listViews));


		pager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
			@Override
			public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

			}

			@Override
			public void onPageSelected(int position) {
				switch (position){
					case 0:
						bottomNav.setSelectedItemId(R.id.navigation_uart);
						break;
					case 1:
						bottomNav.setSelectedItemId(R.id.navigation_3d);
						break;
					case 2:
						bottomNav.setSelectedItemId(R.id.navigation_calibration);
						break;
					case 3:
						bottomNav.setSelectedItemId(R.id.navigation_gesture);
						break;
					case 4:
						bottomNav.setSelectedItemId(R.id.navigation_rate);
						break;
				}
			}

			@Override
			public void onPageScrollStateChanged(int state) {

			}
		});


		// ensure Bluetooth exists
		if (!ensureBLEExists())
			finish();
		//initPermission();
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

	private View getView(String id, Intent intent) {
		return manager.startActivity(id, intent).getDecorView();
	}


	private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
			= item -> {
				switch (item.getItemId()) {
					case R.id.navigation_uart:
						pager.setCurrentItem(0);
						return true;
					case R.id.navigation_3d:
						pager.setCurrentItem(1);
						return true;
					case R.id.navigation_calibration:
						pager.setCurrentItem(2);
						return true;
					case R.id.navigation_rate:
						pager.setCurrentItem(4);
						return true;
					case R.id.navigation_gesture:
						pager.setCurrentItem(3);
						return true;
				}
				return false;
			};


	// todo 蓝牙动态申请权限
	private void initPermission(){

		List<String> mPermissionList = new ArrayList<>();
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
			// Android 版本大于等于 Android12 时
			// 只包括蓝牙这部分的权限，其余的需要什么权限自己添加
			mPermissionList.add(Manifest.permission.BLUETOOTH_SCAN);
			mPermissionList.add(Manifest.permission.BLUETOOTH_ADVERTISE);
			mPermissionList.add(Manifest.permission.BLUETOOTH_CONNECT);
		} else {
			// Android 版本小于 Android12 及以下版本
			mPermissionList.add(Manifest.permission.ACCESS_COARSE_LOCATION);
			mPermissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
		}

		if(mPermissionList.size() > 0){
			ActivityCompat.requestPermissions(this,mPermissionList.toArray(new String[0]),1001);
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

	private class MyPageAdapter extends PagerAdapter {

		private List<View> list;

		private MyPageAdapter(List<View> list) {
			this.list = list;
		}

		@Override
		public void destroyItem(View view, int position, Object arg2) {
			ViewPager pViewPager = ((ViewPager) view);
			pViewPager.removeView(list.get(position));
		}

		@Override
		public void finishUpdate(View arg0) {
		}

		@Override
		public int getCount() {
			return list.size();
		}

		@Override
		public Object instantiateItem(View view, int position) {
			ViewPager pViewPager = ((ViewPager) view);
			pViewPager.addView(list.get(position));
			return list.get(position);
		}

		@Override
		public boolean isViewFromObject(View arg0, Object arg1) {
			return arg0 == arg1;
		}


		@Override
		public void restoreState(Parcelable arg0, ClassLoader arg1) {
		}

		@Override
		public Parcelable saveState() {
			return null;
		}

		@Override
		public void startUpdate(View arg0) {
		}

	}




}
