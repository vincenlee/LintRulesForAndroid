package com.ytjojo.module_article;

import android.os.Bundle;
import android.support.annotation.Keep;
import android.support.annotation.Nullable;
import android.util.Log;
import com.easygroup.ngaridoctor.library.BaseActivity;

/**
 * Created by Administrator on 2018/1/15 0015.
 */

@Keep
public class ArticleListArcivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_articlelist);
        getResources().getIdentifier("app_name","string",getPackageName());
        Log.e("sss","  sdw ");
        getColor(00);
    }

    @Override protected void init() {

    }
}
