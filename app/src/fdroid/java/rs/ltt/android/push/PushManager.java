package rs.ltt.android.push;

import android.app.Application;

import rs.ltt.android.entity.AccountWithCredentials;

import java.util.List;

public final class PushManager {

    private PushManager() {}

    public static boolean register(
            final Application application, final List<AccountWithCredentials> credentials) {
        return false;
    }
}
