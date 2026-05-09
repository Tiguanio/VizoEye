package com.example.vizoeye.di

import com.example.vizoeye.domain.repository.AiRepository
import com.example.vizoeye.domain.usecase.AnalyzeImageUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideAnalyzeImageUseCase(
        aiRepository: AiRepository
    ): AnalyzeImageUseCase {
        return AnalyzeImageUseCase(aiRepository)
    }
}
