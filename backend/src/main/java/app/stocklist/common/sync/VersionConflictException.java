package app.stocklist.common.sync;

import app.stocklist.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** Someone else changed (or deleted) the record since this user's copy. Carries the server's current copy. */
public class VersionConflictException extends ApiException {
    private final transient Object current;
    private final long currentVersion;

    public VersionConflictException(String code, String message, Object current, long currentVersion) {
        super(HttpStatus.CONFLICT, code, message);
        this.current = current;
        this.currentVersion = currentVersion;
    }

    public Object current() { return current; }
    public long currentVersion() { return currentVersion; }

    public static void check(SyncEntity e, Long baseVersion, Object currentDto) {
        // A phone that has already seen the deletion (its base is the deleted version) may edit on top of it,
        // which brings the record back ("keep mine"); anyone else learns that it was deleted.
        boolean seen = baseVersion != null && baseVersion == e.getVersion();
        if (e.isDeleted() && !seen) {
            throw new VersionConflictException("RECORD_DELETED", "This record was deleted by someone else", currentDto,
                    e.getVersion());
        }
        if (baseVersion == null || baseVersion != e.getVersion()) {
            throw new VersionConflictException("VERSION_CONFLICT",
                    "Someone else changed this record. Review their version before saving again.", currentDto,
                    e.getVersion());
        }
    }
}
