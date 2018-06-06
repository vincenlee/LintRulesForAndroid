package com.ytjojo.lintjar.plugin

import com.android.build.gradle.api.BaseVariant;

/**
 * Created by jiulongteng on 2018/6/4.
 */

public class PluginUtils {

    //http://blog.mathieu.carbou.me/post/103163278870/deep-merge-map-in-groovy
    public static Map deepMerge(Map onto, Map... overrides) {
        if (!overrides) {
            return onto
        } else if (overrides.length == 1) {
            overrides[0]?.each { k, v ->
                if (v instanceof Map && onto[k] instanceof Map) {
                    deepMerge((Map) onto[k], (Map) v)
                } else {
                    onto[k] = v
                }
            }
            return onto
        }
        return overrides.inject(onto, { acc, override -> deepMerge(acc, override ?: [:]) })
    }
}
