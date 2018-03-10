package com.asb.otic.ui.custom;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.AttributeSet;

/**
 * Created by Amr Alsaidy on 2017/11/1.
 */

public class Swatch extends android.support.v7.widget.AppCompatImageView {

    public int id;
    public int index;
    public int color;
    /**
     * Position of the swatch (in relative coordinates from -1.0 to 1.0)
     */
    public PointF position;
    /**
     * Animation delay of the swatch (in ms).
     */
    public int animDelay;
    Drawable background;
    GradientDrawable drawable;

    public Swatch(Context context, int id) {
        super(context);
        this.id = id;
    }

    public Swatch(Context context, AttributeSet attrs, int id, int index) {
        super(context, attrs);
        this.id = id;
        this.index = index;
        initSwatch();
    }


    public Swatch(final Context context, final int id, final int index, final int color, final PointF position,
                  final int animDelay, final Drawable background) {
        super(context);
        this.id = id;
        this.index = index;
        this.color = color;
        this.position = position;
        this.animDelay = animDelay;
        this.background = background;
        initSwatch();
    }

    public void initSwatch() {
        drawable = new GradientDrawable();

//        drawable = (GradientDrawable) ResourcesCompat.getDrawable(getResources(), R.drawable.circle_shape, null);
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        setImageDrawable(drawable);
        if (background != null) {
            setBackgroundCorrect(background);
        }
    }

    /**
     * Update stroke width.
     *
     * @param strokeWidth the new stroke width
     */
    public void updateStrokeWidth(final int strokeWidth) {
        final GradientDrawable drawable = (GradientDrawable) getDrawable();
//        drawable.setStroke(strokeWidth, Color.WHITE);
//        drawable.setStroke(strokeWidth, HexagonalBoardView.calculateStrokeColor(color));
    }

    /**
     * Sets the background drawable. Uses the correct API according to API level.
     *
     * @param drawable the new background drawable
     */
    @SuppressWarnings("deprecation")
    private void setBackgroundCorrect(final Drawable drawable) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            setBackgroundDrawable(drawable);
        } else {
            setBackground(drawable);
        }
    }
}
