package rs.ltt.android.entity;

import androidx.annotation.NonNull;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.time.Instant;
import java.util.UUID;
import okhttp3.HttpUrl;
import rs.ltt.android.push.WebPushMessageEncryption;

@Entity(
        tableName = "push_subscription",
        foreignKeys = {
            @ForeignKey(
                    entity = CredentialsEntity.class,
                    parentColumns = {"id"},
                    childColumns = {"credentialsId"},
                    onDelete = ForeignKey.CASCADE)
        },
        indices = {
            @Index(
                    value = {"credentialsId", "deviceClientId"},
                    unique = true)
        })
public class PushSubscriptionEntity {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long credentialsId;

    @NonNull public UUID deviceClientId;

    @NonNull public String distributor;
    public String pushSubscriptionId;

    public HttpUrl url;

    @Embedded public WebPushMessageEncryption.KeyMaterial keyMaterial;

    public String verificationCode;

    public Instant expires;
}
