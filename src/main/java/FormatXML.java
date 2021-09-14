import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Stack;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.parser.Parser;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class FormatXML {
    private static final String INDENT = "  ";

    @Parameter(names = {"--input", "-i"}, description = "Path to an xml file to format", required = true, order = 0)
    String inputFile;

    @Parameter(names = {"--output", "-o"}, description = "Path to where the output should be stored", order = 1)
    String outputFile;

    @Parameter(names = {"--help", "-h"}, description = "Show help text", order = 1)
    boolean help;

    public static void main(String[] args) throws IOException {
        FormatXML app = new FormatXML();

        JCommander jCommander = JCommander
                .newBuilder()
                .addObject(app)
                .build();

        jCommander.setProgramName(FormatXML.class.getSimpleName());

        try {
            jCommander.parse(args);
        } catch (ParameterException e) {
            jCommander.usage();
            return;
        }

        if (app.help) {
            jCommander.usage();
            return;
        }

        app.run();
    }

    public void run() throws IOException {
        if (outputFile == null || outputFile.isEmpty()) {
            int extensionIdx = inputFile.lastIndexOf('.');
            outputFile = inputFile.substring(0, extensionIdx) + "_formatted" + inputFile.substring(extensionIdx);
        }

        try (FileReader in = new FileReader(inputFile); FileWriter out = new FileWriter(outputFile)) {
            Buffer buff = new Buffer(in);
            StringBuilder output = new StringBuilder();

            long time = System.currentTimeMillis();

            Stack<String> tags = new Stack<>();

            String lastStartTag = null;
            int indentLevel = 0;
            while (!buff.done()) {
                buff.findNext('<'); // Every iteration always starts with a new tag
                buff.getText(); // Drop text before the tag, should only be whitespace
                buff.findNext('>');
                buff.step(true);
                String currentTag = buff.getText();
                if (currentTag.charAt(1) == '/') { // End of a tag, </tag>
                    String other = tags.pop();
                    if (!other.substring(1).startsWith(currentTag.substring(2, currentTag.length() - 1))) {
                        throw new IllegalStateException("Start and end tags to not match! " + other + " vs. " + currentTag);
                    }
                    indentLevel -= 1;

                    if (lastStartTag != null && lastStartTag.substring(1).startsWith(
                            currentTag.substring(2, currentTag.length() - 1))) { // Fuse <tag></tag> -> <tag/>
                        output.setLength(output.length() - lastStartTag.length() - 1);
                        output.append(lastStartTag, 0, lastStartTag.length() - 1);
                        output.append("/>");
                        output.append('\n');
                    } else {
                        output.append(indent(indentLevel));
                        output.append(currentTag);
                        output.append('\n');
                    }
                    lastStartTag = null;
                    out.write(output.toString());
                    output.setLength(0);
                } else if (currentTag.charAt(currentTag.length() - 2) == '/' || currentTag.charAt(1)
                                                                                == '?') { // Empty tag (<tag/>) or XML version
                    output.append(indent(indentLevel));
                    output.append(currentTag);
                    output.append('\n');
                    out.write(output.toString());
                    output.setLength(0);
                    lastStartTag = null;
                } else { // Normal tag, <tag>
                    tags.push(currentTag);
                    if (buff.peek() != '<') { // Next item is text
                        buff.findNext('<');
                        String content = Parser.unescapeEntities(buff.getText().trim(), false);
                        buff.findNext('>');
                        buff.step(true);
                        String endTag = buff.getText();
                        if (!currentTag.substring(1).startsWith(endTag.substring(2, endTag.length() - 1))) {
                            throw new IllegalStateException("Start and end tags to not match! " + currentTag + " vs. " + endTag);
                        }
                        tags.pop();
                        output.append(indent(indentLevel));
                        output.append(currentTag);
                        output.append(content);
                        output.append(endTag);
                        output.append('\n');
                        out.write(output.toString());
                        output.setLength(0);
                        lastStartTag = null;
                    } else { // Next item is a new tag
                        output.append(indent(indentLevel));
                        output.append(currentTag);
                        output.append('\n');
                        lastStartTag = currentTag;
                        indentLevel += 1;
                    }
                }
            }
            System.out.println("Time: " + (System.currentTimeMillis() - time)/1e3 + "s");
        }
    }

    private static String indent(int levels) {
        return StringUtils.repeat(INDENT, levels);
    }


    private static class Buffer {
        private FileReader in;
        private char[] buff;
        private int idx;

        private StringBuilder sb;

        public Buffer(FileReader in) throws IOException {
            this.in = in;
            sb = new StringBuilder();
            readNext();
        }

        public boolean done() {
            return buff == null;
        }

        public char peek() {
            return buff[idx];
        }

        public void findNext(char c) throws IOException {
            int i = idx;
            while (true) {
                if (i == buff.length) {
                    sb.append(buff, idx, buff.length-idx);
                    i = 0;
                    readNext();
                }
                if (buff[i] == '\n' || buff[i] == '\r') {
                    sb.append(buff, idx, i-idx);
                    idx = i+1;
                }
                if (buff[i] == c) {
                    sb.append(buff, idx, i-idx);
                    idx = i;
                    break;
                }
                i++;
            }
        }

        public void step(boolean skipSpace) throws IOException {
            sb.append(buff[idx]);
            do {
                idx += 1;
                if (idx == buff.length) {
                    readNext();
                }
            } while (skipSpace && buff != null && Character.isWhitespace(buff[idx]));
        }

        private void readNext() throws IOException {
            buff = new char[4096];
            int len = in.read(buff);
            if (len == -1) {
                buff = null;
                idx = 0;
                return;
            }
            if (len < buff.length) {
                char[] resized = new char[len];
                System.arraycopy(buff, 0, resized, 0, len);
                buff = resized;
            }
            idx = 0;
        }

        public String getText() {
            String text = sb.toString();
            sb.setLength(0);
            return text;
        }
    }
}
