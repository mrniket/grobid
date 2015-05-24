package org.grobid.core.dom;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Niket on 24/05/15.
 *
 * Parses .vec files and separates each figure/table inside into separate .vec files
 *
 * expects input .vec files to have the format image-{pageNumber}.vec
 *
 */
public class FigureDomParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(FigureDomParser.class);

    public static void separateFigures(File inputFile, String assetPath) {

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputFile);
            Element root = document.getDocumentElement();
            NodeList nodeList = root.getChildNodes();

            // group elements within the same clipZone together
            HashMap<String, List<Element>> clipZoneGroups = new HashMap<String, List<Element>>();
            for (int i = 0; i < nodeList.getLength(); i++) {
                Element element = (Element) nodeList.item(i);
                String clipZoneID = "";
                if (element.getNodeName().equals("CLIP")) {
                    clipZoneID = element.getAttribute("idClipZone");
                } else {
                    clipZoneID = element.getAttribute("clipZone");
                }
                if (!clipZoneID.equals("")) {
                    addElementToGroup(clipZoneID, element, clipZoneGroups);
                }
            }

            // remove all clipZones that only have one element
            for (String key: ((HashMap<String, List<Element>>)clipZoneGroups.clone()).keySet()) {
                if (clipZoneGroups.get(key).size() == 1) {
                    clipZoneGroups.remove(key);
                }
            }

            // separate the clipZones into their own Documents
            separateClipZones(clipZoneGroups, builder, assetPath);

            // convert the .vec files into SVG
            convertVecsToSVGs(assetPath);


        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void convertVecsToSVGs(String assetPath) {

        // get all files
        File folder = new File(assetPath + "/figureVecs");
        File[] listOfFiles = folder.listFiles();

        for (File file : listOfFiles) {

            String[] cmd = {
                    "python",
                    "/Users/Niket/Downloads/vec2svg-2.py",
                    "-i",
                    file.getAbsolutePath(),
                    "-o",
                    assetPath + "/figureSVGs/" + FilenameUtils.removeExtension(file.getName()) + ".svg"
            };
            try {
                Process process = Runtime.getRuntime().exec(cmd);

                BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String s;
                while ((s = stdInput.readLine()) != null) {
                    LOGGER.debug(s);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void separateClipZones(HashMap<String, List<Element>> clipZoneGroups, DocumentBuilder documentBuilder, String assetPath) {
        try {
            int figureNo = 1;
            List<Document> documents = new ArrayList<Document>();
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();

            for (String key: clipZoneGroups.keySet()) {
                Document document = documentBuilder.newDocument();
                Element rootElement = document.createElement("VECTORIALIMAGES");
                document.appendChild(rootElement);
                for (Element element : clipZoneGroups.get(key)) {
                    Element newElement = (Element) document.importNode(element, true);
                    rootElement.appendChild(newElement);
                }

                //get page no from the clipZoneID
                Pattern pattern = Pattern.compile("p(\\d+)");
                Matcher matcher = pattern.matcher(key);
                matcher.find();
                int pageNo = Integer.parseInt(matcher.group(1));


                // save Document
                DOMSource source = new DOMSource(document);
                StreamResult result = new StreamResult(new File(assetPath + "/figureVecs/page" + pageNo + "Figure" + figureNo + ".vec"));
                transformer.transform(source, result);
                figureNo++;
            }
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
        }
    }

    public static void addElementToGroup(String key, Element element, HashMap<String, List<Element>> occurrences) {
        if (occurrences.containsKey(key)) {
            List<Element> elements = occurrences.get(key);
            elements.add(element);
        } else {
            List<Element> elements = new ArrayList<Element>();
            elements.add(element);
            occurrences.put(key, elements);
        }
//        if (occurrences.containsKey(key)) {
//            occurrences.replace(key, occurrences.get(key) + 1);
//        } else {
//            occurrences.put(key, 1);
//        }
    }

}
