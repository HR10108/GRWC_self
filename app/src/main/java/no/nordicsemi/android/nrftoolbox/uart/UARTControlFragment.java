package no.nordicsemi.android.nrftoolbox.uart;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import no.nordicsemi.android.nrftoolbox.R;

public class UARTControlFragment extends Fragment {
	@Override
	public void onAttach(final Context context) {
		super.onAttach(context);
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.fragment_feature_uart_control, container, false);
		return view;
	}

}
