package rs.ltt.android.push;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import androidx.annotation.NonNull;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.jmap.client.Services;

public class UnifiedPushService implements PushService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnifiedPushService.class);

    public static final String ACTION_REGISTER = "org.unifiedpush.android.distributor.REGISTER";

    public static final String ACTION_UNREGISTER = "org.unifiedpush.android.distributor.UNREGISTER";

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

    // this action is only used in 'messenger' communication to tell the app that a registration is
    // probably fine but can not be processed right now; for example due to spotty internet
    public static final String ACTION_REGISTRATION_DELAYED =
            "org.unifiedpush.android.connector.REGISTRATION_DELAYED";

    public static final String EXTRA_BYTE_MESSAGE = "bytesMessage";
    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_ENDPOINT = "endpoint";
    public static final String EXTRA_DISTRIBUTOR = "distributor";
    public static final String EXTRA_MESSAGE = "message";

    private final Context context;

    public UnifiedPushService(final Context context) {
        this.context = context;
    }

    public static void unregister(
            final Context context, final String distributor, final UUID deviceClientId) {
        final var intent = new Intent(ACTION_UNREGISTER);
        intent.setPackage(distributor);
        intent.putExtra(EXTRA_TOKEN, deviceClientId.toString());
        context.sendBroadcast(intent);
    }

    @Override
    public ListenableFuture<Endpoint> register(final byte[] applicationServerKey, final UUID uuid) {
        final var distributor = Iterables.getFirst(getSupportedDistributors(), null);
        if (distributor == null) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("No UnifiedPush distributor found"));
        }
        final var broadcast = new Intent(ACTION_REGISTER);
        broadcast.setPackage(distributor.packageName);
        if (distributor.appValidation()) {
            final var broadcastToApp = new Intent();
            broadcastToApp.setPackage(context.getPackageName());
            final var pendingIntent =
                    PendingIntent.getBroadcast(
                            context, 0, broadcastToApp, PendingIntent.FLAG_IMMUTABLE);
            broadcast.putExtra("app", pendingIntent);
        } else {
            broadcast.putExtra("application", context.getPackageName());
        }
        broadcast.putExtra("token", uuid.toString());
        final var features = new ArrayList<String>();
        features.add(ACTION_FEATURE_BYTES_MESSAGE);
        broadcast.putStringArrayListExtra("features", features);
        if (distributor.messenger()) {
            final var handler =
                    new RegistrationMessageHandler(Looper.getMainLooper(), distributor.packageName);
            final var messenger = new Messenger(handler);
            broadcast.putExtra("messenger", messenger);
            this.context.sendBroadcast(broadcast);
            final var endpointWithTimeout =
                    Futures.withTimeout(
                            handler.endpointFuture,
                            30,
                            TimeUnit.SECONDS,
                            Services.SCHEDULED_EXECUTOR_SERVICE);
            return Futures.catching(
                    endpointWithTimeout,
                    TimeoutException.class,
                    ex -> new Endpoint(null, distributor.packageName),
                    MoreExecutors.directExecutor());
        } else {
            this.context.sendBroadcast(broadcast);
            return Futures.immediateFuture(new Endpoint(null, distributor.packageName));
        }
    }

    private static class RegistrationMessageHandler extends Handler {

        private final SettableFuture<Endpoint> endpointFuture = SettableFuture.create();

        private final String distributor;

        public RegistrationMessageHandler(final Looper looper, final String distributor) {
            super(looper);
            this.distributor = distributor;
        }

        @Override
        public void handleMessage(@NonNull final Message message) {
            if (message.obj instanceof Intent intent) {
                final String action = intent.getAction();
                if (ACTION_NEW_ENDPOINT.equals(action)) {
                    final var uri = intent.getStringExtra("endpoint");
                    if (Strings.isNullOrEmpty(uri)) {
                        endpointFuture.setException(
                                new IllegalStateException(
                                        "Registration intent did not contain uri"));
                        return;
                    }
                    final HttpUrl url;
                    try {
                        url = HttpUrl.get(uri);
                    } catch (final IllegalArgumentException e) {
                        endpointFuture.setException(e);
                        return;
                    }
                    final Endpoint endpoint = new Endpoint(url, distributor);
                    LOGGER.info("received endpoint {}", endpoint);
                    endpointFuture.set(endpoint);
                } else if (ACTION_REGISTRATION_FAILED.equalsIgnoreCase(action)) {
                    final var errorMessage = intent.getStringExtra(EXTRA_MESSAGE);
                    LOGGER.error(
                            "Registration failed with '{}'", Strings.nullToEmpty(errorMessage));
                    endpointFuture.setException(
                            new IllegalStateException(
                                    String.format(
                                            "Registration failed with '%s'",
                                            Strings.nullToEmpty(errorMessage))));
                } else if (ACTION_REGISTRATION_DELAYED.equalsIgnoreCase(action)) {
                    final var errorMessage = intent.getStringExtra(EXTRA_MESSAGE);
                    LOGGER.error(
                            "Registration delayed due to '{}'", Strings.nullToEmpty(errorMessage));
                    endpointFuture.set(new Endpoint(null, distributor));
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
        return getDistributors().stream().filter(Distributor::bytesMessage).toList();
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
