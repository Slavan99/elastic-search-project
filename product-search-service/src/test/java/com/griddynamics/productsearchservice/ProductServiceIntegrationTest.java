package com.griddynamics.productsearchservice;

import com.griddynamics.productsearchservice.service.ProductSearchService;
import org.hamcrest.collection.IsMapContaining;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.*;

public class ProductServiceIntegrationTest extends BaseTest {

    private final APIClient client = new APIClient();

    @Test
    public void testEmptyResponse() {
        client
                .productRequest()
                .body("{}")
                .post()
                .then()
                .statusCode(200)
                .body("totalHits", is(0));

        client
                .productRequest()
                .body("{\"queryText\" : \"Calvin klein L red ankle skinny jeans\"}")
                .post()
                .then()
                .statusCode(200)
                .body("totalHits", is(0));

        client
                .productRequest()
                .body("{\"queryText\" : \"Calvin klein L blue ankle skinny jeans wrongword\"}")
                .post()
                .then()
                .statusCode(200)
                .body("totalHits", is(0));


    }

    @Test
    public void testHappyPath() {
        client
                .productRequest()
                .body("{\"queryText\":\"Calvin klein L blue ankle skinny jeans\"}")
                .post()
                .then()
                .statusCode(200)
                .body("totalHits", is(1))
                .body("products", hasSize(1))
                .body("products[0].id", is("2"))
                .body("products[0].brand", is("Calvin Klein"))
                .body("products[0].name", is("Women ankle skinny jeans, model 1282"))
                .body("products[0].skus", hasSize(9))
                .body("facets", IsMapContaining.hasKey("price"))
                .body("facets", IsMapContaining.hasKey("brand"))
                .body("facets", IsMapContaining.hasKey("skus.color"))
                .body("facets", IsMapContaining.hasKey("skus.size"))
                .body("facets[\"price\"]", is(not(emptyArray())))
                .body("facets[\"brand\"]", is(not(emptyArray())))
                .body("facets[\"skus.color\"]", is(not(emptyArray())))
                .body("facets[\"skus.size\"]", is(not(emptyArray())));

    }

