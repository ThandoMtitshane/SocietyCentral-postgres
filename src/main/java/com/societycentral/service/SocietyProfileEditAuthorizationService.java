package com.societycentral.service;

import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.exception.ResourceNotFoundException;
import com.societycentral.model.ExecutivePosition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central authority for society public-profile editing capability.
 */
@Service
@RequiredArgsConstructor
public class SocietyProfileEditAuthorizationService {

    static final String NOT_AUTHORISED_MESSAGE =
            "You are not authorised to edit this society profile.";
    static final String POSITION_REQUIRED_MESSAGE =
            "Only the President or Secretary may edit the society profile.";

    private final ExecutiveSocietyResolver executiveSocietyResolver;

    /**
     * Returns true only for the active President or Secretary of the exact
     * requested society.
     */
    @Transactional(readOnly = true)
    public boolean canEditSocietyProfile(
            String authenticatedEmail,
            String societyID) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()
                || societyID == null || societyID.isBlank()) {
            return false;
        }

        try {
            ExecutiveSocietyResolver.ActiveExecutiveSociety context =
                    executiveSocietyResolver.resolve(
                            authenticatedEmail.trim(), societyID.trim());
            return ExecutivePosition.canEditSocietyProfile(
                    context.executiveRole().getPosition());
        } catch (ForbiddenOperationException
                 | ResourceNotFoundException ignored) {
            return false;
        }
    }

    /**
     * Resolves and authorises the caller's active society for an edit.
     */
    @Transactional(readOnly = true)
    public ExecutiveSocietyResolver.ActiveExecutiveSociety
    requireCanEditOwnSociety(String authenticatedEmail) {
        ExecutiveSocietyResolver.ActiveExecutiveSociety context;
        try {
            context = executiveSocietyResolver.resolve(authenticatedEmail);
        } catch (ForbiddenOperationException ex) {
            throw new ForbiddenOperationException(NOT_AUTHORISED_MESSAGE);
        }

        requireEditorPosition(context);
        return context;
    }

    /**
     * Explicit society-scoped guard retained for service callers and tests.
     */
    @Transactional(readOnly = true)
    public ExecutiveSocietyResolver.ActiveExecutiveSociety
    requireCanEditSocietyProfile(
            String authenticatedEmail,
            String societyID) {
        if (societyID == null || societyID.isBlank()) {
            throw new IllegalArgumentException("Society ID is required.");
        }

        ExecutiveSocietyResolver.ActiveExecutiveSociety context;
        try {
            context = executiveSocietyResolver.resolve(
                    authenticatedEmail, societyID.trim());
        } catch (ForbiddenOperationException
                 | ResourceNotFoundException ex) {
            throw new ForbiddenOperationException(NOT_AUTHORISED_MESSAGE);
        }

        requireEditorPosition(context);
        return context;
    }

    private void requireEditorPosition(
            ExecutiveSocietyResolver.ActiveExecutiveSociety context) {
        if (!ExecutivePosition.canEditSocietyProfile(
                context.executiveRole().getPosition())) {
            throw new ForbiddenOperationException(POSITION_REQUIRED_MESSAGE);
        }
    }
}
