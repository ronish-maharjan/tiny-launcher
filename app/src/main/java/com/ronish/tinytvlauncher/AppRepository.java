package com.ronish.tinytvlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AppRepository {

    private static final String PREFS_NAME = "launcher";

    private static final String KEY_ORDER = "app_order";
    private static final String KEY_HIDDEN = "hidden_apps";

    private final Context context;
    private final PackageManager packageManager;
    private final SharedPreferences preferences;

    /*
     * All discovered applications.

     * Hidden applications remain here so when they are unhidden,
     * they return to their previous location.
     */
    private final ArrayList<AppInfo> allApps = new ArrayList<>(32);

    /*
     * Only apps visible on the launcher.
     */
    private final ArrayList<AppInfo> visibleApps = new ArrayList<>(32);

    /*
     * package -> AppInfo
     */
    private final HashMap<String, AppInfo> byPackage = new HashMap<>(32);

    /*
     * package -> index in allApps
     */
    private final HashMap<String, Integer> indexByPackage =
            new HashMap<>(32);

    private final HashSet<String> hiddenApps =
            new HashSet<>(16);

    private static final Comparator<AppInfo> LABEL_COMPARATOR =
            new Comparator<AppInfo>() {
                @Override
                public int compare(AppInfo a, AppInfo b) {
                    int result =
                            a.label.compareToIgnoreCase(b.label);

                    if (result != 0) {
                        return result;
                    }

                    return a.packageName.compareTo(b.packageName);
                }
            };

    public AppRepository(Context context) {

        this.context = context.getApplicationContext();

        this.packageManager =
                this.context.getPackageManager();

        this.preferences =
                this.context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        Set<String> savedHidden =
                preferences.getStringSet(
                        KEY_HIDDEN,
                        null
                );

        if (savedHidden != null) {
            hiddenApps.addAll(savedHidden);
        }
    }

    /**
     * Refresh the installed launcher applications.
     *
     * @return true if icon/component data may have changed.
     */
    public boolean refresh() {

        HashMap<String, AppInfo> found =
                new HashMap<>(32);

        /*
         * Prefer TV-specific launcher activities.
         */
        collect(
                Intent.CATEGORY_LEANBACK_LAUNCHER,
                found
        );

        /*
         * Then add normal launcher activities for packages
         * that did not expose a Leanback launcher activity.
         */
        collect(
                Intent.CATEGORY_LAUNCHER,
                found
        );

        boolean iconDataChanged =
                byPackage.size() != found.size();

        if (!iconDataChanged) {

            for (Map.Entry<String, AppInfo> entry :
                    found.entrySet()) {

                AppInfo oldInfo =
                        byPackage.get(entry.getKey());

                AppInfo newInfo =
                        entry.getValue();

                if (oldInfo == null
                        || !oldInfo.activityName.equals(
                        newInfo.activityName)) {

                    iconDataChanged = true;
                    break;
                }
            }
        }

        /*
         * Sort only the freshly discovered set.
         * This is not done during normal navigation.
         */
        ArrayList<AppInfo> alphabetical =
                new ArrayList<>(found.values());

        Collections.sort(
                alphabetical,
                LABEL_COMPARATOR
        );

        String savedOrder =
                preferences.getString(
                        KEY_ORDER,
                        ""
                );

        ArrayList<AppInfo> ordered =
                new ArrayList<>(found.size());

        HashSet<String> used =
                new HashSet<>(found.size() * 2 + 1);

        /*
         * Restore saved order first.
         */
        parseSavedOrder(
                savedOrder,
                found,
                ordered,
                used
        );

        /*
         * Append newly installed apps alphabetically.
         */
        for (AppInfo info : alphabetical) {

            if (used.add(info.packageName)) {
                ordered.add(info);
            }
        }

        allApps.clear();
        allApps.addAll(ordered);

        byPackage.clear();
        byPackage.putAll(found);

        /*
         * Remove hidden package names that no longer exist.
         */
        boolean hiddenChanged =
                hiddenApps.retainAll(found.keySet());

        if (hiddenChanged) {
            saveHidden();
        }

        rebuildIndexesAndVisible();

        /*
         * Persist only when the order actually changed.
         *
         * This avoids a disk write every time the launcher resumes.
         */
        String newOrder =
                buildOrderString();

        if (!newOrder.equals(savedOrder)) {

            preferences.edit()
                    .putString(KEY_ORDER, newOrder)
                    .apply();
        }

        return iconDataChanged;
    }

    private void collect(
            String category,
            HashMap<String, AppInfo> found
    ) {

        Intent intent =
                new Intent(Intent.ACTION_MAIN);

        intent.addCategory(category);

        List<ResolveInfo> results =
                packageManager.queryIntentActivities(
                        intent,
                        0
                );

        for (ResolveInfo resolveInfo : results) {

            if (resolveInfo == null) {
                continue;
            }

            ActivityInfo activityInfo =
                    resolveInfo.activityInfo;

            if (activityInfo == null) {
                continue;
            }

            if (!activityInfo.enabled) {
                continue;
            }

            String packageName =
                    activityInfo.packageName;

            if (packageName == null) {
                continue;
            }

            /*
             * Never display ourselves.
             */
            if (packageName.equals(
                    context.getPackageName())) {
                continue;
            }

            /*
             * Only keep one launcher entry per package.
             *
             * Since LEANBACK is collected first, a TV-specific
             * activity wins over a normal launcher activity.
             */
            if (found.containsKey(packageName)) {
                continue;
            }

            CharSequence label =
                    resolveInfo.loadLabel(packageManager);

            String appLabel;

            if (label == null
                    || label.length() == 0) {

                appLabel = packageName;

            } else {

                appLabel = label.toString();
            }

            found.put(
                    packageName,
                    new AppInfo(
                            packageName,
                            activityInfo.name,
                            appLabel
                    )
            );
        }
    }

    private void parseSavedOrder(
            String savedOrder,
            HashMap<String, AppInfo> found,
            ArrayList<AppInfo> ordered,
            HashSet<String> used
    ) {

        if (savedOrder == null
                || savedOrder.length() == 0) {

            return;
        }

        int start = 0;

        while (start < savedOrder.length()) {

            int end =
                    savedOrder.indexOf(
                            '\n',
                            start
                    );

            if (end < 0) {
                end = savedOrder.length();
            }

            if (end > start) {

                String packageName =
                        savedOrder.substring(
                                start,
                                end
                        );

                AppInfo info =
                        found.get(packageName);

                if (info != null
                        && used.add(packageName)) {

                    ordered.add(info);
                }
            }

            if (end == savedOrder.length()) {
                break;
            }

            start = end + 1;
        }
    }

    private void rebuildIndexesAndVisible() {

        indexByPackage.clear();
        visibleApps.clear();

        for (int i = 0; i < allApps.size(); i++) {

            AppInfo info = allApps.get(i);

            indexByPackage.put(
                    info.packageName,
                    i
            );

            if (!hiddenApps.contains(
                    info.packageName)) {

                visibleApps.add(info);
            }
        }
    }

    private String buildOrderString() {

        StringBuilder builder =
                new StringBuilder(allApps.size() * 20);

        for (int i = 0; i < allApps.size(); i++) {

            if (i != 0) {
                builder.append('\n');
            }

            builder.append(
                    allApps.get(i).packageName
            );
        }

        return builder.toString();
    }

    public List<AppInfo> visibleApps() {
        return visibleApps;
    }

    public AppInfo visibleAppAt(int position) {

        if (position < 0
                || position >= visibleApps.size()) {

            return null;
        }

        return visibleApps.get(position);
    }

    public int visibleCount() {
        return visibleApps.size();
    }

    public int visiblePositionOf(
            String packageName
    ) {

        for (int i = 0; i < visibleApps.size(); i++) {

            if (visibleApps.get(i)
                    .packageName
                    .equals(packageName)) {

                return i;
            }
        }

        return -1;
    }

    public ArrayList<AppInfo> hiddenApps() {

        ArrayList<AppInfo> result =
                new ArrayList<>();

        for (AppInfo info : allApps) {

            if (hiddenApps.contains(
                    info.packageName)) {

                result.add(info);
            }
        }

        return result;
    }

    public boolean hasHiddenApps() {
        return !hiddenApps.isEmpty();
    }

    public void hide(String packageName) {

        if (!byPackage.containsKey(packageName)) {
            return;
        }

        if (hiddenApps.add(packageName)) {

            saveHidden();
            rebuildIndexesAndVisible();
        }
    }

    public void unhide(String packageName) {

        if (hiddenApps.remove(packageName)) {

            saveHidden();
            rebuildIndexesAndVisible();
        }
    }

    public void moveVisible(
            int from,
            int to
    ) {

        if (from < 0
                || to < 0
                || from >= visibleApps.size()
                || to >= visibleApps.size()
                || from == to) {

            return;
        }

        AppInfo fromInfo =
                visibleApps.get(from);

        AppInfo toInfo =
                visibleApps.get(to);

        Integer fromIndex =
                indexByPackage.get(
                        fromInfo.packageName
                );

        Integer toIndex =
                indexByPackage.get(
                        toInfo.packageName
                );

        if (fromIndex == null
                || toIndex == null) {

            return;
        }

        Collections.swap(
                allApps,
                fromIndex,
                toIndex
        );

        indexByPackage.put(
                fromInfo.packageName,
                toIndex
        );

        indexByPackage.put(
                toInfo.packageName,
                fromIndex
        );

        rebuildIndexesAndVisible();
    }

    /**
     * Called when the user finishes rearranging.
     * We deliberately do not write to disk on every D-pad move.
     */
    public void persistOrder() {

        preferences.edit()
                .putString(
                        KEY_ORDER,
                        buildOrderString()
                )
                .apply();
    }

    public void resetOrder() {

        Collections.sort(
                allApps,
                LABEL_COMPARATOR
        );

        rebuildIndexesAndVisible();

        persistOrder();
    }

    private void saveHidden() {

        preferences.edit()
                .putStringSet(
                        KEY_HIDDEN,
                        new HashSet<>(hiddenApps)
                )
                .apply();
    }
}