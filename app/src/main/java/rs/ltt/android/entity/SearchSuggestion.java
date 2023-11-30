package rs.ltt.android.entity;

import com.google.common.base.Objects;

public class SearchSuggestion {

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

    public enum Type {
        SEARCH_IN_EMAIL,
        CONTACT
    }

    public static SearchSuggestion userInput(final String userInput) {
        return new UserInput(userInput);
    }

    public static class UserInput extends SearchSuggestion {

        private UserInput(final String value) {
            super(Type.SEARCH_IN_EMAIL, value);
        }
    }
}
