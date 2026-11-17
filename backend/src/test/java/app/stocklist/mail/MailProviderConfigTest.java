package app.stocklist.mail;

import static org.assertj.core.api.Assertions.assertThat;

import app.stocklist.auth.LoggingPasswordResetMailer;
import app.stocklist.auth.MailConfig;
import app.stocklist.auth.PasswordResetMailer;
import app.stocklist.auth.SmtpPasswordResetMailer;
import app.stocklist.config.AppProperties;
import app.stocklist.supplieraccount.LoggingSupplierMailer;
import app.stocklist.supplieraccount.SmtpSupplierMailer;
import app.stocklist.supplieraccount.SupplierMailConfig;
import app.stocklist.supplieraccount.SupplierMailer;
import app.stocklist.supplieraccount.SupplierProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * How the mail provider plugs into the two existing mail configurations, with Spring Boot's real mail
 * auto-configuration. Covers: exactly one JavaMailSender for the selected provider, both kinds of email using it, SMTP
 * unchanged when MAIL_PROVIDER is unset, startup checks of the Gmail settings and From addresses, and no network call
 * at startup.
 */
class MailProviderConfigTest {
    static final String GMAIL = "suppliermanagement.mail@gmail.com";

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({MailProviderProperties.class, AppProperties.class, SupplierProperties.class})
    static class Properties {}

    private StubGoogle google;
    private ApplicationContextRunner runner;

    @BeforeEach
    void setUp() throws Exception {
        google = new StubGoogle();
        runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withUserConfiguration(Properties.class, MailProviderConfig.class, MailConfig.class,
                        SupplierMailConfig.class)
                .withPropertyValues("app.reset-token-ttl=30m", "app.login-rate-per-minute=20",
                        "app.mail-log-tokens=false", "app.supplier.public-base-url=https://test.example");
    }

    @AfterEach
    void tearDown() {
        google.close();
    }

    private ApplicationContextRunner gmail(String... more) {
        return runner.withPropertyValues(
                        "app.mail.provider=gmail-api",
                        "app.mail.gmail.sender=" + GMAIL,
                        "app.mail.gmail.client-id=123-test.apps.googleusercontent.com",
                        "app.mail.gmail.client-secret=GOCSPX-test",
                        "app.mail.gmail.refresh-token=1//test",
                        "app.mail.gmail.token-uri=" + google.tokenUri(),
                        "app.mail.gmail.api-base-uri=" + google.base())
                .withPropertyValues(more);
    }

    private static Object field(Object target, String name) {
        return ReflectionTestUtils.getField(target, name);
    }

    // ------------------------------------------------------------------ SMTP stays as it was

    @Test
    void unsetProviderWithSmtpHostKeepsBootsSmtpSenderForBothKindsOfEmail() {
        runner.withPropertyValues("spring.mail.host=smtp.example.com", "app.mail-from=Shop <no-reply@example.com>")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBeansOfType(JavaMailSender.class)).hasSize(1);
                    JavaMailSender sender = ctx.getBean(JavaMailSender.class);
                    assertThat(sender).isExactlyInstanceOf(JavaMailSenderImpl.class);
                    assertThat(((JavaMailSenderImpl) sender).getHost()).isEqualTo("smtp.example.com");
                    assertThat(((JavaMailSenderImpl) sender).getDefaultEncoding()).isEqualTo("UTF-8");

