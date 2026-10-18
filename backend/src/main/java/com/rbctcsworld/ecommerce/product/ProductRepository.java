package com.rbctcsworld.ecommerce.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * "Similar products": others on sale in the same category (case-insensitive), the closest price first,
     * products in stock before sold-out ones. Without a category: the closest price among all products.
     */
    @Query("""
            select p from Product p
            where p.active = true and p.id <> :id and lower(p.category) = lower(:category)
            order by case when p.stock > 0 then 0 else 1 end, abs(p.price - :price), p.id""")
    List<Product> similarInCategory(@Param("id") Long id, @Param("category") String category,
                                    @Param("price") java.math.BigDecimal price, Pageable pageable);

    /** For a product without a category: the closest price among all products on sale. */
    @Query("""
            select p from Product p
            where p.active = true and p.id <> :id
            order by case when p.stock > 0 then 0 else 1 end, abs(p.price - :price), p.id""")
    List<Product> similarByPrice(@Param("id") Long id, @Param("price") java.math.BigDecimal price, Pageable pageable);

    /** The variants of one item on sale, in the order they were added. */
    @Query("select p from Product p where p.active = true and p.variantGroup = :group order by p.id")
    List<Product> variantsOf(@Param("group") String group);

    /** The variants of several items at once (for the option summary on catalog cards). */
    @Query("select p from Product p where p.active = true and p.variantGroup in :groups order by p.id")
    List<Product> variantsOfAll(@Param("groups") java.util.Collection<String> groups);

    /** The categories of the products on sale, for the catalog's category filter. */
    @Query("select distinct p.category from Product p where p.active = true and p.category is not null order by p.category")
    List<String> activeCategories();


    /** One page of the active catalog (DEF-007: the catalog is never returned in one piece). */
    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseAndActiveTrue(String name, Pageable pageable);

    Optional<Product> findByIdAndActiveTrue(Long id);

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    /** Atomic "check and decrement". Returns 1 if stock was reserved, 0 if not enough stock / inactive. */
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock - :qty where p.id = :id and p.active = true and p.stock >= :qty")
    int decrementStock(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :qty where p.id = :id")
    int incrementStock(@Param("id") Long id, @Param("qty") int qty);

    /** Reads stock straight from the database (not from a possibly stale entity in memory). */
    @Query("select p.stock from Product p where p.id = :id")
    int currentStock(@Param("id") Long id);

    /** SELECT ... FOR UPDATE: serialises rating recalculation for one product (see ReviewService). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> lockById(@Param("id") Long id);
}
