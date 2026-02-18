package com.betting.marketdata.api;

/**
 * Handler interface for authorization flows that require user interaction.
 */
public interface AuthorizationHandler {

    /**
     * Called when user authorization is required.
     * The implementation should present the authorization URL to the user
     * and return the verification/authorization code.
     *
     * @param authorizationUrl the URL the user must visit to authorize
     * @return the verification code entered by the user, or null to cancel
     */
    String handleAuthorization(String authorizationUrl);
}
