package rs.ltt.android.ui;

import android.os.Build;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

public final class BackInvokedDispatchers {

    private BackInvokedDispatchers() {}

    public static OnBackInvokedCallback of(final OnBackPressedCallback onBackPressedCallback) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return onBackPressedCallback::handleOnBackPressed;
        } else {
            return null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static void setEnabled(
            final AppCompatActivity activity,
            final OnBackInvokedCallback onBackInvokedCallback,
            final boolean enabled) {
        final var onBackInvokedDispatcher = activity.getOnBackInvokedDispatcher();
        if (enabled) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_OVERLAY + 1, onBackInvokedCallback);
        } else {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCallback);
        }
    }
}
