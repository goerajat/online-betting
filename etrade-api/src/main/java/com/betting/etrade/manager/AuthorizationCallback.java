package com.betting.etrade.manager;

/**
 * Callback interface for handling OAuth authorization flow.
 */
public interface AuthorizationCallback {

    /**
     * Called when the user needs to authorize the application.
     * The implementation should direct the user to the authorization URL
     * and return the verification code they receive after authorizing.
     *
     * @param authorizationUrl the URL the user must visit to authorize
     * @return the verification code entered by the user
     */
    String onAuthorizationRequired(String authorizationUrl);
}
