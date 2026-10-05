// com.societycentral.dto.request.BulkUpdateExecutiveRequestDTO.java
package com.societycentral.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class BulkUpdateExecutiveRequestDTO {
    private List<UpdateExecutiveRequestDTO> executives;
}
