package com.societycentral.dto.request;

import com.societycentral.model.BudgetRequestStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BudgetRequestReviewDTO {

    @NotNull(message = "Status is required.")
    private BudgetRequestStatus status;

    private String reviewNotes;
}
