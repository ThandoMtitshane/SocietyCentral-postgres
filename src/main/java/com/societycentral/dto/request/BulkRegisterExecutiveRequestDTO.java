// com.societycentral.dto.request.BulkRegisterExecutiveRequest.java
package com.societycentral.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class BulkRegisterExecutiveRequestDTO {
    private List<RegisterExecutiveRequestDTO> executives;
}
