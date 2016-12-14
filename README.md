# urlexpander

```
mvn clean
mvn package
mvn assembly:single
```

StdIn:

```
cat short-url-per-line.txt | java -jar urlex.jar > short-url-tab-longurl.txt
```

File:

```
java -cp urlex.jar org.insight.urlexpander.TextFileExpander file:///path/to/input.txt file:///path/to/output.txt
```