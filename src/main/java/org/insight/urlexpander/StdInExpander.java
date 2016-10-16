package org.insight.urlexpander;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import org.insight.urlexpander.utils.GETCanonicalURL;

import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;

public class StdInExpander {

  /*
   * Simple command line - read stdin
   */
  public final static void main(String[] args) {
    try (GETCanonicalURL canonical = new GETCanonicalURL()) {

      BufferedReader buffer = new BufferedReader(new InputStreamReader(System.in));

      while (true) {
        String readin = "";
        try {
          readin = buffer.readLine();
        } catch (IOException e) {
          continue;
        }

        final String processURL = readin;
        if ((processURL == null) || (processURL.equalsIgnoreCase("exit"))) {
          canonical.phaser.arriveAndAwaitAdvance(); // await any async tasks to complete
          break;
        }

        //TODO: Extra Cleanup steps for urls here?
        try {

          UrlDetector parser = new UrlDetector(processURL, UrlDetectorOptions.Default);
          List<Url> urls = parser.detect();

          if (urls.size() < 1) {
            System.err.println(processURL + "\t" + "ERROR: Bad URL"); // silence bad urls
            continue;
          }

          String fullURL = urls.get(0).getFullUrl();
          canonical.makeRequest(fullURL, fullURL);

        } catch (Exception e) {
          // e.printStackTrace();
          continue;
        }

      } // loop

    }

  }

}
