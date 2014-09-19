package joantune.utils.PDFExtractor;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;

import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.ContentByteUtils;
import com.itextpdf.text.pdf.parser.PdfContentStreamProcessor;
import com.itextpdf.text.pdf.parser.PdfReaderContentParser;
import com.itextpdf.text.pdf.parser.RenderListener;
import com.itextpdf.text.pdf.parser.SimpleTextExtractionStrategy;
import com.itextpdf.text.pdf.parser.TaggedPdfReaderTool;
import com.itextpdf.text.pdf.parser.TextExtractionStrategy;

/**
 * Hello world!
 *
 */
//TODO: ver http://itextpdf.com/examples/iia.php?id=279
//e : http://itextpdf.com/examples/iia.php?id=277
//http://itextpdf.com/examples/iia.php?id=109
public class App {
    private static final int MIN_NUMBER_OF_INTERESTING_LINES = 4;

    public static void main(String[] args) {
        System.out.println("Arguments size" + args.length + " Arguments: " + Arrays.toString(args));
        Options options = new Options();
        options.addOption("o", true, "name/path of the output CSV file");
        //options.addOption("i", true, "name/path of the optional intermediate pdf file with the relevant data");
        options.addOption("f", true, "name/path of the input PDF file");

        CommandLineParser parser = new GnuParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args, true);
        } catch (ParseException e) {
            System.err.println("Error parsing command arguments" + e.getMessage());
        }
        if (cmd.hasOption("o") == false || cmd.hasOption("f") == false) {
            HelpFormatter formatter = new HelpFormatter();
            System.out.println("You need to specify both an o and an f flag");
            formatter.printHelp("PDFExtractor", options);
        } else {
            try {

                //execute(cmd);
                executeSimpleText(cmd);
            } catch (IOException io) {
                System.err.println("Caught an error. " + io.getMessage());
            }
        }

    }

    private static void execute(CommandLine cmd) throws IOException {
        if (cmd.hasOption("i")) {

        }
        //let's read the PDF file
        String filePath = cmd.getOptionValue("f");
        System.out.println("Reading file: " + filePath);
        PdfReader pdfReader = null;
        try {
            pdfReader = new PdfReader(filePath);
        } catch (IOException e) {
            System.err.println("Problem while reading pdf." + e.getMessage());
            return;
        }

        PrintWriter out = new PrintWriter(System.out);
        RenderListener listener = new MyTextRenderListener(out);
        PdfContentStreamProcessor processor = new PdfContentStreamProcessor(listener);
        PdfDictionary pageDic = pdfReader.getPageN(4);
        PdfDictionary resourcesDic = pageDic.getAsDict(PdfName.RESOURCES);
        processor.processContent(ContentByteUtils.getContentBytesForPage(pdfReader, 4), resourcesDic);
        out.flush();
        out.close();

    }

    static Pattern pattern = Pattern.compile("(\\d - \\d) (.*)");

    private static void executeSimpleText(CommandLine cmd) throws IOException {
//        if (cmd.hasOption("i")) {
//
//        }

        String outputPath = cmd.getOptionValue("o");
        File file = new File(outputPath);
        if (file.createNewFile() == false) {
            System.err.println("Cannot continue, output file: " + outputPath
                    + " already exists or cannot be created, give a different output or remove that file");
            System.exit(1);
        }

        PrintStream printStream = new PrintStream(file);

        //let's read the PDF file
        String filePath = cmd.getOptionValue("f");
        System.out.println("Reading file: " + filePath);
        PdfReader pdfReader = null;
        try {
            pdfReader = new PdfReader(filePath);
        } catch (IOException e) {
            System.err.println("Problem while reading pdf." + e.getMessage());
            return;
        }

        PdfReaderContentParser parser = new PdfReaderContentParser(pdfReader);
        PrintWriter out = new PrintWriter(System.out);
        TextExtractionStrategy strategy;
        try {

            int nrPages = pdfReader.getNumberOfPages();
            for (int i = 1; i <= nrPages; i++) {
                strategy = parser.processContent(i, new SimpleTextExtractionStrategy());
                String text = strategy.getResultantText();
                if (StringUtils.containsIgnoreCase(text, "Tukey")) {
                    //System.out.println("Page " + i + " is of interest");
                    String targetId = StringUtils.substringBetween(text, "TargetID=", "\n");
                    //System.out.println("Got the following TargetID: " + targetId);

                    int indexOfLimits = StringUtils.lastIndexOfIgnoreCase(text, "Limits");
                    if (indexOfLimits == -1) {
                        System.out.println("**WARNING**: Could not find 'Limits' in page " + i + " but it has Tukey **");
                        continue;
                    } else {
                        String limitsTable = StringUtils.substring(text, indexOfLimits);
                        //System.out.println("Limits table:\n" + limitsTable);
                        String[] limitsTableContents = limitsTable.split("\n");
                        Set<String> interestingLines = new HashSet<String>(limitsTableContents.length - 1);
                        for (String limitLine : limitsTableContents) {
                            if ("Limits".equalsIgnoreCase(limitLine)) {
                                continue;
                            }
                            if (limitLine.contains("***")) {
                                //got an interesting line, let's add it and see if we should process it
                                //System.out.println("Line: '" + limitLine + "' is of interest");
                                interestingLines.add(limitLine);

                            }
                        }

                        //how many lines did we get
                        if (interestingLines.size() >= MIN_NUMBER_OF_INTERESTING_LINES) {
                            //we ought to process this one
                            for (String limitLine : interestingLines) {

                                Matcher matcher = pattern.matcher(limitLine);
                                if (matcher.matches()) {
                                    //let's extract the group
                                    String group = matcher.group(1);
                                    //System.out.println("Extracted group: " + group);
                                    //let's write the results
                                    printStream.println(targetId + "," + group + "," + "***");

                                } else {
                                    System.out.println("**WARNING**: Could not find group in limits table line: " + limitLine
                                            + " in page " + i + " but we found the limits table and it had at least "
                                            + MIN_NUMBER_OF_INTERESTING_LINES + " interesting lines");
                                }
                            }

                        }

                    }

                    //out.println(strategy.getResultantText());
                }
            }
        } finally {
            printStream.flush();
            printStream.close();
            out.flush();
            out.close();
        }

    }

    private static void executeXML(CommandLine cmd) throws IOException {
        if (cmd.hasOption("i")) {

        }
        //let's read the PDF file
        String filePath = cmd.getOptionValue("f");
        System.out.println("Reading file: " + filePath);
        TaggedPdfReaderTool readerTool = new TaggedPdfReaderTool();
        PdfReader pdfReader = null;
        try {
            pdfReader = new PdfReader(filePath);
        } catch (IOException e) {
            System.err.println("Problem while reading pdf." + e.getMessage());
            return;
        }

        readerTool.convertToXml(pdfReader, System.out);
//        PrintWriter out = new PrintWriter(System.out);
//        RenderListener listener = new MyTextRenderListener(out);
//        PdfContentStreamProcessor processor = new PdfContentStreamProcessor(listener);
//        PdfDictionary pageDic = pdfReader.getPageN(4);
//        PdfDictionary resourcesDic = pageDic.getAsDict(PdfName.RESOURCES);
//        processor.processContent(ContentByteUtils.getContentBytesForPage(pdfReader, 4), resourcesDic);
//        out.flush();
//        out.close();
        pdfReader.close();

    }
}
