package rs.ltt.android.push;

import android.content.Context;
import android.content.Intent;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FirebasePushMessageReceiver extends AbstractPushMessageReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(FirebasePushMessageReceiver.class);

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final var extras = intent.getExtras();
        if (extras == null) {
            LOGGER.warn("Received incomplete push message intent. missing bundle");
            return;
        }
        final var rawData = extras.getByteArray("rawData");
        final var subtype = extras.getString("subtype");
        if (rawData == null || rawData.length == 0 || Strings.isNullOrEmpty(subtype)) {
            LOGGER.warn("Received incomplete push message intent. missing raw data or subtype");
            return;
        }
        final var subtypeParts = Splitter.on(':').splitToList(subtype);
        final String clientDeviceId;
        if (subtypeParts.size() == 2 && subtypeParts.get(0).equals("wp")) {
            clientDeviceId = subtypeParts.get(1);
        } else {
            LOGGER.warn("Received invalid push message. could not extract device id");
            return;
        }
        final UUID clientDeviceUuid;
        try {
            clientDeviceUuid = UUID.fromString(clientDeviceId);
        } catch (final IllegalArgumentException e) {
            LOGGER.warn("Received invalid push message. clientDeviceId is not a valid UUID");
            return;
        }
        LOGGER.info("Received push message for {} ({} bytes)", clientDeviceUuid, rawData.length);
        this.onReceiveMessage(context, clientDeviceUuid, rawData);
    }
}
