/**
 * CSV made in the browser, with the same safety rules as the server (common/Csv.java): every text cell
 * quoted, inner quotes doubled, and a leading = + - @ tab or CR defused with an apostrophe (CSV injection).
 */
export function csvCell(v) {
  if (v == null) return '';
  if (typeof v === 'number') return String(v);
  let s = String(v);
  if (s && '=+-@\t\r'.includes(s[0])) s = `'${s}`;
  return `"${s.replace(/"/g, '""')}"`;
}

export function toCsv(header, rows) {
  return [header, ...rows].map(r => r.map(csvCell).join(',')).join('\r\n') + '\r\n';
}

/** Hands the text to the browser as a file download. */
export function downloadText(filename, text, type = 'text/csv;charset=utf-8') {
  const url = URL.createObjectURL(new Blob([text], { type }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}
