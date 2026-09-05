package de.spardirekt.ugcagent.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SimilarityCheckerTest {

    @Test
    fun hammingDistanceIsZeroForIdenticalHashes() {
        assertThat(SimilarityChecker.hamming(0b1010L, 0b1010L)).isEqualTo(0)
    }

    @Test
    fun hammingDistanceCountsDifferingBits() {
        assertThat(SimilarityChecker.hamming(0b1111L, 0b0001L)).isEqualTo(3)
    }

    @Test
    fun outlierThresholdIsBelowFullHashSize() {
        assertThat(SimilarityChecker.OUTLIER_DISTANCE).isLessThan(SimilarityChecker.HASH_SIZE * SimilarityChecker.HASH_SIZE)
        assertThat(SimilarityChecker.OUTLIER_DISTANCE).isGreaterThan(0)
    }
}
