package rs.ltt.android.push;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.base.Strings;
import java.util.UUID;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnifiedPushMessageReceiver extends AbstractPushMessageReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnifiedPushMessageReceiver.class);

    @Override
    public void onReceive(final Context context, final Intent intent) {
        LOGGER.info("Received {}", Strings.nullToEmpty(intent.getAction()));
        final String token = intent.getStringExtra(UnifiedPushService.EXTRA_TOKEN);
        if (Strings.isNullOrEmpty(token)) {
            return;
        }
        final UUID clientDeviceId;
        try {
            clientDeviceId = UUID.fromString(token);
        } catch (final IllegalArgumentException e) {
            LOGGER.warn("clientDeviceId is not a valid UUID");
            return;
        }
        final var distributorVerification =
                intent.getParcelableExtra(UnifiedPushService.EXTRA_DISTRIBUTOR);
        final String distributor;
        if (distributorVerification instanceof PendingIntent pendingIntent) {
            distributor = pendingIntent.getIntentSender().getCreatorPackage();
        } else {
            distributor = null;
        }
        switch (Strings.nullToEmpty(intent.getAction())) {
            case UnifiedPushService.ACTION_MESSAGE ->
                    onReceiveMessage(
                            context,
                            clientDeviceId,
                            intent.getByteArrayExtra(UnifiedPushService.EXTRA_BYTE_MESSAGE),
                            distributor);
            case UnifiedPushService.ACTION_NEW_ENDPOINT ->
                    onReceiveNewEndpoint(
                            context,
                            clientDeviceId,
                            intent.getStringExtra(UnifiedPushService.EXTRA_ENDPOINT),
                            distributor);
            case UnifiedPushService.ACTION_REGISTRATION_FAILED,
                            UnifiedPushService.ACTION_UNREGISTERED ->
                    onReceiveRegistrationFailedOrUnregistered(context, clientDeviceId, distributor);
        }
    }

    private void onReceiveNewEndpoint(
            final Context context,
            final UUID deviceClientId,
            final String endpoint,
            @Nullable final String distributor) {
        if (Strings.isNullOrEmpty(endpoint)) {
            return;
        }
        final HttpUrl url;
        try {
            url = HttpUrl.get(endpoint);
        } catch (final IllegalArgumentException e) {
            LOGGER.warn("Received new endpoint but url is not a valid", e);
            return;
        }
        this.onReceiveNewEndpoint(context, deviceClientId, url, distributor);
    }

    private void onReceiveMessage(
            final Context context,
            @NonNull final UUID deviceClientId,
            @Nullable final byte[] byteMessage,
            @Nullable final String distributor) {
        if (byteMessage == null || byteMessage.length == 0) {
            return;
        }
        LOGGER.info("Received push message for {} ({} bytes)", deviceClientId, byteMessage.length);
        this.onReceiveMessage(context, deviceClientId, distributor, byteMessage);
    }
}
