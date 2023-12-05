package rs.ltt.android.push;

import android.content.BroadcastReceiver;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.security.GeneralSecurityException;
import java.util.Optional;
import java.util.UUID;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.database.AppDatabase;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.PushSubscription;
import rs.ltt.jmap.common.entity.PushMessage;

public abstract class AbstractPushMessageReceiver extends BroadcastReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPushMessageReceiver.class);

    protected void onReceiveMessage(
            final Context context,
            @NonNull final UUID deviceClientId,
            @Nullable final String distributor,
            final byte[] message) {
        final var pushSubscriptionFuture =
                AppDatabase.getInstance(context)
                        .pushSubscriptionDao()
                        .getPushSubscription(deviceClientId, distributor);
        Futures.addCallback(
                pushSubscriptionFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final PushSubscription pushSubscription) {
                        if (pushSubscription == null) {
                            LOGGER.warn(
                                    "No push subscription found that use a deviceClientId of {}",
                                    deviceClientId);
                            return;
                        }
                        onReceiveMessage(context, pushSubscription, message);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        LOGGER.error(
                                "Could not retrieve account for {} from database",
                                deviceClientId,
                                throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void onReceiveMessage(
            final Context context,
            final PushSubscription pushSubscription,
            final byte[] pushMessage) {
        final var keyMaterialFuture =
                AppDatabase.getInstance(context)
                        .pushSubscriptionDao()
                        .getOptionalKeyMaterial(pushSubscription.id);
        Futures.addCallback(
                keyMaterialFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(
                            final Optional<WebPushMessageEncryption.KeyMaterial> keyMaterial) {
                        onReceiveMessage(
                                context, pushSubscription, keyMaterial.orElse(null), pushMessage);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        LOGGER.error("Could not retrieve key material from database", throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void onReceiveMessage(
            final Context context,
            @NonNull final PushSubscription pushSubscription,
            @Nullable final WebPushMessageEncryption.KeyMaterial keyMaterial,
            final byte[] message) {
        final byte[] plaintextMessage;
        if (keyMaterial == null) {
            plaintextMessage = message;
        } else {
            try {
                plaintextMessage = WebPushMessageEncryption.decrypt(message, keyMaterial);
            } catch (final GeneralSecurityException e) {
                LOGGER.warn("Could not decrypt push message", e);
                return;
            }
        }
        final PushMessage pushMessage;
        try {
            pushMessage = PushManager.deserialize(plaintextMessage);
        } catch (final Exception e) {
            LOGGER.warn("received improperly formatted push message", e);
            return;
        }
        final var pushManager = new PushManager(context);
        pushManager.onMessageReceived(pushSubscription, pushMessage);
    }

    protected void onReceiveNewEndpoint(
            final Context context,
            final UUID deviceClientId,
            @NonNull final HttpUrl url,
            @Nullable final String distributor) {
        final var pushSubscriptionFuture =
                AppDatabase.getInstance(context)
                        .pushSubscriptionDao()
                        .getPushSubscription(deviceClientId, distributor);
        Futures.addCallback(
                pushSubscriptionFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final PushSubscription pushSubscription) {
                        if (pushSubscription == null) {
                            LOGGER.warn(
                                    "No push subscription found that use a deviceClientId of {}",
                                    deviceClientId);
                            return;
                        }
                        final var deviceIdEndpoint =
                                new PushService.DeviceIdEndpoint(
                                        pushSubscription.deviceClientId,
                                        url,
                                        pushSubscription.distributor);
                        onReceiveNewEndpoint(context, pushSubscription, deviceIdEndpoint);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        LOGGER.error(
                                "Could not retrieve account for {} from database",
                                deviceClientId,
                                throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }

    protected void onReceiveNewEndpoint(
            final Context context,
            final PushSubscription pushSubscription,
            final PushService.DeviceIdEndpoint deviceIdEndpoint) {
        LOGGER.info("Received new push endpoint {}", deviceIdEndpoint);
        final var anyAccountFuture =
                AppDatabase.getInstance(context)
                        .accountDao()
                        .getAnyAccountFuture(pushSubscription.credentialsId);
        Futures.addCallback(
                anyAccountFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(AccountWithCredentials account) {
                        final var pushManager = new PushManager(context);
                        pushManager.register(account, deviceIdEndpoint);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {}
                },
                MoreExecutors.directExecutor());
    }

    protected void onReceiveRegistrationFailedOrUnregistered(
            final Context context, final UUID deviceClientId, final String distributor) {
        final var database = AppDatabase.getInstance(context);
        final var pushSubscriptionFuture =
                AppDatabase.getInstance(context)
                        .pushSubscriptionDao()
                        .getPushSubscription(deviceClientId, distributor);
        final var credentialsFuture =
                Futures.transformAsync(
                        pushSubscriptionFuture,
                        pushSubscription -> {
                            if (pushSubscription == null) {
                                throw new IllegalStateException(
                                        String.format(
                                                "No push subscription found that uses a"
                                                        + " deviceClientId of %s",
                                                deviceClientId));
                            }
                            return database.accountDao()
                                    .getCredentials(pushSubscription.credentialsId);
                        },
                        MoreExecutors.directExecutor());
        Futures.addCallback(
                credentialsFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(
                            @Nullable AccountWithCredentials.Credentials credentials) {
                        if (credentials == null) {
                            LOGGER.error(
                                    "No credentials found for deviceClientId {}", deviceClientId);
                            return;
                        }
                        final var pushManager = new PushManager(context);
                        pushManager.scheduleRecurringMainQueryWorkers(credentials);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        LOGGER.error(
                                "unable to unregister deviceClientId {}",
                                deviceClientId,
                                throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }
}
