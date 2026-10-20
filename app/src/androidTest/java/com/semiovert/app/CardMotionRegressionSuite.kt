package com.semiovert.app

import org.junit.runner.RunWith
import org.junit.runners.Suite

/** Existing browsing/form contracts plus the new shared reading contract. */
@RunWith(Suite::class)
@Suite.SuiteClasses(SharedCardMotionTest::class,HomeArcDeckTest::class,CategoryWaveDeckTest::class,PageCompletionTest::class)
class CardMotionRegressionSuite
