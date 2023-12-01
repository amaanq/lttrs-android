package rs.ltt.android.worker;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.security.GeneralSecurityException;
import java.util.Collection;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.MuaPool;
import rs.ltt.android.database.AppDatabase;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.push.PushManager;
import rs.ltt.android.push.PushService;
import rs.ltt.android.push.WebPushMessageEncryption;
import rs.ltt.jmap.client.JmapClient;
import rs.ltt.jmap.client.MethodResponses;
import rs.ltt.jmap.common.entity.PushSubscription;
import rs.ltt.jmap.common.method.call.core.SetPushSubscriptionMethodCall;
import rs.ltt.jmap.common.method.response.core.SetPushSubscriptionMethodResponse;

public class PushRegistrationWorker extends ListenableWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushRegistrationWorker.class);

    private static final String KEY_ACCOUNT = "account";
    private static final String KEY_URI = "uri";
    private static final String KEY_DISTRIBUTOR = "distributor";
    private final Long account;
    private final String uri;
    private final String distributor;

    public PushRegistrationWorker(
            @NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        final Data data = getInputData();
        if (data.hasKeyWithValueOfType(KEY_ACCOUNT, Long.class)) {
            this.account = data.getLong(KEY_ACCOUNT, 0L);
        } else {
            this.account = null;
        }
        this.uri = data.getString(KEY_URI);
        this.distributor = data.getString(KEY_DISTRIBUTOR);
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        if (this.account == null || Strings.isNullOrEmpty(this.uri)) {
            LOGGER.error("Missing input parameters (account and uri)");
            return Futures.immediateFuture(Result.failure());
        }
        final var accountFuture =
                AppDatabase.getInstance(getApplicationContext())
                        .accountDao()
                        .getAccountFuture(this.account);
        final var registrationFuture =
                Futures.transformAsync(
                        accountFuture,
                        account -> register(account, this.distributor, this.uri),
                        MoreExecutors.directExecutor());
        final var resultFuture =
                Futures.transform(
                        registrationFuture,
                        success ->
                                Boolean.TRUE.equals(success) ? Result.success() : Result.failure(),
                        MoreExecutors.directExecutor());
        return Futures.catching(
                resultFuture,
                Exception.class,
                ex -> {
                    LOGGER.error("Could not register PushSubscription", ex);
                    if (AbstractMuaWorker.isNetworkIssue(ex)) {
                        return Result.retry();
                    } else {
                        final var pushManager = new PushManager(getApplicationContext());
                        pushManager.scheduleRecurringMainQueryWorkers(account);
                        return Result.failure();
                    }
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Boolean> register(
            final AccountWithCredentials account, final String distributor, final String uri) {
        final HttpUrl httpUrl;
        try {
            httpUrl = HttpUrl.get(uri);
        } catch (final Exception e) {
            return Futures.immediateFailedFuture(e);
        }
        return register(account, distributor, httpUrl);
    }

    private ListenableFuture<Boolean> register(
            final AccountWithCredentials account, final String distributor, final HttpUrl httpUrl) {
        final WebPushMessageEncryption.KeyMaterial keyMaterial;
        try {
            keyMaterial = WebPushMessageEncryption.generateKeyMaterial();
        } catch (final GeneralSecurityException e) {
            return Futures.immediateFailedFuture(e);
        }
        final var existingSubscriptionIds =
                AppDatabase.getInstance(getApplicationContext())
                        .pushSubscriptionDao()
                        .getExistingSubscriptionIds(account.getCredentials().getId());
        return Futures.transformAsync(
                existingSubscriptionIds,
                ids -> register(account, distributor, httpUrl, keyMaterial, ids),
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Boolean> register(
            final AccountWithCredentials account,
            final String distributor,
            final HttpUrl httpUrl,
            final WebPushMessageEncryption.KeyMaterial keyMaterial,
            final Collection<String> existingSubscriptionIds) {
        final PushSubscription pushSubscription =
                PushSubscription.builder()
                        .deviceClientId(account.getDeviceClientId().toString())
                        .keys(keyMaterial.asKeys())
                        .url(httpUrl.toString())
                        .build();
        LOGGER.info("attempting push subscription {}", pushSubscription);
        final JmapClient jmapClient =
                MuaPool.getInstance(getApplicationContext(), account).getJmapClient();
        final SetPushSubscriptionMethodCall setPushSubscription =
                SetPushSubscriptionMethodCall.builder()
                        .destroy(existingSubscriptionIds.toArray(new String[0]))
                        .create(ImmutableMap.of("ps0", pushSubscription))
                        .build();
        final ListenableFuture<MethodResponses> methodResponsesFuture =
                jmapClient.call(setPushSubscription);
        return Futures.transform(
                methodResponsesFuture,
                methodResponses -> {
                    final SetPushSubscriptionMethodResponse response =
                            methodResponses.getMain(SetPushSubscriptionMethodResponse.class);
                    final var pushManager = new PushManager(getApplicationContext());
                    final var created = response.getCreated();
                    if (created != null && created.containsKey("ps0")) {
                        final var ps = created.get("ps0");
                        final var pushSubscriptionId = ps == null ? null : ps.getId();
                        if (Strings.isNullOrEmpty(pushSubscriptionId)) {
                            LOGGER.warn("No pushSubscriptionId found in server response");
                            return false;
                        }
                        final var expires = ps.getExpires();
                        // expires MUST be 48h in the future. check that it is at least 36h so we
                        // don’t loop when we try to update this every 24h
                        // make credentialsId unique
                        AppDatabase.getInstance(getApplicationContext())
                                .pushSubscriptionDao()
                                .insert(
                                        account.getCredentials(),
                                        distributor,
                                        pushSubscriptionId,
                                        httpUrl,
                                        keyMaterial,
                                        expires);
                        pushManager.cancelRecurringMainQueryWorkers(account.getCredentials());
                        return true;
                    } else {
                        pushManager.scheduleRecurringMainQueryWorkers(account.getCredentials());
                        return false;
                    }
                },
                MoreExecutors.directExecutor());
    }

    public static Data data(final Long account, final PushService.Endpoint endpoint) {
        return new Data.Builder()
                .putLong(KEY_ACCOUNT, account)
                .putString(KEY_DISTRIBUTOR, endpoint.distributor)
                .putString(KEY_URI, endpoint.url.toString())
                .build();
    }
}
