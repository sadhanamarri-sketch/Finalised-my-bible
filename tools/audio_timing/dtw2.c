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