    @Test
    public void testFacets() {
        client
                .productRequest()
                .body("{\"queryText\":\"jeans\"}")
                .post()
                .then()
                .statusCode(200)
                .body("facets", IsMapContaining.hasKey("price"))
                .body("facets", IsMapContaining.hasKey("brand"))
                .body("facets", IsMapContaining.hasKey("skus.color"))
                .body("facets", IsMapContaining.hasKey("skus.size"))
                .body("facets[\"brand\"]", hasSize(2))
                .body("facets.brand[0].value", is("Calvin Klein"))
                .body("facets.brand[0].count", is(4))
                .body("facets.brand[1].value", is("Levi's"))
                .body("facets.brand[1].count", is(4))
                .body("facets[\"price\"]", hasSize(3))
                .body("facets.price[0].value", is("Cheap"))
                .body("facets.price[0].count", is(2))
                .body("facets.price[1].value", is("Average"))
                .body("facets.price[1].count", is(6))
                .body("facets.price[2].value", is("Expensive"))
                .body("facets.price[2].count", is(0))
                .body("facets[\"skus.color\"]", hasSize(4))
                .body("facets.\"skus.color\"[0].value", is("Blue"))
                .body("facets.\"skus.color\"[0].count", is(8))
                .body("facets.\"skus.color\"[1].value", is("Black"))
                .body("facets.\"skus.color\"[1].count", is(7))
                .body("facets.\"skus.color\"[2].value", is("Red"))
                .body("facets.\"skus.color\"[2].count", is(1))
                .body("facets.\"skus.color\"[3].value", is("White"))
                .body("facets.\"skus.color\"[3].count", is(1))
                .body("facets[\"skus.size\"]", hasSize(6))
                .body("facets.\"skus.size\"[0].value", is("L"))
                .body("facets.\"skus.size\"[0].count", is(8))
                .body("facets.\"skus.size\"[1].value", is("M"))
                .body("facets.\"skus.size\"[1].count", is(8))
                .body("facets.\"skus.size\"[2].value", is("S"))
                .body("facets.\"skus.size\"[2].count", is(6))
                .body("facets.\"skus.size\"[3].value", is("XL"))
                .body("facets.\"skus.size\"[3].count", is(5))
                .body("facets.\"skus.size\"[4].value", is("XXL"))
                .body("facets.\"skus.size\"[4].count", is(3))
                .body("facets.\"skus.size\"[5].value", is("XS"))
                .body("facets.\"skus.size\"[5].count", is(2));

        client
                .productRequest()
                .body("{\"queryText\":\"women ankle blue jeans\"}")
                .post()
                .then()
                .statusCode(200)
                .body("facets", IsMapContaining.hasKey("price"))
                .body("facets", IsMapContaining.hasKey("brand"))
                .body("facets", IsMapContaining.hasKey("skus.color"))
                .body("facets", IsMapContaining.hasKey("skus.size"))
                .body("facets[\"brand\"]", hasSize(2))
                .body("facets.brand[0].value", is("Calvin Klein"))
                .body("facets.brand[0].count", is(2))
                .body("facets.brand[1].value", is("Levi's"))
                .body("facets.brand[1].count", is(1))
                .body("facets[\"price\"]", hasSize(3))
                .body("facets.price[0].value", is("Cheap"))
                .body("facets.price[0].count", is(0))
                .body("facets.price[1].value", is("Average"))
                .body("facets.price[1].count", is(3))
                .body("facets.price[2].value", is("Expensive"))
                .body("facets.price[2].count", is(0))
                .body("facets[\"skus.color\"]", hasSize(4))
                .body("facets.\"skus.color\"[0].value", is("Black"))
                .body("facets.\"skus.color\"[0].count", is(3))
                .body("facets.\"skus.color\"[1].value", is("Blue"))
                .body("facets.\"skus.color\"[1].count", is(3))
                .body("facets.\"skus.color\"[2].value", is("Red"))
                .body("facets.\"skus.color\"[2].count", is(1))
                .body("facets.\"skus.color\"[3].value", is("White"))
                .body("facets.\"skus.color\"[3].count", is(1))
                .body("facets[\"skus.size\"]", hasSize(4))
                .body("facets.\"skus.size\"[0].value", is("L"))
                .body("facets.\"skus.size\"[0].count", is(3))
                .body("facets.\"skus.size\"[1].value", is("M"))
                .body("facets.\"skus.size\"[1].count", is(3))
                .body("facets.\"skus.size\"[2].value", is("S"))
                .body("facets.\"skus.size\"[2].count", is(3))
                .body("facets.\"skus.size\"[3].value", is("XS"))
                .body("facets.\"skus.size\"[3].count", is(1));
    }

    @Test
    public void testBoostSort() {
        client
                .productRequest()
                .body("{\"queryText\":\"jeans\"}")
                .post()
                .then()
                .statusCode(200)
                .body("totalHits", is(8))
                .body("products[0].id", is("8"))
                .body("products[1].id", is("7"))
                .body("products[2].id", is("6"))
                .body("products[3].id", is("5"))
                .body("products[4].id", is("4"))
                .body("products[5].id", is("3"))
                .body("products[6].id", is("2"))
                .body("products[7].id", is("1"));

        client
                .productRequest()
                .body("{\"queryText\":\"blue WOMEN jeans\"}")
                .post()
                .then()
                .statusCode(200)
                .body("totalHits", is(5))
                .body("products[0].id", is("5"))
                .body("products[1].id", is("3"))
                .body("products[2].id", is("6"))
                .body("products[3].id", is("2"))
                .body("products[4].id", is("1"));

        client
                .productRequest()
                .body("{\"queryText\":\"WOMEN blue jeans\"}")
                .post()
                .then()
                .statusCode(200)
                .body("totalHits", is(5))
                .body("products[0].id", is("6"))
                .body("products[1].id", is("5"))
                .body("products[2].id", is("3"))
                .body("products[3].id", is("2"))
                .body("products[4].id", is("1"));

        client
                .productRequest()
                .body("{\"queryText\":\"women ankle blue jeans\"}")
                .post()
                .then()
                .statusCode(200)
                .body("totalHits", is(3))
                .body("products[0].id", is("6"))
                .body("products[1].id", is("2"))
                .body("products[2].id", is("1"));

    }

    @Test
    public void testPagination() {
        client
                .productRequest()
                .body("{\"queryText\":\"jeans\", \"size\":2, \"page\":1}")
                .post()
                .then()
                .statusCode(200)
                .body("totalHits", is(8))
                .body("products[0].id", is("6"))
                .body("products[1].id", is("5"));
    }

}
