/*
 * Copyright 2019 Daniel Gultsch
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rs.ltt.android.ui.model;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Objects;
import rs.ltt.android.entity.MailboxOverwriteEntity;
import rs.ltt.android.entity.MailboxWithRoleAndName;
import rs.ltt.android.entity.QueryInfo;
import rs.ltt.android.entity.SearchSuggestion;
import rs.ltt.android.entity.ThreadOverviewItem;
import rs.ltt.android.util.PlaceholderLabel;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.common.entity.query.EmailQuery;
import rs.ltt.jmap.mua.util.LabelWithCount;
import rs.ltt.jmap.mua.util.MailboxUtil;
import rs.ltt.jmap.mua.util.StandardQueries;

public class SearchQueryViewModel extends AbstractQueryViewModel {

    private final SearchSuggestion search;
    private final LiveData<EmailQuery> searchQueryLiveData;
    private final ListenableFuture<MailboxWithRoleAndName> inbox;

    SearchQueryViewModel(
            final Application application, final long accountId, final SearchSuggestion search) {
        super(application, accountId);
        this.search = search;
        this.inbox = queryRepository.getInbox();
        this.searchQueryLiveData =
                Transformations.map(
                        queryRepository.getTrashAndJunk(),
                        trashAndJunk ->
                                switch (search.type) {
                                    case IN_EMAIL -> StandardQueries.search(
                                            search.value, trashAndJunk);
                                    case BY_CONTACT -> StandardQueries.contact(
                                            search.value, trashAndJunk);
                                });
        init();
    }

    public LiveData<String> getSearchTerm() {
        return new MutableLiveData<>(search.value);
    }

    @Override
    protected LiveData<EmailQuery> getQuery() {
        return searchQueryLiveData;
    }

    @Override
    public QueryInfo getQueryInfo() {
        return switch (this.search.type) {
            case IN_EMAIL -> new QueryInfo(
                    queryRepository.getAccountId(),
                    QueryInfo.Type.SEARCH_IN_EMAIL,
                    this.search.value);
            case BY_CONTACT -> new QueryInfo(
                    queryRepository.getAccountId(),
                    QueryInfo.Type.SEARCH_BY_CONTACT,
                    this.search.value);
        };
    }

    @Override
    public LiveData<LabelWithCount> getLabelWithCount() {
        return new MutableLiveData<>(PlaceholderLabel.SEARCH);
    }

    public boolean isInInbox(ThreadOverviewItem item) {
        if (MailboxOverwriteEntity.hasOverwrite(item.mailboxOverwriteEntities, Role.ARCHIVE)) {
            return false;
        }
        if (MailboxOverwriteEntity.hasOverwrite(item.mailboxOverwriteEntities, Role.INBOX)) {
            return true;
        }
        MailboxWithRoleAndName inbox = getInbox();
        if (inbox == null) {
            return false;
        }
        return MailboxUtil.anyIn(item.emails, inbox.id);
    }

    private MailboxWithRoleAndName getInbox() {
        try {
            return this.inbox.get();
        } catch (Exception e) {
            return null;
        }
    }

    public static class Factory implements ViewModelProvider.Factory {

        private final Application application;
        private final long accountId;
        private final SearchSuggestion search;

        public Factory(
                Application application, final long accountId, final SearchSuggestion search) {
            this.application = application;
            this.accountId = accountId;
            this.search = search;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return Objects.requireNonNull(
                    modelClass.cast(new SearchQueryViewModel(application, accountId, search)));
        }
    }
}
