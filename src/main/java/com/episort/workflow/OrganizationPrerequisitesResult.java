package com.episort.workflow;

import java.util.Optional;

public record OrganizationPrerequisitesResult(boolean organizationAllowed, Optional<ApplicationError> error) {
    public static OrganizationPrerequisitesResult allowed() {
        return new OrganizationPrerequisitesResult(true, Optional.empty());
    }

    public static OrganizationPrerequisitesResult blocked(ApplicationError error) {
        return new OrganizationPrerequisitesResult(false, Optional.of(error));
    }
}
