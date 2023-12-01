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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.database.AppDatabase;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.jmap.common.entity.PushMessage;

public abstract class AbstractPushMessageReceiver extends BroadcastReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPushMessageReceiver.class);

    protected void onReceiveMessage(
            final Context context, final UUID deviceClientId, final byte[] message) {
        final var accountFuture =
                AppDatabase.getInstance(context).accountDao().getAnyAccount(deviceClientId);
        Futures.addCallback(
                accountFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final AccountWithCredentials account) {
                        if (account == null) {
                            LOGGER.warn(
                                    "No credentials found that use a deviceClientId of {}",
                                    deviceClientId);
                            return;
                        }
                        onReceiveMessage(context, account, message);
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
            final Context context, final AccountWithCredentials account, final byte[] pushMessage) {
        final var keyMaterialFuture =
                AppDatabase.getInstance(context)
                        .pushSubscriptionDao()
                        .getOptionalKeyMaterial(account.getCredentials().getId());
        Futures.addCallback(
                keyMaterialFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(
                            final Optional<WebPushMessageEncryption.KeyMaterial> keyMaterial) {
                        onReceiveMessage(context, account, keyMaterial.orElse(null), pushMessage);
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
            @NonNull final AccountWithCredentials account,
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
        pushManager.onMessageReceived(account, pushMessage);
    }

    protected void onReceiveNewEndpoint(
            final Context context, final UUID deviceClientId, final PushService.Endpoint endpoint) {
        final var accountFuture =
                AppDatabase.getInstance(context).accountDao().getAnyAccount(deviceClientId);
        Futures.addCallback(
                accountFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final AccountWithCredentials account) {
                        if (account == null) {
                            LOGGER.warn(
                                    "No credentials found that use a deviceClientId of {}",
                                    deviceClientId);
                            return;
                        }
                        onReceiveNewEndpoint(context, account, endpoint);
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
            final AccountWithCredentials account,
            final PushService.Endpoint endpoint) {
        LOGGER.info("Received new push endpoint {} for {}", endpoint, account.getDeviceClientId());
        final var pushManager = new PushManager(context);
        pushManager.register(account, endpoint);
    }
}
