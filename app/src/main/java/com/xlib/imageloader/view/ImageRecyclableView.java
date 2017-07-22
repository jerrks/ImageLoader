package com.xlib.imageloader.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

import java.io.File;

public class ImageRecyclableView extends ImageView {

    protected Bitmap bitmap;

    public ImageRecyclableView(Context context) {
        super(context);
    }

    public ImageRecyclableView(Context context, AttributeSet attrs) {
    	super(context, attrs);
    }

    public ImageRecyclableView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        try {
            if(drawable!=null && drawable instanceof BitmapDrawable){
                Bitmap bm = ((BitmapDrawable)drawable).getBitmap();
                bitmap = bm;
            }else{
                bitmap = null;
            }
            super.setImageDrawable(drawable);
        }catch (Exception e){
            e.printStackTrace();
            System.gc();
        }
    }

    @Override
    public void onDraw (Canvas canvas) {
        try {
            if(bitmap != null && bitmap.isRecycled()){
                setImageBitmap(null);
                System.gc();
            }else{
                super.onDraw(canvas);
            }
        } catch (Exception e) {
            super.setImageDrawable(null);
            e.printStackTrace();
            System.gc();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            if(bitmap != null && bitmap.isRecycled()){
                setImageBitmap(null);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } catch (OutOfMemoryError error) {
            super.setImageDrawable(null);
            System.gc();
        }
    }

    /**
     * play gif image view data
     * */
    public void setGifPath(String path){}

    /**
     * play gif image view data
     * */
    public void setGifPath(File file){}

    /**
     * start play gif image
     * */
    public void startPlayGif(){}

    /**
     * stop play gif image
     * */
    public void stopPlayGif(){}

    public void releaseGif(){};
}