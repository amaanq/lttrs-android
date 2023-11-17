package rs.ltt.android.push;

import android.content.BroadcastReceiver;
import android.content.Context;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.jmap.common.entity.PushMessage;

public abstract class AbstractPushMessageReceiver extends BroadcastReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPushMessageReceiver.class);

    protected void onReceive(
            final Context context, final UUID clientDeviceId, final byte[] message) {
        final PushMessage pushMessage;
        try {
            pushMessage = PushManager.deserialize(message);
        } catch (final Exception e) {
            LOGGER.warn("received improperly formatted push message", e);
            return;
        }
        final var pushManager = new PushManager(context);
        pushManager.onMessageReceived(clientDeviceId, pushMessage);
    }
}
