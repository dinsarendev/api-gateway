package com.cambofreelance.apigateway.constants;

public final class Constants {
  public static final String SUCCESS        = "SUCCESS";
  public static final String BEARER        = "Bearer ";
  public static final String STATUS_ACTIVE = "ACT";
  public static final String SYSTEM        = "SYS";
  public static final String LANG_KH       = "km";
  public static final String LANG_CN       = "cn";
  public static final String YES           = "Y";
  public static final String UNKNOWN       = "Unknown";
  public static final String DEFAULT_MESSAGE = "";

  // Request headers forwarded to upstream services
  public static final String IP              = "X-Client-Ip";
  public static final String USER_ID         = "X-User-Id";
  public static final String CORRELATION_ID  = "X-Correlation-Id";

  // Inbound client headers
  public static final String DEVICE_ID       = "Device-Id";
  public static final String CLIENT_USER_ID  = "User-Id";
  public static final String X_FORWARDED_FOR = "X-Forwarded-For";

  // API types
  public static final String API_TYPE_REST      = "REST";
  public static final String API_TYPE_SOAP      = "SOAP";
  public static final String API_TYPE_GRAPHQL   = "GRAPHQL";
  public static final String API_TYPE_STREAMING = "STREAMING";
  public static final String API_TYPE_AI        = "AI";
}