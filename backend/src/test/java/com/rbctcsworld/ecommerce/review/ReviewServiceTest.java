package com.rbctcsworld.ecommerce.review;

import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.ForbiddenException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.order.OrderRepository;
import com.rbctcsworld.ecommerce.product.Product;
import com.rbctcsworld.ecommerce.product.ProductRepository;
import com.rbctcsworld.ecommerce.review.ReviewDtos.RatingSummary;
import com.rbctcsworld.ecommerce.review.ReviewDtos.ReviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.mockito.InOrder;
import com.rbctcsworld.ecommerce.review.ReviewDtos.ProductReviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final String EMAIL = "jane.doe@test.com";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-01T10:00:00Z"), ZoneOffset.UTC);

    @Mock ReviewRepository reviews;
    @Mock ProductRepository products;
    @Mock OrderRepository orders;
    @Mock UserRepository users;
    private ReviewService service;
    private Product product;

    @BeforeEach
    void setUp() {
        service = new ReviewService(reviews, products, orders, users, CLOCK);
        AppUser user = new AppUser(EMAIL, "x");
        ReflectionTestUtils.setField(user, "id", 7L);
        lenient().when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        product = new Product("Mouse", "SKU-M", "e", new BigDecimal("10.00"), 5);
        ReflectionTestUtils.setField(product, "id", 1L);
        lenient().when(products.lockById(1L)).thenReturn(Optional.of(product));
    }

    // ---------- account deleted (REQ-AUTH-17, ADR-0022) ----------

    @Test
    void anAccountDeletedTakesItsReviewsAndEachProductsRatingIsRecalculatedOnce() {
        Product other = new Product("Pad", "SKU-P", "e", new BigDecimal("5.00"), 5);
        ReflectionTestUtils.setField(other, "id", 2L);
        product.applyRating(new BigDecimal("3.0"), 2);
        other.applyRating(new BigDecimal("1.0"), 1);
        List<Review> mine = List.of(new Review(2L, 7L, 1, null, null, java.time.LocalDateTime.now(CLOCK)),
                new Review(1L, 7L, 1, null, null, java.time.LocalDateTime.now(CLOCK)));
        when(reviews.findByUserId(7L)).thenReturn(mine);
        when(products.lockById(2L)).thenReturn(Optional.of(other));
        when(reviews.ratingHistogram(1L)).thenReturn(List.<Object[]>of(new Object[] {5, 1L}));   // the other customer's 5
        when(reviews.ratingHistogram(2L)).thenReturn(List.of());

        assertThat(service.removeAllBy(7L)).isEqualTo(2);

        verify(reviews).deleteAll(mine);
        assertThat(product.getRatingCount()).isEqualTo(1);
        assertThat(product.getRatingAverage()).isEqualByComparingTo("5.0");
        assertThat(other.getRatingCount()).isZero();
        verify(products).save(product);
        verify(products).save(other);
    }

    @Test
    void noReviewsNothingToRecalculate() {
        when(reviews.findByUserId(7L)).thenReturn(List.of());
        assertThat(service.removeAllBy(7L)).isZero();
        verify(reviews, never()).deleteAll(org.mockito.ArgumentMatchers.anyIterable());
        verify(products, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // ---------- rating maths ----------

    /** Each row: the star values of all published reviews, expected average. */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "'5',        5.0",
            "'4 5',      4.5",
            "'4 4 5',    4.3",   // 4.333 -> 4.3
            "'3 4 4',    3.7",   // 3.666 -> 3.7
            "'1 2',      1.5",
            "'1 1 1 2',  1.3",   // 1.25  -> 1.3 (HALF_UP, not banker's rounding)
            "'5 5 5 5 4 4 3 2 1 1', 3.5"
    })
    void averageIsRoundedHalfUpToOneDecimal(String stars, String expected) {
        RatingSummary s = ReviewService.summarize(histogram(stars));
        assertThat(s.average()).isEqualByComparingTo(expected);
        assertThat(s.count()).isEqualTo(stars.trim().split("\\s+").length);
    }

    @Test
    void noReviewsMeansZeroAndAllFiveDistributionKeys() {
        RatingSummary s = ReviewService.summarize(List.of());
        assertThat(s.average()).isEqualByComparingTo("0.0");
        assertThat(s.count()).isZero();
        assertThat(s.distribution()).containsOnlyKeys(5, 4, 3, 2, 1).allSatisfy((k, v) -> assertThat(v).isZero());
    }

    @ParameterizedTest
    @CsvSource({"jane.doe@test.com, ja***", "ab@x.com, a***", "a@x.com, a***", "qa-123@t.com, qa***"})
    void reviewerNameIsMasked(String email, String expected) {
        assertThat(ReviewService.mask(email)).isEqualTo(expected).doesNotContain("@");
    }

    // ---------- create ----------

    @Test
    void verifiedBuyerCanReviewAndProductRatingIsRecalculated() {
        when(orders.hasReceived(eq(7L), eq(1L), any())).thenReturn(true);
        when(reviews.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reviews.ratingHistogram(1L)).thenReturn(histogram("4 5"));

        ReviewResponse r = service.create(EMAIL, 1L, 5, "  Great  ", "   ");

        assertThat(r.title()).isEqualTo("Great");
        assertThat(r.body()).as("blank body stored as null").isNull();
        assertThat(r.reviewer()).isEqualTo("ja***");
        assertThat(r.verifiedPurchase()).isTrue();
        assertThat(product.getRatingAverage()).isEqualByComparingTo("4.5");
        assertThat(product.getRatingCount()).isEqualTo(2);
    }

    @Test
    void customerWhoNeverReceivedTheProductIsForbidden() {
        when(orders.hasReceived(eq(7L), eq(1L), any())).thenReturn(false);

        assertThatThrownBy(() -> service.create(EMAIL, 1L, 5, null, null))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("received");
        verify(reviews, never()).saveAndFlush(any());
    }

    @Test
    void receivedMeansDeliveredOrLaterNotJustPaidOrShipped() {
        assertThat(ReviewService.RECEIVED).containsExactlyInAnyOrder("DELIVERED", "RETURN_REQUESTED", "RETURNED");
    }

    @Test
    void secondReviewOfTheSameProductIsAConflict() {
        when(orders.hasReceived(eq(7L), eq(1L), any())).thenReturn(true);
        when(reviews.existsByUserIdAndProductId(7L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(EMAIL, 1L, 4, null, null)).isInstanceOf(ConflictException.class);
    }

    @Test
    void inactiveProductCannotBeReviewed() {
        product.setActive(false);
        assertThatThrownBy(() -> service.create(EMAIL, 1L, 4, null, null)).isInstanceOf(NotFoundException.class);
    }

    // ---------- update / delete ----------

    @Test
    void editingSomeoneElsesReviewLooksLikeNotFound() {
        when(reviews.findByIdAndUserId(99L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(EMAIL, 99L, 1, null, null)).isInstanceOf(NotFoundException.class);
        verify(products, never()).lockById(any());
    }

    @Test
    void editRecalculatesAndKeepsHiddenStatus() {
        Review r = new Review(1L, 7L, 5, "t", "b", java.time.LocalDateTime.now(CLOCK));
        r.setStatus(Review.HIDDEN);
        when(reviews.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(r));
        when(reviews.ratingHistogram(1L)).thenReturn(histogram("3"));

        ReviewResponse res = service.update(EMAIL, 3L, 2, "new", null);

        assertThat(res.status()).isEqualTo(Review.HIDDEN);
        assertThat(res.rating()).isEqualTo(2);
        assertThat(res.updatedAt()).isNotNull();
        assertThat(product.getRatingAverage()).isEqualByComparingTo("3.0");
    }

    @Test
    void deletingTheLastReviewResetsTheRating() {
        product.applyRating(new BigDecimal("4.0"), 1);
        Review r = new Review(1L, 7L, 4, null, null, java.time.LocalDateTime.now(CLOCK));
        when(reviews.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(r));
        when(reviews.ratingHistogram(1L)).thenReturn(List.of());

        service.delete(EMAIL, 3L);

        // added after PIT: removing flush() survived. The new average must be computed AFTER the delete reached
        // the database, otherwise the deleted review is still counted.
        InOrder seq = inOrder(reviews, products);
        seq.verify(reviews).delete(r);
        seq.verify(reviews).flush();
        seq.verify(reviews).ratingHistogram(1L);
        seq.verify(products).save(product);
        assertThat(product.getRatingAverage()).isEqualByComparingTo("0.0");
        assertThat(product.getRatingCount()).isZero();
    }

    // ---------- listing ----------

    @Test
    void unknownSortIsRejected() {
        assertThatThrownBy(() -> service.forProduct(1L, "random")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void unknownModerationStatusIsRejected() {
        assertThatThrownBy(() -> service.adminList("deleted")).isInstanceOf(BusinessRuleException.class);
    }

    // ---------- helpers ----------

    /** "4 4 5" -> [[4,2],[5,1]] like the GROUP BY query returns. */
    private static List<Object[]> histogram(String stars) {
        long[] counts = new long[6];
        Arrays.stream(stars.trim().split("\\s+")).mapToInt(Integer::parseInt).forEach(s -> counts[s]++);
        List<Object[]> rows = new ArrayList<>();
        for (int s = 1; s <= 5; s++) if (counts[s] > 0) rows.add(new Object[]{s, counts[s]});
        return rows;
    }

    // ---- added after mutation testing (PIT): the public product page (default sort, reviewer names) and the
    // ---- defaults for an empty sort / status / e-mail had no unit test.

    private Review published(long id, long userId, int rating, int daysAgo) {
        Review r = new Review(1L, userId, rating, "t" + id, null, java.time.LocalDateTime.now(CLOCK).minusDays(daysAgo));
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    @Test
    void productPageShowsNewestFirstByDefaultWithMaskedNames() {
        AppUser jane = new AppUser("jane.doe@test.com", "x");
        ReflectionTestUtils.setField(jane, "id", 21L);
        AppUser bob = new AppUser("bob@test.com", "x");
        ReflectionTestUtils.setField(bob, "id", 22L);
        when(products.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(product));
        when(reviews.findByProductIdAndStatus(1L, Review.PUBLISHED))
                .thenReturn(List.of(published(1, 21L, 5, 3), published(2, 22L, 2, 1)));
        when(users.findAllById(any())).thenReturn(List.of(jane, bob));
        when(reviews.ratingHistogram(1L)).thenReturn(histogram("5 2"));

        for (String defaultSort : new String[] {null, "  "}) {
            ProductReviews page = service.forProduct(1L, defaultSort);
            assertThat(page.reviews()).extracting(ReviewResponse::id).containsExactly(2L, 1L);   // newest first
            assertThat(page.reviews()).extracting(ReviewResponse::reviewer).containsExactly("bo***", "ja***");
            assertThat(page.averageRating()).isEqualByComparingTo("3.5");
            assertThat(page.reviewCount()).isEqualTo(2);
        }
        assertThat(service.forProduct(1L, "HIGHEST").reviews()).extracting(ReviewResponse::rating).containsExactly(5, 2);
    }

    @Test
    void emptyModerationFilterListsEveryReview() {
        when(reviews.findAllByOrderByIdDesc()).thenReturn(List.of(published(9, 21L, 4, 0)));

        assertThat(service.adminList(null)).extracting(ReviewResponse::id).containsExactly(9L);
        assertThat(service.adminList(" ")).extracting(ReviewResponse::id).containsExactly(9L);
        verify(reviews, org.mockito.Mockito.times(2)).findAllByOrderByIdDesc();
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(value = {"NULL, Customer", "'   ', Customer", "noatsign, no***"}, nullValues = "NULL")
    void reviewerNameFallbacks(String email, String expected) {
        assertThat(ReviewService.mask(email)).isEqualTo(expected);
    }
}
