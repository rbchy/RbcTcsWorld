package com.rbctcsworld.ecommerce.report;

import com.rbctcsworld.ecommerce.report.PaymentLedgerService.PaymentPage;
import com.rbctcsworld.ecommerce.report.ReportService.Options;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Store admin only (/api/admin/** in SecurityConfig). Dates are yyyy-MM-dd.
 * GET /api/admin/reports                         the report names
 * GET /api/admin/reports/{name}?from&to&groupBy=day|month&limit&threshold    one report | 400 | 404
 * GET /api/admin/reports/{name}/export?...       the same as a CSV download
 * GET /api/admin/payments?from&to&type&status&q&page&size                   every charge and refund
 */
@RestController
@RequestMapping("/api/admin")
public class ReportController {

    private final ReportService reports;
    private final PaymentLedgerService payments;

    public ReportController(ReportService reports, PaymentLedgerService payments) {
        this.reports = reports;
        this.payments = payments;
    }

    @GetMapping("/reports")
    public List<String> names() {
        return ReportService.NAMES;
    }

    @GetMapping("/reports/{name}")
    public ReportTable report(@PathVariable String name,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                              @RequestParam(required = false) String groupBy,
                              @RequestParam(required = false) Integer limit,
                              @RequestParam(required = false) Integer threshold) {
        return reports.run(name, new Options(from, to, groupBy, limit, threshold));
    }

    @GetMapping("/reports/{name}/export")
    public ResponseEntity<String> export(@PathVariable String name,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) String groupBy,
                                         @RequestParam(required = false) Integer limit,
                                         @RequestParam(required = false) Integer threshold) {
        ReportTable t = reports.run(name, new Options(from, to, groupBy, limit, threshold));
        String file = "report-" + t.name() + (t.from() == null ? "" : "-" + t.from() + "-to-" + t.to()) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + "\"")
                .body(reports.csv(t));
    }

    @GetMapping("/payments")
    public PaymentPage payments(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                @RequestParam(required = false) String type,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false) String q,
                                @RequestParam(required = false) Integer page,
                                @RequestParam(required = false) Integer size) {
        return payments.search(from, to, type, status, q, page, size);
    }
}
