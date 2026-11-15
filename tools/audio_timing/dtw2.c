// Dynamic time warping between a recording's features (a: n x d) and a synthetic reading's (b: m x d),
// from (0,0) to (n-1,m-1), within a band: row i may use columns lo[i]..hi[i] (both non-decreasing,
// lo[0] = 0, hi[n-1] = m-1, lo[i] <= hi[i-1] + 1). Cost: Euclidean distance; steps (1,1) weighted 2,
// (1,0) and (0,1) weighted 1, so no shape of path is favoured. Writes the path into pi/pj from its
// end back to its start and returns its length; *edge counts the path's cells on the band's edge
// (other than its two ends), a sign the band was too narrow.
#include <stdlib.h>
#include <math.h>
#include <float.h>

int dtw_path(const float *a, int n, const float *b, int m, int d, const int *lo, const int *hi,
             int *pi, int *pj, int *edge) {
    size_t total = 0;
    size_t *off = malloc(sizeof(size_t) * (size_t)n);
    for (int i = 0; i < n; i++) { off[i] = total; total += (size_t)(hi[i] - lo[i] + 1); }
    unsigned char *dir = malloc(total);
    float *prev = malloc(sizeof(float) * (size_t)m), *cur = malloc(sizeof(float) * (size_t)m);
    if (!off || !dir || !prev || !cur) { free(off); free(dir); free(prev); free(cur); return -1; }
    int plo = 0, phi = -1;
    for (int i = 0; i < n; i++) {
        int L = lo[i], H = hi[i];
        const float *ai = a + (size_t)i * d;
        for (int j = L; j <= H; j++) {
            const float *bj = b + (size_t)j * d;
            float c = 0;
            for (int k = 0; k < d; k++) { float t = ai[k] - bj[k]; c += t * t; }
            c = sqrtf(c);
            float best = FLT_MAX;
            unsigned char w = 255;
            if (i == 0 && j == 0) { best = c; w = 3; }
            else {
                if (i > 0 && j > 0 && j - 1 >= plo && j - 1 <= phi && prev[j - 1] < FLT_MAX) {
                    float v = prev[j - 1] + 2 * c; if (v < best) { best = v; w = 0; }
                }
                if (i > 0 && j >= plo && j <= phi && prev[j] < FLT_MAX) {
                    float v = prev[j] + c; if (v < best) { best = v; w = 2; }
                }
                if (j > L && cur[j - 1] < FLT_MAX) {
                    float v = cur[j - 1] + c; if (v < best) { best = v; w = 1; }
                }
            }
            cur[j] = best;
            dir[off[i] + (size_t)(j - L)] = w;
        }
        float *t = prev; prev = cur; cur = t;
        plo = L; phi = H;
    }
    free(prev); free(cur);
    int i = n - 1, j = m - 1, len = 0, e = 0;
    while (i >= 0 && j >= 0) {
        pi[len] = i; pj[len] = j; len++;
        if ((j == lo[i] || j == hi[i]) && !(i == 0 && j == 0) && !(i == n - 1 && j == m - 1)) e++;
        unsigned char w = dir[off[i] + (size_t)(j - lo[i])];
        if (w == 3 || w == 255) break;
        if (w == 0) { i--; j--; } else if (w == 1) { j--; } else { i--; }
    }
    free(dir); free(off);
    *edge = e;
    return len;
}

// The cost of matching two short stretches of features end to end (a: n x d, b: m x d), the same
// steps and weights as above, divided by n + m: how alike two people's (or one narrator's) readings
// of the same word are.
double dtw_cost(const float *a, int n, const float *b, int m, int d) {
    double *prev = malloc(sizeof(double) * (size_t)m), *cur = malloc(sizeof(double) * (size_t)m);
    if (!prev || !cur) { free(prev); free(cur); return -1; }
    for (int i = 0; i < n; i++) {
        for (int j = 0; j < m; j++) {
            double c = 0;
            for (int k = 0; k < d; k++) { double t = a[(size_t)i * d + k] - b[(size_t)j * d + k]; c += t * t; }
            c = sqrt(c);
            double best;
            if (i == 0 && j == 0) best = 0;
            else {
                best = 1e300;
                if (i > 0 && j > 0 && prev[j - 1] + c < best) best = prev[j - 1] + c;   /* (1,1): 2c in all */
                if (i > 0 && prev[j] < best) best = prev[j];
                if (j > 0 && cur[j - 1] < best) best = cur[j - 1];
            }
            cur[j] = best + c;
        }
        double *t = prev; prev = cur; cur = t;
    }
    double r = prev[m - 1] / (n + m);
    free(prev); free(cur);
    return r;
}

