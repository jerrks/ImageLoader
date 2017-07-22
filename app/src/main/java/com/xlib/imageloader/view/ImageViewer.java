package com.xlib.imageloader.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.xlib.imageloader.util.ViewConfiguration;

public class ImageViewer extends ImageRecyclableView {

	private static final int DURATION	= 200;
    private float mInitialScaleFactor = 1.0f;
    private float mOverlayScaleFactor;
	private int mPivotX, mPivotY;
	private boolean mLayouted;
    // Temporary buffer used for getting the values out of a matrix.
    private final float[] mMatrixValues = new float[9];
	
    private final Matrix mSuppMatrix = new Matrix();
    private final Matrix mDisplayMatrix = new Matrix();
	private Bitmap mBitmap;
	private AdjustRunnable mAdjustRunnable;

    public ImageViewer(Context context) {
        super(context);
        setScaleType(ScaleType.MATRIX);
        initGesture(context);
    }
    
    public ImageViewer(Context context, AttributeSet attrs) {
    	super(context, attrs);
        setScaleType(ScaleType.MATRIX);
        initGesture(context);
    }
    
    public void setImageBitmap (Bitmap bitmap) {
        if (mBitmap != null && mBitmap == bitmap && !mBitmap.isRecycled()) {
            return;
        }
    	super.setImageBitmap(bitmap);
    	if (mBitmap != null && bitmap != null) {
    		if (mAdjustRunnable != null) {
    			removeCallbacks(mAdjustRunnable);
    		}
    		final float newScale = mInitialScaleFactor * mBitmap.getWidth() / bitmap.getWidth();
        	mSuppMatrix.getValues(mMatrixValues);
            float transx = mMatrixValues[Matrix.MTRANS_X];
            float transy = mMatrixValues[Matrix.MTRANS_Y];
            float scalex = mMatrixValues[Matrix.MSCALE_X];
            final int centerx = (int)(mBitmap.getWidth() * scalex / 2 + transx);
            final int centery = (int)(mBitmap.getHeight() * scalex / 2 + transy);
            Matrix matrix = mSuppMatrix;
            matrix.setTranslate(centerx - (bitmap.getWidth() >> 1), centery - (bitmap.getHeight() >> 1));
        	matrix.postScale(newScale, newScale, centerx, centery);
        	setImageMatrix(getImageViewMatrix());
    		mInitialScaleFactor = newScale;
    	}
    	mBitmap = bitmap;
    }
    
    public void setOverlayScale (float scale) {
    	mOverlayScaleFactor = scale;
    }
    
    @Override
    public void onVisibilityChanged(View changedView, int visibility) {
    	if (changedView == this && visibility == VISIBLE) {
    		mLayouted = false;
    	}
    }
    
    @Override
    public void onLayout (boolean changed, int left, int top, int right, int bottom) {
    	final Bitmap bitmap = mBitmap;
    	super.onLayout(changed, left, top, right, bottom);
    	if (bitmap == null) {
    	    return;
    	}
    	if (mLayouted) return;
    	int w = right - left;
    	int h = bottom - top;
    	float firstScale = (float)w / bitmap.getWidth();
    	if (bitmap.getHeight() * firstScale > h) {
    		firstScale = (float)h / bitmap.getHeight(); 
    	}
    	final float scale;
    	if (mOverlayScaleFactor > 0) {
    		scale = mInitialScaleFactor = firstScale * mOverlayScaleFactor;
    	} else {
    		scale = mInitialScaleFactor = firstScale;
    	}
    	final int offsetx = (w - bitmap.getWidth()) >> 1;
    	final int offsety = (h - bitmap.getHeight()) >> 1;
    	final int centerx = mPivotX = w >> 1;
    	final int centery = mPivotY = h >> 1;
    	Matrix matrix = mSuppMatrix;
    	matrix.reset();
    	matrix.setTranslate(offsetx, offsety);
    	matrix.postScale(firstScale, firstScale, centerx, centery);
    	setImageMatrix(getImageViewMatrix());

		if (mAdjustRunnable != null) {
			removeCallbacks(mAdjustRunnable);
		}
		mAdjustRunnable = new AdjustRunnable(centerx, centery, offsetx, offsetx, offsety, offsety, firstScale, scale, System.currentTimeMillis(), DURATION);
		post(mAdjustRunnable);
    	mLayouted = true;
    }
    
