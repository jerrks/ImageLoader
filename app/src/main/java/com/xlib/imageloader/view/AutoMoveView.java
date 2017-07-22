package com.xlib.imageloader.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.Scroller;

public class AutoMoveView extends FrameLayout {

	final static String VIEW_SLEF_CONFIG = "sp_moveable_view_config";
	
	boolean mEnableMove;
	int mBroundLeft,mBroundTop;
	int mBroundBottom,mBroundRight;
	
	int mTouchX = Integer.MIN_VALUE;
	int mTouchY = Integer.MIN_VALUE;
	
	int mTouchSlop;
	long mTouchTime;
	
	AutoMoveRunnable mAutoMoveRunnable;
	Scroller mScroller;
	
	public AutoMoveView(Context context) {
		this(context, null, -1);
	}
	
	public AutoMoveView(Context context, AttributeSet attrs) {
		this(context, attrs, -1);
	}
	
	public AutoMoveView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		int r = getResources().getDisplayMetrics().widthPixels;
		int b = getResources().getDisplayMetrics().heightPixels - 45;
		setMoveBound(0, 0, r, b);
		postDelayed(new Runnable() {
			@Override
			public void run() {
				setDefualtPosition(Integer.MIN_VALUE, Integer.MIN_VALUE);
			}
		}, 50);
		mScroller = new Scroller(context, new AccelerateInterpolator(1.5f));
		mAutoMoveRunnable = new AutoMoveRunnable();
		mTouchSlop = ViewConfiguration.getTouchSlop();
	}
	
	public void setMoveBound(int l,int t,int r,int b){
		mBroundLeft = l>0?l:mBroundLeft;
		mBroundTop = t>0?t:mBroundTop;
		mBroundRight = r>0?r:mBroundRight;
		mBroundBottom = b>0?b:mBroundBottom;
	}
	
	public void setDefualtPosition(int dx,int dy){
		SharedPreferences sp = getContext().getSharedPreferences(VIEW_SLEF_CONFIG, Context.MODE_MULTI_PROCESS);
		String key = getConfigKey();
		String value = sp.getString(key, "");
		int x=Integer.MIN_VALUE;
		int y=Integer.MIN_VALUE;
		if(!TextUtils.isEmpty(value)){
			String[] a = value.split(",");
			if(a!=null && a.length>1){
				x = Integer.valueOf(a[0]);
				y = Integer.valueOf(a[1]);
			}
		}
		boolean readValid = (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE);
		boolean setValid = (dx != Integer.MIN_VALUE && dy != Integer.MIN_VALUE);
		if(readValid || setValid){
			MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
			if(readValid){
				lp.leftMargin = x;
				lp.topMargin = y;
			}else{
				lp.leftMargin = dx;
				lp.topMargin = dy;
				value = dx +"," + dy;
				SharedPreferences.Editor editor = sp.edit();
				editor.putString(key, value);
				editor.commit();
			}
			setLayoutParams(lp);
		}
	}
	
	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if(!mScroller.isFinished()) return true;
		
		switch (ev.getAction()) {
		case MotionEvent.ACTION_DOWN:
			mEnableMove = false;
			mTouchX = (int)ev.getRawX();
			mTouchY = (int)ev.getRawY();
			mTouchTime = System.currentTimeMillis();
			break;
		case MotionEvent.ACTION_MOVE:
			int x = (int)ev.getRawX();
			int y = (int)ev.getRawY();
			if(!(mTouchX == Integer.MIN_VALUE || mTouchY == Integer.MIN_VALUE)){
				if(!mEnableMove){
					mEnableMove = Math.abs(x-mTouchX)>=mTouchSlop || Math.abs(y-mTouchY)>=mTouchSlop;
				}
				if(mEnableMove){
					onMoveView(x - mTouchX, y - mTouchY);
				}
			}
			mTouchX = x;
			mTouchY = y;
			break;
		case MotionEvent.ACTION_UP:
			if(mEnableMove){
				ev.setAction(MotionEvent.ACTION_CANCEL);
			}
			mTouchX = Integer.MIN_VALUE;
			mTouchY = Integer.MIN_VALUE;
			onAutoScrollBound();
			mEnableMove = false;
			break;
		default:
			break;
		}
		super.dispatchTouchEvent(ev);
		return true;
	}
	
	private String getConfigKey(){
		int id = getId();
		ViewParent vp = getParent();
		String key = null;
		if(vp == null){
			key = String.valueOf(id);
		}else{
			int vpid = ((ViewGroup)vp).getId();
			key = String.valueOf(vpid) + "_" + String.valueOf(id);
		}
		return key;
	}
	
	private void onAutoScrollBound(){
		MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
		int t = lp.topMargin;
		int dr = mBroundRight - getRight();
		int dl = getLeft() - mBroundLeft;
		int duration = 300;
		mScroller.forceFinished(true);
		if(dl>dr){ // move to right
			mScroller.startScroll(lp.leftMargin, t, dr, 0, duration);
		}else{ // move to left
			mScroller.startScroll(lp.leftMargin, t, -dl, 0, duration);
		}
		mAutoMoveRunnable.run();
	}
	
	private void onMoveView(int dx,int dy){
		int l = getLeft();
		if(dx + l < mBroundLeft) dx = mBroundLeft-l; // out of left bound
		int temp = getWidth();
		if(dx + l + temp > mBroundRight) dx = mBroundRight - temp - l; // out of right bound
		
		int t = getTop();
		if(dy + t < mBroundTop) dy = mBroundTop-t; // out of top bound
		temp = getHeight();
		if(dy + t + temp > mBroundBottom) dy = mBroundBottom - temp - t; // out of bottom bound
		
		MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
		lp.leftMargin = lp.leftMargin + dx;
		lp.topMargin = lp.topMargin + dy;
		setLayoutParams(lp);
	}

	class AutoMoveRunnable implements Runnable{
		@Override
		public void run() {
			removeCallbacks(this);
			MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
			if(mScroller.computeScrollOffset()){
				lp.leftMargin = mScroller.getCurrX();
				lp.topMargin = mScroller.getCurrY();
				postDelayed(this, 30);
				setLayoutParams(lp);
			}else{
				lp.leftMargin = mScroller.getFinalX();
				lp.topMargin = mScroller.getFinalY();
				setLayoutParams(lp);
				String key = getConfigKey();
				String value = lp.leftMargin +"," + lp.topMargin;
				SharedPreferences sp = getContext().getSharedPreferences(VIEW_SLEF_CONFIG, Context.MODE_APPEND);
				SharedPreferences.Editor editor = sp.edit();
				editor.putString(key, value);
				editor.commit();
			}
		}
	}
}
