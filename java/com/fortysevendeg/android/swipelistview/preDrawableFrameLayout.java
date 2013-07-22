package com.fortysevendeg.android.swipelistview;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class PreDrawableFrameLayout extends FrameLayout {
    private Command doBeforeDraw = null;

    public PreDrawableFrameLayout(final Context context) {
        super(context);
    }

    public PreDrawableFrameLayout(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public PreDrawableFrameLayout(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);    //To change body of overridden methods use File | Settings | File Templates.
        if (doBeforeDraw != null) doBeforeDraw.execute();
        doBeforeDraw = null;
    }

    public void preDraw(Command doBeforeDraw) {
        this.doBeforeDraw = doBeforeDraw;
    }
}
