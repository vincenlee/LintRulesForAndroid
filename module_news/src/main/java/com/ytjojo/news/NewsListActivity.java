package com.ytjojo.news;

import android.os.Bundle;
import com.easygroup.ngaridoctor.library.BaseActivity;

/**
 * Created by Administrator on 2018/1/13 0013.
 */

public class NewsListActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(android.support.v7.appcompat.R.layout.abc_search_view);
        getColor(0);
    }

    @Override protected void init() {

    }
}
