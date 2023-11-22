/*
 * Copyright 2020 Daniel Gultsch
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rs.ltt.android;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.database.AppDatabase;
import rs.ltt.android.ui.notification.AttachmentNotification;

public class LttrsApplication extends Application {

    private static final Object CACHE_LOCK = new Object();
    private final Logger LOGGER = LoggerFactory.getLogger(LttrsApplication.class);
    private Long mostRecentlySelectedAccountId = null;

    public static LttrsApplication get(final Application application) {
        if (application instanceof LttrsApplication) {
            return (LttrsApplication) application;
        }
        throw new IllegalStateException(
                "Application is not a " + LttrsApplication.class.getSimpleName());
    }

    public static LttrsApplication get(final Activity activity) {
        return get(activity.getApplication());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AttachmentNotification.createChannel(getApplicationContext());
        applyThemeSettings();
    }

    public void applyThemeSettings() {
        final var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences == null) {
            return;
        }
        applyThemeSettings(sharedPreferences);
    }

    private void applyThemeSettings(final SharedPreferences sharedPreferences) {
        AppCompatDelegate.setDefaultNightMode(getDesiredNightMode(this, sharedPreferences));
        var dynamicColorsOptions =
                new DynamicColorsOptions.Builder()
                        .setPrecondition((activity, t) -> isDynamicColorsDesired(activity))
                        .build();
        DynamicColors.applyToActivitiesIfAvailable(this, dynamicColorsOptions);
    }

    public static int getDesiredNightMode(final Context context) {
        final var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPreferences == null) {
            return AppCompatDelegate.getDefaultNightMode();
        }
        return getDesiredNightMode(context, sharedPreferences);
    }

    public static boolean isDynamicColorsDesired(final Context context) {
        final var preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean("dynamic_colors", true);
    }

    private static int getDesiredNightMode(
            final Context context, final SharedPreferences sharedPreferences) {
        final String theme = sharedPreferences.getString("theme", "automatic");
        return getDesiredNightMode(theme);
    }

    public static int getDesiredNightMode(final String theme) {
        if ("automatic".equals(theme)) {
            return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        } else if ("light".equals(theme)) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        } else {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
    }

    public boolean noAccountsConfigured() {
        synchronized (CACHE_LOCK) {
            if (mostRecentlySelectedAccountId != null) {
                return false;
            }
        }
        return !AppDatabase.getInstance(this).accountDao().hasAccounts();
    }

    public Long getMostRecentlySelectedAccountId() {
        synchronized (CACHE_LOCK) {
            if (this.mostRecentlySelectedAccountId == null) {
                final Long id =
                        AppDatabase.getInstance(this)
                                .accountDao()
                                .getMostRecentlySelectedAccountId();
                LOGGER.info("read most recently selected account id from database: {}", id);
                this.mostRecentlySelectedAccountId = id;
            }
            return mostRecentlySelectedAccountId;
        }
    }

    public void invalidateMostRecentlySelectedAccountId() {
        synchronized (CACHE_LOCK) {
            this.mostRecentlySelectedAccountId = null;
        }
    }
}
