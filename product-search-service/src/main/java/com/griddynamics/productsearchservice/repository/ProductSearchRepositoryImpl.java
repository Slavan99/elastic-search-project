package com.griddynamics.productsearchservice.repository;

import com.griddynamics.productsearchservice.model.ProductServiceRequest;
import com.griddynamics.productsearchservice.model.ProductServiceResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeRequest;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNested;
import org.elasticsearch.search.aggregations.bucket.range.ParsedRange;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregator;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.ScoreSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ProductSearchRepositoryImpl implements ProductSearchRepository {

    @Autowired
    private RestHighLevelClient esClient;

    @Value("${com.griddynamics.product.search.service.index}")
    private String aliasName;

    private static final String SKUS = "skus";
    private static final String SKUS_SIZE = SKUS + ".size";
    private static final String SKUS_SIZE_TEXT = SKUS_SIZE + ".text";
    private static final String SKUS_SIZE_AGG = "skusSizeAgg";
    private static final String SKUS_COLOR = SKUS + ".color";
    private static final String SKUS_COLOR_TEXT = SKUS_COLOR + ".text";
    private static final String SKUS_COLOR_AGG = "skusColorAgg";
    private static final String PRICE_AGG = "priceAgg";
    private static final String PRICE_FIELD = "price";
    private static final String ID_FIELD = "_id";
    private static final String NAME_FIELD = "name";
    private static final String BRAND_FIELD = "brand";
    private static final String BRAND_TEXT_FIELD = "brand.text";
    private static final String NAME_SHINGLES_FIELD = "name.shingles";
    private static final String BRAND_SHINGLES_FIELD = "brand.shingles";


    @Override
    public ProductServiceResponse getAllProducts(ProductServiceRequest request) {
        QueryBuilder mainQuery = QueryBuilders.matchAllQuery();
        return getProducts(mainQuery, request);
    }

    @Override
    public ProductServiceResponse getProductsByQuery(ProductServiceRequest request) {
        QueryBuilder mainQuery = getQueryByText(request.getQueryText());
        return getProducts(mainQuery, request);
    }

    private ProductServiceResponse getProducts(QueryBuilder mainQuery, ProductServiceRequest request) {

        int searchOffset = request.getSize() * request.getPage();

        // Create search request
        SearchSourceBuilder ssb = new SearchSourceBuilder()
                .query(mainQuery)
                .size(request.getSize())
                .from(searchOffset);

        // Sorting
        ssb.sort(new ScoreSortBuilder().order(SortOrder.DESC)); // sort by _score DESC
        ssb.sort(new FieldSortBuilder(ID_FIELD).order(SortOrder.DESC)); // tie breaker: sort by _id DESC
        // Aggregation
        List<AggregationBuilder> aggs = createAggs();
        aggs.forEach(ssb::aggregation);


        // Search in ES
        SearchRequest searchRequest = new SearchRequest(aliasName).source(ssb);
        try {
            SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
            // Build service response
            return getServiceResponse(searchResponse);
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
            return new ProductServiceResponse();
        }
    }

    private List<AggregationBuilder> createAggs() {
        List<AggregationBuilder> result = new ArrayList<>();

        AggregationBuilder brandAgg = AggregationBuilders
                .terms(BRAND_FIELD)
                .field(BRAND_FIELD)
                .order(List.of(BucketOrder.count(false), BucketOrder.key(true)));

        RangeAggregationBuilder priceAgg = AggregationBuilders
                .range(PRICE_AGG)
                .field(PRICE_FIELD)
                .keyed(true)
                .addRange(new RangeAggregator.Range("Cheap", 0.0, 99.99))
                .addRange("Average", 100, 499.99)
                .addRange(new RangeAggregator.Range("Expensive", 500.0, null));


        NestedAggregationBuilder skusColorAgg = AggregationBuilders
                .nested(SKUS_COLOR_AGG, SKUS)
                .subAggregation(AggregationBuilders
                        .terms(SKUS_COLOR)
                        .field(SKUS_COLOR)
                        .subAggregation(AggregationBuilders
                                .reverseNested("reverse_color"))
                        .order(List.of(
                                BucketOrder.aggregation("reverse_color", false),
                                BucketOrder.key(true))));

        NestedAggregationBuilder skusSizeAgg = AggregationBuilders
                .nested(SKUS_SIZE_AGG, SKUS)
                .subAggregation(AggregationBuilders
                        .terms(SKUS_SIZE)
                        .field(SKUS_SIZE)
                        .subAggregation(AggregationBuilders
                                .reverseNested("reverse_size"))
                        .order(List.of(
                                BucketOrder.aggregation("reverse_size", false),
                                BucketOrder.key(true))));

        result.add(brandAgg);
        result.add(priceAgg);
        result.add(skusColorAgg);
        result.add(skusSizeAgg);

        return result;
    }

    private ProductServiceResponse getServiceResponse(SearchResponse searchResponse) {
        ProductServiceResponse response = new ProductServiceResponse();

        // Total hits
        response.setTotalHits(searchResponse.getHits().getTotalHits().value);

        // Documents
        List<Map<String, Object>> products = Arrays.stream(searchResponse.getHits().getHits())
                .map(hit -> {
                    Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                    sourceAsMap.put("id", (hit.getId().substring(1, hit.getId().length() - 1)));
                    return sourceAsMap;
                })
                .collect(Collectors.toList());
        response.setProducts(products);

        if (!products.isEmpty()) {

            List<Map<String, Object>> brandAgg = new ArrayList<>();
            Terms brandTerms = searchResponse.getAggregations().get(BRAND_FIELD);
            brandTerms.getBuckets().forEach(bucket -> {
                        String key = bucket.getKeyAsString();
                        Long docCount = bucket.getDocCount();
                        Map<String, Object> bucketValues = new LinkedHashMap<>();
                        bucketValues.put("value", key);
                        bucketValues.put("count", docCount);

                        brandAgg.add(bucketValues);
                    }
            );

            response.getFacets().put("brand", brandAgg);

            List<Map<String, Object>> priceAgg = new ArrayList<>();

            ParsedRange parsedRange = searchResponse.getAggregations().get(PRICE_AGG);
            parsedRange.getBuckets().stream()
                    .sorted(Comparator.comparingDouble(bucket -> (Double) bucket.getFrom()))
                    .forEach(bucket -> {
                        String key = bucket.getKeyAsString();
                        Long docCount = bucket.getDocCount();
                        Map<String, Object> bucketValues = new LinkedHashMap<>();
                        bucketValues.put("value", key);
                        bucketValues.put("count", docCount);


                        priceAgg.add(bucketValues);
                    });

            response.getFacets().put("price", priceAgg);

            ParsedNested skusSizeOuter = searchResponse.getAggregations().get(SKUS_SIZE_AGG);
            ParsedStringTerms skusSizeNested = skusSizeOuter.getAggregations().get(SKUS_SIZE);
            List<Map<String, Object>> skusSizeAgg = new ArrayList<>();
            skusSizeNested.getBuckets().forEach(bucket -> {
                ReverseNested reverse_size = bucket.getAggregations().get("reverse_size");
                String key = bucket.getKeyAsString();
                long count = reverse_size.getDocCount();
                Map<String, Object> bucketValues = new LinkedHashMap<>();
                bucketValues.put("value", key);
                bucketValues.put("count", count);
                skusSizeAgg.add(bucketValues);

            });

            response.getFacets().put("skus.size", skusSizeAgg);

            ParsedNested skusColorOuter = searchResponse.getAggregations().get(SKUS_COLOR_AGG);
            ParsedStringTerms skusColorNested = skusColorOuter.getAggregations().get(SKUS_COLOR);
            List<Map<String, Object>> skusColorAgg = new ArrayList<>();
            skusColorNested.getBuckets().forEach(bucket -> {
                ReverseNested reverse_color = bucket.getAggregations().get("reverse_color");
                String key = bucket.getKeyAsString();
                long count = reverse_color.getDocCount();
                Map<String, Object> bucketValues = new LinkedHashMap<>();
                bucketValues.put("value", key);
                bucketValues.put("count", count);
                skusColorAgg.add(bucketValues);
            });

            response.getFacets().put("skus.color", skusColorAgg);


        }

        return response;
    }

    private QueryBuilder getQueryByText(String textQuery) {
        BoolQueryBuilder result = QueryBuilders.boolQuery();

        try {

            AnalyzeRequest shingleAnalyzerRequest = new AnalyzeRequest().text(textQuery).index(aliasName).analyzer("shingle_analyzer");
            AnalyzeResponse shingleAnalyzerResponse = esClient.indices().analyze(shingleAnalyzerRequest, RequestOptions.DEFAULT);
            List<String> shingleTokens = shingleAnalyzerResponse.getTokens()
                    .stream()
                    .map(AnalyzeResponse.AnalyzeToken::getTerm)
                    .collect(Collectors.toList());

            AnalyzeRequest request = new AnalyzeRequest().text(textQuery).index(aliasName).analyzer("text_analyzer");
            AnalyzeResponse analyzeResponse = esClient.indices().analyze(request, RequestOptions.DEFAULT);
            List<AnalyzeResponse.AnalyzeToken> tokens = analyzeResponse.getTokens();
            List<String> words = tokens
                    .stream()
                    .map(AnalyzeResponse.AnalyzeToken::getTerm)
                    .collect(Collectors.toList());

            List<QueryBuilder> mainQueryList = new ArrayList<>();

            List<String> sizeList = List.of("xxs", "xs", "s", "m", "l", "xl", "xxl", "xxxl");

            List<String> colorList = List.of(
                    "green", "black", "white", "blue",
                    "yellow", "red", "brown", "orange", "grey");

            BoolQueryBuilder sizeColorMatchResult = QueryBuilders.boolQuery();

            for (String token : words) {

                List<QueryBuilder> wordQueries = new ArrayList<>();

                if (sizeList.contains(token)) {
                    sizeColorMatchResult.must(
                            QueryBuilders.matchQuery(SKUS_SIZE_TEXT, token).boost(2.0f));
                } else if (colorList.contains(token)) {
                    sizeColorMatchResult.must(
                            QueryBuilders.matchQuery(SKUS_COLOR_TEXT, token).boost(3.0f));
                } else {
                    wordQueries.add(QueryBuilders
                            .multiMatchQuery(token, NAME_FIELD, BRAND_TEXT_FIELD)
                            .type(MultiMatchQueryBuilder.Type.CROSS_FIELDS)
                            .operator(Operator.AND));
                }

                mainQueryList.addAll(wordQueries);
            }
            mainQueryList.add(QueryBuilders.nestedQuery(SKUS, sizeColorMatchResult, ScoreMode.Avg));

            mainQueryList.forEach(result::must);
            shingleTokens.forEach(shingleToken -> result.should(QueryBuilders
                    .multiMatchQuery(shingleToken, NAME_SHINGLES_FIELD, BRAND_SHINGLES_FIELD)
                    .type(MultiMatchQueryBuilder.Type.CROSS_FIELDS)
                    .boost(5.0f)));

        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

}
