package app.stocklist.supplieraccount;

import app.stocklist.config.AppProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/** Same rule as the owner reset mail: SMTP (Amazon SES) when SMTP_HOST and MAIL_FROM are set, else the logging stand-in. */
@Configuration
public class SupplierMailConfig {
    @Bean
    SupplierMailer supplierMailer(ObjectProvider<JavaMailSender> sender, AppProperties app, SupplierProperties props,
                                  @Value("${spring.mail.host:}") String host,
                                  @Value("${app.mail-from:}") String from) {
        JavaMailSender s = sender.getIfAvailable();
        if (s != null && !host.isBlank() && !from.isBlank()) {
            return new SmtpSupplierMailer(s, from, props);
        }
        return new LoggingSupplierMailer(app.mailLogTokens());
    }
}
