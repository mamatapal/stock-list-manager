package app.stocklist.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * The process is ended only in the one-shot mail-test run (deploy/mail-test.sh). A normal backend start (no
 * --mail-test option, whatever else is on the command line) returns at once without sending or exiting. Every
 * integration test also starts the full backend with this runner registered.
 */
class MailTestCommandLineTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final List<Integer> exits = new ArrayList<>();

    private MailTestCommandLine runner(String supplierFrom) {
        OutgoingMail mail = new OutgoingMail(MailProviderProperties.Provider.SMTP, sender, "smtp.example.com",
                "Shop <no-reply@yourshop.in>", supplierFrom);
        return new MailTestCommandLine(mail, exits::add);
    }

    private static DefaultApplicationArguments args(String... args) {
        return new DefaultApplicationArguments(args);
    }

    @Test
    void aNormalStartNeitherSendsNorExits() {
        runner(null).run(args());
        runner(null).run(args("--server.port=0", "--spring.profiles.active=prod", "mail-test", "--mail-testing=x"));
        assertThat(exits).isEmpty();
        verifyNoInteractions(sender);
    }

    @Test
    void theMailTestSendsAndExitsWithZero() {
        runner(null).run(args("--mail-test=owner@example.com"));
        assertThat(exits).containsExactly(0);
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, times(1)).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("owner@example.com");
        assertThat(sent.getValue().getFrom()).isEqualTo("Shop <no-reply@yourshop.in>");
        assertThat(sent.getValue().getText()).contains("नमस्ते").contains("Hello");
    }

    @Test
    void aDifferentSupplierSenderGetsASecondTestEmail() {
        runner("Suppliers <suppliers@yourshop.in>").run(args("--mail-test=owner@example.com"));
        assertThat(exits).containsExactly(0);
        verify(sender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void aMissingOrBadAddressExitsWithOneWithoutSending() {
        runner(null).run(args("--mail-test"));
        runner(null).run(args("--mail-test="));
        runner(null).run(args("--mail-test=not an address"));
        runner(null).run(args("--mail-test=a@example.com", "--mail-test=b@example.com"));
        assertThat(exits).containsExactly(1, 1, 1, 1);
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void emailThatIsNotConfiguredExitsWithOne() {
        OutgoingMail notReady = new OutgoingMail(MailProviderProperties.Provider.SMTP, null, "", "", null);
        new MailTestCommandLine(notReady, exits::add).run(args("--mail-test=owner@example.com"));
        assertThat(exits).containsExactly(1);
    }

    @Test
    void aFailedSendExitsWithOne() {
        doThrow(new MailSendException("refused")).when(sender).send(any(SimpleMailMessage.class));
        runner(null).run(args("--mail-test=owner@example.com"));
        assertThat(exits).containsExactly(1);
    }
}
