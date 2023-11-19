package rs.ltt.android.push;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.google.common.base.Strings;
import java.util.UUID;
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
                    intent.getByteArrayExtra(UnifiedPushService.EXTRA_BYTE_MESSAGE));
            case UnifiedPushService.ACTION_NEW_ENDPOINT -> onReceiveNewEndpoint(
                    context,
                    intent.getStringExtra(UnifiedPushService.EXTRA_TOKEN),
                    intent.getStringExtra(UnifiedPushService.EXTRA_ENDPOINT));
        }
    }

    private void onReceiveNewEndpoint(
            final Context context, final String token, final String endpoint) {
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
        this.onReceiveNewEndpoint(context, clientDeviceId, Uri.parse(endpoint));
    }

    private void onReceiveMessage(
            final Context context, final String token, final byte[] byteMessage) {
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
        LOGGER.info("Received push message for {} ({} bytes)", clientDeviceId, byteMessage.length);
        this.onReceiveMessage(context, clientDeviceId, byteMessage);
    }
}
