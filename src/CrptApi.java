import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CrptApi {
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;
    private final int requestLimit;
    private final TimeUnit timeUnit;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.requestLimit = requestLimit;
        this.timeUnit = timeUnit;
        this.semaphore = new Semaphore(requestLimit);
        this.scheduler = Executors.newScheduledThreadPool(1);

        // Сбросить разрешения семафора через указанный интервал времени
        long intervals = timeUnit.toMillis(1);
        this.scheduler.scheduleAtFixedRate(() -> semaphore.release(requestLimit - semaphore.availablePermits()),
                intervals, intervals, TimeUnit.MILLISECONDS);
    }

    public void createDocument(Document document, String signature) throws InterruptedException, IOException {
        semaphore.acquire();

        try {
            // Сериализация документа в JSON
            String requestBody = objectMapper.writeValueAsString(document);

            // Создание HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + signature)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Failed to create document: " + response.body());
            }
            System.out.println("Document created successfully: " + response.body());
        } finally {
            semaphore.release();
        }
    }

    public static class Document {
        public String description;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public Product[] products;
        public String reg_date;
        public String reg_number;

        public static class Product {
            public String certificate_document;
            public String certificate_document_date;
            public String certificate_document_number;
            public String owner_inn;
            public String producer_inn;
            public String production_date;
            public String tnved_code;
            public String uit_code;
            public String uitu_code;
        }
    }

    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.MINUTES, 10);

        Document.Product product = new Document.Product();
        product.certificate_document = "cert_doc";
        product.certificate_document_date = "2020-01-23";
        product.certificate_document_number = "cert_num";
        product.owner_inn = "owner_inn";
        product.producer_inn = "producer_inn";
        product.production_date = "2020-01-23";
        product.tnved_code = "tnved_code";
        product.uit_code = "uit_code";
        product.uitu_code = "uitu_code";

        Document document = new Document();
        document.description = "Sample Description";
        document.doc_id = "doc_id";
        document.doc_status = "doc_status";
        document.doc_type = "LP_INTRODUCE_GOODS";
        document.importRequest = true;
        document.owner_inn = "owner_inn";
        document.participant_inn = "participant_inn";
        document.producer_inn = "producer_inn";
        document.production_date = "2020-01-23";
        document.production_type = "production_type";
        document.products = new Document.Product[]{product};
        document.reg_date = "2020-01-23";
        document.reg_number = "reg_number";

        try {
            api.createDocument(document, "your_signature_token_here");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}