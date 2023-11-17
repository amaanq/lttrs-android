package rs.ltt.android.push;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FirebaseRegistrationReceiver extends BroadcastReceiver {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(FirebaseRegistrationReceiver.class);

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final var registrationId = intent.getStringExtra("registration_id");
        LOGGER.info("registration id: {}", registrationId);
        LOGGER.info("received FCM registration token {}", intent.getExtras());
        final var pushManager = new PushManager(context);
    }
}
