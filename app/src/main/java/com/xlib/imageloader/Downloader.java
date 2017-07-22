package com.xlib.imageloader;

import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Created by GuoQiang.Mo At 17/3/4.
 */

public class Downloader {

    public static String createFilePath(String url){
        String dir = Environment.getExternalStorageDirectory() + "/.cached/.image/";
        return createFilePath(url,dir);
    }

    public static String createTempFilePath(String key){
        String dir = Environment.getExternalStorageDirectory() + "/.cached/.image/";
        File file = new File(dir);
        if (!file.exists()) file.mkdirs();
        return dir + Uri.encode(key);
    }

    public static String createFilePath(String url,String dir){
        if(isFile(url)) return url;
        File file = new File(dir);
        if (!file.exists()) file.mkdirs();
        return dir + Uri.encode(url);
    }

    public static void cleanCacheFile(String dir,FileFilter filter) {
        File file = new File(dir);
        if (file.exists()) {
            File[] files = file.listFiles(filter);
            if(files==null) return;
            for (File f : files) {
                f.delete();
            }
        }
    }

    public static boolean isFile(String uri){
        if(TextUtils.isEmpty(uri)) return false;
        String host = Uri.parse(uri).getHost();
        return TextUtils.isEmpty(host) || host.equalsIgnoreCase("file");
    }

    public static boolean isGif(String file){
        if(file==null) return false;
        int dot = file.lastIndexOf(".");
        if (dot < 0) return false;
        String ext = file.substring(dot + 1).toLowerCase();
        if("gif".equals(ext)) return true;

        File f;
        FileInputStream fis = null;
        try {
            f = new File(file);
            if(!f.exists()) return false;
            fis = new FileInputStream(f);
            byte[] data = new byte[4];
            int read = fis.read(data);
            if (read != 4) return false;
            // GIF
            return data[0] == (byte) 0x47 && data[1] == (byte) 0x49 && data[2] == (byte) 0x46;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (Exception e) {}
        }
        return false;
    }

    public static boolean downloadFile(String url,String saveFilePath){

        if(isFile(url)) return true;
        // check the file is exists or not
        String savePath = TextUtils.isEmpty(saveFilePath) ? createFilePath(url,"/") : saveFilePath;
        File f = new File(savePath);
        if(f.exists()){
            return true;
        }

        boolean download = false;
        HttpURLConnection conn = null;
        int statusCode = 0;
        boolean interrupt;
        int tick = 0;
        do {
            interrupt = false;
            try {
                conn = createHttpURLConnecttion(url, null, null);
                if(conn==null){
                    break;
                }
                conn.setRequestProperty("Connection", "close");
                conn.setRequestProperty("Accept", "image/*");
                conn.setRequestProperty("Accept-Encoding", "gzip,deflate");

                statusCode = conn.getResponseCode();
                String contentType = conn.getContentType();
                if (contentType != null)  interrupt = true;
            } catch (java.io.IOException e) {
                try {
                    conn.disconnect();
                } catch (Exception ex) {
                }
                return false;
            } catch (Exception e) {//e.printStackTrace();
                if (tick == 0) {
                    interrupt = true;
                } else {
                    try {
                        conn.disconnect();
                    } catch (Exception ex) {
                    }
                    return false;
                }
            }
            tick ++;
            if (tick >= 2) break;
            if (interrupt) {	//	如果出现错误，稍后重试
                try {
                    conn.disconnect();
                    Thread.sleep(200);
                } catch (Exception e) {
                }
            }else{
                break;
            }
        } while (true);

        try {
            if(statusCode == 200) {
                final String encoding = conn.getHeaderField("Content-Encoding");
                InputStream is;
                if ("gzip".equalsIgnoreCase(encoding)) {
                    is = new java.util.zip.GZIPInputStream(conn.getInputStream());
                } else {
                    is = conn.getInputStream();
                }
                File file = new File(savePath + ".tmp");
                FileOutputStream fos = new FileOutputStream(file);
                try {
                    byte[] buffer = new byte[8 * 1024];
                    int readCount = 0;
                    while ((readCount = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, readCount);
                        Thread.sleep(1);
                    }
                    fos.flush();
                    file.renameTo(new File(savePath));
                    download = true;
                } catch (Exception e) {
                    download = false;
                } finally {
                    try {
                        if (is != null) is.close();
                        if (fos != null) fos.close();
                        if (conn != null) conn.disconnect();
                        Thread.sleep(30);
                    } catch (Exception e) {
                    }
                }
            }
        } catch (Exception e) {
            download = false;
        }

        // if download file failed then delete it
        if(!download){
            f = new File(savePath);
            if(f.exists()) f.delete();
        }
        return download;
    }

    static HttpURLConnection createHttpURLConnecttion(String strUrl, Map<String, String> requestProperties, byte[] postBytes) throws Exception{
        HttpURLConnection conn = null;
        URL url = new URL(strUrl);
        // check download url valid
        String protocol = url.getProtocol();
        String host = url.getHost();
        String file = url.getFile();
        if(TextUtils.isEmpty(protocol) || TextUtils.isEmpty(host) || TextUtils.isEmpty(file) || strUrl.endsWith("/")) return conn;
        conn = (HttpURLConnection) url.openConnection();
        if (requestProperties != null && requestProperties.size() > 0) {
            Set<String> keys = requestProperties.keySet();
            Iterator<String> iterator = keys.iterator();
            while (iterator.hasNext()) {
                String key = iterator.next();
                if (key != null && key.length() > 0) {
                    conn.setRequestProperty(key, requestProperties.get(key));
                }
            }
        }
        conn.setConnectTimeout(60 * 1000);
        conn.setDoInput(true);

        if (postBytes != null) {
            conn.setDoOutput(true);
            OutputStream out = conn.getOutputStream();
            out.write(postBytes);
        }
        return conn;
    }
}
