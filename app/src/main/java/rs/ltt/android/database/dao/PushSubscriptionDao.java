package rs.ltt.android.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import okhttp3.HttpUrl;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.PushSubscription;
import rs.ltt.android.entity.PushSubscriptionEntity;
import rs.ltt.android.push.WebPushMessageEncryption;

@Dao
public abstract class PushSubscriptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insert(PushSubscriptionEntity pushSubscription);

    public void insert(
            final AccountWithCredentials.Credentials credentials,
            final UUID deviceClientId,
            final String distributor) {
        final var entity = new PushSubscriptionEntity();
        entity.credentialsId = credentials.getId();
        entity.deviceClientId = deviceClientId;
        entity.distributor = distributor;
        insert(entity);
    }

    @Query(
            "UPDATE push_subscription SET"
                + " pushSubscriptionId=:pushSubscriptionId,url=:httpUrl,publicKey=:publicKey,privateKey=:privateKey,authenticationSecret=:authenticationSecret,expires=:expires"
                + " WHERE credentialsId=:credentialsId AND deviceClientId=:deviceClientId")
    protected abstract int update(
            final long credentialsId,
            final UUID deviceClientId,
            String pushSubscriptionId,
            HttpUrl httpUrl,
            byte[] publicKey,
            byte[] privateKey,
            byte[] authenticationSecret,
            Instant expires);

    public boolean update(
            AccountWithCredentials.Credentials credentials,
            UUID deviceClientId,
            String pushSubscriptionId,
            HttpUrl httpUrl,
            WebPushMessageEncryption.KeyMaterial keyMaterial,
            Instant expires) {
        return update(
                        credentials.getId(),
                        deviceClientId,
                        pushSubscriptionId,
                        httpUrl,
                        keyMaterial.publicKey,
                        keyMaterial.privateKey,
                        keyMaterial.authenticationSecret,
                        expires)
                >= 1;
    }

    @Query(
            "UPDATE push_subscription SET verificationCode=:verificationCode WHERE"
                    + " credentialsId=:credentialsId"
                    + " AND pushSubscriptionId=:pushSubscriptionId")
    public abstract void setVerificationCode(
            long credentialsId, final String pushSubscriptionId, final String verificationCode);

    @Query(
            "SELECT id,credentialsId,deviceClientId,distributor,pushSubscriptionId FROM"
                    + " push_subscription WHERE credentialsId=:credentialsId")
    public abstract List<PushSubscription> getPushSubscriptions(Long credentialsId);

    @Query(
            "SELECT pushSubscriptionId FROM push_subscription WHERE credentialsId=:credentialsId"
                    + " AND pushSubscriptionId IS NOT NULL")
    public abstract ListenableFuture<List<String>> getExistingSubscriptionIds(
            final long credentialsId);

    @Query(
            "SELECT id,credentialsId,deviceClientId,distributor,pushSubscriptionId FROM"
                    + " push_subscription WHERE deviceClientId=:deviceClientId")
    protected abstract ListenableFuture<PushSubscription> getPushSubscriptionInternal(
            final UUID deviceClientId);

    @Query(
            "SELECT id,credentialsId,deviceClientId,distributor,pushSubscriptionId FROM"
                    + " push_subscription WHERE deviceClientId=:deviceClientId AND"
                    + " distributor=:distributor")
    protected abstract ListenableFuture<PushSubscription> getPushSubscriptionInternal(
            final UUID deviceClientId, final String distributor);

    public ListenableFuture<PushSubscription> getPushSubscription(
            final UUID deviceClientId, final String distributor) {
        if (distributor == null) {
            return getPushSubscriptionInternal(deviceClientId);
        } else {
            return getPushSubscriptionInternal(deviceClientId, distributor);
        }
    }

    @Query(
            "SELECT publicKey,privateKey,authenticationSecret FROM push_subscription WHERE"
                    + " id=:id LIMIT 1")
    abstract ListenableFuture<WebPushMessageEncryption.KeyMaterial> getKeyMaterial(long id);

    public ListenableFuture<Optional<WebPushMessageEncryption.KeyMaterial>> getOptionalKeyMaterial(
            long id) {
        return Futures.transform(
                getKeyMaterial(id),
                km -> {
                    if (km != null
                            && km.privateKey != null
                            && km.privateKey.length > 0
                            && km.publicKey != null
                            && km.publicKey.length > 0) {
                        return Optional.of(km);
                    } else {
                        return Optional.empty();
                    }
                },
                MoreExecutors.directExecutor());
    }
}
