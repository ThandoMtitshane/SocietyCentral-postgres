package com.societycentral.dto.request;

import com.societycentral.model.BudgetRequestType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class BudgetRequestRequestDTO {

    @NotBlank(message = "Event ID is required.")
    private String eventID;

    @NotBlank(message = "Society ID is required.")
    private String societyID;

    @NotBlank(message = "Budget item name is required.")
    private String name;

    private String description;

    @NotNull(message = "Amount is required.")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
    private BigDecimal amount;

    @NotNull(message = "Budget request type is required.")
    private BudgetRequestType type;

    private String typeSpecification;
}