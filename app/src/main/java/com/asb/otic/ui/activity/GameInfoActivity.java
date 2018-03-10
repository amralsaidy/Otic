package com.asb.otic.ui.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

import com.asb.otic.R;
import com.asb.otic.ui.custom.CDialog;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by Amr Alsaidy on 2017/11/1.
 */

public class GameInfoActivity extends BaseActivity {
    @BindView(R.id.rate_btn_id)
    Button rateButton;
    @BindView(R.id.share_btn_id)
    Button shareButton;

    private GradientDrawable gradientDrawable;
    private static CDialog cDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_info);
        ButterKnife.bind(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Window w = getWindow(); // in Activity's onCreate() for instance
            w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }

        View layout = findViewById(R.id.game_info_ll_id);
        gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{COLOR1, COLOR2});
        layout.setBackground(gradientDrawable);

        rateButton.setTypeface(font);
        shareButton.setTypeface(font);
        rateButton.setBackgroundColor(BaseActivity.COLOR3);
        shareButton.setBackgroundColor(BaseActivity.COLOR3);

    }

    @OnClick(R.id.rate_btn_id)
    public void showRateDialog() {
        cDialog = new CDialog.Builder(this)
                .setTitle(getString(R.string.rate_otic))
                .setMessage(getString(R.string.please_rate_otic))
                .setPositiveButton(getString(R.string.rate), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (this != null) {
                            String link = "market://details?id=";
                            try {
                                // play market available
                                getPackageManager().getPackageInfo("com.android.vending", 0);
                                // not available
                            } catch (PackageManager.NameNotFoundException e) {
                                e.printStackTrace();
                                // should use browser
                                link = "https://play.google.com/store/apps/details?id=";
                            }
                            // starts external action
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse(link + getPackageName())));
                        }
                    }
                })
                .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                })
                .setCanceledOnTouchOutside(false)
                .show();
        cDialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    @OnClick(R.id.share_btn_id)
    public void shareDialog() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "Otic");
        String sAux = "\n" + getString(R.string.let_me_share_you) + "\n\n";
        sAux = sAux + "https://play.google.com/store/apps/details?id=" + getPackageName() + "\n\n";
        i.putExtra(Intent.EXTRA_TEXT, sAux);
        startActivity(Intent.createChooser(i, getString(R.string.share_by)));
    }
}

