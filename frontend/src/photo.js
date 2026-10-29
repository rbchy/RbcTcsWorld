/**
 * Prepares a product photo in the browser before it is uploaded (ADR-0017). The server accepts JPEG and PNG up to
 * 10 MB and does all the safety work itself; this only makes the upload work for what phones actually produce:
 *  - WebP and HEIC (iPhone) photos are turned into JPEG when the browser can read them (Safari reads HEIC);
 *  - very big photos are shrunk to at most 2400 px first, so a 12 MB phone photo uploads in seconds on mobile data;
 *  - the photo is drawn upright (EXIF orientation), so it never arrives lying on its side.
 * A small JPEG or PNG is sent as it is: the server turns it upright and re-encodes it anyway.
 */
export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
export const MAX_SOURCE_BYTES = 40 * 1024 * 1024;     // bigger files are not even opened
export const BROWSER_MAX_EDGE = 2400;
export const ACCEPT = 'image/jpeg,image/png,image/webp,image/heic,image/heif';
export const DECODE_TIMEOUT_MS = 4000;

/** Error keys (i18n): photoTooBig, photoUnreadable. */
export class PhotoError extends Error {
  constructor(key) { super(key); this.key = key; }
}

const SENT_AS_IS = ['image/jpeg', 'image/png'];

/** Returns a Blob (or the File itself) ready for POST /api/products/pictures. */
export async function preparePhoto(file) {
  if (!file || file.size === 0) throw new PhotoError('photoUnreadable');
  if (file.size > MAX_SOURCE_BYTES) throw new PhotoError('photoTooBig');
  let bitmap = null;
  try {
    // a decoder can stall on a file that is not a picture: give up after a few seconds and let the server decide
    bitmap = await Promise.race([
      createImageBitmap(file, { imageOrientation: 'from-image' }),
      new Promise((_, reject) => setTimeout(() => reject(new Error('decode timeout')), DECODE_TIMEOUT_MS)),
    ]);
  } catch {
    // this browser cannot read it (HEIC in Chrome, a damaged file): only JPEG/PNG may still go to the server
  }
  const plain = SENT_AS_IS.includes(file.type);
  if (!bitmap) {
    if (plain && file.size <= MAX_UPLOAD_BYTES) return file;
    throw new PhotoError(plain ? 'photoTooBig' : 'photoUnreadable');
  }
  try {
    const longEdge = Math.max(bitmap.width, bitmap.height);
    if (plain && file.size <= MAX_UPLOAD_BYTES && longEdge <= BROWSER_MAX_EDGE) return file;
    const scale = Math.min(1, BROWSER_MAX_EDGE / longEdge);
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    const g = canvas.getContext('2d');
    // PNG keeps its transparency; everything else becomes a JPEG on white
    const png = file.type === 'image/png';
    if (!png) { g.fillStyle = '#fff'; g.fillRect(0, 0, canvas.width, canvas.height); }
    g.imageSmoothingQuality = 'high';
    g.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise(resolve => canvas.toBlob(resolve, png ? 'image/png' : 'image/jpeg', 0.9));
    if (!blob) throw new PhotoError('photoUnreadable');
    if (blob.size > MAX_UPLOAD_BYTES) throw new PhotoError('photoTooBig');
    return blob;
  } finally {
    bitmap.close?.();
  }
}

/** File name sent with the upload (the server ignores it; it only helps reading logs). */
export function uploadName(blob, original) {
  const base = (original?.name || 'photo').replace(/\.[^.]*$/, '').replace(/[^\w-]+/g, '-').slice(0, 40) || 'photo';
  return `${base}.${blob.type === 'image/png' ? 'png' : 'jpg'}`;
}
