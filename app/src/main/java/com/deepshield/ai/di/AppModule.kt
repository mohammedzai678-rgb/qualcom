package com.deepshield.ai.di

import android.content.Context
import com.deepshield.ai.ml.DeepfakeDetector
import com.deepshield.ai.ml.ModelManager
import com.deepshield.ai.ml.PerformanceProfiler
import com.deepshield.ai.watermark.AudioWatermarkEngine
import com.deepshield.ai.watermark.CryptoSigner
import com.deepshield.ai.watermark.DctWatermarkEngine
import com.deepshield.ai.watermark.InvisibleWatermarkProcessor
import com.deepshield.ai.watermark.VideoWatermarkEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI module — provides all singleton dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideModelManager(
        @ApplicationContext context: Context
    ): ModelManager = ModelManager(context)

    @Provides
    @Singleton
    fun providePerformanceProfiler(
        @ApplicationContext context: Context
    ): PerformanceProfiler = PerformanceProfiler(context)

    @Provides
    @Singleton
    fun provideDeepfakeDetector(
        @ApplicationContext context: Context,
        modelManager: ModelManager,
        performanceProfiler: PerformanceProfiler
    ): DeepfakeDetector = DeepfakeDetector(context, modelManager, performanceProfiler)

    @Provides
    @Singleton
    fun provideInvisibleWatermarkProcessor(): InvisibleWatermarkProcessor =
        InvisibleWatermarkProcessor()

    /**
     * DctWatermarkEngine requires InvisibleWatermarkProcessor — was previously
     * instantiated with no-arg constructor (compile error). Fixed here.
     */
    @Provides
    @Singleton
    fun provideDctWatermarkEngine(
        invisibleProcessor: InvisibleWatermarkProcessor
    ): DctWatermarkEngine = DctWatermarkEngine(invisibleProcessor)

    @Provides
    @Singleton
    fun provideVideoWatermarkEngine(
        invisibleProcessor: InvisibleWatermarkProcessor
    ): VideoWatermarkEngine = VideoWatermarkEngine(invisibleProcessor)

    @Provides
    @Singleton
    fun provideAudioWatermarkEngine(): AudioWatermarkEngine = AudioWatermarkEngine()

    @Provides
    @Singleton
    fun provideCryptoSigner(
        @ApplicationContext context: Context
    ): CryptoSigner = CryptoSigner(context)
}