// A reading (b) somewhere in a recording (a), matched with the path's slope kept between 1/2 and 2
// (Sakoe and Chiba's symmetric steps, P = 1: (1,1) weighted 2, and (1,2) and (2,1) through the cell
// between, weighted 3), so that no stretch of the reading is squeezed into an instant of the recording
// or the other way round. The reading may start in any of the first begin_rows + 1 rows (at column 0;
// those rows' bands must start there) and end in any row whose band reaches column m-1; each row of the
// recording left out before or after it costs [skip], so that leaving out the recording is no cheaper
// than matching it. Writes the path (from its end back) into pi/pj and returns its length; *end_row is
// the row it ends in, *cost its cost for its length (every path from (s, 0) to (i, j) weighs
// (i - s) + j + 1), and *edge counts its cells on the band's edge (other than its two ends).
int dtw_path_p1(const float *a, int n, const float *b, int m, int d, const int *lo, const int *hi,
                int begin_rows, double skip, int *pi, int *pj, int *edge, int *end_row, double *cost) {
    size_t total = 0;
    size_t *off = malloc(sizeof(size_t) * (size_t)n);
    if (!off) return -1;
    for (int i = 0; i < n; i++) { off[i] = total; total += (size_t)(hi[i] - lo[i] + 1); }
    unsigned char *dir = malloc(total);
    float *D2 = malloc(sizeof(float) * (size_t)m), *D1 = malloc(sizeof(float) * (size_t)m);
    float *D0 = malloc(sizeof(float) * (size_t)m), *cp = malloc(sizeof(float) * (size_t)m);
    float *cc = malloc(sizeof(float) * (size_t)m);
    int *S2 = malloc(sizeof(int) * (size_t)m), *S1 = malloc(sizeof(int) * (size_t)m), *S0 = malloc(sizeof(int) * (size_t)m);
    if (!dir || !D2 || !D1 || !D0 || !cp || !cc || !S2 || !S1 || !S0) {
        free(off); free(dir); free(D2); free(D1); free(D0); free(cp); free(cc); free(S2); free(S1); free(S0);
        return -1;
    }
    int best_i = -1;
    double best = 1e300;
    for (int i = 0; i < n; i++) {
        int L = lo[i], H = hi[i];
        int L1 = i >= 1 ? lo[i - 1] : 1, H1 = i >= 1 ? hi[i - 1] : 0;
        int L2 = i >= 2 ? lo[i - 2] : 1, H2 = i >= 2 ? hi[i - 2] : 0;
        const float *ai = a + (size_t)i * d;
        for (int j = L; j <= H; j++) {
            const float *bj = b + (size_t)j * d;
            float c = 0;
            for (int k = 0; k < d; k++) { float t = ai[k] - bj[k]; c += t * t; }
            c = sqrtf(c);
            cc[j] = c;
            float v_best = FLT_MAX;
            unsigned char w = 255;
            int s = -1;
            if (j == 0) {
                if (i <= begin_rows) { v_best = c + (float)(skip * i); w = 3; s = i; }
            } else {
                if (j - 1 >= L1 && j - 1 <= H1 && D1[j - 1] < FLT_MAX) {
                    float v = D1[j - 1] + 2 * c; if (v < v_best) { v_best = v; w = 0; s = S1[j - 1]; }
                }
                if (j >= 2 && j - 1 >= L && j - 2 >= L1 && j - 2 <= H1 && D1[j - 2] < FLT_MAX) {
                    float v = D1[j - 2] + 2 * cc[j - 1] + c; if (v < v_best) { v_best = v; w = 1; s = S1[j - 2]; }
                }
                if (j - 1 >= L2 && j - 1 <= H2 && j >= L1 && j <= H1 && D2[j - 1] < FLT_MAX) {
                    float v = D2[j - 1] + 2 * cp[j] + c; if (v < v_best) { v_best = v; w = 2; s = S2[j - 1]; }
                }
            }
            D0[j] = v_best; S0[j] = s;
            dir[off[i] + (size_t)(j - L)] = w;
        }
        if (H == m - 1 && D0[m - 1] < FLT_MAX) {
            double v = D0[m - 1] + skip * (n - 1 - i);
            if (v < best) { best = v; best_i = i; *cost = (D0[m - 1] - skip * S0[m - 1]) / (double)(i - S0[m - 1] + m); }
        }
        float *t = D2; D2 = D1; D1 = D0; D0 = t;
        int *u = S2; S2 = S1; S1 = S0; S0 = u;
        t = cp; cp = cc; cc = t;
    }
    free(D2); free(D1); free(D0); free(cp); free(cc); free(S2); free(S1); free(S0);
    if (best_i < 0) { free(dir); free(off); return -2; }
    int i = best_i, j = m - 1, len = 0, e = 0;
    while (i >= 0 && j >= 0) {
        pi[len] = i; pj[len] = j; len++;
        if ((j == lo[i] || j == hi[i]) && j != 0 && !(i == best_i && j == m - 1)) e++;
        unsigned char w = dir[off[i] + (size_t)(j - lo[i])];
        if (w == 3 || w == 255) break;
        if (w == 0) { i--; j--; }
        else if (w == 1) { pi[len] = i; pj[len] = j - 1; len++; i--; j -= 2; }
        else { pi[len] = i - 1; pj[len] = j; len++; i -= 2; j--; }
    }
    free(dir); free(off);
    *edge = e; *end_row = best_i;
    return len;
}
