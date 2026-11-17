package app.stocklist.mail;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

/**
 * Server-only email check, run through deploy/mail-test.sh (never reachable over the network):
 * <pre>
 *   --mail-test=you@example.com
 * </pre>
 * Sends one Hindi + English test email through the configured provider from MAIL_FROM, and a second one from
 * SUPPLIER_MAIL_FROM when that address is different. Prints only success or the error type. Without --mail-test this
 * does nothing.
 */
@Component
public class MailTestCommandLine implements ApplicationRunner {
    static final String SUBJECT = "परीक्षण ईमेल / Test email - Stock List Manager";
    static final String BODY = """
            नमस्ते,

            यह Stock List Manager सर्वर से भेजा गया परीक्षण ईमेल है। अगर यह आपको मिला है, तो ईमेल भेजना ठीक से काम कर रहा है।

            ----

            Hello,

            This is a test email from the Stock List Manager server. If you received it, sending email works.

            --
            इस ईमेल का जवाब न दें। / Please do not reply to this email.
            """;

    private final OutgoingMail mail;
    private final ApplicationContext context;

    public MailTestCommandLine(OutgoingMail mail, ApplicationContext context) {
        this.mail = mail;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> values = args.getOptionValues("mail-test");
        if (values == null || values.isEmpty()) return;
        int code = 0;
        try {
            if (!mail.ready()) {
                throw new IllegalStateException("email is not configured (check MAIL_PROVIDER, MAIL_FROM and the"
                        + " settings of the provider)");
            }
            String to = SenderAddresses.address(values.get(0), "The test address");
            send(to, mail.from());
            System.out.println("OK: test email from MAIL_FROM accepted (" + label() + ").");
            if (!mail.supplierFrom().equals(mail.from())) {
                send(to, mail.supplierFrom());
                System.out.println("OK: test email from SUPPLIER_MAIL_FROM accepted (" + label() + ").");
            }
            System.out.println("Check the inbox, and the spam folder, of the test address.");
        } catch (IllegalStateException e) {
            System.err.println("ERROR: " + e.getMessage());
            code = 1;
        } catch (MailException e) {
            System.err.println("ERROR: the email could not be sent (" + e.getClass().getSimpleName()
                    + "). The backend log has no more detail than this; see docs/EMAIL_SETUP.md.");
            code = 1;
        }
        int exit = code;
        System.exit(SpringApplication.exit(context, () -> exit));
    }

    private void send(String to, String from) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(to);
        m.setSubject(SUBJECT);
        m.setText(BODY);
        mail.sender().send(m);
    }

    private String label() {
        return mail.provider() == MailProviderProperties.Provider.GMAIL_API ? "Gmail API" : "SMTP";
    }
}
