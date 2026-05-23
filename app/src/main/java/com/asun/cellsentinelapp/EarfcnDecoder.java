package com.asun.cellsentinelapp;

/** Maps LTE EARFCN / NR NRARFCN to band number and approximate DL center frequency. */
public final class EarfcnDecoder {
    private EarfcnDecoder() {}

    /** Returns e.g. "B3 · 1842 MHz (FDD)" or null if unknown. */
    public static String decodeLte(int earfcn) {
        if (earfcn == Integer.MAX_VALUE || earfcn < 0) return null;
        for (int[] e : LTE) {
            if (earfcn >= e[0] && earfcn <= e[1])
                return "B" + e[2] + " · " + e[3] + " MHz (" + (e[4] == 1 ? "TDD" : "FDD") + ")";
        }
        return null;
    }

    /** Returns e.g. "n41 · 2593 MHz (TDD)" or null if unknown. */
    public static String decodeNr(int nrArfcn) {
        if (nrArfcn == Integer.MAX_VALUE || nrArfcn < 0) return null;
        for (int[] e : NR) {
            if (nrArfcn >= e[0] && nrArfcn <= e[1])
                return "n" + e[2] + " · " + e[3] + " MHz (" + (e[4] == 1 ? "TDD" : "FDD") + ")";
        }
        return null;
    }

    /**
     * Precise LTE DL frequency from 3GPP TS 36.101 Table 5.7.3-1.
     * Returns frequency in MHz × 10 (i.e. tenths of MHz), or -1 if unknown.
     * Formula: F_DL = F_DL_low + 0.1 × (N_DL - N_DL_Offset)
     * Table columns: {startEARFCN, endEARFCN, F_DL_low×10, N_DL_Offset}
     */
    public static double lteFrequencyMhz(int earfcn) {
        if (earfcn == Integer.MAX_VALUE || earfcn < 0) return -1;
        for (int[] r : LTE_FREQ) {
            if (earfcn >= r[0] && earfcn <= r[1]) {
                // F_DL_low is stored ×10; result = F_DL_low/10 + 0.1*(earfcn - N_DL_Offset)
                return r[2] / 10.0 + 0.1 * (earfcn - r[3]);
            }
        }
        return -1;
    }

    /**
     * Precise NR DL frequency from 3GPP TS 38.101 Table 5.4.2.1-1.
     * Returns frequency in MHz, or -1 if out of range.
     */
    public static double nrFrequencyMhz(int nrArfcn) {
        if (nrArfcn == Integer.MAX_VALUE || nrArfcn < 0) return -1;
        if (nrArfcn <= 600000) {
            // FR1 sub-3GHz: FREF = FREF-Offs + ΔFGlobal × (NREF - NREF-Offs)
            // ΔFGlobal=5kHz, FREF-Offs=0, NREF-Offs=0 for 0..599999
            return nrArfcn * 0.005;
        } else if (nrArfcn <= 2016666) {
            // FR1 3-7.125 GHz: ΔFGlobal=15kHz, FREF-Offs=3000MHz, NREF-Offs=600000
            return 3000.0 + (nrArfcn - 600000) * 0.015;
        } else {
            // FR2 mmWave: ΔFGlobal=60kHz, FREF-Offs=24250.08MHz, NREF-Offs=2016667
            return 24250.08 + (nrArfcn - 2016667) * 0.060;
        }
    }

    // {startEARFCN, endEARFCN, band, approxFreqMHz, 0=FDD/1=TDD}
    private static final int[][] LTE = {
        {0,     599,   1,  2140, 0},
        {600,   1199,  2,  1960, 0},
        {1200,  1949,  3,  1842, 0},
        {1950,  2399,  4,  2132, 0},
        {2400,  2649,  5,   881, 0},
        {2650,  2749,  6,   880, 0},
        {2750,  3449,  7,  2655, 0},
        {3450,  3799,  8,   942, 0},
        {3800,  4149,  9,  1862, 0},
        {4150,  4749, 10,  2140, 0},
        {4750,  4999, 11,  1488, 0},
        {5000,  5179, 12,   737, 0},
        {5180,  5279, 13,   751, 0},
        {5280,  5379, 14,   763, 0},
        {5730,  5849, 17,   740, 0},
        {5850,  5999, 18,   867, 0},
        {6000,  6149, 19,   882, 0},
        {6150,  6449, 20,   806, 0},
        {6450,  6599, 21,  1503, 0},
        {6600,  7399, 22,  3550, 0},
        {7500,  7699, 23,  2190, 0},
        {7700,  8039, 24,  1542, 0},
        {8040,  8689, 25,  1962, 0},
        {8690,  9039, 26,   876, 0},
        {9040,  9209, 27,   860, 0},
        {9210,  9659, 28,   780, 0},
        {9660,  9769, 29,   722, 0},
        {9770,  9869, 30,  2355, 0},
        {9870,  9919, 31,   455, 0},
        // TDD bands
        {36200, 36349, 34, 2017, 1},
        {36350, 36949, 35, 1880, 1},
        {36950, 37549, 36, 1960, 1},
        {37550, 37749, 37, 1920, 1},
        {37750, 38249, 38, 2595, 1},
        {38250, 38649, 39, 1900, 1},
        {38650, 39649, 40, 2350, 1},
        {39650, 41589, 41, 2593, 1},
        {41590, 43589, 42, 3500, 1},
        {43590, 45589, 43, 3700, 1},
        {45590, 46589, 44,  753, 1},
    };

