/*
 * deflate-find: dump every large raw-DEFLATE stream found in a file.
 *
 * ksud embeds its assets through rust-embed's `compression` feature, which
 * stores each file as a bare DEFLATE stream (no zlib header, no index) beside
 * the rest of the ELF data.  Nothing in the binary says where one stream ends
 * and the next begins, so the only way to prove a module is really inside is to
 * inflate the streams and compare the result byte for byte.
 *
 * Usage: deflate-find <file> <output-dir> [min-bytes]
 *
 * Writes payload-000000.bin, payload-000001.bin, ... for every stream that
 * inflates cleanly and is at least min-bytes long (default 100000), and prints
 * one "payload-N size" line per file.  Scanning resumes after each accepted
 * stream, so a multi-megabyte payload is inflated once, not once per offset.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <zlib.h>

#define CHUNK (1u << 20)

int main(int argc, char **argv) {
    if (argc < 3) {
        fprintf(stderr, "usage: %s <file> <output-dir> [min-bytes]\n", argv[0]);
        return 2;
    }
    const char *path = argv[1];
    const char *outdir = argv[2];
    const size_t minimum = argc > 3 ? strtoul(argv[3], NULL, 10) : 100000;

    FILE *fh = fopen(path, "rb");
    if (!fh) {
        perror(path);
        return 1;
    }
    if (fseek(fh, 0, SEEK_END) != 0) {
        perror("seek");
        return 1;
    }
    long length = ftell(fh);
    if (length <= 0) {
        fprintf(stderr, "%s: empty\n", path);
        return 1;
    }
    rewind(fh);
    unsigned char *data = malloc((size_t)length);
    if (!data || fread(data, 1, (size_t)length, fh) != (size_t)length) {
        fprintf(stderr, "%s: cannot read\n", path);
        return 1;
    }
    fclose(fh);

    unsigned char *payload = NULL;
    size_t payload_cap = 0;
    size_t found = 0;

    size_t pos = 0;
    const size_t end = (size_t)length;
    while (pos + 4 < end) {
        /* A dynamic-Huffman block starts with BFINAL=x, BTYPE=10, which is
         * bits 0..2 == 100 or 101 of the first byte.  Everything else is
         * skipped without touching zlib. */
        if ((data[pos] & 0x07) != 0x04 && (data[pos] & 0x07) != 0x05) {
            pos++;
            continue;
        }

        z_stream stream;
        memset(&stream, 0, sizeof(stream));
        if (inflateInit2(&stream, -15) != Z_OK) {
            fprintf(stderr, "inflateInit2 failed\n");
            return 1;
        }
        stream.next_in = data + pos;
        stream.avail_in = (uInt)(end - pos);

        size_t used = 0;
        int status = Z_OK;
        do {
            if (used == payload_cap) {
                payload_cap = payload_cap ? payload_cap * 2 : CHUNK;
                unsigned char *grown = realloc(payload, payload_cap);
                if (!grown) {
                    fprintf(stderr, "out of memory\n");
                    return 1;
                }
                payload = grown;
            }
            stream.next_out = payload + used;
            stream.avail_out = (uInt)((payload_cap - used) > CHUNK ? CHUNK : (payload_cap - used));
            status = inflate(&stream, Z_NO_FLUSH);
            used = (size_t)(stream.next_out - payload);
            if (status == Z_BUF_ERROR && stream.avail_out > 0) {
                break;
            }
        } while (status == Z_OK);

        size_t consumed = (size_t)(stream.next_in - (data + pos));
        inflateEnd(&stream);

        if (status == Z_STREAM_END && used >= minimum) {
            char name[4096];
            snprintf(name, sizeof(name), "%s/payload-%06zu.bin", outdir, found);
            FILE *out = fopen(name, "wb");
            if (!out || fwrite(payload, 1, used, out) != used) {
                fprintf(stderr, "cannot write %s\n", name);
                return 1;
            }
            fclose(out);
            printf("%s %zu\n", strrchr(name, '/') + 1, used);
            found++;
            pos += consumed ? consumed : 1;
        } else {
            pos++;
        }
    }

    free(payload);
    free(data);
    printf("streams: %zu\n", found);
    return 0;
}
