package com.asb.otic.ui.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.asb.otic.R;
import com.asb.otic.data.DatabaseHandler;
import com.asb.otic.ui.custom.BoardView;
import com.asb.otic.ui.custom.BoardView.OnPlayerChangedListener;
import com.asb.otic.ui.custom.BoardView.OnResultChangedListener;
import com.asb.otic.ui.custom.BoardView.OnSwatchSelectedListener;
import com.asb.otic.ui.custom.CDialog;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by Amr Alsaidy on 2017/11/1.
 */

public class GameActivity extends BaseActivity implements OnSwatchSelectedListener,
        OnPlayerChangedListener, OnResultChangedListener {
    private String TAG = "TEST_TAG";
    @BindView(R.id.hexagonal_bv_id)
    BoardView boardView;
    @BindView(R.id.player_one_name_tv_id)
    public TextView playerOneNameTextView;
    @BindView(R.id.player_two_name_tv_id)
    public TextView playerTwoNameTextView;
    @BindView(R.id.player_result_ll_id)
    LinearLayout playerResultLinearLayout;
    @BindView(R.id.player_one_ll_id)
    public LinearLayout playerOneLinearLayout;
    @BindView(R.id.player_two_ll_id)
    public LinearLayout playerTwoLinearLayout;
    @BindView(R.id.player_one_score_ts_id)
    TextSwitcher playerOneScoreTextSwitcher;
    @BindView(R.id.player_two_score_ts_id)
    TextSwitcher playerTwoScoreTextSwitcher;
    @BindView(R.id.sound_on_iv_id)
    ImageView soundOnImageView;
    @BindView(R.id.settings_iv_id)
    ImageView settingsImageView;
    @BindView(R.id.cancel_game_iv_id)
    ImageView cancelGameImageView;
    @BindView(R.id.new_game_iv_id)
    public ImageView newGameImageView;

    public int difficulty;
    public String playerOneName;
    public String playerTwoName;
    public int gameType;
    public String type;
    public String touch = "no";
    public TextView myText1;
    public TextView myText2;

    GradientDrawable gradientDrawable;

    DatabaseHandler databaseHandler;
    Bundle bundle;
    private AdView mAdView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        databaseHandler = new DatabaseHandler(this);
        ButterKnife.bind(this);

        bundle = getIntent().getExtras();
        boardSize = bundle.getInt("BOARD_RADIUS");
        playerOneName = bundle.getString("PLAYER_ONE_NAME");
        playerTwoName = bundle.getString("PLAYER_TWO_NAME");
        gameType = bundle.getInt("GAME_TYPE");
        difficulty = bundle.getInt("GAME_DIFFICULTY");
        type = bundle.getString("TYPE");

        if (type.equals("receiver")) {
            playerOneLinearLayout.removeViewAt(1);
            playerTwoLinearLayout.removeViewAt(1);
            playerOneLinearLayout.addView(playerTwoScoreTextSwitcher, 1);
            playerTwoLinearLayout.addView(playerOneScoreTextSwitcher, 1);
        }
        if (type.equals("receiver") && touch.equals("no")) {
            boardView.touchEnabled = false;
        } else if (touch.equals("yes")) {
            boardView.touchEnabled = true;
        }
        playerOneNameTextView.setText(playerOneName);
        playerTwoNameTextView.setText(playerTwoName);

        View layout = findViewById(R.id.activity_game_ll_id);
        gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BaseActivity.COLOR1, BaseActivity.COLOR2});
        layout.setBackground(gradientDrawable);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Window w = getWindow(); // in Activity's onCreate() for instance
            w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }

        boardView.setAttrs(gameType, difficulty, boardSize,
                this, this, this);

        // Set the ViewFactory of the TextSwitcher that will create TextView object when asked
        playerOneScoreTextSwitcher.setFactory(new ViewSwitcher.ViewFactory() {
            @Override
            public View makeView() {
                // TODO Auto-generated method stub
                // create new textView and set the properties like clolr, size etc
                myText1 = new TextView(GameActivity.this);
                myText1.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                myText1.setText("0");
                myText1.setTextSize(36);
                myText1.setTextColor(Color.WHITE);
                myText1.setTypeface(font);
                return myText1;
            }
        });

        // Set the ViewFactory of the TextSwitcher that will create TextView object when asked
        playerTwoScoreTextSwitcher.setFactory(new ViewSwitcher.ViewFactory() {
            @Override
            public View makeView() {
                // TODO Auto-generated method stub
                // create new textView and set the properties like clolr, size etc
                myText2 = new TextView(GameActivity.this);
                myText2.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                myText2.setText("0");
                myText2.setTextSize(36);
                myText2.setTextColor(Color.WHITE);
                myText2.setTypeface(font);
                return myText2;

            }
        });

        // Declare the in and out animations and initialize them
        Animation in = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        Animation out = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);


        playerOneScoreTextSwitcher.setInAnimation(in);
        playerOneScoreTextSwitcher.setOutAnimation(out);
        playerTwoScoreTextSwitcher.setInAnimation(in);
        playerTwoScoreTextSwitcher.setOutAnimation(out);

        if (soundEnabled) {
            soundOnImageView.setImageDrawable(getResources().getDrawable(R.drawable.ic_sound_on));
            boardView.soundEnabled = true;
        } else if (!soundEnabled) {
            soundOnImageView.setImageDrawable(getResources().getDrawable(R.drawable.ic_sound_off));
            boardView.soundEnabled = false;
        }

        soundOnImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (boardView.soundEnabled) {
                    soundOnImageView.setImageDrawable(getResources().getDrawable(R.drawable.ic_sound_off));
                    boardView.soundEnabled = false;
                } else if (!boardView.soundEnabled) {
                    soundOnImageView.setImageDrawable(getResources().getDrawable(R.drawable.ic_sound_on));
                    boardView.soundEnabled = true;
                }
            }
        });

        settingsImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = SettingsActivity.Factory.getIntent(GameActivity.this);
                intent.putExtra("CALLER", "GameActivity");
                startActivity(intent);
            }
        });

        if (gameType == 3) {
            newGameImageView.setImageDrawable(getResources().getDrawable(R.drawable.ic_new_color));
            newGameImageView.setEnabled(false);
        }

        newGameImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final CDialog cDialog = new CDialog.Builder(GameActivity.this)
                        .setTitle(getString(R.string.new_game))
                        .setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                recreate();
                            }
                        })
                        .setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        })
                        .setCanceledOnTouchOutside(false)
                        .show();
                cDialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            }
        });


        cancelGameImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final CDialog cDialog = new CDialog.Builder(GameActivity.this)
                        .setTitle(getString(R.string.exit_game))
                        .setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                finish();
                            }
                        })
                        .setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        })
                        .setCanceledOnTouchOutside(false)
                        .show();
                cDialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            }
        });

        MobileAds.initialize(this, "Put_Your_adId_Here");
        mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder()
                .build();
        mAdView.loadAd(adRequest);
    }

    @Override
    public void onSwatchSelected() {
        playerOneNameTextView.setTextColor(Color.WHITE);
        playerTwoNameTextView.setTextColor(Color.WHITE);
        playerOneLinearLayout.setBackgroundColor(BaseActivity.COLOR3);
        playerTwoLinearLayout.setBackgroundColor(getResources().getColor(R.color.opacity25));
    }

    @Override
    public void onPlayerChanged() {
        playerOneNameTextView.setTextColor(Color.WHITE);
        playerTwoNameTextView.setTextColor(Color.WHITE);
        playerTwoLinearLayout.setBackgroundColor(BaseActivity.COLOR3);
        playerOneLinearLayout.setBackgroundColor(getResources().getColor(R.color.opacity25));
    }

    @Override
    public void onResultChanged(int score1, int score2, int turn) {
        if (turn > 1) {
            touch = "yes";
        }
        playerOneScoreTextSwitcher.setText(String.valueOf(score1));
        playerTwoScoreTextSwitcher.setText(String.valueOf(score2));
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
