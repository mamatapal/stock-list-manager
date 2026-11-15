package app.stocklist.common.security;

import app.stocklist.config.AppProperties;
import app.stocklist.user.AppUser;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Issues and verifies HS256 access and step-up tokens. The key comes from JWT_SIGNING_KEY. */
@Service
public class JwtService {
    private static final String ISSUER = "stock-list-manager";

    private final byte[] key;
    private final String keyId;
    private final Duration accessTtl;
    private final Duration stepUpTtl;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        String raw = props.jwt() == null ? null : props.jwt().signingKey();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("JWT_SIGNING_KEY is not set");
        }
        this.key = Base64.getDecoder().decode(raw.trim());
        if (key.length < 32) {
            throw new IllegalStateException("JWT_SIGNING_KEY must decode to at least 32 bytes");
        }
        this.keyId = props.jwt().keyId();
        this.accessTtl = props.jwt().accessTtl();
        this.stepUpTtl = props.jwt().stepUpTtl();
        this.clock = clock;
    }

    public IssuedToken issueAccess(AppUser user, String deviceId) {
        return issue(user, deviceId, TokenClaims.SCOPE_ACCESS, accessTtl);
    }

    public IssuedToken issueStepUp(AppUser user, String deviceId) {
        return issue(user, deviceId, TokenClaims.SCOPE_STEP_UP, stepUpTtl);
    }

    private IssuedToken issue(AppUser user, String deviceId, String scope, Duration ttl) {
        Instant now = Instant.now(clock);
        Instant exp = now.plus(ttl);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .claim("bid", user.getBusinessId().toString())
                .claim("role", user.getRole().name())
                .claim("tv", user.getTokenVersion())
                .claim("did", deviceId)
                .claim("scope", scope)
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(keyId).type(JOSEObjectType.JWT).build(), claims);
        try {
            jwt.sign(new MACSigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign token", e);
        }
        return new IssuedToken(jwt.serialize(), exp);
    }

    /** Returns empty for anything that is not a valid, unexpired token signed with our key. */
    public Optional<TokenClaims> verify(String token, String expectedScope) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            if (!jwt.verify(new MACVerifier(key))) {
                return Optional.empty();
            }
            JWTClaimsSet c = jwt.getJWTClaimsSet();
            Instant now = Instant.now(clock);
            if (c.getExpirationTime() == null || !c.getExpirationTime().toInstant().isAfter(now)) {
                return Optional.empty();
            }
            if (!ISSUER.equals(c.getIssuer()) || !expectedScope.equals(c.getStringClaim("scope"))) {
                return Optional.empty();
            }
            Number tv = (Number) c.getClaim("tv");
            return Optional.of(new TokenClaims(
                    UUID.fromString(c.getSubject()),
                    UUID.fromString(c.getStringClaim("bid")),
                    c.getStringClaim("role"),
                    tv == null ? -1 : tv.intValue(),
                    c.getStringClaim("did"),
                    c.getStringClaim("scope"),
                    c.getExpirationTime().toInstant()));
        } catch (ParseException | JOSEException | IllegalArgumentException | ClassCastException e) {
            return Optional.empty();
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {}
}
