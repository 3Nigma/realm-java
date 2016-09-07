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

package io.realm.objectserver;

import java.util.HashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.realm.RealmAsyncTask;
import io.realm.internal.Keep;
import io.realm.internal.Util;
import io.realm.objectserver.internal.syncpolicy.SyncPolicy;
import io.realm.objectserver.internal.Token;
import io.realm.objectserver.internal.network.AuthenticateResponse;
import io.realm.objectserver.internal.network.AuthenticationServer;
import io.realm.objectserver.internal.network.NetworkStateReceiver;
import io.realm.log.RealmLog;

/**
 * This class controls the connection to a Realm Object Server for one Realm.
 * <p>
 * A Session is created by either calling {@link SyncManager#getSession(SyncConfiguration)} or by opening
 * a Realm instance. Once a session has been created it will continue to exist until explicitly closed or the
 * underlying Realm file is deleted.
 * <p>
 * It is normally not necessary to interact directly with a session. That should be done by the {@link SyncPolicy}
 * defined using {@link io.realm.objectserver.SyncConfiguration.Builder#syncPolicy(SyncPolicy)}.
 * <p>
 * A session has a lifecycle consisting of the following states:
 * <p>
 * <ol>
 * <li>
 *     <b>INITIAL</b> Initial state when creating the Session object. No connections to the object server have been
 *     made yet. At this point it is possible to register any relevant error and event listeners. Calling
 *     {@link #start()} will cause the session to become <b>unbound</b> and notify the {@link SyncPolicy} that the
 *     session is ready by calling {@link SyncPolicy#onSessionCreated(Session)}.
 * </li>
 * <li>
 *     <b>UNBOUND</b> When a session is unbound, no synchronization between the local and remote Realm is happening.
 *     Call {@link #bind()} to start synchronizing changes.
 * </li>
 * <li>
 *     <b>BINDING</b> A session is in the process of binding a local Realm to a remote one. Calling {@link #unbind()}
 *     at this stage, will cancel the process. If binding fails, the session will revert to being unbound and the error
 *     will be reported to the error handler.
 *
 *     During binding, if a users access has expired, the session will be <b>AUTHENTICATING</b>. During this state,
 *     Realm will automatically try to acquire new valid credentials. If this succeed <b>BINDING</b> will
 *     automatically be resumed, if not, the session will become <b>UNBOUND</b> and an appropriate error reported.
 * </li>
 * <li>
 *     <b>BOUND</b> A bound session has an active connection to the remote Realm and will synchronize any changes
 *     immediately.
 * </li>
 * <li>
 *     <b>STOPPED</b> The session has been stopped and no longer work. A new session will be created the next time
 *     either the Realm is opened or {@link SyncManager#getSession(SyncConfiguration)} is called.
 * </li>
 * </ol>
 *
 * This object is thread safe.
 *
 * @see io.realm.objectserver.SyncConfiguration.Builder#syncPolicy(SyncPolicy)
 */
@Keep
public final class Session {

    private final HashMap<SessionState, FsmState> FSM = new HashMap<SessionState, FsmState>();

    // Variables used by the FSM
    final SyncConfiguration configuration;
    final AuthenticationServer authServer;
    private final ErrorHandler errorHandler;
    public long nativeSessionPointer;
    final User user;
    RealmAsyncTask networkRequest;
    NetworkStateReceiver.ConnectionListener networkListener;
    private SyncPolicy syncPolicy;

    // Keeping track of currrent FSM state
    SessionState currentStateDescription;
    FsmState currentState;

    /**
     /**
     * Creates a new Object Server Session
     *
     * @param syncConfiguration Sync configuration defining this session
     * @param authServer Authentication server used to refresh credentials if needed
     * @param policy Sync Policy to use by this Session.
     */
    public Session(SyncConfiguration syncConfiguration, AuthenticationServer authServer, SyncPolicy policy) {
        this.configuration = syncConfiguration;
        this.user = configuration.getUser();
        this.authServer = authServer;
        this.errorHandler = configuration.getErrorHandler();
        this.syncPolicy = policy;
        setupStateMachine();
    }

