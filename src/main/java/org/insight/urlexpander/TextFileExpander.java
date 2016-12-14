package org.insight.urlexpander;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.insight.urlexpander.utils.GETCanonicalURL;

import com.linkedin.urls.Url;
import com.linkedin.urls.detection.UrlDetector;
import com.linkedin.urls.detection.UrlDetectorOptions;

public class TextFileExpander {

  /*
   * Process file 1 url per line, output 1 expanded url per line
   * TODO: Better
   */
  public final static void main(String[] args) throws IOException, URISyntaxException {

    Path input = Paths.get(new URI(args[0]));
    Path output = Paths.get(new URI(args[1]));

    output.toFile().createNewFile();

    System.out.println("Input File: " + input.toString());
    System.out.println("Output File: " + output.toString());

    List<String> inputURLs = Files.readAllLines(input);

    try (GETCanonicalURL canonical = new GETCanonicalURL()) {
      // Process:
      for (String processURL : inputURLs) {
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
      }

      canonical.phaser.arriveAndAwaitAdvance(); // await any async tasks to complete


      System.out.println("[debug] Finished requests, writing output...");

      List<String> outputLines = new ArrayList<String>();

      for (String processURL : inputURLs) {
        try {
          UrlDetector parser = new UrlDetector(processURL, UrlDetectorOptions.Default);
          List<Url> urls = parser.detect();

          if (urls.size() < 1) {
            System.err.println(processURL + "\t" + "ERROR: Bad URL"); // silence bad urls
            continue;
          }
          String fullURL = urls.get(0).getFullUrl();
          outputLines.add(canonical.cache.getOrDefault(fullURL, ""));
        } catch (Exception e) {
          continue;
        }
      }

      Files.write(output, outputLines, Charset.forName("UTF-8"), StandardOpenOption.APPEND);
      System.out.println("Done.");
    }



  }
}
