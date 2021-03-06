package mkl.testarea.pdfbox2.content;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.util.Matrix;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * <a href="https://stackoverflow.com/questions/58475104/filter-out-all-text-above-a-certain-font-size-from-pdf">
 * Filter out all text above a certain font size from PDF
 * </a>
 * <p>
 * This test class tests the {@link PdfContentStreamEditor}.
 * </p>
 * <p>
 * {@link PdfContentStreamEditor} is the base editor class which by default acts as the
 * identity operation (or at least it produces an equivalent content stream).
 * </p>
 * 
 * @author mkl
 */
public class EditPageContent {
    final static File RESULT_FOLDER = new File("target/test-outputs", "content");

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        RESULT_FOLDER.mkdirs();
    }

    /**
     * Test identity editing.
     */
    @Test
    public void testIdentityInput() throws IOException {
        try (   InputStream resource = getClass().getResourceAsStream("input.pdf");
                PDDocument document = Loader.loadPDF(resource)) {
            for (PDPage page : document.getDocumentCatalog().getPages()) {
                PdfContentStreamEditor identity = new PdfContentStreamEditor(document, page);
                identity.processPage(page);
            }
            document.save(new File(RESULT_FOLDER, "input-identity.pdf"));
        }
    }

    /**
     * <a href="http://stackoverflow.com/questions/38498431/how-to-remove-filtered-content-from-a-pdf-with-itext">
     * How to remove filtered content from a PDF with iText
     * </a>
     * <br/>
     * <a href="https://1drv.ms/b/s!AmNST-TRoPSemi2k0UnGFsjQM1Yt">
     * document.pdf
     * </a>
     * <p>
     * This test shows how to remove text filtered by actual font size.
     * </p>
     */
    @Test
    public void testRemoveBigTextDocument() throws IOException {
        try (   InputStream resource = getClass().getResourceAsStream("document.pdf");
                PDDocument document = Loader.loadPDF(resource)) {
            for (PDPage page : document.getDocumentCatalog().getPages()) {
                PdfContentStreamEditor identity = new PdfContentStreamEditor(document, page) {
                    @Override
                    protected void write(ContentStreamWriter contentStreamWriter, Operator operator, List<COSBase> operands) throws IOException {
                        String operatorString = operator.getName();

                        if (TEXT_SHOWING_OPERATORS.contains(operatorString))
                        {
                            float fs = getGraphicsState().getTextState().getFontSize();
                            Matrix matrix = getTextMatrix().multiply(getGraphicsState().getCurrentTransformationMatrix());
                            Point2D.Float transformedFsVector = matrix.transformPoint(0, fs);
                            Point2D.Float transformedOrigin = matrix.transformPoint(0, 0);
                            double transformedFs = transformedFsVector.distance(transformedOrigin);
                            if (transformedFs > 100)
                                return;
                        }

                        super.write(contentStreamWriter, operator, operands);
                    }

                    final List<String> TEXT_SHOWING_OPERATORS = Arrays.asList("Tj", "'", "\"", "TJ");
                };
                identity.processPage(page);
            }
            document.save(new File(RESULT_FOLDER, "document-noBigText.pdf"));
        }
    }

    /**
     * <a href="https://stackoverflow.com/questions/59489717/extract-content-streamimages-text-and-graphics-with-in-a-bbox-and-place-it-b">
     * Extract content stream(Images, Text and graphics) with in a BBOX. And place it back in new PDF without loosing any style?
     * </a>
     * <br/>
     * <a href="https://drive.google.com/file/d/1q8e3475Urg56fo5svuckjlbqxspElQ2s/view?usp=sharing">
     * Cengage1.pdf
     * </a>
     * <p>
     * This test tries to sort text drawing instructions by area.
     * It is a mere proof of concept, though, even less actually,
     * as it creates an invalid marked content structure (multiple
     * marked content sections with the same ID.
     * </p>
     * <p>
     * This allowed, though, to identify a bug in PdfContentStreamEditor -
     * a few OperatorProcessor implementations recursively feed other
     * operators by which they are defined into the stream engine. For
     * the purpose of editing one has to ignore those recursive processing
     * calls which PdfContentStreamEditor did not do yet. This is fixed now.
     * </p>
     */
    @Test
    public void testSortDrawsCengage1() throws IOException {
        try (   InputStream resource = getClass().getResourceAsStream("Cengage1.pdf");
                PDDocument document = Loader.loadPDF(resource)) {
            for (PDPage page : document.getDocumentCatalog().getPages()) {
                PdfContentStreamEditor identity = new PdfContentStreamEditor(document, page) {
                    Rectangle2D[] rectangles = new Rectangle2D[] {new Rectangle2D.Float(67, 567, 135, 85),
                            new Rectangle2D.Float(222, 372, 400, 305), new Rectangle2D.Float(67, 347, 145, 245)};
                    PDStream[] streams = null;
                    OutputStream[] outputStreams = null;
                    ContentStreamWriter[] writers = null;

                    @Override
                    public void processPage(PDPage page) throws IOException {
                        streams = new PDStream[rectangles.length];
                        outputStreams = new OutputStream[rectangles.length];
                        writers = new ContentStreamWriter[rectangles.length];
                        for (int i = 0; i < rectangles.length; i++) {
                            streams[i] = new PDStream(document);
                            outputStreams[i] = streams[i].createOutputStream(COSName.FLATE_DECODE);
                            writers[i] = new ContentStreamWriter(outputStreams[i]);
                            writers[i].writeToken(Operator.getOperator("q"));
                            writers[i].writeTokens(new COSFloat((float)rectangles[i].getMinX()), new COSFloat((float)rectangles[i].getMinY()),
                                    new COSFloat((float)rectangles[i].getWidth()), new COSFloat((float)rectangles[i].getHeight()));
                            writers[i].writeToken(Operator.getOperator("re"));
                            writers[i].writeToken(Operator.getOperator("W"));
                            writers[i].writeToken(Operator.getOperator("n"));
                        }
                        super.processPage(page);
                        List<PDStream> contents = new ArrayList<PDStream>();
                        page.getContentStreams().forEachRemaining(pd -> contents.add(pd));
                        for (int i = 0; i < rectangles.length; i++) {
                            writers[i].writeToken(Operator.getOperator("Q"));
                            outputStreams[i].close();
                            contents.add(streams[i]);
                        }
                        page.setContents(contents);
                        streams = null;
                        outputStreams = null;
                        writers = null;
                    }

                    @Override
                    protected void write(ContentStreamWriter contentStreamWriter, Operator operator, List<COSBase> operands) throws IOException {
                        String operatorString = operator.getName();

                        if (TEXT_SHOWING_OPERATORS.contains(operatorString))
                        {
                            Matrix matrix = getTextMatrix().multiply(getGraphicsState().getCurrentTransformationMatrix());
                            Point2D origin = matrix.transformPoint(0, 0);
                            boolean found = false;
                            for (int i = 0; i < rectangles.length; i++) {
                                if (rectangles[i].contains(origin)) {
                                    found = true;
                                    super.write(writers[i], operator, operands);
                                }
                            }
                            if (!found)
                                super.write(contentStreamWriter, operator, operands);
                            return;
                        }

                        for (int i = 0; i < rectangles.length; i++)
                            super.write(writers[i], operator, operands);
                        super.write(contentStreamWriter, operator, operands);
                    }

                    final List<String> TEXT_SHOWING_OPERATORS = Arrays.asList("Tj", "'", "\"", "TJ");
                };
                identity.processPage(page);
            }
            document.save(new File(RESULT_FOLDER, "Cengage1-sorted-draws.pdf"));
        }
    }

    /**
     * <a href="https://stackoverflow.com/questions/61202822/remove-large-tokens-from-pdf-using-pdfbox-or-equivalent-library">
     * Remove Large Tokens from PDF using PDFBox or equivalent library
     * </a>
     * <br/>
     * <a href="https://drive.google.com/file/d/184waC6PjjDi8yolIZN5R-6vgWGR5SvKl/view?usp=sharing">
     * kommers_annons_elite.pdf
     * </a>
     * <p>
     * This test shows how to remove text filtered by actual font size.
     * </p>
     */
    @Test
    public void testRemoveBigTextKommersAnnonsElite() throws IOException {
        try (   InputStream resource = getClass().getResourceAsStream("kommers_annons_elite.pdf");
                PDDocument document = Loader.loadPDF(resource)) {
            PDPage page = document.getPage(0);
            PdfContentStreamEditor editor = new PdfContentStreamEditor(document, page) {
                @Override
                protected void write(ContentStreamWriter contentStreamWriter, Operator operator, List<COSBase> operands) throws IOException {
                    String operatorString = operator.getName();

                    if (TEXT_SHOWING_OPERATORS.contains(operatorString))
                    {
                        float fs = getGraphicsState().getTextState().getFontSize();
                        Matrix matrix = getTextMatrix().multiply(getGraphicsState().getCurrentTransformationMatrix());
                        Point2D.Float transformedFsVector = matrix.transformPoint(0, fs);
                        Point2D.Float transformedOrigin = matrix.transformPoint(0, 0);
                        double transformedFs = transformedFsVector.distance(transformedOrigin);
                        if (transformedFs > 50)
                            return;
                    }

                    super.write(contentStreamWriter, operator, operands);
                }

                final List<String> TEXT_SHOWING_OPERATORS = Arrays.asList("Tj", "'", "\"", "TJ");
            };
            editor.processPage(page);
            document.save(new File(RESULT_FOLDER, "kommers_annons_elite-noBigText.pdf"));
        }
    }
}
