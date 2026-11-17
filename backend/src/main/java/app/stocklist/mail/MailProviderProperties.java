package app.stocklist.mail;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How outgoing email leaves the server (prefix app.mail). {@code smtp} (the default, also when MAIL_PROVIDER is unset
 * or empty) keeps the spring.mail settings exactly as before; {@code gmail-api} sends through the Gmail API with
 * OAuth 2.0 (see docs/EMAIL_SETUP.md). An unknown value stops the backend at startup.
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProviderProperties(Provider provider, Gmail gmail) {

    public enum Provider { SMTP, GMAIL_API }

    public MailProviderProperties {
        if (provider == null) provider = Provider.SMTP;
        if (gmail == null) gmail = new Gmail(null, null, null, null, null, null, null);
    }

    /**
     * Gmail API settings. The client secret of a Desktop OAuth client is not confidential (public client); the refresh
     * token is the only real secret. toString never shows either.
     *
     * @param sender        the Gmail account the token belongs to (GMAIL_SENDER)
     * @param sendAsAliases optional comma-separated "Send mail as" aliases of that account (GMAIL_SEND_AS_ALIASES)
     * @param tokenUri      only changed by tests
     * @param apiBaseUri    only changed by tests
     */
    public record Gmail(String sender, String sendAsAliases, String clientId, String clientSecret, String refreshToken,
                        URI tokenUri, URI apiBaseUri) {

        public Gmail {
            if (tokenUri == null) tokenUri = GoogleAccessTokens.DEFAULT_TOKEN_URI;
            if (apiBaseUri == null) apiBaseUri = GmailApiMailSender.DEFAULT_API_BASE;
        }

        @Override
        public String toString() {
            return "Gmail[sender=" + sender + ", sendAsAliases=" + sendAsAliases + ", clientId=" + clientId
                    + ", clientSecret=(hidden), refreshToken=(hidden)]";
        }
    }
}
