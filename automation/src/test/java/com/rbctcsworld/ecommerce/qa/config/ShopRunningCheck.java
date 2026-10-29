package com.rbctcsworld.ecommerce.qa.config;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * The automation suite tests a RUNNING shop. Started without one, hundreds of tests each fail with a bare
 * "Connection refused", which reads like hundreds of bugs. This listener checks the addresses once and, if the shop
 * is not reachable, prints one plain explanation with the command to start it, before and after the run.
 * Registered in META-INF/services; it never changes a test result.
 */
public class ShopRunningCheck implements TestExecutionListener {

    private List<String> missing = List.of();

    @Override
    public void testPlanExecutionStarted(TestPlan plan) {
        missing = unreachable(hasUiTests(plan));
        if (!missing.isEmpty()) System.out.println(message(missing));
    }

    @Override
    public void testPlanExecutionFinished(TestPlan plan) {
        if (!missing.isEmpty()) System.out.println(message(missing));
    }

    /** True when the selected tests include UI tests (tag "ui"): only they need the storefront. */
    static boolean hasUiTests(TestPlan plan) {
        return plan.getRoots().stream().flatMap(root -> plan.getDescendants(root).stream())
                .anyMatch(t -> t.getTags().stream().anyMatch(tag -> tag.getName().equals("ui")));
    }

    static List<String> unreachable(boolean ui) {
        List<String> down = new ArrayList<>();
        if (!answers(TestConfig.baseUrl() + "/actuator/health")) down.add("backend  " + TestConfig.baseUrl());
        if (ui && !answers(TestConfig.uiUrl())) down.add("storefront  " + TestConfig.uiUrl() + "  (UI tests)");
        return down;
    }

    static boolean answers(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setConnectTimeout(2000);
            c.setReadTimeout(3000);
            int code = c.getResponseCode();
            c.disconnect();
            return code > 0 && code < 500;
        } catch (Exception e) {
            return false;
        }
    }

    static String message(List<String> down) {
        String line = "=".repeat(78);
        return "\n" + line + "\n THE SHOP IS NOT RUNNING - the failures below are \"Connection refused\", not bugs.\n"
                + " Not reachable:\n" + String.join("\n", down.stream().map(d -> "   - " + d).toList()) + "\n"
                + " Start the whole shop first, then run the tests again:\n"
                + "   make app        (or: docker compose --profile app up -d --build --wait)\n"
                + " Docker Desktop must be running. Stop the shop later with: make down\n" + line + "\n";
    }
}
