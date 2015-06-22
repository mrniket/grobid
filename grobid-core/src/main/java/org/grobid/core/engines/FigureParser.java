package org.grobid.core.engines;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.grobid.core.document.Document;
import org.grobid.core.layout.Block;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.utilities.ImageUtils;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Niket Shah
 */
public class FigureParser {


    private final String assetPath;
    private Document document;
    private EngineParsers parsers;

    // default bins for relative position
    private static final int NBBINS = 12;
    private LayoutToken previousLayoutToken;


    public FigureParser(String assetPath) {
        this.assetPath = assetPath;
        System.out.println("assetPath: " + assetPath);
        try {
            //clear assetPath directory
            FileUtils.cleanDirectory(new File(assetPath));

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
    }

    public void addLayoutTokenToSVG(Map<Integer, org.w3c.dom.Document> pageToSVGDocument, LayoutToken layoutToken) {
        org.w3c.dom.Document document = pageToSVGDocument.get(layoutToken.getPageNumber());
        if (document != null) {
            Element rootElement = (Element)document.getFirstChild();
            if (previousLayoutToken != null && previousLayoutToken.getX() == layoutToken.getX() && previousLayoutToken.getY() == layoutToken.getY()) {
                Element element = (Element)rootElement.getLastChild();
                element.setTextContent(element.getTextContent() + layoutToken.getText());
            } else {
                Element element = document.createElement("text");
                element.setAttribute("x", String.valueOf(layoutToken.getX()));
                element.setAttribute("y", String.valueOf(layoutToken.getY()));
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("font-size:" + layoutToken.getFontSize() + "px;");
                if (layoutToken.getBold()) {
                    stringBuilder.append("font-weight:bold;");
                }
                if (layoutToken.getItalic()) {
                    stringBuilder.append("font-style:italic;");
                }
                if (layoutToken.getRotation()) {
                    element.setAttribute("dominant-baseline", "text-after-edge");
                    element.setAttribute("transform", "rotate(" + (-1 * layoutToken.getRotationValue()) + " " + layoutToken.getX() + " " + (layoutToken.getY()) + ") translate(" + (-1 * layoutToken.height) + " " + (layoutToken.width /2) + " )");
                } else {
                    element.setAttribute("dominant-baseline", "text-before-edge");
                }
                element.setAttribute("font-family", layoutToken.getFont().split("\\+")[1]);
                element.setAttribute("style", stringBuilder.toString());

                element.setTextContent(layoutToken.getText());
                rootElement.appendChild(element);
            }
            previousLayoutToken = layoutToken;
        }
    }

    public void saveAndCloseSVGDocuments(Map<Integer, org.w3c.dom.Document> pageToSVGDocument, String assetPath) {
        try {
            String figureSVGPath = null;
            if (assetPath.charAt(assetPath.length()  -1) == '/') {
                figureSVGPath = StringUtils.chop(assetPath) + "/figureSVGs";
            } else {
                figureSVGPath = assetPath + "/figureSVGs";
            }
            for (Integer pageNumber : pageToSVGDocument.keySet()) {
                org.w3c.dom.Document document = pageToSVGDocument.get(pageNumber);
                // write the content into xml file
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                DOMSource source = new DOMSource(document);
                StreamResult result = new StreamResult(new File(figureSVGPath + "/page-" + (pageNumber + 1) + ".svg"));
                transformer.transform(source, result);
                System.out.println("wrote to " + figureSVGPath + "/page-" + (pageNumber + 1) + ".svg");
            }
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
        }


    }

    public Map<Integer, org.w3c.dom.Document> openSVGDocuments(String assetPath) {
        try {
            String figureSVGPath = null;
            if (assetPath.charAt(assetPath.length()  -1) == '/') {
                figureSVGPath = StringUtils.chop(assetPath) + "/figureSVGs";
            } else {
                figureSVGPath = assetPath + "/figureSVGs";
            }
            File figureSVGDirectory = new File(figureSVGPath);
            System.out.println("assetPath in openSVGDocuments: " + figureSVGPath);
            File[] listOfFiles = figureSVGDirectory.listFiles();
            System.out.println("isDirectory: " + figureSVGDirectory.isDirectory());
            System.out.println("number of things found inside: " + listOfFiles.length);
            Map<Integer, org.w3c.dom.Document> pageToSVGDocument = new HashMap<Integer, org.w3c.dom.Document>();

            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

            for (int i = 0; i < listOfFiles.length; i++) {
                File file = listOfFiles[i];
                String fileName = file.getName();
                System.out.println("found: " + fileName);
                if (file.isFile() && FilenameUtils.getExtension(fileName).equals("svg")) {
                    Pattern pattern = Pattern.compile("image-(\\d+)");
                    Matcher matcher = pattern.matcher(fileName);
                    matcher.find();
                    Integer pageNumber = Integer.parseInt(matcher.group(1));

                    org.w3c.dom.Document document = documentBuilder.parse(file);

                    pageToSVGDocument.put(pageNumber - 1, document);
                }
            }
            return pageToSVGDocument;
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public void addImageToSVG(Map<Integer, org.w3c.dom.Document> pageToSVGDocument, Block block) {
        String fileName = FilenameUtils.getBaseName(block.getText().split(" ")[1]);
        System.out.println(fileName);
        File imageFile = new File(assetPath + "/" + fileName + ".png");
        try {
            BufferedImage bufferedImage = ImageIO.read(imageFile);
            String imageString = ImageUtils.encodeToString(bufferedImage, "png");
            org.w3c.dom.Document document = pageToSVGDocument.get(block.getPage());
            if (document != null) {
                Element rootElement = (Element) document.getFirstChild();
                Element imageElement = document.createElement("image");
                imageElement.setAttribute("width", String.valueOf(block.getWidth()));
                imageElement.setAttribute("height", String.valueOf(block.getHeight()));
                imageElement.setAttribute("x", String.valueOf(block.getX()));
                imageElement.setAttribute("y", String.valueOf(block.getY()));
                imageElement.setAttribute("xlink:href", "data:image/png;base64," + imageString);
                rootElement.insertBefore(imageElement, rootElement.getFirstChild());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
