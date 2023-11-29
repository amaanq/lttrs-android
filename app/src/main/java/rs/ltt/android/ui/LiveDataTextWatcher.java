package rs.ltt.android.ui;

import android.text.Editable;
import android.text.TextWatcher;
import androidx.lifecycle.MutableLiveData;
import rs.ltt.android.util.CharSequences;

public class LiveDataTextWatcher implements TextWatcher {

    private final MutableLiveData<String> liveData;

    public LiveDataTextWatcher(final MutableLiveData<String> liveData) {
        this.liveData = liveData;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(final Editable editable) {
        this.liveData.postValue(CharSequences.nullToEmpty(editable));
    }
}
