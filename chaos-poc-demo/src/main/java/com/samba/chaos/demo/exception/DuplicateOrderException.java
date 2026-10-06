package com.samba.chaos.demo.exception;

/** Duplicate Order Exception. */
public class DuplicateOrderException extends RuntimeException {

  /** Duplicate Order Exception. */
  public DuplicateOrderException(String sku) {
    super("Order already exists for SKU: " + sku);
  }
}
