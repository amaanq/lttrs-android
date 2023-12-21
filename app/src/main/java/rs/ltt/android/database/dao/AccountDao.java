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

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.HttpUrl;
import rs.ltt.android.entity.AccountEntity;
import rs.ltt.android.entity.AccountName;
import rs.ltt.android.entity.AccountWithCredentials;
import rs.ltt.android.entity.CredentialsEntity;
import rs.ltt.jmap.client.http.HttpAuthentication;
import rs.ltt.jmap.common.entity.Account;

@Dao
public abstract class AccountDao {

    @Query(
            "select account.id as"
                + " id,credentialsId,authenticationScheme,username,password,sessionResource,accountId,name"
                + " from credentials join account on credentialsId = credentials.id where"
                + " account.id=:id limit 1")
    public abstract ListenableFuture<AccountWithCredentials> getAccountFuture(Long id);

    @Query(
            "select account.id as"
                + " id,credentialsId,authenticationScheme,username,password,sessionResource,accountId,name"
                + " from credentials join account on credentialsId = credentials.id where"
                + " account.credentialsId=:credentialsId limit 1")
    public abstract ListenableFuture<AccountWithCredentials> getAnyAccountFuture(
            Long credentialsId);

    @Query(
            "select account.id as"
                + " id,credentialsId,authenticationScheme,username,password,sessionResource,accountId,name"
                + " from credentials join account on credentialsId = credentials.id")
    public abstract ListenableFuture<List<AccountWithCredentials>> getAccounts();

    @Query(
            "select account.id as"
                + " id,credentialsId,authenticationScheme,username,password,sessionResource,accountId,name"
                + " from credentials join account on credentialsId = credentials.id where"
                + " account.id=:id limit 1")
    public abstract AccountWithCredentials getAccount(Long id);

    @Query(
            "select account.id as"
                + " id,account.credentialsId,authenticationScheme,username,password,sessionResource,accountId,name"
                + " FROM credentials JOIN account ON account.credentialsId = credentials.id JOIN"
                + " push_subscription ON push_subscription.credentialsId = credentials.id WHERE"
                + " account.accountId=:accountId and deviceClientId=:deviceClientId limit 1")
    public abstract AccountWithCredentials getAccount(UUID deviceClientId, String accountId);

    @Query("select id,name from account where id=:id limit 1")
    public abstract LiveData<AccountName> getAccountNameLiveData(Long id);

    @Query("select id,name from account where id=:id limit 1")
    public abstract AccountName getAccountName(Long id);

    @Query("select id,name from account order by name")
    public abstract LiveData<List<AccountName>> getAccountNames();

    @Query("select id from account")
    public abstract LiveData<List<Long>> getAccountIds();

    @Query("select id from account WHERE credentialsId=:credentialsId")
    public abstract ListenableFuture<List<Long>> getAccountIds(final long credentialsId);

    @Query("select id from account order by selected desc limit 1")
    public abstract Long getMostRecentlySelectedAccountId();

    @Query("select exists (select 1 from account)")
    public abstract boolean hasAccounts();

    @Query("delete from account where id=:id")
    abstract void deleteAccount(final Long id);

    @Query("delete from credentials where id=:id")
    abstract void deleteCredentials(final Long id);

    @Query("select exists (select 1 from account where credentialsId=:credentialsId)")
    abstract boolean hasAccountsWithCredentialsId(final Long credentialsId);

    @Insert
    abstract Long insert(CredentialsEntity entity);

    @Insert
    abstract Long insert(AccountEntity entity);

    @Transaction
    public List<AccountWithCredentials> insert(
            final HttpAuthentication.Scheme authenticationScheme,
            final String username,
            final String password,
            final HttpUrl sessionResource,
            final Map<String, Account> accounts) {
        final ImmutableList.Builder<AccountWithCredentials> builder = ImmutableList.builder();
        final Long credentialId =
                insert(
                        new CredentialsEntity(
                                authenticationScheme, username, password, sessionResource));
        for (final Map.Entry<String, Account> entry : accounts.entrySet()) {
            final String accountId = entry.getKey();
            final String name = entry.getValue().getName();
            final Long id = insert(new AccountEntity(credentialId, accountId, name));
            builder.add(
                    new AccountWithCredentials(
                            credentialId,
                            id,
                            accountId,
                            name,
                            authenticationScheme,
                            username,
                            password,
                            sessionResource));
        }
        return builder.build();
    }

    @Transaction
    public boolean delete(final AccountWithCredentials account) {
        final var credentials = account.getCredentials();
        deleteAccount(account.getId());
        if (hasAccountsWithCredentialsId(credentials.getId())) {
            return false;
        }
        deleteCredentials(credentials.getId());
        return true;
    }

    @Query("update account set selected=1 where id=:id")
    abstract void setSelected(final Long id);

    @Query("update account set selected=0 where id is not :id")
    abstract void setNotSelected(final Long id);

    @Transaction
    public void selectAccount(final Long id) {
        setSelected(id);
        setNotSelected(id);
    }

    @Query(
            "SELECT id,authenticationScheme,username,password,sessionResource FROM credentials"
                    + " WHERE id=:credentialsId")
    public abstract ListenableFuture<AccountWithCredentials.Credentials> getCredentials(
            Long credentialsId);
}
