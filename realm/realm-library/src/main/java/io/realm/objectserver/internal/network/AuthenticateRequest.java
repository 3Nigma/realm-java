/*
 * Copyright 2016 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.objectserver.internal.network;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.realm.objectserver.internal.Token;
import io.realm.objectserver.Credentials;
import io.realm.objectserver.SyncManager;

/**
 * This class encapsulates a request to authenticate a user on the Realm Authentication Server. It is responsible for
 * constructing the JSON understood by the Realm Authentication Server.
 */
public class AuthenticateRequest {

    private final Provider provider;
    private final String data;
    private final String appId;
    private final Map<String, Object> userInfo;
    private final String path;

    /**
     * Generates a proper login request for a new user.
     */
    public static AuthenticateRequest fromCredentials(Credentials credentials, boolean createUser) {
        if (credentials == null) {
           throw new IllegalArgumentException("Non-null credentials required.");
        }
        Provider provider;
        String data;
        String appId = SyncManager.APP_ID;
        Map<String, Object> userInfo = new HashMap<String, Object>();
        userInfo.put("register", createUser);

        switch (credentials.getLoginType()) {
            case FACEBOOK:
                provider = Provider.FACEBOOK;
                data = credentials.getField1();
                break;
            case USERNAME_PASSWORD:
                provider = Provider.PASSWORD;
                data = credentials.getField1();
                userInfo.put("password", credentials.getField2());
                break;
            default:
                throw new IllegalArgumentException("Login type not supported: " + credentials.getLoginType());
        }

        return new AuthenticateRequest(provider, data, appId, null, userInfo);
    }

    /**
     * Authenticate access to a given Realm using an already logged in user.
     *
     * @param refreshToken Users refresh token
     * @param path Path of the Realm to gain access to.
     */
    public static AuthenticateRequest fromRefreshToken(Token refreshToken, URI path) {
        // Authenticate a given Realm path using an already logged in user.
        return new AuthenticateRequest(Provider.REALM,
                refreshToken.value(),
                SyncManager.APP_ID,
                path.getPath(),
                Collections.<String, Object>emptyMap()
        );
    }

    /**
     * Create an admin user request. Admin access gives access to all Realms. Admin access is disabled if the
     * Authentication Server is in production mode.
     */
    public static AuthenticateRequest adminAccess() {
        return debug("admin", null);
    }

    /**
     * Creates an debug user request. Debug users are automatically logged into the Realm Authentication Server, and
     * will always be granted access. Debug users are disabled if the Authentication Server is in production mode.
     */
    public static AuthenticateRequest debug(String username, String path) {
        return new AuthenticateRequest(
                Provider.DEBUG,
                username,
                SyncManager.APP_ID,
                path,
                Collections.<String, Object>emptyMap()
        );
    }

    private AuthenticateRequest(Provider provider, String data, String appId, String path, Map<String, Object> userInfo) {
        this.provider = provider;
        this.data = data;
        this.appId = appId;
        this.path = path;
        this.userInfo = userInfo;
    }

    /**
     * Converts the request into a JSON payload.
     */
    public String toJson() {
        JSONObject request = new JSONObject();
        try {
            request.put("provider", provider.getProvider());
            request.put("data", data);
            request.put("app_id", appId);
            if (path != null) {
                request.put("path", path);
            }
            request.put("user_info", new JSONObject(userInfo));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return request.toString();
    }

    private enum Provider {
        REALM("realm"), // Used if you already have a valid refresh token
        DEBUG("debug"), // Will always succeed
        PASSWORD("password"), // password/username login
        FACEBOOK("facebook"); // facebook login

        private final String provider;

        Provider(String provider) {
            this.provider = provider;
        }

        public String getProvider() {
            return provider;
        }
    }

}