                    PasswordResetMailer owner = ctx.getBean(PasswordResetMailer.class);
                    SupplierMailer supplier = ctx.getBean(SupplierMailer.class);
                    assertThat(owner).isInstanceOf(SmtpPasswordResetMailer.class);
                    assertThat(supplier).isInstanceOf(SmtpSupplierMailer.class);
                    assertThat(field(owner, "sender")).isSameAs(sender);
                    assertThat(field(supplier, "sender")).isSameAs(sender);
                    assertThat(field(owner, "from")).isEqualTo("Shop <no-reply@example.com>");
                    assertThat(field(supplier, "from")).as("SUPPLIER_MAIL_FROM unset: MAIL_FROM, as before")
                            .isEqualTo("Shop <no-reply@example.com>");
                });
    }

    @Test
    void unsetProviderWithoutSmtpHostKeepsTheLoggingStandIns() {
        runner.withPropertyValues("app.mail-from=Shop <no-reply@example.com>").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBeansOfType(JavaMailSender.class)).isEmpty();
            assertThat(ctx.getBean(PasswordResetMailer.class)).isInstanceOf(LoggingPasswordResetMailer.class);
            assertThat(ctx.getBean(SupplierMailer.class)).isInstanceOf(LoggingSupplierMailer.class);
        });
    }

    @Test
    void smtpWithoutMailFromKeepsTheLoggingStandIns() {
        runner.withPropertyValues("spring.mail.host=smtp.example.com").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(PasswordResetMailer.class)).isInstanceOf(LoggingPasswordResetMailer.class);
            assertThat(ctx.getBean(SupplierMailer.class)).isInstanceOf(LoggingSupplierMailer.class);
        });
    }

    @Test
    void anEmptyOrExplicitSmtpProviderBehavesLikeUnset() {
        for (String value : new String[] {"", "smtp", "SMTP"}) {
            runner.withPropertyValues("app.mail.provider=" + value, "spring.mail.host=smtp.example.com",
                    "app.mail-from=x@example.com").run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(ctx.getBean(JavaMailSender.class)).isExactlyInstanceOf(JavaMailSenderImpl.class);
                assertThat(ctx.getBean(SupplierMailer.class)).isInstanceOf(SmtpSupplierMailer.class);
            });
        }
    }

    @Test
    void smtpAcceptsAnyFromAddressAndUsesSupplierMailFromWhenSet() {
        runner.withPropertyValues("spring.mail.host=smtp.example.com", "app.mail-from=Shop <no-reply@yourshop.in>",
                "app.supplier.mail-from=Suppliers <suppliers@yourshop.in>").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(field(ctx.getBean(PasswordResetMailer.class), "from")).isEqualTo("Shop <no-reply@yourshop.in>");
            assertThat(field(ctx.getBean(SupplierMailer.class), "from")).isEqualTo("Suppliers <suppliers@yourshop.in>");
        });
    }

    @Test
    void anUnknownProviderStopsStartup() {
        runner.withPropertyValues("app.mail.provider=smpt", "spring.mail.host=smtp.example.com")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    // ------------------------------------------------------------------ Gmail API

    @Test
    void gmailApiIsTheOnlySenderEvenWithSmtpHostSetAndBothKindsOfEmailUseIt() {
        gmail("spring.mail.host=smtp.example.com",
                "app.mail-from=Stock List Manager <" + GMAIL + ">",
                "app.supplier.mail-from=Supplier Management <" + GMAIL + ">").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBeansOfType(JavaMailSender.class)).hasSize(1);
            JavaMailSender sender = ctx.getBean(JavaMailSender.class);
            assertThat(sender).isExactlyInstanceOf(GmailApiMailSender.class);
            assertThat(((JavaMailSenderImpl) sender).getDefaultEncoding()).isEqualTo("UTF-8");

            PasswordResetMailer owner = ctx.getBean(PasswordResetMailer.class);
            SupplierMailer supplier = ctx.getBean(SupplierMailer.class);
            assertThat(owner).isInstanceOf(SmtpPasswordResetMailer.class);
            assertThat(supplier).isInstanceOf(SmtpSupplierMailer.class);
            assertThat(field(owner, "sender")).isSameAs(sender);
            assertThat(field(supplier, "sender")).isSameAs(sender);
            assertThat(field(owner, "from")).isEqualTo("Stock List Manager <" + GMAIL + ">");
            assertThat(field(supplier, "from")).isEqualTo("Supplier Management <" + GMAIL + ">");

            assertThat(google.tokenCalls.get()).as("no call to Google at startup").isZero();
            assertThat(google.sendCalls.get()).isZero();
        });
    }

    @Test
    void gmailApiWithoutMailFromStartsButSendsNothing() {
        gmail().run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(PasswordResetMailer.class)).isInstanceOf(LoggingPasswordResetMailer.class);
            assertThat(ctx.getBean(SupplierMailer.class)).isInstanceOf(LoggingSupplierMailer.class);
        });
    }

    @Test
    void eachGmailSettingIsRequired() {
        String[][] missing = {
                {"app.mail.gmail.sender=", "GMAIL_SENDER"},
                {"app.mail.gmail.client-id=", "GMAIL_OAUTH_CLIENT_ID"},
                {"app.mail.gmail.client-secret=", "GMAIL_OAUTH_CLIENT_SECRET"},
                {"app.mail.gmail.refresh-token=", "GMAIL_OAUTH_REFRESH_TOKEN"},
        };
        for (String[] m : missing) {
            gmail("app.mail-from=" + GMAIL, m[0]).run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining(m[1] + " must be set");
            });
        }
        assertThat(google.tokenCalls.get()).isZero();
    }

    // ------------------------------------------------------------------ From addresses must match the Gmail account

    @Test
    void fromAddressesMatchingTheGmailAccountStart() {
        String[] accepted = {
                GMAIL,
                "Stock List Manager <" + GMAIL + ">",
                "Stock List Manager <SupplierManagement.Mail@Gmail.com>",
                "स्टॉक लिस्ट <" + GMAIL + ">",
        };
        for (String from : accepted) {
            gmail("app.mail-from=" + from).run(ctx -> assertThat(ctx).as(from).hasNotFailed());
        }
    }

    @Test
    void aListedAliasIsAccepted() {
        gmail("app.mail.gmail.send-as-aliases= orders@yourshop.in , Help@YourShop.in",
                "app.mail-from=Shop <orders@yourshop.in>",
                "app.supplier.mail-from=Supplier Management <help@yourshop.in>")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void aMailFromOutsideTheAccountAndAliasesStopsStartup() {
        gmail("app.mail-from=Shop <no-reply@yourshop.in>").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("MAIL_FROM must use the GMAIL_SENDER");
        });
        gmail("app.mail.gmail.send-as-aliases=orders@yourshop.in", "app.mail-from=Shop <other@yourshop.in>")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void aSupplierMailFromOutsideTheAccountAndAliasesStopsStartup() {
        gmail("app.mail-from=" + GMAIL, "app.supplier.mail-from=Supplier Management <suppliers@yourshop.in>")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).rootCause()
                            .hasMessageContaining("SUPPLIER_MAIL_FROM must use the GMAIL_SENDER");
                });
    }

    @Test
    void anAddressThatCannotBeParsedStopsStartup() {
        String[] broken = {"not an address", "Shop <" + GMAIL, GMAIL + ", other@gmail.com"};
        for (String from : broken) {
            gmail("app.mail-from=" + from).run(ctx -> {
                assertThat(ctx).as(from).hasFailed();
                assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("MAIL_FROM");
            });
        }
        gmail("app.mail-from=" + GMAIL, "app.mail.gmail.send-as-aliases=broken alias")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void errorsNameTheSettingButNotItsValue() {
        gmail("app.mail-from=Shop <private-address@yourshop.in>").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause().hasMessageNotContaining("private-address");
        });
    }
}
