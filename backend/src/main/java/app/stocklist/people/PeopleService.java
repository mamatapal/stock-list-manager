package app.stocklist.people;

import app.stocklist.common.security.AuthUser;
import app.stocklist.user.AppUser;
import app.stocklist.user.AppUserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Names of the people in the caller's business, so screens can say who created or changed a record. Only the
 * name, role and whether the account is active are shared: no usernames, contact details or permissions.
 */
@Service
public class PeopleService {
    private final AppUserRepository users;

    public PeopleService(AppUserRepository users) {
        this.users = users;
    }

    public record PersonDto(UUID id, String fullName, String role, boolean active) {}

    @Transactional(readOnly = true)
    public List<PersonDto> list(AuthUser caller) {
        return users.findByBusinessIdOrderByFullNameAsc(caller.businessId()).stream().map(PeopleService::dto).toList();
    }

    private static PersonDto dto(AppUser u) {
        return new PersonDto(u.getId(), u.getFullName(), u.getRole().name(), "ACTIVE".equals(u.getStatus().name()));
    }
}
