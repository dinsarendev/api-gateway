package com.cambofreelance.apigateway.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import com.cambofreelance.apigateway.constants.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ResponseCodeDto implements Serializable {

    private Long id;
    private String code;
    private String httpStatus;
    private String key;
    private String type;
    private String description;
    private String message;
    private String messageKm;
    private String messageKh;
    private String messageCn;
    private String status;

    public static Map<String, String> getErrorMessage(ResponseCodeDto responseCode) {
        Map<String, String> message = new HashMap<>();
        message.put("message", StringUtils.defaultIfEmpty(responseCode.message, ""));
        message.put("messageKm",
                StringUtils.defaultIfEmpty(responseCode.messageKm, message.get(message)));
        // if message get messageKm still empty then use messageKh
        if (message.get("messageKm") == null || message.get("messageKm").isEmpty()) {
            message.put("messageKm",
                    StringUtils.defaultIfEmpty(responseCode.messageKh, message.get(message)));
        }
        message.put("messageKh",
                StringUtils.defaultIfEmpty(responseCode.messageKh, message.get(message)));
        message.put("messageCn",
                StringUtils.defaultIfEmpty(responseCode.messageCn, message.get(message)));

        return message;
    }

    public void getDefault() {
        this.code = ErrorCode.SUCCESS;
        this.message = "Default message Success";
        this.messageKm = "Default message Success";
        this.messageCn = "Default message Success";
    }
}
