package com.asb.otic.ui.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.asb.otic.R;

import java.util.Locale;

/**
 * Created by Amr Alsaidy on 2017/11/1.
 */

public class BaseActivity extends AppCompatActivity {
    public static int COLOR1;
    public static int COLOR2;
    public static int COLOR3;
    public static int COLOR4;

    public SharedPreferences sharedPreferences;
    public SharedPreferences.Editor editor;

    public String language;
    public boolean soundEnabled;
    public String deviceName;
    public String yourName;
    public int boardSize;
    public int preferColor;
    public Typeface font;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (COLOR1 == 0 && COLOR2 == 0 && COLOR3 == 0 && COLOR4 == 0) {
            COLOR1 = getResources().getColor(R.color.md_grey_800);
            COLOR2 = getResources().getColor(R.color.md_grey_700);
            COLOR3 = getResources().getColor(R.color.md_cyan_300);
            COLOR4 = getResources().getColor(R.color.md_red_500);;
        }

        sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE);
        editor = sharedPreferences.edit();
        language = sharedPreferences.getString("LANGUAGE", Locale.getDefault().getDisplayLanguage().toLowerCase().substring(0,2));
        soundEnabled = sharedPreferences.getBoolean("SOUND_ENABLED", true);
        deviceName = sharedPreferences.getString("DEVICE_NAME", "Android");
        yourName = sharedPreferences.getString("YOUR_NAME", "Me");
        boardSize = sharedPreferences.getInt("BOARD_SIZE", 2);
        preferColor = sharedPreferences.getInt("PREFER_COLOR", 1);

        switch (preferColor) {
            case 1:
                COLOR1 = getResources().getColor(R.color.md_grey_800);
                COLOR2 = getResources().getColor(R.color.md_grey_700);
                COLOR3 = getResources().getColor(R.color.md_cyan_300);
                COLOR4 = getResources().getColor(R.color.md_orange_900);
                break;
            case 2:
                COLOR1 = getResources().getColor(R.color.md_teal_500);
                COLOR2 = getResources().getColor(R.color.md_teal_300);
                COLOR3 = getResources().getColor(R.color.md_green_200);
                COLOR4 = getResources().getColor(R.color.md_orange_900);
                break;
            case 3:
                COLOR1 = getResources().getColor(R.color.md_red_A400);
                COLOR2 = getResources().getColor(R.color.md_red_A100);
                COLOR3 = getResources().getColor(R.color.md_yellow_600);
                COLOR4 = getResources().getColor(R.color.md_blue_700);
                break;
            case 4:
                COLOR1 = getResources().getColor(R.color.md_brown_400);
                COLOR2 = getResources().getColor(R.color.md_brown_300);
                COLOR3 = getResources().getColor(R.color.md_red_200);
                COLOR4 = getResources().getColor(R.color.md_blue_700);
                break;
        }

        if (language.equals("en")) {
            forceLocale(getApplicationContext(), "en");
            font = Typeface.createFromAsset(getAssets(), "fonts/AdventPro-Medium.ttf");
        }else if(language.equals("ال") || language.equals("ar")){
            forceLocale(getApplicationContext(), "ar");
            language = "ar";
            font = Typeface.createFromAsset(getAssets(), "fonts/arabic/stc.otf");
        }

    }

    @SuppressWarnings("deprecation")
    public static void forceLocale(Context context, String localeCode) {
        String localeCodeLowerCase = localeCode.toLowerCase();

        Resources resources = context.getApplicationContext().getResources();
        Configuration overrideConfiguration = resources.getConfiguration();
        Locale overrideLocale = new Locale(localeCodeLowerCase);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            overrideConfiguration.setLocale(overrideLocale);
        } else {
            overrideConfiguration.locale = overrideLocale;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.getApplicationContext().createConfigurationContext(overrideConfiguration);
        } else {
            resources.updateConfiguration(overrideConfiguration, null);
        }
    }
}
