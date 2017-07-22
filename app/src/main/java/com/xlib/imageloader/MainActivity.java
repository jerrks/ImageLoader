package com.xlib.imageloader;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    void loadImage(){
        String url = "image_url";
        ImageView iv = null;
        ImageLoader.getInstance().loadImage(url,iv);
    }
}
