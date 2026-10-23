package com.semiovert.app.feature.home

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.designsystem.articleCoverResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Cold-start data and image work can run before Compose or navigation is constructed. */
internal suspend fun prepareHomeCovers(resources: Resources): Map<Int,ImageBitmap> = withContext(Dispatchers.Default) {
    SampleLibrary.articles.map {articleCoverResource(it,false)}.distinct()
        .associateWith {BitmapFactory.decodeResource(resources,it).asImageBitmap()}
}
