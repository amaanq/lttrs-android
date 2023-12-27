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

package rs.ltt.android.database.dao;

import static androidx.room.OnConflictStrategy.IGNORE;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;
import rs.ltt.android.entity.SearchSuggestion;
import rs.ltt.android.entity.SearchSuggestionEntity;

@Dao
public abstract class SearchSuggestionDao {

    @Insert(onConflict = IGNORE)
    public abstract void insert(final SearchSuggestionEntity entity);

    @Query("select `query` from search_suggestion")
    public abstract List<String> getSearchQueries();

    @Query(
            "select type,`query` as value from search_suggestion WHERE type IS NOT NULL AND `query`"
                    + " IS NOT NULL AND `query` LIKE :term")
    public abstract LiveData<List<SearchSuggestion>> getSearchSuggestions(final String term);
}
