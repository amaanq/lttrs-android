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
import okhttp3.HttpUrl;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.PushSubscriptionEntity;
import rs.ltt.android.push.WebPushMessageEncryption;

@Dao
public abstract class PushSubscriptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insert(PushSubscriptionEntity pushSubscription);

    public void insert(
            final AccountWithCredentials.Credentials credentials,
            final String pushSubscriptionId,
            final HttpUrl url,
            final WebPushMessageEncryption.KeyMaterial keyMaterial,
            final Instant expires) {
        final var entity = new PushSubscriptionEntity();
        entity.credentialsId = credentials.getId();
        entity.pushSubscriptionId = pushSubscriptionId;
        entity.url = url;
        entity.keyMaterial = keyMaterial;
        entity.expires = expires;
        insert(entity);
    }

    @Query(
            "UPDATE push_subscription SET verificationCode=:verificationCode WHERE"
                + " credentialsId=(SELECT credentialsId FROM account WHERE id=:accountId LIMIT 1)"
                + " AND pushSubscriptionId=:pushSubscriptionId")
    public abstract void setVerificationCode(
            long accountId, final String pushSubscriptionId, final String verificationCode);

    @Query("SELECT pushSubscriptionId FROM push_subscription WHERE credentialsId=:credentialsId")
    public abstract ListenableFuture<List<String>> getExistingSubscriptionIds(
            final long credentialsId);

    @Query(
            "SELECT publicKey,privateKey,authenticationSecret FROM push_subscription WHERE"
                    + " credentialsId=:credentialsId LIMIT 1")
    abstract ListenableFuture<WebPushMessageEncryption.KeyMaterial> getKeyMaterial(
            long credentialsId);

    public ListenableFuture<Optional<WebPushMessageEncryption.KeyMaterial>> getOptionalKeyMaterial(
            long credentialsId) {
        return Futures.transform(
                getKeyMaterial(credentialsId),
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
