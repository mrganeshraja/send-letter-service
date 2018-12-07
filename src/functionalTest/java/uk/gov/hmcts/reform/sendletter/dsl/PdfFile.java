package uk.gov.hmcts.reform.sendletter.dsl;

class PdfFile {

    final String name;

    final byte[] content;

    PdfFile(String name, byte[] content) {
        this.name = name;
        this.content = content;
    }

    static PdfFile empty() {
        return new PdfFile(null, new byte[0]);
    }
}
