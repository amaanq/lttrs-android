package rs.ltt.android.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.function.Consumer;
import rs.ltt.android.R;
import rs.ltt.android.databinding.ItemSearchSuggestionBinding;
import rs.ltt.android.entity.SearchSuggestion;

public class SearchSuggestionAdapter
        extends RecyclerView.Adapter<SearchSuggestionAdapter.SearchSuggestionViewHolder> {

    private Consumer<SearchSuggestion> onSearchSuggestionClicked;

    private static final DiffUtil.ItemCallback<SearchSuggestion> DIFF_ITEM_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull SearchSuggestion oldItem, @NonNull SearchSuggestion newItem) {
                    if (oldItem instanceof SearchSuggestion.UserInput
                            && newItem instanceof SearchSuggestion.UserInput) {
                        return true;
                    } else {
                        return oldItem.equals(newItem);
                    }
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull SearchSuggestion oldItem, @NonNull SearchSuggestion newItem) {
                    return oldItem.equals(newItem);
                }
            };

    private final AsyncListDiffer<SearchSuggestion> differ =
            new AsyncListDiffer<>(this, DIFF_ITEM_CALLBACK);

    @NonNull
    @Override
    public SearchSuggestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SearchSuggestionViewHolder(
                DataBindingUtil.inflate(
                        LayoutInflater.from(parent.getContext()),
                        R.layout.item_search_suggestion,
                        parent,
                        false));
    }

    @Override
    public void onBindViewHolder(@NonNull SearchSuggestionViewHolder holder, int position) {
        final var searchSuggestion = getItem(position);
        holder.binding.setSuggestion(searchSuggestion);
        holder.binding.wrapper.setOnClickListener(
                v -> onSearchSuggestionClicked.accept(searchSuggestion));
    }

    public void setOnSearchSuggestionClicked(final Consumer<SearchSuggestion> consumer) {
        this.onSearchSuggestionClicked = consumer;
    }

    private SearchSuggestion getItem(int position) {
        return this.differ.getCurrentList().get(position);
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    public void submitList(final List<SearchSuggestion> searchSuggestions) {

        this.differ.submitList(searchSuggestions);
    }

    protected static class SearchSuggestionViewHolder extends RecyclerView.ViewHolder {

        public final ItemSearchSuggestionBinding binding;

        public SearchSuggestionViewHolder(@NonNull ItemSearchSuggestionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
