package com.ronish.tinytvlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.TextView;

public final class AppAdapter extends BaseAdapter {

    private static final int MAX_ICON_CACHE_ENTRIES = 20;

    private final Context context;
    private final PackageManager packageManager;
    private final AppRepository repository;

    private final int iconSizePx;
    private final int itemHeightPx;

    /*
     * Bounded cache.
     *
     * We intentionally cache by package instead of retaining
     * every icon forever.
     */
    private final LruCache<String, Drawable> iconCache =
            new LruCache<>(
                    MAX_ICON_CACHE_ENTRIES
            );

    private Drawable defaultIcon;

    public AppAdapter(
            Context context,
            AppRepository repository
    ) {

        this.context =
                context.getApplicationContext();

        this.packageManager =
                this.context.getPackageManager();

        this.repository =
                repository;

        iconSizePx =
                dp(context, 60);

        itemHeightPx =
                dp(context, 120);
    }

    @Override
    public int getCount() {
        return repository.visibleCount();
    }

    @Override
    public AppInfo getItem(int position) {
        return repository.visibleAppAt(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(
            int position,
            View convertView,
            ViewGroup parent
    ) {

        TextView cell;

        if (convertView instanceof TextView) {

            cell = (TextView) convertView;

        } else {

            cell = createCell();
        }

        AppInfo info =
                getItem(position);

        if (info == null) {
            return cell;
        }

        cell.setText(info.label);
        cell.setContentDescription(info.label);

        Drawable icon =
                getIcon(info);

        cell.setCompoundDrawables(
                null,
                icon,
                null,
                null
        );

        return cell;
    }

    private TextView createCell() {

        TextView cell =
                new TextView(context);

        cell.setLayoutParams(
                new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        itemHeightPx
                )
        );

        cell.setGravity(
                Gravity.CENTER
        );

        cell.setTextColor(
                Color.WHITE
        );

        cell.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                14
        );

        cell.setSingleLine(true);
        cell.setMaxLines(1);

        cell.setEllipsize(
                TextUtils.TruncateAt.END
        );

        cell.setIncludeFontPadding(false);

        int padding =
                dp(context, 8);

        cell.setPadding(
                padding,
                padding,
                padding,
                padding
        );

        cell.setCompoundDrawablePadding(
                dp(context, 8)
        );

        /*
         * Important for Android TV.
         * GridView must be allowed to give focus to its children.
         */
        cell.setFocusable(true);
        cell.setFocusableInTouchMode(false);

        /*
         * The tile itself does not need to be a separate clickable
         * control. LauncherActivity handles the remote's OK key.
         */
        cell.setClickable(false);

        cell.setSoundEffectsEnabled(false);
        cell.setHapticFeedbackEnabled(false);

        /*
         * Native state-list background.
         * No OnFocusChangeListener.
         * No allocation when focus changes.
         */
        cell.setBackgroundResource(
                R.drawable.app_item_background
        );

        return cell;
    }

    private Drawable getIcon(
            AppInfo info
    ) {

        Drawable icon =
                iconCache.get(
                        info.packageName
                );

        if (icon != null) {

            icon.setBounds(
                    0,
                    0,
                    iconSizePx,
                    iconSizePx
            );

            return icon;
        }

        try {

            ComponentName component =
                    info.component();

            icon =
                    packageManager.getActivityIcon(
                            component
                    );

        } catch (PackageManager.NameNotFoundException e) {

            icon = getDefaultIcon();
        }

        if (icon == null) {
            icon = getDefaultIcon();
        }

        icon.setBounds(
                0,
                0,
                iconSizePx,
                iconSizePx
        );

        iconCache.put(
                info.packageName,
                icon
        );

        return icon;
    }

    private Drawable getDefaultIcon() {

        if (defaultIcon == null) {

            defaultIcon =
                    packageManager.getDefaultActivityIcon();

            defaultIcon.setBounds(
                    0,
                    0,
                    iconSizePx,
                    iconSizePx
            );
        }

        return defaultIcon;
    }

    public void clearIconCache() {
        iconCache.evictAll();
    }

    private static int dp(
            Context context,
            int value
    ) {

        return Math.round(
                value *
                        context.getResources()
                                .getDisplayMetrics()
                                .density
        );
    }
}