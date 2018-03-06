package com.easygroup.ngaridoctor.library;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Created by ytjojo on 21/12/2017.
 */

public abstract class BaseActivity extends Activity {
    protected abstract void init();

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getResources().getIdentifier("app_name","string",getPackageName());
        Log.e("sss","ss");
    }
}
