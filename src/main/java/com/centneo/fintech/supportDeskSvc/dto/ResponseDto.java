package com.centneo.fintech.supportDeskSvc.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResponseDto {
    private boolean success;
    private String message;
    private Object data;
    private int status;

    // ✅ Make this constructor public
    public ResponseDto(boolean success, String message, Object data, int status) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.status = status;
    }

    // Getters & setters (or use Lombok @Data / @Getter/@Setter)
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Object getData() { return data; }
    public int getStatus() { return status; }
    public boolean getSuccess() {return success;}
}
