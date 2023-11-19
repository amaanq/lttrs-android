package rs.ltt.android.push;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import androidx.annotation.NonNull;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnifiedPushService implements PushService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnifiedPushService.class);

    public static final String ACTION_REGISTER = "org.unifiedpush.android.distributor.REGISTER";

    public static final String ACTION_MESSAGE = "org.unifiedpush.android.connector.MESSAGE";

    public static final String ACTION_FEATURE_BYTES_MESSAGE =
            "org.unifiedpush.android.distributor.feature.BYTES_MESSAGE";
    public static final String ACTION_FEATURE_MESSENGER =
            "org.unifiedpush.android.distributor.feature.MESSENGER";
    public static final String ACTION_FEATURE_APP_VALIDATION =
            "org.unifiedpush.android.distributor.feature.APP_VALIDATION";
    public static final String ACTION_NEW_ENDPOINT =
            "org.unifiedpush.android.connector.NEW_ENDPOINT";
    public static final String ACTION_REGISTRATION_FAILED =
            "org.unifiedpush.android.connector.REGISTRATION_FAILED";

    public static final String EXTRA_BYTE_MESSAGE = "bytesMessage";
    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_ENDPOINT = "endpoint";

    private final Context context;

    public UnifiedPushService(final Context context) {
        this.context = context;
    }

    @Override
    public ListenableFuture<Optional<Uri>> register(
            final byte[] applicationServerKey, final UUID uuid) {
        final var broadcastToApp = new Intent();
        broadcastToApp.setPackage(context.getPackageName());
        final var pendingIntent =
                PendingIntent.getBroadcast(
                        context, 0, broadcastToApp, PendingIntent.FLAG_IMMUTABLE);
        final var distributor = Iterables.getFirst(getSupportedDistributors(), null);
        if (distributor == null) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("No UnifiedPush distributor found"));
        }
        final var broadcast = new Intent(ACTION_REGISTER);
        broadcast.setPackage(distributor.packageName);
        broadcast.putExtra("app", pendingIntent);
        broadcast.putExtra("application", context.getPackageName());
        broadcast.putExtra("token", uuid.toString());
        if (distributor.messenger()) {
            final var handler = new RegistrationMessageHandler(Looper.getMainLooper());
            final var messenger = new Messenger(handler);
            broadcast.putExtra("messenger", messenger);
            this.context.sendBroadcast(broadcast);
            return Futures.transform(
                    handler.endpointFuture, Optional::of, MoreExecutors.directExecutor());
        } else {
            this.context.sendBroadcast(broadcast);
            return Futures.immediateFuture(Optional.empty());
        }
    }

    private static class RegistrationMessageHandler extends Handler {

        private final SettableFuture<Uri> endpointFuture = SettableFuture.create();

        public RegistrationMessageHandler(final Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull final Message message) {
            if (message.obj instanceof Intent intent) {
                if (ACTION_NEW_ENDPOINT.equals(intent.getAction())) {
                    final var endpoint = intent.getStringExtra("endpoint");
                    LOGGER.info("received endpoint {}", endpoint);
                    endpointFuture.set(Uri.parse(endpoint));
                } else if (ACTION_REGISTRATION_FAILED.equalsIgnoreCase(intent.getAction())) {
                    endpointFuture.setException(new IllegalStateException("Registration failed"));
                } else {
                    endpointFuture.setException(
                            new IllegalStateException(
                                    String.format(
                                            "Received unexpected action %s", intent.getAction())));
                }
            } else {
                endpointFuture.setException(
                        new IllegalStateException("Message had no usable intent"));
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return getSupportedDistributors().size() > 0;
    }

    @Override
    public boolean requiresVapid() {
        return false;
    }

    private List<Distributor> getDistributors() {
        final var distributorBuilder = new ImmutableList.Builder<Distributor>();
        final var packageManager = context.getPackageManager();
        final var intent = new Intent(ACTION_REGISTER);
        final var info =
                packageManager.queryBroadcastReceivers(intent, PackageManager.GET_RESOLVED_FILTER);
        for (final ResolveInfo resolveInfo : info) {
            final var packageName = resolveInfo.activityInfo.applicationInfo.packageName;
            final var actions = ImmutableList.copyOf(resolveInfo.filter.actionsIterator());
            final var distributor = new Distributor(packageName, actions);
            LOGGER.info("Discovered UnifiedPush distributor {}", distributor);
            distributorBuilder.add(distributor);
        }
        return distributorBuilder.build();
    }

    private List<Distributor> getSupportedDistributors() {
        return getDistributors().stream()
                .filter(d -> d.appValidation() && d.bytesMessage())
                .toList();
    }

    private static class Distributor {

        public final String packageName;
        private final List<String> features;

        private Distributor(String packageName, List<String> features) {
            this.packageName = packageName;
            this.features = features;
        }

        public boolean bytesMessage() {
            return this.features.contains(ACTION_FEATURE_BYTES_MESSAGE);
        }

        public boolean appValidation() {
            return this.features.contains(ACTION_FEATURE_APP_VALIDATION);
        }

        public boolean messenger() {
            return this.features.contains(ACTION_FEATURE_MESSENGER);
        }

        @Override
        @NonNull
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("packageName", packageName)
                    .add("features", features)
                    .toString();
        }
    }
}
