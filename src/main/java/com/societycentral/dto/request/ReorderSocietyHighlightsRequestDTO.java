package com.societycentral.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ReorderSocietyHighlightsRequestDTO {

    @NotEmpty(message = "At least one highlight ID is required.")
    private List<@NotBlank(message = "Highlight ID is required.") String>
            highlightIDs;
}
