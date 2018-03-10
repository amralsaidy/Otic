package com.asb.otic.ui.custom;

import android.content.Context;
import android.graphics.Typeface;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;

import com.asb.otic.ui.activity.BaseActivity;

/**
 * Created by Amr Alsaidy on 2017/11/1.
 */

public class CTextView1 extends AppCompatTextView {
    private Typeface font;

    public CTextView1(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs);
        setFonts(context);
    }

    public CTextView1(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
        setFonts(context);

    }

    public CTextView1(Context context) {
        super(context);
        init(null);
        setFonts(context);
    }

    public void setFonts(Context context) {
        if (((BaseActivity) context).language.equals("en")) {
            font = Typeface.createFromAsset(context.getAssets(), "fonts/Ubuntu-Regular.ttf");
            setTypeface(font);
        } else if (((BaseActivity) context).language.equals("ar")) {
            font = Typeface.createFromAsset(context.getAssets(), "fonts/arabic/stc.otf");
            setTypeface(font);
        }
    }

    private void init(AttributeSet attrs) {
        if (attrs!=null) {
//            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.CTextView);
//            String fontName = a.getString(R.styleable.CTextView_fontName);
//            if (fontName!=null) {
//                Typeface myTypeface = Typeface.createFromAsset(getContext().getAssets(), "fonts/" + fontName);
//                setTypeface(myTypeface);
//            }
//            a.recycle();

        }
    }
}
