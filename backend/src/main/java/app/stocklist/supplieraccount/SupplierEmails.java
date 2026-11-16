package app.stocklist.supplieraccount;

import java.time.Duration;

/** Plain-text emails, Hindi first and English below, so every supplier can read them whatever the app language. */
public final class SupplierEmails {
    private SupplierEmails() {}

    public record Email(String subject, String body) {}

    private static final String FOOTER = """

            --
            Supplier Management / सप्लायर मैनेजमेंट
            इस ईमेल का जवाब न दें। / Please do not reply to this email.
            """;

    public static Email verification(SupplierUser u, String link, Duration validFor) {
        long h = validFor.toHours();
        return new Email("अपना ईमेल पक्का करें / Verify your email - Supplier Management", """
                नमस्ते %1$s,

                Supplier Management में रजिस्टर करने के लिए धन्यवाद। अपना ईमेल पक्का करने के लिए नीचे दिया लिंक खोलें और "सत्यापित करें" दबाएँ:

                %2$s

                यह लिंक %3$d घंटे तक और सिर्फ़ एक बार काम करेगा। ईमेल पक्का होने के बाद हमारी टीम आपके बिज़नेस की जाँच करेगी।
                अगर आपने रजिस्टर नहीं किया, तो इस ईमेल को अनदेखा करें।

                ----

                Hello %1$s,

                Thank you for registering with Supplier Management. To verify your email, open the link below and press "Verify":

                %2$s

                The link works once and expires in %3$d hours. After verification our team will review your business.
                If you did not register, you can ignore this email.
                """.formatted(u.getFullName(), link, h) + FOOTER);
    }

    public static Email passwordReset(SupplierUser u, String link, Duration validFor) {
        long m = validFor.toMinutes();
        return new Email("पासवर्ड बदलें / Reset your password - Supplier Management", """
                नमस्ते %1$s,

                आपके Supplier Management खाते का पासवर्ड बदलने का अनुरोध मिला है। नया पासवर्ड रखने के लिए यह लिंक खोलें:

                %2$s

                यह लिंक %3$d मिनट तक और सिर्फ़ एक बार काम करेगा। अगर आपने यह अनुरोध नहीं किया, तो इस ईमेल को अनदेखा करें; आपका पासवर्ड नहीं बदला है।

                ----

                Hello %1$s,

                We received a request to reset the password of your Supplier Management account. Open this link to choose a new password:

                %2$s

                The link works once and expires in %3$d minutes. If you did not ask for this, ignore this email; your password has not changed.
                """.formatted(u.getFullName(), link, m) + FOOTER);
    }

    public static Email alreadyRegistered(SupplierUser u, String resetLink) {
        return new Email("आपका खाता पहले से है / You already have an account - Supplier Management", """
                नमस्ते %1$s,

                किसी ने इस ईमेल से Supplier Management में दोबारा रजिस्टर करने की कोशिश की। इस ईमेल का खाता पहले से मौजूद है।
                कृपया ऐप में साइन इन करें। पासवर्ड भूल गए हों, तो यह लिंक खोलकर नया पासवर्ड रखें:

                %2$s

                अगर यह आपने नहीं किया, तो इस ईमेल को अनदेखा करें; आपके खाते में कोई बदलाव नहीं हुआ है।

                ----

                Hello %1$s,

                Someone tried to register with Supplier Management again using this email. An account with this email already exists.
                Please sign in to the app. If you forgot your password, open this link to choose a new one:

                %2$s

                If this was not you, ignore this email; nothing has changed in your account.
                """.formatted(u.getFullName(), resetLink) + FOOTER);
    }

    public static Email passwordChanged(SupplierUser u) {
        return new Email("आपका पासवर्ड बदला गया / Your password was changed - Supplier Management", """
                नमस्ते %1$s,

                आपके Supplier Management खाते (%2$s) का पासवर्ड अभी बदला गया है और बाकी सभी फ़ोन साइन आउट कर दिए गए हैं।
                अगर यह आपने किया है, तो कुछ करने की ज़रूरत नहीं है।
                अगर यह आपने नहीं किया, तो तुरंत ऐप में "पासवर्ड भूल गए?" से नया पासवर्ड रखें और अपने ईमेल खाते का पासवर्ड भी बदलें।

                ----

                Hello %1$s,

                The password of your Supplier Management account (%2$s) was just changed and all other phones were signed out.
                If this was you, nothing else is needed.
                If this was not you, set a new password at once with "Forgot password?" in the app, and change the password of your email account too.
                """.formatted(u.getFullName(), u.getEmail()) + FOOTER);
    }

    public static Email statusChanged(SupplierUser u, SupplierOrg o) {
        String reason = o.getStatusReason() == null ? "" : o.getStatusReason();
        String hi, en, subject;
        switch (o.getStatus()) {
            case SupplierOrg.APPROVED -> {
                subject = "आपका बिज़नेस मंज़ूर हो गया / Your business is approved";
                hi = "आपका बिज़नेस \"%s\" मंज़ूर हो गया है। अब आप Supplier Management ऐप की सभी सुविधाएँ इस्तेमाल कर सकते हैं।";
                en = "Your business \"%s\" has been approved. You can now use all features of the Supplier Management app.";
            }
            case SupplierOrg.REJECTED -> {
                subject = "आपका आवेदन मंज़ूर नहीं हुआ / Your application was not approved";
                hi = "आपके बिज़नेस \"%s\" का आवेदन मंज़ूर नहीं हुआ। कारण नीचे लिखा है। ऐप में जानकारी ठीक करके दोबारा भेज सकते हैं।";
                en = "The application for your business \"%s\" was not approved. The reason is below. You can correct the details in the app and send them again.";
            }
            case SupplierOrg.SUSPENDED -> {
                subject = "आपका खाता रोका गया है / Your account is suspended";
                hi = "आपके बिज़नेस \"%s\" का खाता अभी रोका गया है। कारण नीचे लिखा है।";
                en = "The account of your business \"%s\" has been suspended. The reason is below.";
            }
            default -> {
                subject = "आपके खाते की स्थिति बदली है / Your account status has changed";
                hi = "आपके बिज़नेस \"%s\" के खाते की स्थिति बदली है। ऐप खोलकर देखें।";
                en = "The status of your business \"%s\" has changed. Open the app to see it.";
            }
        }
        String name = o.getTradeName() != null ? o.getTradeName() : o.getLegalName();
        String reasonHi = reason.isEmpty() ? "" : "\nकारण: " + reason + "\n";
        String reasonEn = reason.isEmpty() ? "" : "\nReason: " + reason + "\n";
        String body = "नमस्ते " + u.getFullName() + ",\n\n" + hi.formatted(name) + "\n" + reasonHi
                + "\n----\n\nHello " + u.getFullName() + ",\n\n" + en.formatted(name) + "\n" + reasonEn + FOOTER;
        return new Email(subject + " - Supplier Management", body);
    }
}
