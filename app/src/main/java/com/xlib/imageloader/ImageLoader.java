package com.xlib.imageloader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.widget.ImageView;

import com.xlib.imageloader.block.ImageLurCache;
import com.xlib.imageloader.block.LIFOLinkedBlockingDeque;
import com.xlib.imageloader.view.ImageRecyclableView;

import java.io.File;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by GuoQiang.Mo At 17/3/4.
 */

public class ImageLoader {
    private static ImageLoader sInstance;

    Map<String,ImageLoadTask> mImageLoadTaskQeuen;
    List<ImageLoadTask> mIdleImageLoadTask;
    Map<String,Reference<Bitmap>> mBitmapCache;
    Handler mImageLoadHandler;

    private LIFOLinkedBlockingDeque<Runnable> mLifoLinkedBlockingDeque;

    private ExecutorService mImageLoadThreadPool;
    private boolean isAlive;

    final Object mLock = new Object();

    protected ImageLoaderConfiguration mConfig;

    public static ImageLoader getInstance(){
        if(sInstance == null) createInstance();
        return sInstance;
    }

    private ImageLoader(){
        mImageLoadHandler = new ImageloadHandler();
    }

    public void init(){
        getImageLoaderConfiguration();
        isAlive = true;
    }

    public void init(ImageLoaderConfiguration config){
        mConfig = config;
        int minCoreSize = Math.min(Math.max(2,mConfig.minCoreSize),2);
        int maxCoreSize = Math.max(Math.min(50,mConfig.maxCoreSize),minCoreSize);
        mLifoLinkedBlockingDeque = new LIFOLinkedBlockingDeque<Runnable>();
        mImageLoadThreadPool = new ThreadPoolExecutor(minCoreSize,maxCoreSize,1, TimeUnit.SECONDS,mLifoLinkedBlockingDeque);
        mConfig.listener = new DefaultImageLoadingListener();
        isAlive = true;
    }

    public String getCacheDir(){
        return getImageLoaderConfiguration().dir;
    }

    private static synchronized void createInstance(){
        if(sInstance==null) sInstance = new ImageLoader();
    }

    public void destory(){
        isAlive = false;
        try {
            mLifoLinkedBlockingDeque.clear();
            if(!(mImageLoadThreadPool.isTerminated() || mImageLoadThreadPool.isShutdown())){
                mImageLoadThreadPool.shutdown();
            }
            if(mIdleImageLoadTask != null) mImageLoadTaskQeuen.clear();
            if(mImageLoadTaskQeuen!=null) mImageLoadTaskQeuen.clear();
            if(mBitmapCache!=null) mBitmapCache.clear();
        }catch (Exception e){}
    }

    public void loadImage(String url){
        loadImage(url,null);
    }

    public void loadImage(String url,ImageView image){
        loadImage(url,image,getImageLoaderConfiguration().listener);
    }

    public void loadImage(String url,ImageView image,ImageLoadingListener listener){
        loadImage(url,image,listener,null);
    }

    public void loadImage(String url,ImageView image,ImageLoadingListener listener,ImageDecoder decoder){
        loadImage(url,image,listener,decoder,false);
    }

    public void loadLocalImage(String url,ImageView image){
        loadLocalImage(url,image,null);
    }

    public void loadLocalImage(String url,ImageView image,ImageDecoder decoder){
        loadImage(url,image,null,decoder,true);
    }

