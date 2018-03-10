package com.asb.otic.ui.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.asb.otic.R;
import com.asb.otic.ui.activity.BaseActivity;
import com.asb.otic.ui.activity.MainActivity;
import com.asb.otic.util.DialogUtils;
import com.github.ybq.android.spinkit.SpinKitView;

import java.io.IOException;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.asb.otic.ui.activity.MainActivity.ENABLE_BT_REQUEST_CODE;
import static com.asb.otic.ui.activity.MainActivity.uuid;

/**
 * Created by Amr Alsaidy on 2017/11/1.
 */

public class BluetoothDialogFragment extends DialogFragment {
    @BindView(R.id.find_bluetooth_ctv_id)
    TextView findBluetoothTextView;
    @BindView(R.id.find_player_btn_id)
    Button findOpponentButton;
    @BindView(R.id.opponents_lv_id)
    ListView opponentsListView;
    @BindView(R.id.spin_kit)
    public SpinKitView spinKitView;
    @BindView(R.id.close_iv_id)
    public ImageView closeImageView;

    private static final int DISCOVERABLE_BT_REQUEST_CODE = 2;
    private static final int DISCOVERABLE_DURATION = 300;

//    MainActivity activity;

    private ArrayAdapter arrayAdapter;
    private BluetoothAdapter bluetoothAdapter;
    public ListeningThread listeningThread;
    boolean refreshEnabled = false;

//    public static BluetoothDevice bluetoothDevice = null;
//    public static BluetoothSocket bluetoothSocket = null;

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                arrayAdapter.add(bluetoothDevice.getName() + "\n" + bluetoothDevice.getAddress());
//                arrayAdapter.add(bluetoothDevice.getName());
            }
        }
    };
    private Typeface font;


    public static BluetoothDialogFragment newInstance() {
        return new BluetoothDialogFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_dialog_bluetooth, container);
        ButterKnife.bind(this, view);

        font = ((MainActivity) getActivity()).font;
        findOpponentButton.setTypeface(font);
        findBluetoothTextView.setTypeface(font);
        findOpponentButton.setBackgroundColor(BaseActivity.COLOR3);
        findOpponentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                spinKitView.setVisibility(View.VISIBLE);
                if (bluetoothAdapter == null) {
                    Toast.makeText(getActivity(), R.string.your_device_does_not_support_bluetooth, Toast.LENGTH_SHORT).show();
                } else if (!refreshEnabled) {
                    refreshEnabled = true;
                    findOpponentButton.setText(R.string.stop_search);
                    Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBluetoothIntent, ENABLE_BT_REQUEST_CODE);
                    Toast.makeText(getActivity(), R.string.bluetooth_enabled, Toast.LENGTH_SHORT).show();
                } else if (refreshEnabled) {
                    findOpponentButton.setText(R.string.find_players);
                    refreshEnabled = false;
                    spinKitView.setVisibility(View.INVISIBLE);
                    arrayAdapter.clear();
                    bluetoothAdapter.disable();
                }
            }
        });

        opponentsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                String itemValue = (String) opponentsListView.getItemAtPosition(position);
                String MAC = itemValue.substring(itemValue.length() - 17);
                BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(MAC);
                MainActivity.bluetoothDevice = bluetoothDevice;
                ((MainActivity) getActivity()).playerTwoNameEditText.setText(bluetoothDevice.getName());
                bluetoothAdapter.cancelDiscovery();
                getActivity().getSupportFragmentManager().beginTransaction().remove(BluetoothDialogFragment.this).commit();
                ((MainActivity) getActivity()).bluetoothRadioButton.setChecked(true);
            }

        });

        arrayAdapter = new ArrayAdapter(getActivity(), android.R.layout.simple_list_item_1);
        opponentsListView.setAdapter(arrayAdapter);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.disable();
            arrayAdapter.clear();
        }

//        closeImageView.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//
//            }
//        });
        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ENABLE_BT_REQUEST_CODE) {
            // Bluetooth successfully enabled!
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(getActivity(), getString(R.string.bluetooth_enabled) + "\n" + getString(R.string.scanning_for_peers), Toast.LENGTH_SHORT).show();
                makeDiscoverable();
                discoverDevices();

                listeningThread = new ListeningThread();
                listeningThread.start();

            } else {
                Toast.makeText(getActivity(), R.string.bluetooth_is_not_enabled, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == DISCOVERABLE_BT_REQUEST_CODE) {
            if (resultCode == DISCOVERABLE_DURATION) {
                Toast.makeText(getActivity(), getString(R.string.your_device_is_now_discoverable_for) + " " + DISCOVERABLE_DURATION + " " + getString(R.string.seconds), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getActivity(), R.string.Fail_to_enable_discoverable_mode, Toast.LENGTH_SHORT).show();
            }
        } else if (resultCode == ((MainActivity) getActivity()).Finished_Activity) {
            bluetoothAdapter.disable();
            arrayAdapter.clear();
            refreshEnabled = false;
            findOpponentButton.setText(R.string.find_players);
        }
    }

    protected void discoverDevices() {
        if (bluetoothAdapter.startDiscovery()) {
            Toast.makeText(getActivity(), R.string.discovering_peers, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getActivity(), R.string.discovery_failed_to_start, Toast.LENGTH_SHORT).show();
        }
    }

    protected void makeDiscoverable() {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION);
        startActivityForResult(discoverableIntent, DISCOVERABLE_BT_REQUEST_CODE);
    }

//    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
//
////        bluetoothDevice = device;
////        bluetoothSocket = socket;
////        Intent intent = new Intent(SearchOpponentActivity.this, TwoDeviceActivity.class);
////        startActivityForResult(intent, Finished_Activity);
//
//    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        getActivity().registerReceiver(broadcastReceiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(broadcastReceiver);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // create dialog_background in an arbitrary way
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        DialogUtils.setMargins(dialog, 20, 20, 20, 20);
        return dialog;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (getActivity() != null) {
            ((MainActivity) getActivity()).androidRadioButton.setChecked(true);
            ((MainActivity) getActivity()).playerTwoNameEditText.setText(((MainActivity) getActivity()).deviceName);
        }
        super.onDismiss(dialog);
    }


    @OnClick(R.id.close_iv_id)
    public void closeFragment() {
        getActivity().getSupportFragmentManager().beginTransaction().remove(BluetoothDialogFragment.this).commit();
        ((MainActivity) getActivity()).androidRadioButton.setChecked(true);
        ((MainActivity) getActivity()).playerTwoNameEditText.setText(((MainActivity) getActivity()).deviceName);
    }

    public class ListeningThread extends Thread {
        private final BluetoothServerSocket bluetoothServerSocket;

        public ListeningThread() {
            BluetoothServerSocket temp = null;
            try {
                temp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        getString(R.string.app_name), uuid);

            } catch (IOException e) {
                e.printStackTrace();
            }
            bluetoothServerSocket = temp;
        }


        public void run() {
            BluetoothSocket bluetoothSocket;

            while (true) {
                try {
                    bluetoothSocket = bluetoothServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                if (bluetoothSocket != null) {

                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(getActivity(), R.string.connection_has_been_accepted, Toast.LENGTH_SHORT).show();
                        }
                    });
                    ((MainActivity) getActivity()).connected(bluetoothSocket, bluetoothSocket.getRemoteDevice());
                    ((MainActivity) getActivity()).sendData1();
                    try {
                        bluetoothServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        public void cancel() {
            try {
                bluetoothServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}

