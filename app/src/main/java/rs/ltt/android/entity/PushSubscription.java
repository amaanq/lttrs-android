package rs.ltt.android.entity;

import java.util.UUID;

public class PushSubscription {
    public final long id;
    public final Long credentialsId;

    public final UUID deviceClientId;

    public final String distributor;

    public PushSubscription(long id, Long credentialsId, UUID deviceClientId, String distributor) {
        this.id = id;
        this.credentialsId = credentialsId;
        this.deviceClientId = deviceClientId;
        this.distributor = distributor;
    }
}
