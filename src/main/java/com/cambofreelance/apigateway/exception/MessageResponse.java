package com.cambofreelance.apigateway.exception;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Objects;
import com.cambofreelance.apigateway.constants.Constants;
import com.cambofreelance.apigateway.constants.ErrorCode;
import com.cambofreelance.apigateway.dto.ResponseCodeDto;
import com.cambofreelance.apigateway.utils.AppExceptionUtil;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.util.StringUtils;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class MessageResponse implements Serializable {

  private String code;
  private String message;
  private Object data;
  private boolean success = true;
  private long timestamp;
  private String traceId;

  @JsonIgnore
  private ResponseCodeDto responseCode;

  public MessageResponse(Object data, String errorCode, String lang) {
    try {
      ResponseCodeDto localResponseCode = AppExceptionUtil.buildMessage(errorCode);
      if (Objects.nonNull(localResponseCode)) {
        localResponseCode = AppExceptionUtil.buildMessage(errorCode);
      }
      if (StringUtils.hasLength(lang)) {
        this.setError(localResponseCode, lang);
      } else {
        this.setError(localResponseCode);
      }
      if (Objects.nonNull(data)) {
        this.setData(data);
      }
    } catch (Exception e) {
      this.setData(data);
      this.setCode(ErrorCode.SUCCESS);
      this.setMessageSuccess(Constants.SUCCESS);
      this.setError(new ResponseCodeDto());
    }
  }

  public void setError(ResponseCodeDto responseCode) {
    setError(responseCode, new ArrayList<>());
  }

  public void setError(ResponseCodeDto responseCode, Object obj) {
    this.code = responseCode.getCode();
    this.message = responseCode.getMessage();
    this.data = obj;
  }

  public void setError(ResponseCodeDto responseCode, String lang) {
    this.code = responseCode.getCode();
    this.data = new ArrayList<>();
    if (Constants.LANG_KH.equals(lang)) {
      this.message = responseCode.getMessageKh();
    } else if (Constants.LANG_CN.equals(lang)) {
      this.message = responseCode.getMessageCn();
    } else {
      this.message = responseCode.getMessage();
    }


    // this.message is null, turn to empty string
    if (this.message == null) {
      this.message = "";
    }
  }

  public void setMessageSuccess(Object data, String lang) {
    try {
      ResponseCodeDto localResponseCode = AppExceptionUtil.buildMessage(ErrorCode.SUCCESS);
      if (StringUtils.hasLength(lang)) {
        this.setError(localResponseCode, lang);
      } else {
        this.setError(localResponseCode);
      }
      if (null != data) {
        this.setData(data);
      }
    } catch (Exception e) {
      this.setData(data);
      this.setCode(ErrorCode.SUCCESS);
      this.setMessageSuccess(Constants.SUCCESS);
      this.setError(new ResponseCodeDto());
    }
  }

  public void setMessageSuccess(String lang) {
    responseCode = AppExceptionUtil.buildMessage(ErrorCode.SUCCESS);
    this.setError(responseCode);
    this.setData(new ArrayList<>());
    if (StringUtils.hasLength(lang)) {
      this.setError(responseCode, lang);
    }
  }

}

