package rs.ltt.android;

import android.content.Context;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import de.gultsch.common.TrustManagers;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.cache.AutocryptDatabaseStorage;
import rs.ltt.android.cache.DatabaseCache;
import rs.ltt.android.database.AppDatabase;
import rs.ltt.android.database.LttrsDatabase;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.autocrypt.jmap.AutocryptPlugin;
import rs.ltt.jmap.client.session.FileSessionCache;
import rs.ltt.jmap.mua.Mua;

public final class MuaPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(MuaPool.class);

    private static final Map<AccountWithCredentials, Mua> INSTANCES = new HashMap<>();

    private MuaPool() {}

    public static ListenableFuture<Mua> getInstance(final Context context, final long accountId) {
        return Futures.transform(
                AppDatabase.getInstance(context).accountDao().getAccountFuture(accountId),
                account -> getInstance(context, account),
                MoreExecutors.directExecutor());
    }

    public static Mua getInstance(final Context context, final AccountWithCredentials account) {
        final Mua instance = INSTANCES.get(account);
        if (instance != null) {
            return instance;
        }
        synchronized (MuaPool.class) {
            final Mua existing = INSTANCES.get(account);
            if (existing != null) {
                return existing;
            }
            LOGGER.info("Building Mua for account id {}", account.getId());
            final var application = context.getApplicationContext();
            final var database = LttrsDatabase.getInstance(context, account.getId());
            final var storage = new AutocryptDatabaseStorage(database);
            final var autocryptPlugin = new AutocryptPlugin(account.getName(), storage);
            final var credentials = account.getCredentials();
            final Mua mua =
                    Mua.builder()
                            .httpAuthentication(credentials.asHttpAuthentication())
                            .accountId(account.getAccountId())
                            .sessionResource(credentials.getSessionResource())
                            .trustManager(getTrustManagerOrNull(context))
                            .cache(new DatabaseCache(database))
                            .sessionCache(new FileSessionCache(application.getCacheDir()))
                            .plugin(AutocryptPlugin.class, autocryptPlugin)
                            .useWebSocket(false)
                            .queryPageSize(20L)
                            .build();
            INSTANCES.put(account, mua);
            return mua;
        }
    }

    private static X509TrustManager getTrustManagerOrNull(final Context context) {
        try {
            return TrustManagers.createForAndroidVersion(context);
        } catch (final NoSuchAlgorithmException
                | KeyStoreException
                | CertificateException
                | IOException e) {
            LOGGER.error("Could not create TrustManager", e);
            return null;
        }
    }

    public static void evict(final long id) {
        synchronized (MuaPool.class) {
            final var iterator = INSTANCES.entrySet().iterator();
            while (iterator.hasNext()) {
                final var entry = iterator.next();
                final var account = entry.getKey();
                if (account.getId().equals(id)) {
                    final Mua mua = entry.getValue();
                    mua.close();
                    LOGGER.debug("Evicting {} from MuaPool", account.getAccountId());
                    iterator.remove();
                    return;
                }
            }
        }
    }
}
