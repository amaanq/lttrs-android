package rs.ltt.android.util;

import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/* Copyright 2019 Google LLC.
SPDX-License-Identifier: Apache-2.0 */
public class LiveData {

    private LiveData() {}

    public static <T> T getOrAwaitValue(final androidx.lifecycle.LiveData<T> liveData)
            throws InterruptedException {
        final Object[] data = new Object[1];
        final CountDownLatch latch = new CountDownLatch(1);
        final Observer<T> observer =
                new Observer<T>() {
                    @Override
                    public void onChanged(@Nullable T o) {
                        data[0] = o;
                        latch.countDown();
                        liveData.removeObserver(this);
                    }
                };
        MainThreadExecutor.getInstance().execute(() -> liveData.observeForever(observer));

        // Don't wait indefinitely if the LiveData is not set.
        if (!latch.await(2, TimeUnit.SECONDS)) {
            throw new RuntimeException("LiveData value was never set.");
        }
        //noinspection unchecked
        return (T) data[0];
    }
}
