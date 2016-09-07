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

import io.realm.objectserver.ErrorCode;
import io.realm.objectserver.internal.Token;

public class RefreshResponse {
    private Token refreshToken = null;
    private ErrorCode errorCode = null;
    private String errorMessage = null;

    public RefreshResponse(Token refreshToken, ErrorCode errorCode, String errorMessage) {
        this.refreshToken = refreshToken;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public boolean isValid() {
        return false;
    }

    public Token getRefreshToken() {
        return refreshToken;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
