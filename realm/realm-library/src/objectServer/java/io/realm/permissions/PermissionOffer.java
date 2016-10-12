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
package io.realm.permissions;

import java.util.Date;
import java.util.UUID;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.Required;

/**
 * TODO
 */
public class PermissionOffer extends RealmObject {

    // Base fields
    @PrimaryKey
    @Required
    private String id = UUID.randomUUID().toString();
    @Required
    private Date createdAt;
    @Required
    private Date updatedAt;
    private Integer statusCode; // nil=not processed, 0=success, >0=error
    private String statusMessage;

    // Offer fields
    @Required
    private String token = "";
    @Required
    private String realmUrl;
    private boolean mayRead;
    private boolean mayWrite;
    private boolean mayManage;
    private Date expiresAt;

    /**
     * TODO
     */
    public PermissionOffer() {
        this.realmUrl = "";
        this.mayRead = false;
        this.mayWrite= false;
        this.mayManage = false;
    }

    /**
     * TODO
     */
    public PermissionOffer(String url, boolean mayRead, boolean mayWrite, boolean mayManage) {
        if (url == null) {
            throw new IllegalArgumentException("Non-null 'url' required.");
        }
        this.realmUrl = url;
        this.mayRead = mayRead;
        this.mayWrite= mayWrite;
        this.mayManage = mayManage;
    }

    public void setRealmUrl(String realmUrl) {
        this.realmUrl = realmUrl;
    }

    public void setMayRead(boolean mayRead) {
        this.mayRead = mayRead;
    }

    public void setMayWrite(boolean mayWrite) {
        this.mayWrite = mayWrite;
    }

    public void setMayManage(boolean mayManage) {
        this.mayManage = mayManage;
    }

    public void setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getId() {
        return id;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public String getToken() {
        return token;
    }

    public String getRealmUrl() {
        return realmUrl;
    }

    public boolean isMayRead() {
        return mayRead;
    }

    public boolean isMayWrite() {
        return mayWrite;
    }

    public boolean isMayManage() {
        return mayManage;
    }

    public Date getExpiresAt() {
        return expiresAt;
    }
}