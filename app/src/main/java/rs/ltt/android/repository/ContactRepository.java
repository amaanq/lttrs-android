package rs.ltt.android.repository;

import android.app.Application;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import com.google.common.collect.Lists;
import java.util.List;
import rs.ltt.android.entity.SearchSuggestion;

public class ContactRepository extends AbstractMuaRepository {
    public ContactRepository(Application application, long accountId) {
        super(application, accountId);
    }

    public LiveData<List<SearchSuggestion>> getContactSuggestions(final String term) {
        return Transformations.map(
                this.database.contactDao().getContactSuggestions(String.format("%s%%", term)),
                contacts ->
                        Lists.transform(
                                contacts,
                                contact ->
                                        new SearchSuggestion(
                                                SearchSuggestion.Type.BY_CONTACT, contact)));
    }
}
