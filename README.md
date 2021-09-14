# Xml Formatter
A small tool that formats/pretty prints XML files efficiently. Suitable for very large files since it doesn't use a DOM parser
and therefore doesn't load the whole file to memory.

The tool has been tested on files as large as 40GB.

## Usage
Compile the program with `mvn clean package` and run the resulting jar-file as follows
```
java -jar xml-formatter-1.0-SNAPSHOT.jar --input <input xml> --output <output file>
```

## License
This program is free to use, and is licensed under the GPLv3 license, see LICENSE for more details.