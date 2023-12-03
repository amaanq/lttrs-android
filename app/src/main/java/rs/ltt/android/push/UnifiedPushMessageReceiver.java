package rs.ltt.android.push;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;
import com.google.common.base.Strings;
import java.util.UUID;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnifiedPushMessageReceiver extends AbstractPushMessageReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnifiedPushMessageReceiver.class);

    @Override
    public void onReceive(final Context context, final Intent intent) {
        switch (Strings.nullToEmpty(intent.getAction())) {
            case UnifiedPushService.ACTION_MESSAGE -> onReceiveMessage(
                    context,
                    intent.getStringExtra(UnifiedPushService.EXTRA_TOKEN),
                    intent.getByteArrayExtra(UnifiedPushService.EXTRA_BYTE_MESSAGE),
                    intent.getParcelableExtra(UnifiedPushService.EXTRA_DISTRIBUTOR));
            case UnifiedPushService.ACTION_NEW_ENDPOINT -> onReceiveNewEndpoint(
                    context,
                    intent.getStringExtra(UnifiedPushService.EXTRA_TOKEN),
                    intent.getStringExtra(UnifiedPushService.EXTRA_ENDPOINT),
                    intent.getParcelableExtra(UnifiedPushService.EXTRA_DISTRIBUTOR));
        }
    }

    private void onReceiveNewEndpoint(
            final Context context,
            final String token,
            final String endpoint,
            final Parcelable distributorVerification) {
        if (Strings.isNullOrEmpty(token) || Strings.isNullOrEmpty(endpoint)) {
            return;
        }
        final UUID clientDeviceId;
        try {
            clientDeviceId = UUID.fromString(token);
        } catch (final IllegalArgumentException e) {
            LOGGER.warn("Received new endpoint but clientDeviceId is not a valid UUID");
            return;
        }
        final HttpUrl url;
        try {
            url = HttpUrl.get(endpoint);
        } catch (final IllegalArgumentException e) {
            LOGGER.warn("Received new endpoint but url is not a valid", e);
            return;
        }
        final String distributor;
        if (distributorVerification instanceof PendingIntent pendingIntent) {
            distributor = pendingIntent.getIntentSender().getCreatorPackage();
        } else {
            distributor = null;
        }

        this.onReceiveNewEndpoint(context, clientDeviceId, url, distributor);
    }

    private void onReceiveMessage(
            final Context context,
            final String token,
            final byte[] byteMessage,
            final Parcelable distributorVerification) {
        if (Strings.isNullOrEmpty(token) || byteMessage == null || byteMessage.length == 0) {
            return;
        }
        final UUID clientDeviceId;
        try {
            clientDeviceId = UUID.fromString(token);
        } catch (final IllegalArgumentException e) {
            LOGGER.warn("Received invalid push message. clientDeviceId is not a valid UUID");
            return;
        }
        final String distributor;
        if (distributorVerification instanceof PendingIntent pendingIntent) {
            distributor = pendingIntent.getIntentSender().getCreatorPackage();
        } else {
            distributor = null;
        }
        LOGGER.info("Received push message for {} ({} bytes)", clientDeviceId, byteMessage.length);
        this.onReceiveMessage(context, clientDeviceId, distributor, byteMessage);
    }
}
