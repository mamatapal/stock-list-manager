package app.stocklist.supplieraccount;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Access tokens of the Supplier Management app: HS256 with SUPPLIER_JWT_SIGNING_KEY, issuer "supplier-management",
 * scope "supplier_access". The shop app's filter rejects them (other issuer, scope and key) and this service rejects
 * shop tokens for the same reasons.
 */
@Service
public class SupplierJwtService {
    public static final String ISSUER = "supplier-management";
    public static final String SCOPE = "supplier_access";

    private final byte[] key;
    private final String keyId;
    private final SupplierProperties props;
    private final Clock clock;

    public SupplierJwtService(SupplierProperties props, Clock clock) {
        String raw = props.jwtSigningKey();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("SUPPLIER_JWT_SIGNING_KEY is not set");
        }
        this.key = Base64.getDecoder().decode(raw.trim());
        if (key.length < 32) {
            throw new IllegalStateException("SUPPLIER_JWT_SIGNING_KEY must decode to at least 32 bytes");
        }
        this.keyId = props.jwtKeyId();
        this.props = props;
        this.clock = clock;
    }

    public record Issued(String token, Instant expiresAt) {}

    public record Claims(UUID userId, UUID orgId, int tokenVersion, String deviceId) {}

    public Issued issue(SupplierUser user, String deviceId) {
        Instant now = Instant.now(clock);
        Instant exp = now.plus(props.accessTtl());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .claim("oid", user.getSupplierOrgId().toString())
                .claim("tv", user.getTokenVersion())
                .claim("did", deviceId)
                .claim("scope", SCOPE)
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(keyId).type(JOSEObjectType.JWT).build(), claims);
        try {
            jwt.sign(new MACSigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign token", e);
        }
        return new Issued(jwt.serialize(), exp);
    }

    /** Empty for anything that is not a valid, unexpired supplier token signed with the supplier key. */
    public Optional<Claims> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(key))) {
                return Optional.empty();
            }
            JWTClaimsSet c = jwt.getJWTClaimsSet();
            if (c.getExpirationTime() == null || !c.getExpirationTime().toInstant().isAfter(Instant.now(clock))) {
                return Optional.empty();
            }
            if (!ISSUER.equals(c.getIssuer()) || !SCOPE.equals(c.getStringClaim("scope"))) {
                return Optional.empty();
            }
            Number tv = (Number) c.getClaim("tv");
            return Optional.of(new Claims(UUID.fromString(c.getSubject()), UUID.fromString(c.getStringClaim("oid")),
                    tv == null ? -1 : tv.intValue(), c.getStringClaim("did")));
        } catch (ParseException | JOSEException | IllegalArgumentException | ClassCastException
                 | NullPointerException e) {
            return Optional.empty();
        }
    }
}
