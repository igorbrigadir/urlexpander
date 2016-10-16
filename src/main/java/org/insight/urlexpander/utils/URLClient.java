package org.insight.urlexpander.utils;

import java.util.concurrent.Phaser;

import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.insight.urlexpander.utils.Cache.CacheType;

public class URLClient implements AutoCloseable {

  public final Cache cache = new Cache(CacheType.MEMORY);

  static final String CHROME_USER_AGENT =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Ubuntu Chromium/37.0.2062.120 Chrome/37.0.2062.120 Safari/537.36";

  private final RequestConfig globalConfig;

  public final CloseableHttpAsyncClient client;
  public final Phaser phaser = new Phaser(1); // wait for threads

  public URLClient() {
    globalConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.IGNORE_COOKIES).build();
    client = HttpAsyncClients.custom().setUserAgent(CHROME_USER_AGENT).setDefaultRequestConfig(globalConfig).setMaxConnTotal(1024).build();
    client.start();
  }

  @Override
  public void close() {
    try {
      client.close();
    } catch (Exception e) {
    }
  }

}
