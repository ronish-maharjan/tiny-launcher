package com.ronish.tinytvlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LauncherActivity extends Activity {

    private static final int TEXT_COLOR =
            Color.WHITE;

    private static final int CELL_HEIGHT_DP =
            120;

    private static final int CELL_MIN_WIDTH_DP =
            170;

    private static final int CELL_SPACING_DP =
            12;

    private AppRepository repository;

    private PackageManager packageManager;

    private FocusGrid appGrid;

    private TextView settingsButton;

    private TextView modeLabel;

    private TextView emptyLabel;

    private EditMode editMode =
            EditMode.NONE;

    private boolean orderDirty;

    private boolean needsRefresh =
            true;

    private android.window.OnBackInvokedCallback backCallback;

    private enum EditMode {
        NONE,
        MOVE,
        HIDE
    }

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);

        requestWindowFeature(
                Window.FEATURE_NO_TITLE
        );

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        /*
         * Direct color assignment.
         *
         * This guarantees that the launcher is purple even if a
         * resource/theme is accidentally still black.
         */

        repository =
                new AppRepository(this);

        packageManager =
                getPackageManager();

        /*
         * Root.
         */
        FrameLayout root =
                new FrameLayout(this);

        root.setPadding(
                dp(32),
                dp(20),
                dp(32),
                dp(20)
        );

        /*
         * Settings button.
         */
        settingsButton =
                createSettingsButton();

        FrameLayout.LayoutParams settingsParams =
                new FrameLayout.LayoutParams(
                        dp(56),
                        dp(56),
                        Gravity.TOP | Gravity.RIGHT
                );

        root.addView(
                settingsButton,
                settingsParams
        );

        /*
         * Main focus grid.
         */
        appGrid =
                new FocusGrid(this);

        FrameLayout.LayoutParams gridParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        gridParams.topMargin =
                dp(62);

        root.addView(
                appGrid,
                gridParams
        );

        /*
         * Empty state.
         */
        emptyLabel =
                new TextView(this);

        emptyLabel.setText(
                R.string.no_apps
        );

        emptyLabel.setTextColor(
                TEXT_COLOR
        );

        emptyLabel.setTextSize(16);

        emptyLabel.setGravity(
                Gravity.CENTER
        );

        emptyLabel.setVisibility(
                View.GONE
        );

        FrameLayout.LayoutParams emptyParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        emptyParams.topMargin =
                dp(62);

        root.addView(
                emptyLabel,
                emptyParams
        );

        /*
         * Small status text used only while editing.
         */
        modeLabel =
                new TextView(this);

        modeLabel.setTextColor(
                Color.argb(
                        190,
                        255,
                        255,
                        255
                )
        );

        modeLabel.setTextSize(12);

        modeLabel.setVisibility(
                View.GONE
        );

        FrameLayout.LayoutParams modeParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.LEFT
                );

        modeParams.leftMargin =
                dp(8);

        modeParams.topMargin =
                dp(4);

        root.addView(
                modeLabel,
                modeParams
        );

        setContentView(root);

        /*
         * API 33+ back callback.
         *
         * Fully qualified names keep old Android devices from
         * trying to load these classes.
         */
        if (Build.VERSION.SDK_INT >= 33) {

            backCallback =
                    new android.window.OnBackInvokedCallback() {

                        @Override
                        public void onBackInvoked() {
                            handleBack();
                        }
                    };

            getOnBackInvokedDispatcher()
                    .registerOnBackInvokedCallback(
                            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                            backCallback
                    );
        }

        /*
         * Initial application discovery.
         */

        repository.refresh();
        rebuildApps();

        /*
         * Initial focus.
         */
        appGrid.post(
                new Runnable() {

                    @Override
                    public void run() {

                        focusFirstApp();
                    }
                }
        );

        needsRefresh =
                false;
    }

    @Override
    protected void onResume() {

        super.onResume();

        if (needsRefresh) {

            refreshAppsIfChanged();

            needsRefresh =
                    false;
        }

        /*
         * Ensure an application remains immediately actionable.
         */
        appGrid.post(
                new Runnable() {

                    @Override
                    public void run() {

                        if (editMode == EditMode.NONE
                                && repository.visibleCount() > 0) {

                            if (appGrid.findFocus() == null) {
                                focusFirstApp();
                            }
                        }
                    }
                }
        );
    }

    @Override
    protected void onPause() {

        if (editMode != EditMode.NONE) {
            finishEditMode();
        }

        needsRefresh =
                true;

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        if (Build.VERSION.SDK_INT >= 33
                && backCallback != null) {

            getOnBackInvokedDispatcher()
                    .unregisterOnBackInvokedCallback(
                            backCallback
                    );
        }

        super.onDestroy();
    }

    /*
     * Build the app views from repository data.
     *
     * This is done only when the installed-app set changes,
     * not while navigating.
     */
    private void rebuildApps() {

        appGrid.removeAllViews();

        List<AppInfo> apps =
                repository.visibleApps();

        if (apps.isEmpty()) {

            appGrid.setVisibility(
                    View.GONE
            );

            emptyLabel.setVisibility(
                    View.VISIBLE
            );

            settingsButton.requestFocus();

            return;
        }

        appGrid.setVisibility(
                View.VISIBLE
        );

        emptyLabel.setVisibility(
                View.GONE
        );

        /*
         * Create each tile once.
         */
        for (AppInfo info : apps) {

            TextView tile =
                    createAppTile(info);

            appGrid.addView(tile);
        }

        appGrid.calculateColumns();

        wireGridNavigation();
    }

    /*
     * Refresh package list without rebuilding the UI if nothing
     * relevant changed.
     */
    private void refreshAppsIfChanged() {

        boolean changed =
                repository.refresh();

        if (changed
                || appGrid.getChildCount()
                != repository.visibleCount()) {

            rebuildApps();

        } else {

            appGrid.calculateColumns();
            wireGridNavigation();
        }
    }

    /*
     * Create one application tile.
     *
     * One TextView only.
     */
    private TextView createAppTile(
            final AppInfo info
    ) {

        TextView tile =
                new TextView(this);

        tile.setText(
                info.label
        );

        tile.setTextColor(
                TEXT_COLOR
        );

        tile.setTextSize(
                14
        );

        tile.setGravity(
                Gravity.CENTER
        );

        tile.setSingleLine(true);

        tile.setEllipsize(
                android.text.TextUtils.TruncateAt.END
        );

        tile.setIncludeFontPadding(false);

        int padding =
                dp(8);

        tile.setPadding(
                padding,
                padding,
                padding,
                padding
        );

        tile.setCompoundDrawablePadding(
                dp(8)
        );

        /*
         * This view itself participates in Android TV focus.
         */
        tile.setFocusable(true);

        tile.setFocusableInTouchMode(false);

        tile.setClickable(true);

        tile.setSoundEffectsEnabled(false);

        tile.setHapticFeedbackEnabled(false);

        /*
         * State-list drawable.
         *
         * No OnFocusChangeListener.
         */
        tile.setBackgroundResource(
                R.drawable.app_item_background
        );

        /*
         * Load the icon once.
         */
        Drawable icon =
                loadIcon(info);

        if (icon != null) {

            int size =
                    dp(60);

            icon.setBounds(
                    0,
                    0,
                    size,
                    size
            );

            tile.setCompoundDrawables(
                    null,
                    icon,
                    null,
                    null
            );
        }

        /*
         * Explicit application launch.
         */
        tile.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(
                            View view
                    ) {

                        if (editMode != EditMode.NONE) {
                            return;
                        }

                        launch(info);
                    }
                }
        );

        return tile;
    }

    /*
     * Icon cache.
     *
     * PackageManager calls are performed only when a tile is created.
     */
    private final Map<String, Drawable> iconCache =
            new HashMap<>();

    private Drawable loadIcon(
            AppInfo info
    ) {

        Drawable cached =
                iconCache.get(
                        info.packageName
                );

        if (cached != null) {
            return cached;
        }

        try {

            Drawable icon =
                    packageManager.getActivityIcon(
                            info.component()
                    );

            if (icon != null) {

                iconCache.put(
                        info.packageName,
                        icon
                );

                return icon;
            }

        } catch (
                PackageManager.NameNotFoundException ignored
        ) {
            // Use no icon.
        }

        return null;
    }

    /*
     * Explicit D-pad relationships.
     *
     * This means there is no expensive spatial focus search during
     * rapid left/right presses.
     */
    private void wireGridNavigation() {

        int count =
                appGrid.getChildCount();

        int columns =
                appGrid.getColumnCount();

        if (count == 0
                || columns <= 0) {
            return;
        }

        for (int i = 0; i < count; i++) {

            View tile =
                    appGrid.getChildAt(i);

            int row =
                    i / columns;

            int column =
                    i % columns;

            /*
             * LEFT
             */
            if (column > 0) {

                tile.setNextFocusLeftId(
                        appGrid
                                .getChildAt(i - 1)
                                .getId()
                );

            } else {

                tile.setNextFocusLeftId(
                        tile.getId()
                );
            }

            /*
             * RIGHT
             */
            if (column + 1 < columns
                    && i + 1 < count) {

                tile.setNextFocusRightId(
                        appGrid
                                .getChildAt(i + 1)
                                .getId()
                );

            } else {

                tile.setNextFocusRightId(
                        tile.getId()
                );
            }

            /*
             * UP
             */
            int up =
                    i - columns;

            if (up >= 0) {

                tile.setNextFocusUpId(
                        appGrid
                                .getChildAt(up)
                                .getId()
                );

            } else {

                /*
                 * Top application row goes back to Settings.
                 */
                tile.setNextFocusUpId(
                        settingsButton.getId()
                );
            }

            /*
             * DOWN
             */
            int down =
                    i + columns;

            if (down < count) {

                tile.setNextFocusDownId(
                        appGrid
                                .getChildAt(down)
                                .getId()
                );

            } else {

                /*
                 * At the bottom, stay here.
                 */
                tile.setNextFocusDownId(
                        tile.getId()
                );
            }
        }
    }

    /*
     * Settings button.
     */
    private TextView createSettingsButton() {

        TextView button =
                new TextView(this);

        button.setId(
                View.generateViewId()
        );

        button.setText("⚙");

        button.setTextColor(
                TEXT_COLOR
        );

        button.setTextSize(23);

        button.setGravity(
                Gravity.CENTER
        );

        button.setFocusable(true);

        button.setFocusableInTouchMode(false);

        button.setClickable(true);

        button.setSoundEffectsEnabled(false);

        button.setHapticFeedbackEnabled(false);

        configureSettingsBackground(
                button
        );

        button.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(
                            View view
                    ) {

                        showSettings();
                    }
                }
        );

        /*
         * Settings ↓ always goes to first application.
         */
        button.setOnKeyListener(
                new View.OnKeyListener() {

                    @Override
                    public boolean onKey(
                            View v,
                            int keyCode,
                            KeyEvent event
                    ) {

                        if (event.getAction()
                                != KeyEvent.ACTION_DOWN) {

                            return false;
                        }

                        if (keyCode
                                == KeyEvent.KEYCODE_DPAD_DOWN) {

                            if (repository.visibleCount()
                                    > 0) {

                                focusFirstApp();

                            }

                            return true;
                        }

                        /*
                         * Prevent focus from disappearing sideways/up.
                         */
                        if (keyCode
                                == KeyEvent.KEYCODE_DPAD_UP
                                || keyCode
                                == KeyEvent.KEYCODE_DPAD_LEFT
                                || keyCode
                                == KeyEvent.KEYCODE_DPAD_RIGHT) {

                            return true;
                        }

                        return false;
                    }
                }
        );

        return button;
    }

    private void configureSettingsBackground(
            TextView button
    ) {

        final GradientDrawable background =
                new GradientDrawable();

        background.setCornerRadius(
                dp(7)
        );

        background.setColor(
                Color.TRANSPARENT
        );

        button.setBackground(
                background
        );

        button.setOnFocusChangeListener(
                new View.OnFocusChangeListener() {

                    @Override
                    public void onFocusChange(
                            View v,
                            boolean focused
                    ) {

                        if (focused) {

                            background.setColor(
                                    Color.rgb(
                                            70,
                                            58,
                                            84
                                    )
                            );

                            background.setStroke(
                                    dp(2),
                                    Color.rgb(
                                            229,
                                            216,
                                            242
                                    )
                            );

                        } else {

                            background.setColor(
                                    Color.TRANSPARENT
                            );

                            background.setStroke(
                                    0,
                                    Color.TRANSPARENT
                            );
                        }
                    }
                }
        );
    }

    private void focusFirstApp() {

        if (appGrid.getChildCount() == 0) {

            settingsButton.requestFocus();

            return;
        }

        View first =
                appGrid.getChildAt(0);

        if (first != null) {
            first.requestFocus();
        }
    }

    /*
     * Launch selected application.
     */
    private void launch(
            AppInfo info
    ) {

        Intent intent =
                new Intent(
                        Intent.ACTION_MAIN
                );

        intent.setComponent(
                info.component()
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        );

        try {

            startActivity(intent);

        } catch (
                ActivityNotFoundException
                | SecurityException e
        ) {

            new AlertDialog.Builder(this)
                    .setTitle(
                            R.string.app_name
                    )
                    .setMessage(
                            getString(
                                    R.string.launch_error,
                                    info.label
                            )
                    )
                    .setPositiveButton(
                            R.string.ok,
                            null
                    )
                    .show();
        }
    }

    /*
     * Main settings.
     */
    private void showSettings() {

        if (editMode != EditMode.NONE) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        R.string.settings
                )
                .setItems(
                        new CharSequence[]{
                                getString(
                                        R.string.settings_hidden_apps
                                ),
                                getString(
                                        R.string.settings_rearrange_apps
                                ),
                                getString(
                                        R.string.settings_hide_apps
                                ),
                                getString(
                                        R.string.settings_reset_layout
                                )
                        },
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(
                                    DialogInterface dialog,
                                    int which
                            ) {

                                switch (which) {

                                    case 0:
                                        showHiddenApps();
                                        break;

                                    case 1:
                                        enterMoveMode();
                                        break;

                                    case 2:
                                        enterHideMode();
                                        break;

                                    case 3:
                                        showResetConfirmation();
                                        break;

                                    default:
                                        break;
                                }
                            }
                        }
                )
                .setOnDismissListener(
                        new DialogInterface.OnDismissListener() {

                            @Override
                            public void onDismiss(
                                    DialogInterface dialog
                            ) {

                                focusFirstApp();
                            }
                        }
                )
                .show();
    }

    /*
     * Hidden applications.
     */
    private void showHiddenApps() {

        final ArrayList<AppInfo> hidden =
                repository.hiddenApps();

        if (hidden.isEmpty()) {

            new AlertDialog.Builder(this)
                    .setTitle(
                            R.string.hidden_apps
                    )
                    .setMessage(
                            R.string.no_hidden_apps
                    )
                    .setPositiveButton(
                            R.string.ok,
                            null
                    )
                    .show();

            return;
        }

        String[] labels =
                new String[hidden.size()];

        for (int i = 0; i < hidden.size(); i++) {

            labels[i] =
                    hidden.get(i).label;
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        R.string.hidden_apps
                )
                .setItems(
                        labels,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(
                                    DialogInterface dialog,
                                    int which
                            ) {

                                AppInfo info =
                                        hidden.get(
                                                which
                                        );

                                repository.unhide(
                                        info.packageName
                                );

                                rebuildApps();

                                View restored =
                                        findTile(
                                                info.packageName
                                        );

                                if (restored != null) {
                                    restored.requestFocus();
                                }
                            }
                        }
                )
                .show();
    }

    /*
     * Find a view representing a package.
     *
     * We search only when returning from settings, never during
     * ordinary D-pad navigation.
     */
    private View findTile(
            String packageName
    ) {

        List<AppInfo> apps =
                repository.visibleApps();

        for (int i = 0; i < apps.size(); i++) {

            if (apps.get(i)
                    .packageName
                    .equals(packageName)) {

                return appGrid.getChildAt(i);
            }
        }

        return null;
    }

    /*
     * Move mode.
     */
    private void enterMoveMode() {

        if (repository.visibleCount() == 0) {
            return;
        }

        editMode =
                EditMode.MOVE;

        orderDirty =
                false;

        modeLabel.setText(
                R.string.mode_move
        );

        modeLabel.setVisibility(
                View.VISIBLE
        );

        settingsButton.setVisibility(
                View.INVISIBLE
        );

        focusFirstApp();
    }

    /*
     * Hide mode.
     */
    private void enterHideMode() {

        if (repository.visibleCount() == 0) {
            return;
        }

        editMode =
                EditMode.HIDE;

        orderDirty =
                false;

        modeLabel.setText(
                R.string.mode_hide
        );

        modeLabel.setVisibility(
                View.VISIBLE
        );

        settingsButton.setVisibility(
                View.INVISIBLE
        );

        focusFirstApp();
    }

    /*
     * Finish edit mode.
     */
    private void finishEditMode() {

        if (editMode == EditMode.MOVE
                && orderDirty) {

            repository.persistOrder();

            orderDirty =
                    false;
        }

        editMode =
                EditMode.NONE;

        modeLabel.setVisibility(
                View.GONE
        );

        settingsButton.setVisibility(
                View.VISIBLE
        );

        focusFirstApp();
    }

    /*
     * Hide focused application.
     */
    private void hideSelected() {

        View focused =
                appGrid.findFocus();

        if (focused == null) {
            return;
        }

        int position =
                appGrid.indexOfChild(
                        focused
                );

        if (position < 0) {
            return;
        }

        AppInfo info =
                repository.visibleAppAt(
                        position
                );

        if (info == null) {
            return;
        }

        repository.hide(
                info.packageName
        );

        appGrid.removeViewAt(
                position
        );

        appGrid.calculateColumns();

        wireGridNavigation();

        int count =
                appGrid.getChildCount();

        if (count == 0) {

            finishEditMode();

            emptyLabel.setVisibility(
                    View.VISIBLE
            );

            return;
        }

        int newPosition =
                Math.min(
                        position,
                        count - 1
                );

        View next =
                appGrid.getChildAt(
                        newPosition
                );

        if (next != null) {
            next.requestFocus();
        }
    }

    /*
     * Move focused application.
     *
     * The repository and view order are both changed without
     * rebuilding icons.
     */
    private void moveFocused(
            int keyCode
    ) {

        View focused =
                appGrid.findFocus();

        if (focused == null) {
            return;
        }

        int current =
                appGrid.indexOfChild(
                        focused
                );

        if (current < 0) {
            return;
        }

        int columns =
                appGrid.getColumnCount();

        if (columns <= 0) {
            return;
        }

        int count =
                appGrid.getChildCount();

        int target =
                current;

        switch (keyCode) {

            case KeyEvent.KEYCODE_DPAD_LEFT:

                if (current % columns == 0) {
                    return;
                }

                target =
                        current - 1;

                break;

            case KeyEvent.KEYCODE_DPAD_RIGHT:

                if (current + 1 >= count
                        || current % columns == columns - 1) {

                    return;
                }

                target =
                        current + 1;

                break;

            case KeyEvent.KEYCODE_DPAD_UP:

                if (current - columns < 0) {
                    return;
                }

                target =
                        current - columns;

                break;

            case KeyEvent.KEYCODE_DPAD_DOWN:

                if (current + columns >= count) {
                    return;
                }

                target =
                        current + columns;

                break;

            default:
                return;
        }

        repository.moveVisible(
                current,
                target
        );

        /*
         * Swap actual views.
         *
         * Icons stay attached to their views, so no icon reload occurs.
         */
        appGrid.swapChildren(
                current,
                target
        );

        orderDirty =
                true;

        View moved =
                appGrid.getChildAt(
                        target
                );

        if (moved != null) {
            moved.requestFocus();
        }
    }

    /*
     * Reset layout.
     */
    private void showResetConfirmation() {

        new AlertDialog.Builder(this)
                .setTitle(
                        R.string.settings_reset_layout
                )
                .setMessage(
                        R.string.reset_layout_message
                )
                .setNegativeButton(
                        R.string.cancel,
                        null
                )
                .setPositiveButton(
                        R.string.reset_layout,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(
                                    DialogInterface dialog,
                                    int which
                            ) {

                                repository.resetOrder();

                                rebuildApps();

                                focusFirstApp();
                            }
                        }
                )
                .show();
    }

    /*
     * Common back handling.
     */
    private void handleBack() {

        if (editMode != EditMode.NONE) {

            finishEditMode();

            return;
        }

        /*
         * Normal launcher mode intentionally stays on Home.
         */
    }

    /*
     * Android 6 through Android 12.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {

        if (Build.VERSION.SDK_INT < 33) {
            handleBack();
        }
    }

    private int dp(
            int value
    ) {

        return Math.round(
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }

    /*
     * Lightweight custom focus grid.
     *
     * Children are real focusable TextViews.
     *
     * Navigation is calculated directly instead of asking the
     * Android spatial focus algorithm to search the hierarchy.
     */
    private final class FocusGrid
            extends ViewGroup {

        private int columns = 1;

        private int cellWidth;

        private int cellHeight;

        private int spacing;

        FocusGrid(
                android.content.Context context
        ) {

            super(context);

            setFocusable(false);

            setClipChildren(false);

            setClipToPadding(false);
        }

        void calculateColumns() {

            int width =
                    getWidth();

            if (width <= 0) {

                post(
                        new Runnable() {

                            @Override
                            public void run() {
                                calculateColumns();
                            }
                        }
                );

                return;
            }

            int minWidth =
                    dp(
                            CELL_MIN_WIDTH_DP
                    );

            spacing =
                    dp(
                            CELL_SPACING_DP
                    );

            columns =
                    Math.max(
                            1,
                            (width + spacing)
                                    / (minWidth + spacing)
                    );

            cellWidth =
                    (
                            width
                                    - (columns - 1) * spacing
                    )
                            / columns;

            cellHeight =
                    dp(
                            CELL_HEIGHT_DP
                    );

            requestLayout();

            wireGridNavigation();
        }

        int getColumnCount() {
            return columns;
        }

        void swapChildren(
                int first,
                int second
        ) {

            if (first == second) {
                return;
            }

            View a =
                    getChildAt(first);

            View b =
                    getChildAt(second);

            if (a == null || b == null) {
                return;
            }

            /*
             * Remove in descending order so indexes remain valid.
             */
            if (first < second) {

                removeViewAt(second);
                removeViewAt(first);

                addView(
                        b,
                        first
                );

                addView(
                        a,
                        second
                );

            } else {

                removeViewAt(first);
                removeViewAt(second);

                addView(
                        a,
                        second
                );

                addView(
                        b,
                        first
                );
            }

            requestLayout();
        }

        @Override
        protected void onMeasure(
                int widthMeasureSpec,
                int heightMeasureSpec
        ) {

            int width =
                    MeasureSpec.getSize(
                            widthMeasureSpec
                    );

            int height =
                    MeasureSpec.getSize(
                            heightMeasureSpec
                    );

            setMeasuredDimension(
                    width,
                    height
            );

            if (cellWidth <= 0) {
                return;
            }

            for (int i = 0;
                 i < getChildCount();
                 i++) {

                View child =
                        getChildAt(i);

                child.measure(
                        MeasureSpec.makeMeasureSpec(
                                cellWidth,
                                MeasureSpec.EXACTLY
                        ),
                        MeasureSpec.makeMeasureSpec(
                                cellHeight,
                                MeasureSpec.EXACTLY
                        )
                );
            }
        }

        @Override
        protected void onLayout(
                boolean changed,
                int left,
                int top,
                int right,
                int bottom
        ) {

            if (getChildCount() == 0) {
                return;
            }

            if (cellWidth <= 0) {
                calculateColumns();
                return;
            }

            for (int i = 0;
                 i < getChildCount();
                 i++) {

                View child =
                        getChildAt(i);

                int row =
                        i / columns;

                int column =
                        i % columns;

                int childLeft =
                        column *
                                (cellWidth + spacing);

                int childTop =
                        row *
                                (cellHeight + spacing);

                child.layout(
                        childLeft,
                        childTop,
                        childLeft + cellWidth,
                        childTop + cellHeight
                );
            }
        }

        @Override
        public boolean dispatchKeyEvent(
                KeyEvent event
        ) {

            View focused =
                    findFocus();

            if (focused == null) {
                return super.dispatchKeyEvent(
                        event
                );
            }

            int keyCode =
                    event.getKeyCode();

            /*
             * Navigation.
             */
            if (event.getAction()
                    == KeyEvent.ACTION_DOWN) {

                if (editMode == EditMode.MOVE) {

                    switch (keyCode) {

                        case KeyEvent.KEYCODE_DPAD_LEFT:
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                        case KeyEvent.KEYCODE_DPAD_UP:
                        case KeyEvent.KEYCODE_DPAD_DOWN:

                            moveFocused(
                                    keyCode
                            );

                            return true;

                        case KeyEvent.KEYCODE_DPAD_CENTER:
                        case KeyEvent.KEYCODE_ENTER:
                        case KeyEvent.KEYCODE_BUTTON_A:

                            finishEditMode();

                            return true;

                        case KeyEvent.KEYCODE_BACK:

                            finishEditMode();

                            return true;

                        default:
                            break;
                    }
                }

                if (editMode == EditMode.HIDE) {

                    switch (keyCode) {

                        case KeyEvent.KEYCODE_DPAD_CENTER:
                        case KeyEvent.KEYCODE_ENTER:
                        case KeyEvent.KEYCODE_BUTTON_A:

                            if (event.getRepeatCount() == 0) {
                                hideSelected();
                            }

                            return true;

                        case KeyEvent.KEYCODE_BACK:

                            finishEditMode();

                            return true;

                        default:
                            break;
                    }
                }

                /*
                 * Normal navigation.
                 *
                 * We perform it ourselves.
                 */
                switch (keyCode) {

                    case KeyEvent.KEYCODE_DPAD_LEFT:
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_DPAD_DOWN:

                        navigate(
                                focused,
                                keyCode
                        );

                        return true;

                    default:
                        break;
                }
            }

            /*
             * Normal app activation.
             *
             * ACTION_UP prevents double launch.
             */
            if (editMode == EditMode.NONE
                    && event.getAction()
                    == KeyEvent.ACTION_UP
                    && event.getRepeatCount() == 0) {

                switch (keyCode) {

                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                    case KeyEvent.KEYCODE_BUTTON_A:

                        focused.performClick();

                        return true;

                    default:
                        break;
                }
            }

            return super.dispatchKeyEvent(
                    event
            );
        }

        private void navigate(
                View focused,
                int keyCode
        ) {

            int current =
                    indexOfChild(focused);

            if (current < 0) {
                return;
            }

            int count =
                    getChildCount();

            if (count == 0) {
                return;
            }

            int target =
                    current;

            int column =
                    current % columns;

            int row =
                    current / columns;

            switch (keyCode) {

                case KeyEvent.KEYCODE_DPAD_LEFT:

                    if (column == 0) {
                        return;
                    }

                    target =
                            current - 1;

                    break;

                case KeyEvent.KEYCODE_DPAD_RIGHT:

                    /*
                     * Don't move past the last item in the current row.
                     */
                    if (current + 1 >= count
                            || column + 1 >= columns
                            || (current + 1) / columns != row) {

                        return;
                    }

                    target =
                            current + 1;

                    break;

                case KeyEvent.KEYCODE_DPAD_UP:

                    /*
                     * First row -> Settings.
                     */
                    if (row == 0) {

                        settingsButton.requestFocus();

                        return;
                    }

                    /*
                     * Previous row may be shorter.
                     *
                     * Clamp to the last item that actually exists
                     * in that row.
                     */
                    int previousRowStart =
                            (row - 1) * columns;

                    int previousRowEnd =
                            Math.min(
                                    previousRowStart + columns - 1,
                                    count - 1
                            );

                    target =
                            Math.min(
                                    previousRowStart + column,
                                    previousRowEnd
                            );

                    break;

                case KeyEvent.KEYCODE_DPAD_DOWN:

                    int nextRowStart =
                            (row + 1) * columns;

                    /*
                     * No next row.
                     */
                    if (nextRowStart >= count) {
                        return;
                    }

                    int nextRowEnd =
                            Math.min(
                                    nextRowStart + columns - 1,
                                    count - 1
                            );

                    /*
                     * If the next row is shorter, use its last item.
                     */
                    target =
                            Math.min(
                                    nextRowStart + column,
                                    nextRowEnd
                            );

                    break;

                default:
                    return;
            }

            View next =
                    getChildAt(target);

            if (next != null) {
                next.requestFocus();
            }
        }
    }
}