package dev.grzybalson.network;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class NatsDocumentUtil {

    private NatsDocumentUtil() {
    }

    public static Document asDocument(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Document document) {
            return document;
        }
        if (value instanceof Map<?, ?> map) {
            Document document = new Document();
            map.forEach((key, entryValue) -> document.append(String.valueOf(key), entryValue));
            return document;
        }
        return null;
    }

    public static List<Document> asDocumentList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Document> documents = new ArrayList<>(rawList.size());
        for (Object entry : rawList) {
            Document document = asDocument(entry);
            if (document != null) {
                documents.add(document);
            }
        }
        return documents;
    }
}
