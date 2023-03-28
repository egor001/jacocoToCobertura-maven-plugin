package ru.siblion.lab;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.JDOMParseException;
import org.jdom2.input.SAXBuilder;
import ru.siblion.lab.exceptions.ThrowingConsumer;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static java.lang.Math.round;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Converter {

    public static void convert(Path source, Path destination, List<String> pathsToProject) throws IOException, JDOMException, XMLStreamException {
        SAXBuilder saxBuilder = new SAXBuilder();
        saxBuilder.setFeature("http://xml.org/sax/features/validation", false);
        saxBuilder.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        saxBuilder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        Document doc;

        try {
            if (Files.exists(destination)) {
                Files.delete(destination);
            }

            if (!Files.exists(destination.getParent())) {
                Files.createDirectories(destination.getParent());
            }

            doc = saxBuilder.build(source.toString());
            convertHeadOfFile(destination, doc, pathsToProject);
            sendCoveragePercentage(doc);
        } catch (JDOMParseException e) {
            String errorMessage = String.format("Incorrect file format of <%s> ", source.getFileName());
            log.error(errorMessage, e);
            throw e;
        } catch (IOException e) {
            String errorMessage = String.format("Can't find the source file in the path: <%s> or <%s>. Please, check '/' in the beginning of the path",
                    source, destination);
            log.error(errorMessage, e);
            throw e;
        }
    }

    private static void convertHeadOfFile(Path destination, Document doc, List<String> pathsToProject) throws IOException, XMLStreamException {
        Element rootNode = doc.getRootElement();
        String start = rootNode.getChild("sessioninfo").getAttributeValue("start");
        start = convertNumber(Double.parseDouble(start) / 1000);

        try (FileOutputStream out = new FileOutputStream(String.valueOf(destination))) {
            XMLOutputFactory output = XMLOutputFactory.newInstance();
            XMLStreamWriter writer = output.createXMLStreamWriter(out);

            writer.writeStartDocument("1.0");
            writer.writeStartElement("coverage");
            writer.writeAttribute("timestamp", start);
            addCounters(rootNode, writer);
            writer.writeStartElement("sources");
            writer.writeStartElement("source");
            for (String path : pathsToProject) {
                writer.writeStartElement("source");
                writer.writeCharacters(path);
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeStartElement("packages");

            var groupList = rootNode.getChildren("group").isEmpty() ?
                    List.of(rootNode) : rootNode.getChildren("group");
            groupList.forEach(g -> g.getChildren("package")
                    .forEach(throwingConsumerWrapper(pack -> convertPackage(pack, writer))));

            writer.writeEndElement();
            writer.writeEndElement();
        } catch (IOException e) {
            String errorMessage = String.format("Problem with creating a file with path: <%s> ", destination);
            log.error(errorMessage, e);
            throw e;
        } catch (XMLStreamException e) {
            String errorMessage = String.format("Problem with structure of file with path: <%s> ", destination);
            log.error(errorMessage, e);
            throw e;
        }
    }

    private static void convertPackage(Element pack, XMLStreamWriter writer) throws XMLStreamException {
        String name = pack.getAttributeValue("name").replace("/", ".");

        writer.writeStartElement("package");
        writer.writeAttribute("name", name);
        addCounters(pack, writer);
        writer.writeStartElement("classes");

        pack.getChildren("class").stream().
                forEach(throwingConsumerWrapper(cl -> convertClass(cl, pack, writer)));

        writer.writeEndElement();
        writer.writeEndElement();
    }

    private static void convertClass(Element cl, Element pack, XMLStreamWriter writer) throws XMLStreamException {
        String name = cl.getAttributeValue("name").replace("/", ".");

        writer.writeStartElement("class");
        writer.writeAttribute("name", name);
        writer.writeAttribute("filename", guessFilename(cl.getAttributeValue("name")));
        addCounters(cl, writer);
        writer.writeStartElement("methods");

        List<Element> listOfLines = findLines(pack, cl.getAttributeValue("sourcefilename"));
        List<Element> methodLines;
        List<Element> listOfMethods = cl.getChildren("method");
        for (Element method : listOfMethods) {
            methodLines = methodLines(method, listOfMethods, listOfLines);
            convertMethod(method, writer, methodLines);
        }

        writer.writeEndElement();
        convertLines(listOfLines, writer);
        writer.writeEndElement();
    }

    private static void convertMethod(Element method, XMLStreamWriter writer, List<Element> listOfLines) throws XMLStreamException {
        writer.writeStartElement("method");
        writer.writeAttribute("name", method.getAttributeValue("name"));
        writer.writeAttribute("signature", method.getAttributeValue("desc"));
        addCounters(method, writer);
        convertLines(listOfLines, writer);
        writer.writeEndElement();
    }

    private static List<Element> methodLines(Element method, List<Element> listOfMethods, List<Element> listOfLines) {
        Integer startLine = Integer.valueOf(method.getAttributeValue("line", "0"));
        List<Integer> larger = new ArrayList<>();
        List<Element> result;
        Integer endLine;

        listOfMethods.stream()
                .filter(m -> Integer.parseInt(m.getAttributeValue("line", "0")) > startLine)
                .forEach(m -> larger.add(Integer.valueOf(m.getAttributeValue("line", "0"))));

        if (larger.isEmpty()) {
            endLine = 99999999;
        } else {
            endLine = Collections.min(larger);
        }

        result = listOfLines.stream()
                .filter(line -> startLine <= Integer.parseInt(line.getAttributeValue("nr")) &&
                        Integer.parseInt(line.getAttributeValue("nr")) < endLine)
                .toList();

        return result;
    }

    private static List<Element> findLines(Element pack, String filename) {
        List<Element> listOfLines = new ArrayList<>();
        List<Element> listOfSourceFiles = pack.getChildren("sourcefile");

        listOfSourceFiles.stream()
                .filter(i -> i.getAttributeValue("name").equals(filename))
                .forEach(i -> listOfLines.addAll(i.getChildren("line")));


        return listOfLines;
    }

    private static void convertLines(List<Element> listOfLines, XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("lines");

        for (Element line : listOfLines) {
            String mb = line.getAttributeValue("mb");
            String cb = line.getAttributeValue("cb");
            String ci = line.getAttributeValue("ci");

            writer.writeStartElement("line");
            writer.writeAttribute("number", line.getAttributeValue("nr"));
            if (Integer.parseInt(ci) > 0) {
                writer.writeAttribute("hits", "1");
            } else {
                writer.writeAttribute("hits", "0");
            }
            if ((Integer.parseInt(mb) + Integer.parseInt(cb)) > 0) {
                String percentage = 100 * (Double.parseDouble(cb) / (Double.parseDouble(cb) + Double.parseDouble(mb))) + "%";

                writer.writeAttribute("branch", "true");
                writer.writeAttribute("condition-coverage", percentage + " (" + cb + "/" +
                        (Double.parseDouble(cb) + Double.parseDouble(mb)) + ")");
                writer.writeStartElement("conditions");
                writer.writeStartElement("condition");
                writer.writeAttribute("number", "0");
                writer.writeAttribute("type", "jump");
                writer.writeAttribute("coverage", percentage);
                writer.writeEndElement();
                writer.writeEndElement();
            } else {
                writer.writeAttribute("branch", "false");
            }
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    private static String guessFilename(String pathToClass) {
        return pathToClass + ".java";
    }

    private static void addCounters(Element element, XMLStreamWriter writer) throws XMLStreamException {
        writer.writeAttribute("line-rate", counter(element, "LINE"));
        writer.writeAttribute("branch-rate", counter(element, "BRANCH"));
        writer.writeAttribute("complexity", counter(element, "COMPLEXITY"));
    }

    private static String counter(Element element, String type) {
        Element typeOfAttribute;
        List<Element> listOfElements = element.getChildren("counter");

        typeOfAttribute = listOfElements.stream()
                .filter(i -> i.getAttributeValue("type").equals(type))
                .findFirst()
                .orElse(null);

        if (typeOfAttribute == null) {
            return "0.0";
        } else if (type.equals("LINE") || type.equals("BRANCH")) {
            return fraction(typeOfAttribute.getAttributeValue("covered"), typeOfAttribute.getAttributeValue("missed"));
        } else {
            return sum(typeOfAttribute.getAttributeValue("covered"), typeOfAttribute.getAttributeValue("missed"));
        }
    }

    private static String fraction(String covered, String missed) {
        return convertNumber(Double.parseDouble(covered) / (Double.parseDouble(covered) + Double.parseDouble(missed)));
    }

    private static String sum(String covered, String missed) {
        return String.valueOf(Double.parseDouble(covered) + Double.parseDouble(missed));
    }

    private static String convertNumber(Double number) {
        if (number % 1 == 0) {
            return String.format("%.1f", number).replace(",", ".");
        } else {
            DecimalFormat decimalFormat = new DecimalFormat("#.################");
            return decimalFormat.format(number).replace(",", ".");
        }
    }

    private static void sendCoveragePercentage(Document doc) {
        Element counter = doc.getRootElement().getChildren("counter").stream()
                .filter(i -> i.getAttributeValue("type").equals("INSTRUCTION"))
                .findFirst()
                .orElse(null);
        Double missed = Double.valueOf(counter.getAttributeValue("missed"));
        Double covered = Double.valueOf(counter.getAttributeValue("covered"));
        String result = String.valueOf(round(covered / (missed + covered) * 100));

        log.info(String.format("Total coverage %s", result) + "%");
    }

    static <T> Consumer<T> throwingConsumerWrapper(ThrowingConsumer<T, Exception> throwingConsumer) {
        return i -> {
            try {
                throwingConsumer.accept(i);
            } catch (Exception e) {
                String errorMessage = "Problem with file structure, check XML attributes in jacoco.xml";
                log.error(errorMessage);
                throw new RuntimeException(errorMessage, e);
            }
        };
    }
}
