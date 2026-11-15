package app.stocklist.common.sync;

import app.stocklist.business.BusinessSeq;
import app.stocklist.business.BusinessSeqRepository;
import app.stocklist.common.error.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands out the next change number for a business. The row lock serialises writers per business, so
 * change numbers are committed in order and a phone pulling "everything after N" never skips a change.
 */
@Component
public class ChangeSequencer {
    private final BusinessSeqRepository seqs;

    public ChangeSequencer(BusinessSeqRepository seqs) {
        this.seqs = seqs;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long next(UUID businessId) {
        BusinessSeq seq = seqs.lockById(businessId).orElseThrow(() -> ApiException.notFound("Business"));
        return seq.take();
    }
}
