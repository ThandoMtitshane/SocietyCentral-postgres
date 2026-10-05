package com.societycentral.service;

import com.societycentral.dto.response.EventProposalResponseDTO;

record EventProposalSubmittedEvent(
        String sdoEmail,
        EventProposalResponseDTO proposal,
        String submitterName) {
}

record EventProposalReviewedEvent(
        EmailType emailType,
        String executiveEmail,
        String eventName,
        String societyName,
        String rejectionReason) {
}

record EventPublishedEvent(String eventID, String societyID) {
}
