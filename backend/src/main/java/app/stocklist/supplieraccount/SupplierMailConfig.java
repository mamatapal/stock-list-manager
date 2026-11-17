package app.stocklist.supplieraccount;

import app.stocklist.config.AppProperties;
import app.stocklist.mail.OutgoingMail;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Same rule as the owner reset mail: real email when outgoing email is ready (SMTP or the Gmail API, see
 * {@link OutgoingMail}), from SUPPLIER_MAIL_FROM or else MAIL_FROM; otherwise the logging stand-in.
 */
@Configuration
public class SupplierMailConfig {
    @Bean
    SupplierMailer supplierMailer(OutgoingMail mail, AppProperties app, SupplierProperties props) {
        if (mail.ready()) {
            return new SmtpSupplierMailer(mail.sender(), mail.supplierFrom(), props);
        }
        return new LoggingSupplierMailer(app.mailLogTokens());
    }
}
