package app.stocklist.platformadmin;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Server-only administrator management, run through deploy/create-admin.sh (never reachable over the network):
 * <pre>
 *   --admin-cli=create     --admin-username=NAME --admin-name="Full Name" [--admin-email=EMAIL]
 *   --admin-cli=reset      --admin-username=NAME   (new password and new authenticator key)
 *   --admin-cli=deactivate --admin-username=NAME
 * </pre>
 * The password is typed twice at the prompt (not echoed, never in the command line or shell history). The
 * authenticator key is printed once, to be added to an authenticator app. Without --admin-cli this does nothing.
 */
@Component
public class AdminCommandLine implements ApplicationRunner {
    private final AdminAuthService auth;
    private final ApplicationContext context;

    public AdminCommandLine(AdminAuthService auth, ApplicationContext context) {
        this.auth = auth;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        String command = one(args, "admin-cli");
        if (command == null) return;
        int code = 0;
        try {
            String username = one(args, "admin-username");
            switch (command) {
                case "create" -> printKey(username, auth.createAdmin(username, one(args, "admin-name"),
                        one(args, "admin-email"), readPassword()));
                case "reset" -> printKey(username, auth.resetAdmin(username, readPassword()));
                case "deactivate" -> {
                    auth.deactivateAdmin(username);
                    System.out.println("Administrator " + username + " deactivated and signed out everywhere.");
                }
                default -> throw new IllegalArgumentException("Unknown --admin-cli command: " + command);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println("ERROR: " + e.getMessage());
            code = 1;
        }
        int exit = code;
        System.exit(SpringApplication.exit(context, () -> exit));
    }

    private static String one(ApplicationArguments args, String name) {
        List<String> v = args.getOptionValues(name);
        return v == null || v.isEmpty() ? null : v.get(0);
    }

    private static String readPassword() {
        Console console = System.console();
        String first, second;
        if (console != null) {
            first = new String(console.readPassword("New administrator password (12+ characters): "));
            second = new String(console.readPassword("Type it again: "));
        } else {
            // Non-interactive use: two lines on standard input.
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                first = in.readLine();
                second = in.readLine();
            } catch (IOException e) {
                throw new IllegalStateException("Could not read the password");
            }
        }
        if (first == null || !first.equals(second)) throw new IllegalArgumentException("The passwords do not match");
        return first;
    }

    private static void printKey(String username, String secret) {
        System.out.println();
        System.out.println("Administrator " + username + " is ready.");
        System.out.println("Add this key to an authenticator app (Google Authenticator, Microsoft Authenticator):");
        System.out.println("  Account: " + username + "   Key: " + secret + "   (time based, 6 digits)");
        System.out.println("  or import: " + Totp.uri(AdminAuthService.TOTP_ISSUER, username, secret));
        System.out.println("This key is shown only now. Keep it private; anyone with it and the password can sign in.");
    }
}
