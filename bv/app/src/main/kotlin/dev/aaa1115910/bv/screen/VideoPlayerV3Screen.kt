package dev.aaa1115910.bv.screen

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.aaa1115910.bv.component.controllers.VideoPlayerController
import dev.aaa1115910.bv.component.controllers.VideoProgressSeek
import dev.aaa1115910.bv.entity.VideoAspectRatio
import dev.aaa1115910.bv.entity.VideoListItem
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.player.BvVideoPlayer
import dev.aaa1115910.bv.ui.effect.PlayerUiEffect
import dev.aaa1115910.bv.ui.state.PlayerState
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.VideoShotImageCache
import dev.aaa1115910.bv.viewmodel.player.VideoPlayerV3ViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.androidx.compose.koinViewModel

@Composable
fun VideoPlayerV3Screen(
    modifier: Modifier = Modifier,
    playerViewModel: VideoPlayerV3ViewModel = koinViewModel()
) {
    val logger = KotlinLogging.logger { }
    val context = LocalContext.current
    val videoPlayer = playerViewModel.videoPlayer

    var isLooping by remember { mutableStateOf(false) }
    val uiState by playerViewModel.uiState.collectAsState()
    val seekerState = playerViewModel.seekerState.collectAsState()

    val videoShotCache by remember(uiState.videoShot) { mutableStateOf(VideoShotImageCache()) }

    LaunchedEffect(Unit) {
        playerViewModel.uiEffect.collect { effect ->
            when (effect) {
                PlayerUiEffect.FinishActivity -> {
                    (context as Activity).finish()
                }

                PlayerUiEffect.PlayEnded -> {
                    if (isLooping) {
                        playerViewModel.backToStart()
                        return@collect
                    }

                    playerViewModel.checkAndPlayNext()
                }
            }
        }
    }

    // 循环发送心跳
    LaunchedEffect(Unit) {
        delay(5000)
        while (isActive) {
            if (uiState.playerState == PlayerState.Playing) playerViewModel.trySendHeartbeat()
            // 周期延迟
            delay(15000)
        }
    }

    VideoPlayerController(
        modifier = modifier,
        aid = uiState.aid,
        fromSeason = uiState.fromSeason,
        proxyArea = ProxyArea.MainLand,
        isLooping = isLooping,
        isPlaying = videoPlayer?.isPlaying ?: false,
        videoShotCache = videoShotCache,
        uiState = uiState,
        seekerState = seekerState,
        onPlay = { videoPlayer?.start() },
        onPause = {
            videoPlayer?.pause()
            playerViewModel.trySendHeartbeat()
        },
        onExit = {
            (context as Activity).finish()
        },
        onGoTime = { time ->
            playerViewModel.seekToTime(time)
        },
        onBackToStart = { playerViewModel.backToStart() },
        onPlayNewVideo = {
            playerViewModel.trySendHeartbeat()
            playerViewModel.playNewVideo(it)
        },
        onCancelSkipToNextEp = {
            playerViewModel.cancelPlayNext()
        },
        onToggleLoop = {
            isLooping = !isLooping
        },
        onGoToUpPage = { },

        onMediaProfileSettingChange = { action ->
            playerViewModel.updateMediaProfile(action)
        },
        onAspectRatioChange = { aspectRadio ->
            playerViewModel.updateVideoAspectRatio(aspectRadio)
        },
        onPlaySpeedChange = { speed ->
            logger.info { "Set default play speed: $speed" }
            playerViewModel.updatePlaySpeed(speed)
        },
        onSubtitleChange = { subtitle ->
            playerViewModel.loadSubtitle(subtitle.id)
        },
        onSubtitleSettingChange = { action ->
            logger.info { "On subtitle config change" }
            playerViewModel.updateSubtitleState(action)
        },
    ) {
        Box(
            modifier = Modifier.background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            LaunchedEffect(Unit) {
                videoPlayer?.setOptions()
            }

            val aspectRatio = when (uiState.aspectRatio) {
                VideoAspectRatio.Default -> {
                    if (uiState.videoHeight > 0 && uiState.videoWidth > 0) {
                        uiState.videoWidth / uiState.videoHeight.toFloat()
                    } else {
                        16 / 9f
                    }
                }

                VideoAspectRatio.FourToThree -> 4 / 3f
                VideoAspectRatio.SixteenToNine -> 16 / 9f
            }

            BvVideoPlayer(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(aspectRatio)
                    .align(Alignment.Center),
                videoPlayer = videoPlayer,
            )
            if (Prefs.showPersistentSeek) {
                VideoProgressSeek(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    duration = seekerState.value.totalDuration,
                    position = seekerState.value.currentTime,
                    bufferedPercentage = seekerState.value.bufferedPercentage,
                    isPersistentSeek = true
                )
            }
        }
    }
}
