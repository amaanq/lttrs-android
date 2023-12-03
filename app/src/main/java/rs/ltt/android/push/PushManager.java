package rs.ltt.android.push;

import android.content.Context;
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
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.MuaPool;
import rs.ltt.android.database.AppDatabase;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.PushSubscription;
import rs.ltt.android.worker.MainMailboxQueryRefreshWorker;
import rs.ltt.android.worker.PushRegistrationWorker;
import rs.ltt.android.worker.PushVerificationWorker;
import rs.ltt.android.worker.QueryRefreshWorker;
import rs.ltt.jmap.client.Services;
import rs.ltt.jmap.client.session.Session;
import rs.ltt.jmap.common.entity.AbstractIdentifiableEntity;
import rs.ltt.jmap.common.entity.PushMessage;
import rs.ltt.jmap.common.entity.PushVerification;
import rs.ltt.jmap.common.entity.StateChange;
import rs.ltt.jmap.common.entity.capability.WebPushVapidCapability;

public class PushManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushManager.class);

    private final Context context;

    public PushManager(final Context context) {
        this.context = context;
    }

    public void onMessageReceived(
            final PushSubscription pushSubscription, final PushMessage pushMessage) {
        if (pushMessage instanceof PushVerification pushVerification) {
            onPushVerificationReceived(pushSubscription, pushVerification);
        } else if (pushMessage instanceof StateChange stateChange) {
            onStateChangeReceived(pushSubscription.deviceClientId, stateChange);
        } else {
            throw new IllegalArgumentException(
                    String.format(
                            "Receiving %s messages not implemented",
                            pushMessage.getClass().getSimpleName()));
        }
    }

    private void onPushVerificationReceived(
            final PushSubscription pushSubscription, final PushVerification pushMessage) {
        final var pushSubscriptionId = pushMessage.getPushSubscriptionId();
        final var verificationCode = pushMessage.getVerificationCode();
        final OneTimeWorkRequest workRequest =
                new OneTimeWorkRequest.Builder(PushVerificationWorker.class)
                        .setInputData(
                                PushVerificationWorker.data(
                                        pushSubscription.credentialsId,
                                        pushSubscriptionId,
                                        verificationCode))
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

    public ListenableFuture<Void> scheduleRecurringMainQueryWorkers(
            final AccountWithCredentials.Credentials credentials) {
        final var accountIds =
                AppDatabase.getInstance(context).accountDao().getAccountIds(credentials.getId());
        return Futures.transform(
                accountIds,
                this::scheduleRecurringMainQueryWorkers,
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> scheduleRecurringMainQueryWorkers(Long account) {
        return Futures.transformAsync(
                AppDatabase.getInstance(context).accountDao().getAccountFuture(account),
                a -> scheduleRecurringMainQueryWorkers(a.getCredentials()),
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

    public ListenableFuture<Void> cancelRecurringMainQueryWorkers(
            final AccountWithCredentials.Credentials credentials) {
        final var accountIds =
                AppDatabase.getInstance(context).accountDao().getAccountIds(credentials.getId());
        return Futures.transform(
                accountIds, this::cancelRecurringMainQueryWorkers, MoreExecutors.directExecutor());
    }

    private Void cancelRecurringMainQueryWorkers(final Collection<Long> accountIds) {
        LOGGER.info("Cancelling WorkManager fallback for accounts {}", accountIds);
        final WorkManager workManager = WorkManager.getInstance(context);
        for (final Long accountId : accountIds) {
            workManager.cancelUniqueWork(
                    MainMailboxQueryRefreshWorker.uniquePeriodicName(accountId));
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
        final var deviceClientId = UUID.randomUUID();
        final var endpointFuture =
                pushService.register(applicationServerKey.orElse(null), deviceClientId);
        final var appDatabase = AppDatabase.getInstance(context.getApplicationContext());
        return Futures.transformAsync(
                endpointFuture,
                endpoint -> {
                    final var optionalUrl = endpoint.getUrl();
                    appDatabase
                            .pushSubscriptionDao()
                            .insert(account.getCredentials(), deviceClientId, endpoint.distributor);
                    if (optionalUrl.isPresent()) {
                        final var deviceIdEndpoint =
                                new PushService.DeviceIdEndpoint(deviceClientId, endpoint);
                        LOGGER.info("WebPush endpoint: {}", endpoint);
                        this.register(account, deviceIdEndpoint);
                    }
                    return Futures.immediateFuture(Boolean.TRUE);
                },
                appDatabase.getQueryExecutor());
    }

    public void register(
            final AccountWithCredentials account,
            final PushService.DeviceIdEndpoint deviceIdEndpoint) {
        final OneTimeWorkRequest workRequest =
                new OneTimeWorkRequest.Builder(PushRegistrationWorker.class)
                        .setInputData(
                                PushRegistrationWorker.data(account.getId(), deviceIdEndpoint))
                        .build();
        final WorkManager workManager = WorkManager.getInstance(context.getApplicationContext());
        workManager.enqueue(workRequest);
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

    private static void assertIsNotMainThread() {
        if (Looper.getMainLooper().isCurrentThread()) {
            throw new IllegalStateException("should not be called from the main thread.");
        }
    }

    public static PushMessage deserialize(final byte[] message) {
        return Services.GSON.fromJson(new String(message), PushMessage.class);
    }
}
