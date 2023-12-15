package rs.ltt.android.util;

import rs.ltt.jmap.common.entity.Role;
import rs.ltt.jmap.mua.util.LabelWithCount;

public class PlaceholderLabel {

    public static final Search SEARCH = new Search();
    public static final Unfiltered UNFILTERED = new Unfiltered();

    public static class Search implements LabelWithCount {

        private Search() {}

        @Override
        public Integer getCount() {
            return null;
        }

        @Override
        public String getName() {
            throw new IllegalStateException("This class is only used to identify searches");
        }

        @Override
        public Role getRole() {
            throw new IllegalStateException("This class is only used to identify searches");
        }
    }

    public static class Unfiltered implements LabelWithCount {

        private Unfiltered() {}

        @Override
        public Integer getCount() {
            return null;
        }

        @Override
        public String getName() {
            throw new IllegalStateException(
                    "This class is only used to identify an unfiltered query");
        }

        @Override
        public Role getRole() {
            return Role.ALL;
        }
    }
}
