package com.xlib.imageloader.block;

import android.graphics.Bitmap;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;

/**
 * Created by GuoQiang.Mo At 17/3/7.
 */

public class ImageLurCache extends LinkedHashMap<String, Reference<Bitmap>> {

    private static final int LUR_CACHE_CAPACITY = 3;
    int mCapacity;

    public ImageLurCache(int initCapacity, float factor){
        this(initCapacity,LUR_CACHE_CAPACITY,factor);
    }

    public ImageLurCache(int initCapacity, int capacity, float factor){
        super(initCapacity,factor,true);
        mCapacity = Math.max(LUR_CACHE_CAPACITY,capacity);
    }

    @Override
    protected boolean removeEldestEntry(Entry<String, Reference<Bitmap>> eldest) {
        int size = size();
        boolean remove = mCapacity<size;
        if(remove && eldest!=null){
            Reference<Bitmap> value = eldest.getValue();
            Bitmap bm = value==null ? null : value.get();
            if(bm!=null && !bm.isRecycled()) bm.recycle();
        }
        return remove;
    }
}
