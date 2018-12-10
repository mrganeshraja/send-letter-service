package uk.gov.hmcts.reform.sendletter.services.pdf;

import org.apache.http.util.Asserts;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.sendletter.model.in.Document;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Service
public class PdfCreator {

    private final DuplexPreparator duplexPreparator;
    private final IHtmlToPdfConverter converter;

    public PdfCreator(DuplexPreparator duplexPreparator, IHtmlToPdfConverter converter) {
        this.duplexPreparator = duplexPreparator;
        this.converter = converter;
    }

    public byte[] createFromTemplates(List<Document> documents) {
        Asserts.notNull(documents, "documents");

        List<byte[]> docs =
            documents
                .stream()
                .map(this::generatePdf)
                .map(duplexPreparator::prepare)
                .collect(toList());

        return PdfMerger.mergeDocuments(docs);
    }

    public byte[] createFromBase64Pdfs(List<byte[]> base64decodedDocs) {
        Asserts.notNull(base64decodedDocs, "base64decodedDocs");

        List<byte[]> docs = base64decodedDocs
            .stream()
            .map(duplexPreparator::prepare)
            .collect(toList());

        return PdfMerger.mergeDocuments(docs);
    }

    private byte[] generatePdf(Document document) {
        synchronized (PdfCreator.class) {
            return converter.apply(document.template.getBytes(), document.values);
        }
    }
}
