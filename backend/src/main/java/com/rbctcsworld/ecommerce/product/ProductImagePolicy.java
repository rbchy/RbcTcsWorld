package com.rbctcsworld.ecommerce.product;

import com.rbctcsworld.ecommerce.common.SharedEnvironment;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Where product pictures may come from. Normally the shop's own files (/products/...) or any https:// link
 * (ProductRequest checks the format). In the public demo anybody may log in as a store admin, so an
 * outside link could show any picture on a public page: there only the shop's own pictures are accepted.
 */
@Component
public class ProductImagePolicy {

    static final String DEMO_MESSAGE = "This demo shop only accepts its own pictures (/products/...); outside links are switched off";

    private final boolean demo;

    public ProductImagePolicy(Environment env) {
        this.demo = SharedEnvironment.isDemo(env);
    }

    public void check(String imageUrl) {
        if (demo && imageUrl != null && !imageUrl.isBlank() && !imageUrl.trim().startsWith("/products/")) {
            throw new BusinessRuleException(DEMO_MESSAGE);
        }
    }
}
