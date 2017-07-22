package com.xlib.imageloader.view.gif;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;

import com.xlib.imageloader.view.ImageRecyclableView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class GifImageView extends ImageRecyclableView {

    private String mGifFilePath;

    private GifDecoder mGifDecoder;
    private boolean isAnimating;
    private boolean isShouldClear;
    private Thread mAnimationThread;
    private long mFramesDisplayDuration = -1l;
    Bitmap mTempBitmap;
    Object mLock = new Object();

    private final Runnable mUpdateViewCallback = new PlayGifCallback(PlayGifCallback.ACTION_UPDATE_VIEW);
    private final Runnable mCleanUpCallback = new PlayGifCallback(PlayGifCallback.ACTION_CLEAN_UP);
    private final Runnable mPayGifCallback = new PlayGifCallback(PlayGifCallback.ACTION_PAY_GIF);

    public GifImageView(final Context context, final AttributeSet attrs,int defStyle) {
        super(context, attrs,defStyle);
    }

    public GifImageView(final Context context, final AttributeSet attrs) {
        this(context, attrs,-1);
    }

    public GifImageView(final Context context) {
        this(context,null);
    }

    /**
     * play gif image view data
     * */
    public void setGifPath(String path){
        try{
            setGifPath(new File(path));
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void setGifPath(File file) {
        if(file==null || !file.exists()){
            releaseGif();
        }else{
            // if last gifPath is equals path then return for the gif source is not changed
            final String path = file.getAbsolutePath();
            if(path.equals(mGifFilePath)) return;
            try{
                setGifBytes(readByteFromInputStream(new FileInputStream(file)),path);
                mGifFilePath = path;
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    void setGifBytes(final byte[] bytes,String path) {
        // release preview gif source and prepare current gif and play it after init
        releaseGif();
        synchronized (mLock){
            mGifDecoder = new GifDecoder(path);
            try {
                mGifDecoder.read(bytes);
                mGifDecoder.advance();
            } catch (OutOfMemoryError e) {
                mGifDecoder = null;
                return;
            }
        }
        startPlayGif(true);
    }

    @Override
    public void releaseGif(){
        stopPlayGif();
        mGifFilePath = null;
        synchronized (mLock){
            if(mGifDecoder!=null){
                mGifDecoder.clear();
                mGifDecoder = null;
            }
        }
    }

    @Override
    public void startPlayGif() {
        startPlayGif(true);
    }

    @Override
    public void stopPlayGif() {
        isAnimating = false;
        try {
            if (mAnimationThread != null) {
                mAnimationThread.interrupt();
                mAnimationThread = null;
            }
        }catch (Exception e){}
        removeCallbacks(mPayGifCallback);
    }

    public void onDestroy(){
        stopPlayGif();
        removeCallbacks(mUpdateViewCallback);
        removeCallbacks(mCleanUpCallback);
    }

    public boolean isAnimating() {
        return isAnimating;
    }

    public void clear() {
        isAnimating = false;
        isShouldClear = true;
        stopPlayGif();
        post(mCleanUpCallback);
    }

    private boolean canStart() {
        return (isAnimating && mGifDecoder != null && mAnimationThread == null);
    }

    void startPlayGif(boolean anmation) {
        isAnimating = anmation;
        if (canStart()) {
            mAnimationThread = new Thread(mPayGifCallback);
            mAnimationThread.start();
        }
    }

    public static byte[] readByteFromInputStream(InputStream is) {
        byte[] data = null;
        if (is == null) return data;
        try {
            byte[] buff = new byte[8 * 1024];
            int count = 0;
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            while ((count = is.read(buff)) > 0) {
                os.write(buff, 0, count);
            }
            data = os.toByteArray();
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.gc();
        } finally {
            try {
                if (is != null) is.close();
            } catch (Exception e) {
                e.printStackTrace();
                System.gc();
            }
        }
        return data;
    }

    class PlayGifCallback implements Runnable{

        final static int ACTION_CLEAN_UP = 1;
        final static int ACTION_UPDATE_VIEW = 2;
        final static int ACTION_PAY_GIF = 3;

        final int action;
        PlayGifCallback(int action){
            this.action = action;
        }

        @Override
        public void run() {
            switch (action) {
                case ACTION_CLEAN_UP:
                    synchronized (mLock){
                        cleanUp();
                    }
                    break;
                case ACTION_UPDATE_VIEW:
                    if (mTempBitmap != null && !mTempBitmap.isRecycled()) {
                        setImageBitmap(mTempBitmap);
                    }
                    break;
                case ACTION_PAY_GIF:
                    payGif();
                    break;
                default:
                    break;
            }
        }

        protected void cleanUp(){
            isShouldClear = false;
            try {
                if (mAnimationThread != null) {
                    mAnimationThread.interrupt();
                    mAnimationThread = null;
                }
            }catch (Exception e){}
            if(mGifDecoder!=null) mGifDecoder.clear();
            mGifDecoder = null;
        }

        boolean isGifDecoderInited(){
            return mGifDecoder!=null && mGifDecoder.isInited();
        }

        int getFrameCount(){
            synchronized (mLock){
               return isGifDecoderInited() ? mGifDecoder.getFrameCount() : 0;
            }
        }

        void loadNextFrame(){
            synchronized (mLock){
                mTempBitmap = isGifDecoderInited() ? mGifDecoder.getNextFrame() : null;
            }
        }

        void advanceGif(){
            synchronized (mLock){
                if(isGifDecoderInited()) mGifDecoder.advance();
            }
        }

        int getNextDelay(){
            synchronized (mLock){
                return isGifDecoderInited() ? mGifDecoder.getNextDelay() : 0;
            }
        }

        protected void payGif(){
            if (isShouldClear) {
                post(mCleanUpCallback);
                return;
            }
            if(!isGifDecoderInited()) return;

            final int n = getFrameCount();
            while (isAnimating && n>0) {
                for (int i = 0; i < n; i++) {
                    if (!isAnimating) break;

                    // milliseconds spent on frame decode
                    long frameDecodeTime = 0;
                    try {
                        long before = System.nanoTime();
                        loadNextFrame();
                        frameDecodeTime = (System.nanoTime() - before) / 1000000;
                        post(mUpdateViewCallback);
                    } catch (final IllegalArgumentException e) {
                    }
                    advanceGif();
                    try {
                        int delay = getNextDelay();
                        // Sleep for frame duration minus time already spent on
                        // frame decode
                        // Actually we need next frame decode duration here,
                        // but I use previous frame time to make code more readable
                        delay -= frameDecodeTime;
                        if (delay > 0) {
                            Thread.sleep(mFramesDisplayDuration > 0 ? mFramesDisplayDuration : delay);
                        }
                    } catch (final Exception e) {
                        // suppress any exception
                        // it can be InterruptedException or
                        // IllegalArgumentException
                    }
                }
            }
        }
    }
}
