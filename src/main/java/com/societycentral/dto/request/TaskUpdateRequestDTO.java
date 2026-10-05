package com.societycentral.dto.request;

import com.societycentral.model.TaskStatus;
import lombok.Data;

@Data
public class TaskUpdateRequestDTO {
    private TaskStatus status;
    private String comment;
}