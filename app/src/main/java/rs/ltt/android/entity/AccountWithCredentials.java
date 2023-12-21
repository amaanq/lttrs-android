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

package rs.ltt.android.entity;

import com.google.common.base.Objects;
import okhttp3.HttpUrl;
import rs.ltt.jmap.client.http.BasicAuthHttpAuthentication;
import rs.ltt.jmap.client.http.BearerAuthHttpAuthentication;
import rs.ltt.jmap.client.http.HttpAuthentication;

public class AccountWithCredentials {

    private final Long id;
    private final Long credentialsId;
    private final String accountId;
    private final String name;
    private final HttpAuthentication.Scheme authenticationScheme;
    private final String username;
    private final String password;
    private final HttpUrl sessionResource;

    public AccountWithCredentials(
            final Long id,
            final Long credentialsId,
            final String accountId,
            final String name,
            final HttpAuthentication.Scheme authenticationScheme,
            final String username,
            final String password,
            final HttpUrl sessionResource) {
        this.credentialsId = credentialsId;
        this.id = id;
        this.accountId = accountId;
        this.name = name;
        this.authenticationScheme = authenticationScheme;
        this.username = username;
        this.password = password;
        this.sessionResource = sessionResource;
    }

    /**
     * @return The internal database ID
     */
    public Long getId() {
        return id;
    }

    /**
     * @return The JMAP account ID as found in the session resource
     */
    public String getAccountId() {
        return accountId;
    }

    /**
     * @return The display name ('user-friendly string') of the account as found in the account
     *     object in the session resource
     */
    public String getName() {
        return name;
    }

    public Credentials getCredentials() {
        return new Credentials(
                credentialsId, authenticationScheme, username, password, sessionResource);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountWithCredentials that = (AccountWithCredentials) o;
        return Objects.equal(id, that.id)
                && Objects.equal(credentialsId, that.credentialsId)
                && Objects.equal(accountId, that.accountId)
                && Objects.equal(name, that.name)
                && authenticationScheme == that.authenticationScheme
                && Objects.equal(username, that.username)
                && Objects.equal(password, that.password)
                && Objects.equal(sessionResource, that.sessionResource);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
                id,
                credentialsId,
                accountId,
                name,
                authenticationScheme,
                username,
                password,
                sessionResource);
    }

    public static class Credentials {

        private final Long id;

        private final HttpAuthentication.Scheme authenticationScheme;
        private final String username;
        private final String password;
        private final HttpUrl sessionResource;

        public Credentials(
                final Long id,
                HttpAuthentication.Scheme authenticationScheme,
                String username,
                String password,
                HttpUrl sessionResource) {
            this.id = id;
            this.authenticationScheme = authenticationScheme;
            this.username = username;
            this.password = password;
            this.sessionResource = sessionResource;
        }

        public HttpAuthentication asHttpAuthentication() {
            if (authenticationScheme == null
                    || authenticationScheme == HttpAuthentication.Scheme.BASIC) {
                return new BasicAuthHttpAuthentication(username, password);
            } else if (authenticationScheme == HttpAuthentication.Scheme.BEARER) {
                return new BearerAuthHttpAuthentication(username, password);
            } else {
                throw new IllegalStateException(
                        "Could not create HttpAuthentication with supplied scheme");
            }
        }

        /**
         * @return The URL of the session resource when different from the .well-known/jmap or null
         *     otherwise
         */
        public HttpUrl getSessionResource() {
            return sessionResource;
        }

        public Long getId() {
            return this.id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Credentials that = (Credentials) o;
            return Objects.equal(id, that.id)
                    && authenticationScheme == that.authenticationScheme
                    && Objects.equal(username, that.username)
                    && Objects.equal(password, that.password)
                    && Objects.equal(sessionResource, that.sessionResource);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id, authenticationScheme, username, password, sessionResource);
        }
    }
}
