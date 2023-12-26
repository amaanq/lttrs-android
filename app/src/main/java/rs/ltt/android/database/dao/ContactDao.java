package rs.ltt.android.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;
import java.util.List;

@Dao
public abstract class ContactDao {

    @Query("SELECT DISTINCT LOWER(email) FROM email_email_address WHERE email LIKE :term LIMIT 5")
    public abstract LiveData<List<String>> getContactSuggestions(final String term);
}
