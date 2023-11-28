/*
 * Copyright 2019-2021 Daniel Gultsch
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

package rs.ltt.android.ui.adapter;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.AdapterListUpdateCallback;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;
import com.google.common.base.Preconditions;

public class OffsetListUpdateCallback<VH extends RecyclerView.ViewHolder>
        implements ListUpdateCallback {

    private final AdapterListUpdateCallback adapterCallback;

    private final int fixedOffset;
    private final int variableOffset;
    private boolean isVariableOffsetVisible = true;

    public OffsetListUpdateCallback(
            final RecyclerView.Adapter<VH> adapter,
            final int fixedOffset,
            final int variableOffset,
            final boolean isVariableOffsetVisible) {
        this(adapter, fixedOffset, variableOffset);
        this.isVariableOffsetVisible = isVariableOffsetVisible;
    }

    public OffsetListUpdateCallback(
            final RecyclerView.Adapter<VH> adapter,
            final int fixedOffset,
            final int variableOffset) {
        Preconditions.checkArgument(fixedOffset >= 0, "fixed offset can not be negative");
        Preconditions.checkArgument(variableOffset >= 0, "variable offset can not be negative");
        this.adapterCallback = new AdapterListUpdateCallback(adapter);
        this.fixedOffset = fixedOffset;
        this.variableOffset = variableOffset;
    }

    public boolean isVariableOffsetVisible() {
        return this.isVariableOffsetVisible;
    }

    public void setVariableOffsetVisible(final boolean variableOffsetVisible) {
        if (this.isVariableOffsetVisible == variableOffsetVisible || variableOffset == 0) {
            return;
        }
        this.isVariableOffsetVisible = variableOffsetVisible;
        if (variableOffsetVisible) {
            adapterCallback.onInserted(fixedOffset, variableOffset);
        } else {
            adapterCallback.onRemoved(fixedOffset, variableOffset);
        }
    }

    @Override
    public void onInserted(int position, int count) {
        adapterCallback.onInserted(position + getCurrentOffset(), count);
    }

    @Override
    public void onRemoved(int position, int count) {
        adapterCallback.onRemoved(position + getCurrentOffset(), count);
    }

    @Override
    public void onMoved(int fromPosition, int toPosition) {
        adapterCallback.onMoved(fromPosition + getCurrentOffset(), toPosition + getCurrentOffset());
    }

    @Override
    public void onChanged(int position, int count, @Nullable Object payload) {
        adapterCallback.onChanged(position + getCurrentOffset(), count, payload);
    }

    public int getCurrentOffset() {
        return (this.isVariableOffsetVisible ? this.variableOffset : 0) + fixedOffset;
    }

    public int getFixedOffset() {
        return this.fixedOffset;
    }
}
