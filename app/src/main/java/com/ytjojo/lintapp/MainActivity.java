package com.ytjojo.lintapp;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
        id = 3;
        init();
    }

    @Override protected void init() {

    }
    private void abc(){
        init();
        android.text.TextUtils.isEmpty("");

        new android.view.View(this);
    }
    private void setName(String name1){
        name = name;
        name1 = name1;
        this.name = name;
    }
}
