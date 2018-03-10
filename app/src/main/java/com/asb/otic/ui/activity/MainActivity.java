package com.asb.otic.ui.activity;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.asb.otic.R;
import com.asb.otic.ui.custom.BoardView;
import com.asb.otic.ui.custom.CDialog;
import com.asb.otic.ui.custom.CTextView;
import com.asb.otic.ui.fragment.BluetoothDialogFragment;

import java.io.IOException;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by Amr Alsaidy on 2017/11/1.
 */

public class MainActivity extends BaseActivity {

    public final static UUID uuid = UUID.fromString("fc5ffc49-00e3-4c8b-9cf1-6b72aad1001a");
    public static final int ENABLE_BT_REQUEST_CODE = 1;
    public static final int Finished_Activity = 3;
    static final int GAME_ANDROID_REQUEST = 1;
    static final int GAME_FRIEND_REQUEST = 2;
    static final int GAME_BLUETOOTH_REQUEST = 3;

    @BindView(R.id.settings_iv_id)
    ImageView settingsImageView;
    @BindView(R.id.name1_tv_id)
    CTextView name1CTextView;
    @BindView(R.id.type_tv_id)
    CTextView typeCTextView;
    @BindView(R.id.name2_tv_id)
    CTextView name2CTextView;
    @BindView(R.id.size_tv_id)
    CTextView sizeCTextView;
    @BindView(R.id.player_type_rg_id)
    public RadioGroup playerTypeRadioGroup;
    @BindView(R.id.android_rbtn_id)
    public RadioButton androidRadioButton;
    @BindView(R.id.friend_rbtn_id)
    public RadioButton friendRadioButton;
    @BindView(R.id.bluetooth_rbtn_id)
    public RadioButton bluetoothRadioButton;
    @BindView(R.id.board_radius_sb_id)
    SeekBar boardRadiusSeekBar;
    @BindView(R.id.board_radius_et_id)
    TextView boardRadiusTextView;
    @BindView(R.id.player_one_name_et_id)
    public EditText playerOneNameEditText;
    @BindView(R.id.player_two_name_et_id)
    public EditText playerTwoNameEditText;
    @BindView(R.id.play_btn_id)
    Button playButton;
    @BindView(R.id.progress_bar_id)
    ProgressBar progressBar;

    public String playerOneName = "";
    public String playerTwoName = "";
    boolean doubleBackToExitPressedOnce = false;

    CDialog cDialog;
    ConnectingThread connectingThread = null;
    GradientDrawable gradientDrawable;

