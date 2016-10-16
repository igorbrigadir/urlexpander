package org.insight.urlexpander.utils;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.client.RedirectLocations;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;

/*
 * Follow Redirects from short urls using GET http requests, halt once rel=canonical or OG:URL found, avoid rest of page body. Pipe URLS into STDIN, STDOUT is
 * original URL \t Expanded
 */
public class GETCanonicalURL extends URLClient {

  private static final boolean debug = false;

  /*
   * Remember where things point, only output new, unseen redirects:
   */
  public void outputAndCache(String fromURL, String toURL) {
    if (fromURL.equals(toURL)) {
      if (debug) {
        System.err.println("SAME URLS:" + fromURL + "\n" + toURL);
      }
      if (!cache.containsKey(fromURL)) {
        cache.put(fromURL, toURL);
      } else {
        return;
      }
    }

    if (!cache.containsKey(toURL)) {
      System.out.println(String.format("%s\t%s", fromURL, toURL));
    }

    cache.put(fromURL, toURL);

    if (debug) {
      System.err.println("Cache: (" + cache.keySet().size() + " -> " + cache.values().size() + ")\n" + fromURL + "->" + toURL);
    }
  }

  public String cleanupURL(String processURL, String lastLoc, String canonicalURL, String requestURL) {
    if (debug) {
      System.err.println("Cleanup: \nproc:" + processURL + "\nlast:" + lastLoc + "\ncann:" + canonicalURL + "\n for:" + requestURL);
    }
    // If there's no canonical URL (Nothing in head, go with the last location)
    if (canonicalURL == null) {
      if (debug) {
        System.err.println("WARNING: NULL Canonical URL: " + canonicalURL + " using " + lastLoc);
      }
      canonicalURL = lastLoc;
    }
    // If canonical url in html is broken, use last redirect location:
    UrlDetector parser = new UrlDetector(canonicalURL, UrlDetectorOptions.Default);
    List<Url> urls = parser.detect();
    if (urls.size() < 1) {
      if (debug) {
        System.err.println("WARNING: Bad Canonical URL: " + canonicalURL + " using " + lastLoc);
      }
      canonicalURL = lastLoc;
    }
    // Cleanup final URL from utm_ parameters:
    String finalURL = canonicalURL;
    HttpUrl canonicalLink = HttpUrl.parse(canonicalURL);
    if (debug) {
      System.err.println("URL Query: " + canonicalLink.query());
    }
    if (canonicalLink.querySize() > 0) {
      HttpUrl cleanURL =
          canonicalLink.newBuilder().removeAllQueryParameters("utm_source").removeAllQueryParameters("utm_medium").removeAllQueryParameters("utm_term")
              .removeAllQueryParameters("utm_content").removeAllQueryParameters("utm_campaign").build();
      finalURL = cleanURL.toString();
    }
    // Point everything to cleaned up URL:

    outputAndCache(processURL, finalURL);
    if (!requestURL.equals(processURL)) {
      outputAndCache(requestURL, finalURL);
    }

    return finalURL;
  }

  public String cleanupRelativePath(String canonicalURL, String lastLocURL) {
    // Replace known junk:
    canonicalURL = canonicalURL.replace("&amp;_fb_noscript=1", "");
    HttpUrl lastLoc = HttpUrl.parse(lastLocURL);
    String domain = lastLocURL;
    if (lastLoc.encodedFragment() != null) {
      domain = domain.replace(lastLoc.encodedFragment(), "");
    }
    if (lastLoc.encodedQuery() != null) {
      domain = domain.replace("?" + lastLoc.encodedQuery(), "");
    }
    if (lastLoc.encodedPath() != null) {
      domain = domain.replace(lastLoc.encodedPath(), "");
    }
    if (debug) {
      System.err.println("RELATIVE REFRESH PATH:\n" + lastLoc + "\n" + canonicalURL + "\n" + domain);
    }
    return String.format("%s%s", domain, canonicalURL);
  }

  /*
   * Expand URL, cache & print redirects
   */
  public void makeRequest(String processURL) {
    makeRequest(processURL, processURL); // Process URL
  }

