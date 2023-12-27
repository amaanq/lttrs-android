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
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.paging.PagedList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import rs.ltt.android.entity.MailboxWithRoleAndName;
import rs.ltt.android.entity.QueryInfo;
import rs.ltt.android.entity.SearchSuggestion;
import rs.ltt.android.entity.ThreadOverviewItem;
import rs.ltt.android.repository.ContactRepository;
import rs.ltt.android.repository.MainRepository;
import rs.ltt.android.repository.QueryRepository;
import rs.ltt.android.ui.EmptyMailboxAction;
import rs.ltt.android.util.MergedListsLiveData;
import rs.ltt.jmap.common.entity.query.EmailQuery;
import rs.ltt.jmap.mua.util.LabelWithCount;

public abstract class AbstractQueryViewModel extends AndroidViewModel {

    final QueryRepository queryRepository;
    private final MainRepository mainRepository;
    private final ContactRepository contactRepository;
    private final ListenableFuture<MailboxWithRoleAndName> important;
    private final HashSet<String> selectedThreads = new HashSet<>();
    private LiveData<PagedList<ThreadOverviewItem>> threads;
    private LiveData<Boolean> refreshing;
    private LiveData<Boolean> runningPagingRequest;

    private final MutableLiveData<String> searchQueryLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> searchEnabled = new MutableLiveData<>();

    private final LiveData<List<SearchSuggestion>> searchSuggestion;

    AbstractQueryViewModel(@NonNull Application application, final long accountId) {
        super(application);
        this.mainRepository = new MainRepository(application);
        this.queryRepository = new QueryRepository(application, accountId);
        this.contactRepository = new ContactRepository(application, accountId);
        this.important = this.queryRepository.getImportant();
        this.searchSuggestion =
                Transformations.switchMap(
                        searchEnabled,
                        enabled -> {
                            if (Boolean.TRUE.equals(enabled)) {
                                return Transformations.switchMap(
                                        searchQueryLiveData, this::getSearchSuggestion);
                            } else {
                                return new MutableLiveData<>(Collections.emptyList());
                            }
                        });
    }

    private LiveData<List<SearchSuggestion>> getRawSearchSuggestion(final String term) {
        final LiveData<List<SearchSuggestion>> previousSearches =
                this.mainRepository.getPreviousSearches(term);
        if (term.length() < 3) {
            return previousSearches;
        }
        final LiveData<List<SearchSuggestion>> contactSuggestions =
                this.contactRepository.getContactSuggestions(term);
        return new MergedListsLiveData<>(ImmutableList.of(previousSearches, contactSuggestions));
    }

    private LiveData<List<SearchSuggestion>> getSearchSuggestion(final String term) {
        return Transformations.map(
                getRawSearchSuggestion(term),
                s -> Ordering.natural().sortedCopy(ImmutableSet.copyOf(s)));
    }

    void init() {
        this.threads =
                Transformations.switchMap(getQuery(), queryRepository::getThreadOverviewItems);
        this.refreshing = Transformations.switchMap(getQuery(), queryRepository::isRunningQueryFor);
        this.runningPagingRequest =
                Transformations.switchMap(getQuery(), queryRepository::isRunningPagingRequestFor);
    }

    public LiveData<Boolean> isRefreshing() {
        final LiveData<Boolean> refreshing = this.refreshing;
        if (refreshing == null) {
            throw new IllegalStateException(
                    "LiveData for refreshing not initialized. Forgot to call init()?");
        }
        return refreshing;
    }

    public Future<MailboxWithRoleAndName> getImportant() {
        return this.important;
    }

    public LiveData<Boolean> isRunningPagingRequest() {
        final LiveData<Boolean> paging = this.runningPagingRequest;
        if (paging == null) {
            throw new IllegalStateException(
                    "LiveData for paging not initialized. Forgot to call init()?");
        }
        return paging;
    }

    public LiveData<PagedList<ThreadOverviewItem>> getThreadOverviewItems() {
        final LiveData<PagedList<ThreadOverviewItem>> liveData = this.threads;
        if (liveData == null) {
            throw new IllegalStateException(
                    "LiveData for thread items not initialized. Forgot to call init()?");
        }
        return liveData;
    }

    public Set<String> getSelectedThreads() {
        return this.selectedThreads;
    }

    public LiveData<EmptyMailboxAction> getEmptyMailboxAction() {
        return new MutableLiveData<>(null);
    }

    public void onRefresh() {
        final EmailQuery emailQuery = getQuery().getValue();
        if (emailQuery != null) {
            queryRepository.refresh(emailQuery);
        }
    }

    protected abstract LiveData<EmailQuery> getQuery();

    public abstract QueryInfo getQueryInfo();

    public abstract LiveData<LabelWithCount> getLabelWithCount();

    public LiveData<List<SearchSuggestion>> getSearchSuggestions() {
        return this.searchSuggestion;
    }

    public MutableLiveData<String> getSearchQueryLiveData() {
        return this.searchQueryLiveData;
    }

    public void setSearchEnabled(final boolean enabled) {
        this.searchEnabled.postValue(enabled);
    }
}