    public static BluetoothDevice bluetoothDevice;
    public static BluetoothSocket bluetoothSocket;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        View layout = findViewById(R.id.main_fl_id);
        gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{COLOR1, COLOR2});
        layout.setBackground(gradientDrawable);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Window w = getWindow(); // in Activity's onCreate() for instance
            w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }
        name1CTextView.setTextSize(12 * getResources().getDisplayMetrics().density);
        typeCTextView.setTextSize(12 * getResources().getDisplayMetrics().density);
        name2CTextView.setTextSize(12 * getResources().getDisplayMetrics().density);
        sizeCTextView.setTextSize(12 * getResources().getDisplayMetrics().density);
        playerOneNameEditText.setTextSize(10 * getResources().getDisplayMetrics().density);
        playerTwoNameEditText.setTextSize(10 * getResources().getDisplayMetrics().density);
        androidRadioButton.setTextSize(10 * getResources().getDisplayMetrics().density);
        friendRadioButton.setTextSize(10 * getResources().getDisplayMetrics().density);
        bluetoothRadioButton.setTextSize(10 * getResources().getDisplayMetrics().density);

        name1CTextView.setTextColor(COLOR3);
        typeCTextView.setTextColor(COLOR3);
        name2CTextView.setTextColor(COLOR3);
        sizeCTextView.setTextColor(COLOR3);
        playButton.setBackgroundColor(COLOR3);
        if(language.equals("ar")){
            name1CTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            typeCTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            name2CTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            sizeCTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            androidRadioButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            friendRadioButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            bluetoothRadioButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            playerOneNameEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            playerTwoNameEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        }

        playerOneNameEditText.setTypeface(font);
        playerTwoNameEditText.setTypeface(font);
        androidRadioButton.setTypeface(font);
        friendRadioButton.setTypeface(font);
        bluetoothRadioButton.setTypeface(font);
        playButton.setTypeface(font);

        playerOneNameEditText.setText(yourName);
        playerTwoNameEditText.setText(deviceName);

        boardRadiusTextView.setText(String.valueOf(boardSize));

        boardRadiusSeekBar.post(new Runnable() {
            @Override
            public void run() {
                boardRadiusSeekBar.setProgress(boardSize - 2);
            }
        });
        boardRadiusSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
                boardRadiusTextView.setText(String.format("%S", progress + 2));
                boardSize = progress + 2;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        settingsImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = SettingsActivity.Factory.getIntent(MainActivity.this);
                intent.putExtra("CALLER", "MainActivity");
                startActivity(intent);
                MainActivity.this.finish();
            }
        });


    }

    @OnClick(R.id.android_rbtn_id)
    public void selectAndroid() {
        playerTwoNameEditText.setText(getString(R.string.android));
    }

    @OnClick(R.id.friend_rbtn_id)
    public void selectLocal() {
        playerTwoNameEditText.setText(getString(R.string.friend));
    }

    @OnClick(R.id.bluetooth_rbtn_id)
    public void selectBluetooth() {
        if (TextUtils.isEmpty(playerOneNameEditText.getText().toString())) {
            playerOneNameEditText.setError(getString(R.string.your_name_required));
            bluetoothRadioButton.setChecked(false);
        } else {
            playerTwoNameEditText.setText("");
            BluetoothDialogFragment bluetoothDialogFragment = BluetoothDialogFragment.newInstance();
            bluetoothDialogFragment.show(getSupportFragmentManager(), "bluetoothDialogFragment");
        }
    }

    @OnClick(R.id.play_btn_id)
    public void play(View view) {
        switch (playerTypeRadioGroup.getCheckedRadioButtonId()) {
            case R.id.android_rbtn_id:

                cDialog = new CDialog.Builder(MainActivity.this)
                        .setTitle(getString(R.string.choose_difficulty))
                        .setPositiveButton(getString(R.string.easy), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                playerOneName = playerOneNameEditText.getText().toString();
                                playerTwoName = playerTwoNameEditText.getText().toString();
                                Intent deviceIntent = new Intent(MainActivity.this, GameActivity.class);
                                deviceIntent.putExtra("BOARD_RADIUS", boardSize);
                                deviceIntent.putExtra("PLAYER_ONE_NAME", playerOneName);
                                deviceIntent.putExtra("PLAYER_TWO_NAME", playerTwoName);
                                deviceIntent.putExtra("GAME_TYPE", BoardView.ANDROID_PLAYER);
                                deviceIntent.putExtra("GAME_DIFFICULTY", BoardView.EASY);
                                deviceIntent.putExtra("TYPE", "");
                                startActivityForResult(deviceIntent, GAME_ANDROID_REQUEST);
                            }
                        })
                        .setNegativeButton(getString(R.string.hard), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                playerOneName = playerOneNameEditText.getText().toString();
                                playerTwoName = playerTwoNameEditText.getText().toString();
                                Intent deviceIntent = new Intent(MainActivity.this, GameActivity.class);
                                deviceIntent.putExtra("BOARD_RADIUS", boardSize);
                                deviceIntent.putExtra("PLAYER_ONE_NAME", playerOneName);
                                deviceIntent.putExtra("PLAYER_TWO_NAME", playerTwoName);
                                deviceIntent.putExtra("GAME_TYPE", BoardView.ANDROID_PLAYER);
                                deviceIntent.putExtra("GAME_DIFFICULTY", BoardView.HARD);
                                deviceIntent.putExtra("TYPE", "");
                                startActivityForResult(deviceIntent, GAME_ANDROID_REQUEST);
                            }
                        })
                        .setCanceledOnTouchOutside(false)
                        .show();
                cDialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                break;
            case R.id.friend_rbtn_id:
                playerOneName = playerOneNameEditText.getText().toString();
                playerTwoName = playerTwoNameEditText.getText().toString();
                Intent localIntent = new Intent(MainActivity.this, GameActivity.class);
                localIntent.putExtra("BOARD_RADIUS", boardSize);
                localIntent.putExtra("PLAYER_ONE_NAME", playerOneName);
                localIntent.putExtra("PLAYER_TWO_NAME", playerTwoName);
                localIntent.putExtra("GAME_TYPE", BoardView.FRIEND_PLAYER);
                localIntent.putExtra("TYPE", "");
                startActivityForResult(localIntent, GAME_FRIEND_REQUEST);
                break;
            case R.id.bluetooth_rbtn_id:
                progressBar.setVisibility(View.VISIBLE);
                connectingThread = new ConnectingThread(bluetoothDevice);
                connectingThread.start();
                playButton.setEnabled(false);
                break;
        }
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        bluetoothSocket = socket;
        bluetoothDevice = device;
    }

    public void sendData() {
        final Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra("BOARD_RADIUS", boardSize);
        intent.putExtra("PLAYER_ONE_NAME", playerOneNameEditText.getText().toString());
        intent.putExtra("PLAYER_TWO_NAME", bluetoothDevice.getName());
        intent.putExtra("GAME_TYPE", BoardView.BLUETOOTH_PLAYER);
        intent.putExtra("TYPE", "sender");
        startActivityForResult(intent, GAME_BLUETOOTH_REQUEST);
    }

    public void sendData1() {
        final Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra("BOARD_RADIUS", boardSize);
        intent.putExtra("PLAYER_ONE_NAME", playerOneNameEditText.getText().toString());
        intent.putExtra("PLAYER_TWO_NAME", bluetoothDevice.getName());
        intent.putExtra("GAME_TYPE", BoardView.BLUETOOTH_PLAYER);
        intent.putExtra("TYPE", "receiver");
        startActivityForResult(intent, GAME_BLUETOOTH_REQUEST);
    }

    private class ConnectingThread extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final BluetoothDevice bluetoothDevice;
        public ConnectingThread(BluetoothDevice device) {
            BluetoothSocket temp = null;
            bluetoothDevice = device;
            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                temp = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                e.printStackTrace();
            }
            bluetoothSocket = temp;
        }

        public void run() {
            try {
                bluetoothSocket.connect();
            } catch (IOException connectException) {
                connectException.printStackTrace();
                try {
                    bluetoothSocket.close();
                } catch (IOException closeException) {
                    closeException.printStackTrace();
                }
            }

            if (bluetoothSocket != null && bluetoothDevice != null) {
                connected(bluetoothSocket, bluetoothDevice);
                sendData();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.INVISIBLE);
                    }
                });
            }

        }

        // Cancel an open connection and terminate the thread
        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        playButton.setEnabled(true);
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, R.string.click_back_again, Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                doubleBackToExitPressedOnce=false;
            }
        }, 2000);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        androidRadioButton.setChecked(true);
        playerTwoNameEditText.setText(deviceName);
    }
}
