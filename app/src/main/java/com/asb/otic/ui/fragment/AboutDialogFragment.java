package com.asb.otic.ui.fragment;

import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.asb.otic.R;
import com.asb.otic.ui.activity.BaseActivity;
import com.asb.otic.ui.activity.SettingsActivity;
import com.asb.otic.ui.custom.CDialog;
import com.asb.otic.ui.custom.CTextView;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by Amr Alsaidy on 2017/11/1.
 */

public class AboutDialogFragment extends DialogFragment {
    @BindView(R.id.about_game_tv_id)
    TextView textView;

    public static AboutDialogFragment newInstance() {
        return new AboutDialogFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_dialog_about, container);
        ButterKnife.bind(this, view);

        View layout = view.findViewById(R.id.dialog_ll_id);
        GradientDrawable gradientDrawable =
                new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BaseActivity.COLOR1, BaseActivity.COLOR2});
        gradientDrawable.setCornerRadius(view.getResources().getDimensionPixelSize(R.dimen.cornerRadius));
        int insetLeft = view.getResources().getDimensionPixelSize(R.dimen.insetLeft);
        int insetRight = view.getResources().getDimensionPixelSize(R.dimen.insetRight);
        InsetDrawable insetDrawable =  new InsetDrawable(gradientDrawable, insetLeft, 0, insetRight, 0);
//        layout.setBackgroundColor(Color.TRANSPARENT);
        layout.setBackground(insetDrawable);

        textView.setTypeface(((SettingsActivity) getActivity()).font);
        Context context = getActivity().getApplicationContext();
        InputStream input;
        String fileName = "";
        if (((SettingsActivity) getActivity()).language.equals("en")) {
            fileName = "about_en.txt";
        } else if (((SettingsActivity) getActivity()).language.equals("ar")) {
            fileName = "about_ar.txt";
        }
        try {
            input = context.getAssets().open(fileName);

            DataInputStream in = new DataInputStream(input);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String strLine;
            // Read File Line By Line
            while ((strLine = br.readLine()) != null) {
                textView.setText(strLine);
            }
            // Close the input stream
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        CDialog dialog = new CDialog(getActivity(), R.style.CustomDialog);
        dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        return dialog;
    }

}

