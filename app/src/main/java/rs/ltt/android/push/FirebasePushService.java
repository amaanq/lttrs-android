package rs.ltt.android.push;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import androidx.annotation.NonNull;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FirebasePushService implements PushService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FirebasePushService.class);

    private static final String ACTION_REGISTRATION = "com.google.android.c2dm.intent.REGISTER";
    private static final String PACKAGE_NAME_GMS = "com.google.android.gms";

    private static final String URI_WEB_PUSH_ENDPOINT = "https://fcm.googleapis.com/fcm/send/%s";

    private final Context context;

    public FirebasePushService(final Context context) {
        this.context = context;
    }

    @Override
    public ListenableFuture<Optional<Uri>> register(
            final byte[] applicationServerKey, final UUID uuid) {
        if (applicationServerKey == null || applicationServerKey.length == 0) {
            return Futures.immediateFailedFuture(
                    new IllegalArgumentException("FirebasePush requires vapid key"));
        }
        final var broadcastIntent = new Intent();
        broadcastIntent.setPackage(context.getPackageName());
        final String vapidServerKey =
                BaseEncoding.base64Url().omitPadding().encode(applicationServerKey);
        final String subtype = String.format("wp:%s", uuid.toString());
        final var intent = new Intent(ACTION_REGISTRATION);
        intent.setPackage(PACKAGE_NAME_GMS);
        intent.putExtra("scope", "GCM");
        intent.putExtra("sender", vapidServerKey);
        intent.putExtra("subscription", vapidServerKey);
        intent.putExtra("X-subscription", vapidServerKey);
        intent.putExtra("subtype", subtype);
        intent.putExtra("X-subtype", subtype);
        intent.putExtra(
                "app",
                PendingIntent.getBroadcast(
                        context, 0, broadcastIntent, PendingIntent.FLAG_IMMUTABLE));
        final var handler = new RegistrationMessageHandler(Looper.getMainLooper());
        intent.putExtra("google.messenger", new Messenger(handler));
        context.startService(intent);
        return Futures.transform(
                handler.endpointFuture, Optional::of, MoreExecutors.directExecutor());
    }

    private static class RegistrationMessageHandler extends Handler {

        private final SettableFuture<Uri> endpointFuture = SettableFuture.create();

        public RegistrationMessageHandler(final Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull final Message message) {
            if (message.obj instanceof Intent intent) {
                final var registrationId = intent.getStringExtra("registration_id");
                final var error = intent.getStringExtra("error");
                LOGGER.info("bundle " + intent.getExtras());
                if (error != null) {
                    endpointFuture.setException(
                            new IllegalStateException(String.format("Firebase error %s", error)));
                }
                if (Strings.isNullOrEmpty(registrationId)) {
                    endpointFuture.setException(
                            new IllegalStateException("Response did not contain registration id"));
                    return;
                }
                LOGGER.info("registration id: {}", registrationId);
                final var endpoint =
                        Uri.parse(String.format(URI_WEB_PUSH_ENDPOINT, registrationId));
                endpointFuture.set(endpoint);
            } else {
                endpointFuture.setException(
                        new IllegalStateException("Response did not contain intent"));
            }
        }
    }

    @Override
    public boolean isAvailable() {
        final var intent = new Intent(ACTION_REGISTRATION);
        intent.setPackage(PACKAGE_NAME_GMS);
        final var packageManager = context.getPackageManager();
        final var resolveInfo =
                packageManager.resolveService(intent, PackageManager.GET_RESOLVED_FILTER);
        return resolveInfo != null;
    }

    @Override
    public boolean requiresVapid() {
        return true;
    }
}
