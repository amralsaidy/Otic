package com.asb.otic.ui.custom;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.asb.otic.R;
import com.asb.otic.ui.activity.BaseActivity;
import com.asb.otic.util.DialogUtils;

import java.lang.ref.WeakReference;

/**
 * Created by Amr Alsaidy on 2017/11/1.
 */

public class CDialog extends Dialog {

    private boolean forceFullScreenWidth = false;

    public CDialog(@NonNull Context context) {
        super(context);
    }

    public CDialog(@NonNull Context context, int themeResId) {
        super(context, themeResId);
    }

    protected CDialog(@NonNull Context context, boolean cancelable, @Nullable OnCancelListener cancelListener) {
        super(context, cancelable, cancelListener);
    }

    public void setForceFullScreenWidth(boolean full) {
        this.forceFullScreenWidth = full;
    }

    public static class Builder {
        private Context context;
        private CDialog cDialog;
        private CharSequence title;
        private CharSequence message;
        private Button positiveButton, negativeButton, neutralButton;
        private Message positiveButtonMessage, negativeButtonMessage, neutralButtonMessage;
        private CharSequence positiveButtonText, negativeButtonText, neutralButtonText;
        private OnClickListener positiveButtonOnClickListener, negativeButtonOnClickListener, neutralButtonOnClickListener;
        private Handler handler;
        private OnCancelListener onCancelListener;
        private float density;
        private int marginLeft;
        private int marginTop;
        private int marginRight;
        private int marginBottom;
        Typeface font;
        private boolean forceFullScreenWidth = false;
        private View dialogTemplate;
        private View viewContent;
        private boolean cancelable = true;
        private boolean canceledOnTouch = true;

        private int layout = 0;


        public Builder(Context context) {
            this.context = context;
            this.density = context.getResources().getDisplayMetrics().density;

            if (((BaseActivity) context).language.equals("en")) {
                font = Typeface.createFromAsset(context.getAssets(), "fonts/AdventPro-Medium.ttf");
            } else if (((BaseActivity) context).language.equals("ar")) {
                font = Typeface.createFromAsset(context.getAssets(), "fonts/arabic/stc.otf");
            }
        }
        public Context getContext() {
            return this.context;
        }

        View.OnClickListener buttonHandler = new View.OnClickListener() {
            public void onClick(View v) {
                Message m = null;
                if (v == positiveButton && positiveButtonMessage != null) {
                    m = Message.obtain(positiveButtonMessage);
                } else if (v == negativeButton && negativeButtonMessage != null) {
                    m = Message.obtain(negativeButtonMessage);
                } else if (v == neutralButton && neutralButtonMessage != null) {
                    m = Message.obtain(neutralButtonMessage);
                }
                if (m != null) {
                    m.sendToTarget();
                }

                // Post a message so we dismiss after the above handlers are executed
                handler.obtainMessage(ButtonHandler.MSG_DISMISS_DIALOG, cDialog)
                        .sendToTarget();
            }
        };

        private static final class ButtonHandler extends Handler {
            // Button clicks have Message.what as the BUTTON{1,2,3} constant
            private static final int MSG_DISMISS_DIALOG = 1;

            private WeakReference<DialogInterface> mDialog;

            public ButtonHandler(DialogInterface dialog) {
                mDialog = new WeakReference<>(dialog);
            }

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {

                    case DialogInterface.BUTTON_POSITIVE:
                    case DialogInterface.BUTTON_NEGATIVE:
                    case DialogInterface.BUTTON_NEUTRAL:
                        ((OnClickListener) msg.obj).onClick(mDialog.get(), msg.what);
                        break;

                    case MSG_DISMISS_DIALOG:
                        ((DialogInterface) msg.obj).dismiss();
                }
            }
        }

        public Builder setTitle(CharSequence title) {
            this.title = title;
            return this;
        }

        public Builder setTitle(int resId) {
            this.title = context.getString(resId);
            return this;
        }

        public Builder setMessage(CharSequence message) {
            this.message = message;
            return this;
        }

        public Builder setMessage(int resId) {
            this.message = context.getString(resId);
            return this;
        }

        public Builder setPositiveButton(CharSequence positiveButtonText, OnClickListener positiveButtonOnClickListener) {
            this.positiveButtonText = positiveButtonText;
            this.positiveButtonOnClickListener = positiveButtonOnClickListener;
            return this;
        }

        public Builder setPositiveButton(int resId, OnClickListener positiveButtonOnClickListener) {
            this.positiveButtonText = context.getString(resId);
            this.positiveButtonOnClickListener = positiveButtonOnClickListener;
            return this;
        }

        public Builder setNegativeButton(CharSequence negativeButtonText, OnClickListener negativeButtonOnClickListener) {
            this.negativeButtonText = negativeButtonText;
            this.negativeButtonOnClickListener = negativeButtonOnClickListener;
            return this;
        }

        public Builder setNegativeButton(int resId, OnClickListener negativeButtonOnClickListener) {
            this.negativeButtonText = context.getString(resId);
            this.negativeButtonOnClickListener = negativeButtonOnClickListener;
            return this;
        }

