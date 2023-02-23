package no.nordicsemi.android.nrftoolbox.app;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import no.nordicsemi.android.nrftoolbox.R;

public class ExpandableListActivity extends AppCompatActivity implements
		OnCreateContextMenuListener,
		ExpandableListView.OnChildClickListener, ExpandableListView.OnGroupCollapseListener,
		ExpandableListView.OnGroupExpandListener {
	ExpandableListAdapter mAdapter;
	ExpandableListView mList;
	boolean mFinishedStart = false;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
	}

	@Override
	public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
			int childPosition, long id) {
		return false;
	}

	/**
	 * Override this for receiving callbacks when a group has been collapsed.
	 */
	@Override
	public void onGroupCollapse(int groupPosition) {
	}

	/**
	 * Override this for receiving callbacks when a group has been expanded.
	 */
	@Override
	public void onGroupExpand(int groupPosition) {
	}

	/**
	 * Ensures the expandable list view has been created before Activity restores all of the view states.
	 * @see Activity#onRestoreInstanceState(Bundle)
	 */
	@Override
	protected void onRestoreInstanceState(@NonNull Bundle state) {
		ensureList();
		super.onRestoreInstanceState(state);
	}

	/**
	 * Updates the screen state (current list and other views) when the content changes.
	 * @see android.support.v7.app.AppCompatActivity#onContentChanged()
	 */
	@Override
	public void onContentChanged() {
		super.onContentChanged();
		mList = (ExpandableListView) findViewById(R.id.list);
		if (mList == null) {
			throw new RuntimeException(
					"Your content must have a ExpandableListView whose id attribute is " +
							"'R.id.list'");
		}
		mList.setOnChildClickListener(this);
		mList.setOnGroupExpandListener(this);
		mList.setOnGroupCollapseListener(this);

		if (mFinishedStart) {
			setListAdapter(mAdapter);
		}
		mFinishedStart = true;
	}

	/**
	 * Provide the adapter for the expandable list.
	 */
	public void setListAdapter(ExpandableListAdapter adapter) {
		synchronized (this) {
			ensureList();
			mAdapter = adapter;
			mList.setAdapter(adapter);
		}
	}

	private void ensureList() {
		if (mList != null) {
			return;
		}
		setContentView(R.layout.expandable_list_content);
	}

}
