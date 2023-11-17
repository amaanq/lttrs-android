package rs.ltt.android.push;

import android.net.Uri;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import java.util.UUID;

public interface PushService {

    ListenableFuture<Optional<Uri>> register(final byte[] applicationServerKey, final UUID uuid);

    boolean isAvailable();

    boolean requiresVapid();
}
