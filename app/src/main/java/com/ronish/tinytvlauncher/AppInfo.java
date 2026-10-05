package com.ronish.tinytvlauncher;

import android.content.ComponentName;

public final class AppInfo {

    public final String packageName;
    public final String activityName;
    public final String label;

    public AppInfo(
            String packageName,
            String activityName,
            String label
    ) {
        this.packageName = packageName;
        this.activityName = activityName;
        this.label = label;
    }

    public ComponentName component() {
        return new ComponentName(packageName, activityName);
    }
}