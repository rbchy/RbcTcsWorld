package com.rbctcsworld.ecommerce.review;

import com.rbctcsworld.ecommerce.auth.AppUser;
import com.rbctcsworld.ecommerce.auth.UserRepository;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import com.rbctcsworld.ecommerce.common.exception.ConflictException;
import com.rbctcsworld.ecommerce.common.exception.ForbiddenException;
import com.rbctcsworld.ecommerce.common.exception.NotFoundException;
import com.rbctcsworld.ecommerce.order.OrderRepository;
import com.rbctcsworld.ecommerce.order.OrderStatus;
import com.rbctcsworld.ecommerce.product.Product;
import com.rbctcsworld.ecommerce.product.ProductRepository;
import com.rbctcsworld.ecommerce.review.ReviewDtos.ProductReviews;
import com.rbctcsworld.ecommerce.review.ReviewDtos.RatingSummary;
import com.rbctcsworld.ecommerce.review.ReviewDtos.ReviewResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business rules:
 *  1. Rating 1..5, title <= 100, body <= 2000 characters (400). Blank title/body are stored as null.
 *  2. Verified purchase only: the customer must have RECEIVED the product (an order with it that is
 *     DELIVERED, RETURN_REQUESTED or RETURNED). Otherwise 403.
 *  3. One review per customer per product (409). The customer may edit or delete it.
 *  4. Another customer's review id behaves as "not found" (404, IDOR protection).
 *  5. Only PUBLISHED reviews are listed and counted. An admin can hide / re-publish (moderation);
 *     editing a hidden review does not publish it again.
 *  6. The product's averageRating (1 decimal, HALF_UP) and reviewCount are recalculated in the same
 *     transaction as every change, while the product row is locked (SELECT ... FOR UPDATE), so two
 *     reviews saved at the same moment can never leave a wrong average.
 *  7. Public responses show a masked reviewer name, never the e-mail address.
 */
@Service
@Transactional
public class ReviewService {

    static final Set<String> RECEIVED = Set.of(OrderStatus.DELIVERED, OrderStatus.RETURN_REQUESTED, OrderStatus.RETURNED);
    public static final List<String> SORTS = List.of("newest", "highest", "lowest");

    private final ReviewRepository reviews;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final UserRepository users;
    private final Clock clock;

    public ReviewService(ReviewRepository reviews, ProductRepository products, OrderRepository orders,
                         UserRepository users, Clock clock) {
        this.reviews = reviews;
        this.products = products;
        this.orders = orders;
        this.users = users;
        this.clock = clock;
    }

    // ---------- customer ----------

