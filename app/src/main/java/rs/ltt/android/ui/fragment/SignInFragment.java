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

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.R;
import rs.ltt.android.databinding.FragmentSignInBinding;

public class SignInFragment extends AbstractSetupFragment {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignInFragment.class);

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            LOGGER.info("POST_NOTIFICATIONS permission was granted");
                        }
                        setupViewModel.enterEmailAddress();
                    });

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        final FragmentSignInBinding binding =
                DataBindingUtil.inflate(inflater, R.layout.fragment_sign_in, container, false);
        binding.setSetupViewModel(setupViewModel);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.emailAddress.requestFocus();
        binding.next.setOnClickListener((v) -> enterEmailAddress());
        binding.emailAddress.setOnEditorActionListener(
                (v, actionId, event) -> {
                    if (event == null || event.getAction() == KeyEvent.ACTION_UP) {
                        enterEmailAddress();
                    }
                    return true;
                });
        return binding.getRoot();
    }

    private void enterEmailAddress() {
        if (!setupViewModel.checkEmailAddress()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                                requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            LOGGER.info("launching POST_NOTIFICATIONS permission request");
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            setupViewModel.enterEmailAddress();
        }
    }
}