    void loadImage(String url,ImageView image,ImageLoadingListener listener,ImageDecoder decoder,boolean isDisk){
        if(!isAlive) return;
        try{
            final boolean isImageViewExist = image!=null;
            final boolean isEmpty = TextUtils.isEmpty(url);
            // if image url is empty then release image view resource and return
            if(!isEmpty && url.contains("null/appcategory")){
                url =url.replace("null/appcategory","appcategory");
            }
            if(isEmpty || (url.contains("null") || url.contains("undefined")||url.endsWith("/"))){
                if(isImageViewExist){
                    image.setTag(R.id.key_load_image_url,null);
                    if(image instanceof ImageRecyclableView){
                        ((ImageRecyclableView)image).releaseGif();
                    }
                    image.setImageBitmap(null);
                }
                return;
            }
            // if image is exists and is git then return
            String path = Downloader.createFilePath(url,getCacheDir());
            File file = new File(path);
            boolean fileExists = file.exists();
            if(fileExists && Downloader.isGif(path)){
                if(isImageViewExist && image instanceof ImageRecyclableView){
                    playGifImageView((ImageRecyclableView)image,file);
                }
                if(listener!=null)  listener.onLoadingComplete(url,image,null);
                return;
            }

            Bitmap bm = null;
            if(isImageViewExist){
                // if image view exist then release gif
                if(image instanceof ImageRecyclableView){
                    ((ImageRecyclableView)image).releaseGif();
                }
                //  try load bitmap from cache
                image.setTag(R.id.key_load_image_url,url);
                bm = isDisk ? getBitmapFromMemory(url) : fileExists ? getBitmapFromCache(url,isImageViewExist,decoder) : null;
            }else{
                // image view not exist and image file exist then do nothing and return
                if(fileExists) return;
            }

            // image view exist and bitmap loaded from cache then display it otherwise load bitmap from asncy
            if(isImageViewExist && bm!=null){
                image.setImageBitmap(bm);
                if(listener!=null) listener.onLoadingComplete(url,image,bm);
            }else{
                if(isImageViewExist) image.setImageBitmap(null);

                if(listener==null) listener = getImageLoaderConfiguration().listener;
                ImageLoadTask task = getImageLoadTask(url);
                ImageNode node = getImageNode(task);
                node.setData(url,image,listener,decoder);
                if(!task.isRunning){
                    mLifoLinkedBlockingDeque.remove(task);
                    mImageLoadThreadPool.execute(task);
                }
                synchronized (mLock){
                    final Map<String,ImageLoadTask> map = getImageLoadTasks();
                    map.put(url,task);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    ImageLoadTask getImageLoadTask(String url){
        final Map<String,ImageLoadTask> map = getImageLoadTasks();
        ImageLoadTask task = map.get(url);
        if(task==null){
            final List<ImageLoadTask> list = getIdleImageLoadTasks();
            task = list.isEmpty() ? null : list.get(0);
            if(task!=null){
                synchronized (mLock){
                    list.remove(task);
                }
            }
        }
        if(task==null) task = new ImageLoadTask();
        return task;
    }

    ImageNode getImageNode(ImageLoadTask task){
        ImageNode node = task.mNode;
        if(node==null){
            node = new ImageNode();
            task.setImageNode(node);
        }
        return node;
    }

    Map<String,Reference<Bitmap>> getBitmapCache(){
        if(mBitmapCache==null) mBitmapCache = new ImageLurCache(10,getImageLoaderConfiguration().maxCapacity,0.75f);
        return mBitmapCache;
    }

    Map<String,ImageLoadTask> getImageLoadTasks(){
        if(mImageLoadTaskQeuen==null) mImageLoadTaskQeuen = new HashMap<String, ImageLoadTask>();
        return mImageLoadTaskQeuen;
    }

    List<ImageLoadTask> getIdleImageLoadTasks(){
        if(mIdleImageLoadTask==null) mIdleImageLoadTask = new ArrayList<ImageLoadTask>(30);
        return mIdleImageLoadTask;
    }

    ImageLoaderConfiguration getImageLoaderConfiguration(){
        if(mConfig == null){
            mConfig = new ImageLoaderConfiguration();
            mConfig.dir = Environment.getExternalStorageDirectory() + "/.cached/.image/";
            mConfig.maxCapacity = 100;
            mConfig.maxCoreSize = 8;
            mConfig.minCoreSize = 4;
            mLifoLinkedBlockingDeque = new LIFOLinkedBlockingDeque<Runnable>();
            if(mImageLoadThreadPool!=null && !(mImageLoadThreadPool.isShutdown() || mImageLoadThreadPool.isTerminated())) mImageLoadThreadPool.shutdownNow();
            mImageLoadThreadPool = new ThreadPoolExecutor(mConfig.minCoreSize,mConfig.maxCoreSize,1, TimeUnit.SECONDS,mLifoLinkedBlockingDeque);
            mConfig.listener = new DefaultImageLoadingListener();
        }
        return mConfig;
    }

    void playGifImageView(ImageRecyclableView iv,File file){
        if(iv == null || file==null) return;
        try{
            iv.setImageBitmap(null);
            iv.setGifPath(file);
            iv.startPlayGif();
        }catch (Error e){
            e.printStackTrace();
            System.gc();
        }
    }

    public Bitmap getBitmapFromMemory(String key){
        final Map<String,Reference<Bitmap>> map = getBitmapCache();
        Reference<Bitmap> obj = map.get(key);
        Bitmap bm = obj==null ? null : obj.get();
        if(bm!=null && bm.isRecycled()){
            map.remove(key);
            bm = null;
        }
        return bm;
    }

    public Bitmap getBitmapFromCache(String key){
       return getBitmapFromCache(key,false,null);
    }

    public Bitmap getBitmapFromCache(String key,boolean cache,ImageDecoder decoder){
        final Map<String,Reference<Bitmap>> map = getBitmapCache();
        Reference<Bitmap> obj = map.get(key);
        Bitmap bm = obj==null ? getBitmapFromDisk(key,cache,decoder) : obj.get();
        if(bm!=null && bm.isRecycled()){
            map.remove(key);
            bm = null;
        }
        return bm;
    }

    public void releaseBitmapByKey(String key){
        Bitmap bm = getBitmapFromMemory(key);
        if(bm==null || bm.isRecycled()) return;
        bm.recycle();
        mBitmapCache.remove(key);
    }

    protected void saveBitmapToCache(String key,Bitmap bm){
        final Map<String,Reference<Bitmap>> map = getBitmapCache();
        Reference<Bitmap> obj = map.get(key);
        if(obj==null) map.put(key,new WeakReference<Bitmap>(bm));
    }

    private Bitmap getBitmapFromDisk(String url,boolean cache,ImageDecoder decoder){
        String path = Downloader.createFilePath(url,getCacheDir());
        File f = new File(path);
        Bitmap bm = null;
        if(f.exists() && !Downloader.isGif(path)){
            bm = decoder==null ? decodeBitmap(path,null) : decoder.decode(path);
            if(cache && bm!=null){
                saveBitmapToCache(url,bm);
            }
        }
        return bm;
    }

    public Bitmap decodeBitmap (String imagePath, BitmapFactory.Options options) {
        //读取图像文件
        if (options == null) {
            options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, options);
            int width = options.outWidth;
            int height = options.outHeight;
            int inSampleSize = 1;
            // 需要考虑优化
            while (width > 1080 || height > 1920) {
                inSampleSize *= 2;
                width /= 2;
                height /= 2;
            }
            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;
        }
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inPurgeable = true;
        options.inInputShareable = true;
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
            return bitmap;
        } catch (Exception e) {
            System.gc();
        } catch (OutOfMemoryError error) {
            System.gc();
            error.printStackTrace();
        }
        return null;
    }

    public interface ImageDecoder{
        Bitmap decode(String filePath);
    }

    public interface ImageLoadingListener{
        void onLoadingStarted(String url, ImageView view);
        void onLoadingComplete(String url, ImageView view, Bitmap bitmap);
        void onLoadingFailed(String url, ImageView view);
    }

    class ImageNode {
        String url;
        List<ImageView> images = new ArrayList<ImageView>(1);
        ImageLoadingListener listener;
        ImageDecoder decoder;
        Bitmap bitmap;
        int loadCount;

        void setData(String url, ImageView image, ImageLoadingListener listener, ImageDecoder decoder){
            this.url = url;
            this.listener = listener;
            this.decoder = decoder;
            addImageView(image);
        }

        void addImageView(ImageView iv){
            if(!isDecodeable()) images.clear();
            if(!images.contains(iv)) images.add(iv);
        }

        boolean isDecodeable(){
            boolean enable = false;
            if(images.isEmpty()) return  enable;
            for(ImageView iv : images){
                if(iv==null) continue;
                if(url.equals(iv.getTag(R.id.key_load_image_url))){
                    enable = true;
                    break;
                }
            }
            return enable;
        }
    }

    class ImageLoadTask implements Runnable{
        ImageNode mNode;
        volatile boolean isRunning = false;

        void setImageNode(ImageNode node){
            mNode = node;
        }

        @Override
        public void run() {
            isRunning = true;
            try{
                final ImageNode node = mNode;
                node.loadCount ++;
                // send start loading bitmap message
                Message msg = mImageLoadHandler.obtainMessage(ImageloadHandler.LOAD_STATUE_START);
                msg.obj = node;
                mImageLoadHandler.sendMessage(msg);
                // start downloading bitmap

                // check this bitmap is cached/loaded or not
                Bitmap bitmap = node.isDecodeable() ? getBitmapFromCache(node.url,true,node.decoder) : null;

                boolean isLoad = true;
                boolean isGif = false;
                // if bitmap is not cached/loaded then download it from network
                if(bitmap==null){
                    final String filePath = Downloader.createFilePath(node.url,getCacheDir());
                    // download file from network
                    isLoad = Downloader.downloadFile(node.url,filePath); // try download file once
                    if(!isLoad){
                        // if download file failed then sleep 10 ms and try once again
                        Thread.sleep(10);
                        isLoad = Downloader.downloadFile(node.url,filePath);
                    }

                    isGif = Downloader.isGif(filePath);
                    // download succeed and has image with it and not gif image then decode bitmap
                    if(isLoad && node.isDecodeable() && !isGif){
                        // download succeed file then decode it to bitmap
                        bitmap = node.decoder==null ? decodeBitmap(filePath,null) : node.decoder.decode(filePath);
                    }
                }

                // load bitmap finished then notify caller load result
                node.bitmap = bitmap;
                if(isLoad){
                    if(bitmap!=null) saveBitmapToCache(node.url,bitmap);
                    msg = mImageLoadHandler.obtainMessage(ImageloadHandler.LOAD_STATUE_COMPLETED);
                }else{
                    node.loadCount++;
                    msg = mImageLoadHandler.obtainMessage(ImageloadHandler.LOAD_STATUE_FAILED);
                }
                msg.obj = node;
                mImageLoadHandler.sendMessage(msg);

                if(node.loadCount >= 2 || isLoad){
                    synchronized(mLock){
                        node.loadCount = 0;
                        final Map<String,ImageLoadTask> map = getImageLoadTasks();
                        ImageLoadTask task = map.remove(node.url);
                        final List<ImageLoadTask> list = getIdleImageLoadTasks();
                        if(task!=null && !list.contains(task)) list.add(task);
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
            }
            isRunning = false;
        }
    }

    class ImageloadHandler extends Handler{
        final static int LOAD_STATUE_START = -1;
        final static int LOAD_STATUE_COMPLETED = 0;
        final static int LOAD_STATUE_FAILED = 1;
        Bitmap mTempBitmap;

        @Override
        public void handleMessage(Message msg) {
            Object obj = msg.obj;
            if(obj==null || !(obj instanceof ImageNode)) return;
            ImageNode node = (ImageNode)obj;
            Object tag;
            final String url = node.url;
            for (ImageView iv : node.images) {
                if(iv==null) continue;

                tag = iv.getTag(R.id.key_load_image_url);
                if(!url.equals(tag)) continue;

                switch (msg.what){
                    case LOAD_STATUE_START:
                        node.listener.onLoadingStarted(node.url, iv);
                        break;
                    case LOAD_STATUE_COMPLETED:
                        Bitmap bm = node.bitmap;
                        if (bm != null && !bm.isRecycled()) {
                            iv.setImageBitmap(bm);
                        }else{
                            if(mTempBitmap==null || mTempBitmap.isRecycled()){
                                mTempBitmap = Bitmap.createBitmap(10,10, Bitmap.Config.RGB_565);
                            }
                            bm = mTempBitmap;
                            String path = Downloader.createFilePath(url,getCacheDir());
                            File file = new File(path);
                            if(file.exists() && Downloader.isGif(path) && iv instanceof ImageRecyclableView){
                                playGifImageView((ImageRecyclableView)iv,file);
                            }
                        }
                        node.listener.onLoadingComplete(node.url, iv, bm);
                        break;
                    case LOAD_STATUE_FAILED:
                        if(node.loadCount < 2){
                            loadImage(node.url, iv, node.listener);
                        }else{
                            node.listener.onLoadingFailed(node.url, iv);
                        }
                        break;
                    default:break;
                }
            }
        }
    }

    class DefaultImageLoadingListener implements ImageLoadingListener{
        @Override
        public void onLoadingStarted(String url, ImageView view) { }

        @Override
        public void onLoadingComplete(String url, ImageView view, Bitmap bitmap) { }

        @Override
        public void onLoadingFailed(String url, ImageView view) { }
    }

    public static class Builder{
        ImageLoaderConfiguration config = new ImageLoaderConfiguration();

        public ImageLoaderConfiguration create(){
            return config;
        }

        public Builder setCacheDir(String dir){
            config.dir = dir;
            return this;
        }

        public Builder setMinCoreSize(int size){
            config.minCoreSize = size;
            return this;
        }

        public Builder setMaxCoreSize(int size){
            config.maxCoreSize = size;
            return this;
        }

        public Builder setMaxCacheCapacity(int capacity){
            config.maxCapacity = capacity;
            return this;
        }
    }

    public static class ImageLoaderConfiguration{
        String dir;
        int minCoreSize;
        int maxCoreSize;
        int maxCapacity;
        ImageLoadingListener listener;
    }
}
