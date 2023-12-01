package rs.ltt.android.push;

import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import java.util.UUID;
import okhttp3.HttpUrl;

public interface PushService {

    ListenableFuture<Optional<Endpoint>> register(
            final byte[] applicationServerKey, final UUID uuid);

    boolean isAvailable();

    boolean requiresVapid();

    class Endpoint {
        public final HttpUrl url;
        public final String distributor;

        public Endpoint(HttpUrl url, String distributor) {
            this.url = url;
            this.distributor = distributor;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("url", url)
                    .add("distributor", distributor)
                    .toString();
        }
    }
}
