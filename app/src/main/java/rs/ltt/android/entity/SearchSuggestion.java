package rs.ltt.android.entity;

import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;

public class SearchSuggestion implements Comparable<SearchSuggestion> {

    public final Type type;
    public final String value;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchSuggestion that = (SearchSuggestion) o;
        return type == that.type && Objects.equal(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, value);
    }

    public SearchSuggestion(Type type, String value) {
        this.type = type;
        this.value = value;
    }

    @Override
    public int compareTo(final SearchSuggestion o) {
        return ComparisonChain.start()
                .compareTrueFirst(this instanceof UserInput, o instanceof UserInput)
                .compareTrueFirst(this.type == Type.IN_EMAIL, o.type == Type.IN_EMAIL)
                .compare(this.value, o.value)
                .result();
    }

    public enum Type {
        IN_EMAIL,
        BY_CONTACT
    }

    public static SearchSuggestion userInput(final String userInput) {
        return new UserInput(userInput);
    }

    public static class UserInput extends SearchSuggestion {

        private UserInput(final String value) {
            super(Type.IN_EMAIL, value);
        }
    }
}
