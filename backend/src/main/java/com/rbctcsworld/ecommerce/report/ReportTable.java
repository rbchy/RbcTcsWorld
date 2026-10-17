package com.rbctcsworld.ecommerce.report;

import java.time.LocalDate;
import java.util.List;

/**
 * Every report has the same shape, so one page can show, print and export any of them.
 * columns = keys the storefront translates; rows = values in column order; totals = the sum line
 * (null cells where a sum means nothing), or null when the report has no totals.
 * from / to = the dates the report covers (null for a snapshot such as current stock).
 */
public record ReportTable(String name, LocalDate from, LocalDate to, List<String> columns, List<List<Object>> rows,
                          List<Object> totals) {
}
