package com.asb.otic.ui.custom;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.asb.otic.R;
import com.asb.otic.data.DatabaseHandler;
import com.asb.otic.model.Score;
import com.asb.otic.ui.activity.BaseActivity;
import com.asb.otic.ui.activity.GameActivity;
import com.asb.otic.ui.activity.MainActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Created by Amr Alsaidy on 2017/11/1.
 */

public class BoardView extends FrameLayout implements View.OnTouchListener {

    private String TAG = "TEST_TAG";
    // Aspect ratio of the view (4:3).
    private static final float VIEW_ASPECT_RATIO = (float) Math.sqrt(4.0 / 3.0);
    // Duration of the animation for the whole view (all swatches).
    private static final int ANIM_TIME_VIEW = 500;
    // Duration of the animation for a single swatch.
    private static final int ANIM_TIME_SWATCH = 200;
    // Default palette radius (if not specified).
    private static final int DEFAULT_PALETTE_RADIUS = 3;

    public final static int ANDROID_PLAYER = 1;
    public final static int FRIEND_PLAYER = 2;
    public final static int BLUETOOTH_PLAYER = 3;

    public final static int EASY = 1;
    public final static int HARD = 2;
    // Selected swatch index
    private int selectedSwatch;
    // Swatch views pivot point (pixels)
    private PointF swatchPivot;
    // Swatch views scale (pixels)
    private PointF swatchScale;
    // Check mark (selected color swatch)
    private AppCompatImageView checkerImageView;
    // Swatch shadow
    private GradientDrawable shadowDrawable;
    // Animation interpolator
    private final Interpolator interpolator = new OvershootInterpolator();

    private Swatch swatch;
    private int swatchCount;
    private int gameType;
    private int boardRadius;
    private Swatch[] swatches;
    private ArrayList<String> registers = new ArrayList<>();
    private static boolean[] swatchSelects;
    public int turn = 0;
    private int player1Score = 0;
    private int player2Score = 0;
    private int difficulty;
    private static ConnectedThread connectedThread = null;
    private BluetoothSocket bluetoothSocket;
    private DatabaseHandler databaseHandler;
    private boolean isWin = false;
    private boolean isDraw = false;
    public boolean touchEnabled = true;
    public boolean soundEnabled = true;
    private MediaPlayer mediaPlayer1 = MediaPlayer.create(getContext(), R.raw.tone1);
    private MediaPlayer mediaPlayer2 = MediaPlayer.create(getContext(), R.raw.tone2);
    GameActivity gameActivity;
    private OnSwatchSelectedListener onSwatchSelectedListener;
    private OnPlayerChangedListener onPlayerChangedListener;
    private OnResultChangedListener onResultChangedListener;
    private CDialog cDialog;

    public interface OnSwatchSelectedListener {
        void onSwatchSelected();
    }

    public interface OnPlayerChangedListener {
        void onPlayerChanged();
    }

    public interface OnResultChangedListener {
        void onResultChanged(int score1, int score2, int turn);
    }

    public BoardView(@NonNull Context context) {
        super(context);
        init(null, 0);
    }

    public BoardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);

        gameActivity = (GameActivity) context;
        databaseHandler = new DatabaseHandler(context);
    }

    public BoardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs, defStyleAttr);
    }

    private void initBluetooth() {
        bluetoothSocket = MainActivity.bluetoothSocket;
        connectedThread = new ConnectedThread(bluetoothSocket);
        connectedThread.start();
    }

    public void init(AttributeSet attrs, int defStyleAttr) {
        final TypedArray a = getContext().getTheme().obtainStyledAttributes(
                attrs, R.styleable.BoardView, defStyleAttr, defStyleAttr);

        boardRadius = a.getInteger(R.styleable.BoardView_boardRadius, DEFAULT_PALETTE_RADIUS);;
        a.recycle();

        checkerImageView = new AppCompatImageView(getContext());
        checkerImageView.setImageResource(R.drawable.ic_colorpicker_swatch_selected);

        shadowDrawable = new GradientDrawable();
        shadowDrawable.setShape(GradientDrawable.OVAL);
        selectedSwatch = 0;
        onSwatchSelectedListener = null;
        initSwatches();
    }

    public void setAttrs(final int gameType, final int difficulty, final int boardRadius, final OnSwatchSelectedListener onSwatchSelectedListener,
                         final OnPlayerChangedListener onPlayerChangedListener, final OnResultChangedListener onResultChangedListener) {
        this.gameType = gameType;
        this.difficulty = difficulty;
        this.boardRadius = boardRadius;
        this.onSwatchSelectedListener = onSwatchSelectedListener;
        this.onPlayerChangedListener = onPlayerChangedListener;
        this.onResultChangedListener = onResultChangedListener;
        if (gameType == BLUETOOTH_PLAYER) {
            initBluetooth();
        }
        initSwatches();
    }

    public void initSwatches() {
        removeAllViews();
        swatchCount = getSwatchCount(boardRadius);
        swatches = new Swatch[swatchCount];
        swatchSelects = new boolean[swatches.length];
        int index = 0;
        for (int y = -boardRadius * 2; y <= boardRadius * 2; y += 2) {
            final int rowSize = boardRadius * 2 - Math.abs(y / 2);
            for (int x = -rowSize; x <= rowSize; x += 2) {
                final PointF position = new PointF((float) x / (boardRadius * 2 + 1), (float) y / (boardRadius * 2 + 1));
                final int animDelay = (ANIM_TIME_VIEW - ANIM_TIME_SWATCH) * index++ / swatchCount;
                swatch = new Swatch(getContext(), 0, index, Color.WHITE, position, animDelay, shadowDrawable);
                addView(swatch);

                swatch.setOnTouchListener(this);
            }
        }

        if (index != swatchCount) {
            throw new IllegalStateException("The number of color swatches and palette radius are inconsistent.");
        }

        for (int i = 0; i < swatches.length; i++) {
            swatches[i] = new Swatch(getContext(), null, 0, i);
        }

        addView(checkerImageView);
        checkerImageView.setVisibility(INVISIBLE);
        updateSwatchesPosition();

    }

    @Override
    public boolean onTouch(final View view, MotionEvent motionEvent) {
        swatch = (Swatch) view;
        selectedSwatch = swatch.index - 1;
        updateCheckerPosition(swatch);
        for (int i = 0; i < swatches.length; i++) {
            swatches[i] = (Swatch) getChildAt(i);
        }
        if (gameType == ANDROID_PLAYER) {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN && touchEnabled) {
                if (swatches[selectedSwatch].id == 0) {
                    if (swatchSelects[selectedSwatch]) {
                        Toast.makeText(getContext(), "Already selected", Toast.LENGTH_LONG).show();
                    } else {
                        if (soundEnabled) mediaPlayer1.start();
                        turn++;
                        checkerImageView.setVisibility(VISIBLE);
                        swatches[selectedSwatch].drawable.setColor(BaseActivity.COLOR3);
                        swatches[selectedSwatch].setSelected(true);
                        swatchSelects[selectedSwatch] = swatches[selectedSwatch].isSelected();
                        swatches[selectedSwatch].id++;
                        onSwatchSelectedListener.onSwatchSelected();
                        check(selectedSwatch, true);
                        touchEnabled = false;
                        if (!isWin && !isDraw) {
                            Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    algo();
                                    touchEnabled = true;
                                }
                            }, 2000);
                        }
                        onResultChangedListener.onResultChanged(player1Score, player2Score, turn);
                        int count = 0;
                        for (int i = 0; i < swatchCount; i++) {
                            if (swatchSelects[i]) count++;
                        }
                        if (count == swatchCount) {
                            gameFinished();
                        }
                        updateCheckerPosition(swatch);
                    }
                }
            }

        } else if (gameType == FRIEND_PLAYER) {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN && touchEnabled) {
                if (swatches[selectedSwatch].id == 0) {
                    if (swatchSelects[selectedSwatch]) {
                        Toast.makeText(getContext(), "Already selected", Toast.LENGTH_LONG).show();
                    } else {
                        if (soundEnabled) mediaPlayer1.start();
                        turn++;
                        swatches[selectedSwatch].drawable.setColor(BaseActivity.COLOR3);
                        swatches[selectedSwatch].setSelected(true);
                        swatchSelects[selectedSwatch] = swatches[selectedSwatch].isSelected();
                        swatches[selectedSwatch].id++;
                        checkerImageView.setVisibility(VISIBLE);
                        onSwatchSelectedListener.onSwatchSelected();
                        if (turn % 2 == 0) {
                            swatches[selectedSwatch].id++;
                            onPlayerChangedListener.onPlayerChanged();
                        }
                        check(selectedSwatch, true);
                        onResultChangedListener.onResultChanged(player1Score, player2Score, turn);
                        int count = 0;
                        for (int i = 0; i < swatchCount; i++) {
                            if (swatchSelects[i]) count++;
                        }
                        if (count == swatchCount) {
                            gameFinished();
                        }
                        updateCheckerPosition(swatch);
                    }
                }
            }
        } else if (gameType == BLUETOOTH_PLAYER) {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN && touchEnabled) {
                if (swatches[selectedSwatch].id == 0) {
                    if (swatchSelects[selectedSwatch]) {
                        Toast.makeText(getContext(), "Already selected", Toast.LENGTH_LONG).show();
                    } else {
                        if (soundEnabled) mediaPlayer1.start();
                        turn++;
                        swatches[selectedSwatch].id++;
                        if (turn % 2 == 0) {
                            swatches[selectedSwatch].id++;
                        }
                        swatches[selectedSwatch].drawable.setColor(BaseActivity.COLOR3);
                        swatches[selectedSwatch].setSelected(true);
                        swatchSelects[selectedSwatch] = swatches[selectedSwatch].isSelected();
                        onSwatchSelectedListener.onSwatchSelected();
                        check(selectedSwatch, true);
                        onResultChangedListener.onResultChanged(player1Score, player2Score, turn);
                        checkerImageView.setVisibility(VISIBLE);
                        updateCheckerPosition(swatch);
                        if (connectedThread != null) {
                            String status = "";
                            status = status.concat(String.valueOf(selectedSwatch));
                            status = status.concat(";" + String.valueOf(player1Score));
                            status = status.concat(";" + String.valueOf(player2Score));
                            status = status.concat(";" + String.valueOf(turn));
                            status = status.concat(";" + String.valueOf(swatches[selectedSwatch].id));
                            byte[] bytes = status.getBytes();
                            connectedThread.write(bytes);
                            touchEnabled = false;
                        }

                        int count = 0;
                        for (int i = 0; i < swatchCount; i++) {
                            if (swatchSelects[i]) count++;
                        }
                        if (count == swatchCount) {
                            gameFinished();
                        }

                    }

                }
            }
        }
        return true;
    }

    private void gameFinished() {
        Score score = new Score();
        if (gameActivity.type.equals("receiver")) {
            String temp1 = gameActivity.playerOneName;
            gameActivity.playerOneName = gameActivity.playerTwoName;
            gameActivity.playerTwoName = temp1;
        }
        if (player1Score > player2Score) {
            score.setSize(gameActivity.boardSize);
            score.setName(gameActivity.playerOneName);
            databaseHandler.addWin(score);
            cDialog = new CDialog.Builder(gameActivity)
                    .setTitle(gameActivity.getString(R.string.game_finished))
                    .setMessage(gameActivity.playerOneName + " " + gameActivity.getString(R.string.win))
                    .setPositiveButton(gameActivity.getString(R.string.play_again), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            gameActivity.myText1.setText("0");
                            gameActivity.myText2.setText("0");
                            gameActivity.playerOneLinearLayout.setBackgroundColor(getResources().getColor(R.color.opacity25));
                            gameActivity.playerTwoLinearLayout.setBackgroundColor(getResources().getColor(R.color.opacity25));
                            player1Score = 0;
                            player2Score = 0;
                            turn = 0;
                            swatchSelects = new boolean[swatchCount];
                            registers = new ArrayList<>();
                            swatches[selectedSwatch].id = 0;
                            setAttrs(gameActivity.gameType, gameActivity.difficulty, gameActivity.boardSize,
                                    gameActivity, gameActivity, gameActivity);
                            if (gameActivity.type.equals("sender")) {
                                touchEnabled = true;
                            } else if (gameActivity.type.equals("receiver")) {
                                touchEnabled = false;
                            }
                        }
                    })
                    .setNegativeButton(gameActivity.getString(R.string.exit), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            gameActivity.finish();
                        }
                    })
                    .setCancelable(false)
                    .show();
            cDialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        } else if (player1Score < player2Score) {
            score.setSize(gameActivity.boardSize);
            score.setName(gameActivity.playerTwoName);
            databaseHandler.addWin(score);
            cDialog = new CDialog.Builder(gameActivity)
                    .setTitle(gameActivity.getString(R.string.game_finished))
                    .setMessage(gameActivity.playerTwoName + " " + gameActivity.getString(R.string.win))
                    .setPositiveButton(gameActivity.getString(R.string.play_again), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            gameActivity.myText1.setText("0");
                            gameActivity.myText2.setText("0");
                            gameActivity.playerOneLinearLayout.setBackgroundColor(getResources().getColor(R.color.opacity25));
                            gameActivity.playerTwoLinearLayout.setBackgroundColor(getResources().getColor(R.color.opacity25));
                            player1Score = 0;
                            player2Score = 0;
                            turn = 0;
                            swatchSelects = new boolean[swatchCount];
                            registers = new ArrayList<>();
                            swatches[selectedSwatch].id = 0;
                            setAttrs(gameActivity.gameType, gameActivity.difficulty, gameActivity.boardSize,
                                    gameActivity, gameActivity, gameActivity);
                            if (gameActivity.type.equals("sender")) {
                                touchEnabled = true;
                            } else if (gameActivity.type.equals("receiver")) {
                                touchEnabled = false;
                            }
                        }
                    })
                    .setNegativeButton(gameActivity.getString(R.string.exit), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            gameActivity.finish();
                        }
                    })
                    .setCanceledOnTouchOutside(false)
                    .show();
            cDialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        } else if (player1Score == player2Score) {
            cDialog = new CDialog.Builder(gameActivity)
                    .setTitle(gameActivity.getString(R.string.game_finished))
                    .setMessage(gameActivity.getString(R.string.the_game_draw))
                    .setPositiveButton(gameActivity.getString(R.string.play_again), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Log.i(TAG, "--" + gameActivity.myText1.getText().toString());
                            Log.i(TAG, "--" + gameActivity.myText2.getText().toString());
                            gameActivity.myText1.setText("0");
                            gameActivity.myText2.setText("0");
                            gameActivity.playerOneLinearLayout.setBackgroundColor(getResources().getColor(R.color.opacity25));
                            gameActivity.playerTwoLinearLayout.setBackgroundColor(getResources().getColor(R.color.opacity25));
                            player1Score = 0;
                            player2Score = 0;
                            turn = 0;
                            swatchSelects = new boolean[swatchCount];
                            registers = new ArrayList<>();
                            swatches[selectedSwatch].id = 0;
                            setAttrs(gameActivity.gameType, gameActivity.difficulty, gameActivity.boardSize,
                                    gameActivity, gameActivity, gameActivity);
                            if (gameActivity.type.equals("sender")) {
                                touchEnabled = true;
                            } else if (gameActivity.type.equals("receiver")) {
                                touchEnabled = false;
                            }
                        }
                    })
                    .setNegativeButton(gameActivity.getString(R.string.exit), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            gameActivity.finish();
                        }
                    })
                    .setCancelable(false)
                    .show();
            cDialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        }
    }

    private void check(int x, boolean isfull) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(GradientDrawable.OVAL);
        gradientDrawable.setColor(BaseActivity.COLOR3);
        gradientDrawable.setStroke(4, BaseActivity.COLOR4);
        if (boardRadius == 2) {
            if (swatchSelects[0] && swatchSelects[1] && swatchSelects[2] && !registers.contains("012")) {
                registers.add("012");
                swatches[0].setImageDrawable(gradientDrawable);
                swatches[1].setImageDrawable(gradientDrawable);
                swatches[2].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 3;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 3;
                    }
                }
            }
            if (swatchSelects[2] && swatchSelects[6] && swatchSelects[11] && !registers.contains("2611")) {
                registers.add("2611");
                swatches[2].setImageDrawable(gradientDrawable);
                swatches[6].setImageDrawable(gradientDrawable);
                swatches[11].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 3;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 3;
                    }
                }
            }
            if (swatchSelects[11] && swatchSelects[15] && swatchSelects[18] && !registers.contains("111518")) {
                registers.add("111518");
                swatches[11].setImageDrawable(gradientDrawable);
                swatches[15].setImageDrawable(gradientDrawable);
                swatches[18].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 3;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 3;
                    }
                }
            }
            if (swatchSelects[0] && swatchSelects[3] && swatchSelects[7] && !registers.contains("037")) {
                registers.add("037");
                swatches[0].setImageDrawable(gradientDrawable);
                swatches[3].setImageDrawable(gradientDrawable);
                swatches[7].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 3;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 3;
                    }
                }
            }
            if (swatchSelects[7] && swatchSelects[12] && swatchSelects[16] && !registers.contains("71216")) {
                registers.add("71216");
                swatches[7].setImageDrawable(gradientDrawable);
                swatches[12].setImageDrawable(gradientDrawable);
                swatches[16].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 3;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 3;
                    }
                }
            }
            if (swatchSelects[16] && swatchSelects[17] && swatchSelects[18] && !registers.contains("161718")) {
                registers.add("161718");
                swatches[16].setImageDrawable(gradientDrawable);
                swatches[17].setImageDrawable(gradientDrawable);
                swatches[18].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 3;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 3;
                    }
                }
            }

            // 4

            if (swatchSelects[1] && swatchSelects[4] && swatchSelects[8] && swatchSelects[12] && !registers.contains("14812")) {
                registers.add("14812");
                swatches[1].setImageDrawable(gradientDrawable);
                swatches[4].setImageDrawable(gradientDrawable);
                swatches[8].setImageDrawable(gradientDrawable);
                swatches[12].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 4;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 4;
                    }
                }
            }
            if (swatchSelects[12] && swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && !registers.contains("12131415")) {
                registers.add("12131415");
                swatches[12].setImageDrawable(gradientDrawable);
                swatches[13].setImageDrawable(gradientDrawable);
                swatches[14].setImageDrawable(gradientDrawable);
                swatches[15].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 4;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 4;
                    }
                }
            }
            if (swatchSelects[1] && swatchSelects[5] && swatchSelects[10] && swatchSelects[15] && !registers.contains("151015")) {
                registers.add("151015");
                swatches[1].setImageDrawable(gradientDrawable);
                swatches[5].setImageDrawable(gradientDrawable);
                swatches[10].setImageDrawable(gradientDrawable);
                swatches[15].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 4;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 4;
                    }
                }
            }
            if (swatchSelects[3] && swatchSelects[4] && swatchSelects[5] && swatchSelects[6] && !registers.contains("3456")) {
                registers.add("3456");
                swatches[3].setImageDrawable(gradientDrawable);
                swatches[4].setImageDrawable(gradientDrawable);
                swatches[5].setImageDrawable(gradientDrawable);
                swatches[6].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 4;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 4;
                    }
                }
            }
            if (swatchSelects[6] && swatchSelects[10] && swatchSelects[14] && swatchSelects[17] && !registers.contains("6101417")) {
                registers.add("6101417");
                swatches[6].setImageDrawable(gradientDrawable);
                swatches[10].setImageDrawable(gradientDrawable);
                swatches[14].setImageDrawable(gradientDrawable);
                swatches[17].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 4;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 4;
                    }
                }
            }
            if (swatchSelects[3] && swatchSelects[8] && swatchSelects[13] && swatchSelects[17] && !registers.contains("381317")) {
                registers.add("381317");
                swatches[3].setImageDrawable(gradientDrawable);
                swatches[8].setImageDrawable(gradientDrawable);
                swatches[13].setImageDrawable(gradientDrawable);
                swatches[17].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 4;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 4;
                    }
                }
            }

            // 5

            if (swatchSelects[0] && swatchSelects[4] && swatchSelects[9] && swatchSelects[14] && swatchSelects[18]
                    && !registers.contains("0491418")) {
                registers.add("0491418");
                swatches[0].setImageDrawable(gradientDrawable);
                swatches[4].setImageDrawable(gradientDrawable);
                swatches[9].setImageDrawable(gradientDrawable);
                swatches[14].setImageDrawable(gradientDrawable);
                swatches[18].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 5;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 5;
                    }
                }
            }
            if (swatchSelects[2] && swatchSelects[5] && swatchSelects[9] && swatchSelects[13] && swatchSelects[16]
                    && !registers.contains("2591316")) {
                registers.add("2591316");
                swatches[2].setImageDrawable(gradientDrawable);
                swatches[5].setImageDrawable(gradientDrawable);
                swatches[9].setImageDrawable(gradientDrawable);
                swatches[13].setImageDrawable(gradientDrawable);
                swatches[16].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 5;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 5;
                    }
                }
            }
            if (swatchSelects[7] && swatchSelects[8] && swatchSelects[9] && swatchSelects[10] && swatchSelects[11]
                    && !registers.contains("7891011")) {
                registers.add("7891011");
                swatches[7].setImageDrawable(gradientDrawable);
                swatches[8].setImageDrawable(gradientDrawable);
                swatches[9].setImageDrawable(gradientDrawable);
                swatches[10].setImageDrawable(gradientDrawable);
                swatches[11].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 5;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 5;
                    }
                }
            }

        } else if (boardRadius == 3) {
            //4
            if (swatchSelects[0] && swatchSelects[1] && swatchSelects[2] && swatchSelects[3] && !registers.contains("0123")) {
                registers.add("0123");
                swatches[0].setImageDrawable(gradientDrawable);
                swatches[1].setImageDrawable(gradientDrawable);
                swatches[2].setImageDrawable(gradientDrawable);
                swatches[3].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 4;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 4;
                    }
                }
            }
            if (swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36] && !registers.contains("33343536")) {
                registers.add("33343536");
                swatches[33].setImageDrawable(gradientDrawable);
                swatches[34].setImageDrawable(gradientDrawable);
                swatches[35].setImageDrawable(gradientDrawable);
                swatches[36].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 4;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 4;
                    }
                }
            }
            if (swatchSelects[0] && swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && !registers.contains("04915")) {
                registers.add("04915");
                swatches[0].setImageDrawable(gradientDrawable);
                swatches[4].setImageDrawable(gradientDrawable);
                swatches[9].setImageDrawable(gradientDrawable);
                swatches[15].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 4;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 4;
                    }
                }
            }
            if (swatchSelects[21] && swatchSelects[27] && swatchSelects[32] && swatchSelects[36] && !registers.contains("21273236")) {
                registers.add("21273236");
                swatches[21].setImageDrawable(gradientDrawable);
                swatches[27].setImageDrawable(gradientDrawable);
                swatches[32].setImageDrawable(gradientDrawable);
                swatches[36].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 4;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 4;
                    }
                }
            }
            if (swatchSelects[3] && swatchSelects[8] && swatchSelects[14] && swatchSelects[21] && !registers.contains("381421")) {
                registers.add("381421");
                swatches[3].setImageDrawable(gradientDrawable);
                swatches[8].setImageDrawable(gradientDrawable);
                swatches[14].setImageDrawable(gradientDrawable);
                swatches[21].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 4;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 4;
                    }
                }
            }
            if (swatchSelects[15] && swatchSelects[22] && swatchSelects[28] && swatchSelects[33] && !registers.contains("15222833")) {
                registers.add("15222833");
                swatches[15].setImageDrawable(gradientDrawable);
                swatches[22].setImageDrawable(gradientDrawable);
                swatches[28].setImageDrawable(gradientDrawable);
                swatches[33].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 4;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 4;
                    }
                }
            }

            // 5
            if (swatchSelects[4] && swatchSelects[5] && swatchSelects[6] && swatchSelects[7] && swatchSelects[8]
                    && !registers.contains("45678")) {
                registers.add("45678");
                swatches[4].setImageDrawable(gradientDrawable);
                swatches[5].setImageDrawable(gradientDrawable);
                swatches[6].setImageDrawable(gradientDrawable);
                swatches[7].setImageDrawable(gradientDrawable);
                swatches[8].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 5;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 5;
                    }
                }
            }
            if (swatchSelects[28] && swatchSelects[29] && swatchSelects[30] && swatchSelects[31] && swatchSelects[32]
                    && !registers.contains("2829303132")) {
                registers.add("2829303132");
                swatches[28].setImageDrawable(gradientDrawable);
                swatches[29].setImageDrawable(gradientDrawable);
                swatches[30].setImageDrawable(gradientDrawable);
                swatches[31].setImageDrawable(gradientDrawable);
                swatches[32].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 5;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 5;
                    }
                }
            }
            if (swatchSelects[1] && swatchSelects[5] && swatchSelects[10] && swatchSelects[16] && swatchSelects[22]
                    && !registers.contains("15101622")) {
                registers.add("15101622");
                swatches[1].setImageDrawable(gradientDrawable);
                swatches[5].setImageDrawable(gradientDrawable);
                swatches[10].setImageDrawable(gradientDrawable);
                swatches[16].setImageDrawable(gradientDrawable);
                swatches[22].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 5;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 5;
                    }
                }
            }
            if (swatchSelects[14] && swatchSelects[20] && swatchSelects[26] && swatchSelects[31] && swatchSelects[35]
                    && !registers.contains("1420263135")) {
                registers.add("1420263135");
                swatches[14].setImageDrawable(gradientDrawable);
                swatches[20].setImageDrawable(gradientDrawable);
                swatches[26].setImageDrawable(gradientDrawable);
                swatches[31].setImageDrawable(gradientDrawable);
                swatches[35].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 5;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 5;
                    }
                }
            }
            if (swatchSelects[2] && swatchSelects[7] && swatchSelects[13] && swatchSelects[20] && swatchSelects[27]
                    && !registers.contains("27132027")) {
                registers.add("27132027");
                swatches[2].setImageDrawable(gradientDrawable);
                swatches[7].setImageDrawable(gradientDrawable);
                swatches[13].setImageDrawable(gradientDrawable);
                swatches[20].setImageDrawable(gradientDrawable);
                swatches[27].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 5;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 5;
                    }
                }
            }
            if (swatchSelects[9] && swatchSelects[16] && swatchSelects[23] && swatchSelects[29] && swatchSelects[34]
                    && !registers.contains("916232934")) {
                registers.add("916232934");
                swatches[9].setImageDrawable(gradientDrawable);
                swatches[16].setImageDrawable(gradientDrawable);
                swatches[23].setImageDrawable(gradientDrawable);
                swatches[29].setImageDrawable(gradientDrawable);
                swatches[34].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 5;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 5;
                    }
                }
            }

            // 6
            if (swatchSelects[9] && swatchSelects[10] && swatchSelects[11] && swatchSelects[12] && swatchSelects[13]
                    && swatchSelects[14] && !registers.contains("91011121314")) {
                registers.add("91011121314");
                swatches[9].setImageDrawable(gradientDrawable);
                swatches[10].setImageDrawable(gradientDrawable);
                swatches[11].setImageDrawable(gradientDrawable);
                swatches[12].setImageDrawable(gradientDrawable);
                swatches[13].setImageDrawable(gradientDrawable);
                swatches[14].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 6;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 6;
                    }
                }
            }
            if (swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26]
                    && swatchSelects[27] && !registers.contains("222324252627")) {
                registers.add("222324252627");
                swatches[22].setImageDrawable(gradientDrawable);
                swatches[23].setImageDrawable(gradientDrawable);
                swatches[24].setImageDrawable(gradientDrawable);
                swatches[25].setImageDrawable(gradientDrawable);
                swatches[26].setImageDrawable(gradientDrawable);
                swatches[27].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 6;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 6;
                    }
                }
            }
            if (swatchSelects[2] && swatchSelects[6] && swatchSelects[11] && swatchSelects[17] && swatchSelects[23]
                    && swatchSelects[28] && !registers.contains("2611172328")) {
                registers.add("2611172328");
                swatches[2].setImageDrawable(gradientDrawable);
                swatches[6].setImageDrawable(gradientDrawable);
                swatches[11].setImageDrawable(gradientDrawable);
                swatches[17].setImageDrawable(gradientDrawable);
                swatches[23].setImageDrawable(gradientDrawable);
                swatches[28].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 6;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 6;
                    }
                }
            }
            if (swatchSelects[8] && swatchSelects[13] && swatchSelects[19] && swatchSelects[25] && swatchSelects[30]
                    && swatchSelects[34] && !registers.contains("81319253034")) {
                registers.add("81319253034");
                swatches[8].setImageDrawable(gradientDrawable);
                swatches[13].setImageDrawable(gradientDrawable);
                swatches[19].setImageDrawable(gradientDrawable);
                swatches[25].setImageDrawable(gradientDrawable);
                swatches[30].setImageDrawable(gradientDrawable);
                swatches[34].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 6;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 6;
                    }
                }
            }
            if (swatchSelects[1] && swatchSelects[6] && swatchSelects[12] && swatchSelects[19] && swatchSelects[26]
                    && swatchSelects[32] && !registers.contains("1612192632")) {
                registers.add("1612192632");
                swatches[1].setImageDrawable(gradientDrawable);
                swatches[6].setImageDrawable(gradientDrawable);
                swatches[12].setImageDrawable(gradientDrawable);
                swatches[19].setImageDrawable(gradientDrawable);
                swatches[26].setImageDrawable(gradientDrawable);
                swatches[32].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 6;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 6;
                    }
                }
            }
            if (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[24] && swatchSelects[30]
                    && swatchSelects[35] && !registers.contains("41017243035")) {
                registers.add("41017243035");
                swatches[4].setImageDrawable(gradientDrawable);
                swatches[10].setImageDrawable(gradientDrawable);
                swatches[17].setImageDrawable(gradientDrawable);
                swatches[24].setImageDrawable(gradientDrawable);
                swatches[30].setImageDrawable(gradientDrawable);
                swatches[35].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 6;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 6;
                    }
                }
            }

            // 7
            if (swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18] && swatchSelects[19]
                    && swatchSelects[20] && swatchSelects[21] && !registers.contains("15161718192021")) {
                registers.add("15161718192021");
                swatches[15].setImageDrawable(gradientDrawable);
                swatches[16].setImageDrawable(gradientDrawable);
                swatches[17].setImageDrawable(gradientDrawable);
                swatches[18].setImageDrawable(gradientDrawable);
                swatches[19].setImageDrawable(gradientDrawable);
                swatches[20].setImageDrawable(gradientDrawable);
                swatches[21].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 7;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 7;
                    }
                }
            }
            if (swatchSelects[3] && swatchSelects[7] && swatchSelects[12] && swatchSelects[18] && swatchSelects[24]
                    && swatchSelects[29] && swatchSelects[33] && !registers.contains("371218242933")) {
                registers.add("371218242933");
                swatches[3].setImageDrawable(gradientDrawable);
                swatches[7].setImageDrawable(gradientDrawable);
                swatches[12].setImageDrawable(gradientDrawable);
                swatches[18].setImageDrawable(gradientDrawable);
                swatches[24].setImageDrawable(gradientDrawable);
                swatches[29].setImageDrawable(gradientDrawable);
                swatches[33].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 7;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 7;
                    }
                }
            }
            if (swatchSelects[0] && swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[25]
                    && swatchSelects[31] && swatchSelects[36] && !registers.contains("051118253136")) {
                registers.add("051118253136");
                swatches[0].setImageDrawable(gradientDrawable);
                swatches[5].setImageDrawable(gradientDrawable);
                swatches[11].setImageDrawable(gradientDrawable);
                swatches[18].setImageDrawable(gradientDrawable);
                swatches[25].setImageDrawable(gradientDrawable);
                swatches[31].setImageDrawable(gradientDrawable);
                swatches[36].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 7;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 7;
                    }
                }
            }
        } else if (boardRadius == 4) {
            //5
            if (swatchSelects[0] && swatchSelects[1] && swatchSelects[2] && swatchSelects[3] && swatchSelects[4] && !registers.contains("01234")) {
                registers.add("01234");
                swatches[0].setImageDrawable(gradientDrawable);
                swatches[1].setImageDrawable(gradientDrawable);
                swatches[2].setImageDrawable(gradientDrawable);
                swatches[3].setImageDrawable(gradientDrawable);
                swatches[4].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 5;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 5;
                    }
                }
            }
            if (swatchSelects[56] && swatchSelects[57] && swatchSelects[58] && swatchSelects[59] && swatchSelects[60] && !registers.contains("5657585960")) {
                registers.add("5657585960");
                swatches[56].setImageDrawable(gradientDrawable);
                swatches[57].setImageDrawable(gradientDrawable);
                swatches[58].setImageDrawable(gradientDrawable);
                swatches[59].setImageDrawable(gradientDrawable);
                swatches[60].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 5;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 5;
                    }
                }
            }
            if (swatchSelects[0] && swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && !registers.contains("05111826")) {
                registers.add("05111826");
                swatches[0].setImageDrawable(gradientDrawable);
                swatches[5].setImageDrawable(gradientDrawable);
                swatches[11].setImageDrawable(gradientDrawable);
                swatches[18].setImageDrawable(gradientDrawable);
                swatches[26].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 5;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 5;
                    }
                }
            }
            if (swatchSelects[34] && swatchSelects[42] && swatchSelects[49] && swatchSelects[55] && swatchSelects[60] && !registers.contains("3442495560")) {
                registers.add("3442495560");
                swatches[34].setImageDrawable(gradientDrawable);
                swatches[42].setImageDrawable(gradientDrawable);
                swatches[49].setImageDrawable(gradientDrawable);
                swatches[55].setImageDrawable(gradientDrawable);
                swatches[60].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 5;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 5;
                    }
                }
            }
            if (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && !registers.contains("410172534")) {
                registers.add("410172534");
                swatches[4].setImageDrawable(gradientDrawable);
                swatches[10].setImageDrawable(gradientDrawable);
                swatches[17].setImageDrawable(gradientDrawable);
                swatches[25].setImageDrawable(gradientDrawable);
                swatches[34].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 5;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 5;
                    }
                }
            }
            if (swatchSelects[26] && swatchSelects[35] && swatchSelects[43] && swatchSelects[50] && swatchSelects[56] && !registers.contains("2635435056")) {
                registers.add("2635435056");
                swatches[26].setImageDrawable(gradientDrawable);
                swatches[35].setImageDrawable(gradientDrawable);
                swatches[43].setImageDrawable(gradientDrawable);
                swatches[50].setImageDrawable(gradientDrawable);
                swatches[56].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 5;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 5;
                    }
                }
            }

            // 6
            if (swatchSelects[5] && swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && swatchSelects[9]
                    && swatchSelects[10] && !registers.contains("5678910")) {
                registers.add("5678910");
                swatches[5].setImageDrawable(gradientDrawable);
                swatches[6].setImageDrawable(gradientDrawable);
                swatches[7].setImageDrawable(gradientDrawable);
                swatches[8].setImageDrawable(gradientDrawable);
                swatches[9].setImageDrawable(gradientDrawable);
                swatches[10].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 6;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 6;
                    }
                }
            }
            if (swatchSelects[50] && swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54]
                    && swatchSelects[55] && !registers.contains("505152535455")) {
                registers.add("505152535455");
                swatches[50].setImageDrawable(gradientDrawable);
                swatches[51].setImageDrawable(gradientDrawable);
                swatches[52].setImageDrawable(gradientDrawable);
                swatches[53].setImageDrawable(gradientDrawable);
                swatches[54].setImageDrawable(gradientDrawable);
                swatches[55].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 6;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 6;
                    }
                }
            }
            if (swatchSelects[1] && swatchSelects[6] && swatchSelects[12] && swatchSelects[19] && swatchSelects[27]
                    && swatchSelects[35] && !registers.contains("1612192735")) {
                registers.add("1612192735");
                swatches[1].setImageDrawable(gradientDrawable);
                swatches[6].setImageDrawable(gradientDrawable);
                swatches[12].setImageDrawable(gradientDrawable);
                swatches[19].setImageDrawable(gradientDrawable);
                swatches[27].setImageDrawable(gradientDrawable);
                swatches[35].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 6;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 6;
                    }
                }
            }
            if (swatchSelects[25] && swatchSelects[33] && swatchSelects[41] && swatchSelects[48] && swatchSelects[54]
                    && swatchSelects[59] && !registers.contains("253341485459")) {
                registers.add("253341485459");
                swatches[25].setImageDrawable(gradientDrawable);
                swatches[33].setImageDrawable(gradientDrawable);
                swatches[41].setImageDrawable(gradientDrawable);
                swatches[48].setImageDrawable(gradientDrawable);
                swatches[54].setImageDrawable(gradientDrawable);
                swatches[59].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 6;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 6;
                    }
                }
            }
            if (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33]
                    && swatchSelects[42] && !registers.contains("3916243342")) {
                registers.add("3916243342");
                swatches[3].setImageDrawable(gradientDrawable);
                swatches[9].setImageDrawable(gradientDrawable);
                swatches[16].setImageDrawable(gradientDrawable);
                swatches[24].setImageDrawable(gradientDrawable);
                swatches[33].setImageDrawable(gradientDrawable);
                swatches[42].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 6;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 6;
                    }
                }
            }
            if (swatchSelects[18] && swatchSelects[27] && swatchSelects[36] && swatchSelects[44] && swatchSelects[51]
                    && swatchSelects[57] && !registers.contains("182736445157")) {
                registers.add("182736445157");
                swatches[18].setImageDrawable(gradientDrawable);
                swatches[27].setImageDrawable(gradientDrawable);
                swatches[36].setImageDrawable(gradientDrawable);
                swatches[44].setImageDrawable(gradientDrawable);
                swatches[51].setImageDrawable(gradientDrawable);
                swatches[57].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 6;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 6;
                    }
                }
            }

            // 7
            if (swatchSelects[11] && swatchSelects[12] && swatchSelects[13] && swatchSelects[14] && swatchSelects[15]
                    && swatchSelects[16] && swatchSelects[17] && !registers.contains("11121314151617")) {
                registers.add("11121314151617");
                swatches[11].setImageDrawable(gradientDrawable);
                swatches[12].setImageDrawable(gradientDrawable);
                swatches[13].setImageDrawable(gradientDrawable);
                swatches[14].setImageDrawable(gradientDrawable);
                swatches[15].setImageDrawable(gradientDrawable);
                swatches[16].setImageDrawable(gradientDrawable);
                swatches[17].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 7;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 7;
                    }
                }
            }
            if (swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                    && swatchSelects[48] && swatchSelects[49] && !registers.contains("43444546474849")) {
                registers.add("43444546474849");
                swatches[43].setImageDrawable(gradientDrawable);
                swatches[44].setImageDrawable(gradientDrawable);
                swatches[45].setImageDrawable(gradientDrawable);
                swatches[46].setImageDrawable(gradientDrawable);
                swatches[47].setImageDrawable(gradientDrawable);
                swatches[48].setImageDrawable(gradientDrawable);
                swatches[49].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 7;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 7;
                    }
                }
            }
            if (swatchSelects[2] && swatchSelects[7] && swatchSelects[13] && swatchSelects[20] && swatchSelects[28]
                    && swatchSelects[36] && swatchSelects[43] && !registers.contains("271320283643")) {
                registers.add("271320283643");
                swatches[2].setImageDrawable(gradientDrawable);
                swatches[7].setImageDrawable(gradientDrawable);
                swatches[13].setImageDrawable(gradientDrawable);
                swatches[20].setImageDrawable(gradientDrawable);
                swatches[28].setImageDrawable(gradientDrawable);
                swatches[36].setImageDrawable(gradientDrawable);
                swatches[43].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 7;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 7;
                    }
                }
            }
            if (swatchSelects[17] && swatchSelects[24] && swatchSelects[32] && swatchSelects[40] && swatchSelects[47]
                    && swatchSelects[53] && swatchSelects[58] && !registers.contains("17243240475358")) {
                registers.add("17243240475358");
                swatches[17].setImageDrawable(gradientDrawable);
                swatches[24].setImageDrawable(gradientDrawable);
                swatches[32].setImageDrawable(gradientDrawable);
                swatches[40].setImageDrawable(gradientDrawable);
                swatches[47].setImageDrawable(gradientDrawable);
                swatches[53].setImageDrawable(gradientDrawable);
                swatches[58].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 7;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 7;
                    }
                }
            }
            if (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32]
                    && swatchSelects[41] && swatchSelects[49] && !registers.contains("281523324149")) {
                registers.add("281523324149");
                swatches[2].setImageDrawable(gradientDrawable);
                swatches[8].setImageDrawable(gradientDrawable);
                swatches[15].setImageDrawable(gradientDrawable);
                swatches[23].setImageDrawable(gradientDrawable);
                swatches[32].setImageDrawable(gradientDrawable);
                swatches[41].setImageDrawable(gradientDrawable);
                swatches[49].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 7;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 7;
                    }
                }
            }
            if (swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[37] && swatchSelects[45]
                    && swatchSelects[52] && swatchSelects[58] && !registers.contains("11192837455258")) {
                registers.add("11192837455258");
                swatches[11].setImageDrawable(gradientDrawable);
                swatches[19].setImageDrawable(gradientDrawable);
                swatches[28].setImageDrawable(gradientDrawable);
                swatches[37].setImageDrawable(gradientDrawable);
                swatches[45].setImageDrawable(gradientDrawable);
                swatches[52].setImageDrawable(gradientDrawable);
                swatches[58].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 7;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 7;
                    }
                }
            }

            // 8
            if (swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && swatchSelects[22]
                    && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && !registers.contains("1819202122232425")) {
                registers.add("1819202122232425");
                swatches[18].setImageDrawable(gradientDrawable);
                swatches[19].setImageDrawable(gradientDrawable);
                swatches[20].setImageDrawable(gradientDrawable);
                swatches[21].setImageDrawable(gradientDrawable);
                swatches[22].setImageDrawable(gradientDrawable);
                swatches[23].setImageDrawable(gradientDrawable);
                swatches[24].setImageDrawable(gradientDrawable);
                swatches[25].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 8;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 8;
                    }
                }
            }
            if (swatchSelects[35] && swatchSelects[36] && swatchSelects[37] && swatchSelects[38] && swatchSelects[39]
                    && swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && !registers.contains("3536373839404142")) {
                registers.add("3536373839404142");
                swatches[35].setImageDrawable(gradientDrawable);
                swatches[36].setImageDrawable(gradientDrawable);
                swatches[37].setImageDrawable(gradientDrawable);
                swatches[38].setImageDrawable(gradientDrawable);
                swatches[39].setImageDrawable(gradientDrawable);
                swatches[40].setImageDrawable(gradientDrawable);
                swatches[41].setImageDrawable(gradientDrawable);
                swatches[42].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 8;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 8;
                    }
                }
            }
            if (swatchSelects[3] && swatchSelects[8] && swatchSelects[14] && swatchSelects[21] && swatchSelects[29]
                    && swatchSelects[37] && swatchSelects[44] && swatchSelects[50] && !registers.contains("38142129374450")) {
                registers.add("38142129374450");
                swatches[3].setImageDrawable(gradientDrawable);
                swatches[8].setImageDrawable(gradientDrawable);
                swatches[14].setImageDrawable(gradientDrawable);
                swatches[21].setImageDrawable(gradientDrawable);
                swatches[29].setImageDrawable(gradientDrawable);
                swatches[37].setImageDrawable(gradientDrawable);
                swatches[44].setImageDrawable(gradientDrawable);
                swatches[50].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 8;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 8;
                    }
                }
            }
            if (swatchSelects[10] && swatchSelects[16] && swatchSelects[23] && swatchSelects[31] && swatchSelects[39]
                    && swatchSelects[46] && swatchSelects[52] && swatchSelects[57] && !registers.contains("1016233139465257")) {
                registers.add("1016233139465257");
                swatches[10].setImageDrawable(gradientDrawable);
                swatches[16].setImageDrawable(gradientDrawable);
                swatches[23].setImageDrawable(gradientDrawable);
                swatches[31].setImageDrawable(gradientDrawable);
                swatches[39].setImageDrawable(gradientDrawable);
                swatches[46].setImageDrawable(gradientDrawable);
                swatches[52].setImageDrawable(gradientDrawable);
                swatches[57].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 8;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 8;
                    }
                }
            }
            if (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31]
                    && swatchSelects[40] && swatchSelects[48] && swatchSelects[55] && !registers.contains("17142231404855")) {
                registers.add("17142231404855");
                swatches[1].setImageDrawable(gradientDrawable);
                swatches[7].setImageDrawable(gradientDrawable);
                swatches[14].setImageDrawable(gradientDrawable);
                swatches[22].setImageDrawable(gradientDrawable);
                swatches[31].setImageDrawable(gradientDrawable);
                swatches[40].setImageDrawable(gradientDrawable);
                swatches[48].setImageDrawable(gradientDrawable);
                swatches[55].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 8;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 8;
                    }
                }
            }
            if (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[38]
                    && swatchSelects[46] && swatchSelects[53] && swatchSelects[59] && !registers.contains("512202938465359")) {
                registers.add("512202938465359");
                swatches[5].setImageDrawable(gradientDrawable);
                swatches[12].setImageDrawable(gradientDrawable);
                swatches[20].setImageDrawable(gradientDrawable);
                swatches[29].setImageDrawable(gradientDrawable);
                swatches[38].setImageDrawable(gradientDrawable);
                swatches[46].setImageDrawable(gradientDrawable);
                swatches[53].setImageDrawable(gradientDrawable);
                swatches[59].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 8;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 8;
                    }
                }
            }

            // 9
            if (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30] && swatchSelects[31]
                    && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && !registers.contains("262728293031323334")) {
                registers.add("262728293031323334");
                swatches[26].setImageDrawable(gradientDrawable);
                swatches[27].setImageDrawable(gradientDrawable);
                swatches[28].setImageDrawable(gradientDrawable);
                swatches[29].setImageDrawable(gradientDrawable);
                swatches[30].setImageDrawable(gradientDrawable);
                swatches[31].setImageDrawable(gradientDrawable);
                swatches[32].setImageDrawable(gradientDrawable);
                swatches[33].setImageDrawable(gradientDrawable);
                swatches[34].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 9;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 9;
                    }
                }
            }
            if (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30] && swatchSelects[38]
                    && swatchSelects[45] && swatchSelects[51] && swatchSelects[56] && !registers.contains("4915223038455156")) {
                registers.add("4915223038455156");
                swatches[4].setImageDrawable(gradientDrawable);
                swatches[9].setImageDrawable(gradientDrawable);
                swatches[15].setImageDrawable(gradientDrawable);
                swatches[22].setImageDrawable(gradientDrawable);
                swatches[30].setImageDrawable(gradientDrawable);
                swatches[38].setImageDrawable(gradientDrawable);
                swatches[45].setImageDrawable(gradientDrawable);
                swatches[51].setImageDrawable(gradientDrawable);
                swatches[56].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 9;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 9;
                    }
                }
            }
            if (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] && swatchSelects[39]
                    && swatchSelects[47] && swatchSelects[54] && swatchSelects[60] && !registers.contains("0613213039475460")) {
                registers.add("0613213039475460");
                swatches[0].setImageDrawable(gradientDrawable);
                swatches[6].setImageDrawable(gradientDrawable);
                swatches[13].setImageDrawable(gradientDrawable);
                swatches[21].setImageDrawable(gradientDrawable);
                swatches[30].setImageDrawable(gradientDrawable);
                swatches[39].setImageDrawable(gradientDrawable);
                swatches[47].setImageDrawable(gradientDrawable);
                swatches[54].setImageDrawable(gradientDrawable);
                swatches[60].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 9;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 9;
                    }
                }
            }
        } else if (boardRadius == 5) {
            // 6
            if (swatchSelects[0] && swatchSelects[1] && swatchSelects[2] && swatchSelects[3] && swatchSelects[4] && swatchSelects[5]
                    && !registers.contains("012345")) {
                registers.add("012345");
                swatches[0].setImageDrawable(gradientDrawable);
                swatches[1].setImageDrawable(gradientDrawable);
                swatches[2].setImageDrawable(gradientDrawable);
                swatches[3].setImageDrawable(gradientDrawable);
                swatches[4].setImageDrawable(gradientDrawable);
                swatches[5].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 6;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 6;
                    }
                }
            }
            if (swatchSelects[85] && swatchSelects[86] && swatchSelects[87] && swatchSelects[88] && swatchSelects[89] && swatchSelects[90]
                    && !registers.contains("858687888990")) {
                registers.add("858687888990");
                swatches[85].setImageDrawable(gradientDrawable);
                swatches[86].setImageDrawable(gradientDrawable);
                swatches[87].setImageDrawable(gradientDrawable);
                swatches[88].setImageDrawable(gradientDrawable);
                swatches[89].setImageDrawable(gradientDrawable);
                swatches[90].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 6;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 6;
                    }
                }
            }
            if (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] && swatchSelects[40]
                    && !registers.contains("0613213040")) {
                registers.add("0613213040");
                swatches[0].setImageDrawable(gradientDrawable);
                swatches[6].setImageDrawable(gradientDrawable);
                swatches[13].setImageDrawable(gradientDrawable);
                swatches[21].setImageDrawable(gradientDrawable);
                swatches[30].setImageDrawable(gradientDrawable);
                swatches[40].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 6;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 6;
                    }
                }
            }
            if (swatchSelects[50] && swatchSelects[60] && swatchSelects[69] && swatchSelects[77] && swatchSelects[84] && swatchSelects[90]
                    && !registers.contains("506069778490")) {
                registers.add("506069778490");
                swatches[50].setImageDrawable(gradientDrawable);
                swatches[60].setImageDrawable(gradientDrawable);
                swatches[69].setImageDrawable(gradientDrawable);
                swatches[77].setImageDrawable(gradientDrawable);
                swatches[84].setImageDrawable(gradientDrawable);
                swatches[90].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 6;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 6;
                    }
                }
            }
            if (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[39] && swatchSelects[50]
                    && !registers.contains("51220293950")) {
                registers.add("51220293950");
                swatches[5].setImageDrawable(gradientDrawable);
                swatches[12].setImageDrawable(gradientDrawable);
                swatches[20].setImageDrawable(gradientDrawable);
                swatches[29].setImageDrawable(gradientDrawable);
                swatches[39].setImageDrawable(gradientDrawable);
                swatches[50].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 6;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 6;
                    }
                }
            }
            if (swatchSelects[40] && swatchSelects[51] && swatchSelects[61] && swatchSelects[70] && swatchSelects[78] && swatchSelects[85]
                    && !registers.contains("405161707885")) {
                registers.add("405161707885");
                swatches[40].setImageDrawable(gradientDrawable);
                swatches[51].setImageDrawable(gradientDrawable);
                swatches[61].setImageDrawable(gradientDrawable);
                swatches[70].setImageDrawable(gradientDrawable);
                swatches[78].setImageDrawable(gradientDrawable);
                swatches[85].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 6;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 6;
                    }
                }
            }

            // 7
            if (swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && swatchSelects[9] && swatchSelects[10] && swatchSelects[11]
                    && swatchSelects[12] && !registers.contains("6789101112")) {
                registers.add("6789101112");
                swatches[6].setImageDrawable(gradientDrawable);
                swatches[7].setImageDrawable(gradientDrawable);
                swatches[8].setImageDrawable(gradientDrawable);
                swatches[9].setImageDrawable(gradientDrawable);
                swatches[10].setImageDrawable(gradientDrawable);
                swatches[11].setImageDrawable(gradientDrawable);
                swatches[12].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 7;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 7;
                    }
                }
            }
            if (swatchSelects[78] && swatchSelects[79] && swatchSelects[80] && swatchSelects[81] && swatchSelects[82] && swatchSelects[83]
                    && swatchSelects[84] && !registers.contains("78798081828384")) {
                registers.add("78798081828384");
                swatches[78].setImageDrawable(gradientDrawable);
                swatches[79].setImageDrawable(gradientDrawable);
                swatches[80].setImageDrawable(gradientDrawable);
                swatches[81].setImageDrawable(gradientDrawable);
                swatches[82].setImageDrawable(gradientDrawable);
                swatches[83].setImageDrawable(gradientDrawable);
                swatches[84].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 7;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 7;
                    }
                }
            }
            if (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31] && swatchSelects[41]
                    && swatchSelects[51] && !registers.contains("1714223141")) {
                registers.add("1714223141");
                swatches[1].setImageDrawable(gradientDrawable);
                swatches[7].setImageDrawable(gradientDrawable);
                swatches[14].setImageDrawable(gradientDrawable);
                swatches[22].setImageDrawable(gradientDrawable);
                swatches[31].setImageDrawable(gradientDrawable);
                swatches[41].setImageDrawable(gradientDrawable);
                swatches[51].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 7;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 7;
                    }
                }
            }
            if (swatchSelects[39] && swatchSelects[49] && swatchSelects[59] && swatchSelects[68] && swatchSelects[76] && swatchSelects[83]
                    && swatchSelects[89] && !registers.contains("39495968768389")) {
                registers.add("39495968768389");
                swatches[39].setImageDrawable(gradientDrawable);
                swatches[49].setImageDrawable(gradientDrawable);
                swatches[59].setImageDrawable(gradientDrawable);
                swatches[68].setImageDrawable(gradientDrawable);
                swatches[76].setImageDrawable(gradientDrawable);
                swatches[83].setImageDrawable(gradientDrawable);
                swatches[89].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 7;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 7;
                    }
                }
            }
            if (swatchSelects[4] && swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[38] && swatchSelects[49]
                    && swatchSelects[60] && !registers.contains("4111928384960")) {
                registers.add("4111928384960");
                swatches[4].setImageDrawable(gradientDrawable);
                swatches[11].setImageDrawable(gradientDrawable);
                swatches[19].setImageDrawable(gradientDrawable);
                swatches[28].setImageDrawable(gradientDrawable);
                swatches[38].setImageDrawable(gradientDrawable);
                swatches[49].setImageDrawable(gradientDrawable);
                swatches[60].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 7;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 7;
                    }
                }
            }
            if (swatchSelects[30] && swatchSelects[41] && swatchSelects[52] && swatchSelects[62] && swatchSelects[71] && swatchSelects[79]
                    && swatchSelects[86] && !registers.contains("30415262717986")) {
                registers.add("30415262717986");
                swatches[30].setImageDrawable(gradientDrawable);
                swatches[41].setImageDrawable(gradientDrawable);
                swatches[52].setImageDrawable(gradientDrawable);
                swatches[62].setImageDrawable(gradientDrawable);
                swatches[71].setImageDrawable(gradientDrawable);
                swatches[79].setImageDrawable(gradientDrawable);
                swatches[86].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 7;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 7;
                    }
                }
            }

            // 8
            if (swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18]
                    && swatchSelects[19] && swatchSelects[20] && !registers.contains("1314151617181920")) {
                registers.add("1314151617181920");
                swatches[13].setImageDrawable(gradientDrawable);
                swatches[14].setImageDrawable(gradientDrawable);
                swatches[15].setImageDrawable(gradientDrawable);
                swatches[16].setImageDrawable(gradientDrawable);
                swatches[17].setImageDrawable(gradientDrawable);
                swatches[18].setImageDrawable(gradientDrawable);
                swatches[19].setImageDrawable(gradientDrawable);
                swatches[20].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 8;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 8;
                    }
                }
            }
            if (swatchSelects[70] && swatchSelects[71] && swatchSelects[72] && swatchSelects[73] && swatchSelects[74] && swatchSelects[75]
                    && swatchSelects[76] && swatchSelects[77] && !registers.contains("7071727374757677")) {
                registers.add("7071727374757677");
                swatches[70].setImageDrawable(gradientDrawable);
                swatches[71].setImageDrawable(gradientDrawable);
                swatches[72].setImageDrawable(gradientDrawable);
                swatches[73].setImageDrawable(gradientDrawable);
                swatches[74].setImageDrawable(gradientDrawable);
                swatches[75].setImageDrawable(gradientDrawable);
                swatches[76].setImageDrawable(gradientDrawable);
                swatches[77].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 8;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 8;
                    }
                }
            }
            if (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32] && swatchSelects[42]
                    && swatchSelects[52] && swatchSelects[61] && !registers.contains("28152332425261")) {
                registers.add("28152332425261");
                swatches[2].setImageDrawable(gradientDrawable);
                swatches[8].setImageDrawable(gradientDrawable);
                swatches[15].setImageDrawable(gradientDrawable);
                swatches[23].setImageDrawable(gradientDrawable);
                swatches[32].setImageDrawable(gradientDrawable);
                swatches[42].setImageDrawable(gradientDrawable);
                swatches[52].setImageDrawable(gradientDrawable);
                swatches[61].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 8;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 8;
                    }
                }
            }
            if (swatchSelects[29] && swatchSelects[38] && swatchSelects[48] && swatchSelects[58] && swatchSelects[67] && swatchSelects[75]
                    && swatchSelects[82] && swatchSelects[88] && !registers.contains("2938485867758288")) {
                registers.add("2938485867758288");
                swatches[29].setImageDrawable(gradientDrawable);
                swatches[38].setImageDrawable(gradientDrawable);
                swatches[48].setImageDrawable(gradientDrawable);
                swatches[58].setImageDrawable(gradientDrawable);
                swatches[67].setImageDrawable(gradientDrawable);
                swatches[75].setImageDrawable(gradientDrawable);
                swatches[82].setImageDrawable(gradientDrawable);
                swatches[88].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 8;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 8;
                    }
                }
            }
            if (swatchSelects[3] && swatchSelects[10] && swatchSelects[18] && swatchSelects[27] && swatchSelects[37] && swatchSelects[48]
                    && swatchSelects[59] && swatchSelects[69] && !registers.contains("310182737485969")) {
                registers.add("310182737485969");
                swatches[3].setImageDrawable(gradientDrawable);
                swatches[10].setImageDrawable(gradientDrawable);
                swatches[18].setImageDrawable(gradientDrawable);
                swatches[27].setImageDrawable(gradientDrawable);
                swatches[37].setImageDrawable(gradientDrawable);
                swatches[48].setImageDrawable(gradientDrawable);
                swatches[59].setImageDrawable(gradientDrawable);
                swatches[69].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 8;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 8;
                    }
                }
            }
            if (swatchSelects[21] && swatchSelects[31] && swatchSelects[42] && swatchSelects[53] && swatchSelects[63] && swatchSelects[72]
                    && swatchSelects[80] && swatchSelects[87] && !registers.contains("2131425363728087")) {
                registers.add("2131425363728087");
                swatches[21].setImageDrawable(gradientDrawable);
                swatches[31].setImageDrawable(gradientDrawable);
                swatches[42].setImageDrawable(gradientDrawable);
                swatches[53].setImageDrawable(gradientDrawable);
                swatches[63].setImageDrawable(gradientDrawable);
                swatches[72].setImageDrawable(gradientDrawable);
                swatches[80].setImageDrawable(gradientDrawable);
                swatches[87].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 8;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 8;
                    }
                }
            }

            // 9
            if (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26]
                    && swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && !registers.contains("212223242526272829")) {
                registers.add("212223242526272829");
                swatches[21].setImageDrawable(gradientDrawable);
                swatches[22].setImageDrawable(gradientDrawable);
                swatches[23].setImageDrawable(gradientDrawable);
                swatches[24].setImageDrawable(gradientDrawable);
                swatches[25].setImageDrawable(gradientDrawable);
                swatches[26].setImageDrawable(gradientDrawable);
                swatches[27].setImageDrawable(gradientDrawable);
                swatches[28].setImageDrawable(gradientDrawable);
                swatches[29].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 9;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 9;
                    }
                }
            }
            if (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65] && swatchSelects[66]
                    && swatchSelects[67] && swatchSelects[68] && swatchSelects[69] && !registers.contains("616263646566676869")) {
                registers.add("616263646566676869");
                swatches[61].setImageDrawable(gradientDrawable);
                swatches[62].setImageDrawable(gradientDrawable);
                swatches[63].setImageDrawable(gradientDrawable);
                swatches[64].setImageDrawable(gradientDrawable);
                swatches[65].setImageDrawable(gradientDrawable);
                swatches[66].setImageDrawable(gradientDrawable);
                swatches[67].setImageDrawable(gradientDrawable);
                swatches[68].setImageDrawable(gradientDrawable);
                swatches[69].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 9;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 9;
                    }
                }
            }
            if (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33] && swatchSelects[43]
                    && swatchSelects[53] && swatchSelects[62] && swatchSelects[70] && !registers.contains("3916243343536270")) {
                registers.add("3916243343536270");
                swatches[3].setImageDrawable(gradientDrawable);
                swatches[9].setImageDrawable(gradientDrawable);
                swatches[16].setImageDrawable(gradientDrawable);
                swatches[24].setImageDrawable(gradientDrawable);
                swatches[33].setImageDrawable(gradientDrawable);
                swatches[43].setImageDrawable(gradientDrawable);
                swatches[53].setImageDrawable(gradientDrawable);
                swatches[62].setImageDrawable(gradientDrawable);
                swatches[70].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 9;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 9;
                    }
                }
            }
            if (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57] && swatchSelects[66]
                    && swatchSelects[74] && swatchSelects[81] && swatchSelects[87] && !registers.contains("202837475766748187")) {
                registers.add("202837475766748187");
                swatches[20].setImageDrawable(gradientDrawable);
                swatches[28].setImageDrawable(gradientDrawable);
                swatches[37].setImageDrawable(gradientDrawable);
                swatches[47].setImageDrawable(gradientDrawable);
                swatches[57].setImageDrawable(gradientDrawable);
                swatches[66].setImageDrawable(gradientDrawable);
                swatches[74].setImageDrawable(gradientDrawable);
                swatches[81].setImageDrawable(gradientDrawable);
                swatches[87].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 9;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 9;
                    }
                }
            }
            if (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36] && swatchSelects[47]
                    && swatchSelects[58] && swatchSelects[68] && swatchSelects[77] && !registers.contains("2917263647586877")) {
                registers.add("2917263647586877");
                swatches[2].setImageDrawable(gradientDrawable);
                swatches[9].setImageDrawable(gradientDrawable);
                swatches[17].setImageDrawable(gradientDrawable);
                swatches[26].setImageDrawable(gradientDrawable);
                swatches[36].setImageDrawable(gradientDrawable);
                swatches[47].setImageDrawable(gradientDrawable);
                swatches[58].setImageDrawable(gradientDrawable);
                swatches[68].setImageDrawable(gradientDrawable);
                swatches[77].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 9;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 9;
                    }
                }
            }
            if (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54] && swatchSelects[64]
                    && swatchSelects[73] && swatchSelects[81] && swatchSelects[88] && !registers.contains("1322324335464738188")) {
                registers.add("1322324335464738188");
                swatches[13].setImageDrawable(gradientDrawable);
                swatches[22].setImageDrawable(gradientDrawable);
                swatches[32].setImageDrawable(gradientDrawable);
                swatches[43].setImageDrawable(gradientDrawable);
                swatches[54].setImageDrawable(gradientDrawable);
                swatches[64].setImageDrawable(gradientDrawable);
                swatches[73].setImageDrawable(gradientDrawable);
                swatches[81].setImageDrawable(gradientDrawable);
                swatches[88].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 9;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 9;
                    }
                }
            }

            // 10
            if (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35]
                    && swatchSelects[36] && swatchSelects[37] && swatchSelects[38] && swatchSelects[39] && !registers.contains("30313233343536373839")) {
                registers.add("30313233343536373839");
                swatches[30].setImageDrawable(gradientDrawable);
                swatches[31].setImageDrawable(gradientDrawable);
                swatches[32].setImageDrawable(gradientDrawable);
                swatches[33].setImageDrawable(gradientDrawable);
                swatches[34].setImageDrawable(gradientDrawable);
                swatches[35].setImageDrawable(gradientDrawable);
                swatches[36].setImageDrawable(gradientDrawable);
                swatches[37].setImageDrawable(gradientDrawable);
                swatches[38].setImageDrawable(gradientDrawable);
                swatches[39].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 10;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 10;
                    }
                }
            }
            if (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56]
                    && swatchSelects[57] && swatchSelects[58] && swatchSelects[59] && swatchSelects[60] && !registers.contains("51525354555657585960")) {
                registers.add("51525354555657585960");
                swatches[51].setImageDrawable(gradientDrawable);
                swatches[52].setImageDrawable(gradientDrawable);
                swatches[53].setImageDrawable(gradientDrawable);
                swatches[54].setImageDrawable(gradientDrawable);
                swatches[55].setImageDrawable(gradientDrawable);
                swatches[56].setImageDrawable(gradientDrawable);
                swatches[57].setImageDrawable(gradientDrawable);
                swatches[58].setImageDrawable(gradientDrawable);
                swatches[59].setImageDrawable(gradientDrawable);
                swatches[60].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 10;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 10;
                    }
                }
            }
            if (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44]
                    && swatchSelects[54] && swatchSelects[63] && swatchSelects[71] && swatchSelects[78] && !registers.contains("4101725344454637178")) {
                registers.add("4101725344454637178");
                swatches[4].setImageDrawable(gradientDrawable);
                swatches[10].setImageDrawable(gradientDrawable);
                swatches[17].setImageDrawable(gradientDrawable);
                swatches[25].setImageDrawable(gradientDrawable);
                swatches[34].setImageDrawable(gradientDrawable);
                swatches[44].setImageDrawable(gradientDrawable);
                swatches[54].setImageDrawable(gradientDrawable);
                swatches[63].setImageDrawable(gradientDrawable);
                swatches[71].setImageDrawable(gradientDrawable);
                swatches[78].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 10;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 10;
                    }
                }
            }
            if (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56]
                    && swatchSelects[65] && swatchSelects[73] && swatchSelects[80] && swatchSelects[86] && !registers.contains("12192736465665738086")) {
                registers.add("12192736465665738086");
                swatches[12].setImageDrawable(gradientDrawable);
                swatches[19].setImageDrawable(gradientDrawable);
                swatches[27].setImageDrawable(gradientDrawable);
                swatches[36].setImageDrawable(gradientDrawable);
                swatches[46].setImageDrawable(gradientDrawable);
                swatches[56].setImageDrawable(gradientDrawable);
                swatches[65].setImageDrawable(gradientDrawable);
                swatches[73].setImageDrawable(gradientDrawable);
                swatches[80].setImageDrawable(gradientDrawable);
                swatches[86].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 10;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 10;
                    }
                }
            }
            if (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46]
                    && swatchSelects[57] && swatchSelects[67] && swatchSelects[76] && swatchSelects[84] && !registers.contains("181625354657677684")) {
                registers.add("181625354657677684");
                swatches[1].setImageDrawable(gradientDrawable);
                swatches[8].setImageDrawable(gradientDrawable);
                swatches[16].setImageDrawable(gradientDrawable);
                swatches[25].setImageDrawable(gradientDrawable);
                swatches[35].setImageDrawable(gradientDrawable);
                swatches[46].setImageDrawable(gradientDrawable);
                swatches[57].setImageDrawable(gradientDrawable);
                swatches[67].setImageDrawable(gradientDrawable);
                swatches[76].setImageDrawable(gradientDrawable);
                swatches[84].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 10;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 10;
                    }
                }
            }
            if (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55]
                    && swatchSelects[65] && swatchSelects[74] && swatchSelects[82] && swatchSelects[89] && !registers.contains("6142333445565748289")) {
                registers.add("6142333445565748289");
                swatches[6].setImageDrawable(gradientDrawable);
                swatches[14].setImageDrawable(gradientDrawable);
                swatches[23].setImageDrawable(gradientDrawable);
                swatches[33].setImageDrawable(gradientDrawable);
                swatches[44].setImageDrawable(gradientDrawable);
                swatches[55].setImageDrawable(gradientDrawable);
                swatches[65].setImageDrawable(gradientDrawable);
                swatches[74].setImageDrawable(gradientDrawable);
                swatches[82].setImageDrawable(gradientDrawable);
                swatches[89].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 10;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 10;
                    }
                }
            }


            // 11
            if (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                    && swatchSelects[46] && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] &&
                    !registers.contains("4041424344454647484950")) {
                registers.add("4041424344454647484950");
                swatches[40].setImageDrawable(gradientDrawable);
                swatches[41].setImageDrawable(gradientDrawable);
                swatches[42].setImageDrawable(gradientDrawable);
                swatches[43].setImageDrawable(gradientDrawable);
                swatches[44].setImageDrawable(gradientDrawable);
                swatches[45].setImageDrawable(gradientDrawable);
                swatches[46].setImageDrawable(gradientDrawable);
                swatches[47].setImageDrawable(gradientDrawable);
                swatches[48].setImageDrawable(gradientDrawable);
                swatches[49].setImageDrawable(gradientDrawable);
                swatches[50].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 11;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 11;
                    }
                }
            }
            if (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                    && swatchSelects[55] && swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] &&
                    !registers.contains("511182635455564727985")) {
                registers.add("511182635455564727985");
                swatches[5].setImageDrawable(gradientDrawable);
                swatches[11].setImageDrawable(gradientDrawable);
                swatches[18].setImageDrawable(gradientDrawable);
                swatches[26].setImageDrawable(gradientDrawable);
                swatches[35].setImageDrawable(gradientDrawable);
                swatches[45].setImageDrawable(gradientDrawable);
                swatches[55].setImageDrawable(gradientDrawable);
                swatches[64].setImageDrawable(gradientDrawable);
                swatches[72].setImageDrawable(gradientDrawable);
                swatches[79].setImageDrawable(gradientDrawable);
                swatches[85].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 11;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 11;
                    }
                }
            }
            if (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                    && swatchSelects[56] && swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] &&
                    !registers.contains("07152434455666758390")) {
                registers.add("07152434455666758390");
                swatches[0].setImageDrawable(gradientDrawable);
                swatches[7].setImageDrawable(gradientDrawable);
                swatches[15].setImageDrawable(gradientDrawable);
                swatches[24].setImageDrawable(gradientDrawable);
                swatches[34].setImageDrawable(gradientDrawable);
                swatches[45].setImageDrawable(gradientDrawable);
                swatches[56].setImageDrawable(gradientDrawable);
                swatches[66].setImageDrawable(gradientDrawable);
                swatches[75].setImageDrawable(gradientDrawable);
                swatches[83].setImageDrawable(gradientDrawable);
                swatches[90].setImageDrawable(gradientDrawable);
                if (isfull) {
                    if (swatches[x].id == 1) {
                        player1Score = player1Score + 11;
                    } else if (swatches[x].id == 2) {
                        player2Score = player2Score + 11;
                    }
                }
            }
        }
    }

    private void algo() {
        int i = 0;
        if (difficulty == EASY) {
            if (attackStep_1() != -1) {
                i = attackStep_1();
            } else if (attackStep_2() != -1) {
                i = attackStep_2();
            } else if (randomStep() != -1) {
                i = randomStep();
            }
        } else if (difficulty == HARD) {
            if (attackStep_1() != -1) {
                i = attackStep_1();
            } else if (attackStep_2() != -1) {
                i = attackStep_2();
            } else if (preventStep1() != -1) {
                i = preventStep1();
            } else if (preventStep2_1() != -1) {
                i = preventStep2_1();
            } else if (preventStep2_2() != -1) {
                i = preventStep2_2();
            } else if (preventStep2_3() != -1) {
                i = preventStep2_3();
            } else if (preventStep2_4() != -1) {
                i = preventStep2_4();
            } else if (preventStep2_5() != -1) {
                i = preventStep2_5();
            } else if (preventStep2_6() != -1) {
                i = preventStep2_6();
            }
        }
        swatches[i] = (Swatch) getChildAt(i);
        if (swatches[i].id == 0 && !swatchSelects[i]) {
            swatches[i].id += 2;
            turn++;
            swatches[i].setSelected(true);
            swatchSelects[i] = swatches[i].isSelected();
            swatches[i].drawable.setColor(BaseActivity.COLOR3);
            check(i, true);
            onPlayerChangedListener.onPlayerChanged();
            onResultChangedListener.onResultChanged(player1Score, player2Score, turn);
            if (soundEnabled) mediaPlayer2.start();
            updateCheckerPosition(swatches[i]);
        }
    }

    private int randomStep() {
        int i = -1;
        if (boardRadius == 3) {
            int a[] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
                    19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 32, 33, 34, 35, 36};
            i = pickUnSelectedRandom(a);
        } else if (boardRadius == 4) {
            int a[] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
                    22, 23, 24, 25, 26, 27, 28, 29, 30, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42,
                    43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60};
            i = pickUnSelectedRandom(a);
        } else if (boardRadius == 5) {
            int a[] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26,
                    27, 28, 29, 30, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52,
                    53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78,
                    79, 80, 81, 82, 83, 84, 85, 86, 87};
            i = pickUnSelectedRandom(a);
        }
        return i;
    }

    private int attackStep_1() {
        int i = -1;
        if (boardRadius == 2) {
            if ((swatchSelects[0] && swatchSelects[4] && swatchSelects[9] && swatchSelects[14] && !swatchSelects[18]) ||
                    (swatchSelects[0] && swatchSelects[4] && swatchSelects[9] && swatchSelects[18] && !swatchSelects[14]) ||
                    (swatchSelects[0] && swatchSelects[4] && swatchSelects[14] && swatchSelects[18] && !swatchSelects[9]) ||
                    (swatchSelects[0] && swatchSelects[9] && swatchSelects[14] && swatchSelects[18] && !swatchSelects[4]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[14] && swatchSelects[18] && !swatchSelects[0])) {
                int a[] = {0, 4, 9, 14, 18};
                i = pickUnSelected(a);
            } else if ((swatchSelects[2] && swatchSelects[5] && swatchSelects[9] && swatchSelects[13] && !swatchSelects[16]) ||
                    (swatchSelects[2] && swatchSelects[5] && swatchSelects[9] && swatchSelects[16] && !swatchSelects[13]) ||
                    (swatchSelects[2] && swatchSelects[5] && swatchSelects[13] && swatchSelects[16] && !swatchSelects[9]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[13] && swatchSelects[16] && !swatchSelects[5]) ||
                    (swatchSelects[5] && swatchSelects[9] && swatchSelects[13] && swatchSelects[16] && !swatchSelects[2])) {
                int a[] = {2, 5, 9, 13, 16};
                i = pickUnSelected(a);
            } else if ((swatchSelects[7] && swatchSelects[8] && swatchSelects[9] && swatchSelects[10] && !swatchSelects[11]) ||
                    (swatchSelects[7] && swatchSelects[8] && swatchSelects[9] && swatchSelects[11] && !swatchSelects[10]) ||
                    (swatchSelects[7] && swatchSelects[8] && swatchSelects[10] && swatchSelects[11] && !swatchSelects[9]) ||
                    (swatchSelects[7] && swatchSelects[9] && swatchSelects[10] && swatchSelects[11] && !swatchSelects[8]) ||
                    (swatchSelects[8] && swatchSelects[9] && swatchSelects[10] && swatchSelects[11] && !swatchSelects[7])) {
                int a[] = {7, 8, 9, 10, 11};
                i = pickUnSelected(a);
            } else if ((swatchSelects[1] && swatchSelects[4] && swatchSelects[8] && !swatchSelects[12]) ||
                    (swatchSelects[1] && swatchSelects[4] && swatchSelects[12] && !swatchSelects[8]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[12] && !swatchSelects[4]) ||
                    (swatchSelects[4] && swatchSelects[8] && swatchSelects[12] && !swatchSelects[1])) {
                int a[] = {1, 4, 8, 12};
                i = pickUnSelected(a);
            } else if ((swatchSelects[6] && swatchSelects[10] && swatchSelects[14] && !swatchSelects[17]) ||
                    (swatchSelects[6] && swatchSelects[10] && swatchSelects[17] && !swatchSelects[14]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[17] && !swatchSelects[10]) ||
                    (swatchSelects[10] && swatchSelects[14] && swatchSelects[17] && !swatchSelects[6])) {
                int a[] = {6, 10, 14, 17};
                i = pickUnSelected(a);
            } else if ((swatchSelects[1] && swatchSelects[5] && swatchSelects[10] && !swatchSelects[15]) ||
                    (swatchSelects[1] && swatchSelects[5] && swatchSelects[15] && !swatchSelects[10]) ||
                    (swatchSelects[1] && swatchSelects[10] && swatchSelects[15] && !swatchSelects[5]) ||
                    (swatchSelects[5] && swatchSelects[10] && swatchSelects[15] && !swatchSelects[1])) {
                int a[] = {1, 5, 10, 15};
                i = pickUnSelected(a);
            } else if ((swatchSelects[3] && swatchSelects[8] && swatchSelects[13] && !swatchSelects[17]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[17] && !swatchSelects[13]) ||
                    (swatchSelects[3] && swatchSelects[13] && swatchSelects[17] && !swatchSelects[8]) ||
                    (swatchSelects[8] && swatchSelects[13] && swatchSelects[17] && !swatchSelects[3])) {
                int a[] = {3, 8, 13, 17};
                i = pickUnSelected(a);
            } else if ((swatchSelects[3] && swatchSelects[4] && swatchSelects[5] && !swatchSelects[6]) ||
                    (swatchSelects[3] && swatchSelects[4] && swatchSelects[6] && !swatchSelects[5]) ||
                    (swatchSelects[3] && swatchSelects[5] && swatchSelects[6] && !swatchSelects[4]) ||
                    (swatchSelects[4] && swatchSelects[5] && swatchSelects[6] && !swatchSelects[3])) {
                int a[] = {3, 4, 5, 6};
                i = pickUnSelected(a);
            } else if ((swatchSelects[12] && swatchSelects[13] && swatchSelects[14] && !swatchSelects[15]) ||
                    (swatchSelects[12] && swatchSelects[13] && swatchSelects[15] && !swatchSelects[14]) ||
                    (swatchSelects[12] && swatchSelects[14] && swatchSelects[15] && !swatchSelects[13]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && !swatchSelects[12])) {
                int a[] = {12, 13, 14, 15};
                i = pickUnSelected(a);
            } else if ((swatchSelects[0] && swatchSelects[1] && !swatchSelects[2])
                    || (swatchSelects[0] && swatchSelects[2] && !swatchSelects[1])
                    || (swatchSelects[1] && swatchSelects[2] && !swatchSelects[0])) {
                int a[] = {0, 1, 2};
                i = pickUnSelected(a);
            } else if ((swatchSelects[16] && swatchSelects[17] && !swatchSelects[18])
                    || (swatchSelects[16] && swatchSelects[18] && !swatchSelects[17])
                    || (swatchSelects[17] && swatchSelects[18] && !swatchSelects[16])) {
                int a[] = {16, 17, 18};
                i = pickUnSelected(a);
            } else if ((swatchSelects[0] && swatchSelects[3] && !swatchSelects[7])
                    || (swatchSelects[0] && swatchSelects[7] && !swatchSelects[3])
                    || (swatchSelects[3] && swatchSelects[7] && !swatchSelects[0])) {
                int a[] = {0, 3, 7};
                i = pickUnSelected(a);
            } else if ((swatchSelects[11] && swatchSelects[15] && !swatchSelects[18])
                    || (swatchSelects[11] && swatchSelects[18] && !swatchSelects[15])
                    || (swatchSelects[15] && swatchSelects[18] && !swatchSelects[11])) {
                int a[] = {11, 15, 18};
                i = pickUnSelected(a);
            } else if ((swatchSelects[2] && swatchSelects[6] && !swatchSelects[11])
                    || (swatchSelects[2] && swatchSelects[11] && !swatchSelects[6])
                    || (swatchSelects[6] && swatchSelects[11] && !swatchSelects[2])) {
                int a[] = {2, 6, 11};
                i = pickUnSelected(a);
            } else if ((swatchSelects[7] && swatchSelects[12] && !swatchSelects[16])
                    || (swatchSelects[7] && swatchSelects[16] && !swatchSelects[12])
                    || (swatchSelects[12] && swatchSelects[16] && !swatchSelects[7])) {
                int a[] = {7, 12, 16};
                i = pickUnSelected(a);
            } else {
                int a[] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18};
                i = pickUnSelectedRandom(a);
            }

        } else if (boardRadius == 3) {
            if ((swatchSelects[0] && swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[25] && swatchSelects[31] && !swatchSelects[36]) ||
                    (swatchSelects[0] && swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[25] && swatchSelects[36] && !swatchSelects[31]) ||
                    (swatchSelects[0] && swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[31] && swatchSelects[36] && !swatchSelects[25]) ||
                    (swatchSelects[0] && swatchSelects[5] && swatchSelects[11] && swatchSelects[25] && swatchSelects[31] && swatchSelects[36] && !swatchSelects[18]) ||
                    (swatchSelects[0] && swatchSelects[5] && swatchSelects[18] && swatchSelects[25] && swatchSelects[31] && swatchSelects[36] && !swatchSelects[11]) ||
                    (swatchSelects[0] && swatchSelects[11] && swatchSelects[18] && swatchSelects[25] && swatchSelects[31] && swatchSelects[36] && !swatchSelects[5]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[25] && swatchSelects[31] && swatchSelects[36] && !swatchSelects[0])) {
                int a[] = {0, 5, 11, 18, 25, 31, 36};
                i = pickUnSelected(a);
            } else if ((swatchSelects[3] && swatchSelects[7] && swatchSelects[12] && swatchSelects[18] && swatchSelects[24] && swatchSelects[29] && !swatchSelects[33]) ||
                    (swatchSelects[3] && swatchSelects[7] && swatchSelects[12] && swatchSelects[18] && swatchSelects[24] && swatchSelects[33] && !swatchSelects[29]) ||
                    (swatchSelects[3] && swatchSelects[7] && swatchSelects[12] && swatchSelects[18] && swatchSelects[29] && swatchSelects[33] && !swatchSelects[24]) ||
                    (swatchSelects[3] && swatchSelects[7] && swatchSelects[12] && swatchSelects[24] && swatchSelects[29] && swatchSelects[33] && !swatchSelects[18]) ||
                    (swatchSelects[3] && swatchSelects[7] && swatchSelects[18] && swatchSelects[24] && swatchSelects[29] && swatchSelects[33] && !swatchSelects[12]) ||
                    (swatchSelects[3] && swatchSelects[12] && swatchSelects[18] && swatchSelects[24] && swatchSelects[29] && swatchSelects[33] && !swatchSelects[7]) ||
                    (swatchSelects[7] && swatchSelects[12] && swatchSelects[18] && swatchSelects[24] && swatchSelects[29] && swatchSelects[33] && !swatchSelects[3])) {
                int a[] = {3, 7, 12, 18, 24, 29, 33};
                i = pickUnSelected(a);
            } else if ((swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && !swatchSelects[21]) ||
                    (swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18] && swatchSelects[19] && swatchSelects[21] && !swatchSelects[20]) ||
                    (swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18] && swatchSelects[20] && swatchSelects[21] && !swatchSelects[19]) ||
                    (swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && !swatchSelects[18]) ||
                    (swatchSelects[15] && swatchSelects[16] && swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && !swatchSelects[17]) ||
                    (swatchSelects[15] && swatchSelects[17] && swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && !swatchSelects[16]) ||
                    (swatchSelects[16] && swatchSelects[17] && swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && !swatchSelects[15])) {
                int a[] = {15, 16, 17, 18, 19, 20, 21};
                i = pickUnSelected(a);
            } else if ((swatchSelects[1] && swatchSelects[6] && swatchSelects[12] && swatchSelects[19] && swatchSelects[26] && !swatchSelects[32]) ||
                    (swatchSelects[1] && swatchSelects[6] && swatchSelects[12] && swatchSelects[19] && swatchSelects[32] && !swatchSelects[26]) ||
                    (swatchSelects[1] && swatchSelects[6] && swatchSelects[12] && swatchSelects[26] && swatchSelects[32] && !swatchSelects[19]) ||
                    (swatchSelects[1] && swatchSelects[6] && swatchSelects[19] && swatchSelects[26] && swatchSelects[32] && !swatchSelects[12]) ||
                    (swatchSelects[1] && swatchSelects[12] && swatchSelects[19] && swatchSelects[26] && swatchSelects[32] && !swatchSelects[6]) ||
                    (swatchSelects[6] && swatchSelects[12] && swatchSelects[19] && swatchSelects[26] && swatchSelects[32] && !swatchSelects[1])) {
                int a[] = {1, 6, 12, 19, 26, 32};
                i = pickUnSelected(a);
            } else if ((swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[24] && swatchSelects[30] && !swatchSelects[35]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[24] && swatchSelects[35] && !swatchSelects[30]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[30] && swatchSelects[35] && !swatchSelects[24]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[24] && swatchSelects[30] && swatchSelects[35] && !swatchSelects[17]) ||
                    (swatchSelects[4] && swatchSelects[17] && swatchSelects[24] && swatchSelects[30] && swatchSelects[35] && !swatchSelects[10]) ||
                    (swatchSelects[10] && swatchSelects[17] && swatchSelects[24] && swatchSelects[30] && swatchSelects[35] && !swatchSelects[4])) {
                int a[] = {4, 10, 17, 24, 30, 35};
                i = pickUnSelected(a);
            } else if ((swatchSelects[2] && swatchSelects[6] && swatchSelects[11] && swatchSelects[17] && swatchSelects[23] && !swatchSelects[28]) ||
                    (swatchSelects[2] && swatchSelects[6] && swatchSelects[11] && swatchSelects[17] && swatchSelects[28] && !swatchSelects[23]) ||
                    (swatchSelects[2] && swatchSelects[6] && swatchSelects[11] && swatchSelects[23] && swatchSelects[28] && !swatchSelects[17]) ||
                    (swatchSelects[2] && swatchSelects[6] && swatchSelects[17] && swatchSelects[23] && swatchSelects[28] && !swatchSelects[11]) ||
                    (swatchSelects[2] && swatchSelects[11] && swatchSelects[17] && swatchSelects[23] && swatchSelects[28] && !swatchSelects[6]) ||
                    (swatchSelects[6] && swatchSelects[11] && swatchSelects[17] && swatchSelects[23] && swatchSelects[28] && !swatchSelects[2])) {
                int a[] = {2, 6, 11, 17, 23, 28};
                i = pickUnSelected(a);
            } else if ((swatchSelects[8] && swatchSelects[13] && swatchSelects[19] && swatchSelects[25] && swatchSelects[30] && !swatchSelects[34]) ||
                    (swatchSelects[8] && swatchSelects[13] && swatchSelects[19] && swatchSelects[25] && swatchSelects[34] && !swatchSelects[30]) ||
                    (swatchSelects[8] && swatchSelects[13] && swatchSelects[19] && swatchSelects[30] && swatchSelects[34] && !swatchSelects[25]) ||
                    (swatchSelects[8] && swatchSelects[13] && swatchSelects[25] && swatchSelects[30] && swatchSelects[34] && !swatchSelects[19]) ||
                    (swatchSelects[8] && swatchSelects[19] && swatchSelects[25] && swatchSelects[30] && swatchSelects[34] && !swatchSelects[13]) ||
                    (swatchSelects[13] && swatchSelects[19] && swatchSelects[25] && swatchSelects[30] && swatchSelects[34] && !swatchSelects[8])) {
                int a[] = {8, 13, 19, 25, 30, 34};
                i = pickUnSelected(a);
            } else if ((swatchSelects[9] && swatchSelects[10] && swatchSelects[11] && swatchSelects[12] && swatchSelects[13] && !swatchSelects[14]) ||
                    (swatchSelects[9] && swatchSelects[10] && swatchSelects[11] && swatchSelects[12] && swatchSelects[14] && !swatchSelects[13]) ||
                    (swatchSelects[9] && swatchSelects[10] && swatchSelects[11] && swatchSelects[13] && swatchSelects[14] && !swatchSelects[12]) ||
                    (swatchSelects[9] && swatchSelects[10] && swatchSelects[12] && swatchSelects[13] && swatchSelects[14] && !swatchSelects[11]) ||
                    (swatchSelects[9] && swatchSelects[11] && swatchSelects[12] && swatchSelects[13] && swatchSelects[14] && !swatchSelects[10]) ||
                    (swatchSelects[10] && swatchSelects[11] && swatchSelects[12] && swatchSelects[13] && swatchSelects[14] && !swatchSelects[9])) {
                int a[] = {9, 10, 11, 12, 13, 14};
                i = pickUnSelected(a);
            } else if ((swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26] && !swatchSelects[27]) ||
                    (swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && swatchSelects[27] && !swatchSelects[26]) ||
                    (swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[26] && swatchSelects[27] && !swatchSelects[25]) ||
                    (swatchSelects[22] && swatchSelects[23] && swatchSelects[25] && swatchSelects[26] && swatchSelects[27] && !swatchSelects[24]) ||
                    (swatchSelects[22] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26] && swatchSelects[27] && !swatchSelects[23]) ||
                    (swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26] && swatchSelects[27] && !swatchSelects[22])) {
                int a[] = {22, 23, 24, 25, 26, 27};
                i = pickUnSelected(a);
            } else if ((swatchSelects[1] && swatchSelects[5] && swatchSelects[10] && swatchSelects[16] && !swatchSelects[22]) ||
                    (swatchSelects[1] && swatchSelects[5] && swatchSelects[10] && swatchSelects[22] && !swatchSelects[16]) ||
                    (swatchSelects[1] && swatchSelects[5] && swatchSelects[16] && swatchSelects[22] && !swatchSelects[10]) ||
                    (swatchSelects[1] && swatchSelects[10] && swatchSelects[16] && swatchSelects[22] && !swatchSelects[5]) ||
                    (swatchSelects[5] && swatchSelects[10] && swatchSelects[16] && swatchSelects[22] && !swatchSelects[1])) {
                int a[] = {1, 5, 10, 16, 22};
                i = pickUnSelected(a);
            } else if ((swatchSelects[14] && swatchSelects[20] && swatchSelects[26] && swatchSelects[31] && !swatchSelects[35]) ||
                    (swatchSelects[14] && swatchSelects[20] && swatchSelects[26] && swatchSelects[35] && !swatchSelects[31]) ||
                    (swatchSelects[14] && swatchSelects[20] && swatchSelects[31] && swatchSelects[35] && !swatchSelects[26]) ||
                    (swatchSelects[14] && swatchSelects[26] && swatchSelects[31] && swatchSelects[35] && !swatchSelects[20]) ||
                    (swatchSelects[20] && swatchSelects[26] && swatchSelects[31] && swatchSelects[35] && !swatchSelects[14])) {
                int a[] = {14, 20, 26, 31, 35};
                i = pickUnSelected(a);
            } else if ((swatchSelects[2] && swatchSelects[7] && swatchSelects[13] && swatchSelects[20] && !swatchSelects[27]) ||
                    (swatchSelects[2] && swatchSelects[7] && swatchSelects[13] && swatchSelects[27] && !swatchSelects[20]) ||
                    (swatchSelects[2] && swatchSelects[7] && swatchSelects[20] && swatchSelects[27] && !swatchSelects[13]) ||
                    (swatchSelects[2] && swatchSelects[13] && swatchSelects[20] && swatchSelects[27] && !swatchSelects[7]) ||
                    (swatchSelects[7] && swatchSelects[13] && swatchSelects[20] && swatchSelects[27] && !swatchSelects[2])) {
                int a[] = {2, 7, 13, 20, 27};
                i = pickUnSelected(a);
            } else if ((swatchSelects[9] && swatchSelects[16] && swatchSelects[23] && swatchSelects[29] && !swatchSelects[34]) ||
                    (swatchSelects[9] && swatchSelects[16] && swatchSelects[23] && swatchSelects[34] && !swatchSelects[29]) ||
                    (swatchSelects[9] && swatchSelects[16] && swatchSelects[29] && swatchSelects[34] && !swatchSelects[23]) ||
                    (swatchSelects[9] && swatchSelects[23] && swatchSelects[29] && swatchSelects[34] && !swatchSelects[16]) ||
                    (swatchSelects[16] && swatchSelects[23] && swatchSelects[29] && swatchSelects[34] && !swatchSelects[9])) {
                int a[] = {9, 16, 23, 29, 34};
                i = pickUnSelected(a);
            } else if ((swatchSelects[4] && swatchSelects[5] && swatchSelects[6] && swatchSelects[7] && !swatchSelects[8]) ||
                    (swatchSelects[4] && swatchSelects[5] && swatchSelects[6] && swatchSelects[8] && !swatchSelects[7]) ||
                    (swatchSelects[4] && swatchSelects[5] && swatchSelects[7] && swatchSelects[8] && !swatchSelects[6]) ||
                    (swatchSelects[4] && swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && !swatchSelects[5]) ||
                    (swatchSelects[5] && swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && !swatchSelects[4])) {
                int a[] = {4, 5, 6, 7, 8};
                i = pickUnSelected(a);
            } else if ((swatchSelects[28] && swatchSelects[29] && swatchSelects[30] && swatchSelects[31] && !swatchSelects[32]) ||
                    (swatchSelects[28] && swatchSelects[29] && swatchSelects[30] && swatchSelects[32] && !swatchSelects[31]) ||
                    (swatchSelects[28] && swatchSelects[29] && swatchSelects[31] && swatchSelects[32] && !swatchSelects[30]) ||
                    (swatchSelects[28] && swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && !swatchSelects[29]) ||
                    (swatchSelects[29] && swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && !swatchSelects[28])) {
                int a[] = {28, 29, 30, 31, 32};
                i = pickUnSelected(a);
            } else if ((swatchSelects[0] && swatchSelects[1] && swatchSelects[2] && !swatchSelects[3]) ||
                    (swatchSelects[0] && swatchSelects[1] && swatchSelects[3] && !swatchSelects[2]) ||
                    (swatchSelects[0] && swatchSelects[2] && swatchSelects[3] && !swatchSelects[1]) ||
                    (swatchSelects[1] && swatchSelects[2] && swatchSelects[3] && !swatchSelects[0])) {
                int a[] = {0, 1, 2, 3};
                i = pickUnSelected(a);
            } else if ((swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && !swatchSelects[36]) ||
                    (swatchSelects[33] && swatchSelects[34] && swatchSelects[36] && !swatchSelects[35]) ||
                    (swatchSelects[33] && swatchSelects[35] && swatchSelects[36] && !swatchSelects[34]) ||
                    (swatchSelects[34] && swatchSelects[35] && swatchSelects[36] && !swatchSelects[33])) {
                int a[] = {33, 34, 35, 36};
                i = pickUnSelected(a);
            } else if ((swatchSelects[0] && swatchSelects[4] && swatchSelects[9] && !swatchSelects[15]) ||
                    (swatchSelects[0] && swatchSelects[4] && swatchSelects[15] && !swatchSelects[9]) ||
                    (swatchSelects[0] && swatchSelects[9] && swatchSelects[15] && !swatchSelects[4]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && !swatchSelects[0])) {
                int a[] = {0, 4, 9, 15};
                i = pickUnSelected(a);
            } else if ((swatchSelects[21] && swatchSelects[27] && swatchSelects[32] && !swatchSelects[36]) ||
                    (swatchSelects[21] && swatchSelects[27] && swatchSelects[36] && !swatchSelects[32]) ||
                    (swatchSelects[21] && swatchSelects[32] && swatchSelects[36] && !swatchSelects[27]) ||
                    (swatchSelects[27] && swatchSelects[32] && swatchSelects[36] && !swatchSelects[21])) {
                int a[] = {21, 27, 32, 36};
                i = pickUnSelected(a);
            } else if ((swatchSelects[3] && swatchSelects[8] && swatchSelects[14] && !swatchSelects[21]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[21] && !swatchSelects[14]) ||
                    (swatchSelects[3] && swatchSelects[14] && swatchSelects[21] && !swatchSelects[8]) ||
                    (swatchSelects[8] && swatchSelects[14] && swatchSelects[21] && !swatchSelects[3])) {
                int a[] = {3, 8, 14, 21};
                i = pickUnSelected(a);
            } else if ((swatchSelects[15] && swatchSelects[22] && swatchSelects[28] && !swatchSelects[33]) ||
                    (swatchSelects[15] && swatchSelects[22] && swatchSelects[33] && !swatchSelects[28]) ||
                    (swatchSelects[15] && swatchSelects[28] && swatchSelects[33] && !swatchSelects[22]) ||
                    (swatchSelects[22] && swatchSelects[28] && swatchSelects[33] && !swatchSelects[15])) {
                int a[] = {15, 22, 28, 33};
                i = pickUnSelected(a);
            }
//            else {
//                int a[] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
//                        19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 32, 33, 34, 35, 36};
//                i = pickUnSelectedRandom(a);
//            }
        }
        if (i >= 0) {
            return i;
        } else {
            return -1;
        }
    }

    private int attackStep_2() {
        int i = -1;
        if (boardRadius == 4) {
            // 9
            if ((swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] &&
                    swatchSelects[39] && swatchSelects[47] && swatchSelects[54] && !swatchSelects[60]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] &&
                            swatchSelects[39] && swatchSelects[47] && swatchSelects[60] && !swatchSelects[54]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] &&
                            swatchSelects[39] && swatchSelects[54] && swatchSelects[60] && !swatchSelects[47]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] &&
                            swatchSelects[47] && swatchSelects[54] && swatchSelects[60] && !swatchSelects[39]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[39] &&
                            swatchSelects[47] && swatchSelects[54] && swatchSelects[60] && !swatchSelects[30]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[30] && swatchSelects[39] &&
                            swatchSelects[47] && swatchSelects[54] && swatchSelects[60] && !swatchSelects[21]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[21] && swatchSelects[30] && swatchSelects[39] &&
                            swatchSelects[47] && swatchSelects[54] && swatchSelects[60] && !swatchSelects[13]) ||
                    (swatchSelects[0] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] && swatchSelects[39] &&
                            swatchSelects[47] && swatchSelects[54] && swatchSelects[60] && !swatchSelects[6]) ||
                    (swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] && swatchSelects[39] &&
                            swatchSelects[47] && swatchSelects[54] && swatchSelects[60] && !swatchSelects[0])) {
                int a[] = {0, 6, 13, 21, 30, 39, 47, 54, 60};
                i = pickUnSelected(a);
            } else if ((swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30] &&
                    swatchSelects[38] && swatchSelects[45] && swatchSelects[51] && !swatchSelects[56]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30] &&
                            swatchSelects[38] && swatchSelects[45] && swatchSelects[56] && !swatchSelects[51]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30] &&
                            swatchSelects[38] && swatchSelects[51] && swatchSelects[56] && !swatchSelects[45]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30] &&
                            swatchSelects[45] && swatchSelects[51] && swatchSelects[56] && !swatchSelects[38]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[38] &&
                            swatchSelects[45] && swatchSelects[51] && swatchSelects[56] && !swatchSelects[30]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[30] && swatchSelects[38] &&
                            swatchSelects[45] && swatchSelects[51] && swatchSelects[56] && !swatchSelects[22]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[22] && swatchSelects[30] && swatchSelects[38] &&
                            swatchSelects[45] && swatchSelects[51] && swatchSelects[56] && !swatchSelects[15]) ||
                    (swatchSelects[4] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30] && swatchSelects[38] &&
                            swatchSelects[45] && swatchSelects[51] && swatchSelects[56] && !swatchSelects[9]) ||
                    (swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30] && swatchSelects[38] &&
                            swatchSelects[45] && swatchSelects[51] && swatchSelects[56] && !swatchSelects[4])) {
                int a[] = {4, 9, 15, 22, 30, 38, 45, 51, 56};
                i = pickUnSelected(a);
            } else if ((swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30] &&
                    swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && !swatchSelects[34]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30] &&
                            swatchSelects[31] && swatchSelects[32] && swatchSelects[34] && !swatchSelects[33]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30] &&
                            swatchSelects[31] && swatchSelects[33] && swatchSelects[34] && !swatchSelects[32]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30] &&
                            swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && !swatchSelects[31]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[31] &&
                            swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && !swatchSelects[30]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[30] && swatchSelects[31] &&
                            swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && !swatchSelects[29]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[29] && swatchSelects[30] && swatchSelects[31] &&
                            swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && !swatchSelects[28]) ||
                    (swatchSelects[26] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30] && swatchSelects[31] &&
                            swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && !swatchSelects[27]) ||
                    (swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30] && swatchSelects[31] &&
                            swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && !swatchSelects[26])) {
                int a[] = {26, 27, 28, 29, 30, 31, 32, 33, 34};
                i = pickUnSelected(a);
            }

            // 8
            else if ((swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && swatchSelects[22] &&
                    swatchSelects[23] && swatchSelects[24] && !swatchSelects[25]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && swatchSelects[22] &&
                            swatchSelects[23] && swatchSelects[25] && !swatchSelects[24]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && swatchSelects[22] &&
                            swatchSelects[24] && swatchSelects[25] && !swatchSelects[23]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && swatchSelects[23] &&
                            swatchSelects[24] && swatchSelects[25] && !swatchSelects[22]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[22] && swatchSelects[23] &&
                            swatchSelects[24] && swatchSelects[25] && !swatchSelects[21]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[21] && swatchSelects[22] && swatchSelects[23] &&
                            swatchSelects[24] && swatchSelects[25] && !swatchSelects[20]) ||
                    (swatchSelects[18] && swatchSelects[20] && swatchSelects[21] && swatchSelects[22] && swatchSelects[23] &&
                            swatchSelects[24] && swatchSelects[25] && !swatchSelects[19]) ||
                    (swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && swatchSelects[22] && swatchSelects[23] &&
                            swatchSelects[24] && swatchSelects[25] && !swatchSelects[18])) {
                int a[] = {18, 19, 20, 21, 22, 23, 24, 25};
                i = pickUnSelected(a);
            } else if ((swatchSelects[35] && swatchSelects[36] && swatchSelects[37] && swatchSelects[38] && swatchSelects[39] &&
                    swatchSelects[40] && swatchSelects[41] && !swatchSelects[42]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[37] && swatchSelects[38] && swatchSelects[39] &&
                            swatchSelects[40] && swatchSelects[42] && !swatchSelects[41]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[37] && swatchSelects[38] && swatchSelects[39] &&
                            swatchSelects[41] && swatchSelects[42] && !swatchSelects[40]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[37] && swatchSelects[38] && swatchSelects[40] &&
                            swatchSelects[41] && swatchSelects[42] && !swatchSelects[39]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[37] && swatchSelects[39] && swatchSelects[40] &&
                            swatchSelects[41] && swatchSelects[42] && !swatchSelects[38]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[38] && swatchSelects[39] && swatchSelects[40] &&
                            swatchSelects[41] && swatchSelects[42] && !swatchSelects[37]) ||
                    (swatchSelects[35] && swatchSelects[37] && swatchSelects[38] && swatchSelects[39] && swatchSelects[40] &&
                            swatchSelects[41] && swatchSelects[42] && !swatchSelects[36]) ||
                    (swatchSelects[36] && swatchSelects[37] && swatchSelects[38] && swatchSelects[39] && swatchSelects[40] &&
                            swatchSelects[41] && swatchSelects[42] && !swatchSelects[35])) {
                int a[] = {35, 36, 37, 38, 39, 40, 41, 42};
                i = pickUnSelected(a);
            } else if ((swatchSelects[3] && swatchSelects[8] && swatchSelects[14] && swatchSelects[21] && swatchSelects[29] &&
                    swatchSelects[37] && swatchSelects[44] && !swatchSelects[50]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[14] && swatchSelects[21] && swatchSelects[29] &&
                            swatchSelects[37] && swatchSelects[50] && !swatchSelects[44]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[14] && swatchSelects[21] && swatchSelects[29] &&
                            swatchSelects[44] && swatchSelects[50] && !swatchSelects[37]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[14] && swatchSelects[21] && swatchSelects[37] &&
                            swatchSelects[44] && swatchSelects[50] && !swatchSelects[29]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[14] && swatchSelects[29] && swatchSelects[37] &&
                            swatchSelects[44] && swatchSelects[50] && !swatchSelects[21]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[21] && swatchSelects[29] && swatchSelects[37] &&
                            swatchSelects[44] && swatchSelects[50] && !swatchSelects[14]) ||
                    (swatchSelects[3] && swatchSelects[14] && swatchSelects[21] && swatchSelects[29] && swatchSelects[37] &&
                            swatchSelects[44] && swatchSelects[50] && !swatchSelects[8]) ||
                    (swatchSelects[8] && swatchSelects[14] && swatchSelects[21] && swatchSelects[29] && swatchSelects[37] &&
                            swatchSelects[44] && swatchSelects[50] && !swatchSelects[3])) {
                int a[] = {3, 8, 14, 21, 29, 37, 44, 50};
                i = pickUnSelected(a);
            } else if ((swatchSelects[10] && swatchSelects[16] && swatchSelects[23] && swatchSelects[31] && swatchSelects[39] &&
                    swatchSelects[46] && swatchSelects[52] && !swatchSelects[57]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[23] && swatchSelects[31] && swatchSelects[39] &&
                            swatchSelects[46] && swatchSelects[57] && !swatchSelects[52]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[23] && swatchSelects[31] && swatchSelects[39] &&
                            swatchSelects[52] && swatchSelects[57] && !swatchSelects[46]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[23] && swatchSelects[31] && swatchSelects[46] &&
                            swatchSelects[52] && swatchSelects[57] && !swatchSelects[39]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[23] && swatchSelects[39] && swatchSelects[46] &&
                            swatchSelects[52] && swatchSelects[57] && !swatchSelects[31]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[31] && swatchSelects[39] && swatchSelects[46] &&
                            swatchSelects[52] && swatchSelects[57] && !swatchSelects[23]) ||
                    (swatchSelects[10] && swatchSelects[23] && swatchSelects[31] && swatchSelects[39] && swatchSelects[46] &&
                            swatchSelects[52] && swatchSelects[57] && !swatchSelects[16]) ||
                    (swatchSelects[16] && swatchSelects[23] && swatchSelects[31] && swatchSelects[39] && swatchSelects[46] &&
                            swatchSelects[52] && swatchSelects[57] && !swatchSelects[10])) {
                int a[] = {10, 16, 23, 31, 39, 46, 52, 57};
                i = pickUnSelected(a);
            } else if ((swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31] &&
                    swatchSelects[40] && swatchSelects[48] && !swatchSelects[55]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31] &&
                            swatchSelects[40] && swatchSelects[55] && !swatchSelects[48]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31] &&
                            swatchSelects[48] && swatchSelects[55] && !swatchSelects[40]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[40] &&
                            swatchSelects[48] && swatchSelects[55] && !swatchSelects[31]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[31] && swatchSelects[40] &&
                            swatchSelects[48] && swatchSelects[55] && !swatchSelects[22]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[22] && swatchSelects[31] && swatchSelects[40] &&
                            swatchSelects[48] && swatchSelects[55] && !swatchSelects[14]) ||
                    (swatchSelects[1] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31] && swatchSelects[40] &&
                            swatchSelects[48] && swatchSelects[55] && !swatchSelects[7]) ||
                    (swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31] && swatchSelects[40] &&
                            swatchSelects[48] && swatchSelects[55] && !swatchSelects[1])) {
                int a[] = {1, 7, 14, 22, 31, 40, 48, 55};
                i = pickUnSelected(a);
            } else if ((swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[38] &&
                    swatchSelects[46] && swatchSelects[53] && !swatchSelects[59]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[38] &&
                            swatchSelects[46] && swatchSelects[59] && !swatchSelects[53]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[38] &&
                            swatchSelects[53] && swatchSelects[59] && !swatchSelects[46]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[46] &&
                            swatchSelects[53] && swatchSelects[59] && !swatchSelects[38]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[38] && swatchSelects[46] &&
                            swatchSelects[53] && swatchSelects[59] && !swatchSelects[29]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[29] && swatchSelects[38] && swatchSelects[46] &&
                            swatchSelects[53] && swatchSelects[59] && !swatchSelects[20]) ||
                    (swatchSelects[5] && swatchSelects[20] && swatchSelects[29] && swatchSelects[38] && swatchSelects[46] &&
                            swatchSelects[53] && swatchSelects[59] && !swatchSelects[12]) ||
                    (swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[38] && swatchSelects[46] &&
                            swatchSelects[53] && swatchSelects[59] && !swatchSelects[5])) {
                int a[] = {5, 12, 20, 29, 38, 46, 53, 59};
                i = pickUnSelected(a);
            }

            // 7
            else if ((swatchSelects[11] && swatchSelects[12] && swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && !swatchSelects[17]) ||
                    (swatchSelects[11] && swatchSelects[12] && swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[17] && !swatchSelects[16]) ||
                    (swatchSelects[11] && swatchSelects[12] && swatchSelects[13] && swatchSelects[14] && swatchSelects[16] && swatchSelects[17] && !swatchSelects[15]) ||
                    (swatchSelects[11] && swatchSelects[12] && swatchSelects[13] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && !swatchSelects[14]) ||
                    (swatchSelects[11] && swatchSelects[12] && swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && !swatchSelects[13]) ||
                    (swatchSelects[11] && swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && !swatchSelects[12]) ||
                    (swatchSelects[12] && swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && !swatchSelects[11])) {
                int a[] = {11, 12, 13, 14, 15, 16, 17};
                i = pickUnSelected(a);
            } else if ((swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47] && swatchSelects[48] && !swatchSelects[49]) ||
                    (swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47] && swatchSelects[49] && !swatchSelects[48]) ||
                    (swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[47]) ||
                    (swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[46]) ||
                    (swatchSelects[43] && swatchSelects[44] && swatchSelects[46] && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[45]) ||
                    (swatchSelects[43] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[44]) ||
                    (swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[43])) {
                int a[] = {43, 44, 45, 46, 47, 48, 49};
                i = pickUnSelected(a);
            } else if ((swatchSelects[2] && swatchSelects[7] && swatchSelects[13] && swatchSelects[20] && swatchSelects[28] && swatchSelects[36] && !swatchSelects[43]) ||
                    (swatchSelects[2] && swatchSelects[7] && swatchSelects[13] && swatchSelects[20] && swatchSelects[28] && swatchSelects[43] && !swatchSelects[36]) ||
                    (swatchSelects[2] && swatchSelects[7] && swatchSelects[13] && swatchSelects[20] && swatchSelects[36] && swatchSelects[43] && !swatchSelects[28]) ||
                    (swatchSelects[2] && swatchSelects[7] && swatchSelects[13] && swatchSelects[28] && swatchSelects[36] && swatchSelects[43] && !swatchSelects[20]) ||
                    (swatchSelects[2] && swatchSelects[7] && swatchSelects[20] && swatchSelects[28] && swatchSelects[36] && swatchSelects[43] && !swatchSelects[13]) ||
                    (swatchSelects[2] && swatchSelects[13] && swatchSelects[20] && swatchSelects[28] && swatchSelects[36] && swatchSelects[43] && !swatchSelects[7]) ||
                    (swatchSelects[7] && swatchSelects[13] && swatchSelects[20] && swatchSelects[28] && swatchSelects[36] && swatchSelects[43] && !swatchSelects[2])) {
                int a[] = {2, 7, 13, 20, 28, 36, 43};
                i = pickUnSelected(a);
            } else if ((swatchSelects[17] && swatchSelects[24] && swatchSelects[32] && swatchSelects[40] && swatchSelects[47] && swatchSelects[53] && !swatchSelects[58]) ||
                    (swatchSelects[17] && swatchSelects[24] && swatchSelects[32] && swatchSelects[40] && swatchSelects[47] && swatchSelects[58] && !swatchSelects[53]) ||
                    (swatchSelects[17] && swatchSelects[24] && swatchSelects[32] && swatchSelects[40] && swatchSelects[53] && swatchSelects[58] && !swatchSelects[47]) ||
                    (swatchSelects[17] && swatchSelects[24] && swatchSelects[32] && swatchSelects[47] && swatchSelects[53] && swatchSelects[58] && !swatchSelects[40]) ||
                    (swatchSelects[17] && swatchSelects[24] && swatchSelects[40] && swatchSelects[47] && swatchSelects[53] && swatchSelects[58] && !swatchSelects[32]) ||
                    (swatchSelects[17] && swatchSelects[32] && swatchSelects[40] && swatchSelects[47] && swatchSelects[53] && swatchSelects[58] && !swatchSelects[24]) ||
                    (swatchSelects[24] && swatchSelects[32] && swatchSelects[40] && swatchSelects[47] && swatchSelects[53] && swatchSelects[58] && !swatchSelects[17])) {
                int a[] = {17, 24, 32, 40, 47, 53, 58};
                i = pickUnSelected(a);
            } else if ((swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32] && swatchSelects[41] && !swatchSelects[49]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32] && swatchSelects[49] && !swatchSelects[41]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[41] && swatchSelects[49] && !swatchSelects[32]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[32] && swatchSelects[41] && swatchSelects[49] && !swatchSelects[23]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[23] && swatchSelects[32] && swatchSelects[41] && swatchSelects[49] && !swatchSelects[15]) ||
                    (swatchSelects[2] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32] && swatchSelects[41] && swatchSelects[49] && !swatchSelects[8]) ||
                    (swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32] && swatchSelects[41] && swatchSelects[49] && !swatchSelects[2])) {
                int a[] = {2, 8, 15, 23, 32, 41, 49};
                i = pickUnSelected(a);
            } else if ((swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[37] && swatchSelects[45] && swatchSelects[52] && !swatchSelects[58]) ||
                    (swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[37] && swatchSelects[45] && swatchSelects[58] && !swatchSelects[52]) ||
                    (swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[37] && swatchSelects[52] && swatchSelects[58] && !swatchSelects[45]) ||
                    (swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[45] && swatchSelects[52] && swatchSelects[58] && !swatchSelects[37]) ||
                    (swatchSelects[11] && swatchSelects[19] && swatchSelects[37] && swatchSelects[45] && swatchSelects[52] && swatchSelects[58] && !swatchSelects[28]) ||
                    (swatchSelects[11] && swatchSelects[28] && swatchSelects[37] && swatchSelects[45] && swatchSelects[52] && swatchSelects[58] && !swatchSelects[19]) ||
                    (swatchSelects[19] && swatchSelects[28] && swatchSelects[37] && swatchSelects[45] && swatchSelects[52] && swatchSelects[58] && !swatchSelects[11])) {
                int a[] = {11, 19, 28, 37, 45, 52, 58};
                i = pickUnSelected(a);
            } else if ((swatchSelects[5] && swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && swatchSelects[9] && !swatchSelects[10]) ||
                    (swatchSelects[5] && swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && swatchSelects[10] && !swatchSelects[9]) ||
                    (swatchSelects[5] && swatchSelects[6] && swatchSelects[7] && swatchSelects[9] && swatchSelects[10] && !swatchSelects[8]) ||
                    (swatchSelects[5] && swatchSelects[6] && swatchSelects[8] && swatchSelects[9] && swatchSelects[10] && !swatchSelects[7]) ||
                    (swatchSelects[5] && swatchSelects[7] && swatchSelects[8] && swatchSelects[9] && swatchSelects[10] && !swatchSelects[6]) ||
                    (swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && swatchSelects[9] && swatchSelects[10] && !swatchSelects[5])) {
                int a[] = {5, 6, 7, 8, 9, 10};
                i = pickUnSelected(a);
            } else if ((swatchSelects[50] && swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && !swatchSelects[55]) ||
                    (swatchSelects[50] && swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[55] && !swatchSelects[54]) ||
                    (swatchSelects[50] && swatchSelects[51] && swatchSelects[52] && swatchSelects[54] && swatchSelects[55] && !swatchSelects[53]) ||
                    (swatchSelects[50] && swatchSelects[51] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && !swatchSelects[52]) ||
                    (swatchSelects[50] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && !swatchSelects[51]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && !swatchSelects[50])) {
                int a[] = {50, 51, 52, 53, 54, 55};
                i = pickUnSelected(a);
            } else if ((swatchSelects[1] && swatchSelects[6] && swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && !swatchSelects[35]) ||
                    (swatchSelects[1] && swatchSelects[6] && swatchSelects[12] && swatchSelects[19] && swatchSelects[35] && !swatchSelects[27]) ||
                    (swatchSelects[1] && swatchSelects[6] && swatchSelects[12] && swatchSelects[27] && swatchSelects[35] && !swatchSelects[19]) ||
                    (swatchSelects[1] && swatchSelects[6] && swatchSelects[19] && swatchSelects[27] && swatchSelects[35] && !swatchSelects[12]) ||
                    (swatchSelects[1] && swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[35] && !swatchSelects[6]) ||
                    (swatchSelects[6] && swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[35] && !swatchSelects[1])) {
                int a[] = {1, 6, 12, 19, 27, 35};
                i = pickUnSelected(a);
            } else if ((swatchSelects[25] && swatchSelects[33] && swatchSelects[41] && swatchSelects[48] && swatchSelects[54] && !swatchSelects[59]) ||
                    (swatchSelects[25] && swatchSelects[33] && swatchSelects[41] && swatchSelects[48] && swatchSelects[59] && !swatchSelects[54]) ||
                    (swatchSelects[25] && swatchSelects[33] && swatchSelects[41] && swatchSelects[54] && swatchSelects[59] && !swatchSelects[48]) ||
                    (swatchSelects[25] && swatchSelects[33] && swatchSelects[48] && swatchSelects[54] && swatchSelects[59] && !swatchSelects[41]) ||
                    (swatchSelects[25] && swatchSelects[41] && swatchSelects[48] && swatchSelects[54] && swatchSelects[59] && !swatchSelects[33]) ||
                    (swatchSelects[33] && swatchSelects[41] && swatchSelects[48] && swatchSelects[54] && swatchSelects[59] && !swatchSelects[25])) {
                int a[] = {25, 33, 41, 48, 54, 59};
                i = pickUnSelected(a);
            } else if ((swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33] && !swatchSelects[42]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[42] && !swatchSelects[33]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[33] && swatchSelects[42] && !swatchSelects[24]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[24] && swatchSelects[33] && swatchSelects[42] && !swatchSelects[16]) ||
                    (swatchSelects[3] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33] && swatchSelects[42] && !swatchSelects[9]) ||
                    (swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33] && swatchSelects[42] && !swatchSelects[3])) {
                int a[] = {3, 9, 16, 24, 33, 42};
                i = pickUnSelected(a);
            } else if ((swatchSelects[18] && swatchSelects[27] && swatchSelects[36] && swatchSelects[44] && swatchSelects[51] && !swatchSelects[57]) ||
                    (swatchSelects[18] && swatchSelects[27] && swatchSelects[36] && swatchSelects[44] && swatchSelects[57] && !swatchSelects[51]) ||
                    (swatchSelects[18] && swatchSelects[27] && swatchSelects[36] && swatchSelects[51] && swatchSelects[57] && !swatchSelects[44]) ||
                    (swatchSelects[18] && swatchSelects[27] && swatchSelects[44] && swatchSelects[51] && swatchSelects[57] && !swatchSelects[36]) ||
                    (swatchSelects[18] && swatchSelects[36] && swatchSelects[44] && swatchSelects[51] && swatchSelects[57] && !swatchSelects[27]) ||
                    (swatchSelects[27] && swatchSelects[36] && swatchSelects[44] && swatchSelects[51] && swatchSelects[57] && !swatchSelects[18])) {
                int a[] = {18, 27, 36, 44, 51, 57};
                i = pickUnSelected(a);
            } else if ((swatchSelects[0] && swatchSelects[1] && swatchSelects[2] && swatchSelects[3] && !swatchSelects[4]) ||
                    (swatchSelects[0] && swatchSelects[1] && swatchSelects[2] && swatchSelects[4] && !swatchSelects[3]) ||
                    (swatchSelects[0] && swatchSelects[1] && swatchSelects[3] && swatchSelects[4] && !swatchSelects[2]) ||
                    (swatchSelects[0] && swatchSelects[2] && swatchSelects[3] && swatchSelects[4] && !swatchSelects[1]) ||
                    (swatchSelects[1] && swatchSelects[2] && swatchSelects[3] && swatchSelects[4] && !swatchSelects[0])) {
                int a[] = {0, 1, 2, 3, 4};
                i = pickUnSelected(a);
            } else if ((swatchSelects[56] && swatchSelects[57] && swatchSelects[58] && swatchSelects[59] && !swatchSelects[60]) ||
                    (swatchSelects[56] && swatchSelects[57] && swatchSelects[58] && swatchSelects[60] && !swatchSelects[59]) ||
                    (swatchSelects[56] && swatchSelects[57] && swatchSelects[59] && swatchSelects[60] && !swatchSelects[58]) ||
                    (swatchSelects[56] && swatchSelects[58] && swatchSelects[59] && swatchSelects[60] && !swatchSelects[57]) ||
                    (swatchSelects[57] && swatchSelects[58] && swatchSelects[59] && swatchSelects[60] && !swatchSelects[56])) {
                int a[] = {56, 57, 58, 59, 60};
                i = pickUnSelected(a);
            } else if ((swatchSelects[0] && swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && !swatchSelects[26]) ||
                    (swatchSelects[0] && swatchSelects[5] && swatchSelects[11] && swatchSelects[26] && !swatchSelects[18]) ||
                    (swatchSelects[0] && swatchSelects[5] && swatchSelects[18] && swatchSelects[26] && !swatchSelects[11]) ||
                    (swatchSelects[0] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && !swatchSelects[5]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && !swatchSelects[0])) {
                int a[] = {0, 5, 11, 18, 26};
                i = pickUnSelected(a);
            } else if ((swatchSelects[34] && swatchSelects[42] && swatchSelects[49] && swatchSelects[55] && !swatchSelects[60]) ||
                    (swatchSelects[34] && swatchSelects[42] && swatchSelects[49] && swatchSelects[60] && !swatchSelects[55]) ||
                    (swatchSelects[34] && swatchSelects[42] && swatchSelects[55] && swatchSelects[60] && !swatchSelects[49]) ||
                    (swatchSelects[34] && swatchSelects[49] && swatchSelects[55] && swatchSelects[60] && !swatchSelects[42]) ||
                    (swatchSelects[42] && swatchSelects[49] && swatchSelects[55] && swatchSelects[60] && !swatchSelects[34])) {
                int a[] = {34, 42, 49, 55, 60};
                i = pickUnSelected(a);
            } else if ((swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && !swatchSelects[34]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[34] && !swatchSelects[25]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[25] && swatchSelects[34] && !swatchSelects[17]) ||
                    (swatchSelects[4] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && !swatchSelects[10]) ||
                    (swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && !swatchSelects[4])) {
                int a[] = {4, 10, 17, 25, 34};
                i = pickUnSelected(a);
            } else if ((swatchSelects[26] && swatchSelects[35] && swatchSelects[43] && swatchSelects[50] && !swatchSelects[56]) ||
                    (swatchSelects[26] && swatchSelects[35] && swatchSelects[43] && swatchSelects[56] && !swatchSelects[50]) ||
                    (swatchSelects[26] && swatchSelects[35] && swatchSelects[50] && swatchSelects[56] && !swatchSelects[43]) ||
                    (swatchSelects[26] && swatchSelects[43] && swatchSelects[50] && swatchSelects[56] && !swatchSelects[35]) ||
                    (swatchSelects[35] && swatchSelects[43] && swatchSelects[50] && swatchSelects[56] && !swatchSelects[26])) {
                int a[] = {26, 35, 43, 50, 56};
                i = pickUnSelected(a);
            }
//            else {
//                int a[] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
//                        32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60};
//                i = pickUnSelectedRandom(a);
//            }
        } else if (boardRadius == 5) {
            // 11
            if ((swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] &&
                    swatchSelects[46] && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] &&
                            swatchSelects[46] && swatchSelects[47] && swatchSelects[48] && swatchSelects[50] && !swatchSelects[49]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] &&
                            swatchSelects[46] && swatchSelects[47] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[48]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] &&
                            swatchSelects[46] && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[47]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] &&
                            swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[46]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[46] &&
                            swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[45]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[45] && swatchSelects[46] &&
                            swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[44]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] &&
                            swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[43]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] &&
                            swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[42]) ||
                    (swatchSelects[40] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] &&
                            swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[41]) ||
                    (swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] &&
                            swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[40])) {
                int a[] = {40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50};
                i = pickUnSelected(a);
            } else if ((swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] &&
                    swatchSelects[55] && swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] &&
                            swatchSelects[55] && swatchSelects[64] && swatchSelects[72] && swatchSelects[85] && !swatchSelects[79]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] &&
                            swatchSelects[55] && swatchSelects[64] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[72]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] &&
                            swatchSelects[55] && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[64]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] &&
                            swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[55]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[55] &&
                            swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[45]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[45] && swatchSelects[55] &&
                            swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[35]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55] &&
                            swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[26]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55] &&
                            swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[18]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55] &&
                            swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[11]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55] &&
                            swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[5])) {
                int a[] = {5, 11, 18, 26, 35, 45, 55, 64, 72, 79, 85};
                i = pickUnSelected(a);
            } else if ((swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] &&
                    swatchSelects[56] && swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] &&
                            swatchSelects[56] && swatchSelects[66] && swatchSelects[75] && swatchSelects[90] && !swatchSelects[83]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] &&
                            swatchSelects[56] && swatchSelects[66] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[75]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] &&
                            swatchSelects[56] && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[66]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] &&
                            swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[56]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[56] &&
                            swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[45]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[45] && swatchSelects[56] &&
                            swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[34]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56] &&
                            swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[24]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56] &&
                            swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[15]) ||
                    (swatchSelects[0] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56] &&
                            swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[7]) ||
                    (swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56] &&
                            swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[0])) {
                int a[] = {0, 7, 15, 24, 34, 45, 56, 66, 75, 83, 90};
                i = pickUnSelected(a);
            } else if ((swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] &&
                    swatchSelects[36] && swatchSelects[37] && swatchSelects[38] && !swatchSelects[39]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] &&
                            swatchSelects[36] && swatchSelects[37] && swatchSelects[39] && !swatchSelects[38]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] &&
                            swatchSelects[36] && swatchSelects[38] && swatchSelects[39] && !swatchSelects[37]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] &&
                            swatchSelects[37] && swatchSelects[38] && swatchSelects[39] && !swatchSelects[36]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[36] &&
                            swatchSelects[37] && swatchSelects[38] && swatchSelects[39] && !swatchSelects[35]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[35] && swatchSelects[36] &&
                            swatchSelects[37] && swatchSelects[38] && swatchSelects[39] && !swatchSelects[34]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36] &&
                            swatchSelects[37] && swatchSelects[38] && swatchSelects[39] && !swatchSelects[33]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36] &&
                            swatchSelects[37] && swatchSelects[38] && swatchSelects[39] && !swatchSelects[32]) ||
                    (swatchSelects[30] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36] &&
                            swatchSelects[37] && swatchSelects[38] && swatchSelects[39] && !swatchSelects[31]) ||
                    (swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36] &&
                            swatchSelects[37] && swatchSelects[38] && swatchSelects[39] && !swatchSelects[30])) {
                int a[] = {30, 31, 32, 33, 34, 35, 36, 37, 38, 39};
                i = pickUnSelected(a);
            } else if ((swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] &&
                    swatchSelects[57] && swatchSelects[58] && swatchSelects[59] && !swatchSelects[60]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] &&
                            swatchSelects[57] && swatchSelects[58] && swatchSelects[60] && !swatchSelects[59]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] &&
                            swatchSelects[57] && swatchSelects[59] && swatchSelects[60] && !swatchSelects[58]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] &&
                            swatchSelects[58] && swatchSelects[59] && swatchSelects[60] && !swatchSelects[57]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[57] &&
                            swatchSelects[58] && swatchSelects[59] && swatchSelects[60] && !swatchSelects[56]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[56] && swatchSelects[57] &&
                            swatchSelects[58] && swatchSelects[59] && swatchSelects[60] && !swatchSelects[55]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57] &&
                            swatchSelects[58] && swatchSelects[59] && swatchSelects[60] && !swatchSelects[54]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57] &&
                            swatchSelects[58] && swatchSelects[59] && swatchSelects[60] && !swatchSelects[53]) ||
                    (swatchSelects[51] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57] &&
                            swatchSelects[58] && swatchSelects[59] && swatchSelects[60] && !swatchSelects[52]) ||
                    (swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57] &&
                            swatchSelects[58] && swatchSelects[59] && swatchSelects[60] && !swatchSelects[51])) {
                int a[] = {51, 52, 53, 54, 55, 56, 57, 58, 59, 60};
                i = pickUnSelected(a);
            } else if ((swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] &&
                    swatchSelects[54] && swatchSelects[63] && swatchSelects[71] && !swatchSelects[78]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] &&
                            swatchSelects[54] && swatchSelects[63] && swatchSelects[78] && !swatchSelects[71]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] &&
                            swatchSelects[54] && swatchSelects[71] && swatchSelects[78] && !swatchSelects[63]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] &&
                            swatchSelects[63] && swatchSelects[71] && swatchSelects[78] && !swatchSelects[54]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[54] &&
                            swatchSelects[63] && swatchSelects[71] && swatchSelects[78] && !swatchSelects[44]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[44] && swatchSelects[54] &&
                            swatchSelects[63] && swatchSelects[71] && swatchSelects[78] && !swatchSelects[34]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54] &&
                            swatchSelects[63] && swatchSelects[71] && swatchSelects[78] && !swatchSelects[25]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54] &&
                            swatchSelects[63] && swatchSelects[71] && swatchSelects[78] && !swatchSelects[17]) ||
                    (swatchSelects[4] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54] &&
                            swatchSelects[63] && swatchSelects[71] && swatchSelects[78] && !swatchSelects[10]) ||
                    (swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54] &&
                            swatchSelects[63] && swatchSelects[71] && swatchSelects[78] && !swatchSelects[4])) {
                int a[] = {4, 10, 17, 25, 34, 44, 54, 63, 71, 78};
                i = pickUnSelected(a);
            } else if ((swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] &&
                    swatchSelects[65] && swatchSelects[73] && swatchSelects[80] && !swatchSelects[86]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] &&
                            swatchSelects[65] && swatchSelects[73] && swatchSelects[86] && !swatchSelects[80]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] &&
                            swatchSelects[65] && swatchSelects[80] && swatchSelects[86] && !swatchSelects[73]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] &&
                            swatchSelects[73] && swatchSelects[80] && swatchSelects[86] && !swatchSelects[65]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[65] &&
                            swatchSelects[73] && swatchSelects[80] && swatchSelects[86] && !swatchSelects[56]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[56] && swatchSelects[65] &&
                            swatchSelects[73] && swatchSelects[80] && swatchSelects[86] && !swatchSelects[46]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65] &&
                            swatchSelects[73] && swatchSelects[80] && swatchSelects[86] && !swatchSelects[36]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65] &&
                            swatchSelects[73] && swatchSelects[80] && swatchSelects[86] && !swatchSelects[27]) ||
                    (swatchSelects[12] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65] &&
                            swatchSelects[73] && swatchSelects[80] && swatchSelects[86] && !swatchSelects[19]) ||
                    (swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65] &&
                            swatchSelects[73] && swatchSelects[80] && swatchSelects[86] && !swatchSelects[12])) {
                int a[] = {12, 19, 27, 36, 46, 56, 65, 73, 80, 86};
                i = pickUnSelected(a);
            } else if ((swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] &&
                    swatchSelects[57] && swatchSelects[67] && swatchSelects[76] && !swatchSelects[84]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] &&
                            swatchSelects[57] && swatchSelects[67] && swatchSelects[84] && !swatchSelects[76]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] &&
                            swatchSelects[57] && swatchSelects[76] && swatchSelects[84] && !swatchSelects[67]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] &&
                            swatchSelects[67] && swatchSelects[76] && swatchSelects[84] && !swatchSelects[57]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[57] &&
                            swatchSelects[67] && swatchSelects[76] && swatchSelects[84] && !swatchSelects[46]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[46] && swatchSelects[57] &&
                            swatchSelects[67] && swatchSelects[76] && swatchSelects[84] && !swatchSelects[35]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57] &&
                            swatchSelects[67] && swatchSelects[76] && swatchSelects[84] && !swatchSelects[25]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57] &&
                            swatchSelects[67] && swatchSelects[76] && swatchSelects[84] && !swatchSelects[16]) ||
                    (swatchSelects[1] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57] &&
                            swatchSelects[67] && swatchSelects[76] && swatchSelects[84] && !swatchSelects[8]) ||
                    (swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57] &&
                            swatchSelects[67] && swatchSelects[76] && swatchSelects[84] && !swatchSelects[1])) {
                int a[] = {1, 8, 16, 25, 35, 46, 57, 67, 76, 84};
                i = pickUnSelected(a);
            } else if ((swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] &&
                    swatchSelects[65] && swatchSelects[74] && swatchSelects[82] && !swatchSelects[89]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] &&
                            swatchSelects[65] && swatchSelects[74] && swatchSelects[89] && !swatchSelects[82]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] &&
                            swatchSelects[65] && swatchSelects[82] && swatchSelects[89] && !swatchSelects[74]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] &&
                            swatchSelects[74] && swatchSelects[82] && swatchSelects[89] && !swatchSelects[65]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[65] &&
                            swatchSelects[74] && swatchSelects[82] && swatchSelects[89] && !swatchSelects[55]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[55] && swatchSelects[65] &&
                            swatchSelects[74] && swatchSelects[82] && swatchSelects[89] && !swatchSelects[44]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65] &&
                            swatchSelects[74] && swatchSelects[82] && swatchSelects[89] && !swatchSelects[33]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65] &&
                            swatchSelects[74] && swatchSelects[82] && swatchSelects[89] && !swatchSelects[23]) ||
                    (swatchSelects[6] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65] &&
                            swatchSelects[74] && swatchSelects[82] && swatchSelects[89] && !swatchSelects[14]) ||
                    (swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65] &&
                            swatchSelects[74] && swatchSelects[82] && swatchSelects[89] && !swatchSelects[6])) {
                int a[] = {6, 14, 23, 33, 44, 55, 65, 74, 82, 89};
                i = pickUnSelected(a);
            } else if ((swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] &&
                    swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && !swatchSelects[29]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] &&
                            swatchSelects[26] && swatchSelects[27] && swatchSelects[29] && !swatchSelects[28]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] &&
                            swatchSelects[26] && swatchSelects[28] && swatchSelects[29] && !swatchSelects[27]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] &&
                            swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && !swatchSelects[26]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[26] &&
                            swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && !swatchSelects[25]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[25] && swatchSelects[26] &&
                            swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && !swatchSelects[24]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26] &&
                            swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && !swatchSelects[23]) ||
                    (swatchSelects[21] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26] &&
                            swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && !swatchSelects[22]) ||
                    (swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26] &&
                            swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && !swatchSelects[21])) {
                int a[] = {21, 22, 23, 24, 25, 26, 27, 28, 29};
                i = pickUnSelected(a);
            } else if ((swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65] &&
                    swatchSelects[66] && swatchSelects[67] && swatchSelects[68] && !swatchSelects[69]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65] &&
                            swatchSelects[66] && swatchSelects[67] && swatchSelects[69] && !swatchSelects[68]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65] &&
                            swatchSelects[66] && swatchSelects[68] && swatchSelects[69] && !swatchSelects[67]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65] &&
                            swatchSelects[67] && swatchSelects[68] && swatchSelects[69] && !swatchSelects[66]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[66] &&
                            swatchSelects[67] && swatchSelects[68] && swatchSelects[69] && !swatchSelects[65]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[65] && swatchSelects[66] &&
                            swatchSelects[67] && swatchSelects[68] && swatchSelects[69] && !swatchSelects[64]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[64] && swatchSelects[65] && swatchSelects[66] &&
                            swatchSelects[67] && swatchSelects[68] && swatchSelects[69] && !swatchSelects[63]) ||
                    (swatchSelects[61] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65] && swatchSelects[66] &&
                            swatchSelects[67] && swatchSelects[68] && swatchSelects[69] && !swatchSelects[62]) ||
                    (swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65] && swatchSelects[66] &&
                            swatchSelects[67] && swatchSelects[68] && swatchSelects[69] && !swatchSelects[61])) {
                int a[] = {61, 62, 63, 64, 65, 66, 67, 68, 69};
                i = pickUnSelected(a);
            } else if ((swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33] &&
                    swatchSelects[43] && swatchSelects[53] && swatchSelects[62] && !swatchSelects[70]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33] &&
                            swatchSelects[43] && swatchSelects[53] && swatchSelects[70] && !swatchSelects[62]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33] &&
                            swatchSelects[43] && swatchSelects[62] && swatchSelects[70] && !swatchSelects[53]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33] &&
                            swatchSelects[53] && swatchSelects[62] && swatchSelects[70] && !swatchSelects[43]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[43] &&
                            swatchSelects[53] && swatchSelects[62] && swatchSelects[70] && !swatchSelects[33]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[33] && swatchSelects[43] &&
                            swatchSelects[53] && swatchSelects[62] && swatchSelects[70] && !swatchSelects[24]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[24] && swatchSelects[33] && swatchSelects[43] &&
                            swatchSelects[53] && swatchSelects[62] && swatchSelects[70] && !swatchSelects[16]) ||
                    (swatchSelects[3] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33] && swatchSelects[43] &&
                            swatchSelects[53] && swatchSelects[62] && swatchSelects[70] && !swatchSelects[9]) ||
                    (swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33] && swatchSelects[43] &&
                            swatchSelects[53] && swatchSelects[62] && swatchSelects[70] && !swatchSelects[3])) {
                int a[] = {3, 9, 16, 24, 33, 43, 53, 62, 70};
                i = pickUnSelected(a);
            } else if ((swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57] &&
                    swatchSelects[66] && swatchSelects[74] && swatchSelects[81] && !swatchSelects[87]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57] &&
                            swatchSelects[66] && swatchSelects[74] && swatchSelects[87] && !swatchSelects[81]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57] &&
                            swatchSelects[66] && swatchSelects[81] && swatchSelects[87] && !swatchSelects[74]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57] &&
                            swatchSelects[74] && swatchSelects[81] && swatchSelects[87] && !swatchSelects[66]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[66] &&
                            swatchSelects[74] && swatchSelects[81] && swatchSelects[87] && !swatchSelects[57]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[57] && swatchSelects[66] &&
                            swatchSelects[74] && swatchSelects[81] && swatchSelects[87] && !swatchSelects[47]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[47] && swatchSelects[57] && swatchSelects[66] &&
                            swatchSelects[74] && swatchSelects[81] && swatchSelects[87] && !swatchSelects[37]) ||
                    (swatchSelects[20] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57] && swatchSelects[66] &&
                            swatchSelects[74] && swatchSelects[81] && swatchSelects[87] && !swatchSelects[28]) ||
                    (swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57] && swatchSelects[66] &&
                            swatchSelects[74] && swatchSelects[81] && swatchSelects[87] && !swatchSelects[20])) {
                int a[] = {20, 28, 37, 47, 57, 66, 74, 81, 87};
                i = pickUnSelected(a);
            } else if ((swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36] &&
                    swatchSelects[47] && swatchSelects[58] && swatchSelects[68] && !swatchSelects[77]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36] &&
                            swatchSelects[47] && swatchSelects[58] && swatchSelects[77] && !swatchSelects[68]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36] &&
                            swatchSelects[47] && swatchSelects[68] && swatchSelects[77] && !swatchSelects[58]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36] &&
                            swatchSelects[58] && swatchSelects[68] && swatchSelects[77] && !swatchSelects[47]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[47] &&
                            swatchSelects[58] && swatchSelects[68] && swatchSelects[77] && !swatchSelects[36]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[36] && swatchSelects[47] &&
                            swatchSelects[58] && swatchSelects[68] && swatchSelects[77] && !swatchSelects[26]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[26] && swatchSelects[36] && swatchSelects[47] &&
                            swatchSelects[58] && swatchSelects[68] && swatchSelects[77] && !swatchSelects[17]) ||
                    (swatchSelects[2] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36] && swatchSelects[47] &&
                            swatchSelects[58] && swatchSelects[68] && swatchSelects[77] && !swatchSelects[9]) ||
                    (swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36] && swatchSelects[47] &&
                            swatchSelects[58] && swatchSelects[68] && swatchSelects[77] && !swatchSelects[2])) {
                int a[] = {2, 9, 17, 26, 36, 47, 58, 68, 77};
                i = pickUnSelected(a);
            } else if ((swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54] &&
                    swatchSelects[64] && swatchSelects[73] && swatchSelects[81] && !swatchSelects[88]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54] &&
                            swatchSelects[64] && swatchSelects[73] && swatchSelects[88] && !swatchSelects[81]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54] &&
                            swatchSelects[64] && swatchSelects[81] && swatchSelects[88] && !swatchSelects[73]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54] &&
                            swatchSelects[73] && swatchSelects[81] && swatchSelects[88] && !swatchSelects[64]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[64] &&
                            swatchSelects[73] && swatchSelects[81] && swatchSelects[88] && !swatchSelects[54]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[54] && swatchSelects[64] &&
                            swatchSelects[73] && swatchSelects[81] && swatchSelects[88] && !swatchSelects[43]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[43] && swatchSelects[54] && swatchSelects[64] &&
                            swatchSelects[73] && swatchSelects[81] && swatchSelects[88] && !swatchSelects[32]) ||
                    (swatchSelects[13] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54] && swatchSelects[64] &&
                            swatchSelects[73] && swatchSelects[81] && swatchSelects[88] && !swatchSelects[22]) ||
                    (swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54] && swatchSelects[64] &&
                            swatchSelects[73] && swatchSelects[81] && swatchSelects[88] && !swatchSelects[13])) {
                int a[] = {13, 22, 32, 43, 54, 64, 73, 81, 88};
                i = pickUnSelected(a);
            } else if ((swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17] &&
                    swatchSelects[18] && swatchSelects[19] && !swatchSelects[20]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17] &&
                            swatchSelects[18] && swatchSelects[20] && !swatchSelects[19]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17] &&
                            swatchSelects[19] && swatchSelects[20] && !swatchSelects[18]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[18] &&
                            swatchSelects[19] && swatchSelects[20] && !swatchSelects[17]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[17] && swatchSelects[18] &&
                            swatchSelects[19] && swatchSelects[20] && !swatchSelects[16]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18] &&
                            swatchSelects[19] && swatchSelects[20] && !swatchSelects[15]) ||
                    (swatchSelects[13] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18] &&
                            swatchSelects[19] && swatchSelects[20] && !swatchSelects[14]) ||
                    (swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18] &&
                            swatchSelects[19] && swatchSelects[20] && !swatchSelects[13])) {
                int a[] = {13, 14, 15, 16, 17, 18, 19, 20};
                i = pickUnSelected(a);
            } else if ((swatchSelects[70] && swatchSelects[71] && swatchSelects[72] && swatchSelects[73] && swatchSelects[74] &&
                    swatchSelects[75] && swatchSelects[76] && !swatchSelects[77]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[72] && swatchSelects[73] && swatchSelects[74] &&
                            swatchSelects[75] && swatchSelects[77] && !swatchSelects[76]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[72] && swatchSelects[73] && swatchSelects[74] &&
                            swatchSelects[76] && swatchSelects[77] && !swatchSelects[75]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[72] && swatchSelects[73] && swatchSelects[75] &&
                            swatchSelects[76] && swatchSelects[77] && !swatchSelects[74]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[72] && swatchSelects[74] && swatchSelects[75] &&
                            swatchSelects[76] && swatchSelects[77] && !swatchSelects[73]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[73] && swatchSelects[74] && swatchSelects[75] &&
                            swatchSelects[76] && swatchSelects[77] && !swatchSelects[72]) ||
                    (swatchSelects[70] && swatchSelects[72] && swatchSelects[73] && swatchSelects[74] && swatchSelects[75] &&
                            swatchSelects[76] && swatchSelects[77] && !swatchSelects[71]) ||
                    (swatchSelects[71] && swatchSelects[72] && swatchSelects[73] && swatchSelects[74] && swatchSelects[75] &&
                            swatchSelects[76] && swatchSelects[77] && !swatchSelects[70])) {
                int a[] = {70, 71, 72, 73, 74, 75, 76, 77};
                i = pickUnSelected(a);
            } else if ((swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32] &&
                    swatchSelects[42] && swatchSelects[52] && !swatchSelects[61]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32] &&
                            swatchSelects[42] && swatchSelects[61] && !swatchSelects[52]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32] &&
                            swatchSelects[52] && swatchSelects[61] && !swatchSelects[42]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[42] &&
                            swatchSelects[52] && swatchSelects[61] && !swatchSelects[32]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[32] && swatchSelects[42] &&
                            swatchSelects[52] && swatchSelects[61] && !swatchSelects[23]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[23] && swatchSelects[32] && swatchSelects[42] &&
                            swatchSelects[52] && swatchSelects[61] && !swatchSelects[15]) ||
                    (swatchSelects[2] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32] && swatchSelects[42] &&
                            swatchSelects[52] && swatchSelects[61] && !swatchSelects[8]) ||
                    (swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32] && swatchSelects[42] &&
                            swatchSelects[52] && swatchSelects[61] && !swatchSelects[2])) {
                int a[] = {2, 8, 15, 23, 32, 42, 52, 61};
                i = pickUnSelected(a);
            } else if ((swatchSelects[29] && swatchSelects[38] && swatchSelects[48] && swatchSelects[58] && swatchSelects[67] &&
                    swatchSelects[75] && swatchSelects[82] && !swatchSelects[88]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[48] && swatchSelects[58] && swatchSelects[67] &&
                            swatchSelects[75] && swatchSelects[88] && !swatchSelects[82]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[48] && swatchSelects[58] && swatchSelects[67] &&
                            swatchSelects[82] && swatchSelects[88] && !swatchSelects[75]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[48] && swatchSelects[58] && swatchSelects[75] &&
                            swatchSelects[82] && swatchSelects[88] && !swatchSelects[67]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[48] && swatchSelects[67] && swatchSelects[75] &&
                            swatchSelects[82] && swatchSelects[88] && !swatchSelects[58]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[58] && swatchSelects[67] && swatchSelects[75] &&
                            swatchSelects[82] && swatchSelects[88] && !swatchSelects[48]) ||
                    (swatchSelects[29] && swatchSelects[48] && swatchSelects[58] && swatchSelects[67] && swatchSelects[75] &&
                            swatchSelects[82] && swatchSelects[88] && !swatchSelects[38]) ||
                    (swatchSelects[38] && swatchSelects[48] && swatchSelects[58] && swatchSelects[67] && swatchSelects[75] &&
                            swatchSelects[82] && swatchSelects[88] && !swatchSelects[29])) {
                int a[] = {29, 38, 48, 58, 67, 75, 82, 88};
                i = pickUnSelected(a);
            } else if ((swatchSelects[3] && swatchSelects[10] && swatchSelects[18] && swatchSelects[27] && swatchSelects[37] &&
                    swatchSelects[48] && swatchSelects[59] && !swatchSelects[69]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[18] && swatchSelects[27] && swatchSelects[37] &&
                            swatchSelects[48] && swatchSelects[69] && !swatchSelects[59]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[18] && swatchSelects[27] && swatchSelects[37] &&
                            swatchSelects[59] && swatchSelects[69] && !swatchSelects[48]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[18] && swatchSelects[27] && swatchSelects[48] &&
                            swatchSelects[59] && swatchSelects[69] && !swatchSelects[37]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[18] && swatchSelects[37] && swatchSelects[48] &&
                            swatchSelects[59] && swatchSelects[69] && !swatchSelects[27]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[27] && swatchSelects[37] && swatchSelects[48] &&
                            swatchSelects[59] && swatchSelects[69] && !swatchSelects[18]) ||
                    (swatchSelects[3] && swatchSelects[18] && swatchSelects[27] && swatchSelects[37] && swatchSelects[48] &&
                            swatchSelects[59] && swatchSelects[69] && !swatchSelects[10]) ||
                    (swatchSelects[10] && swatchSelects[18] && swatchSelects[27] && swatchSelects[37] && swatchSelects[48] &&
                            swatchSelects[59] && swatchSelects[69] && !swatchSelects[3])) {
                int a[] = {3, 10, 18, 27, 37, 48, 59, 69};
                i = pickUnSelected(a);
            } else if ((swatchSelects[21] && swatchSelects[31] && swatchSelects[42] && swatchSelects[53] && swatchSelects[63] &&
                    swatchSelects[72] && swatchSelects[80] && !swatchSelects[87]) ||
                    (swatchSelects[21] && swatchSelects[31] && swatchSelects[42] && swatchSelects[53] && swatchSelects[63] &&
                            swatchSelects[72] && swatchSelects[87] && !swatchSelects[80]) ||
                    (swatchSelects[21] && swatchSelects[31] && swatchSelects[42] && swatchSelects[53] && swatchSelects[63] &&
                            swatchSelects[80] && swatchSelects[87] && !swatchSelects[72]) ||
                    (swatchSelects[21] && swatchSelects[31] && swatchSelects[42] && swatchSelects[53] && swatchSelects[72] &&
                            swatchSelects[80] && swatchSelects[87] && !swatchSelects[63]) ||
                    (swatchSelects[21] && swatchSelects[31] && swatchSelects[42] && swatchSelects[63] && swatchSelects[72] &&
                            swatchSelects[80] && swatchSelects[87] && !swatchSelects[53]) ||
                    (swatchSelects[21] && swatchSelects[31] && swatchSelects[53] && swatchSelects[63] && swatchSelects[72] &&
                            swatchSelects[80] && swatchSelects[87] && !swatchSelects[42]) ||
                    (swatchSelects[21] && swatchSelects[42] && swatchSelects[53] && swatchSelects[63] && swatchSelects[72] &&
                            swatchSelects[80] && swatchSelects[87] && !swatchSelects[31]) ||
                    (swatchSelects[31] && swatchSelects[42] && swatchSelects[53] && swatchSelects[63] && swatchSelects[72] &&
                            swatchSelects[80] && swatchSelects[87] && !swatchSelects[21])) {
                int a[] = {21, 31, 42, 53, 63, 72, 80, 87};
                i = pickUnSelected(a);
            } else if ((swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && swatchSelects[9] && swatchSelects[10] &&
                    swatchSelects[11] && !swatchSelects[12]) ||
                    (swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && swatchSelects[9] && swatchSelects[10] &&
                            swatchSelects[12] && !swatchSelects[11]) ||
                    (swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && swatchSelects[9] && swatchSelects[11] &&
                            swatchSelects[12] && !swatchSelects[10]) ||
                    (swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && swatchSelects[10] && swatchSelects[11] &&
                            swatchSelects[12] && !swatchSelects[9]) ||
                    (swatchSelects[6] && swatchSelects[7] && swatchSelects[9] && swatchSelects[10] && swatchSelects[11] &&
                            swatchSelects[12] && !swatchSelects[8]) ||
                    (swatchSelects[6] && swatchSelects[8] && swatchSelects[9] && swatchSelects[10] && swatchSelects[11] &&
                            swatchSelects[12] && !swatchSelects[7]) ||
                    (swatchSelects[7] && swatchSelects[8] && swatchSelects[9] && swatchSelects[10] && swatchSelects[11] &&
                            swatchSelects[12] && !swatchSelects[6])) {
                int a[] = {6, 7, 8, 9, 10, 11, 12};
                i = pickUnSelected(a);
            } else if ((swatchSelects[78] && swatchSelects[79] && swatchSelects[80] && swatchSelects[81] && swatchSelects[82] &&
                    swatchSelects[83] && !swatchSelects[84]) ||
                    (swatchSelects[78] && swatchSelects[79] && swatchSelects[80] && swatchSelects[81] && swatchSelects[82] &&
                            swatchSelects[84] && !swatchSelects[83]) ||
                    (swatchSelects[78] && swatchSelects[79] && swatchSelects[80] && swatchSelects[81] && swatchSelects[83] &&
                            swatchSelects[84] && !swatchSelects[82]) ||
                    (swatchSelects[78] && swatchSelects[79] && swatchSelects[80] && swatchSelects[82] && swatchSelects[83] &&
                            swatchSelects[84] && !swatchSelects[81]) ||
                    (swatchSelects[78] && swatchSelects[79] && swatchSelects[81] && swatchSelects[82] && swatchSelects[83] &&
                            swatchSelects[84] && !swatchSelects[80]) ||
                    (swatchSelects[78] && swatchSelects[80] && swatchSelects[81] && swatchSelects[82] && swatchSelects[83] &&
                            swatchSelects[84] && !swatchSelects[79]) ||
                    (swatchSelects[79] && swatchSelects[80] && swatchSelects[81] && swatchSelects[82] && swatchSelects[83] &&
                            swatchSelects[84] && !swatchSelects[78])) {
                int a[] = {78, 79, 80, 81, 82, 83, 84};
                i = pickUnSelected(a);
            } else if ((swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31] &&
                    swatchSelects[41] && !swatchSelects[51]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31] &&
                            swatchSelects[51] && !swatchSelects[41]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[41] &&
                            swatchSelects[51] && !swatchSelects[31]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[31] && swatchSelects[41] &&
                            swatchSelects[51] && !swatchSelects[22]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[22] && swatchSelects[31] && swatchSelects[41] &&
                            swatchSelects[51] && !swatchSelects[14]) ||
                    (swatchSelects[1] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31] && swatchSelects[41] &&
                            swatchSelects[51] && !swatchSelects[7]) ||
                    (swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31] && swatchSelects[41] &&
                            swatchSelects[51] && !swatchSelects[1])) {
                int a[] = {1, 7, 14, 22, 31, 41, 51};
                i = pickUnSelected(a);
            } else if ((swatchSelects[39] && swatchSelects[49] && swatchSelects[59] && swatchSelects[68] && swatchSelects[76] &&
                    swatchSelects[83] && !swatchSelects[89]) ||
                    (swatchSelects[39] && swatchSelects[49] && swatchSelects[59] && swatchSelects[68] && swatchSelects[76] &&
                            swatchSelects[89] && !swatchSelects[83]) ||
                    (swatchSelects[39] && swatchSelects[49] && swatchSelects[59] && swatchSelects[68] && swatchSelects[83] &&
                            swatchSelects[89] && !swatchSelects[76]) ||
                    (swatchSelects[39] && swatchSelects[49] && swatchSelects[59] && swatchSelects[76] && swatchSelects[83] &&
                            swatchSelects[89] && !swatchSelects[68]) ||
                    (swatchSelects[39] && swatchSelects[49] && swatchSelects[68] && swatchSelects[76] && swatchSelects[83] &&
                            swatchSelects[89] && !swatchSelects[59]) ||
                    (swatchSelects[39] && swatchSelects[59] && swatchSelects[68] && swatchSelects[76] && swatchSelects[83] &&
                            swatchSelects[89] && !swatchSelects[49]) ||
                    (swatchSelects[49] && swatchSelects[59] && swatchSelects[68] && swatchSelects[76] && swatchSelects[83] &&
                            swatchSelects[89] && !swatchSelects[39])) {
                int a[] = {39, 49, 59, 68, 76, 83, 89};
                i = pickUnSelected(a);
            } else if ((swatchSelects[4] && swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[38] &&
                    swatchSelects[49] && !swatchSelects[60]) ||
                    (swatchSelects[4] && swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[38] &&
                            swatchSelects[60] && !swatchSelects[49]) ||
                    (swatchSelects[4] && swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[49] &&
                            swatchSelects[60] && !swatchSelects[38]) ||
                    (swatchSelects[4] && swatchSelects[11] && swatchSelects[19] && swatchSelects[38] && swatchSelects[49] &&
                            swatchSelects[60] && !swatchSelects[28]) ||
                    (swatchSelects[4] && swatchSelects[11] && swatchSelects[28] && swatchSelects[38] && swatchSelects[49] &&
                            swatchSelects[60] && !swatchSelects[19]) ||
                    (swatchSelects[4] && swatchSelects[19] && swatchSelects[28] && swatchSelects[38] && swatchSelects[49] &&
                            swatchSelects[60] && !swatchSelects[11]) ||
                    (swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[38] && swatchSelects[49] &&
                            swatchSelects[60] && !swatchSelects[4])) {
                int a[] = {4, 11, 19, 28, 38, 49, 60};
                i = pickUnSelected(a);
            } else if ((swatchSelects[30] && swatchSelects[41] && swatchSelects[52] && swatchSelects[62] && swatchSelects[71] &&
                    swatchSelects[79] && !swatchSelects[86]) ||
                    (swatchSelects[30] && swatchSelects[41] && swatchSelects[52] && swatchSelects[62] && swatchSelects[71] &&
                            swatchSelects[86] && !swatchSelects[79]) ||
                    (swatchSelects[30] && swatchSelects[41] && swatchSelects[52] && swatchSelects[62] && swatchSelects[79] &&
                            swatchSelects[86] && !swatchSelects[71]) ||
                    (swatchSelects[30] && swatchSelects[41] && swatchSelects[52] && swatchSelects[71] && swatchSelects[79] &&
                            swatchSelects[86] && !swatchSelects[62]) ||
                    (swatchSelects[30] && swatchSelects[41] && swatchSelects[62] && swatchSelects[71] && swatchSelects[79] &&
                            swatchSelects[86] && !swatchSelects[52]) ||
                    (swatchSelects[30] && swatchSelects[52] && swatchSelects[62] && swatchSelects[71] && swatchSelects[79] &&
                            swatchSelects[86] && !swatchSelects[41]) ||
                    (swatchSelects[41] && swatchSelects[52] && swatchSelects[62] && swatchSelects[71] && swatchSelects[79] &&
                            swatchSelects[86] && !swatchSelects[30])) {
                int a[] = {30, 41, 52, 62, 71, 79, 86};
                i = pickUnSelected(a);
            } else if ((swatchSelects[0] && swatchSelects[1] && swatchSelects[2] && swatchSelects[3] && swatchSelects[4] &&
                    !swatchSelects[5]) ||
                    (swatchSelects[0] && swatchSelects[1] && swatchSelects[2] && swatchSelects[3] && swatchSelects[5] &&
                            !swatchSelects[4]) ||
                    (swatchSelects[0] && swatchSelects[1] && swatchSelects[2] && swatchSelects[4] && swatchSelects[5] &&
                            !swatchSelects[3]) ||
                    (swatchSelects[0] && swatchSelects[1] && swatchSelects[3] && swatchSelects[4] && swatchSelects[5] &&
                            !swatchSelects[2]) ||
                    (swatchSelects[0] && swatchSelects[2] && swatchSelects[3] && swatchSelects[4] && swatchSelects[5] &&
                            !swatchSelects[1]) ||
                    (swatchSelects[1] && swatchSelects[2] && swatchSelects[3] && swatchSelects[4] && swatchSelects[5] &&
                            !swatchSelects[0])) {
                int a[] = {0, 1, 2, 3, 4, 5};
                i = pickUnSelected(a);
            } else if ((swatchSelects[85] && swatchSelects[86] && swatchSelects[87] && swatchSelects[88] && swatchSelects[89] &&
                    !swatchSelects[90]) ||
                    (swatchSelects[85] && swatchSelects[86] && swatchSelects[87] && swatchSelects[88] && swatchSelects[90] &&
                            !swatchSelects[89]) ||
                    (swatchSelects[85] && swatchSelects[86] && swatchSelects[87] && swatchSelects[89] && swatchSelects[90] &&
                            !swatchSelects[88]) ||
                    (swatchSelects[85] && swatchSelects[86] && swatchSelects[88] && swatchSelects[89] && swatchSelects[90] &&
                            !swatchSelects[87]) ||
                    (swatchSelects[85] && swatchSelects[87] && swatchSelects[88] && swatchSelects[89] && swatchSelects[90] &&
                            !swatchSelects[86]) ||
                    (swatchSelects[86] && swatchSelects[87] && swatchSelects[88] && swatchSelects[89] && swatchSelects[90] &&
                            !swatchSelects[85])) {
                int a[] = {85, 86, 87, 88, 89, 90};
                i = pickUnSelected(a);
            } else if ((swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] &&
                    !swatchSelects[40]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[40] &&
                            !swatchSelects[30]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[30] && swatchSelects[40] &&
                            !swatchSelects[21]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[21] && swatchSelects[30] && swatchSelects[40] &&
                            !swatchSelects[13]) ||
                    (swatchSelects[0] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] && swatchSelects[40] &&
                            !swatchSelects[6]) ||
                    (swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] && swatchSelects[40] &&
                            !swatchSelects[0])) {
                int a[] = {0, 6, 13, 21, 30, 40};
                i = pickUnSelected(a);
            } else if ((swatchSelects[50] && swatchSelects[60] && swatchSelects[69] && swatchSelects[77] && swatchSelects[84] &&
                    !swatchSelects[90]) ||
                    (swatchSelects[50] && swatchSelects[60] && swatchSelects[69] && swatchSelects[77] && swatchSelects[90] &&
                            !swatchSelects[84]) ||
                    (swatchSelects[50] && swatchSelects[60] && swatchSelects[69] && swatchSelects[84] && swatchSelects[90] &&
                            !swatchSelects[77]) ||
                    (swatchSelects[50] && swatchSelects[60] && swatchSelects[77] && swatchSelects[84] && swatchSelects[90] &&
                            !swatchSelects[69]) ||
                    (swatchSelects[50] && swatchSelects[69] && swatchSelects[77] && swatchSelects[84] && swatchSelects[90] &&
                            !swatchSelects[60]) ||
                    (swatchSelects[60] && swatchSelects[69] && swatchSelects[77] && swatchSelects[84] && swatchSelects[90] &&
                            !swatchSelects[50])) {
                int a[] = {50, 60, 69, 77, 84, 90};
                i = pickUnSelected(a);
            } else if ((swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[39] &&
                    !swatchSelects[50]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[50] &&
                            !swatchSelects[39]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[39] && swatchSelects[50] &&
                            !swatchSelects[29]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[29] && swatchSelects[39] && swatchSelects[50] &&
                            !swatchSelects[20]) ||
                    (swatchSelects[5] && swatchSelects[20] && swatchSelects[29] && swatchSelects[39] && swatchSelects[50] &&
                            !swatchSelects[12]) ||
                    (swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[39] && swatchSelects[50] &&
                            !swatchSelects[5])) {
                int a[] = {5, 12, 20, 29, 39, 50};
                i = pickUnSelected(a);
            } else if ((swatchSelects[40] && swatchSelects[51] && swatchSelects[61] && swatchSelects[70] && swatchSelects[78] &&
                    !swatchSelects[85]) ||
                    (swatchSelects[40] && swatchSelects[51] && swatchSelects[61] && swatchSelects[70] && swatchSelects[85] &&
                            !swatchSelects[78]) ||
                    (swatchSelects[40] && swatchSelects[51] && swatchSelects[61] && swatchSelects[78] && swatchSelects[85] &&
                            !swatchSelects[70]) ||
                    (swatchSelects[40] && swatchSelects[51] && swatchSelects[70] && swatchSelects[78] && swatchSelects[85] &&
                            !swatchSelects[61]) ||
                    (swatchSelects[40] && swatchSelects[61] && swatchSelects[70] && swatchSelects[78] && swatchSelects[85] &&
                            !swatchSelects[51]) ||
                    (swatchSelects[51] && swatchSelects[61] && swatchSelects[70] && swatchSelects[78] && swatchSelects[85] &&
                            !swatchSelects[40])) {
                int a[] = {40, 51, 61, 70, 78, 85};
                i = pickUnSelected(a);
            }
        }

        if (i >= 0) {
            return i;
        } else {
            return -1;
        }
    }

    private int preventStep1() {
        int i = -1;
        if (boardRadius == 3) {
            int a1[] = {15, 16, 17, 18, 19, 20, 21};
            ArrayList b1 = pickUnSelectedArray1(a1);
            int a2[] = {0, 5, 11, 18, 25, 31, 36};
            ArrayList b2 = pickUnSelectedArray1(a2);
            int a3[] = {3, 7, 12, 18, 24, 29, 33};
            ArrayList b3 = pickUnSelectedArray1(a3);

            ArrayList c1 = new ArrayList();
            c1.addAll(b1);
            c1.addAll(b2);
            c1.addAll(b3);

            int a4[] = {9, 10, 11, 12, 13, 14};
            ArrayList b4 = pickUnSelectedArray1(a4);
            int a5[] = {22, 23, 24, 25, 26, 27};
            ArrayList b5 = pickUnSelectedArray1(a5);
            int a6[] = {2, 6, 11, 17, 23, 28};
            ArrayList b6 = pickUnSelectedArray1(a6);
            int a7[] = {8, 13, 19, 25, 30, 34};
            ArrayList b7 = pickUnSelectedArray1(a7);
            int a8[] = {1, 6, 12, 19, 26, 32};
            ArrayList b8 = pickUnSelectedArray1(a8);
            int a9[] = {4, 10, 17, 24, 30, 35};
            ArrayList b9 = pickUnSelectedArray1(a9);

            int a10[] = {4, 5, 6, 7, 8};
            ArrayList b10 = pickUnSelectedArray1(a10);
            int a11[] = {28, 29, 30, 31, 32};
            ArrayList b11 = pickUnSelectedArray1(a11);
            int a12[] = {1, 5, 10, 16, 22};
            ArrayList b12 = pickUnSelectedArray1(a12);
            int a13[] = {14, 20, 26, 31, 35};
            ArrayList b13 = pickUnSelectedArray1(a13);
            int a14[] = {2, 7, 13, 20, 27};
            ArrayList b14 = pickUnSelectedArray1(a14);
            int a15[] = {9, 16, 23, 29, 34};
            ArrayList b15 = pickUnSelectedArray1(a15);

            int a16[] = {0, 1, 2, 3};
            ArrayList b16 = pickUnSelectedArray1(a16);
            int a17[] = {33, 34, 35, 36};
            ArrayList b17 = pickUnSelectedArray1(a17);
            int a18[] = {0, 4, 9, 15};
            ArrayList b18 = pickUnSelectedArray1(a18);
            int a19[] = {21, 27, 32, 36};
            ArrayList b19 = pickUnSelectedArray1(a19);
            int a20[] = {3, 8, 14, 21};
            ArrayList b20 = pickUnSelectedArray1(a20);
            int a21[] = {15, 22, 28, 33};
            ArrayList b21 = pickUnSelectedArray1(a21);

            if (!swatchSelects[18]) {
                i = 18;
            } else if (b1.size() > 2 && b2.size() > 2 && b3.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(c1));
            } else if (b1.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b1));
            } else if (b2.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b2));
            } else if (b3.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b3));
            } else if (b4.size() > 2) {
                i = pickUnSelectedRandom1(convertListToArray(b4), new Integer[]{11, 12});
            } else if (b5.size() > 2) {
                i = pickUnSelectedRandom1(convertListToArray(b5), new Integer[]{24, 25});
            } else if (b6.size() > 2) {
                i = pickUnSelectedRandom1(convertListToArray(b6), new Integer[]{11, 17});
            } else if (b7.size() > 2) {
                i = pickUnSelectedRandom1(convertListToArray(b7), new Integer[]{19, 25});
            } else if (b8.size() > 2) {
                i = pickUnSelectedRandom1(convertListToArray(b8), new Integer[]{12, 19});
            } else if (b9.size() > 2) {
                i = pickUnSelectedRandom1(convertListToArray(b9), new Integer[]{17, 24});
            } else if (b10.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b10));
            } else if (b11.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b11));
            } else if (b12.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b12));
            } else if (b13.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b13));
            } else if (b14.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b14));
            } else if (b15.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b15));
            } else if (b16.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b16));
            } else if (b17.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b17));
            } else if (b18.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b18));
            } else if (b19.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b19));
            } else if (b20.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b20));
            } else if (b21.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b21));
            }
        } else if (boardRadius == 4) {
            int a1[] = {26, 27, 28, 29, 30, 31, 32, 33, 34};
            ArrayList b1 = pickUnSelectedArray1(a1);
            int a2[] = {4, 9, 15, 22, 30, 38, 45, 51, 56};
            ArrayList b2 = pickUnSelectedArray1(a2);
            int a3[] = {0, 6, 13, 21, 30, 39, 47, 54, 60};
            ArrayList b3 = pickUnSelectedArray1(a3);

            ArrayList c1 = new ArrayList();
            c1.addAll(b1);
            c1.addAll(b2);
            c1.addAll(b3);

            int a4[] = {18, 19, 20, 21, 22, 23, 24, 25};
            ArrayList b4 = pickUnSelectedArray1(a4);
            int a5[] = {35, 36, 37, 38, 39, 40, 41, 42};
            ArrayList b5 = pickUnSelectedArray1(a5);
            int a6[] = {3, 8, 14, 21, 29, 37, 44, 50};
            ArrayList b6 = pickUnSelectedArray1(a6);
            int a7[] = {10, 16, 23, 31, 39, 46, 52, 57};
            ArrayList b7 = pickUnSelectedArray1(a7);
            int a8[] = {1, 7, 14, 22, 31, 40, 48, 55};
            ArrayList b8 = pickUnSelectedArray1(a8);
            int a9[] = {5, 12, 20, 29, 38, 46, 53, 59};
            ArrayList b9 = pickUnSelectedArray1(a9);

            int a10[] = {11, 12, 13, 14, 15, 16, 17};
            ArrayList b10 = pickUnSelectedArray1(a10);
            int a11[] = {43, 44, 45, 46, 47, 48, 49};
            ArrayList b11 = pickUnSelectedArray1(a11);
            int a12[] = {2, 7, 13, 20, 28, 36, 43};
            ArrayList b12 = pickUnSelectedArray1(a12);
            int a13[] = {17, 24, 32, 40, 47, 53, 58};
            ArrayList b13 = pickUnSelectedArray1(a13);
            int a14[] = {2, 8, 15, 23, 32, 41, 49};
            ArrayList b14 = pickUnSelectedArray1(a14);
            int a15[] = {11, 19, 28, 37, 45, 52, 58};
            ArrayList b15 = pickUnSelectedArray1(a15);

            int a16[] = {5, 6, 7, 8, 9, 10};
            ArrayList b16 = pickUnSelectedArray1(a16);
            int a17[] = {50, 51, 52, 53, 54, 55};
            ArrayList b17 = pickUnSelectedArray1(a17);
            int a18[] = {1, 6, 12, 19, 27, 35};
            ArrayList b18 = pickUnSelectedArray1(a18);
            int a19[] = {25, 33, 41, 48, 54, 59};
            ArrayList b19 = pickUnSelectedArray1(a19);
            int a20[] = {3, 9, 16, 24, 33, 42};
            ArrayList b20 = pickUnSelectedArray1(a20);
            int a21[] = {18, 27, 36, 44, 51, 57};
            ArrayList b21 = pickUnSelectedArray1(a21);

            int a22[] = {0, 1, 2, 3, 4};
            ArrayList b22 = pickUnSelectedArray1(a22);
            int a23[] = {56, 57, 58, 59, 60};
            ArrayList b23 = pickUnSelectedArray1(a23);
            int a24[] = {0, 5, 11, 18, 26};
            ArrayList b24 = pickUnSelectedArray1(a24);
            int a25[] = {34, 42, 49, 55, 60};
            ArrayList b25 = pickUnSelectedArray1(a25);
            int a26[] = {4, 10, 17, 25, 34};
            ArrayList b26 = pickUnSelectedArray1(a26);
            int a27[] = {26, 35, 43, 50, 56};
            ArrayList b27 = pickUnSelectedArray1(a27);

            if (!swatchSelects[30]) {
                i = 30;
            } else if (b1.size() > 2 && b2.size() > 2 && b3.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(c1));
            } else if (b1.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b1));
            } else if (b2.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b2));
            } else if (b3.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b3));
            } else if (b4.size() > 2) {
                i = pickUnSelectedRandom1(convertListToArray(b4), new Integer[]{21, 22});
            } else if (b5.size() > 2) {
                i = pickUnSelectedRandom1(convertListToArray(b5), new Integer[]{38, 39});
            } else if (b6.size() > 2) {
                i = pickUnSelectedRandom1(convertListToArray(b6), new Integer[]{21, 29});
            } else if (b7.size() > 2) {
                i = pickUnSelectedRandom1(convertListToArray(b7), new Integer[]{31, 39});
            } else if (b8.size() > 2) {
                i = pickUnSelectedRandom1(convertListToArray(b8), new Integer[]{22, 31});
            } else if (b9.size() > 2) {
                i = pickUnSelectedRandom1(convertListToArray(b9), new Integer[]{29, 38});
            } else if (b10.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b10));
            } else if (b11.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b11));
            } else if (b12.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b12));
            } else if (b13.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b13));
            } else if (b14.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b14));
            } else if (b15.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b15));
            } else if (b16.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b16));
            } else if (b17.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b17));
            } else if (b18.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b18));
            } else if (b19.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b19));
            } else if (b20.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b20));
            } else if (b21.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b21));
            } else if (b22.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b22));
            } else if (b23.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b23));
            } else if (b24.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b24));
            } else if (b25.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b25));
            } else if (b26.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b26));
            } else if (b27.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b27));
            }

        } else if (boardRadius == 5) {
            int a1[] = {40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50};
            ArrayList b1 = pickUnSelectedArray1(a1);
            int a2[] = {5, 11, 18, 26, 35, 45, 55, 64, 72, 79, 85};
            ArrayList b2 = pickUnSelectedArray1(a2);
            int a3[] = {0, 7, 15, 24, 34, 45, 56, 66, 75, 83, 90};
            ArrayList b3 = pickUnSelectedArray1(a3);

            ArrayList c1 = new ArrayList();
            c1.addAll(b1);
            c1.addAll(b2);
            c1.addAll(b3);

            int a4[] = {30, 31, 32, 33, 34, 35, 36, 37, 38, 39};
            ArrayList b4 = pickUnSelectedArray1(a4);
            int a5[] = {51, 52, 53, 54, 55, 56, 57, 58, 59, 60};
            ArrayList b5 = pickUnSelectedArray1(a5);
            int a6[] = {4, 10, 17, 25, 34, 44, 54, 63, 71, 78};
            ArrayList b6 = pickUnSelectedArray1(a6);
            int a7[] = {12, 19, 27, 36, 46, 56, 65, 73, 80, 86};
            ArrayList b7 = pickUnSelectedArray1(a7);
            int a8[] = {1, 8, 16, 25, 35, 46, 57, 67, 76, 84};
            ArrayList b8 = pickUnSelectedArray1(a8);
            int a9[] = {6, 14, 23, 33, 44, 55, 65, 74, 82, 89};
            ArrayList b9 = pickUnSelectedArray1(a9);

            int a10[] = {21, 22, 23, 24, 25, 26, 27, 28, 29};
            ArrayList b10 = pickUnSelectedArray1(a10);
            int a11[] = {61, 62, 63, 64, 65, 66, 67, 68, 69};
            ArrayList b11 = pickUnSelectedArray1(a11);
            int a12[] = {3, 9, 16, 24, 33, 43, 53, 62, 70};
            ArrayList b12 = pickUnSelectedArray1(a12);
            int a13[] = {20, 28, 37, 47, 57, 66, 74, 81, 87};
            ArrayList b13 = pickUnSelectedArray1(a13);
            int a14[] = {2, 9, 17, 26, 36, 47, 58, 68, 77};
            ArrayList b14 = pickUnSelectedArray1(a14);
            int a15[] = {13, 22, 32, 43, 54, 64, 73, 81, 88};
            ArrayList b15 = pickUnSelectedArray1(a15);

            int a16[] = {13, 14, 15, 16, 17, 18, 19, 20};
            ArrayList b16 = pickUnSelectedArray1(a16);
            int a17[] = {70, 71, 72, 73, 74, 75, 76, 77};
            ArrayList b17 = pickUnSelectedArray1(a17);
            int a18[] = {2, 8, 15, 23, 32, 42, 52, 61};
            ArrayList b18 = pickUnSelectedArray1(a18);
            int a19[] = {29, 38, 48, 58, 67, 75, 82, 88};
            ArrayList b19 = pickUnSelectedArray1(a19);
            int a20[] = {3, 10, 18, 27, 37, 48, 59, 69};
            ArrayList b20 = pickUnSelectedArray1(a20);
            int a21[] = {21, 31, 42, 53, 63, 72, 80, 87};
            ArrayList b21 = pickUnSelectedArray1(a21);

            int a22[] = {6, 7, 8, 9, 10, 11, 12};
            ArrayList b22 = pickUnSelectedArray1(a22);
            int a23[] = {78, 79, 80, 81, 82, 83, 84};
            ArrayList b23 = pickUnSelectedArray1(a23);
            int a24[] = {1, 7, 14, 22, 31, 41, 51};
            ArrayList b24 = pickUnSelectedArray1(a24);
            int a25[] = {39, 49, 59, 68, 76, 83, 89};
            ArrayList b25 = pickUnSelectedArray1(a25);
            int a26[] = {4, 11, 19, 28, 38, 49, 60};
            ArrayList b26 = pickUnSelectedArray1(a26);
            int a27[] = {30, 41, 52, 62, 71, 79, 86};
            ArrayList b27 = pickUnSelectedArray1(a27);

            int a28[] = {0, 1, 2, 3, 4, 5};
            ArrayList b28 = pickUnSelectedArray1(a28);
            int a29[] = {85, 86, 87, 88, 89, 90};
            ArrayList b29 = pickUnSelectedArray1(a29);
            int a30[] = {0, 6, 13, 21, 30, 40};
            ArrayList b30 = pickUnSelectedArray1(a30);
            int a31[] = {50, 60, 69, 77, 84, 90};
            ArrayList b31 = pickUnSelectedArray1(a31);
            int a32[] = {5, 12, 20, 29, 39, 50};
            ArrayList b32 = pickUnSelectedArray1(a32);
            int a33[] = {40, 51, 61, 70, 78, 85};
            ArrayList b33 = pickUnSelectedArray1(a33);

            if (!swatchSelects[45]) {
                i = 45;
            } else if (b1.size() > 2 && b2.size() > 2 && b3.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(c1));
            } else if (b1.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b1));
            } else if (b2.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b2));
            } else if (b3.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b3));
            } else if (b4.size() > 2) {
                i = pickUnSelectedRandom1(convertListToArray(b4), new Integer[]{34, 35});
            } else if (b5.size() > 2) {
                i = pickUnSelectedRandom1(convertListToArray(b5), new Integer[]{55, 56});
            } else if (b6.size() > 2) {
                i = pickUnSelectedRandom1(convertListToArray(b6), new Integer[]{34, 44});
            } else if (b7.size() > 2) {
                i = pickUnSelectedRandom1(convertListToArray(b7), new Integer[]{46, 56});
            } else if (b8.size() > 2) {
                i = pickUnSelectedRandom1(convertListToArray(b8), new Integer[]{35, 46});
            } else if (b9.size() > 2) {
                i = pickUnSelectedRandom1(convertListToArray(b9), new Integer[]{44, 55});
            } else if (b10.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b10));
            } else if (b11.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b11));
            } else if (b12.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b12));
            } else if (b13.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b13));
            } else if (b14.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b14));
            } else if (b15.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b15));
            } else if (b16.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b16));
            } else if (b17.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b17));
            } else if (b18.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b18));
            } else if (b19.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b19));
            } else if (b20.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b20));
            } else if (b21.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b21));
            } else if (b22.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b22));
            } else if (b23.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b23));
            } else if (b24.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b24));
            } else if (b25.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b25));
            } else if (b26.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b26));
            } else if (b27.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b27));
            } else if (b28.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b28));
            } else if (b29.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b29));
            } else if (b30.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b30));
            } else if (b31.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b31));
            } else if (b32.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b32));
            } else if (b33.size() > 2) {
                i = pickUnSelectedRandom(convertListToArray(b33));
            }
        }

        if (i >= 0) {
            return i;
        } else {
            return -1;
        }
    }

    private int preventStep2_1() {
        int i = -1;
        if (boardRadius == 3) {
            if ((swatchSelects[0] && swatchSelects[1] && !swatchSelects[2] && !swatchSelects[3]) ||
                    (swatchSelects[0] && swatchSelects[2] && !swatchSelects[1] && !swatchSelects[3]) ||
                    (swatchSelects[0] && swatchSelects[3] && !swatchSelects[1] && !swatchSelects[2]) ||
                    (swatchSelects[1] && swatchSelects[2] && !swatchSelects[0] && !swatchSelects[3]) ||
                    (swatchSelects[1] && swatchSelects[3] && !swatchSelects[0] && !swatchSelects[2]) ||
                    (swatchSelects[2] && swatchSelects[3] && !swatchSelects[0] && !swatchSelects[1])) {
                int a[] = {0, 1, 2, 3};
                i = pickUnSelected(a);
            } else if ((swatchSelects[33] && swatchSelects[34] && !swatchSelects[35] && !swatchSelects[36]) ||
                    (swatchSelects[33] && swatchSelects[35] && !swatchSelects[34] && !swatchSelects[36]) ||
                    (swatchSelects[33] && swatchSelects[36] && !swatchSelects[34] && !swatchSelects[35]) ||
                    (swatchSelects[34] && swatchSelects[35] && !swatchSelects[33] && !swatchSelects[36]) ||
                    (swatchSelects[34] && swatchSelects[36] && !swatchSelects[33] && !swatchSelects[35]) ||
                    (swatchSelects[35] && swatchSelects[36] && !swatchSelects[33] && !swatchSelects[34])) {
                int a[] = {33, 34, 35, 36};
                i = pickUnSelected(a);
            } else if ((swatchSelects[0] && swatchSelects[4] && !swatchSelects[9] && !swatchSelects[15]) ||
                    (swatchSelects[0] && swatchSelects[9] && !swatchSelects[4] && !swatchSelects[15]) ||
                    (swatchSelects[0] && swatchSelects[15] && !swatchSelects[4] && !swatchSelects[9]) ||
                    (swatchSelects[4] && swatchSelects[9] && !swatchSelects[0] && !swatchSelects[15]) ||
                    (swatchSelects[4] && swatchSelects[15] && !swatchSelects[0] && !swatchSelects[9]) ||
                    (swatchSelects[9] && swatchSelects[15] && !swatchSelects[0] && !swatchSelects[4])) {
                int a[] = {0, 4, 9, 15};
                i = pickUnSelected(a);
            } else if ((swatchSelects[21] && swatchSelects[27] && !swatchSelects[32] && !swatchSelects[36]) ||
                    (swatchSelects[21] && swatchSelects[32] && !swatchSelects[27] && !swatchSelects[36]) ||
                    (swatchSelects[21] && swatchSelects[36] && !swatchSelects[27] && !swatchSelects[32]) ||
                    (swatchSelects[27] && swatchSelects[32] && !swatchSelects[21] && !swatchSelects[36]) ||
                    (swatchSelects[27] && swatchSelects[36] && !swatchSelects[21] && !swatchSelects[32]) ||
                    (swatchSelects[32] && swatchSelects[36] && !swatchSelects[21] && !swatchSelects[27])) {
                int a[] = {21, 27, 32, 36};
                i = pickUnSelected(a);
            } else if ((swatchSelects[3] && swatchSelects[8] && !swatchSelects[14] && !swatchSelects[21]) ||
                    (swatchSelects[3] && swatchSelects[14] && !swatchSelects[8] && !swatchSelects[21]) ||
                    (swatchSelects[3] && swatchSelects[21] && !swatchSelects[8] && !swatchSelects[14]) ||
                    (swatchSelects[8] && swatchSelects[14] && !swatchSelects[3] && !swatchSelects[21]) ||
                    (swatchSelects[8] && swatchSelects[21] && !swatchSelects[3] && !swatchSelects[14]) ||
                    (swatchSelects[14] && swatchSelects[21] && !swatchSelects[3] && !swatchSelects[8])) {
                int a[] = {3, 8, 14, 21};
                i = pickUnSelected(a);
            } else if ((swatchSelects[15] && swatchSelects[22] && !swatchSelects[28] && !swatchSelects[33]) ||
                    (swatchSelects[15] && swatchSelects[28] && !swatchSelects[22] && !swatchSelects[33]) ||
                    (swatchSelects[15] && swatchSelects[33] && !swatchSelects[22] && !swatchSelects[28]) ||
                    (swatchSelects[22] && swatchSelects[28] && !swatchSelects[15] && !swatchSelects[33]) ||
                    (swatchSelects[22] && swatchSelects[33] && !swatchSelects[15] && !swatchSelects[28]) ||
                    (swatchSelects[28] && swatchSelects[33] && !swatchSelects[15] && !swatchSelects[22])) {
                int a[] = {15, 22, 28, 33};
                i = pickUnSelected(a);
            } else if ((swatchSelects[4] && swatchSelects[5] && swatchSelects[6] && !swatchSelects[7] && !swatchSelects[8]) ||
                    (swatchSelects[4] && swatchSelects[5] && swatchSelects[7] && !swatchSelects[6] && !swatchSelects[8]) ||
                    (swatchSelects[4] && swatchSelects[6] && swatchSelects[7] && !swatchSelects[5] && !swatchSelects[8]) ||
                    (swatchSelects[5] && swatchSelects[6] && swatchSelects[7] && !swatchSelects[4] && !swatchSelects[8]) ||
                    (swatchSelects[4] && swatchSelects[5] && swatchSelects[8] && !swatchSelects[6] && !swatchSelects[7]) ||
                    (swatchSelects[4] && swatchSelects[6] && swatchSelects[8] && !swatchSelects[5] && !swatchSelects[7]) ||
                    (swatchSelects[5] && swatchSelects[6] && swatchSelects[8] && !swatchSelects[4] && !swatchSelects[7]) ||
                    (swatchSelects[4] && swatchSelects[7] && swatchSelects[8] && !swatchSelects[5] && !swatchSelects[6]) ||
                    (swatchSelects[5] && swatchSelects[7] && swatchSelects[8] && !swatchSelects[4] && !swatchSelects[6]) ||
                    (swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && !swatchSelects[4] && !swatchSelects[5])) {
                int a[] = {4, 5, 6, 7, 8};
                i = pickUnSelected(a);
            } else if ((swatchSelects[28] && swatchSelects[29] && swatchSelects[30] && !swatchSelects[31] && !swatchSelects[32]) ||
                    (swatchSelects[28] && swatchSelects[29] && swatchSelects[31] && !swatchSelects[30] && !swatchSelects[32]) ||
                    (swatchSelects[28] && swatchSelects[30] && swatchSelects[31] && !swatchSelects[29] && !swatchSelects[32]) ||
                    (swatchSelects[29] && swatchSelects[30] && swatchSelects[31] && !swatchSelects[28] && !swatchSelects[32]) ||
                    (swatchSelects[28] && swatchSelects[29] && swatchSelects[32] && !swatchSelects[30] && !swatchSelects[31]) ||
                    (swatchSelects[28] && swatchSelects[30] && swatchSelects[32] && !swatchSelects[29] && !swatchSelects[31]) ||
                    (swatchSelects[29] && swatchSelects[30] && swatchSelects[32] && !swatchSelects[28] && !swatchSelects[31]) ||
                    (swatchSelects[28] && swatchSelects[31] && swatchSelects[32] && !swatchSelects[29] && !swatchSelects[30]) ||
                    (swatchSelects[29] && swatchSelects[31] && swatchSelects[32] && !swatchSelects[28] && !swatchSelects[30]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && !swatchSelects[28] && !swatchSelects[29])) {
                int a[] = {28, 29, 30, 31, 32};
                i = pickUnSelected(a);
            } else if ((swatchSelects[1] && swatchSelects[5] && swatchSelects[10] && !swatchSelects[16] && !swatchSelects[22]) ||
                    (swatchSelects[1] && swatchSelects[5] && swatchSelects[16] && !swatchSelects[10] && !swatchSelects[22]) ||
                    (swatchSelects[1] && swatchSelects[10] && swatchSelects[16] && !swatchSelects[5] && !swatchSelects[22]) ||
                    (swatchSelects[5] && swatchSelects[10] && swatchSelects[16] && !swatchSelects[1] && !swatchSelects[22]) ||
                    (swatchSelects[1] && swatchSelects[10] && swatchSelects[22] && !swatchSelects[10] && !swatchSelects[16]) ||
                    (swatchSelects[1] && swatchSelects[10] && swatchSelects[22] && !swatchSelects[5] && !swatchSelects[16]) ||
                    (swatchSelects[5] && swatchSelects[10] && swatchSelects[22] && !swatchSelects[1] && !swatchSelects[16]) ||
                    (swatchSelects[1] && swatchSelects[16] && swatchSelects[22] && !swatchSelects[5] && !swatchSelects[10]) ||
                    (swatchSelects[5] && swatchSelects[16] && swatchSelects[22] && !swatchSelects[1] && !swatchSelects[10]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[22] && !swatchSelects[1] && !swatchSelects[5])) {
                int a[] = {1, 5, 10, 16, 22};
                i = pickUnSelected(a);
            } else if ((swatchSelects[14] && swatchSelects[20] && swatchSelects[26] && !swatchSelects[31] && !swatchSelects[35]) ||
                    (swatchSelects[14] && swatchSelects[20] && swatchSelects[31] && !swatchSelects[26] && !swatchSelects[35]) ||
                    (swatchSelects[14] && swatchSelects[26] && swatchSelects[31] && !swatchSelects[20] && !swatchSelects[35]) ||
                    (swatchSelects[20] && swatchSelects[26] && swatchSelects[31] && !swatchSelects[14] && !swatchSelects[35]) ||
                    (swatchSelects[14] && swatchSelects[20] && swatchSelects[35] && !swatchSelects[26] && !swatchSelects[31]) ||
                    (swatchSelects[14] && swatchSelects[26] && swatchSelects[35] && !swatchSelects[20] && !swatchSelects[31]) ||
                    (swatchSelects[20] && swatchSelects[26] && swatchSelects[35] && !swatchSelects[14] && !swatchSelects[31]) ||
                    (swatchSelects[14] && swatchSelects[31] && swatchSelects[35] && !swatchSelects[20] && !swatchSelects[26]) ||
                    (swatchSelects[20] && swatchSelects[31] && swatchSelects[35] && !swatchSelects[14] && !swatchSelects[26]) ||
                    (swatchSelects[26] && swatchSelects[31] && swatchSelects[35] && !swatchSelects[14] && !swatchSelects[20])) {
                int a[] = {14, 20, 26, 31, 35};
                i = pickUnSelected(a);
            } else if ((swatchSelects[2] && swatchSelects[7] && swatchSelects[13] && !swatchSelects[20] && !swatchSelects[27]) ||
                    (swatchSelects[2] && swatchSelects[7] && swatchSelects[20] && !swatchSelects[13] && !swatchSelects[27]) ||
                    (swatchSelects[2] && swatchSelects[13] && swatchSelects[20] && !swatchSelects[7] && !swatchSelects[27]) ||
                    (swatchSelects[7] && swatchSelects[13] && swatchSelects[20] && !swatchSelects[2] && !swatchSelects[27]) ||
                    (swatchSelects[2] && swatchSelects[7] && swatchSelects[27] && !swatchSelects[13] && !swatchSelects[20]) ||
                    (swatchSelects[2] && swatchSelects[13] && swatchSelects[27] && !swatchSelects[7] && !swatchSelects[20]) ||
                    (swatchSelects[7] && swatchSelects[13] && swatchSelects[27] && !swatchSelects[2] && !swatchSelects[20]) ||
                    (swatchSelects[2] && swatchSelects[20] && swatchSelects[27] && !swatchSelects[7] && !swatchSelects[13]) ||
                    (swatchSelects[7] && swatchSelects[20] && swatchSelects[27] && !swatchSelects[2] && !swatchSelects[13]) ||
                    (swatchSelects[13] && swatchSelects[20] && swatchSelects[27] && !swatchSelects[2] && !swatchSelects[7])) {
                int a[] = {2, 7, 13, 20, 27};
                i = pickUnSelected(a);
            } else if ((swatchSelects[9] && swatchSelects[16] && swatchSelects[23] && !swatchSelects[29] && !swatchSelects[34]) ||
                    (swatchSelects[9] && swatchSelects[16] && swatchSelects[29] && !swatchSelects[23] && !swatchSelects[34]) ||
                    (swatchSelects[9] && swatchSelects[23] && swatchSelects[29] && !swatchSelects[16] && !swatchSelects[34]) ||
                    (swatchSelects[16] && swatchSelects[23] && swatchSelects[29] && !swatchSelects[9] && !swatchSelects[34]) ||
                    (swatchSelects[9] && swatchSelects[16] && swatchSelects[34] && !swatchSelects[23] && !swatchSelects[29]) ||
                    (swatchSelects[9] && swatchSelects[23] && swatchSelects[34] && !swatchSelects[16] && !swatchSelects[29]) ||
                    (swatchSelects[16] && swatchSelects[23] && swatchSelects[34] && !swatchSelects[9] && !swatchSelects[29]) ||
                    (swatchSelects[9] && swatchSelects[29] && swatchSelects[34] && !swatchSelects[16] && !swatchSelects[23]) ||
                    (swatchSelects[16] && swatchSelects[29] && swatchSelects[34] && !swatchSelects[9] && !swatchSelects[23]) ||
                    (swatchSelects[23] && swatchSelects[29] && swatchSelects[34] && !swatchSelects[9] && !swatchSelects[16])) {
                int a[] = {9, 16, 23, 29, 34};
                i = pickUnSelected(a);
            } else if ((swatchSelects[9] && swatchSelects[10] && swatchSelects[11] && swatchSelects[12]
                    && !swatchSelects[13] && !swatchSelects[14]) ||
                    (swatchSelects[9] && swatchSelects[10] && swatchSelects[11] && swatchSelects[13]
                            && !swatchSelects[12] && !swatchSelects[14]) ||
                    (swatchSelects[9] && swatchSelects[10] && swatchSelects[12] && swatchSelects[13]
                            && !swatchSelects[11] && !swatchSelects[14]) ||
                    (swatchSelects[9] && swatchSelects[11] && swatchSelects[12] && swatchSelects[13]
                            && !swatchSelects[10] && !swatchSelects[14]) ||
                    (swatchSelects[10] && swatchSelects[11] && swatchSelects[12] && swatchSelects[13]
                            && !swatchSelects[9] && !swatchSelects[14]) ||
                    (swatchSelects[9] && swatchSelects[10] && swatchSelects[11] && swatchSelects[14]
                            && !swatchSelects[12] && !swatchSelects[13]) ||
                    (swatchSelects[9] && swatchSelects[10] && swatchSelects[12] && swatchSelects[14]
                            && !swatchSelects[11] && !swatchSelects[13]) ||
                    (swatchSelects[9] && swatchSelects[11] && swatchSelects[12] && swatchSelects[14]
                            && !swatchSelects[10] && !swatchSelects[13]) ||
                    (swatchSelects[10] && swatchSelects[11] && swatchSelects[12] && swatchSelects[14]
                            && !swatchSelects[9] && !swatchSelects[13]) ||
                    (swatchSelects[9] && swatchSelects[10] && swatchSelects[13] && swatchSelects[14]
                            && !swatchSelects[11] && !swatchSelects[12]) ||
                    (swatchSelects[9] && swatchSelects[11] && swatchSelects[13] && swatchSelects[14]
                            && !swatchSelects[10] && !swatchSelects[12]) ||
                    (swatchSelects[10] && swatchSelects[11] && swatchSelects[13] && swatchSelects[14]
                            && !swatchSelects[9] && !swatchSelects[12]) ||
                    (swatchSelects[9] && swatchSelects[12] && swatchSelects[13] && swatchSelects[14]
                            && !swatchSelects[10] && !swatchSelects[11]) ||
                    (swatchSelects[10] && swatchSelects[12] && swatchSelects[13] && swatchSelects[14]
                            && !swatchSelects[9] && !swatchSelects[11]) ||
                    (swatchSelects[11] && swatchSelects[12] && swatchSelects[13] && swatchSelects[14]
                            && !swatchSelects[9] && !swatchSelects[10])) {
                int a[] = {9, 10, 11, 12, 13, 14};
                i = pickUnSelected(a);
            } else if ((swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25]
                    && !swatchSelects[26] && !swatchSelects[27]) ||
                    (swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[26]
                            && !swatchSelects[25] && !swatchSelects[27]) ||
                    (swatchSelects[22] && swatchSelects[23] && swatchSelects[25] && swatchSelects[26]
                            && !swatchSelects[24] && !swatchSelects[27]) ||
                    (swatchSelects[22] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26]
                            && !swatchSelects[23] && !swatchSelects[27]) ||
                    (swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26]
                            && !swatchSelects[22] && !swatchSelects[27]) ||
                    (swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[27]
                            && !swatchSelects[25] && !swatchSelects[26]) ||
                    (swatchSelects[22] && swatchSelects[23] && swatchSelects[25] && swatchSelects[27]
                            && !swatchSelects[24] && !swatchSelects[26]) ||
                    (swatchSelects[22] && swatchSelects[24] && swatchSelects[25] && swatchSelects[27]
                            && !swatchSelects[23] && !swatchSelects[26]) ||
                    (swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && swatchSelects[27]
                            && !swatchSelects[22] && !swatchSelects[26]) ||
                    (swatchSelects[22] && swatchSelects[23] && swatchSelects[26] && swatchSelects[27]
                            && !swatchSelects[24] && !swatchSelects[25]) ||
                    (swatchSelects[22] && swatchSelects[24] && swatchSelects[26] && swatchSelects[27]
                            && !swatchSelects[23] && !swatchSelects[25]) ||
                    (swatchSelects[23] && swatchSelects[24] && swatchSelects[26] && swatchSelects[27]
                            && !swatchSelects[22] && !swatchSelects[25]) ||
                    (swatchSelects[22] && swatchSelects[25] && swatchSelects[26] && swatchSelects[27]
                            && !swatchSelects[23] && !swatchSelects[24]) ||
                    (swatchSelects[23] && swatchSelects[25] && swatchSelects[26] && swatchSelects[27]
                            && !swatchSelects[22] && !swatchSelects[24]) ||
                    (swatchSelects[24] && swatchSelects[25] && swatchSelects[26] && swatchSelects[27]
                            && !swatchSelects[22] && !swatchSelects[23])) {
                int a[] = {22, 23, 24, 25, 26, 27};
                i = pickUnSelected(a);
            } else if ((swatchSelects[2] && swatchSelects[6] && swatchSelects[11] && swatchSelects[17]
                    && !swatchSelects[23] && !swatchSelects[28]) ||
                    (swatchSelects[2] && swatchSelects[6] && swatchSelects[11] && swatchSelects[23]
                            && !swatchSelects[17] && !swatchSelects[28]) ||
                    (swatchSelects[2] && swatchSelects[6] && swatchSelects[17] && swatchSelects[23]
                            && !swatchSelects[11] && !swatchSelects[28]) ||
                    (swatchSelects[2] && swatchSelects[11] && swatchSelects[17] && swatchSelects[23]
                            && !swatchSelects[6] && !swatchSelects[28]) ||
                    (swatchSelects[6] && swatchSelects[11] && swatchSelects[17] && swatchSelects[23]
                            && !swatchSelects[2] && !swatchSelects[28]) ||
                    (swatchSelects[2] && swatchSelects[6] && swatchSelects[11] && swatchSelects[28]
                            && !swatchSelects[17] && !swatchSelects[23]) ||
                    (swatchSelects[2] && swatchSelects[6] && swatchSelects[17] && swatchSelects[28]
                            && !swatchSelects[11] && !swatchSelects[23]) ||
                    (swatchSelects[2] && swatchSelects[11] && swatchSelects[17] && swatchSelects[28]
                            && !swatchSelects[6] && !swatchSelects[23]) ||
                    (swatchSelects[6] && swatchSelects[11] && swatchSelects[17] && swatchSelects[28]
                            && !swatchSelects[2] && !swatchSelects[23]) ||
                    (swatchSelects[2] && swatchSelects[6] && swatchSelects[23] && swatchSelects[28]
                            && !swatchSelects[11] && !swatchSelects[17]) ||
                    (swatchSelects[2] && swatchSelects[11] && swatchSelects[23] && swatchSelects[28]
                            && !swatchSelects[6] && !swatchSelects[17]) ||
                    (swatchSelects[6] && swatchSelects[11] && swatchSelects[23] && swatchSelects[28]
                            && !swatchSelects[2] && !swatchSelects[17]) ||
                    (swatchSelects[2] && swatchSelects[17] && swatchSelects[23] && swatchSelects[28]
                            && !swatchSelects[6] && !swatchSelects[11]) ||
                    (swatchSelects[6] && swatchSelects[17] && swatchSelects[23] && swatchSelects[28]
                            && !swatchSelects[2] && !swatchSelects[11]) ||
                    (swatchSelects[11] && swatchSelects[17] && swatchSelects[23] && swatchSelects[28]
                            && !swatchSelects[2] && !swatchSelects[6])) {
                int a[] = {2, 6, 11, 17, 23, 28};
                i = pickUnSelected(a);
            } else if ((swatchSelects[8] && swatchSelects[13] && swatchSelects[19] && swatchSelects[25]
                    && !swatchSelects[30] && !swatchSelects[34]) ||
                    (swatchSelects[8] && swatchSelects[13] && swatchSelects[19] && swatchSelects[30]
                            && !swatchSelects[25] && !swatchSelects[34]) ||
                    (swatchSelects[8] && swatchSelects[13] && swatchSelects[25] && swatchSelects[30]
                            && !swatchSelects[19] && !swatchSelects[34]) ||
                    (swatchSelects[8] && swatchSelects[19] && swatchSelects[25] && swatchSelects[30]
                            && !swatchSelects[13] && !swatchSelects[34]) ||
                    (swatchSelects[13] && swatchSelects[19] && swatchSelects[25] && swatchSelects[30]
                            && !swatchSelects[8] && !swatchSelects[34]) ||
                    (swatchSelects[8] && swatchSelects[13] && swatchSelects[19] && swatchSelects[34]
                            && !swatchSelects[25] && !swatchSelects[30]) ||
                    (swatchSelects[8] && swatchSelects[13] && swatchSelects[25] && swatchSelects[34]
                            && !swatchSelects[19] && !swatchSelects[30]) ||
                    (swatchSelects[8] && swatchSelects[19] && swatchSelects[25] && swatchSelects[34]
                            && !swatchSelects[13] && !swatchSelects[30]) ||
                    (swatchSelects[13] && swatchSelects[19] && swatchSelects[25] && swatchSelects[34]
                            && !swatchSelects[8] && !swatchSelects[30]) ||
                    (swatchSelects[8] && swatchSelects[13] && swatchSelects[30] && swatchSelects[34]
                            && !swatchSelects[19] && !swatchSelects[25]) ||
                    (swatchSelects[8] && swatchSelects[19] && swatchSelects[30] && swatchSelects[34]
                            && !swatchSelects[13] && !swatchSelects[25]) ||
                    (swatchSelects[13] && swatchSelects[19] && swatchSelects[30] && swatchSelects[34]
                            && !swatchSelects[8] && !swatchSelects[25]) ||
                    (swatchSelects[8] && swatchSelects[25] && swatchSelects[30] && swatchSelects[34]
                            && !swatchSelects[13] && !swatchSelects[19]) ||
                    (swatchSelects[13] && swatchSelects[25] && swatchSelects[30] && swatchSelects[34]
                            && !swatchSelects[8] && !swatchSelects[19]) ||
                    (swatchSelects[19] && swatchSelects[25] && swatchSelects[30] && swatchSelects[34]
                            && !swatchSelects[8] && !swatchSelects[13])) {
                int a[] = {8, 13, 19, 25, 30, 34};
                i = pickUnSelected(a);
            } else if ((swatchSelects[1] && swatchSelects[6] && swatchSelects[12] && swatchSelects[19]
                    && !swatchSelects[26] && !swatchSelects[32]) ||
                    (swatchSelects[1] && swatchSelects[6] && swatchSelects[12] && swatchSelects[26]
                            && !swatchSelects[19] && !swatchSelects[32]) ||
                    (swatchSelects[1] && swatchSelects[6] && swatchSelects[19] && swatchSelects[26]
                            && !swatchSelects[12] && !swatchSelects[32]) ||
                    (swatchSelects[1] && swatchSelects[12] && swatchSelects[19] && swatchSelects[26]
                            && !swatchSelects[6] && !swatchSelects[32]) ||
                    (swatchSelects[6] && swatchSelects[12] && swatchSelects[19] && swatchSelects[26]
                            && !swatchSelects[1] && !swatchSelects[32]) ||
                    (swatchSelects[1] && swatchSelects[6] && swatchSelects[12] && swatchSelects[32]
                            && !swatchSelects[19] && !swatchSelects[26]) ||
                    (swatchSelects[1] && swatchSelects[6] && swatchSelects[19] && swatchSelects[32]
                            && !swatchSelects[12] && !swatchSelects[26]) ||
                    (swatchSelects[1] && swatchSelects[12] && swatchSelects[19] && swatchSelects[32]
                            && !swatchSelects[6] && !swatchSelects[26]) ||
                    (swatchSelects[6] && swatchSelects[12] && swatchSelects[19] && swatchSelects[32]
                            && !swatchSelects[1] && !swatchSelects[26]) ||
                    (swatchSelects[1] && swatchSelects[6] && swatchSelects[26] && swatchSelects[32]
                            && !swatchSelects[12] && !swatchSelects[19]) ||
                    (swatchSelects[1] && swatchSelects[12] && swatchSelects[26] && swatchSelects[32]
                            && !swatchSelects[6] && !swatchSelects[19]) ||
                    (swatchSelects[6] && swatchSelects[12] && swatchSelects[26] && swatchSelects[32]
                            && !swatchSelects[1] && !swatchSelects[19]) ||
                    (swatchSelects[1] && swatchSelects[19] && swatchSelects[26] && swatchSelects[32]
                            && !swatchSelects[6] && !swatchSelects[12]) ||
                    (swatchSelects[6] && swatchSelects[19] && swatchSelects[26] && swatchSelects[32]
                            && !swatchSelects[1] && !swatchSelects[12]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[26] && swatchSelects[32]
                            && !swatchSelects[1] && !swatchSelects[6])) {
                int a[] = {1, 6, 12, 19, 26, 32};
                i = pickUnSelected(a);
            } else if ((swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[24]
                    && !swatchSelects[30] && !swatchSelects[35]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[30]
                            && !swatchSelects[24] && !swatchSelects[35]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[24] && swatchSelects[30]
                            && !swatchSelects[17] && !swatchSelects[35]) ||
                    (swatchSelects[4] && swatchSelects[17] && swatchSelects[24] && swatchSelects[30]
                            && !swatchSelects[10] && !swatchSelects[35]) ||
                    (swatchSelects[10] && swatchSelects[17] && swatchSelects[24] && swatchSelects[30]
                            && !swatchSelects[4] && !swatchSelects[35]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[35]
                            && !swatchSelects[24] && !swatchSelects[30]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[24] && swatchSelects[35]
                            && !swatchSelects[17] && !swatchSelects[30]) ||
                    (swatchSelects[4] && swatchSelects[17] && swatchSelects[24] && swatchSelects[35]
                            && !swatchSelects[10] && !swatchSelects[30]) ||
                    (swatchSelects[10] && swatchSelects[17] && swatchSelects[24] && swatchSelects[35]
                            && !swatchSelects[4] && !swatchSelects[30]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[30] && swatchSelects[35]
                            && !swatchSelects[17] && !swatchSelects[24]) ||
                    (swatchSelects[4] && swatchSelects[17] && swatchSelects[30] && swatchSelects[35]
                            && !swatchSelects[10] && !swatchSelects[24]) ||
                    (swatchSelects[10] && swatchSelects[17] && swatchSelects[30] && swatchSelects[35]
                            && !swatchSelects[4] && !swatchSelects[24]) ||
                    (swatchSelects[4] && swatchSelects[24] && swatchSelects[30] && swatchSelects[35]
                            && !swatchSelects[10] && !swatchSelects[17]) ||
                    (swatchSelects[10] && swatchSelects[24] && swatchSelects[30] && swatchSelects[35]
                            && !swatchSelects[4] && !swatchSelects[17]) ||
                    (swatchSelects[17] && swatchSelects[24] && swatchSelects[30] && swatchSelects[35]
                            && !swatchSelects[4] && !swatchSelects[10])) {
                int a[] = {4, 10, 17, 24, 30, 35};
                i = pickUnSelected(a);
            } else if ((swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18]
                    && swatchSelects[19] && !swatchSelects[20] && !swatchSelects[21]) ||
                    (swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18]
                            && swatchSelects[20] && !swatchSelects[19] && !swatchSelects[21]) ||
                    (swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[18] && !swatchSelects[21]) ||
                    (swatchSelects[15] && swatchSelects[16] && swatchSelects[18] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[17] && !swatchSelects[21]) ||
                    (swatchSelects[15] && swatchSelects[17] && swatchSelects[18] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[16] && !swatchSelects[21]) ||
                    (swatchSelects[16] && swatchSelects[17] && swatchSelects[18] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[15] && !swatchSelects[21]) ||
                    (swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18]
                            && swatchSelects[21] && !swatchSelects[19] && !swatchSelects[20]) ||
                    (swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[19]
                            && swatchSelects[21] && !swatchSelects[18] && !swatchSelects[20]) ||
                    (swatchSelects[15] && swatchSelects[16] && swatchSelects[18] && swatchSelects[19]
                            && swatchSelects[21] && !swatchSelects[17] && !swatchSelects[20]) ||
                    (swatchSelects[15] && swatchSelects[17] && swatchSelects[18] && swatchSelects[19]
                            && swatchSelects[21] && !swatchSelects[16] && !swatchSelects[20]) ||
                    (swatchSelects[16] && swatchSelects[17] && swatchSelects[18] && swatchSelects[19]
                            && swatchSelects[21] && !swatchSelects[15] && !swatchSelects[20]) ||
                    (swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[20]
                            && swatchSelects[21] && !swatchSelects[18] && !swatchSelects[19]) ||
                    (swatchSelects[15] && swatchSelects[16] && swatchSelects[18] && swatchSelects[20]
                            && swatchSelects[21] && !swatchSelects[17] && !swatchSelects[19]) ||
                    (swatchSelects[15] && swatchSelects[17] && swatchSelects[18] && swatchSelects[20]
                            && swatchSelects[21] && !swatchSelects[16] && !swatchSelects[19]) ||
                    (swatchSelects[16] && swatchSelects[17] && swatchSelects[18] && swatchSelects[20]
                            && swatchSelects[21] && !swatchSelects[15] && !swatchSelects[19]) ||
                    (swatchSelects[15] && swatchSelects[16] && swatchSelects[19] && swatchSelects[20]
                            && swatchSelects[21] && !swatchSelects[17] && !swatchSelects[18]) ||
                    (swatchSelects[15] && swatchSelects[17] && swatchSelects[19] && swatchSelects[20]
                            && swatchSelects[21] && !swatchSelects[16] && !swatchSelects[18]) ||
                    (swatchSelects[16] && swatchSelects[17] && swatchSelects[19] && swatchSelects[20]
                            && swatchSelects[21] && !swatchSelects[15] && !swatchSelects[18]) ||
                    (swatchSelects[15] && swatchSelects[18] && swatchSelects[19] && swatchSelects[20]
                            && swatchSelects[21] && !swatchSelects[16] && !swatchSelects[17]) ||
                    (swatchSelects[16] && swatchSelects[18] && swatchSelects[19] && swatchSelects[20]
                            && swatchSelects[21] && !swatchSelects[15] && !swatchSelects[17]) ||
                    (swatchSelects[17] && swatchSelects[18] && swatchSelects[19] && swatchSelects[20]
                            && swatchSelects[21] && !swatchSelects[15] && !swatchSelects[16])) {
                int a[] = {15, 16, 17, 18, 19, 20, 21};
                i = pickUnSelected(a);
            } else if ((swatchSelects[4] && swatchSelects[5] && swatchSelects[11] && swatchSelects[18]
                    && swatchSelects[25] && !swatchSelects[31] && !swatchSelects[36]) ||
                    (swatchSelects[4] && swatchSelects[5] && swatchSelects[11] && swatchSelects[18]
                            && swatchSelects[31] && !swatchSelects[25] && !swatchSelects[36]) ||
                    (swatchSelects[4] && swatchSelects[5] && swatchSelects[11] && swatchSelects[25]
                            && swatchSelects[31] && !swatchSelects[18] && !swatchSelects[36]) ||
                    (swatchSelects[4] && swatchSelects[5] && swatchSelects[18] && swatchSelects[25]
                            && swatchSelects[31] && !swatchSelects[11] && !swatchSelects[36]) ||
                    (swatchSelects[4] && swatchSelects[11] && swatchSelects[18] && swatchSelects[25]
                            && swatchSelects[31] && !swatchSelects[5] && !swatchSelects[36]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[25]
                            && swatchSelects[31] && !swatchSelects[4] && !swatchSelects[36]) ||
                    (swatchSelects[4] && swatchSelects[5] && swatchSelects[11] && swatchSelects[18]
                            && swatchSelects[36] && !swatchSelects[25] && !swatchSelects[31]) ||
                    (swatchSelects[4] && swatchSelects[5] && swatchSelects[11] && swatchSelects[25]
                            && swatchSelects[36] && !swatchSelects[18] && !swatchSelects[31]) ||
                    (swatchSelects[4] && swatchSelects[5] && swatchSelects[18] && swatchSelects[25]
                            && swatchSelects[36] && !swatchSelects[11] && !swatchSelects[31]) ||
                    (swatchSelects[4] && swatchSelects[11] && swatchSelects[18] && swatchSelects[25]
                            && swatchSelects[36] && !swatchSelects[5] && !swatchSelects[31]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[25]
                            && swatchSelects[36] && !swatchSelects[4] && !swatchSelects[31]) ||
                    (swatchSelects[4] && swatchSelects[5] && swatchSelects[11] && swatchSelects[31]
                            && swatchSelects[36] && !swatchSelects[18] && !swatchSelects[25]) ||
                    (swatchSelects[4] && swatchSelects[5] && swatchSelects[18] && swatchSelects[31]
                            && swatchSelects[36] && !swatchSelects[11] && !swatchSelects[25]) ||
                    (swatchSelects[4] && swatchSelects[11] && swatchSelects[18] && swatchSelects[31]
                            && swatchSelects[36] && !swatchSelects[5] && !swatchSelects[25]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[31]
                            && swatchSelects[36] && !swatchSelects[4] && !swatchSelects[25]) ||
                    (swatchSelects[4] && swatchSelects[5] && swatchSelects[25] && swatchSelects[31]
                            && swatchSelects[36] && !swatchSelects[11] && !swatchSelects[18]) ||
                    (swatchSelects[4] && swatchSelects[11] && swatchSelects[25] && swatchSelects[31]
                            && swatchSelects[36] && !swatchSelects[5] && !swatchSelects[18]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[25] && swatchSelects[31]
                            && swatchSelects[36] && !swatchSelects[4] && !swatchSelects[18]) ||
                    (swatchSelects[4] && swatchSelects[18] && swatchSelects[25] && swatchSelects[31]
                            && swatchSelects[36] && !swatchSelects[5] && !swatchSelects[11]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[25] && swatchSelects[31]
                            && swatchSelects[36] && !swatchSelects[4] && !swatchSelects[11]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[25] && swatchSelects[31]
                            && swatchSelects[36] && !swatchSelects[4] && !swatchSelects[5])) {
                int a[] = {0, 5, 11, 18, 25, 31, 36};
                i = pickUnSelected(a);
            } else if ((swatchSelects[3] && swatchSelects[7] && swatchSelects[12] && swatchSelects[18]
                    && swatchSelects[24] && !swatchSelects[29] && !swatchSelects[33]) ||
                    (swatchSelects[3] && swatchSelects[7] && swatchSelects[12] && swatchSelects[18]
                            && swatchSelects[29] && !swatchSelects[24] && !swatchSelects[33]) ||
                    (swatchSelects[3] && swatchSelects[7] && swatchSelects[12] && swatchSelects[24]
                            && swatchSelects[29] && !swatchSelects[18] && !swatchSelects[33]) ||
                    (swatchSelects[3] && swatchSelects[7] && swatchSelects[18] && swatchSelects[24]
                            && swatchSelects[29] && !swatchSelects[12] && !swatchSelects[33]) ||
                    (swatchSelects[3] && swatchSelects[12] && swatchSelects[18] && swatchSelects[24]
                            && swatchSelects[29] && !swatchSelects[7] && !swatchSelects[33]) ||
                    (swatchSelects[7] && swatchSelects[12] && swatchSelects[18] && swatchSelects[24]
                            && swatchSelects[29] && !swatchSelects[3] && !swatchSelects[33]) ||
                    (swatchSelects[3] && swatchSelects[7] && swatchSelects[12] && swatchSelects[18]
                            && swatchSelects[33] && !swatchSelects[24] && !swatchSelects[29]) ||
                    (swatchSelects[3] && swatchSelects[7] && swatchSelects[12] && swatchSelects[24]
                            && swatchSelects[33] && !swatchSelects[18] && !swatchSelects[29]) ||
                    (swatchSelects[3] && swatchSelects[7] && swatchSelects[18] && swatchSelects[24]
                            && swatchSelects[33] && !swatchSelects[12] && !swatchSelects[29]) ||
                    (swatchSelects[3] && swatchSelects[12] && swatchSelects[18] && swatchSelects[24]
                            && swatchSelects[33] && !swatchSelects[7] && !swatchSelects[29]) ||
                    (swatchSelects[7] && swatchSelects[12] && swatchSelects[18] && swatchSelects[24]
                            && swatchSelects[33] && !swatchSelects[3] && !swatchSelects[29]) ||
                    (swatchSelects[3] && swatchSelects[7] && swatchSelects[12] && swatchSelects[29]
                            && swatchSelects[33] && !swatchSelects[18] && !swatchSelects[24]) ||
                    (swatchSelects[3] && swatchSelects[7] && swatchSelects[18] && swatchSelects[29]
                            && swatchSelects[33] && !swatchSelects[12] && !swatchSelects[24]) ||
                    (swatchSelects[3] && swatchSelects[12] && swatchSelects[18] && swatchSelects[29]
                            && swatchSelects[33] && !swatchSelects[7] && !swatchSelects[24]) ||
                    (swatchSelects[7] && swatchSelects[12] && swatchSelects[18] && swatchSelects[29]
                            && swatchSelects[33] && !swatchSelects[3] && !swatchSelects[24]) ||
                    (swatchSelects[3] && swatchSelects[7] && swatchSelects[24] && swatchSelects[29]
                            && swatchSelects[33] && !swatchSelects[12] && !swatchSelects[18]) ||
                    (swatchSelects[3] && swatchSelects[12] && swatchSelects[24] && swatchSelects[29]
                            && swatchSelects[33] && !swatchSelects[7] && !swatchSelects[18]) ||
                    (swatchSelects[7] && swatchSelects[12] && swatchSelects[24] && swatchSelects[29]
                            && swatchSelects[33] && !swatchSelects[3] && !swatchSelects[18]) ||
                    (swatchSelects[3] && swatchSelects[18] && swatchSelects[24] && swatchSelects[29]
                            && swatchSelects[33] && !swatchSelects[7] && !swatchSelects[12]) ||
                    (swatchSelects[7] && swatchSelects[18] && swatchSelects[24] && swatchSelects[29]
                            && swatchSelects[33] && !swatchSelects[3] && !swatchSelects[12]) ||
                    (swatchSelects[12] && swatchSelects[18] && swatchSelects[24] && swatchSelects[29]
                            && swatchSelects[33] && !swatchSelects[3] && !swatchSelects[7])) {
                int a[] = {3, 7, 12, 18, 24, 29, 33};
                i = pickUnSelected(a);
            }
        }

        if (i >= 0) {
            return i;
        } else {
            return -1;
        }
    }

    private int preventStep2_2() {
        int i = -1;
        if (boardRadius == 4) {
            if ((swatchSelects[0] && swatchSelects[1] && swatchSelects[2] && !swatchSelects[3] && !swatchSelects[4]) ||
                    (swatchSelects[0] && swatchSelects[1] && swatchSelects[3] && !swatchSelects[2] && !swatchSelects[4]) ||
                    (swatchSelects[0] && swatchSelects[2] && swatchSelects[3] && !swatchSelects[1] && !swatchSelects[4]) ||
                    (swatchSelects[1] && swatchSelects[2] && swatchSelects[3] && !swatchSelects[0] && !swatchSelects[4]) ||
                    (swatchSelects[0] && swatchSelects[1] && swatchSelects[4] && !swatchSelects[2] && !swatchSelects[3]) ||
                    (swatchSelects[0] && swatchSelects[2] && swatchSelects[4] && !swatchSelects[1] && !swatchSelects[3]) ||
                    (swatchSelects[1] && swatchSelects[2] && swatchSelects[4] && !swatchSelects[0] && !swatchSelects[3]) ||
                    (swatchSelects[0] && swatchSelects[3] && swatchSelects[4] && !swatchSelects[1] && !swatchSelects[2]) ||
                    (swatchSelects[1] && swatchSelects[3] && swatchSelects[4] && !swatchSelects[0] && !swatchSelects[2]) ||
                    (swatchSelects[2] && swatchSelects[3] && swatchSelects[4] && !swatchSelects[0] && !swatchSelects[1])) {
                int a[] = {0, 1, 2, 3, 4};
                i = pickUnSelected(a);
            } else if ((swatchSelects[56] && swatchSelects[57] && swatchSelects[58] && !swatchSelects[59] && !swatchSelects[60]) ||
                    (swatchSelects[56] && swatchSelects[57] && swatchSelects[59] && !swatchSelects[58] && !swatchSelects[60]) ||
                    (swatchSelects[56] && swatchSelects[58] && swatchSelects[59] && !swatchSelects[57] && !swatchSelects[60]) ||
                    (swatchSelects[57] && swatchSelects[58] && swatchSelects[59] && !swatchSelects[56] && !swatchSelects[60]) ||
                    (swatchSelects[56] && swatchSelects[57] && swatchSelects[60] && !swatchSelects[58] && !swatchSelects[59]) ||
                    (swatchSelects[56] && swatchSelects[58] && swatchSelects[60] && !swatchSelects[57] && !swatchSelects[59]) ||
                    (swatchSelects[57] && swatchSelects[58] && swatchSelects[60] && !swatchSelects[56] && !swatchSelects[59]) ||
                    (swatchSelects[56] && swatchSelects[59] && swatchSelects[60] && !swatchSelects[57] && !swatchSelects[58]) ||
                    (swatchSelects[57] && swatchSelects[59] && swatchSelects[60] && !swatchSelects[56] && !swatchSelects[58]) ||
                    (swatchSelects[58] && swatchSelects[59] && swatchSelects[60] && !swatchSelects[56] && !swatchSelects[57])) {
                int a[] = {56, 57, 58, 59, 60};
                i = pickUnSelected(a);
            } else if ((swatchSelects[0] && swatchSelects[5] && swatchSelects[11] && !swatchSelects[18] && !swatchSelects[26]) ||
                    (swatchSelects[0] && swatchSelects[5] && swatchSelects[18] && !swatchSelects[11] && !swatchSelects[26]) ||
                    (swatchSelects[0] && swatchSelects[11] && swatchSelects[18] && !swatchSelects[5] && !swatchSelects[26]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && !swatchSelects[0] && !swatchSelects[26]) ||
                    (swatchSelects[0] && swatchSelects[5] && swatchSelects[26] && !swatchSelects[11] && !swatchSelects[18]) ||
                    (swatchSelects[0] && swatchSelects[11] && swatchSelects[26] && !swatchSelects[5] && !swatchSelects[18]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[26] && !swatchSelects[0] && !swatchSelects[18]) ||
                    (swatchSelects[0] && swatchSelects[18] && swatchSelects[26] && !swatchSelects[5] && !swatchSelects[11]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[26] && !swatchSelects[0] && !swatchSelects[11]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && !swatchSelects[0] && !swatchSelects[5])) {
                int a[] = {0, 5, 11, 18, 26};
                i = pickUnSelected(a);
            } else if ((swatchSelects[34] && swatchSelects[42] && swatchSelects[49] && !swatchSelects[55] && !swatchSelects[60]) ||
                    (swatchSelects[34] && swatchSelects[42] && swatchSelects[55] && !swatchSelects[49] && !swatchSelects[60]) ||
                    (swatchSelects[34] && swatchSelects[49] && swatchSelects[55] && !swatchSelects[42] && !swatchSelects[60]) ||
                    (swatchSelects[42] && swatchSelects[49] && swatchSelects[55] && !swatchSelects[34] && !swatchSelects[60]) ||
                    (swatchSelects[34] && swatchSelects[42] && swatchSelects[60] && !swatchSelects[49] && !swatchSelects[55]) ||
                    (swatchSelects[34] && swatchSelects[49] && swatchSelects[60] && !swatchSelects[42] && !swatchSelects[55]) ||
                    (swatchSelects[42] && swatchSelects[49] && swatchSelects[60] && !swatchSelects[34] && !swatchSelects[55]) ||
                    (swatchSelects[34] && swatchSelects[55] && swatchSelects[60] && !swatchSelects[42] && !swatchSelects[49]) ||
                    (swatchSelects[42] && swatchSelects[55] && swatchSelects[60] && !swatchSelects[34] && !swatchSelects[49]) ||
                    (swatchSelects[49] && swatchSelects[55] && swatchSelects[60] && !swatchSelects[34] && !swatchSelects[42])) {
                int a[] = {34, 42, 49, 55, 60};
                i = pickUnSelected(a);
            } else if ((swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && !swatchSelects[25] && !swatchSelects[34]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[25] && !swatchSelects[17] && !swatchSelects[34]) ||
                    (swatchSelects[4] && swatchSelects[17] && swatchSelects[25] && !swatchSelects[10] && !swatchSelects[34]) ||
                    (swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && !swatchSelects[4] && !swatchSelects[34]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[34] && !swatchSelects[17] && !swatchSelects[25]) ||
                    (swatchSelects[4] && swatchSelects[17] && swatchSelects[34] && !swatchSelects[10] && !swatchSelects[25]) ||
                    (swatchSelects[10] && swatchSelects[17] && swatchSelects[34] && !swatchSelects[4] && !swatchSelects[25]) ||
                    (swatchSelects[4] && swatchSelects[25] && swatchSelects[34] && !swatchSelects[10] && !swatchSelects[17]) ||
                    (swatchSelects[10] && swatchSelects[25] && swatchSelects[34] && !swatchSelects[4] && !swatchSelects[17]) ||
                    (swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && !swatchSelects[4] && !swatchSelects[10])) {
                int a[] = {4, 10, 17, 25, 34};
                i = pickUnSelected(a);
            } else if ((swatchSelects[26] && swatchSelects[35] && swatchSelects[43] && !swatchSelects[50] && !swatchSelects[56]) ||
                    (swatchSelects[26] && swatchSelects[35] && swatchSelects[50] && !swatchSelects[43] && !swatchSelects[56]) ||
                    (swatchSelects[26] && swatchSelects[43] && swatchSelects[50] && !swatchSelects[35] && !swatchSelects[56]) ||
                    (swatchSelects[35] && swatchSelects[43] && swatchSelects[50] && !swatchSelects[26] && !swatchSelects[56]) ||
                    (swatchSelects[26] && swatchSelects[35] && swatchSelects[56] && !swatchSelects[43] && !swatchSelects[50]) ||
                    (swatchSelects[26] && swatchSelects[43] && swatchSelects[56] && !swatchSelects[35] && !swatchSelects[50]) ||
                    (swatchSelects[35] && swatchSelects[43] && swatchSelects[56] && !swatchSelects[26] && !swatchSelects[50]) ||
                    (swatchSelects[26] && swatchSelects[50] && swatchSelects[56] && !swatchSelects[35] && !swatchSelects[43]) ||
                    (swatchSelects[35] && swatchSelects[50] && swatchSelects[56] && !swatchSelects[26] && !swatchSelects[43]) ||
                    (swatchSelects[43] && swatchSelects[50] && swatchSelects[56] && !swatchSelects[26] && !swatchSelects[35])) {
                int a[] = {26, 35, 43, 50, 56};
                i = pickUnSelected(a);
            } else if ((swatchSelects[5] && swatchSelects[6] && swatchSelects[7] && swatchSelects[8]
                    && !swatchSelects[9] && !swatchSelects[10]) ||
                    (swatchSelects[5] && swatchSelects[6] && swatchSelects[7] && swatchSelects[9]
                            && !swatchSelects[8] && !swatchSelects[10]) ||
                    (swatchSelects[5] && swatchSelects[6] && swatchSelects[8] && swatchSelects[9]
                            && !swatchSelects[7] && !swatchSelects[10]) ||
                    (swatchSelects[5] && swatchSelects[7] && swatchSelects[8] && swatchSelects[9]
                            && !swatchSelects[6] && !swatchSelects[10]) ||
                    (swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && swatchSelects[9]
                            && !swatchSelects[5] && !swatchSelects[10]) ||
                    (swatchSelects[5] && swatchSelects[6] && swatchSelects[7] && swatchSelects[10]
                            && !swatchSelects[8] && !swatchSelects[9]) ||
                    (swatchSelects[5] && swatchSelects[6] && swatchSelects[8] && swatchSelects[10]
                            && !swatchSelects[7] && !swatchSelects[9]) ||
                    (swatchSelects[5] && swatchSelects[7] && swatchSelects[8] && swatchSelects[10]
                            && !swatchSelects[6] && !swatchSelects[9]) ||
                    (swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && swatchSelects[10]
                            && !swatchSelects[5] && !swatchSelects[9]) ||
                    (swatchSelects[5] && swatchSelects[6] && swatchSelects[9] && swatchSelects[10]
                            && !swatchSelects[7] && !swatchSelects[8]) ||
                    (swatchSelects[5] && swatchSelects[7] && swatchSelects[9] && swatchSelects[10]
                            && !swatchSelects[6] && !swatchSelects[8]) ||
                    (swatchSelects[6] && swatchSelects[7] && swatchSelects[9] && swatchSelects[10]
                            && !swatchSelects[5] && !swatchSelects[8]) ||
                    (swatchSelects[5] && swatchSelects[8] && swatchSelects[9] && swatchSelects[10]
                            && !swatchSelects[6] && !swatchSelects[7]) ||
                    (swatchSelects[6] && swatchSelects[8] && swatchSelects[9] && swatchSelects[10]
                            && !swatchSelects[5] && !swatchSelects[7]) ||
                    (swatchSelects[7] && swatchSelects[8] && swatchSelects[9] && swatchSelects[10]
                            && !swatchSelects[5] && !swatchSelects[6])) {
                int a[] = {5, 6, 7, 8, 9, 10};
                i = pickUnSelected(a);
            } else if ((swatchSelects[50] && swatchSelects[51] && swatchSelects[52] && swatchSelects[53]
                    && !swatchSelects[54] && !swatchSelects[55]) ||
                    (swatchSelects[50] && swatchSelects[51] && swatchSelects[52] && swatchSelects[54]
                            && !swatchSelects[53] && !swatchSelects[55]) ||
                    (swatchSelects[50] && swatchSelects[51] && swatchSelects[53] && swatchSelects[54]
                            && !swatchSelects[52] && !swatchSelects[55]) ||
                    (swatchSelects[50] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54]
                            && !swatchSelects[51] && !swatchSelects[55]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54]
                            && !swatchSelects[50] && !swatchSelects[55]) ||
                    (swatchSelects[50] && swatchSelects[51] && swatchSelects[52] && swatchSelects[55]
                            && !swatchSelects[53] && !swatchSelects[54]) ||
                    (swatchSelects[50] && swatchSelects[51] && swatchSelects[53] && swatchSelects[55]
                            && !swatchSelects[52] && !swatchSelects[54]) ||
                    (swatchSelects[50] && swatchSelects[52] && swatchSelects[53] && swatchSelects[55]
                            && !swatchSelects[51] && !swatchSelects[54]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[55]
                            && !swatchSelects[50] && !swatchSelects[54]) ||
                    (swatchSelects[50] && swatchSelects[51] && swatchSelects[54] && swatchSelects[55]
                            && !swatchSelects[52] && !swatchSelects[53]) ||
                    (swatchSelects[50] && swatchSelects[52] && swatchSelects[54] && swatchSelects[55]
                            && !swatchSelects[51] && !swatchSelects[53]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[54] && swatchSelects[55]
                            && !swatchSelects[50] && !swatchSelects[53]) ||
                    (swatchSelects[50] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55]
                            && !swatchSelects[51] && !swatchSelects[52]) ||
                    (swatchSelects[51] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55]
                            && !swatchSelects[50] && !swatchSelects[52]) ||
                    (swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55]
                            && !swatchSelects[50] && !swatchSelects[51])) {
                int a[] = {50, 51, 52, 53, 54, 55};
                i = pickUnSelected(a);
            } else if ((swatchSelects[1] && swatchSelects[6] && swatchSelects[12] && swatchSelects[19]
                    && !swatchSelects[27] && !swatchSelects[35]) ||
                    (swatchSelects[1] && swatchSelects[6] && swatchSelects[12] && swatchSelects[27]
                            && !swatchSelects[19] && !swatchSelects[35]) ||
                    (swatchSelects[1] && swatchSelects[6] && swatchSelects[19] && swatchSelects[27]
                            && !swatchSelects[12] && !swatchSelects[35]) ||
                    (swatchSelects[1] && swatchSelects[12] && swatchSelects[19] && swatchSelects[27]
                            && !swatchSelects[6] && !swatchSelects[35]) ||
                    (swatchSelects[6] && swatchSelects[12] && swatchSelects[19] && swatchSelects[27]
                            && !swatchSelects[1] && !swatchSelects[35]) ||
                    (swatchSelects[1] && swatchSelects[6] && swatchSelects[12] && swatchSelects[35]
                            && !swatchSelects[19] && !swatchSelects[27]) ||
                    (swatchSelects[1] && swatchSelects[6] && swatchSelects[19] && swatchSelects[35]
                            && !swatchSelects[12] && !swatchSelects[27]) ||
                    (swatchSelects[1] && swatchSelects[12] && swatchSelects[19] && swatchSelects[35]
                            && !swatchSelects[6] && !swatchSelects[27]) ||
                    (swatchSelects[6] && swatchSelects[12] && swatchSelects[19] && swatchSelects[35]
                            && !swatchSelects[1] && !swatchSelects[27]) ||
                    (swatchSelects[1] && swatchSelects[6] && swatchSelects[27] && swatchSelects[35]
                            && !swatchSelects[12] && !swatchSelects[19]) ||
                    (swatchSelects[1] && swatchSelects[12] && swatchSelects[27] && swatchSelects[35]
                            && !swatchSelects[6] && !swatchSelects[19]) ||
                    (swatchSelects[6] && swatchSelects[12] && swatchSelects[27] && swatchSelects[35]
                            && !swatchSelects[1] && !swatchSelects[19]) ||
                    (swatchSelects[1] && swatchSelects[19] && swatchSelects[27] && swatchSelects[35]
                            && !swatchSelects[6] && !swatchSelects[12]) ||
                    (swatchSelects[6] && swatchSelects[19] && swatchSelects[27] && swatchSelects[35]
                            && !swatchSelects[1] && !swatchSelects[12]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[35]
                            && !swatchSelects[1] && !swatchSelects[6])) {
                int a[] = {1, 6, 12, 19, 27, 35};
                i = pickUnSelected(a);
            } else if ((swatchSelects[25] && swatchSelects[33] && swatchSelects[41] && swatchSelects[48]
                    && !swatchSelects[54] && !swatchSelects[59]) ||
                    (swatchSelects[25] && swatchSelects[33] && swatchSelects[41] && swatchSelects[54]
                            && !swatchSelects[48] && !swatchSelects[59]) ||
                    (swatchSelects[25] && swatchSelects[33] && swatchSelects[48] && swatchSelects[54]
                            && !swatchSelects[41] && !swatchSelects[59]) ||
                    (swatchSelects[25] && swatchSelects[41] && swatchSelects[48] && swatchSelects[54]
                            && !swatchSelects[33] && !swatchSelects[59]) ||
                    (swatchSelects[33] && swatchSelects[41] && swatchSelects[48] && swatchSelects[54]
                            && !swatchSelects[25] && !swatchSelects[59]) ||
                    (swatchSelects[25] && swatchSelects[33] && swatchSelects[41] && swatchSelects[59]
                            && !swatchSelects[48] && !swatchSelects[54]) ||
                    (swatchSelects[25] && swatchSelects[33] && swatchSelects[48] && swatchSelects[59]
                            && !swatchSelects[41] && !swatchSelects[54]) ||
                    (swatchSelects[25] && swatchSelects[41] && swatchSelects[48] && swatchSelects[59]
                            && !swatchSelects[33] && !swatchSelects[54]) ||
                    (swatchSelects[33] && swatchSelects[41] && swatchSelects[48] && swatchSelects[59]
                            && !swatchSelects[25] && !swatchSelects[54]) ||
                    (swatchSelects[25] && swatchSelects[33] && swatchSelects[54] && swatchSelects[59]
                            && !swatchSelects[41] && !swatchSelects[48]) ||
                    (swatchSelects[25] && swatchSelects[41] && swatchSelects[54] && swatchSelects[59]
                            && !swatchSelects[33] && !swatchSelects[48]) ||
                    (swatchSelects[33] && swatchSelects[41] && swatchSelects[54] && swatchSelects[59]
                            && !swatchSelects[25] && !swatchSelects[48]) ||
                    (swatchSelects[25] && swatchSelects[48] && swatchSelects[54] && swatchSelects[59]
                            && !swatchSelects[33] && !swatchSelects[41]) ||
                    (swatchSelects[33] && swatchSelects[48] && swatchSelects[54] && swatchSelects[59]
                            && !swatchSelects[25] && !swatchSelects[41]) ||
                    (swatchSelects[41] && swatchSelects[48] && swatchSelects[54] && swatchSelects[59]
                            && !swatchSelects[25] && !swatchSelects[33])) {
                int a[] = {25, 33, 41, 48, 54, 59};
                i = pickUnSelected(a);
            } else if ((swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24]
                    && !swatchSelects[33] && !swatchSelects[42]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[33]
                            && !swatchSelects[24] && !swatchSelects[42]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[24] && swatchSelects[33]
                            && !swatchSelects[16] && !swatchSelects[42]) ||
                    (swatchSelects[3] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33]
                            && !swatchSelects[9] && !swatchSelects[42]) ||
                    (swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33]
                            && !swatchSelects[3] && !swatchSelects[42]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[42]
                            && !swatchSelects[24] && !swatchSelects[33]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[24] && swatchSelects[42]
                            && !swatchSelects[16] && !swatchSelects[33]) ||
                    (swatchSelects[3] && swatchSelects[16] && swatchSelects[24] && swatchSelects[42]
                            && !swatchSelects[9] && !swatchSelects[33]) ||
                    (swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[42]
                            && !swatchSelects[3] && !swatchSelects[33]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[33] && swatchSelects[42]
                            && !swatchSelects[16] && !swatchSelects[24]) ||
                    (swatchSelects[3] && swatchSelects[16] && swatchSelects[33] && swatchSelects[42]
                            && !swatchSelects[9] && !swatchSelects[24]) ||
                    (swatchSelects[9] && swatchSelects[16] && swatchSelects[33] && swatchSelects[42]
                            && !swatchSelects[3] && !swatchSelects[24]) ||
                    (swatchSelects[3] && swatchSelects[24] && swatchSelects[33] && swatchSelects[42]
                            && !swatchSelects[9] && !swatchSelects[16]) ||
                    (swatchSelects[9] && swatchSelects[24] && swatchSelects[33] && swatchSelects[42]
                            && !swatchSelects[3] && !swatchSelects[16]) ||
                    (swatchSelects[16] && swatchSelects[24] && swatchSelects[33] && swatchSelects[42]
                            && !swatchSelects[3] && !swatchSelects[9])) {
                int a[] = {3, 9, 16, 24, 33, 42};
                i = pickUnSelected(a);
            } else if ((swatchSelects[18] && swatchSelects[27] && swatchSelects[36] && swatchSelects[44]
                    && !swatchSelects[51] && !swatchSelects[57]) ||
                    (swatchSelects[18] && swatchSelects[27] && swatchSelects[36] && swatchSelects[51]
                            && !swatchSelects[44] && !swatchSelects[57]) ||
                    (swatchSelects[18] && swatchSelects[27] && swatchSelects[44] && swatchSelects[51]
                            && !swatchSelects[36] && !swatchSelects[57]) ||
                    (swatchSelects[18] && swatchSelects[36] && swatchSelects[44] && swatchSelects[51]
                            && !swatchSelects[27] && !swatchSelects[57]) ||
                    (swatchSelects[27] && swatchSelects[36] && swatchSelects[44] && swatchSelects[51]
                            && !swatchSelects[18] && !swatchSelects[57]) ||
                    (swatchSelects[18] && swatchSelects[27] && swatchSelects[36] && swatchSelects[57]
                            && !swatchSelects[44] && !swatchSelects[51]) ||
                    (swatchSelects[18] && swatchSelects[27] && swatchSelects[44] && swatchSelects[57]
                            && !swatchSelects[36] && !swatchSelects[51]) ||
                    (swatchSelects[18] && swatchSelects[36] && swatchSelects[44] && swatchSelects[57]
                            && !swatchSelects[27] && !swatchSelects[51]) ||
                    (swatchSelects[27] && swatchSelects[36] && swatchSelects[44] && swatchSelects[57]
                            && !swatchSelects[18] && !swatchSelects[51]) ||
                    (swatchSelects[18] && swatchSelects[27] && swatchSelects[51] && swatchSelects[57]
                            && !swatchSelects[36] && !swatchSelects[44]) ||
                    (swatchSelects[18] && swatchSelects[36] && swatchSelects[51] && swatchSelects[57]
                            && !swatchSelects[27] && !swatchSelects[44]) ||
                    (swatchSelects[27] && swatchSelects[36] && swatchSelects[51] && swatchSelects[57]
                            && !swatchSelects[18] && !swatchSelects[44]) ||
                    (swatchSelects[18] && swatchSelects[44] && swatchSelects[51] && swatchSelects[57]
                            && !swatchSelects[27] && !swatchSelects[36]) ||
                    (swatchSelects[27] && swatchSelects[44] && swatchSelects[51] && swatchSelects[57]
                            && !swatchSelects[18] && !swatchSelects[36]) ||
                    (swatchSelects[36] && swatchSelects[44] && swatchSelects[51] && swatchSelects[57]
                            && !swatchSelects[18] && !swatchSelects[27])) {
                int a[] = {18, 27, 36, 44, 51, 57};
                i = pickUnSelected(a);
            } else if ((swatchSelects[11] && swatchSelects[12] && swatchSelects[13] && swatchSelects[14]
                    && swatchSelects[15] && !swatchSelects[16] && !swatchSelects[17]) ||
                    (swatchSelects[11] && swatchSelects[12] && swatchSelects[13] && swatchSelects[14]
                            && swatchSelects[16] && !swatchSelects[15] && !swatchSelects[17]) ||
                    (swatchSelects[11] && swatchSelects[12] && swatchSelects[13] && swatchSelects[15]
                            && swatchSelects[16] && !swatchSelects[14] && !swatchSelects[17]) ||
                    (swatchSelects[11] && swatchSelects[12] && swatchSelects[14] && swatchSelects[15]
                            && swatchSelects[16] && !swatchSelects[13] && !swatchSelects[17]) ||
                    (swatchSelects[11] && swatchSelects[13] && swatchSelects[14] && swatchSelects[15]
                            && swatchSelects[16] && !swatchSelects[12] && !swatchSelects[17]) ||
                    (swatchSelects[12] && swatchSelects[13] && swatchSelects[14] && swatchSelects[15]
                            && swatchSelects[16] && !swatchSelects[11] && !swatchSelects[17]) ||
                    (swatchSelects[11] && swatchSelects[12] && swatchSelects[13] && swatchSelects[14]
                            && swatchSelects[17] && !swatchSelects[15] && !swatchSelects[16]) ||
                    (swatchSelects[11] && swatchSelects[12] && swatchSelects[13] && swatchSelects[15]
                            && swatchSelects[17] && !swatchSelects[14] && !swatchSelects[16]) ||
                    (swatchSelects[11] && swatchSelects[12] && swatchSelects[14] && swatchSelects[15]
                            && swatchSelects[17] && !swatchSelects[13] && !swatchSelects[16]) ||
                    (swatchSelects[11] && swatchSelects[13] && swatchSelects[14] && swatchSelects[15]
                            && swatchSelects[17] && !swatchSelects[12] && !swatchSelects[16]) ||
                    (swatchSelects[12] && swatchSelects[13] && swatchSelects[14] && swatchSelects[15]
                            && swatchSelects[17] && !swatchSelects[11] && !swatchSelects[16]) ||
                    (swatchSelects[11] && swatchSelects[12] && swatchSelects[13] && swatchSelects[16]
                            && swatchSelects[17] && !swatchSelects[14] && !swatchSelects[15]) ||
                    (swatchSelects[11] && swatchSelects[12] && swatchSelects[14] && swatchSelects[16]
                            && swatchSelects[17] && !swatchSelects[13] && !swatchSelects[15]) ||
                    (swatchSelects[11] && swatchSelects[13] && swatchSelects[14] && swatchSelects[16]
                            && swatchSelects[17] && !swatchSelects[12] && !swatchSelects[15]) ||
                    (swatchSelects[12] && swatchSelects[13] && swatchSelects[14] && swatchSelects[16]
                            && swatchSelects[17] && !swatchSelects[11] && !swatchSelects[15]) ||
                    (swatchSelects[11] && swatchSelects[12] && swatchSelects[15] && swatchSelects[16]
                            && swatchSelects[17] && !swatchSelects[13] && !swatchSelects[14]) ||
                    (swatchSelects[11] && swatchSelects[13] && swatchSelects[15] && swatchSelects[16]
                            && swatchSelects[17] && !swatchSelects[12] && !swatchSelects[14]) ||
                    (swatchSelects[12] && swatchSelects[13] && swatchSelects[15] && swatchSelects[16]
                            && swatchSelects[17] && !swatchSelects[11] && !swatchSelects[14]) ||
                    (swatchSelects[11] && swatchSelects[14] && swatchSelects[15] && swatchSelects[16]
                            && swatchSelects[17] && !swatchSelects[12] && !swatchSelects[13]) ||
                    (swatchSelects[12] && swatchSelects[14] && swatchSelects[15] && swatchSelects[16]
                            && swatchSelects[17] && !swatchSelects[11] && !swatchSelects[13]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[16]
                            && swatchSelects[17] && !swatchSelects[11] && !swatchSelects[12])) {
                int a[] = {11, 12, 13, 14, 15, 16, 17};
                i = pickUnSelected(a);
            } else if ((swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                    && swatchSelects[47] && !swatchSelects[48] && !swatchSelects[49]) ||
                    (swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[48] && !swatchSelects[47] && !swatchSelects[49]) ||
                    (swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[47]
                            && swatchSelects[48] && !swatchSelects[46] && !swatchSelects[49]) ||
                    (swatchSelects[43] && swatchSelects[44] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && !swatchSelects[45] && !swatchSelects[49]) ||
                    (swatchSelects[43] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && !swatchSelects[44] && !swatchSelects[49]) ||
                    (swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && !swatchSelects[43] && !swatchSelects[49]) ||
                    (swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[49] && !swatchSelects[47] && !swatchSelects[48]) ||
                    (swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[47]
                            && swatchSelects[49] && !swatchSelects[46] && !swatchSelects[48]) ||
                    (swatchSelects[43] && swatchSelects[44] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[49] && !swatchSelects[45] && !swatchSelects[48]) ||
                    (swatchSelects[43] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[49] && !swatchSelects[44] && !swatchSelects[48]) ||
                    (swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[49] && !swatchSelects[43] && !swatchSelects[48]) ||
                    (swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[48]
                            && swatchSelects[49] && !swatchSelects[46] && !swatchSelects[47]) ||
                    (swatchSelects[43] && swatchSelects[44] && swatchSelects[46] && swatchSelects[48]
                            && swatchSelects[49] && !swatchSelects[45] && !swatchSelects[47]) ||
                    (swatchSelects[43] && swatchSelects[45] && swatchSelects[46] && swatchSelects[48]
                            && swatchSelects[49] && !swatchSelects[44] && !swatchSelects[47]) ||
                    (swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[48]
                            && swatchSelects[49] && !swatchSelects[43] && !swatchSelects[47]) ||
                    (swatchSelects[43] && swatchSelects[44] && swatchSelects[47] && swatchSelects[48]
                            && swatchSelects[49] && !swatchSelects[45] && !swatchSelects[46]) ||
                    (swatchSelects[43] && swatchSelects[45] && swatchSelects[47] && swatchSelects[48]
                            && swatchSelects[49] && !swatchSelects[44] && !swatchSelects[46]) ||
                    (swatchSelects[44] && swatchSelects[45] && swatchSelects[47] && swatchSelects[48]
                            && swatchSelects[49] && !swatchSelects[43] && !swatchSelects[46]) ||
                    (swatchSelects[43] && swatchSelects[46] && swatchSelects[47] && swatchSelects[48]
                            && swatchSelects[49] && !swatchSelects[44] && !swatchSelects[45]) ||
                    (swatchSelects[44] && swatchSelects[46] && swatchSelects[47] && swatchSelects[48]
                            && swatchSelects[49] && !swatchSelects[43] && !swatchSelects[45]) ||
                    (swatchSelects[45] && swatchSelects[46] && swatchSelects[47] && swatchSelects[48]
                            && swatchSelects[49] && !swatchSelects[43] && !swatchSelects[44])) {
                int a[] = {43, 44, 45, 46, 47, 48, 49};
                i = pickUnSelected(a);
            } else if ((swatchSelects[2] && swatchSelects[7] && swatchSelects[13] && swatchSelects[20]
                    && swatchSelects[28] && !swatchSelects[36] && !swatchSelects[43]) ||
                    (swatchSelects[2] && swatchSelects[7] && swatchSelects[13] && swatchSelects[20]
                            && swatchSelects[36] && !swatchSelects[28] && !swatchSelects[43]) ||
                    (swatchSelects[2] && swatchSelects[7] && swatchSelects[13] && swatchSelects[28]
                            && swatchSelects[36] && !swatchSelects[20] && !swatchSelects[43]) ||
                    (swatchSelects[2] && swatchSelects[7] && swatchSelects[20] && swatchSelects[28]
                            && swatchSelects[36] && !swatchSelects[13] && !swatchSelects[43]) ||
                    (swatchSelects[2] && swatchSelects[13] && swatchSelects[20] && swatchSelects[28]
                            && swatchSelects[36] && !swatchSelects[7] && !swatchSelects[43]) ||
                    (swatchSelects[7] && swatchSelects[13] && swatchSelects[20] && swatchSelects[28]
                            && swatchSelects[36] && !swatchSelects[2] && !swatchSelects[43]) ||
                    (swatchSelects[2] && swatchSelects[7] && swatchSelects[13] && swatchSelects[20]
                            && swatchSelects[43] && !swatchSelects[28] && !swatchSelects[36]) ||
                    (swatchSelects[2] && swatchSelects[7] && swatchSelects[13] && swatchSelects[28]
                            && swatchSelects[43] && !swatchSelects[20] && !swatchSelects[36]) ||
                    (swatchSelects[2] && swatchSelects[7] && swatchSelects[20] && swatchSelects[28]
                            && swatchSelects[43] && !swatchSelects[13] && !swatchSelects[36]) ||
                    (swatchSelects[2] && swatchSelects[13] && swatchSelects[20] && swatchSelects[28]
                            && swatchSelects[43] && !swatchSelects[7] && !swatchSelects[36]) ||
                    (swatchSelects[7] && swatchSelects[13] && swatchSelects[20] && swatchSelects[28]
                            && swatchSelects[43] && !swatchSelects[2] && !swatchSelects[36]) ||
                    (swatchSelects[2] && swatchSelects[7] && swatchSelects[13] && swatchSelects[36]
                            && swatchSelects[43] && !swatchSelects[20] && !swatchSelects[28]) ||
                    (swatchSelects[2] && swatchSelects[7] && swatchSelects[20] && swatchSelects[36]
                            && swatchSelects[43] && !swatchSelects[13] && !swatchSelects[28]) ||
                    (swatchSelects[2] && swatchSelects[13] && swatchSelects[20] && swatchSelects[36]
                            && swatchSelects[43] && !swatchSelects[7] && !swatchSelects[28]) ||
                    (swatchSelects[7] && swatchSelects[13] && swatchSelects[20] && swatchSelects[36]
                            && swatchSelects[43] && !swatchSelects[2] && !swatchSelects[28]) ||
                    (swatchSelects[2] && swatchSelects[7] && swatchSelects[28] && swatchSelects[36]
                            && swatchSelects[43] && !swatchSelects[13] && !swatchSelects[20]) ||
                    (swatchSelects[2] && swatchSelects[13] && swatchSelects[28] && swatchSelects[36]
                            && swatchSelects[43] && !swatchSelects[7] && !swatchSelects[20]) ||
                    (swatchSelects[7] && swatchSelects[13] && swatchSelects[28] && swatchSelects[36]
                            && swatchSelects[43] && !swatchSelects[2] && !swatchSelects[20]) ||
                    (swatchSelects[2] && swatchSelects[20] && swatchSelects[28] && swatchSelects[36]
                            && swatchSelects[43] && !swatchSelects[7] && !swatchSelects[13]) ||
                    (swatchSelects[7] && swatchSelects[20] && swatchSelects[28] && swatchSelects[36]
                            && swatchSelects[43] && !swatchSelects[2] && !swatchSelects[13]) ||
                    (swatchSelects[13] && swatchSelects[20] && swatchSelects[28] && swatchSelects[36]
                            && swatchSelects[43] && !swatchSelects[2] && !swatchSelects[7])) {
                int a[] = {2, 7, 13, 20, 28, 36, 43};
                i = pickUnSelected(a);
            } else if ((swatchSelects[17] && swatchSelects[24] && swatchSelects[32] && swatchSelects[40]
                    && swatchSelects[47] && !swatchSelects[53] && !swatchSelects[58]) ||
                    (swatchSelects[17] && swatchSelects[24] && swatchSelects[32] && swatchSelects[40]
                            && swatchSelects[53] && !swatchSelects[47] && !swatchSelects[58]) ||
                    (swatchSelects[17] && swatchSelects[24] && swatchSelects[32] && swatchSelects[47]
                            && swatchSelects[53] && !swatchSelects[40] && !swatchSelects[58]) ||
                    (swatchSelects[17] && swatchSelects[24] && swatchSelects[40] && swatchSelects[47]
                            && swatchSelects[53] && !swatchSelects[32] && !swatchSelects[58]) ||
                    (swatchSelects[17] && swatchSelects[32] && swatchSelects[40] && swatchSelects[47]
                            && swatchSelects[53] && !swatchSelects[24] && !swatchSelects[58]) ||
                    (swatchSelects[24] && swatchSelects[32] && swatchSelects[40] && swatchSelects[47]
                            && swatchSelects[53] && !swatchSelects[17] && !swatchSelects[58]) ||
                    (swatchSelects[17] && swatchSelects[24] && swatchSelects[32] && swatchSelects[40]
                            && swatchSelects[58] && !swatchSelects[47] && !swatchSelects[53]) ||
                    (swatchSelects[17] && swatchSelects[24] && swatchSelects[32] && swatchSelects[47]
                            && swatchSelects[58] && !swatchSelects[40] && !swatchSelects[53]) ||
                    (swatchSelects[17] && swatchSelects[24] && swatchSelects[40] && swatchSelects[47]
                            && swatchSelects[58] && !swatchSelects[32] && !swatchSelects[53]) ||
                    (swatchSelects[17] && swatchSelects[32] && swatchSelects[40] && swatchSelects[47]
                            && swatchSelects[58] && !swatchSelects[24] && !swatchSelects[53]) ||
                    (swatchSelects[24] && swatchSelects[32] && swatchSelects[40] && swatchSelects[47]
                            && swatchSelects[58] && !swatchSelects[17] && !swatchSelects[53]) ||
                    (swatchSelects[17] && swatchSelects[24] && swatchSelects[32] && swatchSelects[53]
                            && swatchSelects[58] && !swatchSelects[40] && !swatchSelects[47]) ||
                    (swatchSelects[17] && swatchSelects[24] && swatchSelects[40] && swatchSelects[53]
                            && swatchSelects[58] && !swatchSelects[32] && !swatchSelects[47]) ||
                    (swatchSelects[17] && swatchSelects[32] && swatchSelects[40] && swatchSelects[53]
                            && swatchSelects[58] && !swatchSelects[24] && !swatchSelects[47]) ||
                    (swatchSelects[24] && swatchSelects[32] && swatchSelects[40] && swatchSelects[53]
                            && swatchSelects[58] && !swatchSelects[17] && !swatchSelects[47]) ||
                    (swatchSelects[17] && swatchSelects[24] && swatchSelects[47] && swatchSelects[53]
                            && swatchSelects[58] && !swatchSelects[32] && !swatchSelects[40]) ||
                    (swatchSelects[17] && swatchSelects[32] && swatchSelects[47] && swatchSelects[53]
                            && swatchSelects[58] && !swatchSelects[24] && !swatchSelects[40]) ||
                    (swatchSelects[24] && swatchSelects[32] && swatchSelects[47] && swatchSelects[53]
                            && swatchSelects[58] && !swatchSelects[17] && !swatchSelects[40]) ||
                    (swatchSelects[17] && swatchSelects[40] && swatchSelects[47] && swatchSelects[53]
                            && swatchSelects[58] && !swatchSelects[24] && !swatchSelects[32]) ||
                    (swatchSelects[24] && swatchSelects[40] && swatchSelects[47] && swatchSelects[53]
                            && swatchSelects[58] && !swatchSelects[17] && !swatchSelects[32]) ||
                    (swatchSelects[32] && swatchSelects[40] && swatchSelects[47] && swatchSelects[53]
                            && swatchSelects[58] && !swatchSelects[17] && !swatchSelects[24])) {
                int a[] = {17, 24, 32, 40, 47, 53, 58};
                i = pickUnSelected(a);
            } else if ((swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[23]
                    && swatchSelects[32] && !swatchSelects[41] && !swatchSelects[49]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[23]
                            && swatchSelects[41] && !swatchSelects[32] && !swatchSelects[49]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[32]
                            && swatchSelects[41] && !swatchSelects[23] && !swatchSelects[49]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[23] && swatchSelects[32]
                            && swatchSelects[41] && !swatchSelects[15] && !swatchSelects[49]) ||
                    (swatchSelects[2] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32]
                            && swatchSelects[41] && !swatchSelects[8] && !swatchSelects[49]) ||
                    (swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32]
                            && swatchSelects[41] && !swatchSelects[2] && !swatchSelects[49]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[23]
                            && swatchSelects[49] && !swatchSelects[32] && !swatchSelects[41]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[32]
                            && swatchSelects[49] && !swatchSelects[23] && !swatchSelects[41]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[23] && swatchSelects[32]
                            && swatchSelects[49] && !swatchSelects[15] && !swatchSelects[41]) ||
                    (swatchSelects[2] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32]
                            && swatchSelects[49] && !swatchSelects[8] && !swatchSelects[41]) ||
                    (swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32]
                            && swatchSelects[49] && !swatchSelects[2] && !swatchSelects[41]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[41]
                            && swatchSelects[49] && !swatchSelects[23] && !swatchSelects[32]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[23] && swatchSelects[41]
                            && swatchSelects[49] && !swatchSelects[15] && !swatchSelects[32]) ||
                    (swatchSelects[2] && swatchSelects[15] && swatchSelects[23] && swatchSelects[41]
                            && swatchSelects[49] && !swatchSelects[8] && !swatchSelects[32]) ||
                    (swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[41]
                            && swatchSelects[49] && !swatchSelects[2] && !swatchSelects[32]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[32] && swatchSelects[41]
                            && swatchSelects[49] && !swatchSelects[15] && !swatchSelects[23]) ||
                    (swatchSelects[2] && swatchSelects[15] && swatchSelects[32] && swatchSelects[41]
                            && swatchSelects[49] && !swatchSelects[8] && !swatchSelects[23]) ||
                    (swatchSelects[8] && swatchSelects[15] && swatchSelects[32] && swatchSelects[41]
                            && swatchSelects[49] && !swatchSelects[2] && !swatchSelects[23]) ||
                    (swatchSelects[2] && swatchSelects[23] && swatchSelects[32] && swatchSelects[41]
                            && swatchSelects[49] && !swatchSelects[8] && !swatchSelects[15]) ||
                    (swatchSelects[8] && swatchSelects[23] && swatchSelects[32] && swatchSelects[41]
                            && swatchSelects[49] && !swatchSelects[2] && !swatchSelects[15]) ||
                    (swatchSelects[15] && swatchSelects[23] && swatchSelects[32] && swatchSelects[41]
                            && swatchSelects[49] && !swatchSelects[2] && !swatchSelects[8])) {
                int a[] = {2, 8, 15, 23, 32, 41, 49};
                i = pickUnSelected(a);
            } else if ((swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[37]
                    && swatchSelects[45] && !swatchSelects[52] && !swatchSelects[58]) ||
                    (swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[37]
                            && swatchSelects[52] && !swatchSelects[45] && !swatchSelects[58]) ||
                    (swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[45]
                            && swatchSelects[52] && !swatchSelects[37] && !swatchSelects[58]) ||
                    (swatchSelects[11] && swatchSelects[19] && swatchSelects[37] && swatchSelects[45]
                            && swatchSelects[52] && !swatchSelects[28] && !swatchSelects[58]) ||
                    (swatchSelects[11] && swatchSelects[28] && swatchSelects[37] && swatchSelects[45]
                            && swatchSelects[52] && !swatchSelects[19] && !swatchSelects[58]) ||
                    (swatchSelects[19] && swatchSelects[28] && swatchSelects[37] && swatchSelects[45]
                            && swatchSelects[52] && !swatchSelects[11] && !swatchSelects[58]) ||
                    (swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[37]
                            && swatchSelects[58] && !swatchSelects[45] && !swatchSelects[52]) ||
                    (swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[45]
                            && swatchSelects[58] && !swatchSelects[37] && !swatchSelects[52]) ||
                    (swatchSelects[11] && swatchSelects[19] && swatchSelects[37] && swatchSelects[45]
                            && swatchSelects[58] && !swatchSelects[28] && !swatchSelects[52]) ||
                    (swatchSelects[11] && swatchSelects[28] && swatchSelects[37] && swatchSelects[45]
                            && swatchSelects[58] && !swatchSelects[19] && !swatchSelects[52]) ||
                    (swatchSelects[19] && swatchSelects[28] && swatchSelects[37] && swatchSelects[45]
                            && swatchSelects[58] && !swatchSelects[11] && !swatchSelects[52]) ||
                    (swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[52]
                            && swatchSelects[58] && !swatchSelects[37] && !swatchSelects[45]) ||
                    (swatchSelects[11] && swatchSelects[19] && swatchSelects[37] && swatchSelects[52]
                            && swatchSelects[58] && !swatchSelects[28] && !swatchSelects[45]) ||
                    (swatchSelects[11] && swatchSelects[28] && swatchSelects[37] && swatchSelects[52]
                            && swatchSelects[58] && !swatchSelects[19] && !swatchSelects[45]) ||
                    (swatchSelects[19] && swatchSelects[28] && swatchSelects[37] && swatchSelects[52]
                            && swatchSelects[58] && !swatchSelects[11] && !swatchSelects[45]) ||
                    (swatchSelects[11] && swatchSelects[19] && swatchSelects[45] && swatchSelects[52]
                            && swatchSelects[58] && !swatchSelects[28] && !swatchSelects[37]) ||
                    (swatchSelects[11] && swatchSelects[28] && swatchSelects[45] && swatchSelects[52]
                            && swatchSelects[58] && !swatchSelects[19] && !swatchSelects[37]) ||
                    (swatchSelects[19] && swatchSelects[28] && swatchSelects[45] && swatchSelects[52]
                            && swatchSelects[58] && !swatchSelects[11] && !swatchSelects[37]) ||
                    (swatchSelects[11] && swatchSelects[37] && swatchSelects[45] && swatchSelects[52]
                            && swatchSelects[58] && !swatchSelects[19] && !swatchSelects[28]) ||
                    (swatchSelects[19] && swatchSelects[37] && swatchSelects[45] && swatchSelects[52]
                            && swatchSelects[58] && !swatchSelects[11] && !swatchSelects[28]) ||
                    (swatchSelects[28] && swatchSelects[37] && swatchSelects[45] && swatchSelects[52]
                            && swatchSelects[58] && !swatchSelects[11] && !swatchSelects[19])) {
                int a[] = {11, 19, 28, 37, 45, 52, 58};
                i = pickUnSelected(a);
            } else if ((swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && swatchSelects[22]
                    && swatchSelects[23] && !swatchSelects[24] && !swatchSelects[25]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && swatchSelects[22]
                            && swatchSelects[24] && !swatchSelects[23] && !swatchSelects[25]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && swatchSelects[23]
                            && swatchSelects[24] && !swatchSelects[22] && !swatchSelects[25]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[22] && swatchSelects[23]
                            && swatchSelects[24] && !swatchSelects[21] && !swatchSelects[25]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[21] && swatchSelects[22] && swatchSelects[23]
                            && swatchSelects[24] && !swatchSelects[20] && !swatchSelects[25]) ||
                    (swatchSelects[18] && swatchSelects[20] && swatchSelects[21] && swatchSelects[22] && swatchSelects[23]
                            && swatchSelects[24] && !swatchSelects[19] && !swatchSelects[25]) ||
                    (swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && swatchSelects[22] && swatchSelects[23]
                            && swatchSelects[24] && !swatchSelects[18] && !swatchSelects[25]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && swatchSelects[22]
                            && swatchSelects[25] && !swatchSelects[23] && !swatchSelects[24]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && swatchSelects[23]
                            && swatchSelects[25] && !swatchSelects[22] && !swatchSelects[24]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[22] && swatchSelects[23]
                            && swatchSelects[25] && !swatchSelects[21] && !swatchSelects[24]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[21] && swatchSelects[22] && swatchSelects[23]
                            && swatchSelects[25] && !swatchSelects[20] && !swatchSelects[24]) ||
                    (swatchSelects[18] && swatchSelects[20] && swatchSelects[21] && swatchSelects[22] && swatchSelects[23]
                            && swatchSelects[25] && !swatchSelects[19] && !swatchSelects[24]) ||
                    (swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && swatchSelects[22] && swatchSelects[23]
                            && swatchSelects[25] && !swatchSelects[18] && !swatchSelects[24]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && swatchSelects[24]
                            && swatchSelects[25] && !swatchSelects[22] && !swatchSelects[23]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[22] && swatchSelects[24]
                            && swatchSelects[25] && !swatchSelects[21] && !swatchSelects[23]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[21] && swatchSelects[22] && swatchSelects[24]
                            && swatchSelects[25] && !swatchSelects[20] && !swatchSelects[23]) ||
                    (swatchSelects[18] && swatchSelects[20] && swatchSelects[21] && swatchSelects[22] && swatchSelects[24]
                            && swatchSelects[25] && !swatchSelects[19] && !swatchSelects[23]) ||
                    (swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && swatchSelects[22] && swatchSelects[24]
                            && swatchSelects[25] && !swatchSelects[18] && !swatchSelects[23]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[20] && swatchSelects[23] && swatchSelects[24]
                            && swatchSelects[25] && !swatchSelects[21] && !swatchSelects[22]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[21] && swatchSelects[23] && swatchSelects[24]
                            && swatchSelects[25] && !swatchSelects[20] && !swatchSelects[22]) ||
                    (swatchSelects[18] && swatchSelects[20] && swatchSelects[21] && swatchSelects[23] && swatchSelects[24]
                            && swatchSelects[25] && !swatchSelects[19] && !swatchSelects[22]) ||
                    (swatchSelects[19] && swatchSelects[20] && swatchSelects[21] && swatchSelects[23] && swatchSelects[24]
                            && swatchSelects[25] && !swatchSelects[18] && !swatchSelects[22]) ||
                    (swatchSelects[18] && swatchSelects[19] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24]
                            && swatchSelects[25] && !swatchSelects[20] && !swatchSelects[21]) ||
                    (swatchSelects[18] && swatchSelects[20] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24]
                            && swatchSelects[25] && !swatchSelects[19] && !swatchSelects[21]) ||
                    (swatchSelects[19] && swatchSelects[20] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24]
                            && swatchSelects[25] && !swatchSelects[18] && !swatchSelects[21]) ||
                    (swatchSelects[18] && swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24]
                            && swatchSelects[25] && !swatchSelects[19] && !swatchSelects[20]) ||
                    (swatchSelects[19] && swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24]
                            && swatchSelects[25] && !swatchSelects[18] && !swatchSelects[20]) ||
                    (swatchSelects[20] && swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24]
                            && swatchSelects[25] && !swatchSelects[18] && !swatchSelects[19])) {
                int a[] = {18, 19, 20, 21, 22, 23, 24, 25};
                i = pickUnSelected(a);
            } else if ((swatchSelects[35] && swatchSelects[36] && swatchSelects[37] && swatchSelects[38] && swatchSelects[39]
                    && swatchSelects[40] && !swatchSelects[41] && !swatchSelects[42]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[37] && swatchSelects[38] && swatchSelects[39]
                            && swatchSelects[41] && !swatchSelects[40] && !swatchSelects[42]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[37] && swatchSelects[38] && swatchSelects[40]
                            && swatchSelects[41] && !swatchSelects[39] && !swatchSelects[42]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[37] && swatchSelects[39] && swatchSelects[40]
                            && swatchSelects[41] && !swatchSelects[38] && !swatchSelects[42]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[38] && swatchSelects[39] && swatchSelects[40]
                            && swatchSelects[41] && !swatchSelects[37] && !swatchSelects[42]) ||
                    (swatchSelects[35] && swatchSelects[37] && swatchSelects[38] && swatchSelects[39] && swatchSelects[40]
                            && swatchSelects[41] && !swatchSelects[36] && !swatchSelects[42]) ||
                    (swatchSelects[36] && swatchSelects[37] && swatchSelects[38] && swatchSelects[39] && swatchSelects[40]
                            && swatchSelects[41] && !swatchSelects[35] && !swatchSelects[42]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[37] && swatchSelects[38] && swatchSelects[39]
                            && swatchSelects[42] && !swatchSelects[40] && !swatchSelects[41]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[37] && swatchSelects[38] && swatchSelects[40]
                            && swatchSelects[42] && !swatchSelects[39] && !swatchSelects[41]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[37] && swatchSelects[39] && swatchSelects[40]
                            && swatchSelects[42] && !swatchSelects[38] && !swatchSelects[41]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[38] && swatchSelects[39] && swatchSelects[40]
                            && swatchSelects[42] && !swatchSelects[37] && !swatchSelects[41]) ||
                    (swatchSelects[35] && swatchSelects[37] && swatchSelects[38] && swatchSelects[39] && swatchSelects[40]
                            && swatchSelects[42] && !swatchSelects[36] && !swatchSelects[41]) ||
                    (swatchSelects[36] && swatchSelects[37] && swatchSelects[38] && swatchSelects[39] && swatchSelects[40]
                            && swatchSelects[42] && !swatchSelects[35] && !swatchSelects[41]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[37] && swatchSelects[38] && swatchSelects[41]
                            && swatchSelects[42] && !swatchSelects[39] && !swatchSelects[40]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[37] && swatchSelects[39] && swatchSelects[41]
                            && swatchSelects[42] && !swatchSelects[38] && !swatchSelects[40]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[38] && swatchSelects[39] && swatchSelects[41]
                            && swatchSelects[42] && !swatchSelects[37] && !swatchSelects[40]) ||
                    (swatchSelects[35] && swatchSelects[37] && swatchSelects[38] && swatchSelects[39] && swatchSelects[41]
                            && swatchSelects[42] && !swatchSelects[36] && !swatchSelects[40]) ||
                    (swatchSelects[36] && swatchSelects[37] && swatchSelects[38] && swatchSelects[39] && swatchSelects[41]
                            && swatchSelects[42] && !swatchSelects[35] && !swatchSelects[40]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[37] && swatchSelects[40] && swatchSelects[41]
                            && swatchSelects[42] && !swatchSelects[38] && !swatchSelects[39]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[38] && swatchSelects[40] && swatchSelects[41]
                            && swatchSelects[42] && !swatchSelects[37] && !swatchSelects[39]) ||
                    (swatchSelects[35] && swatchSelects[37] && swatchSelects[38] && swatchSelects[40] && swatchSelects[41]
                            && swatchSelects[42] && !swatchSelects[36] && !swatchSelects[39]) ||
                    (swatchSelects[36] && swatchSelects[37] && swatchSelects[38] && swatchSelects[40] && swatchSelects[41]
                            && swatchSelects[42] && !swatchSelects[35] && !swatchSelects[39]) ||
                    (swatchSelects[35] && swatchSelects[36] && swatchSelects[39] && swatchSelects[40] && swatchSelects[41]
                            && swatchSelects[42] && !swatchSelects[37] && !swatchSelects[38]) ||
                    (swatchSelects[35] && swatchSelects[37] && swatchSelects[39] && swatchSelects[40] && swatchSelects[41]
                            && swatchSelects[42] && !swatchSelects[36] && !swatchSelects[38]) ||
                    (swatchSelects[36] && swatchSelects[37] && swatchSelects[39] && swatchSelects[40] && swatchSelects[41]
                            && swatchSelects[42] && !swatchSelects[35] && !swatchSelects[38]) ||
                    (swatchSelects[35] && swatchSelects[38] && swatchSelects[39] && swatchSelects[40] && swatchSelects[41]
                            && swatchSelects[42] && !swatchSelects[36] && !swatchSelects[37]) ||
                    (swatchSelects[36] && swatchSelects[38] && swatchSelects[39] && swatchSelects[40] && swatchSelects[41]
                            && swatchSelects[42] && !swatchSelects[35] && !swatchSelects[37]) ||
                    (swatchSelects[37] && swatchSelects[38] && swatchSelects[39] && swatchSelects[40] && swatchSelects[41]
                            && swatchSelects[42] && !swatchSelects[35] && !swatchSelects[36])) {
                int a[] = {35, 36, 37, 38, 39, 40, 41, 42};
                i = pickUnSelected(a);
            } else if ((swatchSelects[3] && swatchSelects[8] && swatchSelects[14] && swatchSelects[21] && swatchSelects[29]
                    && swatchSelects[37] && !swatchSelects[44] && !swatchSelects[50]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[14] && swatchSelects[21] && swatchSelects[29]
                            && swatchSelects[44] && !swatchSelects[37] && !swatchSelects[50]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[14] && swatchSelects[21] && swatchSelects[37]
                            && swatchSelects[44] && !swatchSelects[29] && !swatchSelects[50]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[14] && swatchSelects[29] && swatchSelects[37]
                            && swatchSelects[44] && !swatchSelects[21] && !swatchSelects[50]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[21] && swatchSelects[29] && swatchSelects[37]
                            && swatchSelects[44] && !swatchSelects[14] && !swatchSelects[50]) ||
                    (swatchSelects[3] && swatchSelects[14] && swatchSelects[21] && swatchSelects[29] && swatchSelects[37]
                            && swatchSelects[44] && !swatchSelects[8] && !swatchSelects[50]) ||
                    (swatchSelects[8] && swatchSelects[14] && swatchSelects[21] && swatchSelects[29] && swatchSelects[37]
                            && swatchSelects[44] && !swatchSelects[3] && !swatchSelects[50]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[14] && swatchSelects[21] && swatchSelects[29]
                            && swatchSelects[50] && !swatchSelects[37] && !swatchSelects[44]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[14] && swatchSelects[21] && swatchSelects[37]
                            && swatchSelects[50] && !swatchSelects[29] && !swatchSelects[44]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[14] && swatchSelects[29] && swatchSelects[37]
                            && swatchSelects[50] && !swatchSelects[21] && !swatchSelects[44]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[21] && swatchSelects[29] && swatchSelects[37]
                            && swatchSelects[50] && !swatchSelects[14] && !swatchSelects[44]) ||
                    (swatchSelects[3] && swatchSelects[14] && swatchSelects[21] && swatchSelects[29] && swatchSelects[37]
                            && swatchSelects[50] && !swatchSelects[8] && !swatchSelects[44]) ||
                    (swatchSelects[8] && swatchSelects[14] && swatchSelects[21] && swatchSelects[29] && swatchSelects[37]
                            && swatchSelects[50] && !swatchSelects[3] && !swatchSelects[44]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[14] && swatchSelects[21] && swatchSelects[44]
                            && swatchSelects[50] && !swatchSelects[29] && !swatchSelects[37]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[14] && swatchSelects[29] && swatchSelects[44]
                            && swatchSelects[50] && !swatchSelects[21] && !swatchSelects[37]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[21] && swatchSelects[29] && swatchSelects[44]
                            && swatchSelects[50] && !swatchSelects[14] && !swatchSelects[37]) ||
                    (swatchSelects[3] && swatchSelects[14] && swatchSelects[21] && swatchSelects[29] && swatchSelects[44]
                            && swatchSelects[50] && !swatchSelects[8] && !swatchSelects[37]) ||
                    (swatchSelects[8] && swatchSelects[14] && swatchSelects[21] && swatchSelects[29] && swatchSelects[44]
                            && swatchSelects[50] && !swatchSelects[3] && !swatchSelects[37]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[14] && swatchSelects[37] && swatchSelects[44]
                            && swatchSelects[50] && !swatchSelects[21] && !swatchSelects[29]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[21] && swatchSelects[37] && swatchSelects[44]
                            && swatchSelects[50] && !swatchSelects[14] && !swatchSelects[29]) ||
                    (swatchSelects[3] && swatchSelects[14] && swatchSelects[21] && swatchSelects[37] && swatchSelects[44]
                            && swatchSelects[50] && !swatchSelects[8] && !swatchSelects[29]) ||
                    (swatchSelects[8] && swatchSelects[14] && swatchSelects[21] && swatchSelects[37] && swatchSelects[44]
                            && swatchSelects[50] && !swatchSelects[3] && !swatchSelects[29]) ||
                    (swatchSelects[3] && swatchSelects[8] && swatchSelects[29] && swatchSelects[37] && swatchSelects[44]
                            && swatchSelects[50] && !swatchSelects[14] && !swatchSelects[21]) ||
                    (swatchSelects[3] && swatchSelects[14] && swatchSelects[29] && swatchSelects[37] && swatchSelects[44]
                            && swatchSelects[50] && !swatchSelects[8] && !swatchSelects[21]) ||
                    (swatchSelects[8] && swatchSelects[14] && swatchSelects[29] && swatchSelects[37] && swatchSelects[44]
                            && swatchSelects[50] && !swatchSelects[3] && !swatchSelects[21]) ||
                    (swatchSelects[3] && swatchSelects[21] && swatchSelects[29] && swatchSelects[37] && swatchSelects[44]
                            && swatchSelects[50] && !swatchSelects[8] && !swatchSelects[14]) ||
                    (swatchSelects[8] && swatchSelects[21] && swatchSelects[29] && swatchSelects[37] && swatchSelects[44]
                            && swatchSelects[50] && !swatchSelects[3] && !swatchSelects[14]) ||
                    (swatchSelects[14] && swatchSelects[21] && swatchSelects[29] && swatchSelects[37] && swatchSelects[44]
                            && swatchSelects[50] && !swatchSelects[3] && !swatchSelects[8])) {
                int a[] = {3, 8, 14, 21, 29, 37, 44, 50};
                i = pickUnSelected(a);
            } else if ((swatchSelects[10] && swatchSelects[16] && swatchSelects[23] && swatchSelects[31] && swatchSelects[39]
                    && swatchSelects[46] && !swatchSelects[52] && !swatchSelects[57]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[23] && swatchSelects[31] && swatchSelects[39]
                            && swatchSelects[52] && !swatchSelects[46] && !swatchSelects[57]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[23] && swatchSelects[31] && swatchSelects[46]
                            && swatchSelects[52] && !swatchSelects[39] && !swatchSelects[57]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[23] && swatchSelects[39] && swatchSelects[46]
                            && swatchSelects[52] && !swatchSelects[31] && !swatchSelects[57]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[31] && swatchSelects[39] && swatchSelects[46]
                            && swatchSelects[52] && !swatchSelects[23] && !swatchSelects[57]) ||
                    (swatchSelects[10] && swatchSelects[23] && swatchSelects[31] && swatchSelects[39] && swatchSelects[46]
                            && swatchSelects[52] && !swatchSelects[16] && !swatchSelects[57]) ||
                    (swatchSelects[16] && swatchSelects[23] && swatchSelects[31] && swatchSelects[39] && swatchSelects[46]
                            && swatchSelects[52] && !swatchSelects[10] && !swatchSelects[57]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[23] && swatchSelects[31] && swatchSelects[39]
                            && swatchSelects[57] && !swatchSelects[46] && !swatchSelects[52]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[23] && swatchSelects[31] && swatchSelects[46]
                            && swatchSelects[57] && !swatchSelects[39] && !swatchSelects[52]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[23] && swatchSelects[39] && swatchSelects[46]
                            && swatchSelects[57] && !swatchSelects[31] && !swatchSelects[52]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[31] && swatchSelects[39] && swatchSelects[46]
                            && swatchSelects[57] && !swatchSelects[23] && !swatchSelects[52]) ||
                    (swatchSelects[10] && swatchSelects[23] && swatchSelects[31] && swatchSelects[39] && swatchSelects[46]
                            && swatchSelects[57] && !swatchSelects[16] && !swatchSelects[52]) ||
                    (swatchSelects[16] && swatchSelects[23] && swatchSelects[31] && swatchSelects[39] && swatchSelects[46]
                            && swatchSelects[57] && !swatchSelects[10] && !swatchSelects[52]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[23] && swatchSelects[31] && swatchSelects[52]
                            && swatchSelects[57] && !swatchSelects[39] && !swatchSelects[46]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[23] && swatchSelects[39] && swatchSelects[52]
                            && swatchSelects[57] && !swatchSelects[31] && !swatchSelects[46]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[31] && swatchSelects[39] && swatchSelects[52]
                            && swatchSelects[57] && !swatchSelects[23] && !swatchSelects[46]) ||
                    (swatchSelects[10] && swatchSelects[23] && swatchSelects[31] && swatchSelects[39] && swatchSelects[52]
                            && swatchSelects[57] && !swatchSelects[16] && !swatchSelects[46]) ||
                    (swatchSelects[16] && swatchSelects[23] && swatchSelects[31] && swatchSelects[39] && swatchSelects[52]
                            && swatchSelects[57] && !swatchSelects[10] && !swatchSelects[46]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[23] && swatchSelects[46] && swatchSelects[52]
                            && swatchSelects[57] && !swatchSelects[31] && !swatchSelects[39]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[31] && swatchSelects[46] && swatchSelects[52]
                            && swatchSelects[57] && !swatchSelects[23] && !swatchSelects[39]) ||
                    (swatchSelects[10] && swatchSelects[23] && swatchSelects[31] && swatchSelects[46] && swatchSelects[52]
                            && swatchSelects[57] && !swatchSelects[16] && !swatchSelects[39]) ||
                    (swatchSelects[16] && swatchSelects[23] && swatchSelects[31] && swatchSelects[46] && swatchSelects[52]
                            && swatchSelects[57] && !swatchSelects[10] && !swatchSelects[39]) ||
                    (swatchSelects[10] && swatchSelects[16] && swatchSelects[39] && swatchSelects[46] && swatchSelects[52]
                            && swatchSelects[57] && !swatchSelects[23] && !swatchSelects[31]) ||
                    (swatchSelects[10] && swatchSelects[23] && swatchSelects[39] && swatchSelects[46] && swatchSelects[52]
                            && swatchSelects[57] && !swatchSelects[16] && !swatchSelects[31]) ||
                    (swatchSelects[16] && swatchSelects[23] && swatchSelects[39] && swatchSelects[46] && swatchSelects[52]
                            && swatchSelects[57] && !swatchSelects[10] && !swatchSelects[31]) ||
                    (swatchSelects[10] && swatchSelects[31] && swatchSelects[39] && swatchSelects[46] && swatchSelects[52]
                            && swatchSelects[57] && !swatchSelects[16] && !swatchSelects[23]) ||
                    (swatchSelects[16] && swatchSelects[31] && swatchSelects[39] && swatchSelects[46] && swatchSelects[52]
                            && swatchSelects[57] && !swatchSelects[10] && !swatchSelects[23]) ||
                    (swatchSelects[23] && swatchSelects[31] && swatchSelects[39] && swatchSelects[46] && swatchSelects[52]
                            && swatchSelects[57] && !swatchSelects[10] && !swatchSelects[16])) {
                int a[] = {10, 16, 23, 31, 39, 46, 52, 57};
                i = pickUnSelected(a);
            } else if ((swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31]
                    && swatchSelects[40] && !swatchSelects[48] && !swatchSelects[55]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31]
                            && swatchSelects[48] && !swatchSelects[40] && !swatchSelects[55]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[40]
                            && swatchSelects[48] && !swatchSelects[31] && !swatchSelects[55]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[31] && swatchSelects[40]
                            && swatchSelects[48] && !swatchSelects[22] && !swatchSelects[55]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[22] && swatchSelects[31] && swatchSelects[40]
                            && swatchSelects[48] && !swatchSelects[14] && !swatchSelects[55]) ||
                    (swatchSelects[1] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31] && swatchSelects[40]
                            && swatchSelects[48] && !swatchSelects[7] && !swatchSelects[55]) ||
                    (swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31] && swatchSelects[40]
                            && swatchSelects[48] && !swatchSelects[1] && !swatchSelects[55]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31]
                            && swatchSelects[55] && !swatchSelects[40] && !swatchSelects[48]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[40]
                            && swatchSelects[55] && !swatchSelects[31] && !swatchSelects[48]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[31] && swatchSelects[40]
                            && swatchSelects[55] && !swatchSelects[22] && !swatchSelects[48]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[22] && swatchSelects[31] && swatchSelects[40]
                            && swatchSelects[55] && !swatchSelects[14] && !swatchSelects[48]) ||
                    (swatchSelects[1] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31] && swatchSelects[40]
                            && swatchSelects[55] && !swatchSelects[7] && !swatchSelects[48]) ||
                    (swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31] && swatchSelects[40]
                            && swatchSelects[55] && !swatchSelects[1] && !swatchSelects[48]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[48]
                            && swatchSelects[55] && !swatchSelects[31] && !swatchSelects[40]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[31] && swatchSelects[48]
                            && swatchSelects[55] && !swatchSelects[22] && !swatchSelects[40]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[22] && swatchSelects[31] && swatchSelects[48]
                            && swatchSelects[55] && !swatchSelects[14] && !swatchSelects[40]) ||
                    (swatchSelects[1] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31] && swatchSelects[48]
                            && swatchSelects[55] && !swatchSelects[7] && !swatchSelects[40]) ||
                    (swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31] && swatchSelects[48]
                            && swatchSelects[55] && !swatchSelects[1] && !swatchSelects[40]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[40] && swatchSelects[48]
                            && swatchSelects[55] && !swatchSelects[22] && !swatchSelects[31]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[22] && swatchSelects[40] && swatchSelects[48]
                            && swatchSelects[55] && !swatchSelects[14] && !swatchSelects[31]) ||
                    (swatchSelects[1] && swatchSelects[14] && swatchSelects[22] && swatchSelects[40] && swatchSelects[48]
                            && swatchSelects[55] && !swatchSelects[7] && !swatchSelects[31]) ||
                    (swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[40] && swatchSelects[48]
                            && swatchSelects[55] && !swatchSelects[1] && !swatchSelects[31]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[31] && swatchSelects[40] && swatchSelects[48]
                            && swatchSelects[55] && !swatchSelects[14] && !swatchSelects[22]) ||
                    (swatchSelects[1] && swatchSelects[14] && swatchSelects[31] && swatchSelects[40] && swatchSelects[48]
                            && swatchSelects[55] && !swatchSelects[7] && !swatchSelects[22]) ||
                    (swatchSelects[7] && swatchSelects[14] && swatchSelects[31] && swatchSelects[40] && swatchSelects[48]
                            && swatchSelects[55] && !swatchSelects[1] && !swatchSelects[22]) ||
                    (swatchSelects[1] && swatchSelects[22] && swatchSelects[31] && swatchSelects[40] && swatchSelects[48]
                            && swatchSelects[55] && !swatchSelects[7] && !swatchSelects[14]) ||
                    (swatchSelects[7] && swatchSelects[22] && swatchSelects[31] && swatchSelects[40] && swatchSelects[48]
                            && swatchSelects[55] && !swatchSelects[1] && !swatchSelects[14]) ||
                    (swatchSelects[14] && swatchSelects[22] && swatchSelects[31] && swatchSelects[40] && swatchSelects[48]
                            && swatchSelects[55] && !swatchSelects[1] && !swatchSelects[7])) {
                int a[] = {1, 7, 14, 22, 31, 40, 48, 55};
                i = pickUnSelected(a);
            } else if ((swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[38]
                    && swatchSelects[46] && !swatchSelects[53] && !swatchSelects[59]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[38]
                            && swatchSelects[53] && !swatchSelects[46] && !swatchSelects[59]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[46]
                            && swatchSelects[53] && !swatchSelects[38] && !swatchSelects[59]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[38] && swatchSelects[46]
                            && swatchSelects[53] && !swatchSelects[29] && !swatchSelects[59]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[29] && swatchSelects[38] && swatchSelects[46]
                            && swatchSelects[53] && !swatchSelects[20] && !swatchSelects[59]) ||
                    (swatchSelects[5] && swatchSelects[20] && swatchSelects[29] && swatchSelects[38] && swatchSelects[46]
                            && swatchSelects[53] && !swatchSelects[12] && !swatchSelects[59]) ||
                    (swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[38] && swatchSelects[46]
                            && swatchSelects[53] && !swatchSelects[5] && !swatchSelects[59]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[38]
                            && swatchSelects[59] && !swatchSelects[46] && !swatchSelects[53]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[46]
                            && swatchSelects[59] && !swatchSelects[38] && !swatchSelects[53]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[38] && swatchSelects[46]
                            && swatchSelects[59] && !swatchSelects[29] && !swatchSelects[53]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[29] && swatchSelects[38] && swatchSelects[46]
                            && swatchSelects[59] && !swatchSelects[20] && !swatchSelects[53]) ||
                    (swatchSelects[5] && swatchSelects[20] && swatchSelects[29] && swatchSelects[38] && swatchSelects[46]
                            && swatchSelects[59] && !swatchSelects[12] && !swatchSelects[53]) ||
                    (swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[38] && swatchSelects[46]
                            && swatchSelects[59] && !swatchSelects[5] && !swatchSelects[53]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[53]
                            && swatchSelects[59] && !swatchSelects[38] && !swatchSelects[46]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[38] && swatchSelects[53]
                            && swatchSelects[59] && !swatchSelects[29] && !swatchSelects[46]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[29] && swatchSelects[38] && swatchSelects[53]
                            && swatchSelects[59] && !swatchSelects[20] && !swatchSelects[46]) ||
                    (swatchSelects[5] && swatchSelects[20] && swatchSelects[29] && swatchSelects[38] && swatchSelects[53]
                            && swatchSelects[59] && !swatchSelects[12] && !swatchSelects[46]) ||
                    (swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[38] && swatchSelects[53]
                            && swatchSelects[59] && !swatchSelects[5] && !swatchSelects[46]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[46] && swatchSelects[53]
                            && swatchSelects[59] && !swatchSelects[29] && !swatchSelects[38]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[29] && swatchSelects[46] && swatchSelects[53]
                            && swatchSelects[59] && !swatchSelects[20] && !swatchSelects[38]) ||
                    (swatchSelects[5] && swatchSelects[20] && swatchSelects[29] && swatchSelects[46] && swatchSelects[53]
                            && swatchSelects[59] && !swatchSelects[12] && !swatchSelects[38]) ||
                    (swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[46] && swatchSelects[53]
                            && swatchSelects[59] && !swatchSelects[5] && !swatchSelects[38]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[38] && swatchSelects[46] && swatchSelects[53]
                            && swatchSelects[59] && !swatchSelects[20] && !swatchSelects[29]) ||
                    (swatchSelects[5] && swatchSelects[20] && swatchSelects[38] && swatchSelects[46] && swatchSelects[53]
                            && swatchSelects[59] && !swatchSelects[12] && !swatchSelects[29]) ||
                    (swatchSelects[12] && swatchSelects[20] && swatchSelects[38] && swatchSelects[46] && swatchSelects[53]
                            && swatchSelects[59] && !swatchSelects[5] && !swatchSelects[29]) ||
                    (swatchSelects[5] && swatchSelects[29] && swatchSelects[38] && swatchSelects[46] && swatchSelects[53]
                            && swatchSelects[59] && !swatchSelects[12] && !swatchSelects[20]) ||
                    (swatchSelects[12] && swatchSelects[29] && swatchSelects[38] && swatchSelects[46] && swatchSelects[53]
                            && swatchSelects[59] && !swatchSelects[5] && !swatchSelects[20]) ||
                    (swatchSelects[20] && swatchSelects[29] && swatchSelects[38] && swatchSelects[46] && swatchSelects[53]
                            && swatchSelects[59] && !swatchSelects[5] && !swatchSelects[12])) {
                int a[] = {5, 12, 20, 29, 38, 46, 53, 59};
                i = pickUnSelected(a);
            } else if ((swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30]
                    && swatchSelects[31] && swatchSelects[32] && !swatchSelects[33] && !swatchSelects[34]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30]
                            && swatchSelects[31] && swatchSelects[33] && !swatchSelects[32] && !swatchSelects[34]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30]
                            && swatchSelects[32] && swatchSelects[33] && !swatchSelects[31] && !swatchSelects[34]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[31]
                            && swatchSelects[32] && swatchSelects[33] && !swatchSelects[30] && !swatchSelects[34]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[30] && swatchSelects[31]
                            && swatchSelects[32] && swatchSelects[33] && !swatchSelects[29] && !swatchSelects[34]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[29] && swatchSelects[30] && swatchSelects[31]
                            && swatchSelects[32] && swatchSelects[33] && !swatchSelects[28] && !swatchSelects[34]) ||
                    (swatchSelects[26] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30] && swatchSelects[31]
                            && swatchSelects[32] && swatchSelects[33] && !swatchSelects[27] && !swatchSelects[34]) ||
                    (swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30] && swatchSelects[31]
                            && swatchSelects[32] && swatchSelects[33] && !swatchSelects[26] && !swatchSelects[34]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30]
                            && swatchSelects[31] && swatchSelects[34] && !swatchSelects[32] && !swatchSelects[33]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30]
                            && swatchSelects[32] && swatchSelects[34] && !swatchSelects[31] && !swatchSelects[33]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[31]
                            && swatchSelects[32] && swatchSelects[34] && !swatchSelects[30] && !swatchSelects[33]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[30] && swatchSelects[31]
                            && swatchSelects[32] && swatchSelects[34] && !swatchSelects[29] && !swatchSelects[33]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[29] && swatchSelects[30] && swatchSelects[31]
                            && swatchSelects[32] && swatchSelects[34] && !swatchSelects[28] && !swatchSelects[33]) ||
                    (swatchSelects[26] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30] && swatchSelects[31]
                            && swatchSelects[32] && swatchSelects[34] && !swatchSelects[27] && !swatchSelects[33]) ||
                    (swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30] && swatchSelects[31]
                            && swatchSelects[32] && swatchSelects[34] && !swatchSelects[26] && !swatchSelects[33]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[31] && !swatchSelects[32]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[31]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[30] && !swatchSelects[32]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[30] && swatchSelects[31]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[29] && !swatchSelects[32]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[29] && swatchSelects[30] && swatchSelects[31]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[28] && !swatchSelects[32]) ||
                    (swatchSelects[26] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30] && swatchSelects[31]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[27] && !swatchSelects[32]) ||
                    (swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30] && swatchSelects[31]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[26] && !swatchSelects[32]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[32]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[30] && !swatchSelects[31]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[30] && swatchSelects[32]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[29] && !swatchSelects[31]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[29] && swatchSelects[30] && swatchSelects[32]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[28] && !swatchSelects[31]) ||
                    (swatchSelects[26] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30] && swatchSelects[32]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[27] && !swatchSelects[31]) ||
                    (swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[30] && swatchSelects[32]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[26] && !swatchSelects[31]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[28] && swatchSelects[31] && swatchSelects[32]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[29] && !swatchSelects[30]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[29] && swatchSelects[31] && swatchSelects[32]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[28] && !swatchSelects[30]) ||
                    (swatchSelects[26] && swatchSelects[28] && swatchSelects[29] && swatchSelects[31] && swatchSelects[32]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[27] && !swatchSelects[30]) ||
                    (swatchSelects[27] && swatchSelects[28] && swatchSelects[29] && swatchSelects[31] && swatchSelects[32]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[26] && !swatchSelects[30]) ||
                    (swatchSelects[26] && swatchSelects[27] && swatchSelects[30] && swatchSelects[31] && swatchSelects[32]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[28] && !swatchSelects[29]) ||
                    (swatchSelects[26] && swatchSelects[28] && swatchSelects[30] && swatchSelects[31] && swatchSelects[32]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[27] && !swatchSelects[29]) ||
                    (swatchSelects[27] && swatchSelects[28] && swatchSelects[30] && swatchSelects[31] && swatchSelects[32]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[26] && !swatchSelects[29]) ||
                    (swatchSelects[26] && swatchSelects[29] && swatchSelects[30] && swatchSelects[31] && swatchSelects[32]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[27] && !swatchSelects[28]) ||
                    (swatchSelects[27] && swatchSelects[29] && swatchSelects[30] && swatchSelects[31] && swatchSelects[32]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[26] && !swatchSelects[28]) ||
                    (swatchSelects[28] && swatchSelects[29] && swatchSelects[30] && swatchSelects[31] && swatchSelects[32]
                            && swatchSelects[33] && swatchSelects[34] && !swatchSelects[26] && !swatchSelects[27])) {
                int a[] = {26, 27, 28, 29, 30, 31, 32, 33, 34};
                i = pickUnSelected(a);
            } else if ((swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30]
                    && swatchSelects[38] && swatchSelects[45] && !swatchSelects[51] && !swatchSelects[56]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30]
                            && swatchSelects[38] && swatchSelects[51] && !swatchSelects[45] && !swatchSelects[56]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30]
                            && swatchSelects[45] && swatchSelects[51] && !swatchSelects[38] && !swatchSelects[56]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[38]
                            && swatchSelects[45] && swatchSelects[51] && !swatchSelects[30] && !swatchSelects[56]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[30] && swatchSelects[38]
                            && swatchSelects[45] && swatchSelects[51] && !swatchSelects[22] && !swatchSelects[56]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[22] && swatchSelects[30] && swatchSelects[38]
                            && swatchSelects[45] && swatchSelects[51] && !swatchSelects[15] && !swatchSelects[56]) ||
                    (swatchSelects[4] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30] && swatchSelects[38]
                            && swatchSelects[45] && swatchSelects[51] && !swatchSelects[9] && !swatchSelects[56]) ||
                    (swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30] && swatchSelects[38]
                            && swatchSelects[45] && swatchSelects[51] && !swatchSelects[4] && !swatchSelects[56]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30]
                            && swatchSelects[38] && swatchSelects[56] && !swatchSelects[45] && !swatchSelects[51]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30]
                            && swatchSelects[45] && swatchSelects[56] && !swatchSelects[38] && !swatchSelects[51]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[38]
                            && swatchSelects[45] && swatchSelects[56] && !swatchSelects[30] && !swatchSelects[51]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[30] && swatchSelects[38]
                            && swatchSelects[45] && swatchSelects[56] && !swatchSelects[22] && !swatchSelects[51]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[22] && swatchSelects[30] && swatchSelects[38]
                            && swatchSelects[45] && swatchSelects[56] && !swatchSelects[15] && !swatchSelects[51]) ||
                    (swatchSelects[4] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30] && swatchSelects[38]
                            && swatchSelects[45] && swatchSelects[56] && !swatchSelects[9] && !swatchSelects[51]) ||
                    (swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30] && swatchSelects[38]
                            && swatchSelects[45] && swatchSelects[56] && !swatchSelects[4] && !swatchSelects[51]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[38] && !swatchSelects[45]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[38]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[30] && !swatchSelects[45]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[30] && swatchSelects[38]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[22] && !swatchSelects[45]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[22] && swatchSelects[30] && swatchSelects[38]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[15] && !swatchSelects[45]) ||
                    (swatchSelects[4] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30] && swatchSelects[38]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[9] && !swatchSelects[45]) ||
                    (swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30] && swatchSelects[38]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[4] && !swatchSelects[45]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[45]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[30] && !swatchSelects[38]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[30] && swatchSelects[45]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[22] && !swatchSelects[38]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[22] && swatchSelects[30] && swatchSelects[45]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[15] && !swatchSelects[38]) ||
                    (swatchSelects[4] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30] && swatchSelects[45]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[9] && !swatchSelects[38]) ||
                    (swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[30] && swatchSelects[45]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[4] && !swatchSelects[38]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[15] && swatchSelects[38] && swatchSelects[45]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[22] && !swatchSelects[30]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[22] && swatchSelects[38] && swatchSelects[45]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[15] && !swatchSelects[30]) ||
                    (swatchSelects[4] && swatchSelects[15] && swatchSelects[22] && swatchSelects[38] && swatchSelects[45]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[9] && !swatchSelects[30]) ||
                    (swatchSelects[9] && swatchSelects[15] && swatchSelects[22] && swatchSelects[38] && swatchSelects[45]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[4] && !swatchSelects[30]) ||
                    (swatchSelects[4] && swatchSelects[9] && swatchSelects[30] && swatchSelects[38] && swatchSelects[45]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[15] && !swatchSelects[22]) ||
                    (swatchSelects[4] && swatchSelects[15] && swatchSelects[30] && swatchSelects[38] && swatchSelects[45]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[9] && !swatchSelects[22]) ||
                    (swatchSelects[9] && swatchSelects[15] && swatchSelects[30] && swatchSelects[38] && swatchSelects[45]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[4] && !swatchSelects[22]) ||
                    (swatchSelects[4] && swatchSelects[22] && swatchSelects[30] && swatchSelects[38] && swatchSelects[45]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[9] && !swatchSelects[15]) ||
                    (swatchSelects[9] && swatchSelects[22] && swatchSelects[30] && swatchSelects[38] && swatchSelects[45]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[4] && !swatchSelects[15]) ||
                    (swatchSelects[15] && swatchSelects[22] && swatchSelects[30] && swatchSelects[38] && swatchSelects[45]
                            && swatchSelects[51] && swatchSelects[56] && !swatchSelects[4] && !swatchSelects[9])) {
                int a[] = {4, 9, 15, 22, 30, 38, 45, 51, 56};
                i = pickUnSelected(a);
            } else if ((swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30]
                    && swatchSelects[39] && swatchSelects[47] && !swatchSelects[54] && !swatchSelects[60]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30]
                            && swatchSelects[39] && swatchSelects[54] && !swatchSelects[47] && !swatchSelects[60]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30]
                            && swatchSelects[47] && swatchSelects[54] && !swatchSelects[39] && !swatchSelects[60]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[39]
                            && swatchSelects[47] && swatchSelects[54] && !swatchSelects[30] && !swatchSelects[60]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[30] && swatchSelects[39]
                            && swatchSelects[47] && swatchSelects[54] && !swatchSelects[21] && !swatchSelects[60]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[21] && swatchSelects[30] && swatchSelects[39]
                            && swatchSelects[47] && swatchSelects[54] && !swatchSelects[13] && !swatchSelects[60]) ||
                    (swatchSelects[0] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] && swatchSelects[39]
                            && swatchSelects[47] && swatchSelects[54] && !swatchSelects[6] && !swatchSelects[60]) ||
                    (swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] && swatchSelects[39]
                            && swatchSelects[47] && swatchSelects[54] && !swatchSelects[0] && !swatchSelects[60]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30]
                            && swatchSelects[39] && swatchSelects[60] && !swatchSelects[47] && !swatchSelects[54]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30]
                            && swatchSelects[47] && swatchSelects[60] && !swatchSelects[39] && !swatchSelects[54]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[39]
                            && swatchSelects[47] && swatchSelects[60] && !swatchSelects[30] && !swatchSelects[54]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[30] && swatchSelects[39]
                            && swatchSelects[47] && swatchSelects[60] && !swatchSelects[21] && !swatchSelects[54]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[21] && swatchSelects[30] && swatchSelects[39]
                            && swatchSelects[47] && swatchSelects[60] && !swatchSelects[13] && !swatchSelects[54]) ||
                    (swatchSelects[0] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] && swatchSelects[39]
                            && swatchSelects[47] && swatchSelects[60] && !swatchSelects[6] && !swatchSelects[54]) ||
                    (swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] && swatchSelects[39]
                            && swatchSelects[47] && swatchSelects[60] && !swatchSelects[0] && !swatchSelects[54]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[39] && !swatchSelects[47]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[39]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[30] && !swatchSelects[47]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[30] && swatchSelects[39]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[21] && !swatchSelects[47]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[21] && swatchSelects[30] && swatchSelects[39]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[13] && !swatchSelects[47]) ||
                    (swatchSelects[0] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] && swatchSelects[39]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[6] && !swatchSelects[47]) ||
                    (swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] && swatchSelects[39]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[0] && !swatchSelects[47]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[47]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[30] && !swatchSelects[39]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[30] && swatchSelects[47]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[21] && !swatchSelects[39]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[21] && swatchSelects[30] && swatchSelects[47]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[13] && !swatchSelects[39]) ||
                    (swatchSelects[0] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] && swatchSelects[47]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[6] && !swatchSelects[39]) ||
                    (swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30] && swatchSelects[47]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[0] && !swatchSelects[39]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[39] && swatchSelects[47]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[21] && !swatchSelects[30]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[21] && swatchSelects[39] && swatchSelects[47]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[13] && !swatchSelects[30]) ||
                    (swatchSelects[0] && swatchSelects[13] && swatchSelects[21] && swatchSelects[39] && swatchSelects[47]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[6] && !swatchSelects[30]) ||
                    (swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[39] && swatchSelects[47]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[0] && !swatchSelects[30]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[30] && swatchSelects[39] && swatchSelects[47]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[13] && !swatchSelects[21]) ||
                    (swatchSelects[0] && swatchSelects[13] && swatchSelects[30] && swatchSelects[39] && swatchSelects[47]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[6] && !swatchSelects[21]) ||
                    (swatchSelects[6] && swatchSelects[13] && swatchSelects[30] && swatchSelects[39] && swatchSelects[47]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[0] && !swatchSelects[21]) ||
                    (swatchSelects[0] && swatchSelects[21] && swatchSelects[30] && swatchSelects[39] && swatchSelects[47]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[6] && !swatchSelects[13]) ||
                    (swatchSelects[6] && swatchSelects[21] && swatchSelects[30] && swatchSelects[39] && swatchSelects[47]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[0] && !swatchSelects[13]) ||
                    (swatchSelects[13] && swatchSelects[21] && swatchSelects[30] && swatchSelects[39] && swatchSelects[47]
                            && swatchSelects[54] && swatchSelects[60] && !swatchSelects[0] && !swatchSelects[6])) {
                int a[] = {0, 6, 13, 21, 30, 39, 47, 54, 60};
                i = pickUnSelected(a);
            }
        }

        if (i >= 0) {
            return i;
        } else {
            return -1;
        }
    }

    private int preventStep2_3() {
        int i = -1;
        if (boardRadius == 5) {
            if ((swatchSelects[0] && swatchSelects[1] && swatchSelects[2] && swatchSelects[3]
                    && !swatchSelects[4] && !swatchSelects[4]) ||
                    (swatchSelects[0] && swatchSelects[1] && swatchSelects[2] && swatchSelects[4]
                            && !swatchSelects[3] && !swatchSelects[4]) ||
                    (swatchSelects[0] && swatchSelects[1] && swatchSelects[3] && swatchSelects[4]
                            && !swatchSelects[2] && !swatchSelects[4]) ||
                    (swatchSelects[0] && swatchSelects[2] && swatchSelects[3] && swatchSelects[4]
                            && !swatchSelects[1] && !swatchSelects[4]) ||
                    (swatchSelects[1] && swatchSelects[2] && swatchSelects[3] && swatchSelects[4]
                            && !swatchSelects[0] && !swatchSelects[4]) ||
                    (swatchSelects[0] && swatchSelects[1] && swatchSelects[2] && swatchSelects[4]
                            && !swatchSelects[3] && !swatchSelects[4]) ||
                    (swatchSelects[0] && swatchSelects[1] && swatchSelects[3] && swatchSelects[4]
                            && !swatchSelects[2] && !swatchSelects[4]) ||
                    (swatchSelects[0] && swatchSelects[2] && swatchSelects[3] && swatchSelects[4]
                            && !swatchSelects[1] && !swatchSelects[4]) ||
                    (swatchSelects[1] && swatchSelects[2] && swatchSelects[3] && swatchSelects[4]
                            && !swatchSelects[0] && !swatchSelects[4]) ||
                    (swatchSelects[0] && swatchSelects[1] && swatchSelects[4] && swatchSelects[4]
                            && !swatchSelects[2] && !swatchSelects[3]) ||
                    (swatchSelects[0] && swatchSelects[2] && swatchSelects[4] && swatchSelects[4]
                            && !swatchSelects[1] && !swatchSelects[3]) ||
                    (swatchSelects[1] && swatchSelects[2] && swatchSelects[4] && swatchSelects[4]
                            && !swatchSelects[0] && !swatchSelects[3]) ||
                    (swatchSelects[0] && swatchSelects[3] && swatchSelects[4] && swatchSelects[4]
                            && !swatchSelects[1] && !swatchSelects[2]) ||
                    (swatchSelects[1] && swatchSelects[3] && swatchSelects[4] && swatchSelects[4]
                            && !swatchSelects[0] && !swatchSelects[2]) ||
                    (swatchSelects[2] && swatchSelects[3] && swatchSelects[4] && swatchSelects[4]
                            && !swatchSelects[0] && !swatchSelects[1])) {
                int a[] = {0, 1, 2, 3, 4, 5};
                i = pickUnSelected(a);
            } else if ((swatchSelects[85] && swatchSelects[86] && swatchSelects[87] && swatchSelects[88]
                    && !swatchSelects[89] && !swatchSelects[90]) ||
                    (swatchSelects[85] && swatchSelects[86] && swatchSelects[87] && swatchSelects[89]
                            && !swatchSelects[88] && !swatchSelects[90]) ||
                    (swatchSelects[85] && swatchSelects[86] && swatchSelects[88] && swatchSelects[89]
                            && !swatchSelects[87] && !swatchSelects[90]) ||
                    (swatchSelects[85] && swatchSelects[87] && swatchSelects[88] && swatchSelects[89]
                            && !swatchSelects[86] && !swatchSelects[90]) ||
                    (swatchSelects[86] && swatchSelects[87] && swatchSelects[88] && swatchSelects[89]
                            && !swatchSelects[85] && !swatchSelects[90]) ||
                    (swatchSelects[85] && swatchSelects[86] && swatchSelects[87] && swatchSelects[90]
                            && !swatchSelects[88] && !swatchSelects[89]) ||
                    (swatchSelects[85] && swatchSelects[86] && swatchSelects[88] && swatchSelects[90]
                            && !swatchSelects[87] && !swatchSelects[89]) ||
                    (swatchSelects[85] && swatchSelects[87] && swatchSelects[88] && swatchSelects[90]
                            && !swatchSelects[86] && !swatchSelects[89]) ||
                    (swatchSelects[86] && swatchSelects[87] && swatchSelects[88] && swatchSelects[90]
                            && !swatchSelects[85] && !swatchSelects[89]) ||
                    (swatchSelects[85] && swatchSelects[86] && swatchSelects[89] && swatchSelects[90]
                            && !swatchSelects[87] && !swatchSelects[88]) ||
                    (swatchSelects[85] && swatchSelects[87] && swatchSelects[89] && swatchSelects[90]
                            && !swatchSelects[86] && !swatchSelects[88]) ||
                    (swatchSelects[86] && swatchSelects[87] && swatchSelects[89] && swatchSelects[90]
                            && !swatchSelects[85] && !swatchSelects[88]) ||
                    (swatchSelects[85] && swatchSelects[88] && swatchSelects[89] && swatchSelects[90]
                            && !swatchSelects[86] && !swatchSelects[87]) ||
                    (swatchSelects[86] && swatchSelects[88] && swatchSelects[89] && swatchSelects[90]
                            && !swatchSelects[85] && !swatchSelects[87]) ||
                    (swatchSelects[87] && swatchSelects[88] && swatchSelects[89] && swatchSelects[90]
                            && !swatchSelects[85] && !swatchSelects[86])) {
                int a[] = {85, 86, 87, 88, 89, 90};
                i = pickUnSelected(a);
            } else if ((swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[21]
                    && !swatchSelects[30] && !swatchSelects[40]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[30]
                            && !swatchSelects[21] && !swatchSelects[40]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[21] && swatchSelects[30]
                            && !swatchSelects[13] && !swatchSelects[40]) ||
                    (swatchSelects[0] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30]
                            && !swatchSelects[6] && !swatchSelects[40]) ||
                    (swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[30]
                            && !swatchSelects[0] && !swatchSelects[40]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[13] && swatchSelects[40]
                            && !swatchSelects[21] && !swatchSelects[30]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[21] && swatchSelects[40]
                            && !swatchSelects[13] && !swatchSelects[30]) ||
                    (swatchSelects[0] && swatchSelects[13] && swatchSelects[21] && swatchSelects[40]
                            && !swatchSelects[6] && !swatchSelects[30]) ||
                    (swatchSelects[6] && swatchSelects[13] && swatchSelects[21] && swatchSelects[40]
                            && !swatchSelects[0] && !swatchSelects[30]) ||
                    (swatchSelects[0] && swatchSelects[6] && swatchSelects[30] && swatchSelects[40]
                            && !swatchSelects[13] && !swatchSelects[21]) ||
                    (swatchSelects[0] && swatchSelects[13] && swatchSelects[30] && swatchSelects[40]
                            && !swatchSelects[6] && !swatchSelects[21]) ||
                    (swatchSelects[6] && swatchSelects[13] && swatchSelects[30] && swatchSelects[40]
                            && !swatchSelects[0] && !swatchSelects[21]) ||
                    (swatchSelects[0] && swatchSelects[21] && swatchSelects[30] && swatchSelects[40]
                            && !swatchSelects[6] && !swatchSelects[13]) ||
                    (swatchSelects[6] && swatchSelects[21] && swatchSelects[30] && swatchSelects[40]
                            && !swatchSelects[0] && !swatchSelects[13]) ||
                    (swatchSelects[13] && swatchSelects[21] && swatchSelects[30] && swatchSelects[40]
                            && !swatchSelects[0] && !swatchSelects[6])) {
                int a[] = {0, 6, 13, 21, 30, 40};
                i = pickUnSelected(a);
            } else if ((swatchSelects[50] && swatchSelects[60] && swatchSelects[69] && swatchSelects[77]
                    && !swatchSelects[84] && !swatchSelects[90]) ||
                    (swatchSelects[50] && swatchSelects[60] && swatchSelects[69] && swatchSelects[84]
                            && !swatchSelects[77] && !swatchSelects[90]) ||
                    (swatchSelects[50] && swatchSelects[60] && swatchSelects[77] && swatchSelects[84]
                            && !swatchSelects[69] && !swatchSelects[90]) ||
                    (swatchSelects[50] && swatchSelects[69] && swatchSelects[77] && swatchSelects[84]
                            && !swatchSelects[60] && !swatchSelects[90]) ||
                    (swatchSelects[60] && swatchSelects[69] && swatchSelects[77] && swatchSelects[84]
                            && !swatchSelects[50] && !swatchSelects[90]) ||
                    (swatchSelects[50] && swatchSelects[60] && swatchSelects[69] && swatchSelects[90]
                            && !swatchSelects[77] && !swatchSelects[84]) ||
                    (swatchSelects[50] && swatchSelects[60] && swatchSelects[77] && swatchSelects[90]
                            && !swatchSelects[69] && !swatchSelects[84]) ||
                    (swatchSelects[50] && swatchSelects[69] && swatchSelects[77] && swatchSelects[90]
                            && !swatchSelects[60] && !swatchSelects[84]) ||
                    (swatchSelects[60] && swatchSelects[69] && swatchSelects[77] && swatchSelects[90]
                            && !swatchSelects[50] && !swatchSelects[84]) ||
                    (swatchSelects[50] && swatchSelects[60] && swatchSelects[84] && swatchSelects[90]
                            && !swatchSelects[69] && !swatchSelects[77]) ||
                    (swatchSelects[50] && swatchSelects[69] && swatchSelects[84] && swatchSelects[90]
                            && !swatchSelects[60] && !swatchSelects[77]) ||
                    (swatchSelects[60] && swatchSelects[69] && swatchSelects[84] && swatchSelects[90]
                            && !swatchSelects[50] && !swatchSelects[77]) ||
                    (swatchSelects[50] && swatchSelects[77] && swatchSelects[84] && swatchSelects[90]
                            && !swatchSelects[60] && !swatchSelects[69]) ||
                    (swatchSelects[60] && swatchSelects[77] && swatchSelects[84] && swatchSelects[90]
                            && !swatchSelects[50] && !swatchSelects[69]) ||
                    (swatchSelects[69] && swatchSelects[77] && swatchSelects[84] && swatchSelects[90]
                            && !swatchSelects[50] && !swatchSelects[60])) {
                int a[] = {50, 60, 69, 77, 84, 90};
                i = pickUnSelected(a);
            } else if ((swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[29]
                    && !swatchSelects[39] && !swatchSelects[50]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[39]
                            && !swatchSelects[29] && !swatchSelects[50]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[29] && swatchSelects[39]
                            && !swatchSelects[20] && !swatchSelects[50]) ||
                    (swatchSelects[5] && swatchSelects[20] && swatchSelects[29] && swatchSelects[39]
                            && !swatchSelects[12] && !swatchSelects[50]) ||
                    (swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[39]
                            && !swatchSelects[5] && !swatchSelects[50]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[20] && swatchSelects[50]
                            && !swatchSelects[29] && !swatchSelects[39]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[29] && swatchSelects[50]
                            && !swatchSelects[20] && !swatchSelects[39]) ||
                    (swatchSelects[5] && swatchSelects[20] && swatchSelects[29] && swatchSelects[50]
                            && !swatchSelects[12] && !swatchSelects[39]) ||
                    (swatchSelects[12] && swatchSelects[20] && swatchSelects[29] && swatchSelects[50]
                            && !swatchSelects[5] && !swatchSelects[39]) ||
                    (swatchSelects[5] && swatchSelects[12] && swatchSelects[39] && swatchSelects[50]
                            && !swatchSelects[20] && !swatchSelects[29]) ||
                    (swatchSelects[5] && swatchSelects[20] && swatchSelects[39] && swatchSelects[50]
                            && !swatchSelects[12] && !swatchSelects[29]) ||
                    (swatchSelects[12] && swatchSelects[20] && swatchSelects[39] && swatchSelects[50]
                            && !swatchSelects[5] && !swatchSelects[29]) ||
                    (swatchSelects[5] && swatchSelects[29] && swatchSelects[39] && swatchSelects[50]
                            && !swatchSelects[12] && !swatchSelects[20]) ||
                    (swatchSelects[12] && swatchSelects[29] && swatchSelects[39] && swatchSelects[50]
                            && !swatchSelects[5] && !swatchSelects[20]) ||
                    (swatchSelects[20] && swatchSelects[29] && swatchSelects[39] && swatchSelects[50]
                            && !swatchSelects[5] && !swatchSelects[12])) {
                int a[] = {5, 12, 20, 29, 39, 50};
                i = pickUnSelected(a);
            } else if ((swatchSelects[40] && swatchSelects[51] && swatchSelects[61] && swatchSelects[70]
                    && !swatchSelects[78] && !swatchSelects[85]) ||
                    (swatchSelects[40] && swatchSelects[51] && swatchSelects[61] && swatchSelects[78]
                            && !swatchSelects[70] && !swatchSelects[85]) ||
                    (swatchSelects[40] && swatchSelects[51] && swatchSelects[70] && swatchSelects[78]
                            && !swatchSelects[61] && !swatchSelects[85]) ||
                    (swatchSelects[40] && swatchSelects[61] && swatchSelects[70] && swatchSelects[78]
                            && !swatchSelects[51] && !swatchSelects[85]) ||
                    (swatchSelects[51] && swatchSelects[61] && swatchSelects[70] && swatchSelects[78]
                            && !swatchSelects[40] && !swatchSelects[85]) ||
                    (swatchSelects[40] && swatchSelects[51] && swatchSelects[61] && swatchSelects[85]
                            && !swatchSelects[70] && !swatchSelects[78]) ||
                    (swatchSelects[40] && swatchSelects[51] && swatchSelects[70] && swatchSelects[85]
                            && !swatchSelects[61] && !swatchSelects[78]) ||
                    (swatchSelects[40] && swatchSelects[61] && swatchSelects[70] && swatchSelects[85]
                            && !swatchSelects[51] && !swatchSelects[78]) ||
                    (swatchSelects[51] && swatchSelects[61] && swatchSelects[70] && swatchSelects[85]
                            && !swatchSelects[40] && !swatchSelects[78]) ||
                    (swatchSelects[40] && swatchSelects[51] && swatchSelects[78] && swatchSelects[85]
                            && !swatchSelects[61] && !swatchSelects[70]) ||
                    (swatchSelects[40] && swatchSelects[61] && swatchSelects[78] && swatchSelects[85]
                            && !swatchSelects[51] && !swatchSelects[70]) ||
                    (swatchSelects[51] && swatchSelects[61] && swatchSelects[78] && swatchSelects[85]
                            && !swatchSelects[40] && !swatchSelects[70]) ||
                    (swatchSelects[40] && swatchSelects[70] && swatchSelects[78] && swatchSelects[85]
                            && !swatchSelects[51] && !swatchSelects[61]) ||
                    (swatchSelects[51] && swatchSelects[70] && swatchSelects[78] && swatchSelects[85]
                            && !swatchSelects[40] && !swatchSelects[61]) ||
                    (swatchSelects[61] && swatchSelects[70] && swatchSelects[78] && swatchSelects[85]
                            && !swatchSelects[40] && !swatchSelects[51])) {
                int a[] = {40, 51, 61, 70, 78, 85};
                i = pickUnSelected(a);
            } else if ((swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && swatchSelects[9]
                    && swatchSelects[10] && !swatchSelects[11] && !swatchSelects[12]) ||
                    (swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && swatchSelects[9]
                            && swatchSelects[11] && !swatchSelects[10] && !swatchSelects[12]) ||
                    (swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && swatchSelects[10]
                            && swatchSelects[11] && !swatchSelects[9] && !swatchSelects[12]) ||
                    (swatchSelects[6] && swatchSelects[7] && swatchSelects[9] && swatchSelects[10]
                            && swatchSelects[11] && !swatchSelects[8] && !swatchSelects[12]) ||
                    (swatchSelects[6] && swatchSelects[8] && swatchSelects[9] && swatchSelects[10]
                            && swatchSelects[11] && !swatchSelects[7] && !swatchSelects[12]) ||
                    (swatchSelects[7] && swatchSelects[8] && swatchSelects[9] && swatchSelects[10]
                            && swatchSelects[11] && !swatchSelects[6] && !swatchSelects[12]) ||
                    (swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && swatchSelects[9]
                            && swatchSelects[12] && !swatchSelects[10] && !swatchSelects[11]) ||
                    (swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && swatchSelects[10]
                            && swatchSelects[12] && !swatchSelects[9] && !swatchSelects[11]) ||
                    (swatchSelects[6] && swatchSelects[7] && swatchSelects[9] && swatchSelects[10]
                            && swatchSelects[12] && !swatchSelects[8] && !swatchSelects[11]) ||
                    (swatchSelects[6] && swatchSelects[8] && swatchSelects[9] && swatchSelects[10]
                            && swatchSelects[12] && !swatchSelects[7] && !swatchSelects[11]) ||
                    (swatchSelects[7] && swatchSelects[8] && swatchSelects[9] && swatchSelects[10]
                            && swatchSelects[12] && !swatchSelects[6] && !swatchSelects[11]) ||
                    (swatchSelects[6] && swatchSelects[7] && swatchSelects[8] && swatchSelects[11]
                            && swatchSelects[12] && !swatchSelects[9] && !swatchSelects[10]) ||
                    (swatchSelects[6] && swatchSelects[7] && swatchSelects[9] && swatchSelects[11]
                            && swatchSelects[12] && !swatchSelects[8] && !swatchSelects[10]) ||
                    (swatchSelects[6] && swatchSelects[8] && swatchSelects[9] && swatchSelects[11]
                            && swatchSelects[12] && !swatchSelects[7] && !swatchSelects[10]) ||
                    (swatchSelects[7] && swatchSelects[8] && swatchSelects[9] && swatchSelects[11]
                            && swatchSelects[12] && !swatchSelects[6] && !swatchSelects[10]) ||
                    (swatchSelects[6] && swatchSelects[7] && swatchSelects[10] && swatchSelects[11]
                            && swatchSelects[12] && !swatchSelects[8] && !swatchSelects[9]) ||
                    (swatchSelects[6] && swatchSelects[8] && swatchSelects[10] && swatchSelects[11]
                            && swatchSelects[12] && !swatchSelects[7] && !swatchSelects[9]) ||
                    (swatchSelects[7] && swatchSelects[8] && swatchSelects[10] && swatchSelects[11]
                            && swatchSelects[12] && !swatchSelects[6] && !swatchSelects[9]) ||
                    (swatchSelects[6] && swatchSelects[9] && swatchSelects[10] && swatchSelects[11]
                            && swatchSelects[12] && !swatchSelects[7] && !swatchSelects[8]) ||
                    (swatchSelects[7] && swatchSelects[9] && swatchSelects[10] && swatchSelects[11]
                            && swatchSelects[12] && !swatchSelects[6] && !swatchSelects[8]) ||
                    (swatchSelects[8] && swatchSelects[9] && swatchSelects[10] && swatchSelects[11]
                            && swatchSelects[12] && !swatchSelects[6] && !swatchSelects[7])) {
                int a[] = {6, 7, 8, 9, 10, 11, 12};
                i = pickUnSelected(a);
            } else if ((swatchSelects[78] && swatchSelects[79] && swatchSelects[80] && swatchSelects[81]
                    && swatchSelects[82] && !swatchSelects[83] && !swatchSelects[84]) ||
                    (swatchSelects[78] && swatchSelects[79] && swatchSelects[80] && swatchSelects[81]
                            && swatchSelects[83] && !swatchSelects[82] && !swatchSelects[84]) ||
                    (swatchSelects[78] && swatchSelects[79] && swatchSelects[80] && swatchSelects[82]
                            && swatchSelects[83] && !swatchSelects[81] && !swatchSelects[84]) ||
                    (swatchSelects[78] && swatchSelects[79] && swatchSelects[81] && swatchSelects[82]
                            && swatchSelects[83] && !swatchSelects[80] && !swatchSelects[84]) ||
                    (swatchSelects[78] && swatchSelects[80] && swatchSelects[81] && swatchSelects[82]
                            && swatchSelects[83] && !swatchSelects[79] && !swatchSelects[84]) ||
                    (swatchSelects[79] && swatchSelects[80] && swatchSelects[81] && swatchSelects[82]
                            && swatchSelects[83] && !swatchSelects[78] && !swatchSelects[84]) ||
                    (swatchSelects[78] && swatchSelects[79] && swatchSelects[80] && swatchSelects[81]
                            && swatchSelects[84] && !swatchSelects[82] && !swatchSelects[83]) ||
                    (swatchSelects[78] && swatchSelects[79] && swatchSelects[80] && swatchSelects[82]
                            && swatchSelects[84] && !swatchSelects[81] && !swatchSelects[83]) ||
                    (swatchSelects[78] && swatchSelects[79] && swatchSelects[81] && swatchSelects[82]
                            && swatchSelects[84] && !swatchSelects[80] && !swatchSelects[83]) ||
                    (swatchSelects[78] && swatchSelects[80] && swatchSelects[81] && swatchSelects[82]
                            && swatchSelects[84] && !swatchSelects[79] && !swatchSelects[83]) ||
                    (swatchSelects[79] && swatchSelects[80] && swatchSelects[81] && swatchSelects[82]
                            && swatchSelects[84] && !swatchSelects[78] && !swatchSelects[83]) ||
                    (swatchSelects[78] && swatchSelects[79] && swatchSelects[80] && swatchSelects[83]
                            && swatchSelects[84] && !swatchSelects[81] && !swatchSelects[82]) ||
                    (swatchSelects[78] && swatchSelects[79] && swatchSelects[81] && swatchSelects[83]
                            && swatchSelects[84] && !swatchSelects[80] && !swatchSelects[82]) ||
                    (swatchSelects[78] && swatchSelects[80] && swatchSelects[81] && swatchSelects[83]
                            && swatchSelects[84] && !swatchSelects[79] && !swatchSelects[82]) ||
                    (swatchSelects[79] && swatchSelects[80] && swatchSelects[81] && swatchSelects[83]
                            && swatchSelects[84] && !swatchSelects[78] && !swatchSelects[82]) ||
                    (swatchSelects[78] && swatchSelects[79] && swatchSelects[82] && swatchSelects[83]
                            && swatchSelects[84] && !swatchSelects[80] && !swatchSelects[81]) ||
                    (swatchSelects[78] && swatchSelects[80] && swatchSelects[82] && swatchSelects[83]
                            && swatchSelects[84] && !swatchSelects[79] && !swatchSelects[81]) ||
                    (swatchSelects[79] && swatchSelects[80] && swatchSelects[82] && swatchSelects[83]
                            && swatchSelects[84] && !swatchSelects[78] && !swatchSelects[81]) ||
                    (swatchSelects[78] && swatchSelects[81] && swatchSelects[82] && swatchSelects[83]
                            && swatchSelects[84] && !swatchSelects[79] && !swatchSelects[80]) ||
                    (swatchSelects[79] && swatchSelects[81] && swatchSelects[82] && swatchSelects[83]
                            && swatchSelects[84] && !swatchSelects[78] && !swatchSelects[80]) ||
                    (swatchSelects[80] && swatchSelects[81] && swatchSelects[82] && swatchSelects[83]
                            && swatchSelects[84] && !swatchSelects[78] && !swatchSelects[79])) {
                int a[] = {78, 79, 80, 81, 82, 83, 84};
                i = pickUnSelected(a);
            } else if ((swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[22]
                    && swatchSelects[31] && !swatchSelects[41] && !swatchSelects[51]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[22]
                            && swatchSelects[41] && !swatchSelects[31] && !swatchSelects[51]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[31]
                            && swatchSelects[41] && !swatchSelects[22] && !swatchSelects[51]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[22] && swatchSelects[31]
                            && swatchSelects[41] && !swatchSelects[14] && !swatchSelects[51]) ||
                    (swatchSelects[1] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31]
                            && swatchSelects[41] && !swatchSelects[7] && !swatchSelects[51]) ||
                    (swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31]
                            && swatchSelects[41] && !swatchSelects[1] && !swatchSelects[51]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[22]
                            && swatchSelects[51] && !swatchSelects[31] && !swatchSelects[41]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[31]
                            && swatchSelects[51] && !swatchSelects[22] && !swatchSelects[41]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[22] && swatchSelects[31]
                            && swatchSelects[51] && !swatchSelects[14] && !swatchSelects[41]) ||
                    (swatchSelects[1] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31]
                            && swatchSelects[51] && !swatchSelects[7] && !swatchSelects[41]) ||
                    (swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[31]
                            && swatchSelects[51] && !swatchSelects[1] && !swatchSelects[41]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[14] && swatchSelects[41]
                            && swatchSelects[51] && !swatchSelects[22] && !swatchSelects[31]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[22] && swatchSelects[41]
                            && swatchSelects[51] && !swatchSelects[14] && !swatchSelects[31]) ||
                    (swatchSelects[1] && swatchSelects[14] && swatchSelects[22] && swatchSelects[41]
                            && swatchSelects[51] && !swatchSelects[7] && !swatchSelects[31]) ||
                    (swatchSelects[7] && swatchSelects[14] && swatchSelects[22] && swatchSelects[41]
                            && swatchSelects[51] && !swatchSelects[1] && !swatchSelects[31]) ||
                    (swatchSelects[1] && swatchSelects[7] && swatchSelects[31] && swatchSelects[41]
                            && swatchSelects[51] && !swatchSelects[14] && !swatchSelects[22]) ||
                    (swatchSelects[1] && swatchSelects[14] && swatchSelects[31] && swatchSelects[41]
                            && swatchSelects[51] && !swatchSelects[7] && !swatchSelects[22]) ||
                    (swatchSelects[7] && swatchSelects[14] && swatchSelects[31] && swatchSelects[41]
                            && swatchSelects[51] && !swatchSelects[1] && !swatchSelects[22]) ||
                    (swatchSelects[1] && swatchSelects[22] && swatchSelects[31] && swatchSelects[41]
                            && swatchSelects[51] && !swatchSelects[7] && !swatchSelects[14]) ||
                    (swatchSelects[7] && swatchSelects[22] && swatchSelects[31] && swatchSelects[41]
                            && swatchSelects[51] && !swatchSelects[1] && !swatchSelects[14]) ||
                    (swatchSelects[14] && swatchSelects[22] && swatchSelects[31] && swatchSelects[41]
                            && swatchSelects[51] && !swatchSelects[1] && !swatchSelects[7])) {
                int a[] = {1, 7, 14, 22, 31, 41, 51};
                i = pickUnSelected(a);
            } else if ((swatchSelects[39] && swatchSelects[49] && swatchSelects[59] && swatchSelects[68]
                    && swatchSelects[76] && !swatchSelects[83] && !swatchSelects[89]) ||
                    (swatchSelects[39] && swatchSelects[49] && swatchSelects[59] && swatchSelects[68]
                            && swatchSelects[83] && !swatchSelects[76] && !swatchSelects[89]) ||
                    (swatchSelects[39] && swatchSelects[49] && swatchSelects[59] && swatchSelects[76]
                            && swatchSelects[83] && !swatchSelects[68] && !swatchSelects[89]) ||
                    (swatchSelects[39] && swatchSelects[49] && swatchSelects[68] && swatchSelects[76]
                            && swatchSelects[83] && !swatchSelects[59] && !swatchSelects[89]) ||
                    (swatchSelects[39] && swatchSelects[59] && swatchSelects[68] && swatchSelects[76]
                            && swatchSelects[83] && !swatchSelects[49] && !swatchSelects[89]) ||
                    (swatchSelects[49] && swatchSelects[59] && swatchSelects[68] && swatchSelects[76]
                            && swatchSelects[83] && !swatchSelects[39] && !swatchSelects[89]) ||
                    (swatchSelects[39] && swatchSelects[49] && swatchSelects[59] && swatchSelects[68]
                            && swatchSelects[89] && !swatchSelects[76] && !swatchSelects[83]) ||
                    (swatchSelects[39] && swatchSelects[49] && swatchSelects[59] && swatchSelects[76]
                            && swatchSelects[89] && !swatchSelects[68] && !swatchSelects[83]) ||
                    (swatchSelects[39] && swatchSelects[49] && swatchSelects[68] && swatchSelects[76]
                            && swatchSelects[89] && !swatchSelects[59] && !swatchSelects[83]) ||
                    (swatchSelects[39] && swatchSelects[59] && swatchSelects[68] && swatchSelects[76]
                            && swatchSelects[89] && !swatchSelects[49] && !swatchSelects[83]) ||
                    (swatchSelects[49] && swatchSelects[59] && swatchSelects[68] && swatchSelects[76]
                            && swatchSelects[89] && !swatchSelects[39] && !swatchSelects[83]) ||
                    (swatchSelects[39] && swatchSelects[49] && swatchSelects[59] && swatchSelects[83]
                            && swatchSelects[89] && !swatchSelects[68] && !swatchSelects[76]) ||
                    (swatchSelects[39] && swatchSelects[49] && swatchSelects[68] && swatchSelects[83]
                            && swatchSelects[89] && !swatchSelects[59] && !swatchSelects[76]) ||
                    (swatchSelects[39] && swatchSelects[59] && swatchSelects[68] && swatchSelects[83]
                            && swatchSelects[89] && !swatchSelects[49] && !swatchSelects[76]) ||
                    (swatchSelects[49] && swatchSelects[59] && swatchSelects[68] && swatchSelects[83]
                            && swatchSelects[89] && !swatchSelects[39] && !swatchSelects[76]) ||
                    (swatchSelects[39] && swatchSelects[49] && swatchSelects[76] && swatchSelects[83]
                            && swatchSelects[89] && !swatchSelects[59] && !swatchSelects[68]) ||
                    (swatchSelects[39] && swatchSelects[59] && swatchSelects[76] && swatchSelects[83]
                            && swatchSelects[89] && !swatchSelects[49] && !swatchSelects[68]) ||
                    (swatchSelects[49] && swatchSelects[59] && swatchSelects[76] && swatchSelects[83]
                            && swatchSelects[89] && !swatchSelects[39] && !swatchSelects[68]) ||
                    (swatchSelects[39] && swatchSelects[68] && swatchSelects[76] && swatchSelects[83]
                            && swatchSelects[89] && !swatchSelects[49] && !swatchSelects[59]) ||
                    (swatchSelects[49] && swatchSelects[68] && swatchSelects[76] && swatchSelects[83]
                            && swatchSelects[89] && !swatchSelects[39] && !swatchSelects[59]) ||
                    (swatchSelects[59] && swatchSelects[68] && swatchSelects[76] && swatchSelects[83]
                            && swatchSelects[89] && !swatchSelects[39] && !swatchSelects[49])) {
                int a[] = {39, 49, 59, 68, 76, 83, 89};
                i = pickUnSelected(a);
            } else if ((swatchSelects[4] && swatchSelects[11] && swatchSelects[19] && swatchSelects[28]
                    && swatchSelects[38] && !swatchSelects[49] && !swatchSelects[60]) ||
                    (swatchSelects[4] && swatchSelects[11] && swatchSelects[19] && swatchSelects[28]
                            && swatchSelects[49] && !swatchSelects[38] && !swatchSelects[60]) ||
                    (swatchSelects[4] && swatchSelects[11] && swatchSelects[19] && swatchSelects[38]
                            && swatchSelects[49] && !swatchSelects[28] && !swatchSelects[60]) ||
                    (swatchSelects[4] && swatchSelects[11] && swatchSelects[28] && swatchSelects[38]
                            && swatchSelects[49] && !swatchSelects[19] && !swatchSelects[60]) ||
                    (swatchSelects[4] && swatchSelects[19] && swatchSelects[28] && swatchSelects[38]
                            && swatchSelects[49] && !swatchSelects[11] && !swatchSelects[60]) ||
                    (swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[38]
                            && swatchSelects[49] && !swatchSelects[4] && !swatchSelects[60]) ||
                    (swatchSelects[4] && swatchSelects[11] && swatchSelects[19] && swatchSelects[28]
                            && swatchSelects[60] && !swatchSelects[38] && !swatchSelects[49]) ||
                    (swatchSelects[4] && swatchSelects[11] && swatchSelects[19] && swatchSelects[38]
                            && swatchSelects[60] && !swatchSelects[28] && !swatchSelects[49]) ||
                    (swatchSelects[4] && swatchSelects[11] && swatchSelects[28] && swatchSelects[38]
                            && swatchSelects[60] && !swatchSelects[19] && !swatchSelects[49]) ||
                    (swatchSelects[4] && swatchSelects[19] && swatchSelects[28] && swatchSelects[38]
                            && swatchSelects[60] && !swatchSelects[11] && !swatchSelects[49]) ||
                    (swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[38]
                            && swatchSelects[60] && !swatchSelects[4] && !swatchSelects[49]) ||
                    (swatchSelects[4] && swatchSelects[11] && swatchSelects[19] && swatchSelects[49]
                            && swatchSelects[60] && !swatchSelects[28] && !swatchSelects[38]) ||
                    (swatchSelects[4] && swatchSelects[11] && swatchSelects[28] && swatchSelects[49]
                            && swatchSelects[60] && !swatchSelects[19] && !swatchSelects[38]) ||
                    (swatchSelects[4] && swatchSelects[19] && swatchSelects[28] && swatchSelects[49]
                            && swatchSelects[60] && !swatchSelects[11] && !swatchSelects[38]) ||
                    (swatchSelects[11] && swatchSelects[19] && swatchSelects[28] && swatchSelects[49]
                            && swatchSelects[60] && !swatchSelects[4] && !swatchSelects[38]) ||
                    (swatchSelects[4] && swatchSelects[11] && swatchSelects[38] && swatchSelects[49]
                            && swatchSelects[60] && !swatchSelects[19] && !swatchSelects[28]) ||
                    (swatchSelects[4] && swatchSelects[19] && swatchSelects[38] && swatchSelects[49]
                            && swatchSelects[60] && !swatchSelects[11] && !swatchSelects[28]) ||
                    (swatchSelects[11] && swatchSelects[19] && swatchSelects[38] && swatchSelects[49]
                            && swatchSelects[60] && !swatchSelects[4] && !swatchSelects[28]) ||
                    (swatchSelects[4] && swatchSelects[28] && swatchSelects[38] && swatchSelects[49]
                            && swatchSelects[60] && !swatchSelects[11] && !swatchSelects[19]) ||
                    (swatchSelects[11] && swatchSelects[28] && swatchSelects[38] && swatchSelects[49]
                            && swatchSelects[60] && !swatchSelects[4] && !swatchSelects[19]) ||
                    (swatchSelects[19] && swatchSelects[28] && swatchSelects[38] && swatchSelects[49]
                            && swatchSelects[60] && !swatchSelects[4] && !swatchSelects[11])) {
                int a[] = {4, 11, 19, 28, 38, 49, 60};
                i = pickUnSelected(a);
            } else if ((swatchSelects[30] && swatchSelects[41] && swatchSelects[52] && swatchSelects[62]
                    && swatchSelects[71] && !swatchSelects[79] && !swatchSelects[86]) ||
                    (swatchSelects[30] && swatchSelects[41] && swatchSelects[52] && swatchSelects[62]
                            && swatchSelects[79] && !swatchSelects[71] && !swatchSelects[86]) ||
                    (swatchSelects[30] && swatchSelects[41] && swatchSelects[52] && swatchSelects[71]
                            && swatchSelects[79] && !swatchSelects[62] && !swatchSelects[86]) ||
                    (swatchSelects[30] && swatchSelects[41] && swatchSelects[62] && swatchSelects[71]
                            && swatchSelects[79] && !swatchSelects[52] && !swatchSelects[86]) ||
                    (swatchSelects[30] && swatchSelects[52] && swatchSelects[62] && swatchSelects[71]
                            && swatchSelects[79] && !swatchSelects[41] && !swatchSelects[86]) ||
                    (swatchSelects[41] && swatchSelects[52] && swatchSelects[62] && swatchSelects[71]
                            && swatchSelects[79] && !swatchSelects[30] && !swatchSelects[86]) ||
                    (swatchSelects[30] && swatchSelects[41] && swatchSelects[52] && swatchSelects[62]
                            && swatchSelects[86] && !swatchSelects[71] && !swatchSelects[79]) ||
                    (swatchSelects[30] && swatchSelects[41] && swatchSelects[52] && swatchSelects[71]
                            && swatchSelects[86] && !swatchSelects[62] && !swatchSelects[79]) ||
                    (swatchSelects[30] && swatchSelects[41] && swatchSelects[62] && swatchSelects[71]
                            && swatchSelects[86] && !swatchSelects[52] && !swatchSelects[79]) ||
                    (swatchSelects[30] && swatchSelects[52] && swatchSelects[62] && swatchSelects[71]
                            && swatchSelects[86] && !swatchSelects[41] && !swatchSelects[79]) ||
                    (swatchSelects[41] && swatchSelects[52] && swatchSelects[62] && swatchSelects[71]
                            && swatchSelects[86] && !swatchSelects[30] && !swatchSelects[79]) ||
                    (swatchSelects[30] && swatchSelects[41] && swatchSelects[52] && swatchSelects[79]
                            && swatchSelects[86] && !swatchSelects[62] && !swatchSelects[71]) ||
                    (swatchSelects[30] && swatchSelects[41] && swatchSelects[62] && swatchSelects[79]
                            && swatchSelects[86] && !swatchSelects[52] && !swatchSelects[71]) ||
                    (swatchSelects[30] && swatchSelects[52] && swatchSelects[62] && swatchSelects[79]
                            && swatchSelects[86] && !swatchSelects[41] && !swatchSelects[71]) ||
                    (swatchSelects[41] && swatchSelects[52] && swatchSelects[62] && swatchSelects[79]
                            && swatchSelects[86] && !swatchSelects[30] && !swatchSelects[71]) ||
                    (swatchSelects[30] && swatchSelects[41] && swatchSelects[71] && swatchSelects[79]
                            && swatchSelects[86] && !swatchSelects[52] && !swatchSelects[62]) ||
                    (swatchSelects[30] && swatchSelects[52] && swatchSelects[71] && swatchSelects[79]
                            && swatchSelects[86] && !swatchSelects[41] && !swatchSelects[62]) ||
                    (swatchSelects[41] && swatchSelects[52] && swatchSelects[71] && swatchSelects[79]
                            && swatchSelects[86] && !swatchSelects[30] && !swatchSelects[62]) ||
                    (swatchSelects[30] && swatchSelects[62] && swatchSelects[71] && swatchSelects[79]
                            && swatchSelects[86] && !swatchSelects[41] && !swatchSelects[52]) ||
                    (swatchSelects[41] && swatchSelects[62] && swatchSelects[71] && swatchSelects[79]
                            && swatchSelects[86] && !swatchSelects[30] && !swatchSelects[52]) ||
                    (swatchSelects[52] && swatchSelects[62] && swatchSelects[71] && swatchSelects[79]
                            && swatchSelects[86] && !swatchSelects[30] && !swatchSelects[41])) {
                int a[] = {30, 41, 52, 62, 71, 79, 86};
                i = pickUnSelected(a);
            } else if ((swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17]
                    && swatchSelects[18] && !swatchSelects[19] && !swatchSelects[20]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17]
                            && swatchSelects[19] && !swatchSelects[18] && !swatchSelects[20]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[18]
                            && swatchSelects[19] && !swatchSelects[17] && !swatchSelects[20]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[17] && swatchSelects[18]
                            && swatchSelects[19] && !swatchSelects[16] && !swatchSelects[20]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18]
                            && swatchSelects[19] && !swatchSelects[15] && !swatchSelects[20]) ||
                    (swatchSelects[13] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18]
                            && swatchSelects[19] && !swatchSelects[14] && !swatchSelects[20]) ||
                    (swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18]
                            && swatchSelects[19] && !swatchSelects[13] && !swatchSelects[20]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17]
                            && swatchSelects[20] && !swatchSelects[18] && !swatchSelects[19]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[18]
                            && swatchSelects[20] && !swatchSelects[17] && !swatchSelects[19]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[17] && swatchSelects[18]
                            && swatchSelects[20] && !swatchSelects[16] && !swatchSelects[19]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18]
                            && swatchSelects[20] && !swatchSelects[15] && !swatchSelects[19]) ||
                    (swatchSelects[13] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18]
                            && swatchSelects[20] && !swatchSelects[14] && !swatchSelects[19]) ||
                    (swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18]
                            && swatchSelects[20] && !swatchSelects[13] && !swatchSelects[19]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[17] && !swatchSelects[18]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[17] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[16] && !swatchSelects[18]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[16] && swatchSelects[17] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[15] && !swatchSelects[18]) ||
                    (swatchSelects[13] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[14] && !swatchSelects[18]) ||
                    (swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[13] && !swatchSelects[18]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[15] && swatchSelects[18] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[16] && !swatchSelects[17]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[16] && swatchSelects[18] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[15] && !swatchSelects[17]) ||
                    (swatchSelects[13] && swatchSelects[15] && swatchSelects[16] && swatchSelects[18] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[14] && !swatchSelects[17]) ||
                    (swatchSelects[14] && swatchSelects[15] && swatchSelects[16] && swatchSelects[18] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[13] && !swatchSelects[17]) ||
                    (swatchSelects[13] && swatchSelects[14] && swatchSelects[17] && swatchSelects[18] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[15] && !swatchSelects[16]) ||
                    (swatchSelects[13] && swatchSelects[15] && swatchSelects[17] && swatchSelects[18] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[14] && !swatchSelects[16]) ||
                    (swatchSelects[14] && swatchSelects[15] && swatchSelects[17] && swatchSelects[18] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[13] && !swatchSelects[16]) ||
                    (swatchSelects[13] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[14] && !swatchSelects[15]) ||
                    (swatchSelects[14] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[13] && !swatchSelects[15]) ||
                    (swatchSelects[15] && swatchSelects[16] && swatchSelects[17] && swatchSelects[18] && swatchSelects[19]
                            && swatchSelects[20] && !swatchSelects[13] && !swatchSelects[14])) {
                int a[] = {13, 14, 15, 16, 17, 18, 19, 20};
                i = pickUnSelected(a);
            } else if ((swatchSelects[70] && swatchSelects[71] && swatchSelects[72] && swatchSelects[73] && swatchSelects[74]
                    && swatchSelects[75] && !swatchSelects[76] && !swatchSelects[77]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[72] && swatchSelects[73] && swatchSelects[74]
                            && swatchSelects[76] && !swatchSelects[75] && !swatchSelects[77]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[72] && swatchSelects[73] && swatchSelects[75]
                            && swatchSelects[76] && !swatchSelects[74] && !swatchSelects[77]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[72] && swatchSelects[74] && swatchSelects[75]
                            && swatchSelects[76] && !swatchSelects[73] && !swatchSelects[77]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[73] && swatchSelects[74] && swatchSelects[75]
                            && swatchSelects[76] && !swatchSelects[72] && !swatchSelects[77]) ||
                    (swatchSelects[70] && swatchSelects[72] && swatchSelects[73] && swatchSelects[74] && swatchSelects[75]
                            && swatchSelects[76] && !swatchSelects[71] && !swatchSelects[77]) ||
                    (swatchSelects[71] && swatchSelects[72] && swatchSelects[73] && swatchSelects[74] && swatchSelects[75]
                            && swatchSelects[76] && !swatchSelects[70] && !swatchSelects[77]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[72] && swatchSelects[73] && swatchSelects[74]
                            && swatchSelects[77] && !swatchSelects[75] && !swatchSelects[76]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[72] && swatchSelects[73] && swatchSelects[75]
                            && swatchSelects[77] && !swatchSelects[74] && !swatchSelects[76]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[72] && swatchSelects[74] && swatchSelects[75]
                            && swatchSelects[77] && !swatchSelects[73] && !swatchSelects[76]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[73] && swatchSelects[74] && swatchSelects[75]
                            && swatchSelects[77] && !swatchSelects[72] && !swatchSelects[76]) ||
                    (swatchSelects[70] && swatchSelects[72] && swatchSelects[73] && swatchSelects[74] && swatchSelects[75]
                            && swatchSelects[77] && !swatchSelects[71] && !swatchSelects[76]) ||
                    (swatchSelects[71] && swatchSelects[72] && swatchSelects[73] && swatchSelects[74] && swatchSelects[75]
                            && swatchSelects[77] && !swatchSelects[70] && !swatchSelects[76]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[72] && swatchSelects[73] && swatchSelects[76]
                            && swatchSelects[77] && !swatchSelects[74] && !swatchSelects[75]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[72] && swatchSelects[74] && swatchSelects[76]
                            && swatchSelects[77] && !swatchSelects[73] && !swatchSelects[75]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[73] && swatchSelects[74] && swatchSelects[76]
                            && swatchSelects[77] && !swatchSelects[72] && !swatchSelects[75]) ||
                    (swatchSelects[70] && swatchSelects[72] && swatchSelects[73] && swatchSelects[74] && swatchSelects[76]
                            && swatchSelects[77] && !swatchSelects[71] && !swatchSelects[75]) ||
                    (swatchSelects[71] && swatchSelects[72] && swatchSelects[73] && swatchSelects[74] && swatchSelects[76]
                            && swatchSelects[77] && !swatchSelects[70] && !swatchSelects[75]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[72] && swatchSelects[75] && swatchSelects[76]
                            && swatchSelects[77] && !swatchSelects[73] && !swatchSelects[74]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[73] && swatchSelects[75] && swatchSelects[76]
                            && swatchSelects[77] && !swatchSelects[72] && !swatchSelects[74]) ||
                    (swatchSelects[70] && swatchSelects[72] && swatchSelects[73] && swatchSelects[75] && swatchSelects[76]
                            && swatchSelects[77] && !swatchSelects[71] && !swatchSelects[74]) ||
                    (swatchSelects[71] && swatchSelects[72] && swatchSelects[73] && swatchSelects[75] && swatchSelects[76]
                            && swatchSelects[77] && !swatchSelects[70] && !swatchSelects[74]) ||
                    (swatchSelects[70] && swatchSelects[71] && swatchSelects[74] && swatchSelects[75] && swatchSelects[76]
                            && swatchSelects[77] && !swatchSelects[72] && !swatchSelects[73]) ||
                    (swatchSelects[70] && swatchSelects[72] && swatchSelects[74] && swatchSelects[75] && swatchSelects[76]
                            && swatchSelects[77] && !swatchSelects[71] && !swatchSelects[73]) ||
                    (swatchSelects[71] && swatchSelects[72] && swatchSelects[74] && swatchSelects[75] && swatchSelects[76]
                            && swatchSelects[77] && !swatchSelects[70] && !swatchSelects[73]) ||
                    (swatchSelects[70] && swatchSelects[73] && swatchSelects[74] && swatchSelects[75] && swatchSelects[76]
                            && swatchSelects[77] && !swatchSelects[71] && !swatchSelects[72]) ||
                    (swatchSelects[71] && swatchSelects[73] && swatchSelects[74] && swatchSelects[75] && swatchSelects[76]
                            && swatchSelects[77] && !swatchSelects[70] && !swatchSelects[72]) ||
                    (swatchSelects[72] && swatchSelects[73] && swatchSelects[74] && swatchSelects[75] && swatchSelects[76]
                            && swatchSelects[77] && !swatchSelects[70] && !swatchSelects[71])) {
                int a[] = {70, 71, 72, 73, 74, 75, 76, 77};
                i = pickUnSelected(a);
            } else if ((swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32]
                    && swatchSelects[42] && !swatchSelects[52] && !swatchSelects[61]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32]
                            && swatchSelects[52] && !swatchSelects[42] && !swatchSelects[61]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[42]
                            && swatchSelects[52] && !swatchSelects[32] && !swatchSelects[61]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[32] && swatchSelects[42]
                            && swatchSelects[52] && !swatchSelects[23] && !swatchSelects[61]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[23] && swatchSelects[32] && swatchSelects[42]
                            && swatchSelects[52] && !swatchSelects[15] && !swatchSelects[61]) ||
                    (swatchSelects[2] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32] && swatchSelects[42]
                            && swatchSelects[52] && !swatchSelects[8] && !swatchSelects[61]) ||
                    (swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32] && swatchSelects[42]
                            && swatchSelects[52] && !swatchSelects[2] && !swatchSelects[61]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32]
                            && swatchSelects[61] && !swatchSelects[42] && !swatchSelects[52]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[42]
                            && swatchSelects[61] && !swatchSelects[32] && !swatchSelects[52]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[32] && swatchSelects[42]
                            && swatchSelects[61] && !swatchSelects[23] && !swatchSelects[52]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[23] && swatchSelects[32] && swatchSelects[42]
                            && swatchSelects[61] && !swatchSelects[15] && !swatchSelects[52]) ||
                    (swatchSelects[2] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32] && swatchSelects[42]
                            && swatchSelects[61] && !swatchSelects[8] && !swatchSelects[52]) ||
                    (swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32] && swatchSelects[42]
                            && swatchSelects[61] && !swatchSelects[2] && !swatchSelects[52]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[52]
                            && swatchSelects[61] && !swatchSelects[32] && !swatchSelects[42]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[32] && swatchSelects[52]
                            && swatchSelects[61] && !swatchSelects[23] && !swatchSelects[42]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[23] && swatchSelects[32] && swatchSelects[52]
                            && swatchSelects[61] && !swatchSelects[15] && !swatchSelects[42]) ||
                    (swatchSelects[2] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32] && swatchSelects[52]
                            && swatchSelects[61] && !swatchSelects[8] && !swatchSelects[42]) ||
                    (swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[32] && swatchSelects[52]
                            && swatchSelects[61] && !swatchSelects[2] && !swatchSelects[42]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[15] && swatchSelects[42] && swatchSelects[52]
                            && swatchSelects[61] && !swatchSelects[23] && !swatchSelects[32]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[23] && swatchSelects[42] && swatchSelects[52]
                            && swatchSelects[61] && !swatchSelects[15] && !swatchSelects[32]) ||
                    (swatchSelects[2] && swatchSelects[15] && swatchSelects[23] && swatchSelects[42] && swatchSelects[52]
                            && swatchSelects[61] && !swatchSelects[8] && !swatchSelects[32]) ||
                    (swatchSelects[8] && swatchSelects[15] && swatchSelects[23] && swatchSelects[42] && swatchSelects[52]
                            && swatchSelects[61] && !swatchSelects[2] && !swatchSelects[32]) ||
                    (swatchSelects[2] && swatchSelects[8] && swatchSelects[32] && swatchSelects[42] && swatchSelects[52]
                            && swatchSelects[61] && !swatchSelects[15] && !swatchSelects[23]) ||
                    (swatchSelects[2] && swatchSelects[15] && swatchSelects[32] && swatchSelects[42] && swatchSelects[52]
                            && swatchSelects[61] && !swatchSelects[8] && !swatchSelects[23]) ||
                    (swatchSelects[8] && swatchSelects[15] && swatchSelects[32] && swatchSelects[42] && swatchSelects[52]
                            && swatchSelects[61] && !swatchSelects[2] && !swatchSelects[23]) ||
                    (swatchSelects[2] && swatchSelects[23] && swatchSelects[32] && swatchSelects[42] && swatchSelects[52]
                            && swatchSelects[61] && !swatchSelects[8] && !swatchSelects[15]) ||
                    (swatchSelects[8] && swatchSelects[23] && swatchSelects[32] && swatchSelects[42] && swatchSelects[52]
                            && swatchSelects[61] && !swatchSelects[2] && !swatchSelects[15]) ||
                    (swatchSelects[15] && swatchSelects[23] && swatchSelects[32] && swatchSelects[42] && swatchSelects[52]
                            && swatchSelects[61] && !swatchSelects[2] && !swatchSelects[8])) {
                int a[] = {2, 8, 15, 23, 32, 42, 52, 61};
                i = pickUnSelected(a);
            } else if ((swatchSelects[29] && swatchSelects[38] && swatchSelects[48] && swatchSelects[58] && swatchSelects[67]
                    && swatchSelects[75] && !swatchSelects[82] && !swatchSelects[88]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[48] && swatchSelects[58] && swatchSelects[67]
                            && swatchSelects[82] && !swatchSelects[75] && !swatchSelects[88]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[48] && swatchSelects[58] && swatchSelects[75]
                            && swatchSelects[82] && !swatchSelects[67] && !swatchSelects[88]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[48] && swatchSelects[67] && swatchSelects[75]
                            && swatchSelects[82] && !swatchSelects[58] && !swatchSelects[88]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[58] && swatchSelects[67] && swatchSelects[75]
                            && swatchSelects[82] && !swatchSelects[48] && !swatchSelects[88]) ||
                    (swatchSelects[29] && swatchSelects[48] && swatchSelects[58] && swatchSelects[67] && swatchSelects[75]
                            && swatchSelects[82] && !swatchSelects[38] && !swatchSelects[88]) ||
                    (swatchSelects[38] && swatchSelects[48] && swatchSelects[58] && swatchSelects[67] && swatchSelects[75]
                            && swatchSelects[82] && !swatchSelects[29] && !swatchSelects[88]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[48] && swatchSelects[58] && swatchSelects[67]
                            && swatchSelects[88] && !swatchSelects[75] && !swatchSelects[82]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[48] && swatchSelects[58] && swatchSelects[75]
                            && swatchSelects[88] && !swatchSelects[67] && !swatchSelects[82]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[48] && swatchSelects[67] && swatchSelects[75]
                            && swatchSelects[88] && !swatchSelects[58] && !swatchSelects[82]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[58] && swatchSelects[67] && swatchSelects[75]
                            && swatchSelects[88] && !swatchSelects[48] && !swatchSelects[82]) ||
                    (swatchSelects[29] && swatchSelects[48] && swatchSelects[58] && swatchSelects[67] && swatchSelects[75]
                            && swatchSelects[88] && !swatchSelects[38] && !swatchSelects[82]) ||
                    (swatchSelects[38] && swatchSelects[48] && swatchSelects[58] && swatchSelects[67] && swatchSelects[75]
                            && swatchSelects[88] && !swatchSelects[29] && !swatchSelects[82]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[48] && swatchSelects[58] && swatchSelects[82]
                            && swatchSelects[88] && !swatchSelects[67] && !swatchSelects[75]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[48] && swatchSelects[67] && swatchSelects[82]
                            && swatchSelects[88] && !swatchSelects[58] && !swatchSelects[75]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[58] && swatchSelects[67] && swatchSelects[82]
                            && swatchSelects[88] && !swatchSelects[48] && !swatchSelects[75]) ||
                    (swatchSelects[29] && swatchSelects[48] && swatchSelects[58] && swatchSelects[67] && swatchSelects[82]
                            && swatchSelects[88] && !swatchSelects[38] && !swatchSelects[75]) ||
                    (swatchSelects[38] && swatchSelects[48] && swatchSelects[58] && swatchSelects[67] && swatchSelects[82]
                            && swatchSelects[88] && !swatchSelects[29] && !swatchSelects[75]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[48] && swatchSelects[75] && swatchSelects[82]
                            && swatchSelects[88] && !swatchSelects[58] && !swatchSelects[67]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[58] && swatchSelects[75] && swatchSelects[82]
                            && swatchSelects[88] && !swatchSelects[48] && !swatchSelects[67]) ||
                    (swatchSelects[29] && swatchSelects[48] && swatchSelects[58] && swatchSelects[75] && swatchSelects[82]
                            && swatchSelects[88] && !swatchSelects[38] && !swatchSelects[67]) ||
                    (swatchSelects[38] && swatchSelects[48] && swatchSelects[58] && swatchSelects[75] && swatchSelects[82]
                            && swatchSelects[88] && !swatchSelects[29] && !swatchSelects[67]) ||
                    (swatchSelects[29] && swatchSelects[38] && swatchSelects[67] && swatchSelects[75] && swatchSelects[82]
                            && swatchSelects[88] && !swatchSelects[48] && !swatchSelects[58]) ||
                    (swatchSelects[29] && swatchSelects[48] && swatchSelects[67] && swatchSelects[75] && swatchSelects[82]
                            && swatchSelects[88] && !swatchSelects[38] && !swatchSelects[58]) ||
                    (swatchSelects[38] && swatchSelects[48] && swatchSelects[67] && swatchSelects[75] && swatchSelects[82]
                            && swatchSelects[88] && !swatchSelects[29] && !swatchSelects[58]) ||
                    (swatchSelects[29] && swatchSelects[58] && swatchSelects[67] && swatchSelects[75] && swatchSelects[82]
                            && swatchSelects[88] && !swatchSelects[38] && !swatchSelects[48]) ||
                    (swatchSelects[38] && swatchSelects[58] && swatchSelects[67] && swatchSelects[75] && swatchSelects[82]
                            && swatchSelects[88] && !swatchSelects[29] && !swatchSelects[48]) ||
                    (swatchSelects[48] && swatchSelects[58] && swatchSelects[67] && swatchSelects[75] && swatchSelects[82]
                            && swatchSelects[88] && !swatchSelects[29] && !swatchSelects[38])) {
                int a[] = {29, 38, 48, 58, 67, 75, 82, 88};
                i = pickUnSelected(a);
            } else if ((swatchSelects[3] && swatchSelects[10] && swatchSelects[18] && swatchSelects[27] && swatchSelects[37]
                    && swatchSelects[48] && !swatchSelects[59] && !swatchSelects[69]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[18] && swatchSelects[27] && swatchSelects[37]
                            && swatchSelects[59] && !swatchSelects[48] && !swatchSelects[69]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[18] && swatchSelects[27] && swatchSelects[48]
                            && swatchSelects[59] && !swatchSelects[37] && !swatchSelects[69]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[18] && swatchSelects[37] && swatchSelects[48]
                            && swatchSelects[59] && !swatchSelects[27] && !swatchSelects[69]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[27] && swatchSelects[37] && swatchSelects[48]
                            && swatchSelects[59] && !swatchSelects[18] && !swatchSelects[69]) ||
                    (swatchSelects[3] && swatchSelects[18] && swatchSelects[27] && swatchSelects[37] && swatchSelects[48]
                            && swatchSelects[59] && !swatchSelects[10] && !swatchSelects[69]) ||
                    (swatchSelects[10] && swatchSelects[18] && swatchSelects[27] && swatchSelects[37] && swatchSelects[48]
                            && swatchSelects[59] && !swatchSelects[3] && !swatchSelects[69]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[18] && swatchSelects[27] && swatchSelects[37]
                            && swatchSelects[69] && !swatchSelects[48] && !swatchSelects[59]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[18] && swatchSelects[27] && swatchSelects[48]
                            && swatchSelects[69] && !swatchSelects[37] && !swatchSelects[59]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[18] && swatchSelects[37] && swatchSelects[48]
                            && swatchSelects[69] && !swatchSelects[27] && !swatchSelects[59]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[27] && swatchSelects[37] && swatchSelects[48]
                            && swatchSelects[69] && !swatchSelects[18] && !swatchSelects[59]) ||
                    (swatchSelects[3] && swatchSelects[18] && swatchSelects[27] && swatchSelects[37] && swatchSelects[48]
                            && swatchSelects[69] && !swatchSelects[10] && !swatchSelects[59]) ||
                    (swatchSelects[10] && swatchSelects[18] && swatchSelects[27] && swatchSelects[37] && swatchSelects[48]
                            && swatchSelects[69] && !swatchSelects[3] && !swatchSelects[59]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[18] && swatchSelects[27] && swatchSelects[59]
                            && swatchSelects[69] && !swatchSelects[37] && !swatchSelects[48]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[18] && swatchSelects[37] && swatchSelects[59]
                            && swatchSelects[69] && !swatchSelects[27] && !swatchSelects[48]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[27] && swatchSelects[37] && swatchSelects[59]
                            && swatchSelects[69] && !swatchSelects[18] && !swatchSelects[48]) ||
                    (swatchSelects[3] && swatchSelects[18] && swatchSelects[27] && swatchSelects[37] && swatchSelects[59]
                            && swatchSelects[69] && !swatchSelects[10] && !swatchSelects[48]) ||
                    (swatchSelects[10] && swatchSelects[18] && swatchSelects[27] && swatchSelects[37] && swatchSelects[59]
                            && swatchSelects[69] && !swatchSelects[3] && !swatchSelects[48]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[18] && swatchSelects[48] && swatchSelects[59]
                            && swatchSelects[69] && !swatchSelects[27] && !swatchSelects[37]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[27] && swatchSelects[48] && swatchSelects[59]
                            && swatchSelects[69] && !swatchSelects[18] && !swatchSelects[37]) ||
                    (swatchSelects[3] && swatchSelects[18] && swatchSelects[27] && swatchSelects[48] && swatchSelects[59]
                            && swatchSelects[69] && !swatchSelects[10] && !swatchSelects[37]) ||
                    (swatchSelects[10] && swatchSelects[18] && swatchSelects[27] && swatchSelects[48] && swatchSelects[59]
                            && swatchSelects[69] && !swatchSelects[3] && !swatchSelects[37]) ||
                    (swatchSelects[3] && swatchSelects[10] && swatchSelects[37] && swatchSelects[48] && swatchSelects[59]
                            && swatchSelects[69] && !swatchSelects[18] && !swatchSelects[27]) ||
                    (swatchSelects[3] && swatchSelects[18] && swatchSelects[37] && swatchSelects[48] && swatchSelects[59]
                            && swatchSelects[69] && !swatchSelects[10] && !swatchSelects[27]) ||
                    (swatchSelects[10] && swatchSelects[18] && swatchSelects[37] && swatchSelects[48] && swatchSelects[59]
                            && swatchSelects[69] && !swatchSelects[3] && !swatchSelects[27]) ||
                    (swatchSelects[3] && swatchSelects[27] && swatchSelects[37] && swatchSelects[48] && swatchSelects[59]
                            && swatchSelects[69] && !swatchSelects[10] && !swatchSelects[18]) ||
                    (swatchSelects[10] && swatchSelects[27] && swatchSelects[37] && swatchSelects[48] && swatchSelects[59]
                            && swatchSelects[69] && !swatchSelects[3] && !swatchSelects[18]) ||
                    (swatchSelects[18] && swatchSelects[27] && swatchSelects[37] && swatchSelects[48] && swatchSelects[59]
                            && swatchSelects[69] && !swatchSelects[3] && !swatchSelects[10])) {
                int a[] = {3, 10, 18, 27, 37, 48, 59, 69};
                i = pickUnSelected(a);
            } else if ((swatchSelects[42] && swatchSelects[31] && swatchSelects[42] && swatchSelects[53] && swatchSelects[63]
                    && swatchSelects[72] && !swatchSelects[80] && !swatchSelects[87]) ||
                    (swatchSelects[42] && swatchSelects[31] && swatchSelects[42] && swatchSelects[53] && swatchSelects[63]
                            && swatchSelects[80] && !swatchSelects[72] && !swatchSelects[87]) ||
                    (swatchSelects[42] && swatchSelects[31] && swatchSelects[42] && swatchSelects[53] && swatchSelects[72]
                            && swatchSelects[80] && !swatchSelects[63] && !swatchSelects[87]) ||
                    (swatchSelects[42] && swatchSelects[31] && swatchSelects[42] && swatchSelects[63] && swatchSelects[72]
                            && swatchSelects[80] && !swatchSelects[53] && !swatchSelects[87]) ||
                    (swatchSelects[42] && swatchSelects[31] && swatchSelects[53] && swatchSelects[63] && swatchSelects[72]
                            && swatchSelects[80] && !swatchSelects[42] && !swatchSelects[87]) ||
                    (swatchSelects[42] && swatchSelects[42] && swatchSelects[53] && swatchSelects[63] && swatchSelects[72]
                            && swatchSelects[80] && !swatchSelects[31] && !swatchSelects[87]) ||
                    (swatchSelects[31] && swatchSelects[42] && swatchSelects[53] && swatchSelects[63] && swatchSelects[72]
                            && swatchSelects[80] && !swatchSelects[42] && !swatchSelects[87]) ||
                    (swatchSelects[42] && swatchSelects[31] && swatchSelects[42] && swatchSelects[53] && swatchSelects[63]
                            && swatchSelects[87] && !swatchSelects[72] && !swatchSelects[80]) ||
                    (swatchSelects[42] && swatchSelects[31] && swatchSelects[42] && swatchSelects[53] && swatchSelects[72]
                            && swatchSelects[87] && !swatchSelects[63] && !swatchSelects[80]) ||
                    (swatchSelects[42] && swatchSelects[31] && swatchSelects[42] && swatchSelects[63] && swatchSelects[72]
                            && swatchSelects[87] && !swatchSelects[53] && !swatchSelects[80]) ||
                    (swatchSelects[42] && swatchSelects[31] && swatchSelects[53] && swatchSelects[63] && swatchSelects[72]
                            && swatchSelects[87] && !swatchSelects[42] && !swatchSelects[80]) ||
                    (swatchSelects[42] && swatchSelects[42] && swatchSelects[53] && swatchSelects[63] && swatchSelects[72]
                            && swatchSelects[87] && !swatchSelects[31] && !swatchSelects[80]) ||
                    (swatchSelects[31] && swatchSelects[42] && swatchSelects[53] && swatchSelects[63] && swatchSelects[72]
                            && swatchSelects[87] && !swatchSelects[42] && !swatchSelects[80]) ||
                    (swatchSelects[42] && swatchSelects[31] && swatchSelects[42] && swatchSelects[53] && swatchSelects[80]
                            && swatchSelects[87] && !swatchSelects[63] && !swatchSelects[72]) ||
                    (swatchSelects[42] && swatchSelects[31] && swatchSelects[42] && swatchSelects[63] && swatchSelects[80]
                            && swatchSelects[87] && !swatchSelects[53] && !swatchSelects[72]) ||
                    (swatchSelects[42] && swatchSelects[31] && swatchSelects[53] && swatchSelects[63] && swatchSelects[80]
                            && swatchSelects[87] && !swatchSelects[42] && !swatchSelects[72]) ||
                    (swatchSelects[42] && swatchSelects[42] && swatchSelects[53] && swatchSelects[63] && swatchSelects[80]
                            && swatchSelects[87] && !swatchSelects[31] && !swatchSelects[72]) ||
                    (swatchSelects[31] && swatchSelects[42] && swatchSelects[53] && swatchSelects[63] && swatchSelects[80]
                            && swatchSelects[87] && !swatchSelects[42] && !swatchSelects[72]) ||
                    (swatchSelects[42] && swatchSelects[31] && swatchSelects[42] && swatchSelects[72] && swatchSelects[80]
                            && swatchSelects[87] && !swatchSelects[53] && !swatchSelects[63]) ||
                    (swatchSelects[42] && swatchSelects[31] && swatchSelects[53] && swatchSelects[72] && swatchSelects[80]
                            && swatchSelects[87] && !swatchSelects[42] && !swatchSelects[63]) ||
                    (swatchSelects[42] && swatchSelects[42] && swatchSelects[53] && swatchSelects[72] && swatchSelects[80]
                            && swatchSelects[87] && !swatchSelects[31] && !swatchSelects[63]) ||
                    (swatchSelects[31] && swatchSelects[42] && swatchSelects[53] && swatchSelects[72] && swatchSelects[80]
                            && swatchSelects[87] && !swatchSelects[42] && !swatchSelects[63]) ||
                    (swatchSelects[42] && swatchSelects[31] && swatchSelects[63] && swatchSelects[72] && swatchSelects[80]
                            && swatchSelects[87] && !swatchSelects[42] && !swatchSelects[53]) ||
                    (swatchSelects[42] && swatchSelects[42] && swatchSelects[63] && swatchSelects[72] && swatchSelects[80]
                            && swatchSelects[87] && !swatchSelects[31] && !swatchSelects[53]) ||
                    (swatchSelects[31] && swatchSelects[42] && swatchSelects[63] && swatchSelects[72] && swatchSelects[80]
                            && swatchSelects[87] && !swatchSelects[42] && !swatchSelects[53]) ||
                    (swatchSelects[42] && swatchSelects[53] && swatchSelects[63] && swatchSelects[72] && swatchSelects[80]
                            && swatchSelects[87] && !swatchSelects[31] && !swatchSelects[42]) ||
                    (swatchSelects[31] && swatchSelects[53] && swatchSelects[63] && swatchSelects[72] && swatchSelects[80]
                            && swatchSelects[87] && !swatchSelects[42] && !swatchSelects[42]) ||
                    (swatchSelects[42] && swatchSelects[53] && swatchSelects[63] && swatchSelects[72] && swatchSelects[80]
                            && swatchSelects[87] && !swatchSelects[42] && !swatchSelects[31])) {
                int a[] = {42, 31, 42, 53, 63, 72, 80, 87};
                i = pickUnSelected(a);
            }
        }

        if (i >= 0) {
            return i;
        } else {
            return -1;
        }
    }

    private int preventStep2_4() {
        int i = -1;
        if (boardRadius == 5) {
            if ((swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25]
                    && swatchSelects[26] && swatchSelects[27] && !swatchSelects[28] && !swatchSelects[29]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25]
                            && swatchSelects[26] && swatchSelects[28] && !swatchSelects[27] && !swatchSelects[29]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25]
                            && swatchSelects[27] && swatchSelects[28] && !swatchSelects[26] && !swatchSelects[29]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[26]
                            && swatchSelects[27] && swatchSelects[28] && !swatchSelects[25] && !swatchSelects[29]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[25] && swatchSelects[26]
                            && swatchSelects[27] && swatchSelects[28] && !swatchSelects[24] && !swatchSelects[29]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26]
                            && swatchSelects[27] && swatchSelects[28] && !swatchSelects[23] && !swatchSelects[29]) ||
                    (swatchSelects[21] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26]
                            && swatchSelects[27] && swatchSelects[28] && !swatchSelects[22] && !swatchSelects[29]) ||
                    (swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26]
                            && swatchSelects[27] && swatchSelects[28] && !swatchSelects[21] && !swatchSelects[29]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25]
                            && swatchSelects[26] && swatchSelects[29] && !swatchSelects[27] && !swatchSelects[28]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25]
                            && swatchSelects[27] && swatchSelects[29] && !swatchSelects[26] && !swatchSelects[28]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[26]
                            && swatchSelects[27] && swatchSelects[29] && !swatchSelects[25] && !swatchSelects[28]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[25] && swatchSelects[26]
                            && swatchSelects[27] && swatchSelects[29] && !swatchSelects[24] && !swatchSelects[28]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26]
                            && swatchSelects[27] && swatchSelects[29] && !swatchSelects[23] && !swatchSelects[28]) ||
                    (swatchSelects[21] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26]
                            && swatchSelects[27] && swatchSelects[29] && !swatchSelects[22] && !swatchSelects[28]) ||
                    (swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26]
                            && swatchSelects[27] && swatchSelects[29] && !swatchSelects[21] && !swatchSelects[28]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[26] && !swatchSelects[27]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[26]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[25] && !swatchSelects[27]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[25] && swatchSelects[26]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[24] && !swatchSelects[27]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[23] && !swatchSelects[27]) ||
                    (swatchSelects[21] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[22] && !swatchSelects[27]) ||
                    (swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[21] && !swatchSelects[27]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[27]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[25] && !swatchSelects[26]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[25] && swatchSelects[27]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[24] && !swatchSelects[26]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[24] && swatchSelects[25] && swatchSelects[27]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[23] && !swatchSelects[26]) ||
                    (swatchSelects[21] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && swatchSelects[27]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[22] && !swatchSelects[26]) ||
                    (swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && swatchSelects[27]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[21] && !swatchSelects[26]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[23] && swatchSelects[26] && swatchSelects[27]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[24] && !swatchSelects[25]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[24] && swatchSelects[26] && swatchSelects[27]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[23] && !swatchSelects[25]) ||
                    (swatchSelects[21] && swatchSelects[23] && swatchSelects[24] && swatchSelects[26] && swatchSelects[27]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[22] && !swatchSelects[25]) ||
                    (swatchSelects[22] && swatchSelects[23] && swatchSelects[24] && swatchSelects[26] && swatchSelects[27]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[21] && !swatchSelects[25]) ||
                    (swatchSelects[21] && swatchSelects[22] && swatchSelects[25] && swatchSelects[26] && swatchSelects[27]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[23] && !swatchSelects[24]) ||
                    (swatchSelects[21] && swatchSelects[23] && swatchSelects[25] && swatchSelects[26] && swatchSelects[27]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[22] && !swatchSelects[24]) ||
                    (swatchSelects[22] && swatchSelects[23] && swatchSelects[25] && swatchSelects[26] && swatchSelects[27]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[21] && !swatchSelects[24]) ||
                    (swatchSelects[21] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26] && swatchSelects[27]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[22] && !swatchSelects[23]) ||
                    (swatchSelects[22] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26] && swatchSelects[27]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[21] && !swatchSelects[23]) ||
                    (swatchSelects[23] && swatchSelects[24] && swatchSelects[25] && swatchSelects[26] && swatchSelects[27]
                            && swatchSelects[28] && swatchSelects[29] && !swatchSelects[21] && !swatchSelects[22])) {
                int a[] = {21, 22, 23, 24, 25, 26, 27, 28, 29};
                i = pickUnSelected(a);
            } else if ((swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65]
                    && swatchSelects[66] && swatchSelects[67] && !swatchSelects[68] && !swatchSelects[69]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65]
                            && swatchSelects[66] && swatchSelects[68] && !swatchSelects[67] && !swatchSelects[69]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65]
                            && swatchSelects[67] && swatchSelects[68] && !swatchSelects[66] && !swatchSelects[69]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[66]
                            && swatchSelects[67] && swatchSelects[68] && !swatchSelects[65] && !swatchSelects[69]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[65] && swatchSelects[66]
                            && swatchSelects[67] && swatchSelects[68] && !swatchSelects[64] && !swatchSelects[69]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[64] && swatchSelects[65] && swatchSelects[66]
                            && swatchSelects[67] && swatchSelects[68] && !swatchSelects[63] && !swatchSelects[69]) ||
                    (swatchSelects[61] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65] && swatchSelects[66]
                            && swatchSelects[67] && swatchSelects[68] && !swatchSelects[62] && !swatchSelects[69]) ||
                    (swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65] && swatchSelects[66]
                            && swatchSelects[67] && swatchSelects[68] && !swatchSelects[61] && !swatchSelects[69]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65]
                            && swatchSelects[66] && swatchSelects[69] && !swatchSelects[67] && !swatchSelects[68]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65]
                            && swatchSelects[67] && swatchSelects[69] && !swatchSelects[66] && !swatchSelects[68]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[66]
                            && swatchSelects[67] && swatchSelects[69] && !swatchSelects[65] && !swatchSelects[68]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[65] && swatchSelects[66]
                            && swatchSelects[67] && swatchSelects[69] && !swatchSelects[64] && !swatchSelects[68]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[64] && swatchSelects[65] && swatchSelects[66]
                            && swatchSelects[67] && swatchSelects[69] && !swatchSelects[63] && !swatchSelects[68]) ||
                    (swatchSelects[61] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65] && swatchSelects[66]
                            && swatchSelects[67] && swatchSelects[69] && !swatchSelects[62] && !swatchSelects[68]) ||
                    (swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65] && swatchSelects[66]
                            && swatchSelects[67] && swatchSelects[69] && !swatchSelects[61] && !swatchSelects[68]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[66] && !swatchSelects[67]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[66]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[65] && !swatchSelects[67]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[65] && swatchSelects[66]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[64] && !swatchSelects[67]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[64] && swatchSelects[65] && swatchSelects[66]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[63] && !swatchSelects[67]) ||
                    (swatchSelects[61] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65] && swatchSelects[66]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[62] && !swatchSelects[67]) ||
                    (swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65] && swatchSelects[66]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[61] && !swatchSelects[67]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[67]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[65] && !swatchSelects[66]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[65] && swatchSelects[67]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[64] && !swatchSelects[66]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[64] && swatchSelects[65] && swatchSelects[67]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[63] && !swatchSelects[66]) ||
                    (swatchSelects[61] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65] && swatchSelects[67]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[62] && !swatchSelects[66]) ||
                    (swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[65] && swatchSelects[67]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[61] && !swatchSelects[66]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[63] && swatchSelects[66] && swatchSelects[67]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[64] && !swatchSelects[65]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[64] && swatchSelects[66] && swatchSelects[67]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[63] && !swatchSelects[65]) ||
                    (swatchSelects[61] && swatchSelects[63] && swatchSelects[64] && swatchSelects[66] && swatchSelects[67]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[62] && !swatchSelects[65]) ||
                    (swatchSelects[62] && swatchSelects[63] && swatchSelects[64] && swatchSelects[66] && swatchSelects[67]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[61] && !swatchSelects[65]) ||
                    (swatchSelects[61] && swatchSelects[62] && swatchSelects[65] && swatchSelects[66] && swatchSelects[67]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[63] && !swatchSelects[64]) ||
                    (swatchSelects[61] && swatchSelects[63] && swatchSelects[65] && swatchSelects[66] && swatchSelects[67]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[62] && !swatchSelects[64]) ||
                    (swatchSelects[62] && swatchSelects[63] && swatchSelects[65] && swatchSelects[66] && swatchSelects[67]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[61] && !swatchSelects[64]) ||
                    (swatchSelects[61] && swatchSelects[64] && swatchSelects[65] && swatchSelects[66] && swatchSelects[67]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[62] && !swatchSelects[63]) ||
                    (swatchSelects[62] && swatchSelects[64] && swatchSelects[65] && swatchSelects[66] && swatchSelects[67]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[61] && !swatchSelects[63]) ||
                    (swatchSelects[63] && swatchSelects[64] && swatchSelects[65] && swatchSelects[66] && swatchSelects[67]
                            && swatchSelects[68] && swatchSelects[69] && !swatchSelects[61] && !swatchSelects[62])) {
                int a[] = {61, 62, 63, 64, 65, 66, 67, 68, 69};
                i = pickUnSelected(a);
            } else if ((swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33]
                    && swatchSelects[43] && swatchSelects[53] && !swatchSelects[62] && !swatchSelects[70]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33]
                            && swatchSelects[43] && swatchSelects[62] && !swatchSelects[53] && !swatchSelects[70]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33]
                            && swatchSelects[53] && swatchSelects[62] && !swatchSelects[43] && !swatchSelects[70]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[43]
                            && swatchSelects[53] && swatchSelects[62] && !swatchSelects[33] && !swatchSelects[70]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[33] && swatchSelects[43]
                            && swatchSelects[53] && swatchSelects[62] && !swatchSelects[24] && !swatchSelects[70]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[24] && swatchSelects[33] && swatchSelects[43]
                            && swatchSelects[53] && swatchSelects[62] && !swatchSelects[16] && !swatchSelects[70]) ||
                    (swatchSelects[3] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33] && swatchSelects[43]
                            && swatchSelects[53] && swatchSelects[62] && !swatchSelects[9] && !swatchSelects[70]) ||
                    (swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33] && swatchSelects[43]
                            && swatchSelects[53] && swatchSelects[62] && !swatchSelects[3] && !swatchSelects[70]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33]
                            && swatchSelects[43] && swatchSelects[70] && !swatchSelects[53] && !swatchSelects[62]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33]
                            && swatchSelects[53] && swatchSelects[70] && !swatchSelects[43] && !swatchSelects[62]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[43]
                            && swatchSelects[53] && swatchSelects[70] && !swatchSelects[33] && !swatchSelects[62]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[33] && swatchSelects[43]
                            && swatchSelects[53] && swatchSelects[70] && !swatchSelects[24] && !swatchSelects[62]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[24] && swatchSelects[33] && swatchSelects[43]
                            && swatchSelects[53] && swatchSelects[70] && !swatchSelects[16] && !swatchSelects[62]) ||
                    (swatchSelects[3] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33] && swatchSelects[43]
                            && swatchSelects[53] && swatchSelects[70] && !swatchSelects[9] && !swatchSelects[62]) ||
                    (swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33] && swatchSelects[43]
                            && swatchSelects[53] && swatchSelects[70] && !swatchSelects[3] && !swatchSelects[62]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[43] && !swatchSelects[53]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[43]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[33] && !swatchSelects[53]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[33] && swatchSelects[43]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[24] && !swatchSelects[53]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[24] && swatchSelects[33] && swatchSelects[43]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[16] && !swatchSelects[53]) ||
                    (swatchSelects[3] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33] && swatchSelects[43]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[9] && !swatchSelects[53]) ||
                    (swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33] && swatchSelects[43]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[3] && !swatchSelects[53]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[53]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[33] && !swatchSelects[43]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[33] && swatchSelects[53]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[24] && !swatchSelects[43]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[24] && swatchSelects[33] && swatchSelects[53]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[16] && !swatchSelects[43]) ||
                    (swatchSelects[3] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33] && swatchSelects[53]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[9] && !swatchSelects[43]) ||
                    (swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[33] && swatchSelects[53]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[3] && !swatchSelects[43]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[16] && swatchSelects[43] && swatchSelects[53]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[24] && !swatchSelects[33]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[24] && swatchSelects[43] && swatchSelects[53]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[16] && !swatchSelects[33]) ||
                    (swatchSelects[3] && swatchSelects[16] && swatchSelects[24] && swatchSelects[43] && swatchSelects[53]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[9] && !swatchSelects[33]) ||
                    (swatchSelects[9] && swatchSelects[16] && swatchSelects[24] && swatchSelects[43] && swatchSelects[53]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[3] && !swatchSelects[33]) ||
                    (swatchSelects[3] && swatchSelects[9] && swatchSelects[33] && swatchSelects[43] && swatchSelects[53]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[16] && !swatchSelects[24]) ||
                    (swatchSelects[3] && swatchSelects[16] && swatchSelects[33] && swatchSelects[43] && swatchSelects[53]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[9] && !swatchSelects[24]) ||
                    (swatchSelects[9] && swatchSelects[16] && swatchSelects[33] && swatchSelects[43] && swatchSelects[53]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[3] && !swatchSelects[24]) ||
                    (swatchSelects[3] && swatchSelects[24] && swatchSelects[33] && swatchSelects[43] && swatchSelects[53]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[9] && !swatchSelects[16]) ||
                    (swatchSelects[9] && swatchSelects[24] && swatchSelects[33] && swatchSelects[43] && swatchSelects[53]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[3] && !swatchSelects[16]) ||
                    (swatchSelects[16] && swatchSelects[24] && swatchSelects[33] && swatchSelects[43] && swatchSelects[53]
                            && swatchSelects[62] && swatchSelects[70] && !swatchSelects[3] && !swatchSelects[9])) {
                int a[] = {3, 9, 16, 24, 33, 43, 53, 62, 70};
                i = pickUnSelected(a);
            } else if ((swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57]
                    && swatchSelects[66] && swatchSelects[74] && !swatchSelects[81] && !swatchSelects[87]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57]
                            && swatchSelects[66] && swatchSelects[81] && !swatchSelects[74] && !swatchSelects[87]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57]
                            && swatchSelects[74] && swatchSelects[81] && !swatchSelects[66] && !swatchSelects[87]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[66]
                            && swatchSelects[74] && swatchSelects[81] && !swatchSelects[57] && !swatchSelects[87]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[57] && swatchSelects[66]
                            && swatchSelects[74] && swatchSelects[81] && !swatchSelects[47] && !swatchSelects[87]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[47] && swatchSelects[57] && swatchSelects[66]
                            && swatchSelects[74] && swatchSelects[81] && !swatchSelects[37] && !swatchSelects[87]) ||
                    (swatchSelects[20] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57] && swatchSelects[66]
                            && swatchSelects[74] && swatchSelects[81] && !swatchSelects[28] && !swatchSelects[87]) ||
                    (swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57] && swatchSelects[66]
                            && swatchSelects[74] && swatchSelects[81] && !swatchSelects[20] && !swatchSelects[87]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57]
                            && swatchSelects[66] && swatchSelects[87] && !swatchSelects[74] && !swatchSelects[81]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57]
                            && swatchSelects[74] && swatchSelects[87] && !swatchSelects[66] && !swatchSelects[81]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[66]
                            && swatchSelects[74] && swatchSelects[87] && !swatchSelects[57] && !swatchSelects[81]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[57] && swatchSelects[66]
                            && swatchSelects[74] && swatchSelects[87] && !swatchSelects[47] && !swatchSelects[81]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[47] && swatchSelects[57] && swatchSelects[66]
                            && swatchSelects[74] && swatchSelects[87] && !swatchSelects[37] && !swatchSelects[81]) ||
                    (swatchSelects[20] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57] && swatchSelects[66]
                            && swatchSelects[74] && swatchSelects[87] && !swatchSelects[28] && !swatchSelects[81]) ||
                    (swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57] && swatchSelects[66]
                            && swatchSelects[74] && swatchSelects[87] && !swatchSelects[20] && !swatchSelects[81]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[66] && !swatchSelects[74]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[66]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[57] && !swatchSelects[74]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[57] && swatchSelects[66]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[47] && !swatchSelects[74]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[47] && swatchSelects[57] && swatchSelects[66]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[37] && !swatchSelects[74]) ||
                    (swatchSelects[20] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57] && swatchSelects[66]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[28] && !swatchSelects[74]) ||
                    (swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57] && swatchSelects[66]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[20] && !swatchSelects[74]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[74]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[57] && !swatchSelects[66]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[57] && swatchSelects[74]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[47] && !swatchSelects[66]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[47] && swatchSelects[57] && swatchSelects[74]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[37] && !swatchSelects[66]) ||
                    (swatchSelects[20] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57] && swatchSelects[74]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[28] && !swatchSelects[66]) ||
                    (swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[57] && swatchSelects[74]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[20] && !swatchSelects[66]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[37] && swatchSelects[66] && swatchSelects[74]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[47] && !swatchSelects[57]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[47] && swatchSelects[66] && swatchSelects[74]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[37] && !swatchSelects[57]) ||
                    (swatchSelects[20] && swatchSelects[37] && swatchSelects[47] && swatchSelects[66] && swatchSelects[74]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[28] && !swatchSelects[57]) ||
                    (swatchSelects[28] && swatchSelects[37] && swatchSelects[47] && swatchSelects[66] && swatchSelects[74]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[20] && !swatchSelects[57]) ||
                    (swatchSelects[20] && swatchSelects[28] && swatchSelects[57] && swatchSelects[66] && swatchSelects[74]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[37] && !swatchSelects[47]) ||
                    (swatchSelects[20] && swatchSelects[37] && swatchSelects[57] && swatchSelects[66] && swatchSelects[74]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[28] && !swatchSelects[47]) ||
                    (swatchSelects[28] && swatchSelects[37] && swatchSelects[57] && swatchSelects[66] && swatchSelects[74]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[20] && !swatchSelects[47]) ||
                    (swatchSelects[20] && swatchSelects[47] && swatchSelects[57] && swatchSelects[66] && swatchSelects[74]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[28] && !swatchSelects[37]) ||
                    (swatchSelects[28] && swatchSelects[47] && swatchSelects[57] && swatchSelects[66] && swatchSelects[74]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[20] && !swatchSelects[37]) ||
                    (swatchSelects[37] && swatchSelects[47] && swatchSelects[57] && swatchSelects[66] && swatchSelects[74]
                            && swatchSelects[81] && swatchSelects[87] && !swatchSelects[20] && !swatchSelects[28])) {
                int a[] = {20, 28, 37, 47, 57, 66, 74, 81, 87};
                i = pickUnSelected(a);
            } else if ((swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36]
                    && swatchSelects[47] && swatchSelects[58] && !swatchSelects[68] && !swatchSelects[77]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36]
                            && swatchSelects[47] && swatchSelects[68] && !swatchSelects[58] && !swatchSelects[77]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36]
                            && swatchSelects[58] && swatchSelects[68] && !swatchSelects[47] && !swatchSelects[77]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[47]
                            && swatchSelects[58] && swatchSelects[68] && !swatchSelects[36] && !swatchSelects[77]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[36] && swatchSelects[47]
                            && swatchSelects[58] && swatchSelects[68] && !swatchSelects[26] && !swatchSelects[77]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[26] && swatchSelects[36] && swatchSelects[47]
                            && swatchSelects[58] && swatchSelects[68] && !swatchSelects[17] && !swatchSelects[77]) ||
                    (swatchSelects[2] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36] && swatchSelects[47]
                            && swatchSelects[58] && swatchSelects[68] && !swatchSelects[9] && !swatchSelects[77]) ||
                    (swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36] && swatchSelects[47]
                            && swatchSelects[58] && swatchSelects[68] && !swatchSelects[2] && !swatchSelects[77]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36]
                            && swatchSelects[47] && swatchSelects[77] && !swatchSelects[58] && !swatchSelects[68]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36]
                            && swatchSelects[58] && swatchSelects[77] && !swatchSelects[47] && !swatchSelects[68]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[47]
                            && swatchSelects[58] && swatchSelects[77] && !swatchSelects[36] && !swatchSelects[68]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[36] && swatchSelects[47]
                            && swatchSelects[58] && swatchSelects[77] && !swatchSelects[26] && !swatchSelects[68]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[26] && swatchSelects[36] && swatchSelects[47]
                            && swatchSelects[58] && swatchSelects[77] && !swatchSelects[17] && !swatchSelects[68]) ||
                    (swatchSelects[2] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36] && swatchSelects[47]
                            && swatchSelects[58] && swatchSelects[77] && !swatchSelects[9] && !swatchSelects[68]) ||
                    (swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36] && swatchSelects[47]
                            && swatchSelects[58] && swatchSelects[77] && !swatchSelects[2] && !swatchSelects[68]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[47] && !swatchSelects[58]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[47]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[36] && !swatchSelects[58]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[36] && swatchSelects[47]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[26] && !swatchSelects[58]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[26] && swatchSelects[36] && swatchSelects[47]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[17] && !swatchSelects[58]) ||
                    (swatchSelects[2] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36] && swatchSelects[47]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[9] && !swatchSelects[58]) ||
                    (swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36] && swatchSelects[47]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[2] && !swatchSelects[58]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[58]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[36] && !swatchSelects[47]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[36] && swatchSelects[58]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[26] && !swatchSelects[47]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[26] && swatchSelects[36] && swatchSelects[58]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[17] && !swatchSelects[47]) ||
                    (swatchSelects[2] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36] && swatchSelects[58]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[9] && !swatchSelects[47]) ||
                    (swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[36] && swatchSelects[58]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[2] && !swatchSelects[47]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[17] && swatchSelects[47] && swatchSelects[58]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[26] && !swatchSelects[36]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[26] && swatchSelects[47] && swatchSelects[58]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[17] && !swatchSelects[36]) ||
                    (swatchSelects[2] && swatchSelects[17] && swatchSelects[26] && swatchSelects[47] && swatchSelects[58]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[9] && !swatchSelects[36]) ||
                    (swatchSelects[9] && swatchSelects[17] && swatchSelects[26] && swatchSelects[47] && swatchSelects[58]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[2] && !swatchSelects[36]) ||
                    (swatchSelects[2] && swatchSelects[9] && swatchSelects[36] && swatchSelects[47] && swatchSelects[58]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[17] && !swatchSelects[26]) ||
                    (swatchSelects[2] && swatchSelects[17] && swatchSelects[36] && swatchSelects[47] && swatchSelects[58]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[9] && !swatchSelects[26]) ||
                    (swatchSelects[9] && swatchSelects[17] && swatchSelects[36] && swatchSelects[47] && swatchSelects[58]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[2] && !swatchSelects[26]) ||
                    (swatchSelects[2] && swatchSelects[26] && swatchSelects[36] && swatchSelects[47] && swatchSelects[58]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[9] && !swatchSelects[17]) ||
                    (swatchSelects[9] && swatchSelects[26] && swatchSelects[36] && swatchSelects[47] && swatchSelects[58]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[2] && !swatchSelects[17]) ||
                    (swatchSelects[17] && swatchSelects[26] && swatchSelects[36] && swatchSelects[47] && swatchSelects[58]
                            && swatchSelects[68] && swatchSelects[77] && !swatchSelects[2] && !swatchSelects[9])) {
                int a[] = {2, 9, 17, 26, 36, 47, 58, 68, 77};
                i = pickUnSelected(a);
            } else if ((swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54]
                    && swatchSelects[64] && swatchSelects[73] && !swatchSelects[81] && !swatchSelects[88]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54]
                            && swatchSelects[64] && swatchSelects[81] && !swatchSelects[73] && !swatchSelects[88]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54]
                            && swatchSelects[73] && swatchSelects[81] && !swatchSelects[64] && !swatchSelects[88]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[64]
                            && swatchSelects[73] && swatchSelects[81] && !swatchSelects[54] && !swatchSelects[88]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[54] && swatchSelects[64]
                            && swatchSelects[73] && swatchSelects[81] && !swatchSelects[43] && !swatchSelects[88]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[43] && swatchSelects[54] && swatchSelects[64]
                            && swatchSelects[73] && swatchSelects[81] && !swatchSelects[32] && !swatchSelects[88]) ||
                    (swatchSelects[13] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54] && swatchSelects[64]
                            && swatchSelects[73] && swatchSelects[81] && !swatchSelects[22] && !swatchSelects[88]) ||
                    (swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54] && swatchSelects[64]
                            && swatchSelects[73] && swatchSelects[81] && !swatchSelects[13] && !swatchSelects[88]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54]
                            && swatchSelects[64] && swatchSelects[88] && !swatchSelects[73] && !swatchSelects[81]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54]
                            && swatchSelects[73] && swatchSelects[88] && !swatchSelects[64] && !swatchSelects[81]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[64]
                            && swatchSelects[73] && swatchSelects[88] && !swatchSelects[54] && !swatchSelects[81]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[54] && swatchSelects[64]
                            && swatchSelects[73] && swatchSelects[88] && !swatchSelects[43] && !swatchSelects[81]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[43] && swatchSelects[54] && swatchSelects[64]
                            && swatchSelects[73] && swatchSelects[88] && !swatchSelects[32] && !swatchSelects[81]) ||
                    (swatchSelects[13] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54] && swatchSelects[64]
                            && swatchSelects[73] && swatchSelects[88] && !swatchSelects[22] && !swatchSelects[81]) ||
                    (swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54] && swatchSelects[64]
                            && swatchSelects[73] && swatchSelects[88] && !swatchSelects[13] && !swatchSelects[81]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[64] && !swatchSelects[73]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[64]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[54] && !swatchSelects[73]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[54] && swatchSelects[64]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[43] && !swatchSelects[73]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[43] && swatchSelects[54] && swatchSelects[64]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[32] && !swatchSelects[73]) ||
                    (swatchSelects[13] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54] && swatchSelects[64]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[22] && !swatchSelects[73]) ||
                    (swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54] && swatchSelects[64]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[13] && !swatchSelects[73]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[73]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[54] && !swatchSelects[64]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[54] && swatchSelects[73]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[43] && !swatchSelects[64]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[43] && swatchSelects[54] && swatchSelects[73]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[32] && !swatchSelects[64]) ||
                    (swatchSelects[13] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54] && swatchSelects[73]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[22] && !swatchSelects[64]) ||
                    (swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[54] && swatchSelects[73]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[13] && !swatchSelects[64]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[32] && swatchSelects[64] && swatchSelects[73]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[43] && !swatchSelects[54]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[43] && swatchSelects[64] && swatchSelects[73]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[32] && !swatchSelects[54]) ||
                    (swatchSelects[13] && swatchSelects[32] && swatchSelects[43] && swatchSelects[64] && swatchSelects[73]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[22] && !swatchSelects[54]) ||
                    (swatchSelects[22] && swatchSelects[32] && swatchSelects[43] && swatchSelects[64] && swatchSelects[73]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[13] && !swatchSelects[54]) ||
                    (swatchSelects[13] && swatchSelects[22] && swatchSelects[54] && swatchSelects[64] && swatchSelects[73]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[32] && !swatchSelects[43]) ||
                    (swatchSelects[13] && swatchSelects[32] && swatchSelects[54] && swatchSelects[64] && swatchSelects[73]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[22] && !swatchSelects[43]) ||
                    (swatchSelects[22] && swatchSelects[32] && swatchSelects[54] && swatchSelects[64] && swatchSelects[73]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[13] && !swatchSelects[43]) ||
                    (swatchSelects[13] && swatchSelects[43] && swatchSelects[54] && swatchSelects[64] && swatchSelects[73]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[22] && !swatchSelects[32]) ||
                    (swatchSelects[22] && swatchSelects[43] && swatchSelects[54] && swatchSelects[64] && swatchSelects[73]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[13] && !swatchSelects[32]) ||
                    (swatchSelects[32] && swatchSelects[43] && swatchSelects[54] && swatchSelects[64] && swatchSelects[73]
                            && swatchSelects[81] && swatchSelects[88] && !swatchSelects[13] && !swatchSelects[22])) {
                int a[] = {13, 22, 32, 43, 54, 64, 73, 81, 88};
                i = pickUnSelected(a);
            }
        }

        if (i >= 0) {
            return i;
        } else {
            return -1;
        }
    }

    private int preventStep2_5() {
        int i = -1;
        if (boardRadius == 5) {
            if ((swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35]
                    && swatchSelects[36] && swatchSelects[37] && !swatchSelects[38] && !swatchSelects[39]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35]
                            && swatchSelects[36] && swatchSelects[38] && !swatchSelects[37] && !swatchSelects[39]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35]
                            && swatchSelects[37] && swatchSelects[38] && !swatchSelects[36] && !swatchSelects[39]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[36]
                            && swatchSelects[37] && swatchSelects[38] && !swatchSelects[35] && !swatchSelects[39]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[35] && swatchSelects[36]
                            && swatchSelects[37] && swatchSelects[38] && !swatchSelects[34] && !swatchSelects[39]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36]
                            && swatchSelects[37] && swatchSelects[38] && !swatchSelects[33] && !swatchSelects[39]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36]
                            && swatchSelects[37] && swatchSelects[38] && !swatchSelects[32] && !swatchSelects[39]) ||
                    (swatchSelects[30] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36]
                            && swatchSelects[37] && swatchSelects[38] && !swatchSelects[31] && !swatchSelects[39]) ||
                    (swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36]
                            && swatchSelects[37] && swatchSelects[38] && !swatchSelects[30] && !swatchSelects[39]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35]
                            && swatchSelects[36] && swatchSelects[39] && !swatchSelects[37] && !swatchSelects[38]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35]
                            && swatchSelects[37] && swatchSelects[39] && !swatchSelects[36] && !swatchSelects[38]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[36]
                            && swatchSelects[37] && swatchSelects[39] && !swatchSelects[35] && !swatchSelects[38]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[35] && swatchSelects[36]
                            && swatchSelects[37] && swatchSelects[39] && !swatchSelects[34] && !swatchSelects[38]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36]
                            && swatchSelects[37] && swatchSelects[39] && !swatchSelects[33] && !swatchSelects[38]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36]
                            && swatchSelects[37] && swatchSelects[39] && !swatchSelects[32] && !swatchSelects[38]) ||
                    (swatchSelects[30] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36]
                            && swatchSelects[37] && swatchSelects[39] && !swatchSelects[31] && !swatchSelects[38]) ||
                    (swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36]
                            && swatchSelects[37] && swatchSelects[39] && !swatchSelects[30] && !swatchSelects[38]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[36] && !swatchSelects[37]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[36]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[35] && !swatchSelects[37]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[35] && swatchSelects[36]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[34] && !swatchSelects[37]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[33] && !swatchSelects[37]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[32] && !swatchSelects[37]) ||
                    (swatchSelects[30] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[31] && !swatchSelects[37]) ||
                    (swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[30] && !swatchSelects[37]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[35] && !swatchSelects[36]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[35] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[34] && !swatchSelects[36]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[34] && swatchSelects[35] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[33] && !swatchSelects[36]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[32] && !swatchSelects[36]) ||
                    (swatchSelects[30] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[31] && !swatchSelects[36]) ||
                    (swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[30] && !swatchSelects[36]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[36] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[34] && !swatchSelects[35]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[34] && swatchSelects[36] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[33] && !swatchSelects[35]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[33] && swatchSelects[34] && swatchSelects[36] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[32] && !swatchSelects[35]) ||
                    (swatchSelects[30] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[36] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[31] && !swatchSelects[35]) ||
                    (swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[36] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[30] && !swatchSelects[35]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[32] && swatchSelects[35] && swatchSelects[36] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[33] && !swatchSelects[34]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[33] && swatchSelects[35] && swatchSelects[36] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[32] && !swatchSelects[34]) ||
                    (swatchSelects[30] && swatchSelects[32] && swatchSelects[33] && swatchSelects[35] && swatchSelects[36] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[31] && !swatchSelects[34]) ||
                    (swatchSelects[31] && swatchSelects[32] && swatchSelects[33] && swatchSelects[35] && swatchSelects[36] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[30] && !swatchSelects[34]) ||
                    (swatchSelects[30] && swatchSelects[31] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[32] && !swatchSelects[33]) ||
                    (swatchSelects[30] && swatchSelects[32] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[31] && !swatchSelects[33]) ||
                    (swatchSelects[31] && swatchSelects[32] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[30] && !swatchSelects[33]) ||
                    (swatchSelects[30] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[31] && !swatchSelects[32]) ||
                    (swatchSelects[31] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[30] && !swatchSelects[32]) ||
                    (swatchSelects[32] && swatchSelects[33] && swatchSelects[34] && swatchSelects[35] && swatchSelects[36] && swatchSelects[37]
                            && swatchSelects[38] && swatchSelects[39] && !swatchSelects[30] && !swatchSelects[31])) {
                int a[] = {30, 31, 32, 33, 34, 35, 36, 37, 38, 39};
                i = pickUnSelected(a);
            } else if ((swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56]
                    && swatchSelects[57] && swatchSelects[58] && !swatchSelects[59] && !swatchSelects[60]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56]
                            && swatchSelects[57] && swatchSelects[59] && !swatchSelects[58] && !swatchSelects[60]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56]
                            && swatchSelects[58] && swatchSelects[59] && !swatchSelects[57] && !swatchSelects[60]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[57]
                            && swatchSelects[58] && swatchSelects[59] && !swatchSelects[56] && !swatchSelects[60]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[56] && swatchSelects[57]
                            && swatchSelects[58] && swatchSelects[59] && !swatchSelects[55] && !swatchSelects[60]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57]
                            && swatchSelects[58] && swatchSelects[59] && !swatchSelects[54] && !swatchSelects[60]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57]
                            && swatchSelects[58] && swatchSelects[59] && !swatchSelects[53] && !swatchSelects[60]) ||
                    (swatchSelects[51] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57]
                            && swatchSelects[58] && swatchSelects[59] && !swatchSelects[52] && !swatchSelects[60]) ||
                    (swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57]
                            && swatchSelects[58] && swatchSelects[59] && !swatchSelects[51] && !swatchSelects[60]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56]
                            && swatchSelects[57] && swatchSelects[60] && !swatchSelects[58] && !swatchSelects[59]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56]
                            && swatchSelects[58] && swatchSelects[60] && !swatchSelects[57] && !swatchSelects[59]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[57]
                            && swatchSelects[58] && swatchSelects[60] && !swatchSelects[56] && !swatchSelects[59]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[56] && swatchSelects[57]
                            && swatchSelects[58] && swatchSelects[60] && !swatchSelects[55] && !swatchSelects[59]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57]
                            && swatchSelects[58] && swatchSelects[60] && !swatchSelects[54] && !swatchSelects[59]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57]
                            && swatchSelects[58] && swatchSelects[60] && !swatchSelects[53] && !swatchSelects[59]) ||
                    (swatchSelects[51] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57]
                            && swatchSelects[58] && swatchSelects[60] && !swatchSelects[52] && !swatchSelects[59]) ||
                    (swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57]
                            && swatchSelects[58] && swatchSelects[60] && !swatchSelects[51] && !swatchSelects[59]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[57] && !swatchSelects[58]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[57]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[56] && !swatchSelects[58]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[56] && swatchSelects[57]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[55] && !swatchSelects[58]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[54] && !swatchSelects[58]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[53] && !swatchSelects[58]) ||
                    (swatchSelects[51] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[52] && !swatchSelects[58]) ||
                    (swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[51] && !swatchSelects[58]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[56] && !swatchSelects[57]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[56] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[55] && !swatchSelects[57]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[55] && swatchSelects[56] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[54] && !swatchSelects[57]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[53] && !swatchSelects[57]) ||
                    (swatchSelects[51] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[52] && !swatchSelects[57]) ||
                    (swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[51] && !swatchSelects[57]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[57] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[55] && !swatchSelects[56]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[55] && swatchSelects[57] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[54] && !swatchSelects[56]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[54] && swatchSelects[55] && swatchSelects[57] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[53] && !swatchSelects[56]) ||
                    (swatchSelects[51] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[57] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[52] && !swatchSelects[56]) ||
                    (swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[57] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[51] && !swatchSelects[56]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[53] && swatchSelects[56] && swatchSelects[57] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[54] && !swatchSelects[55]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[54] && swatchSelects[56] && swatchSelects[57] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[53] && !swatchSelects[55]) ||
                    (swatchSelects[51] && swatchSelects[53] && swatchSelects[54] && swatchSelects[56] && swatchSelects[57] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[52] && !swatchSelects[55]) ||
                    (swatchSelects[52] && swatchSelects[53] && swatchSelects[54] && swatchSelects[56] && swatchSelects[57] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[51] && !swatchSelects[55]) ||
                    (swatchSelects[51] && swatchSelects[52] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[53] && !swatchSelects[54]) ||
                    (swatchSelects[51] && swatchSelects[53] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[52] && !swatchSelects[54]) ||
                    (swatchSelects[52] && swatchSelects[53] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[51] && !swatchSelects[54]) ||
                    (swatchSelects[51] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[52] && !swatchSelects[53]) ||
                    (swatchSelects[52] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[51] && !swatchSelects[53]) ||
                    (swatchSelects[53] && swatchSelects[54] && swatchSelects[55] && swatchSelects[56] && swatchSelects[57] && swatchSelects[58]
                            && swatchSelects[59] && swatchSelects[60] && !swatchSelects[51] && !swatchSelects[52])) {
                int a[] = {51, 52, 53, 54, 55, 56, 57, 58, 59, 60};
                i = pickUnSelected(a);
            } else if ((swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44]
                    && swatchSelects[54] && swatchSelects[63] && !swatchSelects[71] && !swatchSelects[78]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44]
                            && swatchSelects[54] && swatchSelects[71] && !swatchSelects[63] && !swatchSelects[78]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44]
                            && swatchSelects[63] && swatchSelects[71] && !swatchSelects[54] && !swatchSelects[78]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[54]
                            && swatchSelects[63] && swatchSelects[71] && !swatchSelects[44] && !swatchSelects[78]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[44] && swatchSelects[54]
                            && swatchSelects[63] && swatchSelects[71] && !swatchSelects[34] && !swatchSelects[78]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54]
                            && swatchSelects[63] && swatchSelects[71] && !swatchSelects[25] && !swatchSelects[78]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54]
                            && swatchSelects[63] && swatchSelects[71] && !swatchSelects[17] && !swatchSelects[78]) ||
                    (swatchSelects[4] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54]
                            && swatchSelects[63] && swatchSelects[71] && !swatchSelects[10] && !swatchSelects[78]) ||
                    (swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54]
                            && swatchSelects[63] && swatchSelects[71] && !swatchSelects[4] && !swatchSelects[78]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44]
                            && swatchSelects[54] && swatchSelects[78] && !swatchSelects[63] && !swatchSelects[71]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44]
                            && swatchSelects[63] && swatchSelects[78] && !swatchSelects[54] && !swatchSelects[71]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[54]
                            && swatchSelects[63] && swatchSelects[78] && !swatchSelects[44] && !swatchSelects[71]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[44] && swatchSelects[54]
                            && swatchSelects[63] && swatchSelects[78] && !swatchSelects[34] && !swatchSelects[71]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54]
                            && swatchSelects[63] && swatchSelects[78] && !swatchSelects[25] && !swatchSelects[71]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54]
                            && swatchSelects[63] && swatchSelects[78] && !swatchSelects[17] && !swatchSelects[71]) ||
                    (swatchSelects[4] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54]
                            && swatchSelects[63] && swatchSelects[78] && !swatchSelects[10] && !swatchSelects[71]) ||
                    (swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54]
                            && swatchSelects[63] && swatchSelects[78] && !swatchSelects[4] && !swatchSelects[71]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[54] && !swatchSelects[63]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[54]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[44] && !swatchSelects[63]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[44] && swatchSelects[54]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[34] && !swatchSelects[63]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[25] && !swatchSelects[63]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[17] && !swatchSelects[63]) ||
                    (swatchSelects[4] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[10] && !swatchSelects[63]) ||
                    (swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[4] && !swatchSelects[63]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[44] && !swatchSelects[54]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[44] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[34] && !swatchSelects[54]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[34] && swatchSelects[44] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[25] && !swatchSelects[54]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[17] && !swatchSelects[54]) ||
                    (swatchSelects[4] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[10] && !swatchSelects[54]) ||
                    (swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[4] && !swatchSelects[54]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[54] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[34] && !swatchSelects[44]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[34] && swatchSelects[54] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[25] && !swatchSelects[44]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[25] && swatchSelects[34] && swatchSelects[54] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[17] && !swatchSelects[44]) ||
                    (swatchSelects[4] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[54] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[10] && !swatchSelects[44]) ||
                    (swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[54] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[4] && !swatchSelects[44]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[17] && swatchSelects[44] && swatchSelects[54] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[25] && !swatchSelects[34]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[25] && swatchSelects[44] && swatchSelects[54] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[17] && !swatchSelects[34]) ||
                    (swatchSelects[4] && swatchSelects[17] && swatchSelects[25] && swatchSelects[44] && swatchSelects[54] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[10] && !swatchSelects[34]) ||
                    (swatchSelects[10] && swatchSelects[17] && swatchSelects[25] && swatchSelects[44] && swatchSelects[54] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[4] && !swatchSelects[34]) ||
                    (swatchSelects[4] && swatchSelects[10] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[17] && !swatchSelects[25]) ||
                    (swatchSelects[4] && swatchSelects[17] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[10] && !swatchSelects[25]) ||
                    (swatchSelects[10] && swatchSelects[17] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[4] && !swatchSelects[25]) ||
                    (swatchSelects[4] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[10] && !swatchSelects[17]) ||
                    (swatchSelects[10] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[4] && !swatchSelects[17]) ||
                    (swatchSelects[17] && swatchSelects[25] && swatchSelects[34] && swatchSelects[44] && swatchSelects[54] && swatchSelects[63]
                            && swatchSelects[71] && swatchSelects[78] && !swatchSelects[4] && !swatchSelects[10])) {
                int a[] = {4, 10, 17, 25, 34, 44, 54, 63, 71, 78};
                i = pickUnSelected(a);
            } else if ((swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56]
                    && swatchSelects[65] && swatchSelects[73] && !swatchSelects[80] && !swatchSelects[86]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56]
                            && swatchSelects[65] && swatchSelects[80] && !swatchSelects[73] && !swatchSelects[86]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56]
                            && swatchSelects[73] && swatchSelects[80] && !swatchSelects[65] && !swatchSelects[86]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[65]
                            && swatchSelects[73] && swatchSelects[80] && !swatchSelects[56] && !swatchSelects[86]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[56] && swatchSelects[65]
                            && swatchSelects[73] && swatchSelects[80] && !swatchSelects[46] && !swatchSelects[86]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65]
                            && swatchSelects[73] && swatchSelects[80] && !swatchSelects[36] && !swatchSelects[86]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65]
                            && swatchSelects[73] && swatchSelects[80] && !swatchSelects[27] && !swatchSelects[86]) ||
                    (swatchSelects[12] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65]
                            && swatchSelects[73] && swatchSelects[80] && !swatchSelects[19] && !swatchSelects[86]) ||
                    (swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65]
                            && swatchSelects[73] && swatchSelects[80] && !swatchSelects[12] && !swatchSelects[86]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56]
                            && swatchSelects[65] && swatchSelects[86] && !swatchSelects[73] && !swatchSelects[80]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56]
                            && swatchSelects[73] && swatchSelects[86] && !swatchSelects[65] && !swatchSelects[80]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[65]
                            && swatchSelects[73] && swatchSelects[86] && !swatchSelects[56] && !swatchSelects[80]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[56] && swatchSelects[65]
                            && swatchSelects[73] && swatchSelects[86] && !swatchSelects[46] && !swatchSelects[80]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65]
                            && swatchSelects[73] && swatchSelects[86] && !swatchSelects[36] && !swatchSelects[80]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65]
                            && swatchSelects[73] && swatchSelects[86] && !swatchSelects[27] && !swatchSelects[80]) ||
                    (swatchSelects[12] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65]
                            && swatchSelects[73] && swatchSelects[86] && !swatchSelects[19] && !swatchSelects[80]) ||
                    (swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65]
                            && swatchSelects[73] && swatchSelects[86] && !swatchSelects[12] && !swatchSelects[80]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[65] && !swatchSelects[73]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[65]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[56] && !swatchSelects[73]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[56] && swatchSelects[65]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[46] && !swatchSelects[73]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[36] && !swatchSelects[73]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[27] && !swatchSelects[73]) ||
                    (swatchSelects[12] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[19] && !swatchSelects[73]) ||
                    (swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[12] && !swatchSelects[73]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[56] && !swatchSelects[65]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[56] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[46] && !swatchSelects[65]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[46] && swatchSelects[56] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[36] && !swatchSelects[65]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[27] && !swatchSelects[65]) ||
                    (swatchSelects[12] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[19] && !swatchSelects[65]) ||
                    (swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[12] && !swatchSelects[65]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[65] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[46] && !swatchSelects[56]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[46] && swatchSelects[65] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[36] && !swatchSelects[56]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[36] && swatchSelects[46] && swatchSelects[65] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[27] && !swatchSelects[56]) ||
                    (swatchSelects[12] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[65] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[19] && !swatchSelects[56]) ||
                    (swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[65] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[12] && !swatchSelects[56]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[27] && swatchSelects[56] && swatchSelects[65] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[36] && !swatchSelects[46]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[36] && swatchSelects[56] && swatchSelects[65] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[27] && !swatchSelects[46]) ||
                    (swatchSelects[12] && swatchSelects[27] && swatchSelects[36] && swatchSelects[56] && swatchSelects[65] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[19] && !swatchSelects[46]) ||
                    (swatchSelects[19] && swatchSelects[27] && swatchSelects[36] && swatchSelects[56] && swatchSelects[65] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[12] && !swatchSelects[46]) ||
                    (swatchSelects[12] && swatchSelects[19] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[27] && !swatchSelects[36]) ||
                    (swatchSelects[12] && swatchSelects[27] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[19] && !swatchSelects[36]) ||
                    (swatchSelects[19] && swatchSelects[27] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[12] && !swatchSelects[36]) ||
                    (swatchSelects[12] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[19] && !swatchSelects[27]) ||
                    (swatchSelects[19] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[12] && !swatchSelects[27]) ||
                    (swatchSelects[27] && swatchSelects[36] && swatchSelects[46] && swatchSelects[56] && swatchSelects[65] && swatchSelects[73]
                            && swatchSelects[80] && swatchSelects[86] && !swatchSelects[12] && !swatchSelects[19])) {
                int a[] = {12, 19, 27, 36, 46, 56, 65, 73, 80, 86};
                i = pickUnSelected(a);
            } else if ((swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46]
                    && swatchSelects[57] && swatchSelects[67] && !swatchSelects[76] && !swatchSelects[84]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46]
                            && swatchSelects[57] && swatchSelects[76] && !swatchSelects[67] && !swatchSelects[84]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46]
                            && swatchSelects[67] && swatchSelects[76] && !swatchSelects[57] && !swatchSelects[84]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[57]
                            && swatchSelects[67] && swatchSelects[76] && !swatchSelects[46] && !swatchSelects[84]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[46] && swatchSelects[57]
                            && swatchSelects[67] && swatchSelects[76] && !swatchSelects[35] && !swatchSelects[84]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57]
                            && swatchSelects[67] && swatchSelects[76] && !swatchSelects[25] && !swatchSelects[84]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57]
                            && swatchSelects[67] && swatchSelects[76] && !swatchSelects[16] && !swatchSelects[84]) ||
                    (swatchSelects[1] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57]
                            && swatchSelects[67] && swatchSelects[76] && !swatchSelects[8] && !swatchSelects[84]) ||
                    (swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57]
                            && swatchSelects[67] && swatchSelects[76] && !swatchSelects[1] && !swatchSelects[84]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46]
                            && swatchSelects[57] && swatchSelects[84] && !swatchSelects[67] && !swatchSelects[76]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46]
                            && swatchSelects[67] && swatchSelects[84] && !swatchSelects[57] && !swatchSelects[76]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[57]
                            && swatchSelects[67] && swatchSelects[84] && !swatchSelects[46] && !swatchSelects[76]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[46] && swatchSelects[57]
                            && swatchSelects[67] && swatchSelects[84] && !swatchSelects[35] && !swatchSelects[76]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57]
                            && swatchSelects[67] && swatchSelects[84] && !swatchSelects[25] && !swatchSelects[76]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57]
                            && swatchSelects[67] && swatchSelects[84] && !swatchSelects[16] && !swatchSelects[76]) ||
                    (swatchSelects[1] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57]
                            && swatchSelects[67] && swatchSelects[84] && !swatchSelects[8] && !swatchSelects[76]) ||
                    (swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57]
                            && swatchSelects[67] && swatchSelects[84] && !swatchSelects[1] && !swatchSelects[76]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[57] && !swatchSelects[67]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[57]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[46] && !swatchSelects[67]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[46] && swatchSelects[57]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[35] && !swatchSelects[67]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[25] && !swatchSelects[67]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[16] && !swatchSelects[67]) ||
                    (swatchSelects[1] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[8] && !swatchSelects[67]) ||
                    (swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[1] && !swatchSelects[67]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[46] && !swatchSelects[57]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[46] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[35] && !swatchSelects[57]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[35] && swatchSelects[46] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[25] && !swatchSelects[57]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[16] && !swatchSelects[57]) ||
                    (swatchSelects[1] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[8] && !swatchSelects[57]) ||
                    (swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[1] && !swatchSelects[57]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[57] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[35] && !swatchSelects[46]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[35] && swatchSelects[57] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[25] && !swatchSelects[46]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[25] && swatchSelects[35] && swatchSelects[57] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[16] && !swatchSelects[46]) ||
                    (swatchSelects[1] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[57] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[8] && !swatchSelects[46]) ||
                    (swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[57] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[1] && !swatchSelects[46]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[16] && swatchSelects[46] && swatchSelects[57] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[25] && !swatchSelects[35]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[25] && swatchSelects[46] && swatchSelects[57] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[16] && !swatchSelects[35]) ||
                    (swatchSelects[1] && swatchSelects[16] && swatchSelects[25] && swatchSelects[46] && swatchSelects[57] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[8] && !swatchSelects[35]) ||
                    (swatchSelects[8] && swatchSelects[16] && swatchSelects[25] && swatchSelects[46] && swatchSelects[57] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[1] && !swatchSelects[35]) ||
                    (swatchSelects[1] && swatchSelects[8] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[16] && !swatchSelects[25]) ||
                    (swatchSelects[1] && swatchSelects[16] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[8] && !swatchSelects[25]) ||
                    (swatchSelects[8] && swatchSelects[16] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[1] && !swatchSelects[25]) ||
                    (swatchSelects[1] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[8] && !swatchSelects[16]) ||
                    (swatchSelects[8] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[1] && !swatchSelects[16]) ||
                    (swatchSelects[16] && swatchSelects[25] && swatchSelects[35] && swatchSelects[46] && swatchSelects[57] && swatchSelects[67]
                            && swatchSelects[76] && swatchSelects[84] && !swatchSelects[1] && !swatchSelects[8])) {
                int a[] = {1, 8, 16, 25, 35, 46, 57, 67, 76, 84};
                i = pickUnSelected(a);
            } else if ((swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55]
                    && swatchSelects[65] && swatchSelects[74] && !swatchSelects[82] && !swatchSelects[89]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55]
                            && swatchSelects[65] && swatchSelects[82] && !swatchSelects[74] && !swatchSelects[89]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55]
                            && swatchSelects[74] && swatchSelects[82] && !swatchSelects[65] && !swatchSelects[89]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[65]
                            && swatchSelects[74] && swatchSelects[82] && !swatchSelects[55] && !swatchSelects[89]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[55] && swatchSelects[65]
                            && swatchSelects[74] && swatchSelects[82] && !swatchSelects[44] && !swatchSelects[89]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65]
                            && swatchSelects[74] && swatchSelects[82] && !swatchSelects[33] && !swatchSelects[89]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65]
                            && swatchSelects[74] && swatchSelects[82] && !swatchSelects[23] && !swatchSelects[89]) ||
                    (swatchSelects[6] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65]
                            && swatchSelects[74] && swatchSelects[82] && !swatchSelects[14] && !swatchSelects[89]) ||
                    (swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65]
                            && swatchSelects[74] && swatchSelects[82] && !swatchSelects[6] && !swatchSelects[89]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55]
                            && swatchSelects[65] && swatchSelects[89] && !swatchSelects[74] && !swatchSelects[82]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55]
                            && swatchSelects[74] && swatchSelects[89] && !swatchSelects[65] && !swatchSelects[82]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[65]
                            && swatchSelects[74] && swatchSelects[89] && !swatchSelects[55] && !swatchSelects[82]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[55] && swatchSelects[65]
                            && swatchSelects[74] && swatchSelects[89] && !swatchSelects[44] && !swatchSelects[82]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65]
                            && swatchSelects[74] && swatchSelects[89] && !swatchSelects[33] && !swatchSelects[82]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65]
                            && swatchSelects[74] && swatchSelects[89] && !swatchSelects[23] && !swatchSelects[82]) ||
                    (swatchSelects[6] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65]
                            && swatchSelects[74] && swatchSelects[89] && !swatchSelects[14] && !swatchSelects[82]) ||
                    (swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65]
                            && swatchSelects[74] && swatchSelects[89] && !swatchSelects[6] && !swatchSelects[82]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[65] && !swatchSelects[74]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[65]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[55] && !swatchSelects[74]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[55] && swatchSelects[65]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[44] && !swatchSelects[74]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[33] && !swatchSelects[74]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[23] && !swatchSelects[74]) ||
                    (swatchSelects[6] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[14] && !swatchSelects[74]) ||
                    (swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[6] && !swatchSelects[74]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[55] && !swatchSelects[65]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[55] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[44] && !swatchSelects[65]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[44] && swatchSelects[55] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[33] && !swatchSelects[65]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[23] && !swatchSelects[65]) ||
                    (swatchSelects[6] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[14] && !swatchSelects[65]) ||
                    (swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[6] && !swatchSelects[65]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[65] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[44] && !swatchSelects[55]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[44] && swatchSelects[65] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[33] && !swatchSelects[55]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[33] && swatchSelects[44] && swatchSelects[65] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[23] && !swatchSelects[55]) ||
                    (swatchSelects[6] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[65] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[14] && !swatchSelects[55]) ||
                    (swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[65] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[6] && !swatchSelects[55]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[23] && swatchSelects[55] && swatchSelects[65] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[33] && !swatchSelects[44]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[33] && swatchSelects[55] && swatchSelects[65] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[23] && !swatchSelects[44]) ||
                    (swatchSelects[6] && swatchSelects[23] && swatchSelects[33] && swatchSelects[55] && swatchSelects[65] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[14] && !swatchSelects[44]) ||
                    (swatchSelects[14] && swatchSelects[23] && swatchSelects[33] && swatchSelects[55] && swatchSelects[65] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[6] && !swatchSelects[44]) ||
                    (swatchSelects[6] && swatchSelects[14] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[23] && !swatchSelects[33]) ||
                    (swatchSelects[6] && swatchSelects[23] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[14] && !swatchSelects[33]) ||
                    (swatchSelects[14] && swatchSelects[23] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[6] && !swatchSelects[33]) ||
                    (swatchSelects[6] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[14] && !swatchSelects[23]) ||
                    (swatchSelects[14] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[6] && !swatchSelects[23]) ||
                    (swatchSelects[23] && swatchSelects[33] && swatchSelects[44] && swatchSelects[55] && swatchSelects[65] && swatchSelects[74]
                            && swatchSelects[82] && swatchSelects[89] && !swatchSelects[6] && !swatchSelects[14])) {
                int a[] = {6, 14, 23, 33, 44, 55, 65, 74, 82, 89};
                i = pickUnSelected(a);
            } else if ((swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                    && swatchSelects[46] && swatchSelects[47] && swatchSelects[48] && !swatchSelects[49] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                            && swatchSelects[46] && swatchSelects[47] && swatchSelects[49] && !swatchSelects[48] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                            && swatchSelects[46] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[47] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[46] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[45] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[44] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[43] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[42] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[41] && !swatchSelects[50]) ||
                    (swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[40] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                            && swatchSelects[46] && swatchSelects[47] && swatchSelects[50] && !swatchSelects[48] && !swatchSelects[49]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                            && swatchSelects[46] && swatchSelects[48] && swatchSelects[50] && !swatchSelects[47] && !swatchSelects[49]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[50] && !swatchSelects[46] && !swatchSelects[49]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[50] && !swatchSelects[45] && !swatchSelects[49]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[50] && !swatchSelects[44] && !swatchSelects[49]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[50] && !swatchSelects[43] && !swatchSelects[49]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[50] && !swatchSelects[42] && !swatchSelects[49]) ||
                    (swatchSelects[40] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[50] && !swatchSelects[41] && !swatchSelects[49]) ||
                    (swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[50] && !swatchSelects[40] && !swatchSelects[49]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                            && swatchSelects[46] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[47] && !swatchSelects[48]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                            && swatchSelects[47] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[46] && !swatchSelects[48]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[45] && !swatchSelects[48]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[44] && !swatchSelects[48]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[43] && !swatchSelects[48]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[42] && !swatchSelects[48]) ||
                    (swatchSelects[40] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[41] && !swatchSelects[48]) ||
                    (swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[40] && !swatchSelects[48]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[46] && !swatchSelects[47]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[46]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[45] && !swatchSelects[47]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[44] && !swatchSelects[47]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[43] && !swatchSelects[47]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[42] && !swatchSelects[47]) ||
                    (swatchSelects[40] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[41] && !swatchSelects[47]) ||
                    (swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[40] && !swatchSelects[47]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[45] && !swatchSelects[46]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[45] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[44] && !swatchSelects[46]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[44] && swatchSelects[45] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[43] && !swatchSelects[46]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[42] && !swatchSelects[46]) ||
                    (swatchSelects[40] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[41] && !swatchSelects[46]) ||
                    (swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[40] && !swatchSelects[46]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[44] && !swatchSelects[45]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[44] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[43] && !swatchSelects[45]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[43] && swatchSelects[44] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[42] && !swatchSelects[45]) ||
                    (swatchSelects[40] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[41] && !swatchSelects[45]) ||
                    (swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[40] && !swatchSelects[45]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[43] && !swatchSelects[44]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[43] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[42] && !swatchSelects[44]) ||
                    (swatchSelects[40] && swatchSelects[42] && swatchSelects[43] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[41] && !swatchSelects[44]) ||
                    (swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[40] && !swatchSelects[44]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[42] && !swatchSelects[43]) ||
                    (swatchSelects[40] && swatchSelects[42] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[41] && !swatchSelects[43]) ||
                    (swatchSelects[41] && swatchSelects[42] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[40] && !swatchSelects[43]) ||
                    (swatchSelects[40] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[41] && !swatchSelects[42]) ||
                    (swatchSelects[41] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[40] && !swatchSelects[42]) ||
                    (swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[40] && !swatchSelects[41])) {
                int a[] = {40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50};
                i = pickUnSelected(a);
            } else if ((swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                    && swatchSelects[55] && swatchSelects[64] && swatchSelects[72] && !swatchSelects[79] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                            && swatchSelects[55] && swatchSelects[64] && swatchSelects[79] && !swatchSelects[72] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                            && swatchSelects[55] && swatchSelects[72] && swatchSelects[79] && !swatchSelects[64] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && !swatchSelects[55] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && !swatchSelects[45] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && !swatchSelects[35] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && !swatchSelects[26] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && !swatchSelects[18] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && !swatchSelects[11] && !swatchSelects[85]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && !swatchSelects[5] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                            && swatchSelects[55] && swatchSelects[64] && swatchSelects[85] && !swatchSelects[72] && !swatchSelects[79]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                            && swatchSelects[55] && swatchSelects[72] && swatchSelects[85] && !swatchSelects[64] && !swatchSelects[79]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[85] && !swatchSelects[55] && !swatchSelects[79]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[85] && !swatchSelects[45] && !swatchSelects[79]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[85] && !swatchSelects[35] && !swatchSelects[79]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[85] && !swatchSelects[26] && !swatchSelects[79]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[85] && !swatchSelects[18] && !swatchSelects[79]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[85] && !swatchSelects[11] && !swatchSelects[79]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[85] && !swatchSelects[5] && !swatchSelects[79]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                            && swatchSelects[55] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[64] && !swatchSelects[72]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                            && swatchSelects[64] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[55] && !swatchSelects[72]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[45] && !swatchSelects[72]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[35] && !swatchSelects[72]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[26] && !swatchSelects[72]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[18] && !swatchSelects[72]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[11] && !swatchSelects[72]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[5] && !swatchSelects[72]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[55] && !swatchSelects[64]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[55]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[45] && !swatchSelects[64]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[35] && !swatchSelects[64]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[26] && !swatchSelects[64]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[18] && !swatchSelects[64]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[11] && !swatchSelects[64]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[5] && !swatchSelects[64]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[45] && !swatchSelects[55]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[45] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[35] && !swatchSelects[55]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[35] && swatchSelects[45] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[26] && !swatchSelects[55]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[18] && !swatchSelects[55]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[11] && !swatchSelects[55]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[5] && !swatchSelects[55]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[35] && !swatchSelects[45]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[35] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[26] && !swatchSelects[45]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[26] && swatchSelects[35] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[18] && !swatchSelects[45]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[11] && !swatchSelects[45]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[5] && !swatchSelects[45]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[26] && !swatchSelects[35]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[26] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[18] && !swatchSelects[35]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[26] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[11] && !swatchSelects[35]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[5] && !swatchSelects[35]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[18] && !swatchSelects[26]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[11] && !swatchSelects[26]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[5] && !swatchSelects[26]) ||
                    (swatchSelects[5] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[11] && !swatchSelects[18]) ||
                    (swatchSelects[11] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[5] && !swatchSelects[18]) ||
                    (swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[5] && !swatchSelects[11])) {
                int a[] = {5, 11, 18, 26, 35, 45, 55, 64, 72, 79, 85};
                i = pickUnSelected(a);
            } else if ((swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                    && swatchSelects[56] && swatchSelects[66] && swatchSelects[75] && !swatchSelects[83] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                            && swatchSelects[56] && swatchSelects[66] && swatchSelects[83] && !swatchSelects[75] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                            && swatchSelects[56] && swatchSelects[75] && swatchSelects[83] && !swatchSelects[66] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && !swatchSelects[56] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && !swatchSelects[45] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && !swatchSelects[34] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && !swatchSelects[24] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && !swatchSelects[15] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && !swatchSelects[7] && !swatchSelects[90]) ||
                    (swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && !swatchSelects[0] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                            && swatchSelects[56] && swatchSelects[66] && swatchSelects[90] && !swatchSelects[75] && !swatchSelects[83]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                            && swatchSelects[56] && swatchSelects[75] && swatchSelects[90] && !swatchSelects[66] && !swatchSelects[83]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[90] && !swatchSelects[56] && !swatchSelects[83]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[90] && !swatchSelects[45] && !swatchSelects[83]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[90] && !swatchSelects[34] && !swatchSelects[83]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[90] && !swatchSelects[24] && !swatchSelects[83]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[90] && !swatchSelects[15] && !swatchSelects[83]) ||
                    (swatchSelects[0] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[90] && !swatchSelects[7] && !swatchSelects[83]) ||
                    (swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[90] && !swatchSelects[0] && !swatchSelects[83]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                            && swatchSelects[56] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[66] && !swatchSelects[75]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                            && swatchSelects[66] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[56] && !swatchSelects[75]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[45] && !swatchSelects[75]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[34] && !swatchSelects[75]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[24] && !swatchSelects[75]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[15] && !swatchSelects[75]) ||
                    (swatchSelects[0] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[7] && !swatchSelects[75]) ||
                    (swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[0] && !swatchSelects[75]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[56] && !swatchSelects[66]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[56]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[45] && !swatchSelects[66]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[34] && !swatchSelects[66]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[24] && !swatchSelects[66]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[15] && !swatchSelects[66]) ||
                    (swatchSelects[0] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[7] && !swatchSelects[66]) ||
                    (swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[0] && !swatchSelects[66]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[45] && !swatchSelects[56]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[45] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[34] && !swatchSelects[56]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[34] && swatchSelects[45] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[24] && !swatchSelects[56]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[15] && !swatchSelects[56]) ||
                    (swatchSelects[0] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[7] && !swatchSelects[56]) ||
                    (swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[0] && !swatchSelects[56]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[34] && !swatchSelects[45]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[34] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[24] && !swatchSelects[45]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[24] && swatchSelects[34] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[15] && !swatchSelects[45]) ||
                    (swatchSelects[0] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[7] && !swatchSelects[45]) ||
                    (swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[0] && !swatchSelects[45]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[24] && !swatchSelects[34]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[24] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[15] && !swatchSelects[34]) ||
                    (swatchSelects[0] && swatchSelects[15] && swatchSelects[24] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[7] && !swatchSelects[34]) ||
                    (swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[0] && !swatchSelects[34]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[15] && !swatchSelects[24]) ||
                    (swatchSelects[0] && swatchSelects[15] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[7] && !swatchSelects[24]) ||
                    (swatchSelects[7] && swatchSelects[15] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[0] && !swatchSelects[24]) ||
                    (swatchSelects[0] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[7] && !swatchSelects[15]) ||
                    (swatchSelects[7] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[0] && !swatchSelects[15]) ||
                    (swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[0] && !swatchSelects[7])) {
                int a[] = {0, 7, 15, 24, 34, 45, 56, 66, 75, 83, 90};
                i = pickUnSelected(a);
            }
        }

        if (i >= 0) {
            return i;
        } else {
            return -1;
        }
    }

    private int preventStep2_6() {
        int i = -1;
        if (boardRadius == 5) {
            if ((swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                    && swatchSelects[46] && swatchSelects[47] && swatchSelects[48] && !swatchSelects[49] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                            && swatchSelects[46] && swatchSelects[47] && swatchSelects[49] && !swatchSelects[48] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                            && swatchSelects[46] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[47] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[46] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[45] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[44] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[43] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[42] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[41] && !swatchSelects[50]) ||
                    (swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[49] && !swatchSelects[40] && !swatchSelects[50]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                            && swatchSelects[46] && swatchSelects[47] && swatchSelects[50] && !swatchSelects[48] && !swatchSelects[49]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                            && swatchSelects[46] && swatchSelects[48] && swatchSelects[50] && !swatchSelects[47] && !swatchSelects[49]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[50] && !swatchSelects[46] && !swatchSelects[49]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[50] && !swatchSelects[45] && !swatchSelects[49]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[50] && !swatchSelects[44] && !swatchSelects[49]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[50] && !swatchSelects[43] && !swatchSelects[49]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[50] && !swatchSelects[42] && !swatchSelects[49]) ||
                    (swatchSelects[40] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[50] && !swatchSelects[41] && !swatchSelects[49]) ||
                    (swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[48] && swatchSelects[50] && !swatchSelects[40] && !swatchSelects[49]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                            && swatchSelects[46] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[47] && !swatchSelects[48]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                            && swatchSelects[47] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[46] && !swatchSelects[48]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[45] && !swatchSelects[48]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[44] && !swatchSelects[48]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[43] && !swatchSelects[48]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[42] && !swatchSelects[48]) ||
                    (swatchSelects[40] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[41] && !swatchSelects[48]) ||
                    (swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[47] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[40] && !swatchSelects[48]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[46] && !swatchSelects[47]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[46]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[45] && !swatchSelects[47]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[44] && !swatchSelects[47]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[43] && !swatchSelects[47]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[42] && !swatchSelects[47]) ||
                    (swatchSelects[40] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[41] && !swatchSelects[47]) ||
                    (swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[40] && !swatchSelects[47]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[45] && !swatchSelects[46]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[45] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[44] && !swatchSelects[46]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[44] && swatchSelects[45] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[43] && !swatchSelects[46]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[42] && !swatchSelects[46]) ||
                    (swatchSelects[40] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[41] && !swatchSelects[46]) ||
                    (swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[40] && !swatchSelects[46]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[44] && !swatchSelects[45]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[44] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[43] && !swatchSelects[45]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[43] && swatchSelects[44] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[42] && !swatchSelects[45]) ||
                    (swatchSelects[40] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[41] && !swatchSelects[45]) ||
                    (swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[40] && !swatchSelects[45]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[42] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[43] && !swatchSelects[44]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[43] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[42] && !swatchSelects[44]) ||
                    (swatchSelects[40] && swatchSelects[42] && swatchSelects[43] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[41] && !swatchSelects[44]) ||
                    (swatchSelects[41] && swatchSelects[42] && swatchSelects[43] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[40] && !swatchSelects[44]) ||
                    (swatchSelects[40] && swatchSelects[41] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[42] && !swatchSelects[43]) ||
                    (swatchSelects[40] && swatchSelects[42] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[41] && !swatchSelects[43]) ||
                    (swatchSelects[41] && swatchSelects[42] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[40] && !swatchSelects[43]) ||
                    (swatchSelects[40] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[41] && !swatchSelects[42]) ||
                    (swatchSelects[41] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[40] && !swatchSelects[42]) ||
                    (swatchSelects[42] && swatchSelects[43] && swatchSelects[44] && swatchSelects[45] && swatchSelects[46] && swatchSelects[47]
                            && swatchSelects[48] && swatchSelects[49] && swatchSelects[50] && !swatchSelects[40] && !swatchSelects[41])) {
                int a[] = {40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50};
                i = pickUnSelected(a);
            } else if ((swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                    && swatchSelects[55] && swatchSelects[64] && swatchSelects[72] && !swatchSelects[79] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                            && swatchSelects[55] && swatchSelects[64] && swatchSelects[79] && !swatchSelects[72] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                            && swatchSelects[55] && swatchSelects[72] && swatchSelects[79] && !swatchSelects[64] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && !swatchSelects[55] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && !swatchSelects[45] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && !swatchSelects[35] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && !swatchSelects[26] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && !swatchSelects[18] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && !swatchSelects[11] && !swatchSelects[85]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[79] && !swatchSelects[5] && !swatchSelects[85]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                            && swatchSelects[55] && swatchSelects[64] && swatchSelects[85] && !swatchSelects[72] && !swatchSelects[79]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                            && swatchSelects[55] && swatchSelects[72] && swatchSelects[85] && !swatchSelects[64] && !swatchSelects[79]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[85] && !swatchSelects[55] && !swatchSelects[79]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[85] && !swatchSelects[45] && !swatchSelects[79]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[85] && !swatchSelects[35] && !swatchSelects[79]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[85] && !swatchSelects[26] && !swatchSelects[79]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[85] && !swatchSelects[18] && !swatchSelects[79]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[85] && !swatchSelects[11] && !swatchSelects[79]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[72] && swatchSelects[85] && !swatchSelects[5] && !swatchSelects[79]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                            && swatchSelects[55] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[64] && !swatchSelects[72]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                            && swatchSelects[64] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[55] && !swatchSelects[72]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[45] && !swatchSelects[72]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[35] && !swatchSelects[72]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[26] && !swatchSelects[72]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[18] && !swatchSelects[72]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[11] && !swatchSelects[72]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[64] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[5] && !swatchSelects[72]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[55] && !swatchSelects[64]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[55]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[45] && !swatchSelects[64]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[35] && !swatchSelects[64]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[26] && !swatchSelects[64]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[18] && !swatchSelects[64]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[11] && !swatchSelects[64]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[5] && !swatchSelects[64]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[45] && !swatchSelects[55]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[45] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[35] && !swatchSelects[55]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[35] && swatchSelects[45] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[26] && !swatchSelects[55]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[18] && !swatchSelects[55]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[11] && !swatchSelects[55]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[5] && !swatchSelects[55]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[35] && !swatchSelects[45]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[35] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[26] && !swatchSelects[45]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[26] && swatchSelects[35] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[18] && !swatchSelects[45]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[11] && !swatchSelects[45]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[5] && !swatchSelects[45]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[18] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[26] && !swatchSelects[35]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[26] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[18] && !swatchSelects[35]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[26] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[11] && !swatchSelects[35]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[26] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[5] && !swatchSelects[35]) ||
                    (swatchSelects[5] && swatchSelects[11] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[18] && !swatchSelects[26]) ||
                    (swatchSelects[5] && swatchSelects[18] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[11] && !swatchSelects[26]) ||
                    (swatchSelects[11] && swatchSelects[18] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[5] && !swatchSelects[26]) ||
                    (swatchSelects[5] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[11] && !swatchSelects[18]) ||
                    (swatchSelects[11] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[5] && !swatchSelects[18]) ||
                    (swatchSelects[18] && swatchSelects[26] && swatchSelects[35] && swatchSelects[45] && swatchSelects[55] && swatchSelects[64]
                            && swatchSelects[72] && swatchSelects[79] && swatchSelects[85] && !swatchSelects[5] && !swatchSelects[11])) {
                int a[] = {5, 11, 18, 26, 35, 45, 55, 64, 72, 79, 85};
                i = pickUnSelected(a);
            } else if ((swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                    && swatchSelects[56] && swatchSelects[66] && swatchSelects[75] && !swatchSelects[83] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                            && swatchSelects[56] && swatchSelects[66] && swatchSelects[83] && !swatchSelects[75] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                            && swatchSelects[56] && swatchSelects[75] && swatchSelects[83] && !swatchSelects[66] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && !swatchSelects[56] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && !swatchSelects[45] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && !swatchSelects[34] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && !swatchSelects[24] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && !swatchSelects[15] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && !swatchSelects[7] && !swatchSelects[90]) ||
                    (swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[83] && !swatchSelects[0] && !swatchSelects[90]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                            && swatchSelects[56] && swatchSelects[66] && swatchSelects[90] && !swatchSelects[75] && !swatchSelects[83]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                            && swatchSelects[56] && swatchSelects[75] && swatchSelects[90] && !swatchSelects[66] && !swatchSelects[83]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[90] && !swatchSelects[56] && !swatchSelects[83]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[90] && !swatchSelects[45] && !swatchSelects[83]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[90] && !swatchSelects[34] && !swatchSelects[83]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[90] && !swatchSelects[24] && !swatchSelects[83]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[90] && !swatchSelects[15] && !swatchSelects[83]) ||
                    (swatchSelects[0] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[90] && !swatchSelects[7] && !swatchSelects[83]) ||
                    (swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[75] && swatchSelects[90] && !swatchSelects[0] && !swatchSelects[83]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                            && swatchSelects[56] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[66] && !swatchSelects[75]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                            && swatchSelects[66] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[56] && !swatchSelects[75]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[45] && !swatchSelects[75]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[34] && !swatchSelects[75]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[24] && !swatchSelects[75]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[15] && !swatchSelects[75]) ||
                    (swatchSelects[0] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[7] && !swatchSelects[75]) ||
                    (swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[66] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[0] && !swatchSelects[75]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[56] && !swatchSelects[66]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[56]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[45] && !swatchSelects[66]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[34] && !swatchSelects[66]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[24] && !swatchSelects[66]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[15] && !swatchSelects[66]) ||
                    (swatchSelects[0] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[7] && !swatchSelects[66]) ||
                    (swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[0] && !swatchSelects[66]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[45] && !swatchSelects[56]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[45] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[34] && !swatchSelects[56]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[34] && swatchSelects[45] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[24] && !swatchSelects[56]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[15] && !swatchSelects[56]) ||
                    (swatchSelects[0] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[7] && !swatchSelects[56]) ||
                    (swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[0] && !swatchSelects[56]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[34] && !swatchSelects[45]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[34] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[24] && !swatchSelects[45]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[24] && swatchSelects[34] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[15] && !swatchSelects[45]) ||
                    (swatchSelects[0] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[7] && !swatchSelects[45]) ||
                    (swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[0] && !swatchSelects[45]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[15] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[24] && !swatchSelects[34]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[24] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[15] && !swatchSelects[34]) ||
                    (swatchSelects[0] && swatchSelects[15] && swatchSelects[24] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[7] && !swatchSelects[34]) ||
                    (swatchSelects[7] && swatchSelects[15] && swatchSelects[24] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[0] && !swatchSelects[34]) ||
                    (swatchSelects[0] && swatchSelects[7] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[15] && !swatchSelects[24]) ||
                    (swatchSelects[0] && swatchSelects[15] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[7] && !swatchSelects[24]) ||
                    (swatchSelects[7] && swatchSelects[15] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[0] && !swatchSelects[24]) ||
                    (swatchSelects[0] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[7] && !swatchSelects[15]) ||
                    (swatchSelects[7] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[0] && !swatchSelects[15]) ||
                    (swatchSelects[15] && swatchSelects[24] && swatchSelects[34] && swatchSelects[45] && swatchSelects[56] && swatchSelects[66]
                            && swatchSelects[75] && swatchSelects[83] && swatchSelects[90] && !swatchSelects[0] && !swatchSelects[7])) {
                int a[] = {0, 7, 15, 24, 34, 45, 56, 66, 75, 83, 90};
                i = pickUnSelected(a);
            }
        }

        if (i >= 0) {
            return i;
        } else {
            return -1;
        }
    }

    private int[] convertListToArray(ArrayList<Integer> arrayList) {
        int[] arr = new int[arrayList.size()];
        for (int i = 0; i < arrayList.size(); i++) {
            arr[i] = arrayList.get(i);
        }
        return arr;
    }

    private static int pickUnSelected(int[] array) {
        int j = 0;
        for (int i : array) {
            if (!swatchSelects[i])
                j = i;
        }
        return j;
    }

    private static ArrayList<Integer> pickUnSelectedArray(ArrayList<Integer> array) {
        ArrayList<Integer> integers = new ArrayList<>();
        for (int i : array) {
            if (!swatchSelects[i])
                integers.add(i);
        }
        return integers;
    }

    private static ArrayList<Integer> pickUnSelectedArray1(int[] array) {
        ArrayList<Integer> integers = new ArrayList<>();
        for (int i : array) {
            if (!swatchSelects[i])
                integers.add(i);
        }
        return integers;
    }

    private static int pickUnSelectedRandom(int[] array) {
        ArrayList<Integer> integers = new ArrayList<>();
        int j = 0;
        for (int i : array) {
            if (!swatchSelects[i])
                integers.add(i);
        }
        Collections.shuffle(integers);
        if (integers.size() > 0)
            return integers.get(0);
        else return 0;
    }

    private static int pickUnSelectedRandom1(int[] array, Integer[] exception) {
        ArrayList<Integer> integers = new ArrayList<>();
        int j = 0;
        for (int i : array) {
            if (!swatchSelects[i] && !Arrays.asList(exception).contains(i))
                integers.add(i);
        }
        Collections.shuffle(integers);
        if (integers.size() > 0)
            return integers.get(0);
        else return 0;
    }

    private static int pickUnSelectedRandom1(ArrayList<Integer> array) {
        ArrayList<Integer> integers = new ArrayList<>();
        Integer i;
        for (i = 0; i < array.size(); i++) {
            if (!swatchSelects[i])
                integers.add(array.indexOf(i));

        }
        Collections.shuffle(integers);
        if (integers.size() > 0)
            return integers.get(0);
        else return 0;
    }

    public class ConnectedThread extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.i(TAG, "create ConnectedThread");
            bluetoothSocket = socket;
            InputStream inputStreamTemp = null;
            OutputStream outputStreamTemp = null;
            // Get the BluetoothSocket input and output streams
            try {
                inputStreamTemp = socket.getInputStream();
                outputStreamTemp = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }
            inputStream = inputStreamTemp;
            outputStream = outputStreamTemp;
            Log.e(TAG, "ConnectedThread");
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    String readMessage = "";
                    bytes = inputStream.read(buffer);
                    readMessage = new String(buffer, 0, bytes);
                    if (readMessage.contains(";")) {
                        String[] parts = readMessage.split(";");
                        selectedSwatch = (int) (Byte.parseByte(parts[0]));
                        swatches[selectedSwatch] = (Swatch) getChildAt(selectedSwatch);
                        player1Score = (int) (Byte.parseByte(parts[1]));
                        player2Score = (int) (Byte.parseByte(parts[2]));
                        turn = (int) (Byte.parseByte(parts[3]));
                        swatches[selectedSwatch].id = (int) (Byte.parseByte(parts[4]));
                        gameActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                swatches[selectedSwatch].setSelected(true);
                                swatchSelects[selectedSwatch] = swatches[selectedSwatch].isSelected();
                                swatches[selectedSwatch].drawable.setColor(BaseActivity.COLOR3);
                                onPlayerChangedListener.onPlayerChanged();
                                check(selectedSwatch, false);
                                onResultChangedListener.onResultChanged(player1Score, player2Score, turn);
                                checkerImageView.setVisibility(VISIBLE);
                                updateCheckerPosition(swatches[selectedSwatch]);
                                if (soundEnabled) mediaPlayer2.start();
                                touchEnabled = true;
                                int count = 0;
                                for (int i = 0; i < swatchCount; i++) {
                                    if (swatchSelects[i]) count++;
                                }
                                if (count == swatchCount) {
                                    gameFinished();
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "disconnected", e);
                    Log.i(TAG, e.getMessage());
                    break;
                }
            }
        }

        public void write(byte[] buffer) {
            if (!isWin && !isDraw) {
                try {
                    outputStream.write(buffer);
                } catch (IOException e) {
                    Log.e(TAG, "Exception during write", e);
                }
            }
        }

        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }

    }

    private void updateCheckerPosition(final Swatch swatch) {
        checkerImageView.setLayoutParams(swatch.getLayoutParams());
        checkerImageView.setPadding(swatch.getPaddingLeft(), swatch.getPaddingTop(),
                swatch.getPaddingRight(), swatch.getPaddingBottom());
    }

    private void updateSwatchesPosition() {
        if (swatchScale == null || swatchPivot == null) {
            return;
        }

        final float swatchRadius = getSwatchRadius();
        final int padding = (int) (0.075f * swatchRadius);
        final int strokeWidth = (int) (0.05f * swatchRadius);

        for (int i = 0; i < getChildCount(); i++) {
            final View childView = getChildAt(i);
            if (childView instanceof Swatch) {
                final Swatch swatch = (Swatch) childView;
                final int swatchSize = (int) (swatchRadius - strokeWidth) * 2;
                LayoutParams params = new LayoutParams(swatchSize, swatchSize);
                params.leftMargin = (int) (getItemPositionX(swatch) - swatchRadius);
                params.topMargin = (int) (getItemPositionY(swatch) - swatchRadius);
                params.gravity = Gravity.TOP | Gravity.START;
                swatch.setLayoutParams(params);
                swatch.setPadding(0, 0, padding, padding);
                swatch.updateStrokeWidth(strokeWidth);

                final Animation itemAnim = createSwatchAnimation(swatchRadius, swatch.animDelay);
                if (itemAnim != null) {
                    swatch.startAnimation(itemAnim);
                }

                if (swatch.id == selectedSwatch) {
                    updateCheckerPosition(swatch);
                    if (itemAnim != null) {
                        checkerImageView.startAnimation(itemAnim);
                    }
                }
            }
        }
    }

    private Animation createSwatchAnimation(final float swatchRadius, final int delay) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return null;
        }
        ScaleAnimation scaleAnim = new ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f, swatchRadius, swatchRadius);
        scaleAnim.setDuration(ANIM_TIME_SWATCH);
        scaleAnim.setStartOffset(delay);
        scaleAnim.setInterpolator(interpolator);
        return scaleAnim;
    }

    private static int getSwatchCount(final int radius) {
        return 3 * radius * (radius + 1) + 1;
    }

    private float getSwatchRadius() {
        if (swatchScale == null) {
            return 0.0f;
        }
        return 0.5f * swatchScale.x / (boardRadius * 2 + 1);
    }

    private float getItemPositionX(final Swatch item) {
        return swatchPivot.x + (item.position.x * 0.5f * swatchScale.x);
    }

    private float getItemPositionY(final Swatch item) {
        return swatchPivot.y + (item.position.y * 0.5f * swatchScale.y);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        final float width = w - getPaddingLeft() - getPaddingRight();
        final float height = h - getPaddingTop() - getPaddingBottom();
        swatchPivot = new PointF(width / 2.0f, height / 2.0f);
        // additional padding for swatch stroke and overshoot animation
        final float strokePadding = Math.min(w, h) * 0.05f;
        swatchScale = new PointF(width - strokePadding, height - strokePadding);

        if (width > height * VIEW_ASPECT_RATIO) {
            final float diff = width - height * VIEW_ASPECT_RATIO;
            swatchScale.x -= diff;
        } else if (height > width / VIEW_ASPECT_RATIO) {
            final float diff = height - width / VIEW_ASPECT_RATIO;
            swatchScale.y -= diff;
        }

        updateSwatchesPosition();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.UNSPECIFIED) {
            heightSize = (int) (widthSize / VIEW_ASPECT_RATIO);
        }
        if (widthMode == MeasureSpec.UNSPECIFIED && heightMode == MeasureSpec.EXACTLY) {
            widthSize = (int) (heightSize * VIEW_ASPECT_RATIO);
        }

        setMeasuredDimension(widthSize, heightSize);
    }


}
