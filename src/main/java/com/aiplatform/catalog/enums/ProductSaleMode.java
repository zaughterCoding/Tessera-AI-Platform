package com.aiplatform.catalog.enums;

public enum ProductSaleMode {
  STANDARD(0),
  LIMITED(1);

  private final int code;

  ProductSaleMode(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
