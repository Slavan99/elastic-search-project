package com.griddynamics.productindexer.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.IndicesClient;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;

@Component
@Slf4j
public class ProductIndexRepositoryImpl implements ProductIndexRepository {


    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_ALLOWED_INDICES_NUMBER = 3;

    @Autowired
    private RestHighLevelClient esClient;

    @Value("${com.griddynamics.product.indexer.index}")
    private String aliasName;


    // Mappings, settings and bulk data files
    @Value("${com.griddynamics.product.indexer.files.settings:classpath:elastic/productindex/settings.json}")
    private Resource productsSettingsFile;
    @Value("${com.griddynamics.product.indexer.files.mappings:classpath:elastic/productindex/mappings.json}")
    private Resource productsMappingsFile;
    @Value("${com.griddynamics.product.indexer.files.bulkData:classpath:elastic/productindex/task_8_data.json}")
    private Resource productsBulkInsertDataFile;


    @Override
    public void recreateIndex() {

        String indexNameWithDateTime = this.aliasName + "_" + getCurrentDateTime();
        String settings = getStrFromResource(productsSettingsFile);
        String mappings = getStrFromResource(productsMappingsFile);

        createIndex(indexNameWithDateTime, settings, mappings);

        processBulkInsertDataFromJsonArray(productsBulkInsertDataFile, indexNameWithDateTime);

        IndicesAliasesRequest request = new IndicesAliasesRequest();
        AliasActions aliasAction =
                new AliasActions(AliasActions.Type.ADD)
                        .index(indexNameWithDateTime)
                        .alias(aliasName);
        request.addAliasAction(aliasAction);
        try {
            removeAliasFromRemainingIndices();
            esClient.indices().updateAliases(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
        }

        deleteAllExceptLastThreeIndices();
    }

    private void removeAliasFromRemainingIndices() throws IOException {
        IndicesAliasesRequest request = new IndicesAliasesRequest();
        Set<String> indicesFromAliasName = getIndicesFromAliasName(aliasName);
        if (indicesFromAliasName.size() > 0) {
            indicesFromAliasName.forEach(index -> {
                        AliasActions aliasAction =
                                new AliasActions(AliasActions.Type.REMOVE)
                                        .index(index)
                                        .alias(aliasName);
                        request.addAliasAction(aliasAction);
                    }
            );
            esClient.indices().updateAliases(request, RequestOptions.DEFAULT);
        }
    }

    private void deleteAllExceptLastThreeIndices() {
        try {
            Set<String> indicesFromAliasName = getIndicesFromPattern(aliasName + "_*");
            List<String> collect = new ArrayList<>(indicesFromAliasName);
            collect.sort(Comparator.comparing(String::toString));
            if (collect.size() > MAX_ALLOWED_INDICES_NUMBER) {
                List<String> indicesSorted = collect.subList(0, collect.size() - MAX_ALLOWED_INDICES_NUMBER);
                indicesSorted.forEach(indexFromSet -> {
                    DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indexFromSet);

                    try {
                        esClient.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getCurrentDateTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        return dateFormat.format(new Date());
    }

    private Set<String> getIndicesFromAliasName(String aliasName) throws IOException {
        IndicesClient iac = esClient.indices();

        Map<String, Set<AliasMetaData>> map = iac.getAlias(new GetAliasesRequest(aliasName),
                RequestOptions.DEFAULT).getAliases();

        return map.keySet();
    }

    private Set<String> getIndicesFromPattern(String pattern) throws IOException {
        IndicesClient iac = esClient.indices();

        GetIndexRequest getIndexRequest = new GetIndexRequest(pattern);
        return Arrays.stream(iac.get(getIndexRequest, RequestOptions.DEFAULT).getIndices()).collect(Collectors.toSet());

    }

    private void createIndex(String indexName, String settings, String mappings) {
        CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName)
                .mapping(mappings, XContentType.JSON)
                .settings(settings, XContentType.JSON);

        CreateIndexResponse createIndexResponse;
        try {
            createIndexResponse = esClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
        } catch (IOException ex) {
            throw new RuntimeException("An error occurred during creating ES index.", ex);
        }

        if (!createIndexResponse.isAcknowledged()) {
            throw new RuntimeException("Creating index not acknowledged for indexName: " + indexName);
        } else {
            log.info("Index {} has been created.", indexName);
        }
    }

    private static String getStrFromResource(Resource resource) {
        try {
            if (!resource.exists()) {
                throw new IllegalArgumentException("File not found: " + resource.getFilename());
            }
            return Resources.toString(resource.getURL(), Charsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Can not read resource file: " + resource.getFilename(), ex);
        }
    }

    private void processBulkInsertDataFromJsonArray(Resource bulkInsertDataFile, String indexNameWithDateTime) {
        int requestCnt = 0;
        try {
            BulkRequest bulkRequest = new BulkRequest();
            BufferedReader br = new BufferedReader(new InputStreamReader(bulkInsertDataFile.getInputStream()));
            br.readLine();
            String line;
            while (!(line = br.readLine()).equals("]")) {
                requestCnt++;
                IndexRequest indexRequest;
                if (line.charAt(line.length() - 1) == ',') {
                    indexRequest = createIndexRequestFromJson(StringUtils.chop(line), indexNameWithDateTime);
                } else {
                    indexRequest = createIndexRequestFromJson(line, indexNameWithDateTime);
                }
                if (indexRequest != null) {
                    bulkRequest.add(indexRequest);
                }
            }

            BulkResponse bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
            if (bulkResponse.getItems().length != requestCnt) {
                log.warn("Only {} out of {} requests have been processed in a bulk request.", bulkResponse.getItems().length, requestCnt);
            } else {
                log.info("{} requests have been processed in a bulk request.", bulkResponse.getItems().length);
            }

            if (bulkResponse.hasFailures()) {
                log.warn("Bulk data processing has failures:\n{}", bulkResponse.buildFailureMessage());
            }
        } catch (IOException ex) {
            log.error("An exception occurred during bulk data processing", ex);
            throw new RuntimeException(ex);
        }
    }

    private IndexRequest createIndexRequestFromJson(String line, String indexNameWithDateTime) {
        ObjectMapper objectMapper = new ObjectMapper();
        DocWriteRequest.OpType opType = DocWriteRequest.OpType.CREATE;
        String esId = null;
        String lineWithoutId = null;
        boolean isOk = true;

        try {
            JsonNode jsonNode = objectMapper.readTree(line);
            esId = jsonNode.get("id").toString();
            ObjectNode objectNode = (ObjectNode) jsonNode;
            objectNode.remove("id");
            lineWithoutId = objectNode.toString();

        } catch (IOException ex) {
            isOk = false;
        }

        if (isOk) {
            return new IndexRequest(indexNameWithDateTime)
                    .id(esId)
                    .opType(opType)
                    .source(lineWithoutId, XContentType.JSON);
        } else {
            return null;
        }
    }

    private void processBulkInsertData(Resource bulkInsertDataFile, String indexNameWithDateTime) {
        int requestCnt = 0;
        try {
            BulkRequest bulkRequest = new BulkRequest();
            BufferedReader br = new BufferedReader(new InputStreamReader(bulkInsertDataFile.getInputStream()));

            while (br.ready()) {
                String line1 = br.readLine(); // action_and_metadata
                if (isNotEmpty(line1) && br.ready()) {
                    requestCnt++;
                    String line2 = br.readLine();
                    IndexRequest indexRequest = createIndexRequestFromBulkData(line1, line2, indexNameWithDateTime);
                    if (indexRequest != null) {
                        bulkRequest.add(indexRequest);
                    }
                }
            }

            BulkResponse bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
            if (bulkResponse.getItems().length != requestCnt) {
                log.warn("Only {} out of {} requests have been processed in a bulk request.", bulkResponse.getItems().length, requestCnt);
            } else {
                log.info("{} requests have been processed in a bulk request.", bulkResponse.getItems().length);
            }

            if (bulkResponse.hasFailures()) {
                log.warn("Bulk data processing has failures:\n{}", bulkResponse.buildFailureMessage());
            }
        } catch (IOException ex) {
            log.error("An exception occurred during bulk data processing", ex);
            throw new RuntimeException(ex);
        }
    }

    private IndexRequest createIndexRequestFromBulkData(String line1, String line2, String indexNameWithDateTime) {
        DocWriteRequest.OpType opType = null;
        String esIndexName = null;
        String esId = null;
        boolean isOk = true;

        try {
            String esOpType = objectMapper.readTree(line1).fieldNames().next();
            opType = DocWriteRequest.OpType.fromString(esOpType);

            JsonNode indexJsonNode = objectMapper.readTree(line1).iterator().next().get("_index");
            esIndexName = (indexJsonNode != null ? indexJsonNode.textValue() : indexNameWithDateTime);

            JsonNode idJsonNode = objectMapper.readTree(line1).iterator().next().get("_id");
            esId = (idJsonNode != null ? idJsonNode.textValue() : null);
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("An exception occurred during parsing action_and_metadata line in the bulk data file:\n{}\nwith a message:\n{}", line1, ex.getMessage());
            isOk = false;
        }

        try {
            objectMapper.readTree(line2);
        } catch (IOException ex) {
            log.warn("An exception occurred during parsing source line in the bulk data file:\n{}\nwith a message:\n{}", line2, ex.getMessage());
            isOk = false;
        }

        if (isOk) {
            return new IndexRequest(esIndexName)
                    .id(esId)
                    .opType(opType)
                    .source(line2, XContentType.JSON);
        } else {
            return null;
        }
    }
}
