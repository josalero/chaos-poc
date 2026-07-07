package com.samba.chaos.demo.exception;

public class DuplicateOrderException extends RuntimeException {

  public DuplicateOrderException(String sku) {
    super("Order already exists for SKU: " + sku);
  }
}