        public Builder setNeutralButton(CharSequence neutralButtonText, OnClickListener neutralButtonOnClickListener) {
            this.neutralButtonText = neutralButtonText;
            this.neutralButtonOnClickListener = neutralButtonOnClickListener;
            //setButton(DialogInterface.BUTTON_NEGATIVE, mNeutralButtonText, listener, mButtonNeutralMessage);
            return this;
        }

        public Builder setNeutralButton(int resId, OnClickListener neutralButtonOnClickListener) {
            this.neutralButtonText = context.getString(resId);
            this.neutralButtonOnClickListener = neutralButtonOnClickListener;

            return this;
        }

        public Builder setPositiveButtonMessage(Message positiveButtonMessage) {
            this.positiveButtonMessage = positiveButtonMessage;
            return this;
        }

        public Builder setNegativeButtonMessage(Message negativeButtonMessage) {
            this.negativeButtonMessage = negativeButtonMessage;
            return this;
        }

        public Builder setPositiveButtonText(CharSequence positiveButtonText) {
            this.positiveButtonText = positiveButtonText;
            return this;
        }

        public Builder setNegativeButtonText(CharSequence negativeButtonText) {
            this.negativeButtonText = negativeButtonText;
            return this;
        }

        public Builder setPositiveButtonOnClickListener(OnClickListener positiveButtonOnClickListener) {
            this.positiveButtonOnClickListener = positiveButtonOnClickListener;
            return this;
        }

        public Builder setNegativeButtonOnClickListener(OnClickListener negativeButtonOnClickListener) {
            this.negativeButtonOnClickListener = negativeButtonOnClickListener;
            return this;
        }


        public Builder setForceFullScreenWidth(boolean full) {
            forceFullScreenWidth = full;
            return this;
        }

        public Builder setView(View viewContent) {
            this.viewContent = viewContent;
            return this;
        }

        private void setupButtonListener() {
            // TODO Auto-generated method stub
            setButton(BUTTON_POSITIVE, positiveButtonOnClickListener, positiveButtonMessage);
            setButton(BUTTON_NEGATIVE, negativeButtonOnClickListener, negativeButtonMessage);
            setButton(BUTTON_NEUTRAL, neutralButtonOnClickListener, neutralButtonMessage);
        }

        public void setButton(int whichButton, OnClickListener listener, Message msg) {

            if (msg == null && listener != null) {
                msg = handler.obtainMessage(whichButton, listener);
            }

            switch (whichButton) {

                case DialogInterface.BUTTON_POSITIVE:
                    //mPositiveButtonText = text;
                    positiveButtonMessage = msg;
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    //mNegativeButtonText = text;
                    negativeButtonMessage = msg;
                    break;

                case DialogInterface.BUTTON_NEUTRAL:
                    //mNeutralButtonText = text;
                    neutralButtonMessage = msg;
                    break;

                default:
                    throw new IllegalArgumentException("Button does not exist");
            }
        }

        private boolean setupTitle() {
            // TODO Auto-generated method stub
            if (!TextUtils.isEmpty(title)) {
                TextView txtTitle = dialogTemplate.findViewById(R.id.dialog_title_tv_id);
                txtTitle.setText(title);
                txtTitle.setTypeface(font);
                return true;
            } else {
                View titlePanel = dialogTemplate.findViewById(R.id.dialog_title_fl_id);
                titlePanel.setVisibility(View.GONE);
            }
            return false;
        }

        private boolean setupMessage() {
            // TODO Auto-generated method stub
            if (!TextUtils.isEmpty(message)) {
                TextView txtMessage = dialogTemplate.findViewById(R.id.dialog_message_tv_id);
                txtMessage.setText(message);
                txtMessage.setTypeface(font);
                return true;
            } else {
                View messagePanel = dialogTemplate.findViewById(R.id.dialog_message_fl_id);
                messagePanel.setVisibility(View.GONE);
            }
            return false;
        }

