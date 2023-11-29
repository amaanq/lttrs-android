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

package rs.ltt.android.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.search.SearchView;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.LttrsNavigationDirections;
import rs.ltt.android.R;
import rs.ltt.android.databinding.FragmentThreadListBinding;
import rs.ltt.android.entity.ThreadOverviewItem;
import rs.ltt.android.service.EventMonitorService;
import rs.ltt.android.ui.ActionModeMenuConfiguration;
import rs.ltt.android.ui.EmptyMailboxAction;
import rs.ltt.android.ui.ExtendedFabSizeChanger;
import rs.ltt.android.ui.ItemAnimators;
import rs.ltt.android.ui.LiveDataTextWatcher;
import rs.ltt.android.ui.QueryItemTouchHelper;
import rs.ltt.android.ui.RecyclerViews;
import rs.ltt.android.ui.SelectionTracker;
import rs.ltt.android.ui.Translations;
import rs.ltt.android.ui.activity.ComposeActivity;
import rs.ltt.android.ui.activity.result.contract.ComposeContract;
import rs.ltt.android.ui.adapter.OnFlaggedToggled;
import rs.ltt.android.ui.adapter.OnSelectionToggled;
import rs.ltt.android.ui.adapter.SearchSuggestionAdapter;
import rs.ltt.android.ui.adapter.ThreadOverviewAdapter;
import rs.ltt.android.ui.model.AbstractQueryViewModel;
import rs.ltt.android.ui.model.SearchQueryViewModel;
import rs.ltt.android.util.CharSequences;
import rs.ltt.jmap.mua.util.LabelWithCount;