    private void setupStateMachine() {
        FSM.put(SessionState.INITIAL, new InitialState());
        FSM.put(SessionState.UNBOUND, new UnboundState());
        FSM.put(SessionState.BINDING, new BindingState());
        FSM.put(SessionState.AUTHENTICATING, new AuthenticatingState());
        FSM.put(SessionState.BOUND, new BoundState());
        FSM.put(SessionState.STOPPED, new StoppedState());
        RealmLog.debug("Session started: " + configuration.getServerUrl());
        currentState = FSM.get(SessionState.INITIAL);
        currentState.entry(this);
    }

    // Goto the next state. The FsmState classes are responsible for calling this method as a reaction to a FsmAction
    // being called or an internal action triggering a state transition.
    void nextState(SessionState nextStateDescription) {
        currentState.exit();
        FsmState nextState = FSM.get(nextStateDescription);
        if (nextState == null) {
            throw new IllegalStateException("No state was configured to handle: " + nextStateDescription);
        }
        RealmLog.debug("Session[%s]: %s -> %s", configuration.getServerUrl(), currentStateDescription, nextStateDescription);
        currentStateDescription = nextStateDescription;
        currentState = nextState;
        nextState.entry(this);
    }

    /**
     * Starts the session. This will cause the session to come <i>unbound</i>. {@link #bind()} must be called to
     * actually start synchronizing data.
     */
    public synchronized void start() {
        currentState.onStart();
    }

    /**
     * Stops the session. The session can no longer be used.
     */
    public synchronized void stop() {
        currentState.onStop();
    }

    /**
     * Binds the local Realm to the remote Realm. Once bound, changes to either the local or Remote Realm will be
     * synchronized immediately.
     *
     * While this method will return immediately, binding a Realm is not guaranteed to succeed. Possible reasons for
     * failure could be either if the device is offline or credentials have expired. Binding is an asynchronous
     * operation and all errors will be sent first to {@link SyncPolicy#onError(Session, ObjectServerError)} and if the
     * SyncPolicy didn't handle it, to the {@link ErrorHandler} defined by
     * {@link SyncConfiguration.Builder#errorHandler(ErrorHandler)}.
     */
    public synchronized void bind() {
        currentState.onBind();
    }

    /**
     * Stops a local Realm from synchronizing changes with the remote Realm.
     *
     * It is possible to call {@link #bind()} again after a Realm has been unbound.
     */
    public synchronized void unbind() {
        currentState.onUnbind();
    }


    /**
     * // FIXME This method shouldn't be public
     * Notify the session that an error has occurred.
     * @param error the kind of err
     */
    public synchronized void onError(ObjectServerError error) {
        currentState.onError(error); // FSM needs to respond to the error first, before notifying the User
        if (errorHandler != null) {
            errorHandler.onError(this, error);
        }
    }

    // Called from Session.cpp and SyncMaanger
    // This callback will happen on the thread running the Sync Client.
    void notifySessionError(int errorCode, String errorMessage) {
        ObjectServerError error = new ObjectServerError(ErrorCode.fromInt(errorCode), errorMessage);
        onError(error);
    }

    /**
     * Checks if the local Realm is bound to the remote Realm and can synchronize any changes happening on either
     * side.
     *
     * @return {@code true} if the local Realm is bound to the remote Realm, {@code false} otherwise.
     */
    public boolean isBound() {
        return currentStateDescription == SessionState.BOUND;
    }

    //
    // Package protected methods used by the FSM states to manipulate session variables.
    //

    // Create a native session. The session abstraction in Realm Core doesn't support multiple calls to bind()/unbind()
    // yet, so the Java SyncSession must manually create/and close the native sessions as needed.
    void createNativeSession() {
        nativeSessionPointer = nativeCreateSession(configuration.getPath());
    }

    void stopNativeSession() {
        if (nativeSessionPointer != 0) {
            nativeUnbind(nativeSessionPointer);
            nativeSessionPointer = 0;
        }
    }

