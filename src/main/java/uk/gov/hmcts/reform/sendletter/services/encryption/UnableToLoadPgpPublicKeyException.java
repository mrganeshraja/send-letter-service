package uk.gov.hmcts.reform.sendletter.services.encryption;

import uk.gov.hmcts.reform.logging.exception.AlertLevel;
import uk.gov.hmcts.reform.logging.exception.UnknownErrorCodeException;

/**
 * SonarQube reports as error. Max allowed - 5 parents
 */
@SuppressWarnings("squid:MaximumInheritanceDepth")
public class UnableToLoadPgpPublicKeyException extends UnknownErrorCodeException {

    private static final long serialVersionUID = -5621910255408117685L;

    public UnableToLoadPgpPublicKeyException(Throwable cause) {
        super(AlertLevel.P1, cause);
    }
}