    public ReviewResponse create(String email, Long productId, int rating, String title, String body) {
        AppUser user = user(email);
        Product product = products.lockById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId));
        if (!orders.hasReceived(user.getId(), productId, RECEIVED)) {
            throw new ForbiddenException("Only customers who received this product can review it");
        }
        if (reviews.existsByUserIdAndProductId(user.getId(), productId)) {
            throw new ConflictException("You have already reviewed this product - edit your review instead");
        }
        Review r = reviews.saveAndFlush(new Review(productId, user.getId(), rating, clean(title), clean(body), now()));
        recalculate(product);
        return toResponse(r, email);
    }

    public ReviewResponse update(String email, Long reviewId, int rating, String title, String body) {
        AppUser user = user(email);
        Review r = owned(reviewId, user.getId());
        Product product = lockedProduct(r.getProductId());
        r.edit(rating, clean(title), clean(body), now());
        reviews.saveAndFlush(r);
        recalculate(product);
        return toResponse(r, email);
    }

    public void delete(String email, Long reviewId) {
        AppUser user = user(email);
        Review r = owned(reviewId, user.getId());
        Product product = lockedProduct(r.getProductId());
        reviews.delete(r);
        reviews.flush();
        recalculate(product);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> mine(String email) {
        return reviews.findByUserIdOrderByIdDesc(user(email).getId()).stream().map(r -> toResponse(r, email)).toList();
    }

    // ---------- public ----------

    @Transactional(readOnly = true)
    public ProductReviews forProduct(Long productId, String sort) {
        String s = sort == null || sort.isBlank() ? "newest" : sort.trim().toLowerCase();
        if (!SORTS.contains(s)) {
            throw new BusinessRuleException("sort must be one of " + SORTS);
        }
        Product product = products.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId));
        List<Review> published = reviews.findByProductIdAndStatus(productId, Review.PUBLISHED);
        Map<Long, String> names = reviewerNames(published);
        List<ReviewResponse> list = published.stream()
                .sorted(comparator(s))
                .map(r -> toResponse(r, names.get(r.getUserId())))
                .toList();
        RatingSummary summary = summarize(reviews.ratingHistogram(productId));
        return new ProductReviews(product.getId(), summary.average(), summary.count(), summary.distribution(), list);
    }

    // ---------- admin moderation ----------

    @Transactional(readOnly = true)
    public List<ReviewResponse> adminList(String status) {
        List<Review> list;
        if (status == null || status.isBlank()) {
            list = reviews.findAllByOrderByIdDesc();
        } else {
            String st = status.trim().toUpperCase();
            if (!st.equals(Review.PUBLISHED) && !st.equals(Review.HIDDEN)) {
                throw new BusinessRuleException("status must be PUBLISHED or HIDDEN");
            }
            list = reviews.findByStatusOrderByIdDesc(st);
        }
        Map<Long, String> names = reviewerNames(list);
        return list.stream().map(r -> toResponse(r, names.get(r.getUserId()))).toList();
    }

    public ReviewResponse moderate(Long reviewId, String newStatus) {
        Review r = reviews.findById(reviewId).orElseThrow(() -> new NotFoundException("Review not found: " + reviewId));
        Product product = lockedProduct(r.getProductId());
        r.setStatus(newStatus);
        reviews.saveAndFlush(r);
        recalculate(product);
        return toResponse(r, users.findById(r.getUserId()).map(AppUser::getEmail).orElse(null));
    }

    // ---------- rating maths (pure, unit tested) ----------

    /** Turns [rating, count] rows into average (1 decimal, HALF_UP), total count and a 5..1 distribution. */
    static RatingSummary summarize(List<Object[]> histogram) {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) distribution.put(star, 0L);
        long count = 0;
        long sum = 0;
        for (Object[] row : histogram) {
            int star = ((Number) row[0]).intValue();
            long n = ((Number) row[1]).longValue();
            distribution.put(star, n);
            count += n;
            sum += star * n;
        }
        BigDecimal average = count == 0
                ? BigDecimal.ZERO.setScale(1)
                : BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
        return new RatingSummary(average, (int) count, distribution);
    }

    /** "jane.doe@x.com" -> "ja***". Short local parts keep only the first character. */
    static String mask(String email) {
        if (email == null || email.isBlank()) return "Customer";
        String local = email.substring(0, Math.max(email.indexOf('@'), 0));
        if (local.isEmpty()) local = email;
        return (local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2)) + "***";
    }

    /**
     * Account deleted (ADR-0022): the customer's reviews go, and every product they had reviewed gets its rating and
     * review count recalculated without them. Returns how many reviews were removed.
     */
    public int removeAllBy(long userId) {
        List<Review> mine = reviews.findByUserId(userId);
        if (mine.isEmpty()) return 0;
        reviews.deleteAll(mine);
        reviews.flush();
        mine.stream().map(Review::getProductId).distinct().sorted()
                .forEach(id -> products.lockById(id).ifPresent(this::recalculate));
        return mine.size();
    }

    // ---------- helpers ----------

    private void recalculate(Product product) {
        RatingSummary s = summarize(reviews.ratingHistogram(product.getId()));
        product.applyRating(s.average(), s.count());
        products.save(product);
    }

    private static Comparator<Review> comparator(String sort) {
        Comparator<Review> newest = Comparator.comparing(Review::getCreatedAt).thenComparing(Review::getId).reversed();
        return switch (sort) {
            case "highest" -> Comparator.comparingInt(Review::getRating).reversed().thenComparing(newest);
            case "lowest" -> Comparator.comparingInt(Review::getRating).thenComparing(newest);
            default -> newest;
        };
    }

    private Map<Long, String> reviewerNames(List<Review> list) {
        Set<Long> ids = list.stream().map(Review::getUserId).collect(Collectors.toSet());
        return users.findAllById(ids).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getEmail, (a, b) -> a));
    }

    private Product lockedProduct(Long productId) {
        return products.lockById(productId).orElseThrow(() -> new NotFoundException("Product not found: " + productId));
    }

    private Review owned(Long reviewId, Long userId) {
        return reviews.findByIdAndUserId(reviewId, userId)
                .orElseThrow(() -> new NotFoundException("Review not found: " + reviewId));
    }

    private ReviewResponse toResponse(Review r, String reviewerEmail) {
        return new ReviewResponse(r.getId(), r.getProductId(), r.getRating(), r.getTitle(), r.getBody(),
                mask(reviewerEmail), true, r.getStatus(), r.getCreatedAt(), r.getUpdatedAt());
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private AppUser user(String email) {
        return users.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
    }

}
