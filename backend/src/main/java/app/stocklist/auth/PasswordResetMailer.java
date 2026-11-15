package app.stocklist.auth;

import app.stocklist.user.AppUser;

/** Sends the owner's password-reset link. An SMTP implementation is added at deployment (Phase 6). */
public interface PasswordResetMailer {
    void send(AppUser user, String rawToken);
}
