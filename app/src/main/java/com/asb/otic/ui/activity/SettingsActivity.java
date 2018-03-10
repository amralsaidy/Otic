package com.asb.otic.ui.activity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.asb.otic.R;
import com.asb.otic.ui.custom.CDialog;
import com.asb.otic.ui.custom.CTextView;
import com.asb.otic.ui.fragment.AboutDialogFragment;
import com.asb.otic.ui.fragment.HowToPlayDialogFragment;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by Amr Alsaidy on 2017/11/1.
 */

public class SettingsActivity extends BaseActivity {
    @BindView(R.id.status_bar_id)
    View statusBarView;
    @BindView(R.id.english_rbtn_id)
    RadioButton englishRadioButton;
    @BindView(R.id.arabic_rbtn_id)
    RadioButton arabicRadioButton;
    @BindView(R.id.group1_color_fl_id)
    FrameLayout group1ColorFrameLayout;
    @BindView(R.id.group2_color_fl_id)
    FrameLayout group2ColorFrameLayout;
    @BindView(R.id.group3_color_fl_id)
    FrameLayout group3ColorFrameLayout;
    @BindView(R.id.group4_color_fl_id)
    FrameLayout group4ColorFrameLayout;
    @BindView(R.id.settings_ll_id)
    public LinearLayout settingsLinearLayout;
    @BindView(R.id.back_iv_id)
    public ImageView backImageView;
    @BindView(R.id.about_game_ctv_id)
    public CTextView aboutGameCTextView;
    @BindView(R.id.how_to_play_ctv_id)
    public CTextView howToPlayCTextView;
    @BindView(R.id.game_info_ctv_id)
    public CTextView gameInfoCTextView;
    @BindView(R.id.sound_sw_id)
    Switch soundSwitch;
    @BindView(R.id.device_name_tv_id)
    TextView deviceNameTextView;
    @BindView(R.id.your_name_tv_id)
    TextView yourNameTextView;
    @BindView(R.id.board_radius_sb_id)
    SeekBar boardRadiusSeekBar;
    @BindView(R.id.board_radius_et_id)
    TextView boardRadiusTextView;

    private AboutDialogFragment aboutDialogFragment;
    private HowToPlayDialogFragment howToPlayDialogFragment;