    // {startNRARFCN, endNRARFCN, band, approxFreqMHz, 0=FDD/1=TDD}
    private static final int[][] NR = {
        {422000, 434000,  1, 2140, 0},
        {386000, 398000,  3, 1842, 0},
        {173800, 178800,  5,  882, 0},
        {185000, 192000,  8,  942, 0},
        {151600, 160600, 28,  780, 0},
        {499200, 537999, 41, 2593, 1},
        {620000, 653333, 78, 3550, 1},
        {693334, 733333, 79, 4700, 1},
    };

    // 3GPP TS 36.101 Table 5.7.3-1: {startEARFCN, endEARFCN, F_DL_low×10, N_DL_Offset}
    private static final int[][] LTE_FREQ = {
        {0,     599,   21100, 0},      // B1  2110 MHz
        {600,   1199,  19300, 600},    // B2  1930 MHz
        {1200,  1949,  18050, 1200},   // B3  1805 MHz
        {1950,  2399,  21100, 1950},   // B4  2110 MHz
        {2400,  2649,  8690,  2400},   // B5   869 MHz
        {2650,  2749,  8750,  2650},   // B6   875 MHz
        {2750,  3449,  26200, 2750},   // B7  2620 MHz
        {3450,  3799,  9250,  3450},   // B8   925 MHz
        {3800,  4149,  18448, 3800},   // B9  1844.9 MHz
        {4150,  4749,  21100, 4150},   // B10 2110 MHz
        {4750,  4999,  14759, 4750},   // B11 1475.9 MHz
        {5000,  5179,  7290,  5000},   // B12  729 MHz
        {5180,  5279,  7460,  5180},   // B13  746 MHz
        {5280,  5379,  7580,  5280},   // B14  758 MHz
        {5730,  5849,  7340,  5730},   // B17  734 MHz
        {5850,  5999,  8600,  5850},   // B18  860 MHz
        {6000,  6149,  8750,  6000},   // B19  875 MHz
        {6150,  6449,  7910,  6150},   // B20  791 MHz
        {6450,  6599,  14960, 6450},   // B21 1496 MHz
        {6600,  7399,  35000, 6600},   // B22 3500 MHz
        {7500,  7699,  21800, 7500},   // B23 2180 MHz
        {7700,  8039,  15250, 7700},   // B24 1525 MHz
        {8040,  8689,  19300, 8040},   // B25 1930 MHz
        {8690,  9039,  8590,  8690},   // B26  859 MHz
        {9040,  9209,  8520,  9040},   // B27  852 MHz
        {9210,  9659,  7580,  9210},   // B28  758 MHz
        {9660,  9769,  7170,  9660},   // B29  717 MHz
        {9770,  9869,  23500, 9770},   // B30 2350 MHz
        {9870,  9919,  4620,  9870},   // B31  462 MHz
        // TDD bands (DL=UL, N_DL_Offset = startEARFCN)
        {36200, 36349, 20100, 36200},  // B34 2010 MHz
        {36350, 36949, 18500, 36350},  // B35 1850 MHz
        {36950, 37549, 19300, 36950},  // B36 1930 MHz
        {37550, 37749, 19100, 37550},  // B37 1910 MHz
        {37750, 38249, 25700, 37750},  // B38 2570 MHz
        {38250, 38649, 18800, 38250},  // B39 1880 MHz
        {38650, 39649, 23000, 38650},  // B40 2300 MHz
        {39650, 41589, 25750, 39650},  // B41 2575 MHz
        {41590, 43589, 34500, 41590},  // B42 3450 MHz
        {43590, 45589, 36700, 43590},  // B43 3600 MHz  (approx)
        {45590, 46589, 7030,  45590},  // B44  703 MHz
    };
}
