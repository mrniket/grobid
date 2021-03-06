package org.grobid.core.dom;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
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
            List<Element> clipElements = new ArrayList<Element>();

            // group elements within the same clipZone together
            HashMap<String, List<Element>> clipZoneGroups = new HashMap<String, List<Element>>();
            for (int i = 0; i < nodeList.getLength(); i++) {
                Element element = (Element) nodeList.item(i);
                String clipZoneID = "";
                if (element.getNodeName().equals("CLIP")) {
                    clipZoneID = element.getAttribute("idClipZone");
                    clipElements.add(element);
                } else {
                    clipZoneID = element.getAttribute("clipZone");
                }
                if (!clipZoneID.equals("")) {
                    addElementToGroup(clipZoneID, element, clipZoneGroups);
                }
            }

//            writeClipsToWindow(new ArrayList<Element>(clipElements), "before");

            // merge overlapping clip zones together
            Collections.sort(clipElements, new FigureDomParser().new AreaComparator());
            for (int i = 0; i < clipElements.size(); i++) {
                Element element = clipElements.get(i);
                for (int j = i + 1; j < clipElements.size(); j++) {
                    Element element1 = clipElements.get(j);
                    if (intersect(element, element1)) {
                        mergeClipZones(element, element1, clipZoneGroups);
                        clipElements.remove(element1);
                    }
                }
            }

//            writeClipsToWindow(new ArrayList<Element>(clipElements), "after");

            for (Element element : clipElements) {
                int height = (int) Float.parseFloat(element.getAttribute("height"));
                int width = (int) Float.parseFloat(element.getAttribute("width"));
                int x = (int) Float.parseFloat(element.getAttribute("x"));
                int y = (int) Float.parseFloat(element.getAttribute("y"));
                System.out.println("------------------------------------------------");
                System.out.println("x: " + x + "\ty: " + y + "\theight: " + height + "\twidth: " + width);
                System.out.println("------------------------------------------------\n");

            }

            outerloop:
            for (String key: ((HashMap<String, List<Element>>)clipZoneGroups.clone()).keySet()) {
                for (Element element : clipZoneGroups.get(key)) {
                    if (element.getNodeName().equals("GROUP")) {
                        continue outerloop;
                    }
                }
                clipZoneGroups.remove(key);
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

    private static void writeClipsToWindow(List<Element> clipZones, String windowName) {
        JFrame window = new JFrame();
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setBounds(50, 50, 1000, 600);
        window.setName(windowName);
        window.getContentPane().add(new FigureDomParser().new MyCanvas(clipZones));
        window.setVisible(true);
    }

    private static void mergeClipZones(Element element, Element element1, HashMap<String, List<Element>> clipZoneGroups) {
        String clipZoneToRemove = element1.getAttribute("idClipZone");
        List<Element> elementsToMerge = clipZoneGroups.get(clipZoneToRemove);
        element1.setAttribute("idClipZone", element.getAttribute("idClipZone"));
        clipZoneGroups.get(element.getAttribute("idClipZone")).addAll(elementsToMerge);
        clipZoneGroups.remove(clipZoneToRemove);
    }

    private static boolean intersect(Element element1, Element element2) {
        int height = (int) Float.parseFloat(element1.getAttribute("height"));
        int width = (int) Float.parseFloat(element1.getAttribute("width"));
        int x = (int) Float.parseFloat(element1.getAttribute("x"));
        int y = (int) Float.parseFloat(element1.getAttribute("y"));
        Rectangle rectangle1 = new Rectangle(x, y, width, height);
        int height2 = (int) Float.parseFloat(element1.getAttribute("height"));
        int width2 = (int) Float.parseFloat(element1.getAttribute("width"));
        int x2 = (int) Float.parseFloat(element1.getAttribute("x"));
        int y2 = (int) Float.parseFloat(element1.getAttribute("y"));
        Rectangle rectangle2 = new Rectangle(x2, y2, width2, height2);
        return true;//rectangle1.contains(rectangle2) || rectangle1.intersects(rectangle2);
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
    }

    public class MyCanvas extends JComponent {

        List<Element> elements;

        public MyCanvas(List<Element> elements) {
            this.elements = elements;
        }

        public void paint(Graphics g) {
            for (Element element : elements) {
                int height = (int) Float.parseFloat(element.getAttribute("height"));
                int width = (int) Float.parseFloat(element.getAttribute("width"));
                int x = (int) Float.parseFloat(element.getAttribute("x"));
                int y = (int) Float.parseFloat(element.getAttribute("y"));

                g.drawRect(x, y, width, height);
            }
        }

    }


    public class AreaComparator implements Comparator<Element> {
        @Override
        public int compare(Element e1, Element e2) {
            Float height = Float.parseFloat(e1.getAttribute("height"));
            Float width = Float.parseFloat(e1.getAttribute("width"));
            Float height2 = Float.parseFloat(e1.getAttribute("height"));
            Float width2 = Float.parseFloat(e1.getAttribute("width"));
            return (new Float(height*width)).compareTo(new Float(height2*width2));
        }
    }
}
