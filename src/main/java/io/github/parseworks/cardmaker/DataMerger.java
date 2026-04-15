package io.github.parseworks.cardmaker;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DataMerger {

    public static class CsvResult {
        public List<String> headers;
        public List<Map<String, String>> records;

        public CsvResult(List<String> headers, List<Map<String, String>> records) {
            this.headers = headers;
            this.records = records;
        }
    }

    public CsvResult loadCsv(String filePath) throws IOException, CsvException {
        if (filePath.toLowerCase().endsWith(".ods")) {
            return loadOds(filePath);
        }
        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            List<String[]> lines = reader.readAll();
            if (lines.isEmpty()) return new CsvResult(new ArrayList<>(), new ArrayList<>());

            List<String> headers = List.of(lines.get(0));
            List<Map<String, String>> records = new ArrayList<>();

            for (int i = 1; i < lines.size(); i++) {
                String[] values = lines.get(i);
                Map<String, String> record = new HashMap<>();
                for (int j = 0; j < headers.size() && j < values.length; j++) {
                    record.put(headers.get(j), values[j]);
                }
                records.add(record);
            }
            return new CsvResult(headers, records);
        }
    }

    /**
     * Minimal dependency-free ODS parser.
     * ODS is a ZIP containing content.xml with table:table-row and table:table-cell elements.
     */
    public CsvResult loadOds(String filePath) throws IOException {
        try (ZipFile zipFile = new ZipFile(filePath)) {
            ZipEntry entry = zipFile.getEntry("content.xml");
            if (entry == null) throw new IOException("Not a valid ODS file: content.xml missing");

            try (InputStream is = zipFile.getInputStream(entry)) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(is);

                NodeList rowNodes = doc.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:table:1.0", "table-row");
                List<List<String>> allRows = new ArrayList<>();

                for (int i = 0; i < rowNodes.getLength(); i++) {
                    Node rowNode = rowNodes.item(i);
                    List<String> rowValues = new ArrayList<>();
                    NodeList cellNodes = rowNode.getChildNodes();
                    for (int j = 0; j < cellNodes.getLength(); j++) {
                        Node cellNode = cellNodes.item(j);
                        if ("table-cell".equals(cellNode.getLocalName())) {
                            String value = getCellTextValue(cellNode);
                            rowValues.add(value);

                            // Handle table:number-columns-repeated
                            Node repeatedAttr = cellNode.getAttributes().getNamedItemNS("urn:oasis:names:tc:opendocument:xmlns:table:1.0", "number-columns-repeated");
                            if (repeatedAttr != null) {
                                int repeatedCount = Integer.parseInt(repeatedAttr.getNodeValue());
                                // We only repeat if it's a reasonable number to avoid OOM from huge empty sheets
                                for (int k = 1; k < repeatedCount && k < 1000; k++) {
                                    rowValues.add(value);
                                }
                            }
                        }
                    }
                    if (!rowValues.stream().allMatch(String::isEmpty)) {
                        allRows.add(rowValues);
                    }
                }

                if (allRows.isEmpty()) return new CsvResult(new ArrayList<>(), new ArrayList<>());

                List<String> headers = allRows.get(0);
                List<Map<String, String>> records = new ArrayList<>();
                for (int i = 1; i < allRows.size(); i++) {
                    List<String> row = allRows.get(i);
                    Map<String, String> record = new HashMap<>();
                    for (int j = 0; j < headers.size() && j < row.size(); j++) {
                        record.put(headers.get(j), row.get(j));
                    }
                    records.add(record);
                }
                return new CsvResult(headers, records);
            } catch (Exception e) {
                throw new IOException("Failed to parse ODS content.xml: " + e.getMessage(), e);
            }
        }
    }

    private String getCellTextValue(Node cellNode) {
        StringBuilder sb = new StringBuilder();
        NodeList textNodes = cellNode.getChildNodes();
        for (int i = 0; i < textNodes.getLength(); i++) {
            Node node = textNodes.item(i);
            if ("p".equals(node.getLocalName())) {
                sb.append(node.getTextContent());
            }
        }
        return sb.toString();
    }

    public List<Map<String, String>> loadCsvData(String filePath) throws IOException, CsvException {
        return loadCsv(filePath).records;
    }

    public String merge(String template, Map<String, String> record) {
        if (template == null) return null;
        String result = template;
        for (Map.Entry<String, String> entry : record.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    public boolean evaluateCondition(String condition, Map<String, String> record) {
        if (condition == null || condition.trim().isEmpty()) return true;
        if (record == null) return false;

        String mergedCondition = merge(condition, record).trim();
        
        // Simple equality: "val1 == val2"
        if (mergedCondition.contains("==")) {
            String[] parts = mergedCondition.split("==");
            if (parts.length == 2) {
                return parts[0].trim().equals(parts[1].trim());
            }
        }
        
        // Simple inequality: "val1 != val2"
        if (mergedCondition.contains("!=")) {
            String[] parts = mergedCondition.split("!=");
            if (parts.length == 2) {
                return !parts[0].trim().equals(parts[1].trim());
            }
        }

        // Check if merge actually did something or if the tag remains
        if (mergedCondition.startsWith("{{") && mergedCondition.endsWith("}}")) {
            return false;
        }

        // Just check if not empty if no operator
        return !mergedCondition.isEmpty();
    }
}
