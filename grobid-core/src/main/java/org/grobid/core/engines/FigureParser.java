package org.grobid.core.engines;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.grobid.core.GrobidModels;
import org.grobid.core.document.Document;
import org.grobid.core.document.DocumentPiece;
import org.grobid.core.document.DocumentPointer;
import org.grobid.core.dom.FigureDomParser;
import org.grobid.core.features.FeatureFactory;
import org.grobid.core.features.FeaturesVectorFulltext;
import org.grobid.core.layout.Block;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.Pair;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.regex.Matcher;

/**
 * @author Niket Shah
 */
public class FigureParser extends AbstractParser {


    private EngineParsers parsers;

    // default bins for relative position
    private static final int NBBINS = 12;

    public FigureParser(EngineParsers parsers) {
        super(GrobidModels.FIGURE);
        this.parsers = parsers;
        GrobidProperties.getInstance();
    }

    /**
     * Processing with application of the segmentation model
     */
    public void processing(String input, String assetPath) {
        Document doc = parsers.getSegmentationParser().processing(input, assetPath);

        try {
            // create figureVecs directory if it doesn't already exist
            File figureVecDirectory = new File(assetPath + "/figureVecs");
            figureVecDirectory.mkdirs();
            FileUtils.cleanDirectory(figureVecDirectory);

            // create figureSVGs directory if it doesn't already exist
            File figureSVGDirectory = new File(assetPath + "/figureSVGs");
            figureSVGDirectory.mkdirs();
            FileUtils.cleanDirectory(figureSVGDirectory);
        } catch (IOException e) {
            e.printStackTrace();
        }

        File folder = new File(assetPath);
        File[] listOfFiles = folder.listFiles();

        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile() && FilenameUtils.getExtension(listOfFiles[i].getName()).equals("vec")) {
                FigureDomParser.separateFigures(listOfFiles[i], assetPath);
            }
        }

        System.out.println("done");
    }

    public void processingFigures(Document doc) {
        SortedSet<DocumentPiece> documentBodyParts = doc.getDocumentPart(SegmentationLabel.BODY);
        Pair<String,List<String>> featSeg = getFiguresFeatured(doc, documentBodyParts);
        String labelledText = null;
        List<String> tokenizationsBody = null;
        if (featSeg != null) {
            // if featSeg is null, it usually means that no figure segments were found in the
            // document segmentation
            String figureText = featSeg.getA();
            tokenizationsBody = featSeg.getB();
            if ( (figureText != null) && (figureText.trim().length() > 0) ) {
                labelledText = label(figureText);

                // print file out for debugging purposes

                try {
                    PrintWriter out = new PrintWriter("rese.txt");
                    out.println(labelledText);
                    out.flush();
                    out.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private Pair<String, List<String>> getFiguresFeatured(Document doc, SortedSet<DocumentPiece> documentFigureParts) {
        if ((documentFigureParts == null) || (documentFigureParts.size() == 0)) {
            return null;
        }
        FeatureFactory featureFactory = FeatureFactory.getInstance();
        StringBuilder fulltext = new StringBuilder();
        String currentFont = null;
        int currentFontSize = -1;

        List<Block> blocks = doc.getBlocks();
        if ( (blocks == null) || blocks.size() == 0) {
            return null;
        }

        // vector for features
        FeaturesVectorFulltext features;
        FeaturesVectorFulltext previousFeatures = null;
        boolean endblock;
        boolean endPage = true;
        boolean newPage = true;
        boolean start = true;
        int mm = 0; // page position
        int nn = 0; // document position
        int documentLength = 0;
        int pageLength = 0; // length of the current page

        List<String> tokenizationsBody = new ArrayList<String>();
        List<String> tokenizations = doc.getTokenizations();

        // we calculate current document length and intialize the body tokenization structure
        for(DocumentPiece docPiece : documentFigureParts) {
            DocumentPointer dp1 = docPiece.a;
            DocumentPointer dp2 = docPiece.b;

            int tokens = dp1.getTokenDocPos();
            int tokene = dp2.getTokenDocPos();
            for (int i = tokens; i <= tokene; i++) {
                tokenizationsBody.add(tokenizations.get(i));
                documentLength++;
            }
        }

        // System.out.println("documentLength: " + documentLength);
        for(DocumentPiece docPiece : documentFigureParts) {
            DocumentPointer dp1 = docPiece.a;
            DocumentPointer dp2 = docPiece.b;

            //int blockPos = dp1.getBlockPtr();
            for(int blockIndex = dp1.getBlockPtr(); blockIndex <= dp2.getBlockPtr(); blockIndex++) {
                Block block = blocks.get(blockIndex);

                // we estimate the length of the page where the current block is
                if (start || endPage) {
                    boolean stop = false;
                    pageLength = 0;
                    for (int z = blockIndex; (z < blocks.size()) && !stop; z++) {
                        String localText2 = blocks.get(z).getText();
                        if (localText2 != null) {
                            if (localText2.contains("@PAGE")) {
                                if (pageLength > 0) {
                                    if (blocks.get(z).getTokens() != null) {
                                        pageLength += blocks.get(z).getTokens()
                                                .size();
                                    }
                                    stop = true;
                                    break;
                                }
                            } else {
                                if (blocks.get(z).getTokens() != null) {
                                    pageLength += blocks.get(z).getTokens().size();
                                }
                            }
                        }
                    }
                    // System.out.println("pageLength: " + pageLength);
                }
                if (start) {
                    newPage = true;
                    start = false;
                }
                boolean newline;
                boolean previousNewline = false;
                endblock = false;

                if (endPage) {
                    newPage = true;
                    mm = 0;
                }

                String localText = block.getText();
                if (localText != null) {
                    if (localText.contains("@PAGE")) {
                        mm = 0;
                        // pageLength = 0;
                        endPage = true;
                        newPage = false;
                    } else {
                        endPage = false;
                    }
                }

                List<LayoutToken> tokens = block.getTokens();
                if (tokens == null) {
                    //blockPos++;
                    continue;
                }

                int n = 0;// token position in current block
                if (blockIndex == dp1.getBlockPtr()) {
                    n = dp1.getTokenDocPos() - block.getStartToken();
					/*if (n != 0) {
						n = n - 1;
					}*/
                }
                while (n < tokens.size()) {
                    if (blockIndex == dp2.getBlockPtr()) {
                        //if (n > block.getEndToken()) {
                        if (n > dp2.getTokenDocPos() - block.getStartToken()) {
                            break;
                        }
                    }

                    LayoutToken token = tokens.get(n);
                    features = new FeaturesVectorFulltext();
                    features.token = token;

                    String text = token.getText();
                    if (text == null) {
                        n++;
                        mm++;
                        nn++;
                        continue;
                    }
                    text = text.replace(" ", "");
                    if (text.length() == 0) {
                        n++;
                        mm++;
                        nn++;
                        continue;
                    }

                    if (text.equals("\n")) {
                        newline = true;
                        previousNewline = true;
                        n++;
                        mm++;
                        nn++;
                        continue;
                    } else
                        newline = false;

                    if (previousNewline) {
                        newline = true;
                        previousNewline = false;
                    }

                    boolean filter = false;
                    if (text.startsWith("@IMAGE")) {
                        filter = true;
                    } else if (text.contains(".pbm")) {
                        filter = true;
                    } else if (text.contains(".vec")) {
                        filter = true;
                    } else if (text.contains(".jpg")) {
                        filter = true;
                    }

                    if (filter) {
                        n++;
                        mm++;
                        nn++;
                        continue;
                    }

                    features.string = text;

                    if (newline)
                        features.lineStatus = "LINESTART";
                    Matcher m0 = featureFactory.isPunct.matcher(text);
                    if (m0.find()) {
                        features.punctType = "PUNCT";
                    }
                    if (text.equals("(") || text.equals("[")) {
                        features.punctType = "OPENBRACKET";

                    } else if (text.equals(")") || text.equals("]")) {
                        features.punctType = "ENDBRACKET";

                    } else if (text.equals(".")) {
                        features.punctType = "DOT";

                    } else if (text.equals(",")) {
                        features.punctType = "COMMA";

                    } else if (text.equals("-")) {
                        features.punctType = "HYPHEN";

                    } else if (text.equals("\"") || text.equals("\'") || text.equals("`")) {
                        features.punctType = "QUOTE";

                    }

                    if (n == 0) {
                        features.lineStatus = "LINESTART";
                        features.blockStatus = "BLOCKSTART";
                    } else if (n == tokens.size() - 1) {
                        features.lineStatus = "LINEEND";
                        previousNewline = true;
                        features.blockStatus = "BLOCKEND";
                        endblock = true;
                    } else {
                        // look ahead...
                        boolean endline = false;

                        int ii = 1;
                        boolean endloop = false;
                        while ((n + ii < tokens.size()) && (!endloop)) {
                            LayoutToken tok = tokens.get(n + ii);
                            if (tok != null) {
                                String toto = tok.getText();
                                if (toto != null) {
                                    if (toto.equals("\n")) {
                                        endline = true;
                                        endloop = true;
                                    } else {
                                        if ((toto.length() != 0)
                                                && (!(toto.startsWith("@IMAGE")))
                                                && (!text.contains(".pbm"))
                                                && (!text.contains(".vec"))
                                                && (!text.contains(".jpg"))) {
                                            endloop = true;
                                        }
                                    }
                                }
                            }

                            if (n + ii == tokens.size() - 1) {
                                endblock = true;
                                endline = true;
                            }

                            ii++;
                        }

                        if ((!endline) && !(newline)) {
                            features.lineStatus = "LINEIN";
                        }
                        else if (!newline) {
                            features.lineStatus = "LINEEND";
                            previousNewline = true;
                        }

                        if ((!endblock) && (features.blockStatus == null))
                            features.blockStatus = "BLOCKIN";
                        else if (features.blockStatus == null) {
                            features.blockStatus = "BLOCKEND";
                            endblock = true;
                        }
                    }

                    if (newPage) {
                        features.pageStatus = "PAGESTART";
                        newPage = false;
                        endPage = false;
                        if (previousFeatures != null)
                            previousFeatures.pageStatus = "PAGEEND";
                    } else {
                        features.pageStatus = "PAGEIN";
                        newPage = false;
                        endPage = false;
                    }

                    if (text.length() == 1) {
                        features.singleChar = true;
                    }

                    if (Character.isUpperCase(text.charAt(0))) {
                        features.capitalisation = "INITCAP";
                    }

                    if (featureFactory.test_all_capital(text)) {
                        features.capitalisation = "ALLCAP";
                    }

                    if (featureFactory.test_digit(text)) {
                        features.digit = "CONTAINSDIGITS";
                    }

                    if (featureFactory.test_common(text)) {
                        features.commonName = true;
                    }

                    if (featureFactory.test_names(text)) {
                        features.properName = true;
                    }

                    if (featureFactory.test_month(text)) {
                        features.month = true;
                    }

                    Matcher m = featureFactory.isDigit.matcher(text);
                    if (m.find()) {
                        features.digit = "ALLDIGIT";
                    }

                    Matcher m2 = featureFactory.YEAR.matcher(text);
                    if (m2.find()) {
                        features.year = true;
                    }

                    Matcher m3 = featureFactory.EMAIL.matcher(text);
                    if (m3.find()) {
                        features.email = true;
                    }

                    Matcher m4 = featureFactory.HTTP.matcher(text);
                    if (m4.find()) {
                        features.http = true;
                    }

                    if (currentFont == null) {
                        currentFont = token.getFont();
                        features.fontStatus = "NEWFONT";
                    } else if (!currentFont.equals(token.getFont())) {
                        currentFont = token.getFont();
                        features.fontStatus = "NEWFONT";
                    } else
                        features.fontStatus = "SAMEFONT";

                    int newFontSize = (int) token.getFontSize();
                    if (currentFontSize == -1) {
                        currentFontSize = newFontSize;
                        features.fontSize = "HIGHERFONT";
                    } else if (currentFontSize == newFontSize) {
                        features.fontSize = "SAMEFONTSIZE";
                    } else if (currentFontSize < newFontSize) {
                        features.fontSize = "HIGHERFONT";
                        currentFontSize = newFontSize;
                    } else if (currentFontSize > newFontSize) {
                        features.fontSize = "LOWERFONT";
                        currentFontSize = newFontSize;
                    }

                    if (token.getBold())
                        features.bold = true;

                    if (token.getItalic())
                        features.italic = true;

                    // HERE horizontal information
                    // CENTERED
                    // LEFTAJUSTED
                    // CENTERED

                    if (features.capitalisation == null)
                        features.capitalisation = "NOCAPS";

                    if (features.digit == null)
                        features.digit = "NODIGIT";

                    if (features.punctType == null)
                        features.punctType = "NOPUNCT";

                    features.relativeDocumentPosition = featureFactory
                            .relativeLocation(nn, documentLength, NBBINS);
                    // System.out.println(mm + " / " + pageLength);
                    features.relativePagePosition = featureFactory
                            .relativeLocation(mm, pageLength, NBBINS);

                    // fulltext.append(features.printVector());
                    if (previousFeatures != null)
                        fulltext.append(previousFeatures.printVector());
                    n++;
                    mm++;
                    nn++;
                    previousFeatures = features;
                }
                //blockPos++;
            }
        }
        if (previousFeatures != null)
            fulltext.append(previousFeatures.printVector());

        return new Pair<String,List<String>>(fulltext.toString(), tokenizationsBody);
    }
}
