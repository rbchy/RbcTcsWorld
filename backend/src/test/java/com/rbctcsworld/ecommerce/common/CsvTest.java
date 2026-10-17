package com.rbctcsworld.ecommerce.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTest {

    @Test
    void textCellsAreQuotedAndFormulasDefused() {
        assertThat(Csv.cell(null)).isEmpty();
        assertThat(Csv.cell("")).isEqualTo("\"\"");
        assertThat(Csv.cell("a\"b")).isEqualTo("\"a\"\"b\"");
        for (String start : List.of("=", "+", "-", "@", "\t", "\r")) {
            assertThat(Csv.cell(start + "1")).isEqualTo("\"'" + start + "1\"");
        }
        assertThat(Csv.cell("1-2")).isEqualTo("\"1-2\"");
    }

    @Test
    void numbersStayNumbersSoASpreadsheetCanAddThemUp() {
        assertThat(Csv.value(null)).isEmpty();
        assertThat(Csv.value(42L)).isEqualTo("42");
        assertThat(Csv.value(new BigDecimal("1E+3"))).isEqualTo("1000");
        assertThat(Csv.value(new BigDecimal("-5.50"))).isEqualTo("-5.50");
        assertThat(Csv.value("-5.50")).isEqualTo("\"'-5.50\"");
        assertThat(Csv.value(java.time.LocalDate.of(2026, 10, 1))).isEqualTo("\"2026-10-01\"");
    }

    @Test
    void aLineEndsWithCrLf() {
        assertThat(Csv.line(Arrays.asList("a", 1, null, new BigDecimal("2.00")))).isEqualTo("\"a\",1,,2.00\r\n");
        assertThat(Csv.line(List.of())).isEqualTo("\r\n");
    }
}
