package rs.ltt.android.push;

import androidx.annotation.NonNull;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import java.util.UUID;
import okhttp3.HttpUrl;

public interface PushService {

    ListenableFuture<Endpoint> register(final byte[] applicationServerKey, final UUID uuid);

    boolean isAvailable();

    boolean requiresVapid();

    class Endpoint {
        private final HttpUrl url;
        public final String distributor;

        protected Endpoint(final HttpUrl url, final String distributor) {
            Preconditions.checkNotNull(distributor, "Distributor can not be null");
            this.url = url;
            this.distributor = distributor;
        }

        public Optional<HttpUrl> getUrl() {
            return Optional.ofNullable(url);
        }

        @Override
        @NonNull
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("url", url)
                    .add("distributor", distributor)
                    .toString();
        }
    }

    class DeviceIdEndpoint {
        public final UUID deviceClientId;
        public final Endpoint endpoint;

        public DeviceIdEndpoint(final UUID deviceClientId, final Endpoint endpoint) {
            this.deviceClientId = deviceClientId;
            this.endpoint = endpoint;
        }

        public DeviceIdEndpoint(
                final UUID deviceClientId, final HttpUrl httpUrl, final String distributor) {
            this(deviceClientId, new Endpoint(httpUrl, distributor));
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("deviceClientId", deviceClientId)
                    .add("endpoint", endpoint)
                    .toString();
        }
    }
}
