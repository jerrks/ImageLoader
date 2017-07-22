package com.xlib.imageloader.view.gif;

import android.graphics.Bitmap;

final class SimpleBitmapProvider implements GifDecoder.BitmapProvider {
    @Override
    public Bitmap obtain(int width, int height, Bitmap.Config config) {

        Bitmap bitmap = null;
        try {
            // 实例化Bitmap
            bitmap = Bitmap.createBitmap(width, height, config);
        } catch (Error e) {
            //
        }
        return bitmap;
    }

    @Override
    public void release(Bitmap bitmap) {
    // 先判断是否已经回收
        if(bitmap != null && !bitmap.isRecycled()){
            // 回收并且置为null
            bitmap.recycle();
            bitmap = null;
        }
    }

    @Override
    public byte[] obtainByteArray(int size) {
        return new byte[size];
    }

    @Override
    public void release(byte[] bytes) {
        bytes = null;
    }

    @Override
    public int[] obtainIntArray(int size) {
        return new int[size];
    }

    @Override
    public void release(int[] array) {
        array = null;
    }
}