  /*
   * processURL: the URL that needs expanding
   * requestURL: the URL that was first requested
   */
  public void makeRequest(String processURL, String requestURL) {

    if (cache.containsKey(processURL)) {
      System.out.println(String.format("%s\t%s", processURL, cache.get(processURL)));
      if (debug) {
        System.err.println("Cached, skipping: " + processURL + " for " + requestURL + phaser);
      }
      return;
    }

    phaser.register();
    if (debug) {
      System.err.println("Making request:" + processURL + " for " + requestURL + "\n" + phaser);
    }

    final HttpGet request = new HttpGet(processURL);
    final HttpContext context = new BasicHttpContext();

    FutureCallback<HttpResponse> callback = new FutureCallback<HttpResponse>() {
      @Override
      public void completed(final HttpResponse response) {
        if (debug) {
          System.err.println(response.getEntity().isStreaming() + " " + response.getEntity().getContentLength());
          //RedirectLocations locations = (RedirectLocations) context.getAttribute(HttpClientContext.REDIRECT_LOCATIONS);
          //System.err.println(request.getRequestLine() + " -> " + response.getStatusLine() + " Redirects: " + locations);
          System.err.println("Scanning:");
        }

        if (response.getStatusLine().getStatusCode() >= 400) {
          if (debug) {
            System.err.println(processURL + "\t ERROR:" + response.getStatusLine().getStatusCode());
          }
          outputAndCache(processURL, String.format("%s", response.getStatusLine().getStatusCode()));
          phaser.arriveAndDeregister();
          return;
        }

        RedirectLocations locations = (RedirectLocations) context.getAttribute(HttpClientContext.REDIRECT_LOCATIONS);
        String lastLoc = processURL;
        if (locations != null) {
          lastLoc = locations.getAll().get(locations.getAll().size() - 1).toString();
        }

        long readBytes = 0L;
        String canonicalURL = null;
        boolean manualRedirect = false;

        try (Scanner scan = new Scanner(response.getEntity().getContent())) {
          scan.useDelimiter(Pattern.compile(">"));

          scanner: while (scan.hasNext()) {
            String line = scan.next();
            if (debug) {
              System.err.println(line + ">");
            }
            readBytes += line.getBytes().length;

            // Check for canonical URL meta tags:
            int findCanonical = line.toLowerCase().indexOf("rel=\"canonical\"");
            if (findCanonical >= 0) {
              int contentStart = line.indexOf("href=\"") + 6; // 6 is href=" length
              int contentEnd = line.indexOf("\"", contentStart);
              canonicalURL = line.substring(contentStart, contentEnd);
              if (debug) {
                System.err.println("Abort request, canonical found: " + findCanonical + " " + canonicalURL);
              }
              // Fix Relative path:
              if (canonicalURL.startsWith("/")) {
                canonicalURL = cleanupRelativePath(canonicalURL, lastLoc);
              }
              // Do not read the rest of response body, abort.
              request.abort();
              break scanner;
            }

            // Check for canonical URL in meta property:
            int findMetaOG = line.toLowerCase().indexOf("property=\"og:url\"");
            if (findMetaOG >= 0) {
              int contentStart = line.indexOf("content=\"") + 9;
              int contentEnd = line.indexOf("\"", contentStart);
              canonicalURL = line.substring(contentStart, contentEnd);
              if (debug) {
                System.err.println("Abort request, OG:URL found: " + findMetaOG + " " + canonicalURL);
              }
              // Fix Relative path:
              if (canonicalURL.startsWith("/")) {
                canonicalURL = cleanupRelativePath(canonicalURL, lastLoc);
              }
              // Do not read the rest of response body, abort.
              request.abort();
              break scanner;
            }

            // Does the page immediately redirect someplace else?
            int findMetaRefresh = line.toLowerCase().indexOf("http-equiv=\"refresh\" content=\"0;");
            if (findMetaRefresh >= 0) {
              int contentStart = line.indexOf("URL=") + 4;
              int contentEnd = line.indexOf("\"", contentStart);
              canonicalURL = line.substring(contentStart, contentEnd);
              if (debug) {
                System.err.println("Abort request, META refresh found at char " + findMetaRefresh + ":" + canonicalURL);
              }
              // Fix Relative path:
              if (canonicalURL.startsWith("/")) {
                canonicalURL = cleanupRelativePath(canonicalURL, lastLoc);
              }
              // Force a manual redirect (another request)
              if (!cache.containsKey(canonicalURL)) {
                manualRedirect = true;
              }
              // Do not read the rest of response body, abort.
              request.abort();
              break scanner;
            }

            //Stop completely before body starts:
            int findHtmlHead = line.toLowerCase().indexOf("</head");
            if (findHtmlHead >= 0) {
              if (debug) {
                System.err.println("Abort request: HEAD:" + findHtmlHead + " " + line);
              }
              // Do not read the rest of response body, abort.
              request.abort();
              break scanner;
            }
          } // scanner

        } catch (UnsupportedOperationException e) {
          if (debug) {
            e.printStackTrace();
          }
        } catch (IOException e) {
          if (debug) {
            e.printStackTrace();
          }
        }

        if (debug) {
          System.err.println("Read: " + readBytes);
        }

        String finalURL = cleanupURL(processURL, lastLoc, canonicalURL, requestURL);
        // Now go back and fix all the redirects to canonical:
        if (locations != null) {
          for (URI u : locations.getAll()) {
            if (debug) {
              System.err.println("Fix redirects:" + u.toString() + " -> " + finalURL);
            }
            outputAndCache(u.toString(), finalURL);
          }
        }

        phaser.arriveAndDeregister();
        if (debug) {
          System.err.println("Completed Request: " + processURL + " for " + requestURL);
        }

        // Is this a new manual redirect? Make a separate request:
        if (manualRedirect) {
          if (debug) {
            System.err.println("Processing Manual Redirect to: " + finalURL + " from " + requestURL);
          }
          makeRequest(finalURL, processURL);
        }
      } // completed

      @Override
      public void failed(final Exception ex) {
        if (debug) {
          System.err.println("FAILED REQUEST:" + processURL);
          ex.printStackTrace();
        }
        outputAndCache(processURL, String.format("%s", 404));
        phaser.arriveAndDeregister();
      }

      @Override
      public void cancelled() {
        if (debug) {
          System.err.println("CANCELLED REQUEST:" + processURL);
        }
        outputAndCache(processURL, requestURL);
        phaser.arriveAndDeregister();
      }

    };

    client.execute(request, context, callback);
  }

}