    public static class Factory {
        public static Intent getIntent(Context context) {
            return new Intent(context, SettingsActivity.class);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        ButterKnife.bind(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Window w = getWindow(); // in Activity's onCreate() for instance
            w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }
        statusBarView.setBackgroundColor(COLOR1);

        findViewById(R.id.color1_group1_tv_id).setBackgroundColor(getResources().getColor(R.color.md_grey_800));
        findViewById(R.id.color2_group1_tv_id).setBackgroundColor(getResources().getColor(R.color.md_grey_700));
        findViewById(R.id.color3_group1_tv_id).setBackgroundColor(getResources().getColor(R.color.md_cyan_300));
        findViewById(R.id.color4_group1_tv_id).setBackgroundColor(getResources().getColor(R.color.md_orange_900));

        findViewById(R.id.color1_group2_tv_id).setBackgroundColor(getResources().getColor(R.color.md_teal_500));
        findViewById(R.id.color2_group2_tv_id).setBackgroundColor(getResources().getColor(R.color.md_teal_300));
        findViewById(R.id.color3_group2_tv_id).setBackgroundColor(getResources().getColor(R.color.md_green_200));
        findViewById(R.id.color4_group2_tv_id).setBackgroundColor(getResources().getColor(R.color.md_orange_900));

        findViewById(R.id.color1_group3_tv_id).setBackgroundColor(getResources().getColor(R.color.md_red_A400));
        findViewById(R.id.color2_group3_tv_id).setBackgroundColor(getResources().getColor(R.color.md_red_A100));
        findViewById(R.id.color3_group3_tv_id).setBackgroundColor(getResources().getColor(R.color.md_yellow_600));
        findViewById(R.id.color4_group3_tv_id).setBackgroundColor(getResources().getColor(R.color.md_blue_700));

        findViewById(R.id.color1_group4_tv_id).setBackgroundColor(getResources().getColor(R.color.md_brown_400));
        findViewById(R.id.color2_group4_tv_id).setBackgroundColor(getResources().getColor(R.color.md_brown_300));
        findViewById(R.id.color3_group4_tv_id).setBackgroundColor(getResources().getColor(R.color.md_red_200));
        findViewById(R.id.color4_group4_tv_id).setBackgroundColor(getResources().getColor(R.color.md_blue_700));

        if (language.equals("en")) {
            englishRadioButton.setChecked(true);
        } else if (language.equals("ar")) {
            arabicRadioButton.setChecked(true);
        }

        englishRadioButton.setTypeface(font);
        arabicRadioButton.setTypeface(font);

        final GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(GradientDrawable.RECTANGLE);
        gradientDrawable.setStroke(55, Color.DKGRAY);

        switch (preferColor) {
            case 1:
                group1ColorFrameLayout.setBackground(gradientDrawable);
                break;
            case 2:
                group2ColorFrameLayout.setBackground(gradientDrawable);
                break;
            case 3:
                group3ColorFrameLayout.setBackground(gradientDrawable);
                break;
            case 4:
                group4ColorFrameLayout.setBackground(gradientDrawable);
                break;
        }

        group1ColorFrameLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                group1ColorFrameLayout.setBackground(gradientDrawable);
                group2ColorFrameLayout.setBackground(null);
                group3ColorFrameLayout.setBackground(null);
                group4ColorFrameLayout.setBackground(null);
                putPreferColor(1);
            }
        });

        group2ColorFrameLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                group2ColorFrameLayout.setBackground(gradientDrawable);
                group1ColorFrameLayout.setBackground(null);
                group3ColorFrameLayout.setBackground(null);
                group4ColorFrameLayout.setBackground(null);
                putPreferColor(2);
            }
        });

        group3ColorFrameLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                group3ColorFrameLayout.setBackground(gradientDrawable);
                group1ColorFrameLayout.setBackground(null);
                group2ColorFrameLayout.setBackground(null);
                group4ColorFrameLayout.setBackground(null);
                putPreferColor(3);
            }
        });

        group4ColorFrameLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                group4ColorFrameLayout.setBackground(gradientDrawable);
                group1ColorFrameLayout.setBackground(null);
                group2ColorFrameLayout.setBackground(null);
                group3ColorFrameLayout.setBackground(null);
                putPreferColor(4);
            }
        });

        if (soundEnabled) {
            soundSwitch.setChecked(true);
        } else if (!soundEnabled) {
            soundSwitch.setChecked(false);
        }
        soundSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                putSoundOption(isChecked);
            }
        });

        deviceNameTextView.setText(deviceName);
        deviceNameTextView.setTypeface(font);
        final EditText deviceEditText = new EditText(this);
        deviceEditText.setHeight(80);
        deviceEditText.setWidth(LinearLayout.LayoutParams.MATCH_PARENT);
        deviceEditText.setGravity(Gravity.CENTER);
        deviceEditText.setBackgroundColor(Color.WHITE);
        deviceEditText.setPadding(20, 20, 20, 20);
        deviceEditText.setText(deviceNameTextView.getText().toString());
        deviceNameTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CDialog.Builder builder = new CDialog.Builder(SettingsActivity.this);
                if (deviceEditText.getParent() != null)
                    ((ViewGroup) deviceEditText.getParent()).removeView(deviceEditText); // <- fix
                builder.setView(deviceEditText);
                CDialog cDialog = builder
                        .setTitle(getString(R.string.change_device_name))
                        .setPositiveButton(getString(R.string.update), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                putDeviceName(deviceEditText.getText().toString());
                                deviceNameTextView.setText(deviceEditText.getText().toString());
                            }
                        })
                        .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.cancel();
                            }
                        })
                        .setCanceledOnTouchOutside(false)
                        .show();
                cDialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            }
        });

        yourNameTextView.setText(yourName);
        yourNameTextView.setTypeface(font);
        final EditText nameEditText = new EditText(this);
        nameEditText.setHeight(80);
        nameEditText.setWidth(LinearLayout.LayoutParams.MATCH_PARENT);
        nameEditText.setGravity(Gravity.CENTER);
        nameEditText.setBackgroundColor(Color.WHITE);
        nameEditText.setPadding(20, 20, 20, 20);
        nameEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        nameEditText.setText(yourNameTextView.getText().toString());
        yourNameTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CDialog.Builder builder = new CDialog.Builder(SettingsActivity.this);
                if (nameEditText.getParent() != null)
                    ((ViewGroup) nameEditText.getParent()).removeView(nameEditText); // <- fix
                builder.setView(nameEditText);
                CDialog cDialog = builder
                        .setTitle(getString(R.string.change_your_name))
                        .setPositiveButton(getString(R.string.update), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                putYourName(nameEditText.getText().toString());
                                yourNameTextView.setText(nameEditText.getText().toString());
                            }
                        })
                        .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.cancel();
                            }
                        })
                        .setCanceledOnTouchOutside(false)
                        .show();
                cDialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            }
        });

        englishRadioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                forceLocale(getApplicationContext(), "en");
                putLanguage("en");
                SettingsActivity.this.recreate();

            }
        });

        arabicRadioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                forceLocale(getApplicationContext(), "ar");
                putLanguage("ar");
                SettingsActivity.this.recreate();
            }
        });


        boardRadiusSeekBar.post(new Runnable() {
            @Override
            public void run() {
                boardRadiusSeekBar.setProgress(boardSize - 2);
            }
        });
        boardRadiusTextView.setText(String.valueOf(boardSize));
        boardRadiusSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
                boardRadiusTextView.setText(String.format("%S", progress + 2));
                putBoardSize(progress + 2);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

    }




    private void putLanguage(String language) {
        editor.putString("LANGUAGE", language);
        editor.apply();
    }

    private void putPreferColor(int preferColor) {
        editor.putInt("PREFER_COLOR", preferColor);
        editor.apply();
    }

    private void putSoundOption(boolean soundEnabled) {
        editor.putBoolean("SOUND_ENABLED", soundEnabled);
        editor.apply();
    }

    private void putDeviceName(String deviceName) {
        editor.putString("DEVICE_NAME", deviceName);
        editor.apply();
    }

    private void putYourName(String yourName) {
        editor.putString("YOUR_NAME", yourName);
        editor.apply();
    }

    private void putBoardSize(int boardSize) {
        editor.putInt("BOARD_SIZE", boardSize);
        editor.apply();
    }

    @OnClick(R.id.best_scores_ctv_id)
    public void showBestScores() {
        startActivity(new Intent(this, BestScoreActivity.class));
    }

    @OnClick(R.id.about_game_ctv_id)
    public void showAboutGame() {
        aboutDialogFragment = AboutDialogFragment.newInstance();
        aboutDialogFragment.show(getSupportFragmentManager(), "aboutDialogFragment");
    }

    @OnClick(R.id.how_to_play_ctv_id)
    public void showHowToPlay() {
        if ((howToPlayDialogFragment == null)) {
            howToPlayDialogFragment = HowToPlayDialogFragment.newInstance();
            howToPlayDialogFragment.show(getSupportFragmentManager(), "howToPlayDialogFragment");
        }else if (!howToPlayDialogFragment.isAdded()){
            howToPlayDialogFragment.show(getSupportFragmentManager(), "howToPlayDialogFragment");
        }
    }

    @OnClick(R.id.game_info_ctv_id)
    public void showGameInfo() {
        startActivity(new Intent(this, GameInfoActivity.class));
    }

    @OnClick(R.id.back_iv_id)
    public void back() {
        if (getIntent().getStringExtra("CALLER").equals("MainActivity")) {
            // put the String to pass back into an Intent and close this activity
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            super.onBackPressed();
        } else if (getIntent().getStringExtra("CALLER").equals("GameActivity")) {
            super.onBackPressed();
        }
    }

    @Override
    public void onBackPressed() {
        if (getIntent().getStringExtra("CALLER").equals("MainActivity")) {
            // put the String to pass back into an Intent and close this activity
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            super.onBackPressed();
        } else if (getIntent().getStringExtra("CALLER").equals("GameActivity")) {
            super.onBackPressed();
        }
    }


}
