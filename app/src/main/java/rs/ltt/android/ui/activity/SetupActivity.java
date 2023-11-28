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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.R;
import rs.ltt.android.SetupNavigationDirections;
import rs.ltt.android.databinding.ActivitySetupBinding;
import rs.ltt.android.ui.MaterialAlertDialogs;
import rs.ltt.android.ui.model.SetupViewModel;
import rs.ltt.android.util.Event;
import rs.ltt.android.util.NavControllers;
import rs.ltt.jmap.mua.util.MailToUri;

public class SetupActivity extends AppCompatActivity {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetupActivity.class);

    public static String EXTRA_NEXT_ACTION = "rs.ltt.android.extras.next-action";
    private SetupViewModel setupViewModel;

    private final OnBackPressedCallback loadingBackPressedCallback =
            new OnBackPressedCallback(false) {
                @Override
                public void handleOnBackPressed() {
                    if (setupViewModel.cancel()) {
                        LOGGER.info("Cancelled current tasks");
                    }
                }
            };

    public static void launch(final ComponentActivity activity) {
        final Intent intent = new Intent(activity, SetupActivity.class);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ActivitySetupBinding binding =
                DataBindingUtil.setContentView(this, R.layout.activity_setup);
        final ViewModelProvider viewModelProvider =
                new ViewModelProvider(this, getDefaultViewModelProviderFactory());
        this.setupViewModel = viewModelProvider.get(SetupViewModel.class);
        this.setupViewModel.getRedirection().observe(this, this::onRedirectionEvent);
        this.setupViewModel.getWarningMessage().observe(this, this::onWarningMessage);
        this.getOnBackPressedDispatcher().addCallback(this, this.loadingBackPressedCallback);
        this.setupViewModel
                .isLoading()
                .observe(
                        this,
                        loading ->
                                this.loadingBackPressedCallback.setEnabled(
                                        Boolean.TRUE.equals(loading)));
    }

    private void onWarningMessage(Event<String> event) {
        MaterialAlertDialogs.error(this, event);
    }

    private void onRedirectionEvent(final Event<SetupViewModel.Target> targetEvent) {
        if (targetEvent.isConsumable()) {
            final NavController navController = getNavController();
            final SetupViewModel.Target target = targetEvent.consume();
            switch (target) {
                case ENTER_PASSWORD -> navController.navigate(
                        SetupNavigationDirections.enterPassword());
                case ENTER_URL -> navController.navigate(
                        SetupNavigationDirections.enterSessionResource());
                case DONE -> redirectToLttrs(this.setupViewModel.getPrimaryAccountId());
                case IMPORT_PRIVATE_KEY -> navController.navigate(
                        SetupNavigationDirections.importPrivateKey());
                default -> throw new IllegalStateException(
                        String.format("Unable to navigate to target %s", target));
            }
        }
    }

    private NavController getNavController() {
        return NavControllers.findNavController(this, R.id.nav_host_fragment);
    }

    private void redirectToLttrs(final Long accountId) {
        final Intent currentIntent = getIntent();
        final String uri =
                currentIntent == null ? null : currentIntent.getStringExtra(EXTRA_NEXT_ACTION);
        final MailToUri mailToUri = uri == null ? null : MailToUri.parse(uri);
        if (mailToUri != null) {
            ComposeActivity.launch(this, Uri.parse(uri));
        } else {
            LttrsActivity.launch(this, accountId, false);
        }
        finish();
    }
}