public abstract class AbstractQueryFragment extends AbstractLttrsFragment
        implements OnFlaggedToggled,
                ThreadOverviewAdapter.OnThreadClicked,
                QueryItemTouchHelper.OnQueryItemSwipe,
                OnSelectionToggled,
                ThreadOverviewAdapter.OnEmptyMailboxActionClicked,
                SearchView.TransitionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractQueryFragment.class);
    protected FragmentThreadListBinding binding;
    private ThreadOverviewAdapter threadOverviewAdapter;
    private ItemTouchHelper itemTouchHelper;
    private SelectionTracker tracker;
    private ActivityResultLauncher<Bundle> composeLauncher;

    private SearchSuggestionAdapter searchSuggestionAdapter;

    private final OnBackPressedCallback contextualToolbarOnBackPressedCallback =
            new OnBackPressedCallback(false) {
                @Override
                public void handleOnBackPressed() {
                    endActionMode();
                }
            };
    private final OnBackPressedCallback searchViewOnBackPressedCallback =
            new OnBackPressedCallback(false) {
                @Override
                public void handleOnBackPressed() {
                    binding.searchView.hide();
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.composeLauncher =
                registerForActivityResult(new ComposeContract(), this::onComposeResult);
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        final AbstractQueryViewModel viewModel = getQueryViewModel();
        this.binding =
                DataBindingUtil.inflate(inflater, R.layout.fragment_thread_list, container, false);

        this.configureNavigationAction();

        this.binding.contextualToolbar.setNavigationOnClickListener(
                v -> {
                    endActionMode();
                    this.binding.searchBar.collapse(
                            this.binding.contextualToolbar, this.binding.appBarLayout);
                });
        this.binding.contextualToolbar.setOnMenuItemClickListener(this::onActionItemClicked);

        setupAdapter(viewModel);
        this.tracker =
                new SelectionTracker(
                        getQueryViewModel().getSelectedThreads(),
                        threadOverviewAdapter,
                        this::onSelectionChanged);
        if (this.tracker.hasSelection()) {
            this.onSelectionChanged(this.tracker.countSelected(), true);
        } else if (showComposeButton()) {
            this.binding.compose.show();
        }
        observeThreadOverviewItems(viewModel.getThreadOverviewItems());

        binding.setViewModel(viewModel);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.compose.setOnClickListener(
                (v) -> composeLauncher.launch(ComposeActivity.compose()));

        binding.threadList.addOnScrollListener(ExtendedFabSizeChanger.of(binding.compose));

        getViewLifecycleOwner()
                .getLifecycle()
                .addObserver(
                        new DefaultLifecycleObserver() {
                            @Override
                            public void onStart(@NonNull LifecycleOwner owner) {
                                startPushService();
                            }
                        });

        binding.swipeToRefresh.setColorSchemeColors(
                MaterialColors.getColor(
                        binding.swipeToRefresh, androidx.appcompat.R.attr.colorAccent));
        binding.swipeToRefresh.setProgressBackgroundColorSchemeColor(
                MaterialColors.getColor(
                        binding.swipeToRefresh, com.google.android.material.R.attr.colorSurface));

        ItemAnimators.disableChangeAnimation(binding.threadList.getItemAnimator());

        viewModel
                .isRunningPagingRequest()
                .observe(getViewLifecycleOwner(), threadOverviewAdapter::setLoading);
        viewModel
                .getEmptyMailboxAction()
                .observe(getViewLifecycleOwner(), this::setEmptyMailboxAction);

        this.itemTouchHelper = new ItemTouchHelper(new QueryItemTouchHelper(this));
        this.itemTouchHelper.attachToRecyclerView(binding.threadList);

        this.binding.searchView.addTransitionListener(this);

        this.binding
                .searchView
                .getEditText()
                .setOnEditorActionListener(
                        (textView, actionId, event) -> {
                            final String query = CharSequences.nullToEmpty(textView.getText());
                            binding.searchView.clearFocusAndHideKeyboard();
                            binding.searchView.setVisible(false);
                            return executeSearch(query);
                        });
        this.searchSuggestionAdapter = new SearchSuggestionAdapter();
        this.searchSuggestionAdapter.setOnSearchSuggestionClicked(
                suggestion -> {
                    executeSearch(suggestion.value);
                });
        this.binding.searchSuggestionList.setAdapter(this.searchSuggestionAdapter);
        viewModel
                .getSearchSuggestions()
                .observe(getViewLifecycleOwner(), searchSuggestionAdapter::submitList);
        this.binding
                .searchView
                .getEditText()
                .addTextChangedListener(
                        new LiveDataTextWatcher(viewModel.getSearchQueryLiveData()));

        final var dispatcher = this.requireActivity().getOnBackPressedDispatcher();

        dispatcher.addCallback(
                getViewLifecycleOwner(), this.contextualToolbarOnBackPressedCallback);
        dispatcher.addCallback(getViewLifecycleOwner(), this.searchViewOnBackPressedCallback);

        this.getQueryViewModel()
                .getLabelWithCount()
                .observe(getViewLifecycleOwner(), this::onLabelOpened);

        return binding.getRoot();
    }

    private void configureNavigationAction() {
        final var navigationAction = getNavigationAction();
        switch (navigationAction) {
            case DRAWER -> {
                this.binding.searchBar.setNavigationIcon(
                        new DrawerArrowDrawable(this.binding.searchBar.getContext()));

                this.binding.searchBar.setNavigationOnClickListener(
                        v -> requireLttrsActivity().openDrawer());
            }
            case UP -> {
                this.binding.searchBar.setNavigationIcon(R.drawable.ic_arrow_back_24dp);
                this.binding.searchBar.setNavigationOnClickListener(
                        v -> getNavController().navigateUp());
                // this overwrites `app:useDrawerArrowDrawable="true"` on the view; otherwise we
                // would get the animation from drawer to arrow if though this is already an arrow
                this.binding
                        .searchView
                        .getToolbar()
                        .setNavigationIcon(R.drawable.ic_arrow_back_24dp);
            }
            default -> throw new IllegalStateException(
                    String.format("%s is not a valid navigation action", navigationAction));
        }
    }

    private boolean executeSearch(final String query) {
        if (Strings.isNullOrEmpty(query)) {
            final var destination = getNavController().getCurrentDestination();
            if (destination != null && destination.getId() == R.id.search) {
                getNavController().popBackStack();
            }
        } else {
            getLttrsViewModel().insertSearchSuggestion(query);
            getNavController().navigate(LttrsNavigationDirections.actionSearch(query));
        }
        return false;
    }

    private void endActionMode() {
        this.tracker.clearSelection();
    }

    private void setEmptyMailboxAction(final EmptyMailboxAction emptyMailboxAction) {
        // TODO if != null we might need to scroll to top
        threadOverviewAdapter.setEmptyMailboxAction(emptyMailboxAction);
    }

    private void onComposeResult(final Bundle data) {
        if (data == null) {
            return;
        }
        final UUID uuid = (UUID) data.getSerializable(ComposeActivity.EDITING_TASK_ID_EXTRA);
        if (uuid != null) {
            getLttrsViewModel().observeForFailure(uuid);
        }
    }

    private void setupAdapter(final AbstractQueryViewModel viewModel) {
        this.threadOverviewAdapter = new ThreadOverviewAdapter();
        this.binding.threadList.setAdapter(threadOverviewAdapter);
        this.threadOverviewAdapter.setOnFlaggedToggledListener(this);
        this.threadOverviewAdapter.setOnThreadClickedListener(this);
        this.threadOverviewAdapter.setOnEmptyMailboxActionClickedListener(this);
        this.threadOverviewAdapter.setOnSelectionToggled(this);
        this.threadOverviewAdapter.setImportantMailbox(viewModel.getImportant());
    }

    private void observeThreadOverviewItems(
            final LiveData<PagedList<ThreadOverviewItem>> liveData) {
        liveData.observe(
                getViewLifecycleOwner(),
                threadOverviewItems -> {
                    final boolean atTop = RecyclerViews.scrolledToTop(binding.threadList);
                    configureItemAnimator();
                    threadOverviewAdapter.submitList(
                            threadOverviewItems,
                            () -> {
                                if (atTop && binding != null) {
                                    binding.threadList.scrollToPosition(0);
                                }
                                if (this.tracker.hasSelection()) {
                                    this.onSelectionChanged(this.tracker.countSelected());
                                }
                            });
                });
    }

    /**
     * The RecyclerView displays a spinning wheel while waiting for the initial load from database.
     * However we don’t want to animate the change from one item with spinning wheel to multiple
     * real items (Threads). Therefor we disable the animator during the first submission and re-add
     * it later
     */
    private void configureItemAnimator() {
        ItemAnimators.configureItemAnimator(
                this.binding.threadList, this.threadOverviewAdapter.isInitialLoad());
    }

    @Override
    public void onDestroyView() {
        nullReferences();
        super.onDestroyView();
    }

    private void nullReferences() {
        this.binding.threadList.setAdapter(null);
        this.itemTouchHelper.attachToRecyclerView(null);
        this.itemTouchHelper = null;
        this.threadOverviewAdapter = null;
        this.tracker = null;
        this.binding = null;
    }

    private void onSelectionChanged(final int numSelected) {
        onSelectionChanged(numSelected, false);
    }

    private void onSelectionChanged(final int numSelected, final boolean skipAnimation) {
        if (numSelected > 0) {
            requireLttrsActivity().lockDrawerLayout();
            this.binding.searchBar.expand(
                    this.binding.contextualToolbar, this.binding.appBarLayout, skipAnimation);
            this.binding.contextualToolbar.setTitle(String.valueOf(numSelected));
            updateContextualToolbarMenu();
            this.binding.compose.hide();
            this.contextualToolbarOnBackPressedCallback.setEnabled(true);
        } else {
            requireLttrsActivity().unlockDrawerLayout();
            this.binding.searchBar.collapse(
                    this.binding.contextualToolbar, this.binding.appBarLayout, skipAnimation);
            if (showComposeButton()) {
                binding.compose.show();
            }
            this.contextualToolbarOnBackPressedCallback.setEnabled(false);
        }
    }

    private void onLabelOpened(final LabelWithCount label) {
        getLttrsViewModel().setSelectedLabel(label);
        if (label instanceof SearchQueryViewModel.SearchLabel) {
            this.threadOverviewAdapter.setTitle(getString(R.string.results_in_email));
        } else {
            this.threadOverviewAdapter.setTitle(
                    Translations.asHumanReadableName(requireContext(), label));
        }
    }

    @Override
    public void onFlaggedToggled(String threadId, boolean target) {
        getThreadModifier().toggleFlagged(threadId, target);
    }

    @Override
    public void onSelectionToggled(final String threadId, final boolean selected) {
        if (selected) {
            this.tracker.select(threadId);
        } else {
            this.tracker.deselect(threadId);
        }
    }

    private void startPushService() {
        EventMonitorService.watchQuery(requireContext(), getQueryViewModel().getQueryInfo());
        LOGGER.warn("startPushService({})", getClass().getSimpleName());
    }

    protected void archive(ThreadOverviewItem item) {
        archive(ImmutableSet.of(item.threadId));
    }

    protected void archive(Collection<String> threadIds) {
        getThreadModifier().archive(threadIds);
    }

    protected abstract AbstractQueryViewModel getQueryViewModel();

    @Override
    public void onThreadClicked(ThreadOverviewItem threadOverviewItem, boolean important) {
        getNavController()
                .navigate(
                        LttrsNavigationDirections.actionToThread(
                                threadOverviewItem.threadId,
                                null,
                                threadOverviewItem.getSubject().getSubject(),
                                important));
    }

    @Override
    public QueryItemTouchHelper.Swipable onQueryItemSwipe(int position) {
        final ThreadOverviewItem item = threadOverviewAdapter.getItem(position);
        if (item == null) {
            throw new IllegalStateException("Swipe Item not found");
        }
        LOGGER.debug("trying to swipe " + item.getSubject());
        return onQueryItemSwipe(item);
    }

    @Override
    public void onEmptyMailboxActionClicked(final EmptyMailboxAction action) {
        final String message =
                requireActivity()
                        .getResources()
                        .getQuantityString(
                                R.plurals.x_emails_will_permanently_deleted,
                                action.getItemCount(),
                                action.getItemCount());
        new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.empty_trash_dialog_title)
                .setMessage(message)
                .setPositiveButton(
                        R.string.empty,
                        (dialog, which) -> getThreadModifier().executeEmptyMailboxAction(action))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    protected abstract QueryItemTouchHelper.Swipable onQueryItemSwipe(ThreadOverviewItem item);

    protected abstract boolean isQueryItemRemovedAfterSwipe();

    @Override
    public void onQueryItemSwiped(final RecyclerView.ViewHolder viewHolder) {
        final int position = viewHolder.getAdapterPosition();
        final ThreadOverviewItem item = threadOverviewAdapter.getItem(position);
        if (item == null) {
            throw new IllegalStateException("Swipe Item not found");
        }
        onQueryItemSwiped(item);
        if (isQueryItemRemovedAfterSwipe()) {
            tracker.deselect(item.threadId);
        } else {
            LOGGER.debug("Reset swipe because we do not expect QueryItem to be removed");
            // Those two instructions seem to be the only combination that resets the swiped state
            // ItemTouchHelper usually assumes that the items gets removed
            threadOverviewAdapter.notifyItemChanged(position);
            itemTouchHelper.startSwipe(viewHolder);
        }
    }

    protected abstract void onQueryItemSwiped(ThreadOverviewItem item);

    protected abstract boolean showComposeButton();

    private void updateContextualToolbarMenu() {
        final Menu menu = this.binding.contextualToolbar.getMenu();
        final ActionModeMenuConfiguration.QueryType queryType = getQueryType();
        getQueryViewModel().getThreadOverviewItems().getValue();
        final ActionModeMenuConfiguration.SelectionInfo selectionInfo =
                ActionModeMenuConfiguration.SelectionInfo.vote(
                        tracker.getSelection(), threadOverviewAdapter);
        final MenuItem archive = menu.findItem(R.id.action_archive);
        final MenuItem removeLabel = menu.findItem(R.id.action_remove_label);
        final MenuItem moveToTrash = menu.findItem(R.id.action_move_to_trash);
        final MenuItem markRead = menu.findItem(R.id.action_mark_read);
        final MenuItem markUnRead = menu.findItem(R.id.action_mark_unread);
        final MenuItem moveToInbox = menu.findItem(R.id.action_move_to_inbox);
        final MenuItem markImportant = menu.findItem(R.id.action_mark_important);
        final MenuItem markNotImportant = menu.findItem(R.id.action_mark_not_important);
        final MenuItem addFlag = menu.findItem(R.id.action_add_flag);
        final MenuItem removeFlag = menu.findItem(R.id.action_remove_flag);

        if (queryType == ActionModeMenuConfiguration.QueryType.ARCHIVE) {
            archive.setVisible(false);
            removeLabel.setVisible(false);
        } else if (queryType == ActionModeMenuConfiguration.QueryType.TRASH) {
            archive.setVisible(false);
            removeLabel.setVisible(false);
            moveToTrash.setVisible(false);
        } else if (queryType == ActionModeMenuConfiguration.QueryType.SPECIAL) {
            archive.setVisible(false);
            removeLabel.setVisible(false);
            moveToInbox.setVisible(false);
        } else if (queryType == ActionModeMenuConfiguration.QueryType.INBOX) {
            removeLabel.setVisible(false);
            moveToInbox.setVisible(false);
        } else {
            archive.setVisible(false);
            moveToInbox.setVisible(false);
        }
        markRead.setVisible(!selectionInfo.read);
        markUnRead.setVisible(selectionInfo.read);
        markImportant.setVisible(
                queryType != ActionModeMenuConfiguration.QueryType.IMPORTANT
                        && !selectionInfo.important);
        markNotImportant.setVisible(
                queryType != ActionModeMenuConfiguration.QueryType.IMPORTANT
                        && selectionInfo.important);
        addFlag.setVisible(
                queryType != ActionModeMenuConfiguration.QueryType.FLAGGED
                        && !selectionInfo.flagged);
        removeFlag.setVisible(
                queryType != ActionModeMenuConfiguration.QueryType.FLAGGED
                        && selectionInfo.flagged);
    }

    private boolean onActionItemClicked(final MenuItem item) {
        final int itemId = item.getItemId();
        final Collection<String> threadIds = Sets.newHashSet(tracker.getSelection());
        if (itemId == R.id.action_archive) {
            archive(threadIds);
            tracker.clearSelection();
        } else if (itemId == R.id.action_remove_label) {
            removeLabel(threadIds);
            tracker.clearSelection();
        } else if (itemId == R.id.action_mark_read) {
            getThreadModifier().markRead(threadIds);
        } else if (itemId == R.id.action_mark_unread) {
            getThreadModifier().markUnread(threadIds);
        } else if (itemId == R.id.action_change_labels) {
            getNavController()
                    .navigate(
                            LttrsNavigationDirections.actionChangeLabels(
                                    threadIds.toArray(new String[0])));
        } else if (itemId == R.id.action_move_to_inbox) {
            getThreadModifier().moveToInbox(threadIds);
            tracker.clearSelection();
        } else if (itemId == R.id.action_mark_important) {
            getThreadModifier().markImportant(threadIds);
        } else if (itemId == R.id.action_mark_not_important) {
            getThreadModifier().markNotImportant(threadIds);
        } else if (itemId == R.id.action_add_flag) {
            getThreadModifier().addFlag(threadIds);
        } else if (itemId == R.id.action_remove_flag) {
            getThreadModifier().removeFlag(threadIds);
        } else if (itemId == R.id.action_move_to_trash) {
            getThreadModifier().moveToTrash(threadIds);
            tracker.clearSelection();
        }
        return true;
    }

    abstract void removeLabel(Collection<String> threadIds);

    protected abstract ActionModeMenuConfiguration.QueryType getQueryType();

    @Override
    public void onStateChanged(
            @NonNull SearchView searchView,
            @NonNull SearchView.TransitionState previousState,
            @NonNull SearchView.TransitionState newState) {
        final boolean isShowing =
                Arrays.asList(SearchView.TransitionState.SHOWING, SearchView.TransitionState.SHOWN)
                        .contains(newState);
        if (isShowing) {
            this.getQueryViewModel().setSearchEnabled(true);
            this.searchViewOnBackPressedCallback.setEnabled(true);
            this.requireLttrsActivity().lockDrawerLayout();
        } else {
            this.getQueryViewModel().setSearchEnabled(false);
            this.searchViewOnBackPressedCallback.setEnabled(false);
            requireLttrsActivity().unlockDrawerLayout();
        }
    }

    protected abstract NavigationAction getNavigationAction();

    protected enum NavigationAction {
        DRAWER,
        UP
    }
}