    @Override
    public void onDraw (Canvas canvas) {
        try {
            super.onDraw(canvas);
        } catch (Exception e) {
            e.printStackTrace();
            super.setImageDrawable(null);
        }
    }

    
    private Matrix getImageViewMatrix () {
    	mDisplayMatrix.set(mSuppMatrix);
    	return mDisplayMatrix;
    }
    private static final int MAYBE_TAP = 1;
    private static final int TAP = 2;
    private static final int MAYBE_DOUBLE_TAP = 3;
    private static final int DOUBLE_TAP = 4;
    private final Handler mHandler = new Handler () {
        @Override
        public void handleMessage (Message msg) {
            switch (msg.what) {
            case TAP:{
                performClick();
            }break;
            case DOUBLE_TAP:{
                
            }break;
            }
        }
    };
	
	private int mActivePointerId;
	private int mFollowPointerId;
	private float mDownX, mDownY;
	private float mPointerMotionX0, mPointerMotionY0;
	private float mPointerMotionX1, mPointerMotionY1;
    private int mTouchSlopSquare;
    private int mDoubleTapSlopSquare;
    private MotionEvent mCurrentDownEvent;
    private MotionEvent mPreviousUpEvent;
    
    private void initGesture (Context context) {
        ViewConfiguration config = ViewConfiguration.get(getContext());
        mTouchSlopSquare = config.getTouchSlopSquare();
        mDoubleTapSlopSquare = config.getDoubleTapSlopSquare();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mBitmap == null) return super.onTouchEvent(ev);
        final float y = ev.getY();
        final float x = ev.getX();
        final int action = ev.getAction() & MotionEvent.ACTION_MASK;