        private boolean setupButton() {
            // TODO Auto-generated method stub
            int BIT_BUTTON_POSITIVE = 1;
            int BIT_BUTTON_NEGATIVE = 2;
            int BIT_BUTTON_NEUTRAL = 4;
            int whichButtons = 0;
            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setColor(BaseActivity.COLOR3);
            gradientDrawable.setCornerRadius(context.getResources().getDimensionPixelSize(R.dimen.cornerRadius));
            positiveButton = dialogTemplate.findViewById(R.id.positive_btn_id);
            positiveButton.setOnClickListener(buttonHandler);
            positiveButton.setBackground(gradientDrawable);
            positiveButton.setTypeface(font);
            if (TextUtils.isEmpty(positiveButtonText)) {
                positiveButton.setVisibility(View.GONE);
            } else {
                positiveButton.setText(positiveButtonText);
                positiveButton.setVisibility(View.VISIBLE);
                whichButtons = whichButtons | BIT_BUTTON_POSITIVE;
            }

            negativeButton = dialogTemplate.findViewById(R.id.negative_btn_id);
            negativeButton.setOnClickListener(buttonHandler);
            negativeButton.setBackground(gradientDrawable);;
            negativeButton.setTypeface(font);
            if (TextUtils.isEmpty(negativeButtonText)) {
                negativeButton.setVisibility(View.GONE);
            } else {
                negativeButton.setText(negativeButtonText);
                negativeButton.setVisibility(View.VISIBLE);

                whichButtons = whichButtons | BIT_BUTTON_NEGATIVE;
            }

            neutralButton = dialogTemplate.findViewById(R.id.neutral_btn_id);
            neutralButton.setOnClickListener(buttonHandler);
            neutralButton.setBackground(gradientDrawable);
            neutralButton.setTypeface(font);
            if (TextUtils.isEmpty(neutralButtonText)) {
                neutralButton.setVisibility(View.GONE);
            } else {
                neutralButton.setText(neutralButtonText);
                neutralButton.setVisibility(View.VISIBLE);

                whichButtons = whichButtons | BIT_BUTTON_NEUTRAL;
            }

            /*
             * If you have only one button it fills 50% of the space and is centered
             */
            if (whichButtons == BIT_BUTTON_POSITIVE) {
                centerButton(positiveButton);
            } else if (whichButtons == BIT_BUTTON_NEGATIVE) {
                centerButton(negativeButton);
            } else if (whichButtons == BIT_BUTTON_NEUTRAL) {
                centerButton(neutralButton);
            }

            return whichButtons != 0;
        }

        private void centerButton(Button button) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) button.getLayoutParams();
            params.gravity = Gravity.CENTER_HORIZONTAL;
            params.weight = 0.5f;
            button.setLayoutParams(params);
//            View leftSpacer = dialogTemplate.findViewById(R.id.leftSpacer);
//            leftSpacer.setVisibility(View.VISIBLE);
//            View rightSpacer = dialogTemplate.findViewById(R.id.rightSpacer);
//            rightSpacer.setVisibility(View.VISIBLE);
        }

        private boolean setupView() {
            // TODO Auto-generated method stub
            if (viewContent == null) {
                return false;
            } else {
                FrameLayout custom = dialogTemplate.findViewById(R.id.view_content_fl_id);
                custom.addView(viewContent, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            }
            return true;
        }


        public Builder setOnCancelListener(OnCancelListener listener) {
            this.onCancelListener = listener;
            return this;
        }

        public Builder setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        public Builder setCanceledOnTouchOutside(boolean canceledOnTouch) {
            this.canceledOnTouch = canceledOnTouch;
            return this;
        }

        public CDialog create() {
            LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = layoutInflater.inflate(layout == 0 ? R.layout.dialog_layout : layout, null);
            if (view == null) {
                return null;
            }

            View layout1 = view.findViewById(R.id.dialog_ll_id);
            GradientDrawable gradientDrawable =
                    new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BaseActivity.COLOR1, BaseActivity.COLOR2});
            gradientDrawable.setCornerRadius(context.getResources().getDimensionPixelSize(R.dimen.cornerRadius));
            int insetLeft = context.getResources().getDimensionPixelSize(R.dimen.insetLeft);
            int insetRight = context.getResources().getDimensionPixelSize(R.dimen.insetRight);
            InsetDrawable insetDrawable =  new InsetDrawable(gradientDrawable, insetLeft, 0, insetRight, 0);
            layout1.setBackground(insetDrawable);

            dialogTemplate = view;

            boolean hasTitle = setupTitle();
            boolean hasMessage = setupMessage();
//            boolean hasList = setupList();
            boolean hasButton = setupButton();
            boolean hasView = setupView();

//            if (hasList) {
//                LinearLayout content = dialogTemplate.findViewById(R.id.contentPanel);
//                content.setVisibility(View.VISIBLE);
//                ((LinearLayout.LayoutParams) dialogTemplate.findViewById(R.id.customPanel).getLayoutParams()).weight = 0;
//            }

            if (hasView) {
                FrameLayout content = dialogTemplate.findViewById(R.id.dialog_message_fl_id);
                content.removeAllViews();
                dialogTemplate.findViewById(R.id.dialog_message_fl_id).setVisibility(View.GONE);
            } else {
                dialogTemplate.findViewById(R.id.view_container_fl_id).setVisibility(View.GONE);
            }

            if (!hasButton) {
                View buttonPanel = dialogTemplate.findViewById(R.id.buttons_content_fl_id);
                buttonPanel.setVisibility(View.GONE);
                ((ViewGroup) dialogTemplate).removeView(buttonPanel);
            }

            CDialog customDialog = new CDialog(context, R.style.CustomDialog);
            customDialog.setContentView(dialogTemplate);
            customDialog.setForceFullScreenWidth(forceFullScreenWidth);
            cDialog = customDialog;
            cDialog.setCanceledOnTouchOutside(canceledOnTouch);
            cDialog.setCancelable(cancelable);
            handler = new Builder.ButtonHandler(cDialog);
            setupButtonListener();
//            if (onCancelListener != null) {
//                cDialog.setOnCancelListener(onCancelListener);
//            }
            return cDialog;
        }

        public CDialog show() {
            CDialog dialog = create();
            dialog.show();
            return dialog;
        }
    }
}
