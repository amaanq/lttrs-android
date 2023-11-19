package rs.ltt.android.push;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.MuaPool;
import rs.ltt.android.database.AppDatabase;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.worker.MainMailboxQueryRefreshWorker;
import rs.ltt.android.worker.PushVerificationWorker;
import rs.ltt.android.worker.QueryRefreshWorker;
import rs.ltt.jmap.client.JmapClient;
import rs.ltt.jmap.client.MethodResponses;
import rs.ltt.jmap.client.Services;
import rs.ltt.jmap.client.session.Session;
import rs.ltt.jmap.common.entity.AbstractIdentifiableEntity;
import rs.ltt.jmap.common.entity.PushMessage;
import rs.ltt.jmap.common.entity.PushSubscription;
import rs.ltt.jmap.common.entity.PushVerification;
import rs.ltt.jmap.common.entity.StateChange;
import rs.ltt.jmap.common.entity.capability.WebPushVapidCapability;
import rs.ltt.jmap.common.method.call.core.SetPushSubscriptionMethodCall;
import rs.ltt.jmap.common.method.response.core.SetPushSubscriptionMethodResponse;

public class PushManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushManager.class);

    private final Context context;

    public PushManager(final Context context) {
        this.context = context;
    }

    public void onMessageReceived(
            final AccountWithCredentials account, final PushMessage pushMessage) {
        if (pushMessage instanceof PushVerification pushVerification) {
            onPushVerificationReceived(account, pushVerification);
        } else if (pushMessage instanceof StateChange stateChange) {
            onStateChangeReceived(account.getDeviceClientId(), stateChange);
        } else {
            throw new IllegalArgumentException(
                    String.format(
                            "Receiving %s messages not implemented",
                            pushMessage.getClass().getSimpleName()));
        }
    }

    private void onPushVerificationReceived(
            final AccountWithCredentials account, final PushVerification pushMessage) {
        final var pushSubscriptionId = pushMessage.getPushSubscriptionId();
        final var verificationCode = pushMessage.getVerificationCode();
        final OneTimeWorkRequest workRequest =
                new OneTimeWorkRequest.Builder(PushVerificationWorker.class)
                        .setInputData(
                                PushVerificationWorker.data(
                                        account.getId(), pushSubscriptionId, verificationCode))
                        .build();
        final WorkManager workManager = WorkManager.getInstance(context.getApplicationContext());
        workManager.enqueue(workRequest);
    }

    private void onStateChangeReceived(final UUID deviceClientId, final StateChange pushMessage) {
        LOGGER.info("onStateChangeReceived({},{})", deviceClientId, pushMessage);
        for (final Map.Entry<String, Map<Class<? extends AbstractIdentifiableEntity>, String>>
                entry : pushMessage.getChanged().entrySet()) {
            final String accountId = entry.getKey();
            final Map<Class<? extends AbstractIdentifiableEntity>, String> change =
                    entry.getValue();
            final AccountWithCredentials account =
                    AppDatabase.getInstance(context)
                            .accountDao()
                            .getAccount(deviceClientId, accountId);
            if (account == null) {
                LOGGER.error(
                        "Account with deviceClientId={} and accountId={} not found",
                        deviceClientId,
                        accountId);
                continue;
            }
            onStateChangeReceived(account, change);
        }
    }

    private void onStateChangeReceived(
            final AccountWithCredentials account,
            final Map<Class<? extends AbstractIdentifiableEntity>, String> change) {
        final boolean activityStarted =
                ProcessLifecycleOwner.get()
                        .getLifecycle()
                        .getCurrentState()
                        .isAtLeast(Lifecycle.State.STARTED);
        LOGGER.error(
                "Account {} has received a state change {} (activityStarted={})",
                account.getName(),
                change,
                activityStarted);
        // TODO skip if application is in foreground (it's just easier to test if we don’t skip)
        final OneTimeWorkRequest workRequest = QueryRefreshWorker.main(account.getId());
        final WorkManager workManager = WorkManager.getInstance(context.getApplicationContext());
        workManager.enqueueUniqueWork(
                QueryRefreshWorker.uniqueName(account.getId()),
                ExistingWorkPolicy.KEEP,
                workRequest);
    }

    public void onNewToken(final String token) {}

    public static void register(
            final Context context, final List<AccountWithCredentials> accounts) {
        assertIsNotMainThread();
        final var distinctCredentials =
                accounts.stream()
                        .collect(
                                Collectors.toMap(
                                        AccountWithCredentials::getCredentials,
                                        Function.identity(),
                                        (a, b) -> a))
                        .values();
        final var pushManager = new PushManager(context);
        for (final AccountWithCredentials account : distinctCredentials) {
            final var registration = pushManager.register(account);
            Futures.addCallback(
                    registration,
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(Boolean success) {
                            if (Boolean.TRUE.equals(success)) {
                                LOGGER.info("Successfully registered push service");
                            } else {
                                pushManager.scheduleRecurringMainQueryWorkers(
                                        account.getCredentials());
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Throwable throwable) {
                            LOGGER.warn("Could not register with push service", throwable);
                            pushManager.scheduleRecurringMainQueryWorkers(account.getCredentials());
                        }
                    },
                    MoreExecutors.directExecutor());
        }
    }

    private ListenableFuture<Void> scheduleRecurringMainQueryWorkers(
            final AccountWithCredentials.Credentials credentials) {
        final var accountIds =
                AppDatabase.getInstance(context).accountDao().getAccountIds(credentials.getId());
        return Futures.transform(
                accountIds,
                this::scheduleRecurringMainQueryWorkers,
                MoreExecutors.directExecutor());
    }

    private Void scheduleRecurringMainQueryWorkers(final Collection<Long> accountIds) {
        LOGGER.info("Scheduling WorkManager fallback for accounts {}", accountIds);
        final WorkManager workManager = WorkManager.getInstance(context);
        for (final Long accountId : accountIds) {
            final PeriodicWorkRequest periodicWorkRequest =
                    new PeriodicWorkRequest.Builder(
                                    MainMailboxQueryRefreshWorker.class,
                                    15,
                                    TimeUnit.MINUTES,
                                    20,
                                    TimeUnit.MINUTES)
                            .setInputData(MainMailboxQueryRefreshWorker.data(accountId, true))
                            .setConstraints(
                                    new Constraints.Builder()
                                            .setRequiredNetworkType(NetworkType.CONNECTED)
                                            .build())
                            .build();
            workManager.enqueueUniquePeriodicWork(
                    MainMailboxQueryRefreshWorker.uniquePeriodicName(accountId),
                    ExistingPeriodicWorkPolicy.REPLACE,
                    periodicWorkRequest);
        }
        return null;
    }

    private ListenableFuture<Boolean> register(final AccountWithCredentials account) {
        final var sessionFuture =
                MuaPool.getInstance(context, account).getJmapClient().getSession();
        return Futures.transformAsync(
                sessionFuture,
                session -> register(account, session),
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Boolean> register(
            final AccountWithCredentials account, final Session session) {
        final var applicationServerKey = getApplicationServerKey(session);
        final var pushService = getDefaultPushService(applicationServerKey.isPresent());
        if (pushService == null) {
            LOGGER.warn("No push service found on user device");
            return Futures.immediateFuture(false);
        }
        final var clientDeviceId = account.getDeviceClientId();
        if (clientDeviceId == null) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("Missing clientDeviceId on credentials"));
        }
        final var uriFuture =
                pushService.register(applicationServerKey.orElse(null), clientDeviceId);
        return Futures.transformAsync(
                uriFuture,
                optionalUri -> {
                    if (optionalUri.isPresent()) {
                        final var uri = optionalUri.get();
                        LOGGER.info("WebPush uri: {}", uri);
                        return register(account, uri);
                    } else {
                        return Futures.immediateFuture(Boolean.TRUE);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private static Optional<byte[]> getApplicationServerKey(final Session session) {
        final var webPushVapidCapability = session.getCapability(WebPushVapidCapability.class);
        if (webPushVapidCapability == null) {
            return Optional.empty();
        }
        final var applicationServerKeyBase64 = webPushVapidCapability.getApplicationServerKey();
        if (Strings.isNullOrEmpty(applicationServerKeyBase64)) {
            return Optional.empty();
        }
        if (BaseEncoding.base64Url().canDecode(applicationServerKeyBase64)) {
            return Optional.of(BaseEncoding.base64Url().decode(applicationServerKeyBase64));
        }
        return Optional.empty();
    }

    private PushService getDefaultPushService(final boolean hasApplicationServerKey) {
        final var services =
                Arrays.asList(new UnifiedPushService(context), new FirebasePushService(context));
        for (final PushService pushService : services) {
            if (pushService.requiresVapid() && !hasApplicationServerKey) {
                continue;
            }
            if (pushService.isAvailable()) {
                return pushService;
            }
        }
        return null;
    }

    private ListenableFuture<Boolean> register(
            final AccountWithCredentials account, final Uri uri) {
        final HttpUrl httpUrl;
        try {
            httpUrl = HttpUrl.get(uri.toString());
        } catch (final Exception e) {
            return Futures.immediateFailedFuture(e);
        }
        return register(account, httpUrl);
    }

    private ListenableFuture<Boolean> register(
            final AccountWithCredentials account, final HttpUrl httpUrl) {
        final WebPushMessageEncryption.KeyMaterial keyMaterial;
        try {
            keyMaterial = WebPushMessageEncryption.generateKeyMaterial();
        } catch (final GeneralSecurityException e) {
            return Futures.immediateFailedFuture(e);
        }
        final var existingSubscriptionIds =
                AppDatabase.getInstance(context)
                        .pushSubscriptionDao()
                        .getExistingSubscriptionIds(account.getCredentials().getId());
        return Futures.transformAsync(
                existingSubscriptionIds,
                ids -> register(account, httpUrl, keyMaterial, ids),
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Boolean> register(
            final AccountWithCredentials account,
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
        final JmapClient jmapClient = MuaPool.getInstance(context, account).getJmapClient();
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
                        AppDatabase.getInstance(context)
                                .pushSubscriptionDao()
                                .insert(
                                        account.getCredentials(),
                                        pushSubscriptionId,
                                        httpUrl,
                                        keyMaterial,
                                        expires);
                        return true;
                    } else {
                        return false;
                    }
                },
                MoreExecutors.directExecutor());
    }

    private static void assertIsNotMainThread() {
        if (Looper.getMainLooper().isCurrentThread()) {
            throw new IllegalStateException("should not be called from the main thread.");
        }
    }

    public static PushMessage deserialize(final byte[] message) {
        return Services.GSON.fromJson(new String(message), PushMessage.class);
    }
}
