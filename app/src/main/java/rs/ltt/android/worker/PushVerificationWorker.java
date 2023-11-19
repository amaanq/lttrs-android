package rs.ltt.android.worker;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.database.AppDatabase;
import rs.ltt.jmap.client.MethodResponses;
import rs.ltt.jmap.common.method.call.core.SetPushSubscriptionMethodCall;
import rs.ltt.jmap.common.method.response.core.SetPushSubscriptionMethodResponse;

public class PushVerificationWorker extends AbstractMuaWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushVerificationWorker.class);

    private static final String KEY_PUSH_SUBSCRIPTION_ID = "pushSubscriptionId";
    private static final String KEY_VERIFICATION_CODE = "verificationCode";

    private final String pushSubscriptionId;
    private final String verificationCode;

    public PushVerificationWorker(
            @NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        final Data data = workerParams.getInputData();
        this.pushSubscriptionId = data.getString(KEY_PUSH_SUBSCRIPTION_ID);
        this.verificationCode = data.getString(KEY_VERIFICATION_CODE);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (Strings.isNullOrEmpty(pushSubscriptionId) || Strings.isNullOrEmpty(verificationCode)) {
            return Result.failure();
        }
        final SetPushSubscriptionMethodCall setPushSubscription =
                SetPushSubscriptionMethodCall.builder()
                        .update(
                                ImmutableMap.of(
                                        pushSubscriptionId,
                                        ImmutableMap.of("verificationCode", verificationCode)))
                        .build();
        try {
            final MethodResponses methodResponses =
                    getMua().getJmapClient().call(setPushSubscription).get();
            final SetPushSubscriptionMethodResponse setResponse =
                    methodResponses.getMain(SetPushSubscriptionMethodResponse.class);
            if (setResponse.getUpdated().size() == 1) {
                LOGGER.info("Successfully set verification code");
                AppDatabase.getInstance(getApplicationContext())
                        .pushSubscriptionDao()
                        .setVerificationCode(account, pushSubscriptionId, verificationCode);
                // TODO update local database
                return Result.success();
            } else {
                LOGGER.error("Unable to set verification code. No updates?");
                return Result.failure();
            }
        } catch (final ExecutionException e) {
            if (shouldRetry(e)) {
                return Result.retry();
            } else {
                return Result.failure();
            }
        } catch (final InterruptedException e) {
            return Result.retry();
        }
    }

    public static Data data(
            Long account, String pushSubscriptionId, final String verificationCode) {
        return new Data.Builder()
                .putLong(ACCOUNT_KEY, account)
                .putString(KEY_PUSH_SUBSCRIPTION_ID, pushSubscriptionId)
                .putString(KEY_VERIFICATION_CODE, verificationCode)
                .build();
    }
}
