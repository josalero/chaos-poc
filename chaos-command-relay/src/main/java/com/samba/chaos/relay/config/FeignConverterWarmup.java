package com.samba.chaos.relay.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.cloud.openfeign.FeignClientFactory;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.stereotype.Component;

/**
 * Initializes the chaos-command Feign converter list during startup.
 *
 * <p>OpenFeign 5.0.0 publishes that list before it is filled. The first concurrent calls can then
 * decode against an empty list. Resolving the converters here runs that initialization on the
 * startup thread.
 */
@Component
class FeignConverterWarmup implements SmartInitializingSingleton {

  private final FeignClientFactory feignClientFactory;

  FeignConverterWarmup(FeignClientFactory feignClientFactory) {
    this.feignClientFactory = feignClientFactory;
  }

  @Override
  public void afterSingletonsInstantiated() {
    FeignHttpMessageConverters converters =
        feignClientFactory.getInstance("chaos-command", FeignHttpMessageConverters.class);
    if (converters != null) {
      converters.getConverters();
    }
  }
}
