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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.MuaPool;
import rs.ltt.android.database.AppDatabase;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.jmap.common.method.call.core.SetPushSubscriptionMethodCall;
import rs.ltt.jmap.common.method.response.core.SetPushSubscriptionMethodResponse;

public class PushVerificationWorker extends ListenableWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushVerificationWorker.class);

    private static final String KEY_CREDENTIALS_ID = "credentialsId";
    private static final String KEY_PUSH_SUBSCRIPTION_ID = "pushSubscriptionId";
    private static final String KEY_VERIFICATION_CODE = "verificationCode";

    private final long credentialsId;

    private final String pushSubscriptionId;
    private final String verificationCode;

    public PushVerificationWorker(
            @NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        final Data data = workerParams.getInputData();
        this.credentialsId = data.getLong(KEY_CREDENTIALS_ID, -1);
        this.pushSubscriptionId = data.getString(KEY_PUSH_SUBSCRIPTION_ID);
        this.verificationCode = data.getString(KEY_VERIFICATION_CODE);
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        if (Strings.isNullOrEmpty(pushSubscriptionId)
                || Strings.isNullOrEmpty(verificationCode)
                || credentialsId < 0) {
            return Futures.immediateFuture(Result.failure());
        }
        final var anyAccountFuture =
                AppDatabase.getInstance(getApplicationContext())
                        .accountDao()
                        .getAnyAccountFuture(credentialsId);
        return Futures.transformAsync(
                anyAccountFuture, this::verifySubscription, MoreExecutors.directExecutor());
    }

    private ListenableFuture<Result> verifySubscription(final AccountWithCredentials account) {
        if (account == null) {
            return Futures.immediateFuture(Result.failure());
        }
        final var mua = MuaPool.getInstance(getApplicationContext(), account);
        final SetPushSubscriptionMethodCall setPushSubscription =
                SetPushSubscriptionMethodCall.builder()
                        .update(
                                ImmutableMap.of(
                                        pushSubscriptionId,
                                        ImmutableMap.of("verificationCode", verificationCode)))
                        .build();
        final var methodResponseFuture = mua.getJmapClient().call(setPushSubscription);
        final var result =
                Futures.transform(
                        methodResponseFuture,
                        methodResponses -> {
                            final SetPushSubscriptionMethodResponse setResponse =
                                    methodResponses.getMain(
                                            SetPushSubscriptionMethodResponse.class);
                            if (setResponse.getUpdated().size() == 1) {
                                LOGGER.info("Successfully set verification code");
                                AppDatabase.getInstance(getApplicationContext())
                                        .pushSubscriptionDao()
                                        .setVerificationCode(
                                                account.getCredentials().getId(),
                                                pushSubscriptionId,
                                                verificationCode);
                                return Result.success();
                            } else {
                                LOGGER.error("Unable to set verification code. No updates?");
                                return Result.failure();
                            }
                        },
                        MoreExecutors.directExecutor());
        return Futures.catching(
                result,
                Exception.class,
                exception -> {
                    if (AbstractMuaWorker.isNetworkIssue(exception)) {
                        return Result.retry();
                    } else {
                        return Result.failure();
                    }
                },
                MoreExecutors.directExecutor());
    }

    public static Data data(
            Long credentialsId, String pushSubscriptionId, final String verificationCode) {
        return new Data.Builder()
                .putLong(KEY_CREDENTIALS_ID, credentialsId)
                .putString(KEY_PUSH_SUBSCRIPTION_ID, pushSubscriptionId)
                .putString(KEY_VERIFICATION_CODE, verificationCode)
                .build();
    }
}
