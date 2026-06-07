package com.milestoneflow.identity.application.command;

import java.util.Objects;

/**
 * Command for refresh token rotation.
 *
 * <p>Contains the raw refresh token value extracted from the MF_REFRESH cookie.
 * toString() is overridden to prevent token leakage in logs.
 */
public final class RefreshTokenCommand {

    private final String rawRefreshToken;

    public RefreshTokenCommand(String rawRefreshToken) {
        this.rawRefreshToken = Objects.requireNonNull(rawRefreshToken,
                "rawRefreshToken must not be null");
    }

    public String getRawRefreshToken() {
        return rawRefreshToken;
    }

    @Override
    public String toString() {
        return "RefreshTokenCommand{rawRefreshToken=[REDACTED]}";
    }
}