    // Bind with proper access tokens
    // Access tokens are presumed to be present and valid at this point
    void bindWithTokens() {
        Token accessToken = user.getAccessToken(configuration.getServerUrl());
        if (accessToken == null) {
            throw new IllegalStateException("User '" + user.toString() + "' does not have an access token for "
                    + configuration.getServerUrl());
        }
        nativeBind(nativeSessionPointer, configuration.getServerUrl().toString(), accessToken.value());
    }

    // Authenticate by getting access tokens for the specific Realm
    void authenticateRealm(final Runnable onSuccess, final Session.ErrorHandler errorHandler) {
        if (networkRequest != null) {
            networkRequest.cancel();
        }
        // Authenticate in a background thread. This allows incremental backoff and retries in a safe manner.
        Future<?> task = SyncManager.NETWORK_POOL_EXECUTOR.submit(new Runnable() {
            @Override
            public void run() {
                int attempt = 0;
                boolean success;
                ObjectServerError error = null;
                while (true) {
                    attempt++;
                    long sleep = Util.calculateExponentialDelay(attempt - 1, TimeUnit.MINUTES.toMillis(5));
                    if (sleep > 0) {
                        try {
                            Thread.sleep(sleep);
                        } catch (InterruptedException e) {
                            return; // Abort authentication if interrupted.
                        }
                    }

                    AuthenticateResponse response = authServer.authenticateRealm(
                            user.getRefreshToken(),
                            configuration.getServerUrl(),
                            user.getAuthenticationUrl()
                    );
                    if (response.isValid()) {
                        user.addAccessToken(configuration.getServerUrl(), response.getAccessToken());
                        success = true;
                        break;
                    } else {
                        // Only retry in case of IO exceptions, since that might be network timeouts etc.
                        // All other errors indicate a bigger problem, so stop trying to authenticate and
                        // unbind
                        ObjectServerError responseError = response.getError();
                        if (responseError.errorCode() != ErrorCode.IO_EXCEPTION) {
                            success = false;
                            error = responseError;
                            break;
                        }
                    }
                }

                if (success) {
                    onSuccess.run();
                } else {
                    errorHandler.onError(Session.this, error);
                }
            }
        });
        networkRequest = new RealmAsyncTask(task, SyncManager.NETWORK_POOL_EXECUTOR);
    }

    public boolean isAuthenticated(SyncConfiguration configuration) {
        Token token = user.getAccessToken(configuration.getServerUrl());
        return token != null && token.expiresMs() > System.currentTimeMillis();
    }

    public SyncConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (currentStateDescription != SessionState.STOPPED) {
            RealmLog.warn("Session was not closed before being finalized. This is a potential resource leak.");
            stop();
        }
    }

    private native long nativeCreateSession(String localRealmPath);
    private native void nativeBind(long nativeSessionPointer, String remoteRealmUrl, String userToken);
    private native void nativeUnbind(long nativeSessionPointer);
    private native void nativeRefresh(long nativeSessionPointer, String userToken);
    private native void nativeNotifyCommitHappened(long sessionPointer, long version);

    /**
     * FIXME: Find a way to keep this out of the public API. Could probably happen as part of moving everything to the
     * Object Store.
     *
     * Notify session that a commit on the device has happened.
     */
    public void notifyCommit(long version) {
        if (isBound()) {
            nativeNotifyCommitHappened(nativeSessionPointer, version);
        }
    }

    public SyncPolicy getSyncPolicy() {
        return syncPolicy;
    }

    /**
     * Interface used by both the Object Server network client and sessions to report back errors.
     *
     * @see SyncManager#setDefaultSessionErrorHandler(ErrorHandler)
     * @see io.realm.objectserver.SyncConfiguration.Builder#errorHandler(ErrorHandler)
     */
    public interface ErrorHandler {
        /**
         * Callback for errors on this session object.
         * Only errors with an ID between 0-99 and 200-299 and will be reported here.
         *
         * @param session {@link Session} this error happened on.
         * @param error type of error.
         */
        void onError(Session session, ObjectServerError error);
    }
}

