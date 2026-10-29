package com.rbctcsworld.ecommerce.product;

import com.rbctcsworld.ecommerce.common.SharedEnvironment;
import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Where product pictures may come from. Normally the shop's own files (/products/...) or any https:// link
 * (ProductRequest checks the format). In the public demo anybody may log in as a store admin, so an
 * outside link could show any picture on a public page: there only the shop's own pictures are accepted,
 * and photo uploads are switched off.
 */
@Component
public class ProductImagePolicy {

    static final String DEMO_MESSAGE = "This demo shop only accepts its own pictures (/products/...); outside links are switched off";
    static final String DEMO_UPLOAD_MESSAGE = "This demo shop does not take photo uploads; they work in your own shop";

    private final boolean demo;

    public ProductImagePolicy(Environment env) {
        this.demo = SharedEnvironment.isDemo(env);
    }

    public void check(String imageUrl) {
        if (demo && imageUrl != null && !imageUrl.isBlank() && !imageUrl.trim().startsWith("/products/")) {
            throw new BusinessRuleException(DEMO_MESSAGE);
        }
    }

    static final String DEMO_AFFILIATE_MESSAGE = "This demo shop does not take partner (affiliate) links";

    /** Partner links are off in the public demo too: anybody there could point visitors to any site (ADR-0020). */
    public void checkAffiliate(String affiliateUrl) {
        if (demo && affiliateUrl != null && !affiliateUrl.isBlank()) throw new BusinessRuleException(DEMO_AFFILIATE_MESSAGE);
    }

    /** Uploads are off in the public demo for the same reason (ADR-0017): any visitor could put any photo online. */
    public void checkUpload() {
        if (demo) throw new BusinessRuleException(DEMO_UPLOAD_MESSAGE);
    }
}
