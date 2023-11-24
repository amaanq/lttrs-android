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

package rs.ltt.android.ui.activity;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.databinding.DataBindingUtil;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.work.WorkInfo;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.LttrsApplication;
import rs.ltt.android.LttrsNavigationDirections;
import rs.ltt.android.R;
import rs.ltt.android.databinding.ActivityLttrsBinding;
import rs.ltt.android.entity.MailboxOverviewItem;
import rs.ltt.android.entity.MailboxWithRoleAndName;
import rs.ltt.android.ui.EmptyMailboxAction;
import rs.ltt.android.ui.ItemAnimators;
import rs.ltt.android.ui.ThreadModifier;
import rs.ltt.android.ui.Translations;
import rs.ltt.android.ui.adapter.NavigationAdapter;
import rs.ltt.android.ui.model.LttrsViewModel;
import rs.ltt.android.ui.notification.EmailNotification;
import rs.ltt.android.util.Event;
import rs.ltt.android.util.NavControllers;
import rs.ltt.android.worker.Failure;
import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.mua.util.KeywordLabel;
import rs.ltt.jmap.mua.util.Label;

public class LttrsActivity extends AppCompatActivity
        implements ThreadModifier,
                NavController.OnDestinationChangedListener,
                DrawerLayout.DrawerListener {

    public static final String EXTRA_ACCOUNT_ID = "account";
    public static final String EXTRA_THREAD_ID = "thread";
    private static final Logger LOGGER = LoggerFactory.getLogger(LttrsActivity.class);
    private static final List<Integer> MAIN_DESTINATIONS =
            ImmutableList.of(R.id.inbox, R.id.mailbox, R.id.keyword);
    private static final List<Integer> QUERY_DESTINATIONS =
            ImmutableList.of(R.id.inbox, R.id.mailbox, R.id.keyword, R.id.search);
    private static final List<Integer> FULL_SCREEN_DIALOG = ImmutableList.of(R.id.label_as);
    final NavigationAdapter navigationAdapter = new NavigationAdapter();
    private ActivityLttrsBinding binding;
    private LttrsViewModel lttrsViewModel;
    private WeakReference<Snackbar> mostRecentSnackbar;

    public static void launch(final FragmentActivity activity, final long accountId) {
        launch(activity, accountId, true);
    }

    public static void launch(
            final FragmentActivity activity, final long accountId, final boolean skipAnimation) {
        final Intent intent = getLaunchIntent(activity, accountId);
        activity.startActivity(intent);
        if (skipAnimation) {
            activity.overridePendingTransition(0, 0);
        }
    }

    private static Intent getLaunchIntent(final FragmentActivity activity, final long accountId) {
        final Intent intent = new Intent(activity, LttrsActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.putExtra(LttrsActivity.EXTRA_ACCOUNT_ID, accountId);
        // the default launch mode of the this activity is set to 'singleTask'
        // to view a new account we want to force recreate the activity
        // the accountId is essentially a final variable and should not be changed during a lifetime
        // of an activity
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    public static void view(
            final AppCompatActivity activity,
            final EmailNotification.Tag tag,
            final String threadId) {
        final Intent intent = viewIntent(activity, tag, threadId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }

    public static void view(
            final AppCompatActivity activity, final long accountId, final String threadId) {}

    public static Intent viewIntent(
            final Context context, final EmailNotification.Tag tag, final String threadId) {
        final Intent intent = new Intent(context, LttrsActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(tag.toUri());
        intent.putExtra(LttrsActivity.EXTRA_ACCOUNT_ID, tag.getAccountId());
        intent.putExtra(LttrsActivity.EXTRA_THREAD_ID, threadId);
        return intent;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LOGGER.debug("onCreate()");
        binding = DataBindingUtil.setContentView(this, R.layout.activity_lttrs);
        final Intent intent = getIntent();
        final long accountId;
        if (intent != null && intent.hasExtra(EXTRA_ACCOUNT_ID)) {
            accountId = intent.getLongExtra(EXTRA_ACCOUNT_ID, -1);
        } else {
            final long start = SystemClock.elapsedRealtime();
            accountId = LttrsApplication.get(this).getMostRecentlySelectedAccountId();
            LOGGER.warn(
                    "Got most recently selected account id from database in {}ms. This should not"
                            + " be happening",
                    (SystemClock.elapsedRealtime() - start));
        }

        final ViewModelProvider viewModelProvider =
                new ViewModelProvider(
                        getViewModelStore(),
                        new LttrsViewModel.Factory(getApplication(), accountId));
        lttrsViewModel = viewModelProvider.get(LttrsViewModel.class);

        binding.drawerLayout.addDrawerListener(this);

        navigationAdapter.setOnLabelSelectedListener(
                (label, currentlySelected) -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START);
                    if (currentlySelected
                            && MAIN_DESTINATIONS.contains(getCurrentDestinationId())) {
                        return;
                    }
                    final NavController navController = getNavController();
                    final boolean navigateToInbox = label.getRole() == Role.INBOX;
                    if (navigateToInbox) {
                        navController.navigate(LttrsNavigationDirections.actionToInbox());
                    } else if (label instanceof MailboxOverviewItem mailbox) {
                        navController.navigate(
                                LttrsNavigationDirections.actionToMailbox(mailbox.id));
                    } else if (label instanceof KeywordLabel keyword) {
                        navController.navigate(LttrsNavigationDirections.actionToKeyword(keyword));
                    } else {
                        throw new IllegalStateException(
                                String.format("%s is an unsupported label", label.getClass()));
                    }
                    // currently unused should remain here in case we bring scrollable toolbar back
                    // binding.appBarLayout.setExpanded(true, false);
                });
        navigationAdapter.setOnAccountViewToggledListener(
                () -> {
                    lttrsViewModel.toggleAccountSelectionVisibility();
                });
        navigationAdapter.setOnAccountSelected(
                (id -> {
                    if (id == lttrsViewModel.getAccountId()) {
                        closeDrawer(true);
                    } else {
                        closeDrawer(false);
                        lttrsViewModel.setSelectedAccount(id);
                        launch(this, id);
                    }
                }));
        navigationAdapter.setOnAdditionalNavigationItemSelected(
                (type -> {
                    switch (type) {
                        case ADD_ACCOUNT -> {
                            closeDrawer(false);
                            SetupActivity.launch(this);
                        }
                        case MANAGE_ACCOUNT -> {
                            closeDrawer(false);
                            AccountManagerActivity.launch(this);
                        }
                        default -> throw new IllegalStateException(
                                String.format("Not set up to handle %s", type));
                    }
                }));
        binding.navigation.setAdapter(navigationAdapter);
        ItemAnimators.disableChangeAnimation(binding.navigation.getItemAnimator());
        lttrsViewModel.getNavigableItems().observe(this, navigationAdapter::submitList);
        lttrsViewModel.getFailureEvent().observe(this, this::onFailureEvent);
        lttrsViewModel.getSelectedLabel().observe(this, navigationAdapter::setSelectedLabel);
        lttrsViewModel
                .isAccountSelectionVisible()
                .observe(this, navigationAdapter::setAccountSelectionVisible);
        lttrsViewModel.getAccountName().observe(this, navigationAdapter::setAccountInformation);
    }

    public void closeDrawer(final boolean animate) {
        binding.drawerLayout.closeDrawer(GravityCompat.START, animate);
        lttrsViewModel.setAccountSelectionVisibility(false);
    }

    public void openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START);
    }

    private void onFailureEvent(Event<Failure> failureEvent) {
        if (failureEvent.isConsumable()) {
            final Failure failure = failureEvent.consume();
            LOGGER.info("processing failure event {}", failure.getException());
            if (failure instanceof Failure.PreExistingMailbox preExistingMailbox) {
                dismissSnackbar();
                getNavController()
                        .navigate(
                                LttrsNavigationDirections.actionToReassignRole(
                                        preExistingMailbox.getMailboxId(),
                                        preExistingMailbox.getRole().toString()));
            }
        }
    }

    public NavController getNavController() {
        return NavControllers.findNavController(this, R.id.nav_host_fragment);
    }

    private int getCurrentDestinationId() {
        final NavDestination currentDestination = getNavController().getCurrentDestination();
        return currentDestination == null ? 0 : currentDestination.getId();
    }

    @Override
    public void onStart() {
        super.onStart();
        getNavController().addOnDestinationChangedListener(this);
        final Intent intent = getIntent();
        if (handleIntent(intent)) {
            setIntent(getLaunchIntent(this, lttrsViewModel.getAccountId()));
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        getNavController().removeOnDestinationChangedListener(this);
    }

    private void configureActionBarForDestination(NavDestination destination) {
        final ActionBar actionbar = getSupportActionBar();
        if (actionbar == null) {
            return;
        }
        final int destinationId = destination.getId();
        actionbar.setDisplayHomeAsUpEnabled(true);
        @DrawableRes final int upIndicator;
        if (MAIN_DESTINATIONS.contains(destinationId)) {
            upIndicator = R.drawable.ic_menu_24dp;
        } else if (FULL_SCREEN_DIALOG.contains(destinationId)) {
            upIndicator = R.drawable.ic_close_24dp;
        } else {
            upIndicator = R.drawable.ic_arrow_back_24dp;
        }
        actionbar.setHomeAsUpIndicator(upIndicator);
        invalidateOptionsMenu();
    }

    public void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private boolean handleIntent(final Intent intent) {
        final String action = Strings.nullToEmpty(intent == null ? null : intent.getAction());
        switch (action) {
            case Intent.ACTION_SEARCH:
                handleSearchIntent(Objects.requireNonNull(intent));
                return true;
            case Intent.ACTION_VIEW:
                return handleViewIntent(Objects.requireNonNull(intent));
            default:
                return false;
        }
    }

    private boolean handleViewIntent(final Intent intent) {
        final EmailNotification.Tag tag = EmailNotification.Tag.parse(intent.getData());
        final long accountId = intent.getLongExtra(EXTRA_ACCOUNT_ID, -1L);
        final String threadId = intent.getStringExtra(EXTRA_THREAD_ID);
        if (accountId != this.lttrsViewModel.getAccountId()) {
            LOGGER.info("restarting activity to switch to account {}", accountId);
            view(this, tag, threadId);
            return false;
        }
        final NavController navController = getNavController();
        final NavOptions navOptions =
                new NavOptions.Builder()
                        .setPopUpTo(R.id.inbox, false)
                        .setEnterAnim(0)
                        .setExitAnim(0)
                        .build();
        final LttrsNavigationDirections.ActionToThread action =
                LttrsNavigationDirections.actionToThread(threadId, null, null, false);
        navController.navigate(action, navOptions);
        this.closeDrawer(false);
        EmailNotification.cancel(this, tag);
        return true;
    }

    private void handleSearchIntent(final Intent intent) {
        final String query = Strings.nullToEmpty(intent.getStringExtra(SearchManager.QUERY));
        binding.navigation.requestFocus();
        lttrsViewModel.insertSearchSuggestion(query);
        getNavController().navigate(LttrsNavigationDirections.actionSearch(query));
    }

    private void showSnackbar(final Snackbar snackbar) {
        this.mostRecentSnackbar = new WeakReference<>(snackbar);
        snackbar.show();
    }

    private void dismissSnackbar() {
        final Snackbar snackbar =
                this.mostRecentSnackbar != null ? this.mostRecentSnackbar.get() : null;
        if (snackbar != null && snackbar.isShown()) {
            LOGGER.info("Dismissing snackbar");
            snackbar.dismiss();
        }
    }

    @Override
    public void archive(Collection<String> threadIds) {
        final int count = threadIds.size();
        lttrsViewModel.archive(threadIds);
        final Snackbar snackbar =
                Snackbar.make(
                        this.binding.getRoot(),
                        getResources().getQuantityString(R.plurals.n_archived, count, count),
                        Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.undo, v -> lttrsViewModel.moveToInbox(threadIds));
        showSnackbar(snackbar);
    }

    @Override
    public void moveToInbox(final Collection<String> threadIds) {
        final int count = threadIds.size();
        final Snackbar snackbar =
                Snackbar.make(
                        binding.getRoot(),
                        getResources().getQuantityString(R.plurals.n_moved_to_inbox, count, count),
                        Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.undo, v -> lttrsViewModel.archive(threadIds));
        showSnackbar(snackbar);
        lttrsViewModel.moveToInbox(threadIds);
    }

    @Override
    public void moveToTrash(final Collection<String> threadIds) {
        final int count = threadIds.size();
        final Snackbar snackbar =
                Snackbar.make(
                        binding.getRoot(),
                        getResources().getQuantityString(R.plurals.n_deleted, count, count),
                        Snackbar.LENGTH_LONG);
        final ListenableFuture<LiveData<WorkInfo>> future = lttrsViewModel.moveToTrash(threadIds);
        snackbar.setAction(
                R.string.undo,
                v -> {
                    try {
                        final LiveData<WorkInfo> workInfoLiveData = future.get();
                        final WorkInfo workInfo = workInfoLiveData.getValue();
                        lttrsViewModel.cancelMoveToTrash(workInfo, threadIds);
                    } catch (Exception e) {
                        LOGGER.warn("Unable to cancel moveToTrash operation", e);
                    }
                });
        showSnackbar(snackbar);
        future.addListener(
                () -> {
                    try {
                        future.get()
                                .observe(
                                        this,
                                        workInfo -> {
                                            if (workInfo != null
                                                    && workInfo.getState().isFinished()
                                                    && snackbar.isShown()) {
                                                LOGGER.info(
                                                        "Dismissing Move To Trash undo snackbar"
                                                            + " prematurely because WorkInfo went"
                                                            + " into state {}",
                                                        workInfo.getState());
                                                snackbar.dismiss();
                                            }
                                        });
                    } catch (Exception e) {
                        LOGGER.warn("Unable to observe moveToTrash operation", e);
                    }
                },
                ContextCompat.getMainExecutor(this));
    }

    @Override
    public void removeFromMailbox(Collection<String> threadIds, MailboxWithRoleAndName mailbox) {
        final int count = threadIds.size();
        final Snackbar snackbar =
                Snackbar.make(
                        binding.getRoot(),
                        getResources()
                                .getQuantityString(
                                        R.plurals.n_removed_from_x, count, count, mailbox.name),
                        Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.undo, v -> lttrsViewModel.copyToMailbox(threadIds, mailbox));
        showSnackbar(snackbar);
        lttrsViewModel.removeFromMailbox(threadIds, mailbox);
    }

    @Override
    public void markRead(Collection<String> threadIds) {
        this.lttrsViewModel.markRead(threadIds);
    }

    @Override
    public void markUnread(Collection<String> threadIds) {
        this.lttrsViewModel.markUnread(threadIds);
    }

    @Override
    public void markImportant(Collection<String> threadIds) {
        this.lttrsViewModel.markImportant(threadIds);
    }

    @Override
    public void markNotImportant(Collection<String> threadIds) {
        this.lttrsViewModel.markNotImportant(threadIds);
    }

    @Override
    public void toggleFlagged(String threadId, boolean target) {
        this.lttrsViewModel.toggleFlagged(threadId, target);
    }

    @Override
    public void addFlag(Collection<String> threadIds) {
        this.lttrsViewModel.addFlag(threadIds);
    }

    @Override
    public void removeFlag(Collection<String> threadIds) {
        this.lttrsViewModel.removeFlag(threadIds);
    }

    @Override
    public void removeFromKeyword(Collection<String> threadIds, final String keyword) {
        final int count = threadIds.size();
        final Label label = KeywordLabel.of(keyword);
        final Snackbar snackbar =
                Snackbar.make(
                        binding.getRoot(),
                        getResources()
                                .getQuantityString(
                                        R.plurals.n_removed_from_x,
                                        count,
                                        count,
                                        Translations.asHumanReadableName(this, label)),
                        Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.undo, v -> lttrsViewModel.addKeyword(threadIds, keyword));
        showSnackbar(snackbar);
        lttrsViewModel.removeKeyword(threadIds, keyword);
    }

    @Override
    public void executeEmptyMailboxAction(EmptyMailboxAction action) {
        this.lttrsViewModel.executeEmptyMailboxAction(action);
    }

    @Override
    public void onBackPressed() {
        if (binding != null && binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            if (Boolean.TRUE.equals(lttrsViewModel.isAccountSelectionVisible().getValue())) {
                lttrsViewModel.setAccountSelectionVisibility(false);
                return;
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onDestinationChanged(
            @NonNull NavController controller,
            @NonNull NavDestination destination,
            @Nullable Bundle arguments) {
        LOGGER.debug("onDestinationChanged({})", destination.getLabel());
        configureActionBarForDestination(destination);
    }

    @Override
    public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {}

    @Override
    public void onDrawerOpened(@NonNull View drawerView) {}

    @Override
    public void onDrawerClosed(@NonNull View drawerView) {
        lttrsViewModel.setAccountSelectionVisibility(false);
    }

    @Override
    public void onDrawerStateChanged(int newState) {}
}
