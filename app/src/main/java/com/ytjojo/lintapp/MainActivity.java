package com.ytjojo.lintapp;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import com.easygroup.ngaridoctor.library.BaseActivity;

public class MainActivity extends BaseActivity {
    int id = R.mipmap.playstore;
    String name = "abc";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int drawable = R.mipmap.facebook;

        setContentView(R.layout.activity_main);
        Log.e("test", "use log.e"+id);
        android.widget.Toast.makeText(this,"sss",Toast.LENGTH_SHORT).show();
        name.equals("abc");
        init();
    }

    @Override protected void init() {

    }
}
