package rs.ltt.android.worker;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;
import rs.ltt.android.entity.SearchSuggestion;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.common.entity.query.EmailQuery;
import rs.ltt.jmap.mua.util.StandardQueries;

public class SearchQueryRefreshWorker extends QueryRefreshWorker {

    private static final String SEARCH_TERM_KEY = "searchTerm";
    private static final String SEARCH_TYPE_KEY = "searchType";

    private final String searchTerm;
    private final SearchSuggestion.Type searchType;

    public SearchQueryRefreshWorker(
            @NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        final Data data = workerParams.getInputData();
        this.searchTerm = data.getString(SEARCH_TERM_KEY);
        this.searchType = SearchSuggestion.Type.valueOf(data.getString(SEARCH_TYPE_KEY));
    }

    public static Data data(
            final Long account,
            final boolean skipOverEmpty,
            final String searchTerm,
            final SearchSuggestion.Type searchType) {
        return new Data.Builder()
                .putLong(ACCOUNT_KEY, account)
                .putBoolean(SKIP_OVER_EMPTY_KEY, skipOverEmpty)
                .putString(SEARCH_TERM_KEY, searchTerm)
                .putString(SEARCH_TERM_KEY, searchType.toString())
                .build();
    }

    @Override
    EmailQuery getEmailQuery() {
        return switch (searchType) {
            case IN_EMAIL -> StandardQueries.search(
                    searchTerm, getDatabase().mailboxDao().getMailboxes(Role.TRASH, Role.JUNK));
            case BY_CONTACT -> StandardQueries.contact(
                    searchTerm, getDatabase().mailboxDao().getMailboxes(Role.TRASH, Role.JUNK));
        };
    }
}
