package app.stocklist.mail;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** True only when app.mail.provider (MAIL_PROVIDER) binds to gmail-api, using the same rules as MailProviderProperties. */
class GmailApiSelected implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return Binder.get(context.getEnvironment())
                .bind("app.mail.provider", MailProviderProperties.Provider.class)
                .map(p -> p == MailProviderProperties.Provider.GMAIL_API)
                .orElse(false);
    }
}