        switch (action) {
        case MotionEvent.ACTION_DOWN:{
            if (mAdjustRunnable != null) {
                removeCallbacks(mAdjustRunnable);
            }
            mActivePointerId = ev.getPointerId(0);
            mDownX = mPointerMotionX0 = x;
            mDownY = mPointerMotionY0 = y;

            boolean hadTapMessage = mHandler.hasMessages(TAP);
            if (hadTapMessage) mHandler.removeMessages(TAP);
            if ((mCurrentDownEvent != null) && (mPreviousUpEvent != null)
                    && isConsideredDoubleTap(mCurrentDownEvent, mPreviousUpEvent, ev)) {
                mHandler.sendEmptyMessageDelayed(MAYBE_DOUBLE_TAP, ViewConfiguration.getTapTimeout());
            } else {
                // This is a first tap
                mHandler.sendEmptyMessageDelayed(MAYBE_TAP, ViewConfiguration.getTapTimeout());
            }
            if (mCurrentDownEvent != null) {
                mCurrentDownEvent.recycle();
            }
            mCurrentDownEvent = MotionEvent.obtain(ev);
        }return true;
        case MotionEvent.ACTION_POINTER_DOWN:{	//	id为按下时当时的索引值；
            getParent().requestDisallowInterceptTouchEvent(true);
        	if (ev.getPointerCount() != 2) break;
			final int pointIndex = ev.getActionIndex();
			mFollowPointerId = ev.getPointerId(pointIndex);
			mPointerMotionX1 = ev.getX(pointIndex);
            mPointerMotionY1 = ev.getY(pointIndex);
        }break;
        case MotionEvent.ACTION_MOVE:{
        	final int pointerCount = ev.getPointerCount();
        	if (pointerCount < 2) {
                mSuppMatrix.getValues(mMatrixValues);
                final float transx = mMatrixValues[Matrix.MTRANS_X];
                final float scalex = mMatrixValues[Matrix.MSCALE_X];
                final int left = (int)transx;
                final int right = (int)(transx + mBitmap.getWidth() * scalex);
                if ((left < 0 && (x > mPointerMotionX0 || right > getWidth()))
                        || ((right > getWidth() && (x < mPointerMotionX0 || left < 0)))) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
        	    if (left >= 0 && right <= getWidth()) {
                    return false;
                }
        		final int deltaX = (int) (x - mDownX);
        		final int deltaY = (int) (y - mDownY);
        		final int square = deltaX * deltaX + deltaY * deltaY;
            	int offx = (int)(x - mPointerMotionX0);
            	int offy = (int)(y - mPointerMotionY0);

            	Matrix matrix = mSuppMatrix;
            	matrix.postTranslate(offx, offy);
            	setImageMatrix(getImageViewMatrix());
            	
            	mPointerMotionX0 = x;
            	mPointerMotionY0 = y;
        		if (square > mTouchSlopSquare) {
        		    mHandler.removeMessages(MAYBE_TAP);
                    mHandler.removeMessages(MAYBE_DOUBLE_TAP);
            		ev.setAction(MotionEvent.ACTION_CANCEL);
                	super.onTouchEvent(ev);
        		}
            	return true;
        	} else {
        		ev.setAction(MotionEvent.ACTION_CANCEL);
            	super.onTouchEvent(ev);
        	}
        	
            final int pointerIndex0 = ev.findPointerIndex(mActivePointerId);
            if (pointerIndex0 < 0) break;
            final int pointerX0 = (int) ev.getX(pointerIndex0);
            final int pointerY0 = (int) ev.getY(pointerIndex0);
            final int pointerIndex1 = ev.findPointerIndex(mFollowPointerId);
            if (pointerIndex1 < 0) break;
            final int pointerX1 = (int) ev.getX(pointerIndex1);
            final int pointerY1 = (int) ev.getY(pointerIndex1);
            
        	final float prevDiffX = mPointerMotionX0 - mPointerMotionX1;
        	final float prevDiffY = mPointerMotionY0 - mPointerMotionY1;
        	final float currDiffX = pointerX0 - pointerX1;
        	final float currDiffY = pointerY0 - pointerY1;
        	final float prevLen = (prevDiffX * prevDiffX) + (prevDiffY * prevDiffY);
        	final float currLen = (currDiffX * currDiffX) + (currDiffY * currDiffY);
        	final float scale = currLen / prevLen;
        	Matrix matrix = mSuppMatrix;
        	matrix.postScale(scale, scale, mPivotX, mPivotY);
        	setImageMatrix(getImageViewMatrix());
        	
            mPointerMotionX0 = pointerX0;
            mPointerMotionY0 = pointerY0;
            mPointerMotionX1 = pointerX1;
            mPointerMotionY1 = pointerY1;
        }break;
        case MotionEvent.ACTION_POINTER_UP:{	//	POINTER_UP事件后，如果后续没有新点击，index会前移；Count此时不变
        	final int pointerCount = ev.getPointerCount();
        	int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> 8;
	        final int pointerId = ev.getPointerId(pointerIndex);
            if (pointerId == mActivePointerId) {
            	if (pointerCount == 2) {
            		pointerIndex = (pointerIndex + 1) % pointerCount;
            		mActivePointerId = ev.getPointerId(pointerIndex);
                    final int pointerX = (int) ev.getX(pointerIndex);
                    final int pointerY = (int) ev.getY(pointerIndex);
            		mPointerMotionX0 = pointerX;
                    mPointerMotionY0 = pointerY;
            	} else {
            		for (int i=0;i<pointerCount;i++) {
            			if (pointerIndex == i) continue;
            			final int newPointerId = ev.getPointerId(i);
            			if (newPointerId == mFollowPointerId) continue;
            			
            			mActivePointerId = ev.getPointerId(i);
                        final int pointerX = (int) ev.getX(i);
                        final int pointerY = (int) ev.getY(i);
                		mPointerMotionX0 = pointerX;
                        mPointerMotionY0 = pointerY;
            			break;
            		}
            	}
            } else if (pointerId == mFollowPointerId) {
	        	if (pointerCount > 2) {
            		for (int i=0;i<pointerCount;i++) {
            			if (pointerIndex == i) continue;
            			final int newPointerId = ev.getPointerId(i);
            			if (newPointerId == mActivePointerId) continue;
            			
            			mFollowPointerId = ev.getPointerId(i);
                        final int pointerX = (int) ev.getX(i);
                        final int pointerY = (int) ev.getY(i);
                		mPointerMotionX1 = pointerX;
                        mPointerMotionY1 = pointerY;
            			break;
            		}
	        	}
	        }
        }break;
        case MotionEvent.ACTION_CANCEL:{
            adjustIn(mPivotX, mPivotY);
            super.onTouchEvent(ev);
        }break;
        case MotionEvent.ACTION_UP:{
            boolean hadDoubleTapMessage = mHandler.hasMessages(MAYBE_DOUBLE_TAP);
            if (hadDoubleTapMessage) {
                mHandler.removeMessages(MAYBE_DOUBLE_TAP);
                mSuppMatrix.getValues(mMatrixValues);
                final float transx = mMatrixValues[Matrix.MTRANS_X];
                final float transy = mMatrixValues[Matrix.MTRANS_Y];
                final float scalex = mMatrixValues[Matrix.MSCALE_X];
                final int left = (int)transx;
                final int top = (int)transy;
                final int right = (int)(transx + mBitmap.getWidth() * scalex);
                final int bottom = (int)(transy + mBitmap.getHeight() * scalex);
                float adjustScale = scalex;
                if (adjustScale < mInitialScaleFactor || adjustScale >= mInitialScaleFactor*2) {
                    adjustScale = mInitialScaleFactor;
                } else {
                    adjustScale = mInitialScaleFactor * 2;
                }
                if (mAdjustRunnable != null) {
                    removeCallbacks(mAdjustRunnable);
                }
                mAdjustRunnable = new AdjustRunnable(((left + right)/2), ((top + bottom)/2), left, left, top, top, scalex, adjustScale, System.currentTimeMillis(), DURATION);
                post(mAdjustRunnable);
            }
            boolean hadTapMessage = mHandler.hasMessages(MAYBE_TAP);
            if (hadTapMessage) {
                mHandler.removeMessages(MAYBE_TAP);
                final int deltaX = (int) (x - mDownX);
                final int deltaY = (int) (y - mDownY);
                final int square = deltaX * deltaX + deltaY * deltaY;
                if (square <= mTouchSlopSquare) {
                    mHandler.sendEmptyMessageDelayed(TAP, ViewConfiguration.getDoubleTapTimeout() - (ev.getEventTime() - mCurrentDownEvent.getEventTime()));
                }
            }
        	adjustIn(mPivotX, mPivotY);

            if (mPreviousUpEvent != null) {
                mPreviousUpEvent.recycle();
            }
            // Hold the event we obtained above - listeners may have changed the original.
            mPreviousUpEvent = MotionEvent.obtain(ev);
        }return true;
        default:{
        	super.onTouchEvent(ev);
        }break;
        }
		return false;
		
	}
    
    private boolean isConsideredDoubleTap(MotionEvent firstDown, MotionEvent firstUp,
            MotionEvent secondDown) {
        final long deltaTime = secondDown.getEventTime() - firstUp.getEventTime();
        if (deltaTime > ViewConfiguration.getDoubleTapTimeout()
                || deltaTime < ViewConfiguration.getDoubleTapMinTime()) {
            return false;
        }

        int deltaX = (int) firstDown.getX() - (int) secondDown.getX();
        int deltaY = (int) firstDown.getY() - (int) secondDown.getY();
        return (deltaX * deltaX + deltaY * deltaY < mDoubleTapSlopSquare);
    }

    protected void adjustIn(final float centerX, final float centerY) {
    	if (mBitmap == null || mBitmap.isRecycled()) return;
    	mSuppMatrix.getValues(mMatrixValues);
        final float transx = mMatrixValues[Matrix.MTRANS_X];
        final float transy = mMatrixValues[Matrix.MTRANS_Y];
        final float scalex = mMatrixValues[Matrix.MSCALE_X];
        float scale = scalex;
    	final float left = transx;
    	final float top = transy;
    	final float right = (transx + mBitmap.getWidth() * scale);
    	final float bottom = (transy + mBitmap.getHeight() * scale);
    	int adjustX = (int)left, adjustY = (int)top;
    	float adjustScale = scale;
    	if (adjustScale < mInitialScaleFactor) {
    		adjustScale = mInitialScaleFactor;
    	} else {
    	    final int width = (int)(right - left);
    		if (width >= getWidth()) {
            	if (left > 0) adjustX = 0;
            	else if (right <= getWidth()) adjustX = (int)(getWidth() - right + left);
    		} else {
    		    adjustX = (getWidth() - width) / 2;
    		}
    		final int height = (int)(bottom - top);
    		if (height >= getHeight()) {
            	if (top > 0) adjustY = 0;
            	else if (bottom <= getHeight()) adjustY = (int)(getHeight() - bottom + top);
    		} else {
    		    adjustY = (getHeight() - height) / 2;
    		}
    	}

        final long startTime = System.currentTimeMillis();
        
        if (mAdjustRunnable != null) {
            if (!mAdjustRunnable.isFinished()) return;
			removeCallbacks(mAdjustRunnable);
		}
		mAdjustRunnable = new AdjustRunnable((int)((left + right)/2), (int)((top + bottom)/2), (int)left, adjustX, (int)top, adjustY, scale, adjustScale, startTime, DURATION);
		post(mAdjustRunnable);
    }
    
    class AdjustRunnable implements Runnable {
    	
    	private float mCenterX, mCenterY;
    	private int mFromX, mFromY, mToX, mToY;
    	private float mDeltaX, mDeltaY;
    	private float mFromScale, mToScale;
    	private float mDeltaScale;
    	private long mStartTime;
    	private int mDuration;
    	
    	public AdjustRunnable (float centerX, float centerY, int fromX, int toX, int fromY, int toY, float fromScale, float toScale, long startTime, int durationMs) {
    		mCenterX = centerX;
    		mCenterY = centerY;
    		mFromX = fromX;
    		mToX = toX;
    		mFromY = fromY;
    		mToY = toY;
    		mFromScale = fromScale;
    		mToScale = toScale;
    		mStartTime = startTime;
    		mDuration = durationMs;
    		
    		mDeltaX = (float)(mToX - mFromX) / mDuration;
    		mDeltaY = (float)(mToY - mFromY) / mDuration;
    		mDeltaScale = (float)(mToScale - mFromScale) / mDuration;
    	}
    	
    	public boolean isFinished () {
    	    return System.currentTimeMillis() >= mStartTime + mDuration;
    	}
    	
        public void run() {
        	mSuppMatrix.getValues(mMatrixValues);
            float transx = mMatrixValues[Matrix.MTRANS_X];
            float transy = mMatrixValues[Matrix.MTRANS_Y];
            float scalex = mMatrixValues[Matrix.MSCALE_X];
            float left = transx;
            float top = transy;
            long now = System.currentTimeMillis();
            long currentMs = Math.min(mDuration, now - mStartTime);
            Matrix matrix = mSuppMatrix;
            if (mDeltaScale != 0) {
                final float scale = (mFromScale + mDeltaScale * currentMs) / scalex;
            	matrix.postScale(scale, scale, mCenterX, mCenterY);
            }
            if (mDeltaX != 0 || mDeltaY != 0) {
                final int x = mFromX + (int)(mDeltaX * currentMs - left);
                final int y = mFromY + (int)(mDeltaY * currentMs - top);
                matrix.postTranslate(x, y);
            }
        	setImageMatrix(getImageViewMatrix());

        	if (currentMs < mDuration) {
                post(this);
            } else {
                adjustIn(mPivotX, mPivotY);
            }
        }
    }
}
