package com.rbctcsworld.ecommerce.auth;

/** The password was right but the account asks for the e-mailed code: carries the challenge for the 200 answer. */
public class TwoFactorRequiredException extends RuntimeException {

    private final transient TwoFactorService.Challenge challenge;

    public TwoFactorRequiredException(TwoFactorService.Challenge challenge) {
        super("A sign-in code was sent to your e-mail");
        this.challenge = challenge;
    }

    public TwoFactorService.Challenge getChallenge() {
        return challenge;
    }
}
